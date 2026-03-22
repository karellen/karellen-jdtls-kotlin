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
import java.util.regex.Pattern;

import org.antlr.v4.runtime.tree.ParseTree;

import co.karellen.jdtls.kotlin.parser.KotlinParser;

/**
 * Extracts smart cast scope information from a function body in the
 * ANTLR parse tree. Identifies regions where a variable's type is
 * narrowed by {@code is}/{@code !is} checks, null checks, or
 * {@code when} type tests. Used by the search participant to push
 * narrowed type bindings into the {@link ScopeChain} during receiver
 * verification.
 *
 * @author Arcadiy Ivanov
 */
public class SmartCastExtractor {

	private static final Pattern SIMPLE_IDENTIFIER =
			Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

	/**
	 * A smart cast scope recording a narrowed variable binding within
	 * a source range.
	 */
	public static class SmartCastScope implements ScopeRange {

		private final int startOffset;
		private final int endOffset;
		private final String variableName;
		private final String narrowedTypeName;
		private final boolean nullNarrowing;

		public SmartCastScope(int startOffset, int endOffset,
				String variableName, String narrowedTypeName,
				boolean nullNarrowing) {
			this.startOffset = startOffset;
			this.endOffset = endOffset;
			this.variableName = variableName;
			this.narrowedTypeName = narrowedTypeName;
			this.nullNarrowing = nullNarrowing;
		}

		@Override
		public int getStartOffset() {
			return startOffset;
		}

		@Override
		public int getEndOffset() {
			return endOffset;
		}

		public String getVariableName() {
			return variableName;
		}

		public String getNarrowedTypeName() {
			return narrowedTypeName;
		}

		public boolean isNullNarrowing() {
			return nullNarrowing;
		}
	}

	/**
	 * Extracts all smart cast scopes from the given parse tree within
	 * the specified source range (typically a function body).
	 *
	 * @param parseTree  the ANTLR parse tree
	 * @param rangeStart the start offset of the enclosing function
	 * @param rangeEnd   the end offset of the enclosing function
	 * @return list of smart cast scopes, empty if none found
	 */
	public static List<SmartCastScope> extract(ParseTree parseTree,
			int rangeStart, int rangeEnd) {
		if (parseTree == null) {
			return Collections.emptyList();
		}
		List<SmartCastScope> scopes = new ArrayList<>();
		collectSmartCastScopes(parseTree, rangeStart, rangeEnd, scopes);
		return scopes;
	}

	/**
	 * Finds all smart cast scopes containing the given offset, sorted
	 * from outermost to innermost.
	 *
	 * @param scopes the smart cast scopes to search
	 * @param offset the source offset to check
	 * @return list of enclosing scopes outermost-first, empty if none
	 */
	public static List<SmartCastScope> findAllEnclosing(
			List<SmartCastScope> scopes, int offset) {
		return ScopeRange.findAllEnclosing(scopes, offset);
	}

	private static void collectSmartCastScopes(ParseTree node,
			int rangeStart, int rangeEnd,
			List<SmartCastScope> scopes) {
		if (node instanceof KotlinParser.IfExpressionContext ctx) {
			extractFromIfExpression(ctx, rangeStart, rangeEnd, scopes);
		} else if (node instanceof KotlinParser.WhenExpressionContext ctx) {
			extractFromWhenExpression(ctx, rangeStart, rangeEnd,
					scopes);
		} else if (node instanceof KotlinParser.AsExpressionContext ctx) {
			extractFromAsCast(ctx, rangeStart, rangeEnd, scopes);
		} else if (node instanceof KotlinParser.AssignmentContext ctx) {
			extractFromAssignment(ctx, rangeStart, rangeEnd, scopes);
		}

		for (int i = 0; i < node.getChildCount(); i++) {
			collectSmartCastScopes(node.getChild(i), rangeStart,
					rangeEnd, scopes);
		}
	}

