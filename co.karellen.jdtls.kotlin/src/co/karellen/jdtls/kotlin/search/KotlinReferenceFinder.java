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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import co.karellen.jdtls.kotlin.parser.KotlinParser;
import co.karellen.jdtls.kotlin.parser.KotlinParserBaseVisitor;

/**
 * ANTLR visitor that finds source positions of references to a given
 * symbol name in a Kotlin parse tree. Used by
 * {@link KotlinSearchParticipant#locateMatches} for REFERENCES patterns.
 *
 * @author Arcadiy Ivanov
 */
public class KotlinReferenceFinder extends KotlinParserBaseVisitor<Void> {

	/**
	 * A matched reference site with source position.
	 */
	public static class ReferenceMatch {

		private final int offset;
		private final int length;
		private final Kind kind;
		private final ReceiverContext receiver;

		public enum Kind {
			TYPE_REF, METHOD_REF, CONSTRUCTOR_REF, FIELD_REF
		}

		public ReferenceMatch(int offset, int length, Kind kind) {
			this(offset, length, kind, ReceiverContext.NONE);
		}

		public ReferenceMatch(int offset, int length, Kind kind,
				String receiverName,
				KotlinParser.PostfixUnaryExpressionContext
						receiverExprCtx,
				int navSuffixIndex) {
			this(offset, length, kind, new ReceiverContext(
					receiverName, receiverExprCtx, navSuffixIndex));
		}

		public ReferenceMatch(int offset, int length, Kind kind,
				ReceiverContext receiver) {
			this.offset = offset;
			this.length = length;
			this.kind = kind;
			this.receiver = receiver;
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

		public String getReceiverName() {
			return receiver.receiverName();
		}

		public KotlinParser.PostfixUnaryExpressionContext
				getReceiverExprCtx() {
			return receiver.exprCtx();
		}

		public int getNavSuffixIndex() {
			return receiver.navSuffixIndex();
		}

		/**
		 * Returns {@code true} if this reference has a receiver
		 * expression (either as a simple name or a complex expression
		 * context).
		 */
		public boolean hasReceiver() {
			return receiver.receiverName() != null
					|| receiver.exprCtx() != null;
		}
	}

	private final String targetName;
	private final boolean matchTypes;
	private final boolean matchMethods;
	private final boolean matchFields;
	private final Set<String> aliasNames;
	private final List<ReferenceMatch> matches = new ArrayList<>();

	/**
	 * Creates a reference finder for the given symbol name.
	 * The comparison is case-insensitive because JDT patterns
	 * store names in lowercase.
	 *
	 * @param targetName   the simple name to search for
	 * @param matchTypes   whether to find type references
	 * @param matchMethods whether to find method references
	 */
	public KotlinReferenceFinder(String targetName, boolean matchTypes,
			boolean matchMethods) {
		this(targetName, matchTypes, matchMethods, false, null);
	}

	/**
	 * Creates a reference finder for the given symbol name.
	 *
	 * @param targetName   the simple name to search for
	 * @param matchTypes   whether to find type references
	 * @param matchMethods whether to find method references
	 * @param matchFields  whether to find field/property references
	 */
	public KotlinReferenceFinder(String targetName, boolean matchTypes,
			boolean matchMethods, boolean matchFields) {
		this(targetName, matchTypes, matchMethods, matchFields, null);
	}

	/**
	 * Creates a reference finder for the given symbol name, with
	 * import alias awareness.
	 *
	 * @param targetName   the simple name to search for
	 * @param matchTypes   whether to find type references
	 * @param matchMethods whether to find method references
	 * @param matchFields  whether to find field/property references
	 * @param imports      file imports for alias resolution, or null
	 */
	public KotlinReferenceFinder(String targetName, boolean matchTypes,
			boolean matchMethods, boolean matchFields,
			List<KotlinFileModel.ImportEntry> imports) {
		this.targetName = targetName;
		this.matchTypes = matchTypes;
		this.matchMethods = matchMethods;
		this.matchFields = matchFields;
		this.aliasNames = buildAliasNames(targetName, imports);
	}

	private static Set<String> buildAliasNames(String targetName,
			List<KotlinFileModel.ImportEntry> imports) {
		if (imports == null || imports.isEmpty()) {
			return Collections.emptySet();
		}
		Set<String> aliases = new HashSet<>();
		for (KotlinFileModel.ImportEntry entry : imports) {
			if (entry.getAlias() != null && !entry.isStar()) {
				String fqn = entry.getFqn();
				int lastDot = fqn.lastIndexOf('.');
				String originalSimple = lastDot >= 0
						? fqn.substring(lastDot + 1) : fqn;
				if (originalSimple.equalsIgnoreCase(targetName)) {
					aliases.add(entry.getAlias().toLowerCase());
				}
			}
		}
		return aliases;
	}

