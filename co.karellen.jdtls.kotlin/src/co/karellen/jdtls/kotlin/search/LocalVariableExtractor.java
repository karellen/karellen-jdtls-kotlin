/*
 * Copyright 2026 Karellen, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package co.karellen.jdtls.kotlin.search;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.antlr.v4.runtime.tree.ParseTree;

import co.karellen.jdtls.kotlin.parser.KotlinParser;

/**
 * Extracts local variable declarations (name and optional type annotation)
 * from a function body in the ANTLR parse tree. Used to populate the scope
 * chain for receiver type verification during reference matching.
 *
 * @author Arcadiy Ivanov
 */
public class LocalVariableExtractor {

	private static final Pattern SIMPLE_IDENTIFIER =
			Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

	/**
	 * A local variable binding: name, optional type name, and optional
	 * initializer expression context for type inference.
	 */
	public record LocalVariable(String name, String typeName,
			KotlinParser.ExpressionContext initializerCtx,
			boolean isVal) {

		public LocalVariable(String name, String typeName) {
			this(name, typeName, null, true);
		}
	}

	/**
	 * A reassignment of a local variable or qualified property.
	 * For simple reassignments, {@code variableName} is the variable name.
	 * For property assignments, it's {@code "receiver.property"}.
	 */
	public record Reassignment(String variableName, int offset,
			KotlinParser.ExpressionContext rhsExpr) {}

	/**
	 * A scope where a variable has a narrowed type from an assignment.
	 * The {@code resolvedType} is pre-resolved from the RHS expression
	 * once during scope building, avoiding repeated resolution per
	 * reference.
	 */
	public record AssignmentNarrowingScope(String variableName,
			int startOffset, int endOffset,
			KotlinType resolvedType)
			implements ScopeRange {
		@Override
		public int getStartOffset() {
			return startOffset;
		}

		@Override
		public int getEndOffset() {
			return endOffset;
		}
	}

	/**
	 * Result of extraction: local variable declarations and reassignments.
	 */
	public record ExtractionResult(List<LocalVariable> locals,
			List<Reassignment> reassignments) {}

	/**
	 * Extracts local variable declarations from the function body that
	 * encloses the given offset range. Walks the ANTLR parse tree to find
	 * the matching {@code FunctionDeclarationContext}, then collects
	 * {@code propertyDeclaration} nodes from its body. Stops recursion
	 * at nested function declarations and lambda literals to avoid
	 * inner-scope locals.
	 *
	 * @param parseTree the full ANTLR parse tree of the file
	 * @param startOffset the start offset of the enclosing function
	 * @param endOffset the end offset of the enclosing function
	 * @return list of local variables, empty if none found
	 */
	public static ExtractionResult extract(
			ParseTree parseTree, int startOffset, int endOffset) {
		if (parseTree == null) {
			return new ExtractionResult(Collections.emptyList(),
					Collections.emptyList());
		}

		KotlinParser.FunctionDeclarationContext funcCtx =
				findFunctionDeclaration(parseTree, startOffset,
						endOffset);
		if (funcCtx == null) {
			return new ExtractionResult(Collections.emptyList(),
					Collections.emptyList());
		}

		KotlinParser.FunctionBodyContext body = funcCtx.functionBody();
		if (body == null || body.block() == null) {
			return new ExtractionResult(Collections.emptyList(),
					Collections.emptyList());
		}

		List<LocalVariable> locals = new ArrayList<>();
		List<Reassignment> reassignments = new ArrayList<>();
		collectLocals(body.block(), locals, reassignments);
		return new ExtractionResult(locals, reassignments);
	}

