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
import java.util.List;

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

	/**
	 * A local variable binding: name, optional type name, and optional
	 * initializer expression context for type inference.
	 */
	public record LocalVariable(String name, String typeName,
			KotlinParser.ExpressionContext initializerCtx) {

		public LocalVariable(String name, String typeName) {
			this(name, typeName, null);
		}
	}

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
	public static List<LocalVariable> extract(ParseTree parseTree,
			int startOffset, int endOffset) {
		if (parseTree == null) {
			return Collections.emptyList();
		}

		KotlinParser.FunctionDeclarationContext funcCtx =
				findFunctionDeclaration(parseTree, startOffset, endOffset);
		if (funcCtx == null) {
			return Collections.emptyList();
		}

		KotlinParser.FunctionBodyContext body = funcCtx.functionBody();
		if (body == null || body.block() == null) {
			return Collections.emptyList();
		}

		List<LocalVariable> locals = new ArrayList<>();
		collectLocals(body.block(), locals);
		return locals;
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
			List<LocalVariable> locals) {
		// Stop at nested function declarations and lambda literals
		if (node instanceof KotlinParser.FunctionDeclarationContext
				|| node instanceof KotlinParser.LambdaLiteralContext) {
			return;
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
				// Capture initializer for type inference when
				// no explicit type annotation
				KotlinParser.ExpressionContext initExpr =
						typeName == null ? propCtx.expression()
								: null;
				locals.add(new LocalVariable(name, typeName,
						initExpr));
			}
			// Don't recurse into the property's initializer expression
			return;
		}

		for (int i = 0; i < node.getChildCount(); i++) {
			collectLocals(node.getChild(i), locals);
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
}