	private boolean nameMatches(String actual) {
		if (targetName.equalsIgnoreCase(actual)) {
			return true;
		}
		return !aliasNames.isEmpty()
				&& aliasNames.contains(actual.toLowerCase());
	}

	/**
	 * Finds all reference sites in the given parse tree.
	 */
	public List<ReferenceMatch> find(ParseTree tree) {
		matches.clear();
		if (tree != null) {
			visit(tree);
		}
		return new ArrayList<>(matches);
	}

	// ---- Type references ----

	@Override
	public Void visitSimpleUserType(
			KotlinParser.SimpleUserTypeContext ctx) {
		if (matchTypes) {
			KotlinParser.SimpleIdentifierContext id = ctx.simpleIdentifier();
			if (id != null && nameMatches(id.getText())) {
				matches.add(new ReferenceMatch(
						id.getStart().getStartIndex(),
						id.getText().length(),
						ReferenceMatch.Kind.TYPE_REF));
			}
		}
		return visitChildren(ctx);
	}

	// ---- Import statements ----

	@Override
	public Void visitImportHeader(
			KotlinParser.ImportHeaderContext ctx) {
		if (matchTypes) {
			KotlinParser.IdentifierContext identifier = ctx.identifier();
			if (identifier != null) {
				List<KotlinParser.SimpleIdentifierContext> parts =
						identifier.simpleIdentifier();
				if (parts != null && !parts.isEmpty()) {
					KotlinParser.SimpleIdentifierContext last =
							parts.get(parts.size() - 1);
					if (last != null && nameMatches(last.getText())) {
						matches.add(new ReferenceMatch(
								last.getStart().getStartIndex(),
								last.getText().length(),
								ReferenceMatch.Kind.TYPE_REF));
					}
				}
			}
		}
		return null;
	}

	// ---- Constructor invocations in delegation specifiers ----

	@Override
	public Void visitConstructorInvocation(
			KotlinParser.ConstructorInvocationContext ctx) {
		if (matchTypes) {
			KotlinParser.UserTypeContext userType = ctx.userType();
			if (userType != null) {
				List<KotlinParser.SimpleUserTypeContext> parts = userType
						.simpleUserType();
				if (parts != null && !parts.isEmpty()) {
					KotlinParser.SimpleUserTypeContext last = parts
							.get(parts.size() - 1);
					KotlinParser.SimpleIdentifierContext id = last
							.simpleIdentifier();
					if (id != null && nameMatches(id.getText())) {
						matches.add(new ReferenceMatch(
								id.getStart().getStartIndex(),
								id.getText().length(),
								ReferenceMatch.Kind.CONSTRUCTOR_REF));
					}
				}
			}
		}
		return visitChildren(ctx);
	}

	// ---- Method/function calls ----

