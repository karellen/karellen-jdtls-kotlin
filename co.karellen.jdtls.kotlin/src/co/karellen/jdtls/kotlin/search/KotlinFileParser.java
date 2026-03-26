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
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.tree.TerminalNode;

import co.karellen.jdtls.kotlin.parser.KotlinLexer;
import co.karellen.jdtls.kotlin.parser.KotlinParser;
import co.karellen.jdtls.kotlin.parser.KotlinParserBaseVisitor;
import co.karellen.jdtls.kotlin.search.KotlinDeclaration.ConstructorDeclaration;
import co.karellen.jdtls.kotlin.search.KotlinDeclaration.MethodDeclaration;
import co.karellen.jdtls.kotlin.search.KotlinDeclaration.PropertyDeclaration;
import co.karellen.jdtls.kotlin.search.KotlinDeclaration.TypeAliasDeclaration;
import co.karellen.jdtls.kotlin.search.KotlinDeclaration.TypeDeclaration;

/**
 * Parses Kotlin source files into {@link KotlinFileModel} using the ANTLR4
 * Kotlin grammar. Performs best-effort parsing: syntax errors are silently
 * ignored so that partially valid files still produce useful declarations.
 *
 * @author Arcadiy Ivanov
 */
public class KotlinFileParser {

	/**
	 * Parses the given Kotlin source text and returns a structured file model.
	 *
	 * @param source the Kotlin source code
	 * @return the parsed file model
	 */
	public KotlinFileModel parse(String source) {
		return parse(source, false);
	}

	public KotlinFileModel parse(String source, boolean isScript) {
		KotlinLexer lexer = new KotlinLexer(CharStreams.fromString(source));
		CommonTokenStream tokens = new CommonTokenStream(lexer);
		KotlinParser parser = new KotlinParser(tokens);

		parser.removeErrorListeners();
		parser.addErrorListener(SILENT_ERROR_LISTENER);

		if (isScript) {
			return parseScript(parser, source);
		}

		KotlinParser.KotlinFileContext tree = parser.kotlinFile();

		String packageName = extractPackageName(tree.packageHeader());
		List<KotlinFileModel.ImportEntry> imports = extractImports(
				tree.importList());

		KotlinDeclarationExtractor extractor = new KotlinDeclarationExtractor(
				packageName);
		for (KotlinParser.TopLevelObjectContext topLevel : tree.topLevelObject()) {
			KotlinParser.DeclarationContext decl = topLevel.declaration();
			if (decl != null) {
				extractor.visit(decl);
			}
		}

		return new KotlinFileModel(packageName, imports,
				extractor.getDeclarations(), source, tree);
	}

	private KotlinFileModel parseScript(KotlinParser parser,
			String source) {
		KotlinParser.ScriptContext tree = parser.script();

		String packageName = extractPackageName(tree.packageHeader());
		List<KotlinFileModel.ImportEntry> imports = extractImports(
				tree.importList());

		KotlinDeclarationExtractor extractor = new KotlinDeclarationExtractor(
				packageName);
		for (KotlinParser.StatementContext stmt : tree.statement()) {
			KotlinParser.DeclarationContext decl = stmt.declaration();
			if (decl != null) {
				extractor.visit(decl);
			}
		}

		return new KotlinFileModel(packageName, imports,
				extractor.getDeclarations(), source, tree);
	}

	private static String extractPackageName(
			KotlinParser.PackageHeaderContext ctx) {
		if (ctx == null || ctx.identifier() == null) {
			return null;
		}
		return extractIdentifierText(ctx.identifier());
	}

	private static List<KotlinFileModel.ImportEntry> extractImports(
			KotlinParser.ImportListContext ctx) {
		if (ctx == null) {
			return Collections.emptyList();
		}
		List<KotlinFileModel.ImportEntry> imports = new ArrayList<>();
		for (KotlinParser.ImportHeaderContext header : ctx.importHeader()) {
			KotlinParser.IdentifierContext id = header.identifier();
			if (id == null) {
				continue;
			}
			String fqn = extractIdentifierText(id);
			boolean star = header.MULT() != null;
			String alias = null;
			KotlinParser.ImportAliasContext aliasCtx = header.importAlias();
			if (aliasCtx != null && aliasCtx.simpleIdentifier() != null) {
				alias = aliasCtx.simpleIdentifier().getText();
			}
			imports.add(new KotlinFileModel.ImportEntry(fqn, alias, star));
		}
		return imports;
	}

