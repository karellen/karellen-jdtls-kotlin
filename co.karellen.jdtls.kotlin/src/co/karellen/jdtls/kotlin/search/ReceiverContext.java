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

import co.karellen.jdtls.kotlin.parser.KotlinParser;

/**
 * Captures the receiver context for a call site: the simple receiver name
 * (if available), the full postfix unary expression context, and the index
 * of the navigation suffix within that expression. Used by both
 * {@link KotlinReferenceFinder.ReferenceMatch} and
 * {@link LambdaScopeExtractor.LambdaScope}.
 *
 * @param receiverName    the simple name of the receiver, or {@code null}
 * @param exprCtx         the ANTLR expression context, or {@code null}
 * @param navSuffixIndex  the index of the navigation suffix, or -1
 *
 * @author Arcadiy Ivanov
 */
public record ReceiverContext(
		String receiverName,
		KotlinParser.PostfixUnaryExpressionContext exprCtx,
		int navSuffixIndex) {

	/** A context with no receiver information. */
	public static final ReceiverContext NONE =
			new ReceiverContext(null, null, -1);

	/**
	 * Extracts the receiver's simple name from a primary expression.
	 * Returns the identifier text for simple identifiers, {@code "this"}
	 * for this-expressions, {@code "super"} for super-expressions, or
	 * {@code null} for complex expressions.
	 */
	public static String extractPrimaryReceiverName(
			KotlinParser.PrimaryExpressionContext primary) {
		if (primary == null) {
			return null;
		}
		if (primary.simpleIdentifier() != null) {
			return primary.simpleIdentifier().getText();
		}
		if (primary.thisExpression() != null) {
			return "this";
		}
		if (primary.superExpression() != null) {
			return "super";
		}
		return null;
	}
}
