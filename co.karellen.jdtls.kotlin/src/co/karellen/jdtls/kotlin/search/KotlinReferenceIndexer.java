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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.tree.ParseTree;

import org.eclipse.jdt.core.search.SearchDocument;
import org.eclipse.jdt.internal.core.search.indexing.IIndexConstants;
import org.eclipse.jdt.internal.core.search.matching.MethodPattern;

import co.karellen.jdtls.kotlin.parser.KotlinParser;
import co.karellen.jdtls.kotlin.parser.KotlinParserBaseVisitor;

/**
 * ANTLR visitor that walks a Kotlin parse tree and emits index entries
 * for expression-level references: type references ({@code REF}),
 * method call sites ({@code METHOD_REF}), and constructor invocations
 * ({@code CONSTRUCTOR_REF}).
 *
 * @author Arcadiy Ivanov
 */
public class KotlinReferenceIndexer extends KotlinParserBaseVisitor<Void> {

	private final SearchDocument document;
	private final Set<String> indexedTypeRefs = new HashSet<>(); // lowercase keys for case-insensitive dedup
	private final Map<String, String> aliasToOriginal;
	private final Set<String> propertyNames = new HashSet<>();

	public KotlinReferenceIndexer(SearchDocument document) {
		this(document, null);
	}

	public KotlinReferenceIndexer(SearchDocument document,
			List<KotlinFileModel.ImportEntry> imports) {
		this.document = document;
		this.aliasToOriginal = buildAliasMap(imports);
	}

	private static Map<String, String> buildAliasMap(
			List<KotlinFileModel.ImportEntry> imports) {
		if (imports == null || imports.isEmpty()) {
			return Collections.emptyMap();
		}
		Map<String, String> map = new HashMap<>();
		for (KotlinFileModel.ImportEntry entry : imports) {
			if (entry.getAlias() != null && !entry.isStar()) {
				// alias -> original simple name
				String fqn = entry.getFqn();
				int lastDot = fqn.lastIndexOf('.');
				String originalSimple = lastDot >= 0
						? fqn.substring(lastDot + 1) : fqn;
				map.put(entry.getAlias(), originalSimple);
			}
		}
		return map;
	}

	/**
	 * Indexes all expression references in the given parse tree.
	 */
	public void index(ParseTree tree) {
		if (tree != null) {
			visit(tree);
		}
	}

	// ---- Type references in type positions ----

	@Override
	public Void visitUserType(KotlinParser.UserTypeContext ctx) {
		// Extract the simple name from the first (or only) segment
		List<KotlinParser.SimpleUserTypeContext> parts = ctx.simpleUserType();
		if (parts != null && !parts.isEmpty()) {
			// The last segment is the actual type name (for qualified refs)
			KotlinParser.SimpleUserTypeContext lastPart = parts
					.get(parts.size() - 1);
			KotlinParser.SimpleIdentifierContext lastId =
					lastPart.simpleIdentifier();
			if (lastId == null) {
				warnNullIdentifier("userType", ctx);
			} else {
				addTypeRef(lastId.getText());
			}

			// Also index each segment for qualified references
			if (parts.size() > 1) {
				for (KotlinParser.SimpleUserTypeContext part : parts) {
					KotlinParser.SimpleIdentifierContext partId =
							part.simpleIdentifier();
					if (partId != null) {
						addTypeRef(partId.getText());
					}
				}
			}
		}
		return visitChildren(ctx);
	}

	// ---- Import statements ----