	/**
	 * Extracts smart cast scopes from {@code if (x is Foo) { body }}
	 * and {@code if (x != null) { body }}.
	 */
	private static void extractFromIfExpression(
			KotlinParser.IfExpressionContext ctx,
			int rangeStart, int rangeEnd,
			List<SmartCastScope> scopes) {
		KotlinParser.ExpressionContext condition = ctx.expression();
		if (condition == null) {
			return;
		}

		List<KotlinParser.ControlStructureBodyContext> bodies =
				ctx.controlStructureBody();
		if (bodies == null || bodies.isEmpty()) {
			return;
		}

		KotlinParser.ControlStructureBodyContext thenBody = bodies.get(0);
		KotlinParser.ControlStructureBodyContext elseBody =
				bodies.size() > 1 ? bodies.get(1) : null;

		// Collect narrowings from the condition (handles conjunctions)
		List<SmartCastNarrowing> narrowings =
				extractNarrowingsFromCondition(condition);

		for (SmartCastNarrowing narrowing : narrowings) {
			KotlinParser.ControlStructureBodyContext targetBody =
					narrowing.negated ? elseBody : thenBody;
			if (targetBody == null || targetBody.getStart() == null
					|| targetBody.getStop() == null) {
				continue;
			}

			int bodyStart = targetBody.getStart().getStartIndex();
			int bodyEnd = targetBody.getStop().getStopIndex();
			if (bodyStart < rangeStart || bodyEnd > rangeEnd) {
				continue;
			}

			scopes.add(new SmartCastScope(bodyStart, bodyEnd,
					narrowing.variableName, narrowing.typeName,
					narrowing.nullNarrowing));
		}
	}

	/**
	 * Extracts smart cast scopes from {@code when (x) { is Foo -> body }}.
	 */
	private static void extractFromWhenExpression(
			KotlinParser.WhenExpressionContext ctx,
			int rangeStart, int rangeEnd,
			List<SmartCastScope> scopes) {
		// Determine the subject variable name
		String subjectVar = extractWhenSubjectVariable(ctx);
		if (subjectVar == null) {
			return;
		}

		List<KotlinParser.WhenEntryContext> entries = ctx.whenEntry();
		if (entries == null) {
			return;
		}

		for (KotlinParser.WhenEntryContext entry : entries) {
			List<KotlinParser.WhenConditionContext> conditions =
					entry.whenCondition();
			if (conditions == null || conditions.isEmpty()) {
				continue;
			}

			KotlinParser.ControlStructureBodyContext body =
					entry.controlStructureBody();
			if (body == null || body.getStart() == null
					|| body.getStop() == null) {
				continue;
			}

			int bodyStart = body.getStart().getStartIndex();
			int bodyEnd = body.getStop().getStopIndex();
			if (bodyStart < rangeStart || bodyEnd > rangeEnd) {
				continue;
			}

			// Check each condition for type tests
			for (KotlinParser.WhenConditionContext cond : conditions) {
				KotlinParser.TypeTestContext typeTest =
						cond.typeTest();
				if (typeTest == null) {
					continue;
				}

				KotlinParser.IsOperatorContext isOp =
						typeTest.isOperator();
				if (isOp == null || isOp.IS() == null) {
					// Only positive is checks narrow in when branches
					continue;
				}

				KotlinParser.TypeContext typeCtx = typeTest.type();
				if (typeCtx == null) {
					continue;
				}

				String typeName = typeCtx.getText();
				scopes.add(new SmartCastScope(bodyStart, bodyEnd,
						subjectVar, typeName, false));
			}
		}
	}

	/**
	 * Extracts the variable name from a when subject. Handles both
	 * {@code when (x)} and {@code when (val y = expr)}.
	 */
	private static String extractWhenSubjectVariable(
			KotlinParser.WhenExpressionContext ctx) {
		KotlinParser.WhenSubjectContext subject = ctx.whenSubject();
		if (subject == null) {
			return null;
		}

		// when (val y = expr) — variable declaration form
		if (subject.variableDeclaration() != null
				&& subject.variableDeclaration()
						.simpleIdentifier() != null) {
			return subject.variableDeclaration().simpleIdentifier()
					.getText();
		}

		// when (x) — simple expression form
		KotlinParser.ExpressionContext expr = subject.expression();
		if (expr == null) {
			return null;
		}
		String text = expr.getText();
		// Only narrow for simple identifiers (no dots, calls, etc.)
		if (SIMPLE_IDENTIFIER.matcher(text).matches()) {
			return text;
		}
		return null;
	}