	@Override
	public Void visitPostfixUnaryExpression(
			KotlinParser.PostfixUnaryExpressionContext ctx) {
		if (!matchMethods && !matchTypes && !matchFields) {
			return visitChildren(ctx);
		}

		List<KotlinParser.PostfixUnarySuffixContext> suffixes = ctx
				.postfixUnarySuffix();
		if (suffixes == null || suffixes.isEmpty()) {
			return visitChildren(ctx);
		}

		// Match primary expression as type receiver: Type.member,
		// Type.method(), Type::ref
		if (matchTypes && !suffixes.isEmpty()
				&& suffixes.get(0).navigationSuffix() != null) {
			KotlinParser.PrimaryExpressionContext primary = ctx
					.primaryExpression();
			if (primary != null
					&& primary.simpleIdentifier() != null) {
				KotlinParser.SimpleIdentifierContext simpleId =
						primary.simpleIdentifier();
				if (nameMatches(simpleId.getText())) {
					matches.add(new ReferenceMatch(
							simpleId.getStart().getStartIndex(),
							simpleId.getText().length(),
							ReferenceMatch.Kind.TYPE_REF));
				}
			}
		}

		for (int i = 0; i < suffixes.size(); i++) {
			KotlinParser.PostfixUnarySuffixContext suffix = suffixes.get(i);

			KotlinParser.NavigationSuffixContext navSuffix = suffix
					.navigationSuffix();
			if (navSuffix != null) {
				KotlinParser.SimpleIdentifierContext navId = navSuffix
						.simpleIdentifier();
				if (navId != null && nameMatches(navId.getText())) {
					boolean isColonColon =
							navSuffix.memberAccessOperator() != null
							&& navSuffix.memberAccessOperator()
									.COLONCOLON() != null;
					boolean isCall = (i + 1 < suffixes.size())
							&& suffixes.get(i + 1).callSuffix() != null;

					String receiverName = extractReceiverName(
							ctx, suffixes, i);

					if (isColonColon && matchMethods) {
						// :: navigation = callable reference
						matches.add(new ReferenceMatch(
								navId.getStart().getStartIndex(),
								navId.getText().length(),
								ReferenceMatch.Kind.METHOD_REF,
								receiverName, ctx, i));
					} else if (isCall && matchMethods) {
						matches.add(new ReferenceMatch(
								navId.getStart().getStartIndex(),
								navId.getText().length(),
								ReferenceMatch.Kind.METHOD_REF,
								receiverName, ctx, i));
					} else if (!isCall && !isColonColon
							&& matchFields) {
						matches.add(new ReferenceMatch(
								navId.getStart().getStartIndex(),
								navId.getText().length(),
								ReferenceMatch.Kind.FIELD_REF,
								receiverName, ctx, i));
					}
				}
			}

			// Direct call on primary expression: func(args), Type(args),
			// or Type<Arg>(args) where typeArguments precede callSuffix
			KotlinParser.CallSuffixContext callSuffix = suffix.callSuffix();
			if (callSuffix != null
					&& allPrecedingSuffixesAreTypeArguments(suffixes, i)) {
				KotlinParser.PrimaryExpressionContext primary = ctx
						.primaryExpression();
				if (primary != null) {
					KotlinParser.SimpleIdentifierContext simpleId = primary
							.simpleIdentifier();
					if (simpleId != null
							&& nameMatches(simpleId.getText())) {
						// Heuristic: uppercase initial = constructor call.
						// Misclassifies factory functions like Color() but
						// these are rare and the index pre-filters documents.
						boolean isConstructor = Character
								.isUpperCase(targetName.charAt(0));
						if (isConstructor && matchTypes) {
							matches.add(new ReferenceMatch(
									simpleId.getStart().getStartIndex(),
									simpleId.getText().length(),
									ReferenceMatch.Kind.CONSTRUCTOR_REF));
						} else if (matchMethods) {
							matches.add(new ReferenceMatch(
									simpleId.getStart().getStartIndex(),
									simpleId.getText().length(),
									ReferenceMatch.Kind.METHOD_REF));
						}
					}
				}
			}
		}

		return visitChildren(ctx);
	}

