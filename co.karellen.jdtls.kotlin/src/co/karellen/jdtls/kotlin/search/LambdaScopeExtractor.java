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
 * Extracts lambda scope information from a function body in the ANTLR
 * parse tree. For each lambda literal that is a trailing lambda argument
 * to a call expression (e.g., {@code svc.let { ... }}), records the
 * lambda's source range and the enclosing call's receiver expression
 * and function name. Used by the search participant to push
 * {@code it}/{@code this} bindings via {@link LambdaTypeResolver}
 * during receiver verification.
 *
 * @author Arcadiy Ivanov
 */
public class LambdaScopeExtractor {

	/**
	 * A lambda scope with its source range and enclosing call context.
	 */
	public static class LambdaScope implements ScopeRange {

		private final int startOffset;
		private final int endOffset;
		private final String functionName;
		private final ReceiverContext receiver;
		private final KotlinParser.LambdaLiteralContext lambdaCtx;

		public LambdaScope(int startOffset, int endOffset,
				String functionName, String receiverName,
				KotlinParser.PostfixUnaryExpressionContext callExprCtx,
				int callNavSuffixIndex,
				KotlinParser.LambdaLiteralContext lambdaCtx) {
			this.startOffset = startOffset;
			this.endOffset = endOffset;
			this.functionName = functionName;
			this.receiver = new ReceiverContext(receiverName,
					callExprCtx, callNavSuffixIndex);
			this.lambdaCtx = lambdaCtx;
		}

		@Override
		public int getStartOffset() {
			return startOffset;
		}

		@Override
		public int getEndOffset() {
			return endOffset;
		}

		public String getFunctionName() {
			return functionName;
		}

		public String getReceiverName() {
			return receiver.receiverName();
		}

		public KotlinParser.PostfixUnaryExpressionContext
				getCallExprCtx() {
			return receiver.exprCtx();
		}

		public int getCallNavSuffixIndex() {
			return receiver.navSuffixIndex();
		}

		public KotlinParser.LambdaLiteralContext getLambdaCtx() {
			return lambdaCtx;
		}
	}

	/**
	 * Extracts all lambda scopes from the given parse tree within
	 * the specified source range (typically a function body).
	 *
	 * @param parseTree   the ANTLR parse tree
	 * @param rangeStart  the start offset of the enclosing function
	 * @param rangeEnd    the end offset of the enclosing function
	 * @return list of lambda scopes, empty if none found
	 */
	public static List<LambdaScope> extract(ParseTree parseTree,
			int rangeStart, int rangeEnd) {
		if (parseTree == null) {
			return Collections.emptyList();
		}
		List<LambdaScope> scopes = new ArrayList<>();
		collectLambdaScopes(parseTree, rangeStart, rangeEnd, scopes);
		return scopes;
	}

	private static void collectLambdaScopes(ParseTree node,
			int rangeStart, int rangeEnd,
			List<LambdaScope> scopes) {
		if (node instanceof KotlinParser.PostfixUnaryExpressionContext ctx) {
			List<KotlinParser.PostfixUnarySuffixContext> suffixes =
					ctx.postfixUnarySuffix();
			if (suffixes != null) {
				for (int i = 0; i < suffixes.size(); i++) {
					KotlinParser.PostfixUnarySuffixContext suffix =
							suffixes.get(i);
					extractFromSuffix(ctx, suffixes, i, suffix,
							rangeStart, rangeEnd, scopes);
				}
			}
		}

		for (int i = 0; i < node.getChildCount(); i++) {
			collectLambdaScopes(node.getChild(i), rangeStart,
					rangeEnd, scopes);
		}
	}

	private static void extractFromSuffix(
			KotlinParser.PostfixUnaryExpressionContext ctx,
			List<KotlinParser.PostfixUnarySuffixContext> suffixes,
			int suffixIndex,
			KotlinParser.PostfixUnarySuffixContext suffix,
			int rangeStart, int rangeEnd,
			List<LambdaScope> scopes) {
		KotlinParser.CallSuffixContext callSuffix =
				suffix.callSuffix();
		if (callSuffix == null
				|| callSuffix.annotatedLambda() == null) {
			return;
		}

		KotlinParser.LambdaLiteralContext lambda =
				callSuffix.annotatedLambda().lambdaLiteral();
		if (lambda == null || lambda.getStart() == null
				|| lambda.getStop() == null) {
			return;
		}

		int lambdaStart = lambda.getStart().getStartIndex();
		int lambdaEnd = lambda.getStop().getStopIndex();

		if (lambdaStart < rangeStart || lambdaEnd > rangeEnd) {
			return;
		}

		String functionName = null;
		String receiverName = null;
		int callNavIndex = -1;

		if (suffixIndex > 0) {
			KotlinParser.PostfixUnarySuffixContext prev =
					suffixes.get(suffixIndex - 1);
			KotlinParser.NavigationSuffixContext navSuffix =
					prev.navigationSuffix();
			if (navSuffix != null
					&& navSuffix.simpleIdentifier() != null) {
				functionName = navSuffix.simpleIdentifier()
						.getText();
				callNavIndex = suffixIndex - 1;

				if (callNavIndex == 0) {
					receiverName = ReceiverContext
							.extractPrimaryReceiverName(
									ctx.primaryExpression());
				}
				// For deeper chains (callNavIndex > 0), receiverName
				// stays null — use ExpressionTypeResolver via
				// callExprCtx + callNavSuffixIndex
			}
		} else {
			KotlinParser.PrimaryExpressionContext primary =
					ctx.primaryExpression();
			if (primary != null
					&& primary.simpleIdentifier() != null) {
				functionName = primary.simpleIdentifier().getText();
			}
		}

		if (functionName != null) {
			scopes.add(new LambdaScope(lambdaStart, lambdaEnd,
					functionName, receiverName, ctx,
					callNavIndex, lambda));
		}
	}

	/**
	 * Finds the innermost lambda scope containing the given offset.
	 *
	 * @param scopes the lambda scopes to search
	 * @param offset the source offset to check
	 * @return the innermost enclosing lambda scope, or {@code null}
	 */
	public static LambdaScope findEnclosing(List<LambdaScope> scopes,
			int offset) {
		return ScopeRange.findEnclosing(scopes, offset);
	}

	/**
	 * Finds all lambda scopes containing the given offset, sorted
	 * from outermost to innermost.
	 *
	 * @param scopes the lambda scopes to search
	 * @param offset the source offset to check
	 * @return list of enclosing scopes outermost-first, empty if none
	 */
	public static List<LambdaScope> findAllEnclosing(
			List<LambdaScope> scopes, int offset) {
		return ScopeRange.findAllEnclosing(scopes, offset);
	}
}
