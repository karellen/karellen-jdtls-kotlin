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
import java.util.List;

import org.antlr.v4.runtime.tree.ParseTree;

import co.karellen.jdtls.kotlin.parser.KotlinParser;
import co.karellen.jdtls.kotlin.parser.KotlinParserBaseVisitor;

/**
 * ANTLR visitor that finds all call sites (method invocations and
 * constructor calls) within a specified source range in a Kotlin parse tree.
 * Used by {@link KotlinSearchParticipant#locateCallees} for outgoing
 * call hierarchy.
 *
 * @author Arcadiy Ivanov
 */
public class KotlinCalleeFinder extends KotlinParserBaseVisitor<Void> {

	/**
	 * A call site found in the source.
	 */
	public static class CalleeMatch {

		public enum Kind {
			METHOD_CALL, CONSTRUCTOR_CALL
		}

		private final String name;
		private final int offset;
		private final int length;
		private final Kind kind;

		public CalleeMatch(String name, int offset, int length, Kind kind) {
			this.name = name;
			this.offset = offset;
			this.length = length;
			this.kind = kind;
		}

		public String getName() {
			return name;
		}

		public int getOffset() {
			return offset;
		}

		public int getLength() {
			return length;
		}

		public Kind getKind() {
			return kind;
		}
	}

	private final int rangeStart;
	private final int rangeEnd;
	private final List<CalleeMatch> matches = new ArrayList<>();

	/**
	 * Creates a callee finder that only reports call sites within the
	 * given source range (inclusive on both ends).
	 */
	public KotlinCalleeFinder(int rangeStart, int rangeEnd) {
		this.rangeStart = rangeStart;
		this.rangeEnd = rangeEnd;
	}

	private boolean inRange(int offset) {
		return offset >= rangeStart && offset <= rangeEnd;
	}

	/**
	 * Finds all call sites in the given parse tree within this finder's
	 * source range.
	 */
	public List<CalleeMatch> find(ParseTree tree) {
		matches.clear();
		if (tree != null) {
			visit(tree);
		}
		return new ArrayList<>(matches);
	}

	// ---- Method/function calls ----

	@Override
	public Void visitPostfixUnaryExpression(
			KotlinParser.PostfixUnaryExpressionContext ctx) {
		List<KotlinParser.PostfixUnarySuffixContext> suffixes = ctx
				.postfixUnarySuffix();
		if (suffixes == null || suffixes.isEmpty()) {
			return visitChildren(ctx);
		}

		for (int i = 0; i < suffixes.size(); i++) {
			KotlinParser.PostfixUnarySuffixContext suffix = suffixes.get(i);

			// Navigation suffix followed by call: .method(args)
			KotlinParser.NavigationSuffixContext navSuffix = suffix
					.navigationSuffix();
			if (navSuffix != null) {
				KotlinParser.SimpleIdentifierContext navId = navSuffix
						.simpleIdentifier();
				if (navId != null) {
					boolean isColonColon =
							navSuffix.memberAccessOperator() != null
							&& navSuffix.memberAccessOperator()
									.COLONCOLON() != null;
					boolean isCall = (i + 1 < suffixes.size())
							&& suffixes.get(i + 1).callSuffix() != null;
					if ((isCall || isColonColon)
							&& inRange(navId.getStart()
									.getStartIndex())) {
						matches.add(new CalleeMatch(
								navId.getText(),
								navId.getStart().getStartIndex(),
								navId.getText().length(),
								CalleeMatch.Kind.METHOD_CALL));
					}
				}
			}

			// Direct call on primary expression: func(args), Type(args),
			// or Type<Arg>(args) where typeArguments precede callSuffix
			KotlinParser.CallSuffixContext callSuffix = suffix.callSuffix();
			if (callSuffix != null && allPrecedingSuffixesAreTypeArguments(suffixes, i)) {
				KotlinParser.PrimaryExpressionContext primary = ctx
						.primaryExpression();
				if (primary != null) {
					KotlinParser.SimpleIdentifierContext simpleId = primary
							.simpleIdentifier();
					if (simpleId != null
							&& inRange(simpleId.getStart()
									.getStartIndex())) {
						boolean isConstructor = Character
								.isUpperCase(simpleId.getText().charAt(0));
						matches.add(new CalleeMatch(
								simpleId.getText(),
								simpleId.getStart().getStartIndex(),
								simpleId.getText().length(),
								isConstructor
										? CalleeMatch.Kind.CONSTRUCTOR_CALL
										: CalleeMatch.Kind.METHOD_CALL));
					}
				}
			}
		}

		return visitChildren(ctx);
	}