	private static KotlinParser.FunctionDeclarationContext findFunctionDeclaration(
			ParseTree node, int startOffset, int endOffset) {
		if (node instanceof KotlinParser.FunctionDeclarationContext ctx) {
			int nodeStart = ctx.getStart().getStartIndex();
			int nodeEnd = ctx.getStop().getStopIndex();
			if (nodeStart == startOffset && nodeEnd == endOffset) {
				return ctx;
			}
		}
		for (int i = 0; i < node.getChildCount(); i++) {
			KotlinParser.FunctionDeclarationContext result =
					findFunctionDeclaration(node.getChild(i),
							startOffset, endOffset);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	private static void collectLocals(ParseTree node,
			List<LocalVariable> locals,
			List<Reassignment> reassignments) {
		// Stop at nested function declarations and lambda literals
		if (node instanceof KotlinParser.FunctionDeclarationContext
				|| node instanceof KotlinParser.LambdaLiteralContext) {
			return;
		}

		if (node instanceof KotlinParser.AssignmentContext assignCtx) {
			if (assignCtx.ASSIGNMENT() != null) {
				collectAssignment(assignCtx, locals, reassignments);
			}
		}

		if (node instanceof KotlinParser.ForStatementContext forCtx) {
			KotlinParser.VariableDeclarationContext forVar =
					forCtx.variableDeclaration();
			if (forVar != null
					&& forVar.simpleIdentifier() != null) {
				String name = forVar.simpleIdentifier().getText();
				String typeName = null;
				if (forVar.type() != null) {
					typeName = extractSimpleTypeName(
							forVar.type());
				}
				locals.add(new LocalVariable(name, typeName));
			}
			// Also handle destructuring in for loops
			KotlinParser.MultiVariableDeclarationContext multiVar =
					forCtx.multiVariableDeclaration();
			if (multiVar != null) {
				for (KotlinParser.VariableDeclarationContext vd :
						multiVar.variableDeclaration()) {
					if (vd.simpleIdentifier() != null) {
						String name = vd.simpleIdentifier()
								.getText();
						if ("_".equals(name)) {
							continue;
						}
						String typeName = vd.type() != null
								? extractSimpleTypeName(vd.type())
								: null;
						locals.add(new LocalVariable(name,
								typeName));
					}
				}
			}
			// Continue recursion into the for body
		}

		if (node instanceof KotlinParser.PropertyDeclarationContext propCtx) {
			KotlinParser.VariableDeclarationContext varDecl =
					propCtx.variableDeclaration();
			if (varDecl != null
					&& varDecl.simpleIdentifier() != null) {
				String name = varDecl.simpleIdentifier().getText();
				String typeName = null;
				if (varDecl.type() != null) {
					typeName = extractSimpleTypeName(varDecl.type());
				}
				KotlinParser.ExpressionContext initExpr =
						propCtx.expression();
				boolean isVal = propCtx.VAL() != null;
				locals.add(new LocalVariable(name, typeName,
						initExpr, isVal));
			}
			// Don't recurse into the property's initializer expression
			return;
		}

		for (int i = 0; i < node.getChildCount(); i++) {
			collectLocals(node.getChild(i), locals, reassignments);
		}
	}

	/**
	 * Extracts a simple type name from a type context, stripping nullability
	 * markers. Handles user types (e.g., {@code Foo}, {@code Foo?}) and
	 * falls back to getText for complex types.
	 */
	private static String extractSimpleTypeName(
			KotlinParser.TypeContext ctx) {
		if (ctx == null) {
			return null;
		}
		KotlinParser.TypeReferenceContext typeRef = ctx.typeReference();
		if (typeRef != null) {
			return extractFromTypeReference(typeRef);
		}
		KotlinParser.NullableTypeContext nullable = ctx.nullableType();
		if (nullable != null) {
			KotlinParser.TypeReferenceContext innerRef =
					nullable.typeReference();
			if (innerRef != null) {
				return extractFromTypeReference(innerRef);
			}
		}
		// Fallback: strip whitespace from getText
		return ctx.getText().replaceAll("\\s+", "");
	}

	private static String extractFromTypeReference(
			KotlinParser.TypeReferenceContext ctx) {
		if (ctx == null) {
			return null;
		}
		KotlinParser.UserTypeContext userType = ctx.userType();
		if (userType != null && !userType.simpleUserType().isEmpty()) {
			KotlinParser.SimpleUserTypeContext first =
					userType.simpleUserType().get(0);
			String baseName = first.simpleIdentifier().getText();
			// Preserve type arguments (e.g., List<Alpha>)
			if (first.typeArguments() != null
					&& first.typeArguments().typeProjection() != null
					&& !first.typeArguments().typeProjection()
							.isEmpty()) {
				StringBuilder sb = new StringBuilder(baseName);
				sb.append('<');
				List<KotlinParser.TypeProjectionContext> projections =
						first.typeArguments().typeProjection();
				for (int i = 0; i < projections.size(); i++) {
					if (i > 0) {
						sb.append(',');
					}
					KotlinParser.TypeContext typeArg =
							projections.get(i).type();
					if (typeArg != null) {
						sb.append(extractSimpleTypeName(typeArg));
					} else {
						sb.append('*');
					}
				}
				sb.append('>');
				return sb.toString();
			}
			return baseName;
		}
		return ctx.getText().replaceAll("\\s+", "");
	}

	private static void collectAssignment(
			KotlinParser.AssignmentContext assignCtx,
			List<LocalVariable> locals,
			List<Reassignment> reassignments) {
		KotlinParser.DirectlyAssignableExpressionContext lhs =
				assignCtx.directlyAssignableExpression();
		if (lhs == null) {
			return;
		}
		KotlinParser.ExpressionContext rhs = assignCtx.expression();
		if (rhs == null) {
			return;
		}
		int offset = assignCtx.getStart().getStartIndex();

		if (lhs.simpleIdentifier() != null) {
			// Simple reassignment: x = value
			reassignments.add(new Reassignment(
					lhs.simpleIdentifier().getText(), offset, rhs));
		} else if (lhs.postfixUnaryExpression() != null
				&& lhs.assignableSuffix() != null) {
			// Property assignment: obj.field = value
			String receiver = extractSimpleReceiver(
					lhs.postfixUnaryExpression());
			String property = extractPropertyName(
					lhs.assignableSuffix());
			if (receiver != null && property != null) {
				String qualifiedKey = receiver + "." + property;
				reassignments.add(new Reassignment(qualifiedKey,
						offset, rhs));
				// Recursive alias propagation
				for (String alias : findAllAliases(receiver,
						locals)) {
					reassignments.add(new Reassignment(
							alias + "." + property, offset, rhs));
				}
			}
		}
	}

	private static String extractSimpleReceiver(
			KotlinParser.PostfixUnaryExpressionContext ctx) {
		if (ctx == null || ctx.primaryExpression() == null) {
			return null;
		}
		KotlinParser.PrimaryExpressionContext primary =
				ctx.primaryExpression();
		if (primary.simpleIdentifier() != null) {
			return primary.simpleIdentifier().getText();
		}
		return null;
	}

	private static String extractPropertyName(
			KotlinParser.AssignableSuffixContext ctx) {
		if (ctx == null || ctx.navigationSuffix() == null) {
			return null;
		}
		KotlinParser.NavigationSuffixContext nav =
				ctx.navigationSuffix();
		if (nav.simpleIdentifier() != null) {
			return nav.simpleIdentifier().getText();
		}
		return null;
	}

	static List<String> findAllAliases(String name,
			List<LocalVariable> locals) {
		List<String> aliases = new ArrayList<>();
		Set<String> visited = new HashSet<>();
		visited.add(name);
		String current = name;
		while (current != null) {
			String next = null;
			for (LocalVariable local : locals) {
				if (local.name().equals(current)
						&& local.initializerCtx() != null) {
					String initText =
							local.initializerCtx().getText();
					if (SIMPLE_IDENTIFIER.matcher(initText)
									.matches()
							&& !visited.contains(initText)) {
						aliases.add(initText);
						visited.add(initText);
						next = initText;
					}
					break;
				}
			}
			current = next;
		}
		return aliases;
	}

	/**
	 * Builds assignment narrowing scopes from reassignment records.
	 * Each scope runs from the assignment offset to the next
	 * reassignment of the same variable, or to {@code functionEndOffset}.
	 * RHS expressions are resolved upfront via the given
	 * {@code exprResolver} so that per-reference lookups are free.
	 *
	 * @param reassignments the collected reassignment records
	 * @param varNames names of {@code var} local variables (simple
	 *        and qualified names are both accepted)
	 * @param functionEndOffset the end offset of the enclosing function
	 * @param exprResolver the expression type resolver for RHS inference
	 * @return list of assignment narrowing scopes with pre-resolved types
	 */
	public static List<AssignmentNarrowingScope> buildAssignmentScopes(
			List<Reassignment> reassignments,
			Set<String> varNames, int functionEndOffset,
			ExpressionTypeResolver exprResolver) {
		// Group by variable name
		Map<String, List<Reassignment>> grouped = new HashMap<>();
		for (Reassignment r : reassignments) {
			if (varNames.contains(r.variableName())
					|| r.variableName().contains(".")) {
				grouped.computeIfAbsent(r.variableName(),
						k -> new ArrayList<>()).add(r);
			}
		}

		List<AssignmentNarrowingScope> scopes = new ArrayList<>();
		for (Map.Entry<String, List<Reassignment>> entry
				: grouped.entrySet()) {
			List<Reassignment> sorted = new ArrayList<>(
					entry.getValue());
			sorted.sort((a, b) -> Integer.compare(a.offset(),
					b.offset()));
			for (int i = 0; i < sorted.size(); i++) {
				Reassignment r = sorted.get(i);
				int endOffset = (i + 1 < sorted.size())
						? sorted.get(i + 1).offset() - 1
						: functionEndOffset;
				KotlinType resolved = exprResolver != null
						? exprResolver.resolve(r.rhsExpr())
						: KotlinType.UNKNOWN;
				scopes.add(new AssignmentNarrowingScope(
						r.variableName(), r.offset(), endOffset,
						resolved));
			}
		}
		return scopes;
	}
}