	/**
	 * Extracts the receiver's simple name for a navigation suffix at the
	 * given index. Returns the primary expression's identifier if the
	 * navigation is on it directly, or {@code null} for complex receivers.
	 */
	private String extractReceiverName(
			KotlinParser.PostfixUnaryExpressionContext ctx,
			List<KotlinParser.PostfixUnarySuffixContext> suffixes,
			int navIndex) {
		if (navIndex == 0) {
			return ReceiverContext.extractPrimaryReceiverName(
					ctx.primaryExpression());
		} else if (navIndex == 1) {
			// Second suffix: check if first suffix was a call on the primary
			// (e.g., Foo().method() — receiver type is Foo)
			KotlinParser.PostfixUnarySuffixContext prev = suffixes
					.get(0);
			if (prev.callSuffix() != null) {
				KotlinParser.PrimaryExpressionContext primary = ctx
						.primaryExpression();
				if (primary != null
						&& primary.simpleIdentifier() != null) {
					String callee = primary.simpleIdentifier().getText();
					if (Character.isUpperCase(callee.charAt(0))) {
						return callee;
					}
				}
			}
		}
		return null;
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

	// ---- String template simple refs ($variable) ----

	@Override
	public Void visitLineStringContent(
			KotlinParser.LineStringContentContext ctx) {
		if (matchFields && ctx.LineStrRef() != null) {
			matchStringTemplateRef(ctx.LineStrRef());
		}
		return visitChildren(ctx);
	}

	@Override
	public Void visitMultiLineStringContent(
			KotlinParser.MultiLineStringContentContext ctx) {
		if (matchFields && ctx.MultiLineStrRef() != null) {
			matchStringTemplateRef(ctx.MultiLineStrRef());
		}
		return visitChildren(ctx);
	}

	private void matchStringTemplateRef(
			TerminalNode refNode) {
		String text = refNode.getText();
		if (text != null && text.length() >= 2) {
			String name = text.substring(1); // strip $
			if (nameMatches(name)) {
				matches.add(new ReferenceMatch(
						refNode.getSymbol().getStartIndex() + 1,
						name.length(),
						ReferenceMatch.Kind.FIELD_REF));
			}
		}
	}

	// ---- Operator syntax → function name mapping ----

	@Override
	public Void visitPostfixUnarySuffix(
			KotlinParser.PostfixUnarySuffixContext ctx) {
		if (matchMethods && ctx.indexingSuffix() != null
				&& "get".equalsIgnoreCase(targetName)) {
			KotlinParser.IndexingSuffixContext idxCtx =
					ctx.indexingSuffix();
			if (idxCtx.getStart() != null) {
				matches.add(new ReferenceMatch(
						idxCtx.getStart().getStartIndex(),
						idxCtx.getStop().getStopIndex()
								- idxCtx.getStart().getStartIndex()
								+ 1,
						ReferenceMatch.Kind.METHOD_REF));
			}
		}
		return visitChildren(ctx);
	}

	@Override
	public Void visitAdditiveExpression(
			KotlinParser.AdditiveExpressionContext ctx) {
		if (matchMethods && ctx.additiveOperator() != null) {
			for (KotlinParser.AdditiveOperatorContext op :
					ctx.additiveOperator()) {
				String fnName = op.ADD() != null ? "plus" : "minus";
				if (fnName.equalsIgnoreCase(targetName)) {
					matches.add(new ReferenceMatch(
							op.getStart().getStartIndex(),
							op.getText().length(),
							ReferenceMatch.Kind.METHOD_REF));
				}
			}
		}
		return visitChildren(ctx);
	}

	@Override
	public Void visitMultiplicativeExpression(
			KotlinParser.MultiplicativeExpressionContext ctx) {
		if (matchMethods
				&& ctx.multiplicativeOperator() != null) {
			for (KotlinParser.MultiplicativeOperatorContext op :
					ctx.multiplicativeOperator()) {
				String fnName;
				if (op.MULT() != null) {
					fnName = "times";
				} else if (op.DIV() != null) {
					fnName = "div";
				} else {
					fnName = "rem";
				}
				if (fnName.equalsIgnoreCase(targetName)) {
					matches.add(new ReferenceMatch(
							op.getStart().getStartIndex(),
							op.getText().length(),
							ReferenceMatch.Kind.METHOD_REF));
				}
			}
		}
		return visitChildren(ctx);
	}

	@Override
	public Void visitRangeExpression(
			KotlinParser.RangeExpressionContext ctx) {
		if (matchMethods) {
			if ("rangeto".equalsIgnoreCase(targetName)
					&& ctx.RANGE() != null) {
				for (var range : ctx.RANGE()) {
					matches.add(new ReferenceMatch(
							range.getSymbol().getStartIndex(),
							range.getText().length(),
							ReferenceMatch.Kind.METHOD_REF));
				}
			}
			if ("rangeuntil".equalsIgnoreCase(targetName)
					&& ctx.RANGE_UNTIL() != null) {
				for (var range : ctx.RANGE_UNTIL()) {
					matches.add(new ReferenceMatch(
							range.getSymbol().getStartIndex(),
							range.getText().length(),
							ReferenceMatch.Kind.METHOD_REF));
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
		if (id != null && nameMatches(id.getText())) {
			if (matchMethods) {
				matches.add(new ReferenceMatch(
						id.getStart().getStartIndex(),
						id.getText().length(),
						ReferenceMatch.Kind.METHOD_REF));
			}
			if (matchTypes
					&& Character.isUpperCase(
							targetName.charAt(0))) {
				matches.add(new ReferenceMatch(
						id.getStart().getStartIndex(),
						id.getText().length(),
						ReferenceMatch.Kind.TYPE_REF));
			}
		}
		return visitChildren(ctx);
	}

	// ---- Infix function calls (a to b, x shl 1) ----

	@Override
	public Void visitInfixFunctionCall(
			KotlinParser.InfixFunctionCallContext ctx) {
		if (matchMethods) {
			List<KotlinParser.SimpleIdentifierContext> ids =
					ctx.simpleIdentifier();
			if (ids != null) {
				for (KotlinParser.SimpleIdentifierContext id : ids) {
					if (nameMatches(id.getText())) {
						matches.add(new ReferenceMatch(
								id.getStart().getStartIndex(),
								id.getText().length(),
								ReferenceMatch.Kind.METHOD_REF));
					}
				}
			}
		}
		return visitChildren(ctx);
	}

	// Type references in as/is expressions are handled by visitSimpleUserType
	// via the default visitChildren traversal — no explicit override needed.
}