	@Override
	public Void visitImportHeader(
			KotlinParser.ImportHeaderContext ctx) {
		KotlinParser.IdentifierContext identifier = ctx.identifier();
		if (identifier != null) {
			List<KotlinParser.SimpleIdentifierContext> parts =
					identifier.simpleIdentifier();
			if (parts != null && !parts.isEmpty()) {
				// Index the last segment (imported simple name) as REF
				KotlinParser.SimpleIdentifierContext last =
						parts.get(parts.size() - 1);
				if (last != null) {
					addTypeRef(last.getText());
				}
			}
		}
		// Don't visit children — import identifiers are not
		// expressions and shouldn't trigger other visitors
		return null;
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
				KotlinParser.SimpleIdentifierContext ctorId =
						parts.get(parts.size() - 1).simpleIdentifier();
				if (ctorId == null) {
					warnNullIdentifier("constructorInvocation",
							ctx);
					return visitChildren(ctx);
				}
				String typeName = ctorId.getText();
				// Count constructor arguments
				int argCount = 0;
				KotlinParser.ValueArgumentsContext args = ctx
						.valueArguments();
				if (args != null && args.valueArgument() != null) {
					argCount = args.valueArgument().size();
				}
				char[] indexKey = MethodPattern.createIndexKey(
						typeName.toCharArray(), argCount);
				document.addIndexEntry(IIndexConstants.CONSTRUCTOR_REF,
						indexKey);
			}
		}
		return visitChildren(ctx);
	}

	// ---- Method calls and constructor calls in expressions ----

	@Override
	public Void visitPostfixUnaryExpression(
			KotlinParser.PostfixUnaryExpressionContext ctx) {
		// Look for patterns like: expr.method(args) or Type(args)
		List<KotlinParser.PostfixUnarySuffixContext> suffixes = ctx
				.postfixUnarySuffix();
		if (suffixes == null || suffixes.isEmpty()) {
			return visitChildren(ctx);
		}

		// Index the primary expression as a type ref when it looks
		// like a type receiver (uppercase initial + navigation suffix).
		// Handles: MyEnum.VALUE, Type.staticMethod(), Type::ref
		KotlinParser.PostfixUnarySuffixContext firstSuffix =
				suffixes.get(0);
		if (firstSuffix.navigationSuffix() != null) {
			KotlinParser.PrimaryExpressionContext primary = ctx
					.primaryExpression();
			if (primary != null
					&& primary.simpleIdentifier() != null) {
				String name = primary.simpleIdentifier().getText();
				if (!name.isEmpty()
						&& Character.isUpperCase(name.charAt(0))) {
					String original = resolveAlias(name);
					addTypeRef(name);
					if (original != null) {
						addTypeRef(original);
					}
				}
			}
		}

		for (int i = 0; i < suffixes.size(); i++) {
			KotlinParser.PostfixUnarySuffixContext suffix = suffixes.get(i);

			// Check for navigation + call: .method(args)
			// or navigation without call: .property (field access)
			KotlinParser.NavigationSuffixContext navSuffix = suffix
					.navigationSuffix();
			if (navSuffix != null) {
				KotlinParser.SimpleIdentifierContext navId = navSuffix
						.simpleIdentifier();
				if (navId != null) {
					String memberName = navId.getText();
					String originalMember = resolveAlias(memberName);
					boolean isColonColon =
							isCallableRefNav(navSuffix);
					if (isColonColon) {
						// :: navigation = callable reference
						char[] indexKey = MethodPattern.createIndexKey(
								memberName.toCharArray(), 0);
						document.addIndexEntry(
								IIndexConstants.METHOD_REF, indexKey);
						if (originalMember != null) {
							char[] origKey = MethodPattern
									.createIndexKey(
											originalMember
													.toCharArray(),
											0);
							document.addIndexEntry(
									IIndexConstants.METHOD_REF,
									origKey);
						}
					} else if (i + 1 < suffixes.size()) {
						KotlinParser.CallSuffixContext callSuffix = suffixes
								.get(i + 1).callSuffix();
						if (callSuffix != null) {
							int argCount = countCallArgs(callSuffix);
							char[] indexKey = MethodPattern.createIndexKey(
									memberName.toCharArray(), argCount);
							document.addIndexEntry(
									IIndexConstants.METHOD_REF, indexKey);
							if (originalMember != null) {
								char[] origKey = MethodPattern.createIndexKey(
										originalMember.toCharArray(), argCount);
								document.addIndexEntry(
										IIndexConstants.METHOD_REF, origKey);
							}
						} else {
							indexPropertyAccess(memberName,
									originalMember);
						}
					} else {
						indexPropertyAccess(memberName,
								originalMember);
					}
				}
			}

			// Check for direct call suffix on primary expression:
			// ident(args) — could be function call or constructor
			KotlinParser.CallSuffixContext callSuffix = suffix.callSuffix();
			if (callSuffix != null && i == 0) {
				// The primary expression is the callee
				KotlinParser.PrimaryExpressionContext primary = ctx
						.primaryExpression();
				if (primary != null) {
					KotlinParser.SimpleIdentifierContext simpleId = primary
							.simpleIdentifier();
					if (simpleId != null) {
						String calleeName = simpleId.getText();
						String originalCallee = resolveAlias(calleeName);
						int argCount = countCallArgs(callSuffix);
						// Heuristic: uppercase initial = constructor call
						if (Character.isUpperCase(calleeName.charAt(0))) {
							char[] ctorKey = MethodPattern.createIndexKey(
									calleeName.toCharArray(), argCount);
							document.addIndexEntry(
									IIndexConstants.CONSTRUCTOR_REF,
									ctorKey);
							addTypeRef(calleeName);
							if (originalCallee != null) {
								char[] origCtorKey = MethodPattern
										.createIndexKey(
												originalCallee.toCharArray(),
												argCount);
								document.addIndexEntry(
										IIndexConstants.CONSTRUCTOR_REF,
										origCtorKey);
								addTypeRef(originalCallee);
							}
						}
						// Always index as METHOD_REF too
						char[] methodKey = MethodPattern.createIndexKey(
								calleeName.toCharArray(), argCount);
						document.addIndexEntry(IIndexConstants.METHOD_REF,
								methodKey);
						if (originalCallee != null) {
							char[] origMethodKey = MethodPattern
									.createIndexKey(
											originalCallee.toCharArray(),
											argCount);
							document.addIndexEntry(
									IIndexConstants.METHOD_REF,
									origMethodKey);
						}
					}
				}
			}
		}

		return visitChildren(ctx);
	}

	// ---- Type references in as/is expressions ----

	@Override
	public Void visitAsExpression(KotlinParser.AsExpressionContext ctx) {
		// as/as? target type
		List<KotlinParser.TypeContext> types = ctx.type();
		if (types != null) {
			for (KotlinParser.TypeContext type : types) {
				indexTypeContext(type);
			}
		}
		return visitChildren(ctx);
	}

	@Override
	public Void visitInfixOperation(
			KotlinParser.InfixOperationContext ctx) {
		// is/!is type checks contain type references
		if (ctx.isOperator() != null && !ctx.isOperator().isEmpty()) {
			List<KotlinParser.TypeContext> types = ctx.type();
			if (types != null) {
				for (KotlinParser.TypeContext type : types) {
					indexTypeContext(type);
				}
			}
		}
		return visitChildren(ctx);
	}

	/**
	 * Indexes a property-style access ({@code obj.name} without call
	 * parens). Emits REF for the property name plus METHOD_REF for
	 * Java-convention getter/setter names ({@code getName}/{@code setName}).
	 */
	private void indexPropertyAccess(String name, String originalName) {
		document.addIndexEntry(IIndexConstants.REF,
				name.toCharArray());
		if (originalName != null) {
			document.addIndexEntry(IIndexConstants.REF,
					originalName.toCharArray());
		}
		// Also index getter/setter METHOD_REFs for Java interop
		indexGetterSetterRefs(name);
		if (originalName != null) {
			indexGetterSetterRefs(originalName);
		}
	}

	private void indexGetterSetterRefs(String propertyName) {
		if (propertyName.isEmpty()) {
			return;
		}
		// Emit standard getter/setter: capitalize first char
		String capitalized = Character.toUpperCase(propertyName.charAt(0))
				+ propertyName.substring(1);
		emitGetterSetterEntries(capitalized);
		// Collect for post-indexing JDT lookup of actual getter names
		propertyNames.add(propertyName);
	}

	void emitGetterSetterEntries(String capitalizedName) {
		char[] getKey = MethodPattern.createIndexKey(
				("get" + capitalizedName).toCharArray(), 0);
		document.addIndexEntry(IIndexConstants.METHOD_REF, getKey);
		char[] isKey = MethodPattern.createIndexKey(
				("is" + capitalizedName).toCharArray(), 0);
		document.addIndexEntry(IIndexConstants.METHOD_REF, isKey);
		char[] setKey = MethodPattern.createIndexKey(
				("set" + capitalizedName).toCharArray(), 1);
		document.addIndexEntry(IIndexConstants.METHOD_REF, setKey);
	}

	/**
	 * Returns the set of property names encountered during indexing.
	 * Used by KotlinSearchParticipant to look up actual Java getter
	 * names from the JDT index and emit exact METHOD_REF entries.
	 */
	Set<String> getPropertyNames() {
		return propertyNames;
	}

	// ---- Callable references (::name, Type::name) ----

	@Override
	public Void visitCallableReference(
			KotlinParser.CallableReferenceContext ctx) {
		KotlinParser.SimpleIdentifierContext id =
				ctx.simpleIdentifier();
		if (id != null) {
			String name = id.getText();
			String originalName = resolveAlias(name);
			// Index as METHOD_REF with 0 args (arity unknown at
			// reference site)
			char[] indexKey = MethodPattern.createIndexKey(
					name.toCharArray(), 0);
			document.addIndexEntry(IIndexConstants.METHOD_REF,
					indexKey);
			if (originalName != null) {
				char[] origKey = MethodPattern.createIndexKey(
						originalName.toCharArray(), 0);
				document.addIndexEntry(IIndexConstants.METHOD_REF,
						origKey);
			}
			// If uppercase, also index as type ref (Type::class)
			if (Character.isUpperCase(name.charAt(0))) {
				addTypeRef(name);
			}
		}
		// Index the receiver type if present
		if (ctx.receiverType() != null
				&& ctx.receiverType().typeReference() != null
				&& ctx.receiverType().typeReference().userType()
						!= null) {
			visitUserType(
					ctx.receiverType().typeReference().userType());
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
				String name = id.getText();
				String originalName = resolveAlias(name);
				// Infix calls have exactly 2 args (receiver + argument)
				char[] indexKey = MethodPattern.createIndexKey(
						name.toCharArray(), 2);
				document.addIndexEntry(IIndexConstants.METHOD_REF,
						indexKey);
				if (originalName != null) {
					char[] origKey = MethodPattern.createIndexKey(
							originalName.toCharArray(), 2);
					document.addIndexEntry(
							IIndexConstants.METHOD_REF, origKey);
				}
			}
		}
		return visitChildren(ctx);
	}

	// ---- String template simple refs ($variable) ----

	@Override
	public Void visitLineStringContent(
			KotlinParser.LineStringContentContext ctx) {
		if (ctx.LineStrRef() != null) {
			indexStringTemplateRef(ctx.LineStrRef().getText());
		}
		return visitChildren(ctx);
	}

	@Override
	public Void visitMultiLineStringContent(
			KotlinParser.MultiLineStringContentContext ctx) {
		if (ctx.MultiLineStrRef() != null) {
			indexStringTemplateRef(
					ctx.MultiLineStrRef().getText());
		}
		return visitChildren(ctx);
	}

	private void indexStringTemplateRef(String refText) {
		if (refText == null || refText.length() < 2) {
			return;
		}
		// Strip leading $
		String name = refText.substring(1);
		document.addIndexEntry(IIndexConstants.REF,
				name.toCharArray());
	}

	// ---- Operator calls mapped to function names ----

	@Override
	public Void visitPostfixUnarySuffix(
			KotlinParser.PostfixUnarySuffixContext ctx) {
		// Index [] as get/set METHOD_REF
		if (ctx.indexingSuffix() != null) {
			int argCount = 0;
			if (ctx.indexingSuffix().expression() != null) {
				argCount = ctx.indexingSuffix().expression().size();
			}
			char[] getKey = MethodPattern.createIndexKey(
					"get".toCharArray(), argCount);
			document.addIndexEntry(IIndexConstants.METHOD_REF,
					getKey);
		}
		return visitChildren(ctx);
	}

	@Override
	public Void visitAdditiveExpression(
			KotlinParser.AdditiveExpressionContext ctx) {
		if (ctx.additiveOperator() != null) {
			for (KotlinParser.AdditiveOperatorContext op :
					ctx.additiveOperator()) {
				String fnName = op.ADD() != null ? "plus" : "minus";
				char[] key = MethodPattern.createIndexKey(
						fnName.toCharArray(), 1);
				document.addIndexEntry(IIndexConstants.METHOD_REF,
						key);
			}
		}
		return visitChildren(ctx);
	}

	@Override
	public Void visitMultiplicativeExpression(
			KotlinParser.MultiplicativeExpressionContext ctx) {
		if (ctx.multiplicativeOperator() != null) {
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
				char[] key = MethodPattern.createIndexKey(
						fnName.toCharArray(), 1);
				document.addIndexEntry(IIndexConstants.METHOD_REF,
						key);
			}
		}
		return visitChildren(ctx);
	}

	@Override
	public Void visitRangeExpression(
			KotlinParser.RangeExpressionContext ctx) {
		if (ctx.RANGE() != null && !ctx.RANGE().isEmpty()) {
			char[] key = MethodPattern.createIndexKey(
					"rangeTo".toCharArray(), 1);
			document.addIndexEntry(IIndexConstants.METHOD_REF, key);
		}
		if (ctx.RANGE_UNTIL() != null
				&& !ctx.RANGE_UNTIL().isEmpty()) {
			char[] key = MethodPattern.createIndexKey(
					"rangeUntil".toCharArray(), 1);
			document.addIndexEntry(IIndexConstants.METHOD_REF, key);
		}
		return visitChildren(ctx);
	}

	// ---- Helpers ----

	/**
	 * Returns the original name for an import alias, or {@code null}
	 * if the name is not aliased.
	 */
	private String resolveAlias(String name) {
		return aliasToOriginal.get(name);
	}

	private void addTypeRef(String simpleName) {
		if (simpleName == null || simpleName.isEmpty()) {
			return;
		}
		// Skip kotlin built-in type names that won't resolve to Java types
		if (isKotlinBuiltinKeyword(simpleName)) {
			return;
		}
		// Deduplicate within this file (case-insensitive, matching JDT index behavior)
		if (indexedTypeRefs.add(simpleName.toLowerCase())) {
			document.addIndexEntry(IIndexConstants.REF,
					simpleName.toCharArray());
		}
		// If this name is an import alias, also index the original name
		// so searches for the original find this document
		String original = aliasToOriginal.get(simpleName);
		if (original != null
				&& indexedTypeRefs.add(original.toLowerCase())) {
			document.addIndexEntry(IIndexConstants.REF,
					original.toCharArray());
		}
	}

	private void indexTypeContext(KotlinParser.TypeContext ctx) {
		if (ctx == null) {
			return;
		}
		KotlinParser.TypeReferenceContext typeRef = ctx.typeReference();
		if (typeRef != null && typeRef.userType() != null) {
			visitUserType(typeRef.userType());
		}
		KotlinParser.NullableTypeContext nullable = ctx.nullableType();
		if (nullable != null && nullable.typeReference() != null
				&& nullable.typeReference().userType() != null) {
			visitUserType(nullable.typeReference().userType());
		}
	}

	private static void warnNullIdentifier(String context,
			org.antlr.v4.runtime.ParserRuleContext ctx) {
		org.eclipse.core.runtime.Platform.getLog(
				KotlinReferenceIndexer.class).warn(
				"Null simpleIdentifier in " + context + " at "
						+ ctx.getStart().getLine() + ":"
						+ ctx.getStart().getCharPositionInLine());
	}

	private static boolean isCallableRefNav(
			KotlinParser.NavigationSuffixContext navSuffix) {
		return navSuffix.memberAccessOperator() != null
				&& navSuffix.memberAccessOperator().COLONCOLON()
						!= null;
	}

	private int countCallArgs(KotlinParser.CallSuffixContext callSuffix) {
		if (callSuffix == null) {
			return 0;
		}
		int count = 0;
		KotlinParser.ValueArgumentsContext args = callSuffix
				.valueArguments();
		if (args != null && args.valueArgument() != null) {
			count = args.valueArgument().size();
		}
		// Lambda argument counts as one parameter
		if (callSuffix.annotatedLambda() != null) {
			count++;
		}
		return count;
	}

	private static boolean isKotlinBuiltinKeyword(String name) {
		// Only skip primitive-like types that have no Java class equivalent.
		// Types like String, Any, Int etc. DO map to java.lang types and
		// should be indexed so cross-language find-references works.
		return switch (name) {
			case "Unit", "Nothing" -> true;
			default -> false;
		};
	}
}