	private static String extractIdentifierText(
			KotlinParser.IdentifierContext ctx) {
		if (ctx == null) {
			return null;
		}
		List<KotlinParser.SimpleIdentifierContext> parts = ctx
				.simpleIdentifier();
		if (parts.size() == 1) {
			return parts.get(0).getText();
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parts.size(); i++) {
			if (i > 0) {
				sb.append('.');
			}
			sb.append(parts.get(i).getText());
		}
		return sb.toString();
	}

	private static final BaseErrorListener SILENT_ERROR_LISTENER = new BaseErrorListener() {
		@Override
		public void syntaxError(Recognizer<?, ?> recognizer,
				Object offendingSymbol, int line, int charPositionInLine,
				String msg, RecognitionException e) {
			// Intentionally empty: best-effort parsing
		}
	};

	/**
	 * Visitor that extracts declarations from the ANTLR4 parse tree.
	 */
	private static class KotlinDeclarationExtractor
			extends KotlinParserBaseVisitor<Void> {

		private final String packageName;
		private final List<KotlinDeclaration> declarations = new ArrayList<>();
		private final Deque<String> enclosingTypeStack = new ArrayDeque<>();

		KotlinDeclarationExtractor(String packageName) {
			this.packageName = packageName;
		}

		List<KotlinDeclaration> getDeclarations() {
			return declarations;
		}

		private String currentEnclosingTypeName() {
			if (enclosingTypeStack.isEmpty()) {
				return null;
			}
			// Stack is front=innermost, back=outermost; join in
			// reverse (outermost first) to get "Outer.Middle"
			StringBuilder sb = new StringBuilder();
			for (var it = enclosingTypeStack.descendingIterator();
					it.hasNext(); ) {
				if (sb.length() > 0) {
					sb.append('.');
				}
				sb.append(it.next());
			}
			return sb.toString();
		}

		private List<KotlinDeclaration> currentTarget() {
			return declarations;
		}

		@Override
		public Void visitClassDeclaration(
				KotlinParser.ClassDeclarationContext ctx) {
			String name = extractIdentifier(ctx.simpleIdentifier());
			if (name == null) {
				return null;
			}

			int modifiers = extractModifiers(ctx.modifiers());
			TypeDeclaration.Kind kind;
			if (ctx.INTERFACE() != null) {
				kind = TypeDeclaration.Kind.INTERFACE;
				if (ctx.FUN() != null) {
					modifiers |= KotlinDeclaration.FUN_INTERFACE;
				}
			} else {
				kind = TypeDeclaration.Kind.CLASS;
			}

			// Check for enum, annotation, data, sealed from modifiers
			if ((modifiers & KotlinDeclaration.ENUM) != 0) {
				kind = TypeDeclaration.Kind.ENUM;
			}
			if (ctx.modifiers() != null) {
				for (KotlinParser.ModifierContext mod : ctx.modifiers()
						.modifier()) {
					KotlinParser.ClassModifierContext classMod = mod
							.classModifier();
					if (classMod != null && classMod.ANNOTATION() != null) {
						kind = TypeDeclaration.Kind.ANNOTATION;
					}
				}
			}

			List<String> supertypes = extractSupertypes(
					ctx.delegationSpecifiers());
			List<String> typeParameters = extractTypeParameters(
					ctx.typeParameters());
			int startOffset = ctx.getStart().getStartIndex();
			int endOffset = ctx.getStop().getStopIndex();

			// Extract members from class body
			List<KotlinDeclaration> members = new ArrayList<>();
			String enclosingName = currentEnclosingTypeName();

			// Extract primary constructor parameters with val/var as properties
			KotlinParser.PrimaryConstructorContext primaryCtor = ctx
					.primaryConstructor();
			if (primaryCtor != null) {
				List<MethodDeclaration.Parameter> ctorParams = new ArrayList<>();
				KotlinParser.ClassParametersContext classParams = primaryCtor
						.classParameters();
				if (classParams != null) {
					for (KotlinParser.ClassParameterContext param : classParams
							.classParameter()) {
						String paramName = extractIdentifier(
								param.simpleIdentifier());
						String paramType = param.type() != null
								? extractTypeName(param.type())
								: null;
						if (paramName != null) {
							ctorParams.add(
									new MethodDeclaration.Parameter(paramName,
											paramType));
							if (param.VAL() != null
									|| param.VAR() != null) {
								int paramMods = extractModifiers(
										param.modifiers());
								members.add(new PropertyDeclaration(paramName,
										paramMods, param.getStart()
												.getStartIndex(),
										param.getStop().getStopIndex(), name,
										paramType, param.VAR() != null));
							}
						}
					}
				}
				int ctorMods = extractModifiers(primaryCtor.modifiers());
				members.add(new ConstructorDeclaration(name, ctorMods,
						primaryCtor.getStart().getStartIndex(),
						primaryCtor.getStop().getStopIndex(), name, ctorParams,
						true));
			}

			enclosingTypeStack.push(name);
			try {
				KotlinParser.ClassBodyContext body = ctx.classBody();
				if (body != null) {
					extractMembersFromClassBody(body, name, members);
				}
				KotlinParser.EnumClassBodyContext enumBody = ctx
						.enumClassBody();
				if (enumBody != null) {
					extractMembersFromEnumClassBody(enumBody, name, members);
				}
			} finally {
				enclosingTypeStack.pop();
			}

			TypeDeclaration typeDecl = new TypeDeclaration(name, modifiers,
					startOffset, endOffset, enclosingName, kind, supertypes,
					typeParameters, members);
			declarations.add(typeDecl);
			return null;
		}

		@Override
		public Void visitObjectDeclaration(
				KotlinParser.ObjectDeclarationContext ctx) {
			String name = extractIdentifier(ctx.simpleIdentifier());
			if (name == null) {
				return null;
			}

			int modifiers = extractModifiers(ctx.modifiers())
					| KotlinDeclaration.STATIC;
			List<String> supertypes = extractSupertypes(
					ctx.delegationSpecifiers());
			int startOffset = ctx.getStart().getStartIndex();
			int endOffset = ctx.getStop().getStopIndex();

			List<KotlinDeclaration> members = new ArrayList<>();
			String enclosingName = currentEnclosingTypeName();

			enclosingTypeStack.push(name);
			try {
				KotlinParser.ClassBodyContext body = ctx.classBody();
				if (body != null) {
					extractMembersFromClassBody(body, name, members);
				}
			} finally {
				enclosingTypeStack.pop();
			}

			TypeDeclaration typeDecl = new TypeDeclaration(name, modifiers,
					startOffset, endOffset, enclosingName,
					TypeDeclaration.Kind.OBJECT, supertypes,
					Collections.emptyList(), members);
			declarations.add(typeDecl);
			return null;
		}

		@Override
		public Void visitCompanionObject(
				KotlinParser.CompanionObjectContext ctx) {
			String name = ctx.simpleIdentifier() != null
					? extractIdentifier(ctx.simpleIdentifier())
					: "Companion";

			int modifiers = extractModifiers(ctx.modifiers())
					| KotlinDeclaration.COMPANION;
			List<String> supertypes = extractSupertypes(
					ctx.delegationSpecifiers());
			int startOffset = ctx.getStart().getStartIndex();
			int endOffset = ctx.getStop().getStopIndex();

			List<KotlinDeclaration> members = new ArrayList<>();
			String enclosingName = currentEnclosingTypeName();

			enclosingTypeStack.push(name);
			try {
				KotlinParser.ClassBodyContext body = ctx.classBody();
				if (body != null) {
					extractMembersFromClassBody(body, name, members);
				}
			} finally {
				enclosingTypeStack.pop();
			}

			TypeDeclaration typeDecl = new TypeDeclaration(name, modifiers,
					startOffset, endOffset, enclosingName,
					TypeDeclaration.Kind.OBJECT, supertypes,
					Collections.emptyList(), members);
			declarations.add(typeDecl);
			return null;
		}

		@Override
		public Void visitFunctionDeclaration(
				KotlinParser.FunctionDeclarationContext ctx) {
			String name = extractIdentifier(ctx.simpleIdentifier());
			if (name == null) {
				return null;
			}

			int modifiers = extractModifiers(ctx.modifiers());
			List<MethodDeclaration.Parameter> parameters = extractParameters(
					ctx.functionValueParameters());
			String returnTypeName = ctx.type() != null
					? extractTypeName(ctx.type())
					: null;
			String receiverTypeName = ctx.receiverType() != null
					? extractTypeName(ctx.receiverType())
					: null;
			List<String> typeParameters = extractTypeParameters(
					ctx.typeParameters());
			boolean hasDefaultParams = hasDefaultParameters(
					ctx.functionValueParameters());
			int startOffset = ctx.getStart().getStartIndex();
			int endOffset = ctx.getStop().getStopIndex();

			// Extract nested declarations from function body
			List<KotlinDeclaration> nestedMembers = extractFunctionBodyDeclarations(ctx);

			MethodDeclaration methodDecl = new MethodDeclaration(name,
					modifiers, startOffset, endOffset,
					currentEnclosingTypeName(), parameters, returnTypeName,
					receiverTypeName, typeParameters, hasDefaultParams,
					nestedMembers);
			declarations.add(methodDecl);
			return null;
		}

		@Override
		public Void visitPropertyDeclaration(
				KotlinParser.PropertyDeclarationContext ctx) {
			KotlinParser.VariableDeclarationContext varDecl = ctx
					.variableDeclaration();
			if (varDecl == null) {
				// Multi-declaration (destructuring): extract each
				// component as a separate property
				KotlinParser.MultiVariableDeclarationContext multi =
						ctx.multiVariableDeclaration();
				if (multi != null) {
					int modifiers = extractModifiers(ctx.modifiers());
					boolean mutable = ctx.VAR() != null;
					for (KotlinParser.VariableDeclarationContext vd :
							multi.variableDeclaration()) {
						String compName = extractIdentifier(
								vd.simpleIdentifier());
						if (compName == null || "_".equals(compName)) {
							continue;
						}
						String compType = vd.type() != null
								? extractTypeName(vd.type()) : null;
						int startOffset = vd.getStart()
								.getStartIndex();
						int endOffset = vd.getStop().getStopIndex();
						declarations.add(new PropertyDeclaration(
								compName, modifiers, startOffset,
								endOffset,
								currentEnclosingTypeName(),
								compType, mutable));
					}
				}
				return null;
			}

			String name = extractIdentifier(varDecl.simpleIdentifier());
			if (name == null) {
				return null;
			}

			int modifiers = extractModifiers(ctx.modifiers());
			String typeName = varDecl.type() != null
					? extractTypeName(varDecl.type())
					: null;
			boolean mutable = ctx.VAR() != null;
			int startOffset = ctx.getStart().getStartIndex();
			int endOffset = ctx.getStop().getStopIndex();

			PropertyDeclaration propDecl = new PropertyDeclaration(name,
					modifiers, startOffset, endOffset,
					currentEnclosingTypeName(), typeName, mutable);
			declarations.add(propDecl);
			return null;
		}

		@Override
		public Void visitSecondaryConstructor(
				KotlinParser.SecondaryConstructorContext ctx) {
			int modifiers = extractModifiers(ctx.modifiers());
			List<MethodDeclaration.Parameter> parameters = extractParameters(
					ctx.functionValueParameters());
			int startOffset = ctx.getStart().getStartIndex();
			int endOffset = ctx.getStop().getStopIndex();
			String enclosing = currentEnclosingTypeName();

			ConstructorDeclaration ctorDecl = new ConstructorDeclaration(
					enclosing != null ? enclosing : "<init>", modifiers,
					startOffset, endOffset, enclosing, parameters, false);
			declarations.add(ctorDecl);
			return null;
		}

		@Override
		public Void visitTypeAlias(
				KotlinParser.TypeAliasContext ctx) {
			String name = extractIdentifier(ctx.simpleIdentifier());
			if (name == null) {
				return null;
			}

			int modifiers = extractModifiers(ctx.modifiers());
			String aliasedType = ctx.type() != null
					? extractTypeName(ctx.type()) : null;
			List<String> typeParameters = extractTypeParameters(
					ctx.typeParameters());
			int startOffset = ctx.getStart().getStartIndex();
			int endOffset = ctx.getStop().getStopIndex();

			TypeAliasDeclaration aliasDecl = new TypeAliasDeclaration(
					name, modifiers, startOffset, endOffset,
					currentEnclosingTypeName(), aliasedType,
					typeParameters);
			declarations.add(aliasDecl);
			return null;
		}

		private void extractMembersFromClassBody(
				KotlinParser.ClassBodyContext body, String typeName,
				List<KotlinDeclaration> members) {
			if (body == null || body.classMemberDeclarations() == null) {
				return;
			}
			List<KotlinDeclaration> saved = new ArrayList<>(declarations);
			declarations.clear();

			for (KotlinParser.ClassMemberDeclarationContext memberCtx : body
					.classMemberDeclarations().classMemberDeclaration()) {
				KotlinParser.CompanionObjectContext companion = memberCtx
						.companionObject();
				if (companion != null) {
					visitCompanionObject(companion);
					continue;
				}
				KotlinParser.SecondaryConstructorContext secondaryCtor = memberCtx
						.secondaryConstructor();
				if (secondaryCtor != null) {
					visitSecondaryConstructor(secondaryCtor);
					continue;
				}
				KotlinParser.DeclarationContext decl = memberCtx.declaration();
				if (decl != null) {
					visit(decl);
				}
			}

			members.addAll(declarations);
			declarations.clear();
			declarations.addAll(saved);
		}

		private void extractMembersFromEnumClassBody(
				KotlinParser.EnumClassBodyContext body, String typeName,
				List<KotlinDeclaration> members) {
			if (body == null) {
				return;
			}
			// Extract enum entries as simplified type declarations
			KotlinParser.EnumEntriesContext entries = body.enumEntries();
			if (entries != null) {
				for (KotlinParser.EnumEntryContext entry : entries.enumEntry()) {
					String entryName = extractIdentifier(
							entry.simpleIdentifier());
					if (entryName != null) {
						members.add(new PropertyDeclaration(entryName,
								KotlinDeclaration.PUBLIC
										| KotlinDeclaration.STATIC
										| KotlinDeclaration.FINAL,
								entry.getStart().getStartIndex(),
								entry.getStop().getStopIndex(), typeName,
								typeName, false));
					}
				}
			}
			// Extract regular members from classMemberDeclarations
			if (body.classMemberDeclarations() != null) {
				List<KotlinDeclaration> saved = new ArrayList<>(declarations);
				declarations.clear();

				for (KotlinParser.ClassMemberDeclarationContext memberCtx : body
						.classMemberDeclarations()
						.classMemberDeclaration()) {
					KotlinParser.CompanionObjectContext companion = memberCtx
							.companionObject();
					if (companion != null) {
						visitCompanionObject(companion);
						continue;
					}
					KotlinParser.SecondaryConstructorContext secondaryCtor = memberCtx
							.secondaryConstructor();
					if (secondaryCtor != null) {
						visitSecondaryConstructor(secondaryCtor);
						continue;
					}
					KotlinParser.DeclarationContext decl = memberCtx
							.declaration();
					if (decl != null) {
						visit(decl);
					}
				}

				members.addAll(declarations);
				declarations.clear();
				declarations.addAll(saved);
			}
		}

		private static int extractModifiers(
				KotlinParser.ModifiersContext ctx) {
			if (ctx == null) {
				return 0;
			}
			int flags = 0;
			for (KotlinParser.ModifierContext mod : ctx.modifier()) {
				KotlinParser.VisibilityModifierContext vis = mod
						.visibilityModifier();
				if (vis != null) {
					if (vis.PUBLIC() != null) {
						flags |= KotlinDeclaration.PUBLIC;
					} else if (vis.PRIVATE() != null) {
						flags |= KotlinDeclaration.PRIVATE;
					} else if (vis.PROTECTED() != null) {
						flags |= KotlinDeclaration.PROTECTED;
					} else if (vis.INTERNAL() != null) {
						flags |= KotlinDeclaration.INTERNAL;
					}
				}
				KotlinParser.InheritanceModifierContext inh = mod
						.inheritanceModifier();
				if (inh != null) {
					if (inh.ABSTRACT() != null) {
						flags |= KotlinDeclaration.ABSTRACT;
					} else if (inh.FINAL() != null) {
						flags |= KotlinDeclaration.FINAL;
					} else if (inh.OPEN() != null) {
						flags |= KotlinDeclaration.OPEN;
					}
				}
				KotlinParser.ClassModifierContext classMod = mod
						.classModifier();
				if (classMod != null) {
					if (classMod.DATA() != null) {
						flags |= KotlinDeclaration.DATA;
					} else if (classMod.SEALED() != null) {
						flags |= KotlinDeclaration.SEALED;
					} else if (classMod.ENUM() != null) {
						flags |= KotlinDeclaration.ENUM;
					} else if (classMod.INNER() != null) {
						flags |= KotlinDeclaration.INNER;
					} else if (classMod.VALUE() != null) {
						flags |= KotlinDeclaration.VALUE;
					}
				}
				KotlinParser.MemberModifierContext memberMod = mod
						.memberModifier();
				if (memberMod != null) {
					if (memberMod.OVERRIDE() != null) {
						flags |= KotlinDeclaration.OVERRIDE;
					} else if (memberMod.LATEINIT() != null) {
						// LATEINIT is a member modifier; no flag defined
					}
				}
				KotlinParser.FunctionModifierContext funcMod = mod
						.functionModifier();
				if (funcMod != null) {
					if (funcMod.OPERATOR() != null) {
						flags |= KotlinDeclaration.OPERATOR;
					} else if (funcMod.INFIX() != null) {
						flags |= KotlinDeclaration.INFIX;
					} else if (funcMod.INLINE() != null) {
						flags |= KotlinDeclaration.INLINE;
					} else if (funcMod.SUSPEND() != null) {
						flags |= KotlinDeclaration.SUSPEND;
					}
				}
			}
			return flags;
		}

		private static List<MethodDeclaration.Parameter> extractParameters(
				KotlinParser.FunctionValueParametersContext ctx) {
			if (ctx == null) {
				return Collections.emptyList();
			}
			List<MethodDeclaration.Parameter> params = new ArrayList<>();
			for (KotlinParser.FunctionValueParameterContext paramCtx : ctx
					.functionValueParameter()) {
				KotlinParser.ParameterContext param = paramCtx.parameter();
				if (param == null) {
					continue;
				}
				String name = extractIdentifier(param.simpleIdentifier());
				String typeName = param.type() != null
						? extractTypeName(param.type())
						: null;
				if (name != null) {
					params.add(new MethodDeclaration.Parameter(name, typeName));
				}
			}
			return params;
		}

		private List<KotlinDeclaration> extractFunctionBodyDeclarations(
				KotlinParser.FunctionDeclarationContext ctx) {
			KotlinParser.FunctionBodyContext body = ctx.functionBody();
			if (body == null || body.block() == null
					|| body.block().statements() == null) {
				return Collections.emptyList();
			}

			// Save current declarations, extract nested ones, then restore
			List<KotlinDeclaration> saved = new ArrayList<>(declarations);
			declarations.clear();

			for (KotlinParser.StatementContext stmt : body.block()
					.statements().statement()) {
				KotlinParser.DeclarationContext decl = stmt.declaration();
				if (decl != null
						&& decl.functionDeclaration() != null) {
					// Only extract nested function declarations
					// (local functions). Properties are handled by
					// LocalVariableExtractor.
					visit(decl.functionDeclaration());
				}
			}

			List<KotlinDeclaration> nested = new ArrayList<>(declarations);
			declarations.clear();
			declarations.addAll(saved);
			return nested;
		}

		private static boolean hasDefaultParameters(
				KotlinParser.FunctionValueParametersContext ctx) {
			if (ctx == null) {
				return false;
			}
			for (KotlinParser.FunctionValueParameterContext paramCtx : ctx
					.functionValueParameter()) {
				if (paramCtx.expression() != null) {
					return true;
				}
			}
			return false;
		}

		private static String extractTypeName(KotlinParser.TypeContext ctx) {
			if (ctx == null) {
				return null;
			}
			// Strip NL tokens by rebuilding from meaningful children
			KotlinParser.TypeReferenceContext typeRef = ctx.typeReference();
			if (typeRef != null) {
				return extractTypeReferenceName(typeRef);
			}
			KotlinParser.NullableTypeContext nullable = ctx.nullableType();
			if (nullable != null) {
				KotlinParser.TypeReferenceContext innerRef = nullable
						.typeReference();
				if (innerRef != null) {
					return extractTypeReferenceName(innerRef) + "?";
				}
				KotlinParser.ParenthesizedTypeContext paren = nullable
						.parenthesizedType();
				if (paren != null && paren.type() != null) {
					return extractTypeName(paren.type()) + "?";
				}
			}
			// Fallback: use getText and strip whitespace
			return ctx.getText().replaceAll("\\s+", "");
		}

		private static String extractTypeName(
				KotlinParser.ReceiverTypeContext ctx) {
			if (ctx == null) {
				return null;
			}
			KotlinParser.TypeReferenceContext typeRef = ctx.typeReference();
			if (typeRef != null) {
				return extractTypeReferenceName(typeRef);
			}
			KotlinParser.NullableTypeContext nullable = ctx.nullableType();
			if (nullable != null) {
				KotlinParser.TypeReferenceContext innerRef = nullable
						.typeReference();
				if (innerRef != null) {
					return extractTypeReferenceName(innerRef) + "?";
				}
			}
			KotlinParser.ParenthesizedTypeContext paren = ctx
					.parenthesizedType();
			if (paren != null && paren.type() != null) {
				return extractTypeName(paren.type());
			}
			return ctx.getText().replaceAll("\\s+", "");
		}

		private static String extractTypeReferenceName(
				KotlinParser.TypeReferenceContext ctx) {
			if (ctx == null) {
				return null;
			}
			KotlinParser.UserTypeContext userType = ctx.userType();
			if (userType != null) {
				return extractUserTypeName(userType);
			}
			if (ctx.DYNAMIC() != null) {
				return "dynamic";
			}
			return ctx.getText().replaceAll("\\s+", "");
		}

		private static String extractUserTypeName(
				KotlinParser.UserTypeContext ctx) {
			if (ctx == null) {
				return null;
			}
			List<KotlinParser.SimpleUserTypeContext> parts = ctx
					.simpleUserType();
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < parts.size(); i++) {
				if (i > 0) {
					sb.append('.');
				}
				KotlinParser.SimpleUserTypeContext part = parts.get(i);
				sb.append(part.simpleIdentifier().getText());
				KotlinParser.TypeArgumentsContext typeArgs = part
						.typeArguments();
				if (typeArgs != null) {
					sb.append('<');
					List<KotlinParser.TypeProjectionContext> projections = typeArgs
							.typeProjection();
					for (int j = 0; j < projections.size(); j++) {
						if (j > 0) {
							sb.append(", ");
						}
						KotlinParser.TypeProjectionContext proj = projections
								.get(j);
						if (proj.MULT() != null) {
							sb.append('*');
						} else if (proj.type() != null) {
							KotlinParser.TypeProjectionModifiersContext projMods = proj
									.typeProjectionModifiers();
							if (projMods != null) {
								sb.append(projMods.getText()
										.replaceAll("\\s+", " "));
								sb.append(' ');
							}
							sb.append(extractTypeName(proj.type()));
						}
					}
					sb.append('>');
				}
			}
			return sb.toString();
		}