	/**
	 * Extracts a smart cast scope from an unsafe {@code as} cast
	 * statement. {@code obj as Foo} narrows {@code obj} to {@code Foo}
	 * from the cast to the end of the enclosing block.
	 */
	private static void extractFromAsCast(
			KotlinParser.AsExpressionContext ctx,
			int rangeStart, int rangeEnd,
			List<SmartCastScope> scopes) {
		List<KotlinParser.AsOperatorContext> ops = ctx.asOperator();
		if (ops == null || ops.isEmpty()) {
			return;
		}
		// Only unsafe as narrows (not as?)
		KotlinParser.AsOperatorContext asOp = ops.get(0);
		if (asOp.AS() == null) {
			return;
		}
		List<KotlinParser.TypeContext> types = ctx.type();
		if (types == null || types.isEmpty()) {
			return;
		}
		// LHS must be a simple identifier
		KotlinParser.PrefixUnaryExpressionContext prefix =
				ctx.prefixUnaryExpression();
		if (prefix == null) {
			return;
		}
		String varName = prefix.getText();
		if (!SIMPLE_IDENTIFIER.matcher(varName).matches()) {
			return;
		}
		String typeName = types.get(0).getText();
		// Narrowing scope: from the as-cast to the end of the
		// enclosing block (approximated by rangeEnd)
		int castStart = ctx.getStart().getStartIndex();
		if (castStart < rangeStart || castStart > rangeEnd) {
			return;
		}
		scopes.add(new SmartCastScope(castStart, rangeEnd,
				varName, typeName, false));
	}

	/**
	 * Extracts a smart cast scope from a simple assignment whose RHS
	 * is a constructor call. {@code x = Foo()} narrows {@code x} to
	 * {@code Foo} from the assignment to the end of the enclosing
	 * function body.
	 */
	private static void extractFromAssignment(
			KotlinParser.AssignmentContext ctx,
			int rangeStart, int rangeEnd,
			List<SmartCastScope> scopes) {
		// Only simple '=' assignments (not +=, -=, etc.)
		if (ctx.ASSIGNMENT() == null) {
			return;
		}
		// LHS must be a simple identifier
		KotlinParser.DirectlyAssignableExpressionContext lhs =
				ctx.directlyAssignableExpression();
		if (lhs == null || lhs.simpleIdentifier() == null) {
			return;
		}
		String varName = lhs.simpleIdentifier().getText();

		// RHS must be a constructor call: TypeName(...)
		KotlinParser.ExpressionContext rhs = ctx.expression();
		if (rhs == null) {
			return;
		}
		String typeName = extractConstructorCallType(rhs);
		if (typeName == null) {
			return;
		}

		int assignStart = ctx.getStart().getStartIndex();
		if (assignStart < rangeStart || assignStart > rangeEnd) {
			return;
		}
		scopes.add(new SmartCastScope(assignStart, rangeEnd,
				varName, typeName, false));
	}

	/**
	 * Extracts the type name from a constructor call expression.
	 * Returns the type name if the expression is {@code TypeName(...)},
	 * or {@code null} otherwise.
	 */
	private static String extractConstructorCallType(
			KotlinParser.ExpressionContext expr) {
		// Walk down the expression hierarchy to the postfix unary
		// expression: TypeName(args)
		// The structure is: expression → disjunction → ... →
		// postfixUnaryExpression where primary is simpleIdentifier
		// and first suffix is callSuffix
		String text = expr.getText();
		// Quick check: must contain '(' to be a constructor call
		int parenIdx = text.indexOf('(');
		if (parenIdx <= 0) {
			return null;
		}
		String candidate = text.substring(0, parenIdx);
		// Must be an uppercase-starting identifier (constructor)
		if (!candidate.isEmpty()
				&& Character.isUpperCase(candidate.charAt(0))
				&& SIMPLE_IDENTIFIER.matcher(candidate).matches()) {
			return candidate;
		}
		return null;
	}

	// ---- Condition narrowing extraction ----

	/**
	 * Intermediate result from parsing a condition expression.
	 */
	private static class SmartCastNarrowing {
		final String variableName;
		final String typeName;
		final boolean nullNarrowing;
		final boolean negated;

		SmartCastNarrowing(String variableName, String typeName,
				boolean nullNarrowing, boolean negated) {
			this.variableName = variableName;
			this.typeName = typeName;
			this.nullNarrowing = nullNarrowing;
			this.negated = negated;
		}
	}