	// ---- Callable references (::name, Type::name) ----

	@Override
	public Void visitCallableReference(
			KotlinParser.CallableReferenceContext ctx) {
		KotlinParser.SimpleIdentifierContext id =
				ctx.simpleIdentifier();
		if (id != null
				&& inRange(id.getStart().getStartIndex())) {
			String name = id.getText();
			// Uppercase = Type::class or constructor ref
			boolean isConstructor = Character.isUpperCase(
					name.charAt(0));
			matches.add(new CalleeMatch(name,
					id.getStart().getStartIndex(),
					name.length(),
					isConstructor
							? CalleeMatch.Kind.CONSTRUCTOR_CALL
							: CalleeMatch.Kind.METHOD_CALL));
		}
		return visitChildren(ctx);
	}

	// ---- Infix function calls (a to b, x shl 1) ----

	@Override
	public Void visitInfixFunctionCall(
			KotlinParser.InfixFunctionCallContext ctx) {
		List<KotlinParser.SimpleIdentifierContext> ids =
				ctx.simpleIdentifier();
		if (ids != null) {
			for (KotlinParser.SimpleIdentifierContext id : ids) {
				if (inRange(id.getStart().getStartIndex())) {
					matches.add(new CalleeMatch(id.getText(),
							id.getStart().getStartIndex(),
							id.getText().length(),
							CalleeMatch.Kind.METHOD_CALL));
				}
			}
		}
		return visitChildren(ctx);
	}

	// ---- Operator calls ----

	@Override
	public Void visitPostfixUnarySuffix(
			KotlinParser.PostfixUnarySuffixContext ctx) {
		if (ctx.indexingSuffix() != null) {
			KotlinParser.IndexingSuffixContext idxCtx =
					ctx.indexingSuffix();
			if (idxCtx.getStart() != null
					&& inRange(idxCtx.getStart().getStartIndex())) {
				matches.add(new CalleeMatch("get",
						idxCtx.getStart().getStartIndex(),
						1, CalleeMatch.Kind.METHOD_CALL));
			}
		}
		return visitChildren(ctx);
	}

	private boolean allPrecedingSuffixesAreTypeArguments(
			List<KotlinParser.PostfixUnarySuffixContext> suffixes, int index) {
		for (int j = 0; j < index; j++) {
			if (suffixes.get(j).typeArguments() == null) {
				return false;
			}
		}
		return true;
	}

	// ---- Constructor invocations in delegation specifiers ----

	@Override
	public Void visitConstructorInvocation(
			KotlinParser.ConstructorInvocationContext ctx) {
		KotlinParser.UserTypeContext userType = ctx.userType();
		if (userType != null) {
			List<KotlinParser.SimpleUserTypeContext> parts = userType
					.simpleUserType();
			if (parts != null && !parts.isEmpty()) {
				KotlinParser.SimpleUserTypeContext last = parts
						.get(parts.size() - 1);
				KotlinParser.SimpleIdentifierContext id = last
						.simpleIdentifier();
				if (id != null
						&& inRange(id.getStart().getStartIndex())) {
					matches.add(new CalleeMatch(
							id.getText(),
							id.getStart().getStartIndex(),
							id.getText().length(),
							CalleeMatch.Kind.CONSTRUCTOR_CALL));
				}
			}
		}
		return visitChildren(ctx);
	}
}