		private static List<String> extractSupertypes(
				KotlinParser.DelegationSpecifiersContext ctx) {
			if (ctx == null) {
				return Collections.emptyList();
			}
			List<String> result = new ArrayList<>();
			for (KotlinParser.AnnotatedDelegationSpecifierContext annotated : ctx
					.annotatedDelegationSpecifier()) {
				KotlinParser.DelegationSpecifierContext spec = annotated
						.delegationSpecifier();
				if (spec == null) {
					continue;
				}
				KotlinParser.ConstructorInvocationContext ctorInvocation = spec
						.constructorInvocation();
				if (ctorInvocation != null) {
					KotlinParser.UserTypeContext userType = ctorInvocation
							.userType();
					if (userType != null) {
						result.add(extractUserTypeName(userType));
					}
					continue;
				}
				KotlinParser.UserTypeContext userType = spec.userType();
				if (userType != null) {
					result.add(extractUserTypeName(userType));
					continue;
				}
				KotlinParser.ExplicitDelegationContext explDel = spec
						.explicitDelegation();
				if (explDel != null) {
					KotlinParser.UserTypeContext delType = explDel.userType();
					if (delType != null) {
						result.add(extractUserTypeName(delType));
					}
				}
			}
			return result;
		}

		private static List<String> extractTypeParameters(
				KotlinParser.TypeParametersContext ctx) {
			if (ctx == null) {
				return Collections.emptyList();
			}
			List<String> result = new ArrayList<>();
			for (KotlinParser.TypeParameterContext param : ctx
					.typeParameter()) {
				String name = extractIdentifier(param.simpleIdentifier());
				if (name != null) {
					result.add(name);
				}
			}
			return result;
		}

		private static String extractIdentifier(
				KotlinParser.SimpleIdentifierContext ctx) {
			if (ctx == null) {
				return null;
			}
			return ctx.getText();
		}
	}
}