	/**
	 * Extracts narrowings from an if condition. Walks through
	 * disjunction/conjunction structure to handle compound conditions.
	 */
	private static List<SmartCastNarrowing>
			extractNarrowingsFromCondition(
					KotlinParser.ExpressionContext condition) {
		List<SmartCastNarrowing> result = new ArrayList<>();
		collectNarrowingsFromTree(condition, result);
		return result;
	}

	/**
	 * Recursively walks the expression tree to find is-checks and
	 * null-checks. For conjunctions (&&), all narrowings apply to
	 * the then-branch. Disjunctions (||) are not handled (would need
	 * flow-sensitive analysis).
	 */
	private static void collectNarrowingsFromTree(ParseTree node,
			List<SmartCastNarrowing> result) {
		// Check for is/!is in InfixOperationContext
		if (node instanceof KotlinParser.InfixOperationContext ctx) {
			SmartCastNarrowing n = extractIsCheckNarrowing(ctx);
			if (n != null) {
				result.add(n);
				return;
			}
		}

		// Check for != null / == null in EqualityContext
		if (node instanceof KotlinParser.EqualityContext ctx) {
			SmartCastNarrowing n = extractNullCheckNarrowing(ctx);
			if (n != null) {
				result.add(n);
				return;
			}
		}

		// Recurse into children (handles conjunctions transparently)
		for (int i = 0; i < node.getChildCount(); i++) {
			collectNarrowingsFromTree(node.getChild(i), result);
		}
	}

	/**
	 * Extracts an is-check narrowing from an infix operation context.
	 * Matches {@code x is Foo} and {@code x !is Foo}.
	 */
	private static SmartCastNarrowing extractIsCheckNarrowing(
			KotlinParser.InfixOperationContext ctx) {
		List<KotlinParser.IsOperatorContext> isOps = ctx.isOperator();
		if (isOps == null || isOps.isEmpty()) {
			return null;
		}

		List<KotlinParser.TypeContext> types = ctx.type();
		if (types == null || types.isEmpty()) {
			return null;
		}

		// Get the LHS expression (the variable being checked)
		List<KotlinParser.ElvisExpressionContext> elvisExprs =
				ctx.elvisExpression();
		if (elvisExprs == null || elvisExprs.isEmpty()) {
			return null;
		}

		String varName = elvisExprs.get(0).getText();
		// Only narrow for simple identifiers
		if (!SIMPLE_IDENTIFIER.matcher(varName).matches()) {
			return null;
		}

		KotlinParser.IsOperatorContext isOp = isOps.get(0);
		boolean negated = isOp.NOT_IS() != null;
		String typeName = types.get(0).getText();

		return new SmartCastNarrowing(varName, typeName, false,
				negated);
	}

	/**
	 * Extracts a null-check narrowing from an equality context.
	 * Matches {@code x != null} and {@code x == null}.
	 */
	private static SmartCastNarrowing extractNullCheckNarrowing(
			KotlinParser.EqualityContext ctx) {
		List<KotlinParser.EqualityOperatorContext> ops =
				ctx.equalityOperator();
		if (ops == null || ops.isEmpty()) {
			return null;
		}

		List<KotlinParser.ComparisonContext> comparisons =
				ctx.comparison();
		if (comparisons == null || comparisons.size() < 2) {
			return null;
		}

		KotlinParser.EqualityOperatorContext op = ops.get(0);
		String lhs = comparisons.get(0).getText();
		String rhs = comparisons.get(1).getText();

		// Check for x != null or x == null
		boolean lhsIsNull = "null".equals(lhs);
		boolean rhsIsNull = "null".equals(rhs);
		if (!lhsIsNull && !rhsIsNull) {
			return null;
		}

		String varName = lhsIsNull ? rhs : lhs;
		// Only narrow for simple identifiers
		if (!SIMPLE_IDENTIFIER.matcher(varName).matches()) {
			return null;
		}

		// != null narrows in then-branch (negated=false)
		// == null narrows in else-branch (negated=true for not-null)
		boolean isNotEqual = op.EXCL_EQ() != null
				|| op.EXCL_EQEQ() != null;
		boolean negated = !isNotEqual;

		return new SmartCastNarrowing(varName, null, true, negated);
	}
}
