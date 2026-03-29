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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.tree.ParseTree;

import co.karellen.jdtls.kotlin.parser.KotlinParser;

/**
 * Manages a stack of scopes for name-to-type resolution during expression
 * type resolution. Scopes are layered from innermost (block) to outermost
 * (file/package/defaults), with name lookups walking from top to bottom.
 *
 * @author Arcadiy Ivanov
 */
public class ScopeChain {

	private final Deque<Map<String, KotlinType>> scopes = new ArrayDeque<>();
	private final ImportResolver importResolver;
	private final SymbolTable symbolTable;

	public ScopeChain(ImportResolver importResolver,
			SymbolTable symbolTable) {
		this.importResolver = importResolver;
		this.symbolTable = symbolTable;
	}

	/**
	 * Pushes a new empty scope onto the stack.
	 */
	public void pushScope() {
		scopes.push(new LinkedHashMap<>());
	}

	/**
	 * Removes the top scope from the stack.
	 *
	 * @throws java.util.NoSuchElementException if the stack is empty
	 */
	public void popScope() {
		scopes.pop();
	}

	/**
	 * Adds a name-to-type binding in the top scope.
	 *
	 * @param name the binding name
	 * @param type the resolved type
	 */
	public void addBinding(String name, KotlinType type) {
		if (scopes.isEmpty()) {
			pushScope();
		}
		scopes.peek().put(name, type);
	}

	/**
	 * Resolves a simple name to a {@link KotlinType} by walking the scope
	 * stack from top to bottom. If not found in any scope, falls back to
	 * {@link ImportResolver} and {@link SymbolTable}.
	 *
	 * @param name the simple name to resolve
	 * @return the resolved type, or {@link KotlinType#UNKNOWN}
	 */
	public KotlinType resolveType(String name) {
		if (name == null) {
			return KotlinType.UNKNOWN;
		}

		// Walk scopes from innermost to outermost
		for (Map<String, KotlinType> scope : scopes) {
			KotlinType type = scope.get(name);
			if (type != null) {
				return type;
			}
		}

		// Try KotlinType.resolve which handles auto-imports
		// (ArrayList → kotlin.collections.ArrayList, etc.)
		KotlinType resolved = KotlinType.resolve(name,
				importResolver);
		if (!resolved.isUnknown()) {
			// Check if it's a known type in the symbol table
			SymbolTable.TypeSymbol typeSym = symbolTable
					.lookupType(resolved.getFQN());
			if (typeSym != null) {
				return new KotlinType(typeSym.getPackageName(),
						typeSym.getSimpleName());
			}
			return resolved;
		}

		// Try symbol table by simple name
		List<SymbolTable.TypeSymbol> candidates = symbolTable
				.lookupTypesBySimpleName(name);
		if (!candidates.isEmpty()) {
			SymbolTable.TypeSymbol first = candidates.get(0);
			return new KotlinType(first.getPackageName(),
					first.getSimpleName());
		}

		return KotlinType.UNKNOWN;
	}

	/**
	 * Initializes a file-level scope with top-level declarations from the
	 * given file model.
	 *
	 * @param fileModel the parsed file model
	 */
	public void initFileScope(KotlinFileModel fileModel) {
		pushScope();
		String packageName = fileModel.getPackageName();

		// Add top-level type names first so property type resolution can
		// find same-file types (e.g., val x: Dog where Dog is declared
		// in the same file)
		for (KotlinDeclaration.TypeDeclaration typeDecl : fileModel
				.getTopLevelTypes()) {
			KotlinType type = new KotlinType(packageName,
					typeDecl.getName());
			addBinding(typeDecl.getName(), type);
		}

		// Add top-level properties (type resolution can now find
		// same-file types via the scope bindings above)
		for (KotlinDeclaration.PropertyDeclaration prop : fileModel
				.getTopLevelProperties()) {
			String typeName = prop.getTypeName();
			if (typeName != null) {
				KotlinType type = resolveTypeName(typeName);
				addBinding(prop.getName(), type);
			} else {
				addBinding(prop.getName(), KotlinType.UNKNOWN);
			}
		}

		// Add top-level functions as bindings (type is UNKNOWN for now;
		// function types would need full resolution)
		for (KotlinDeclaration.MethodDeclaration method : fileModel
				.getTopLevelFunctions()) {
			addBinding(method.getName(), KotlinType.UNKNOWN);
		}
	}

	/**
	 * Initializes a class scope with the class's members, including
	 * properties, companion object members, and a "this" binding.
	 *
	 * @param typeDecl    the type declaration
	 * @param packageName the package name of the enclosing file
	 */
	public void initClassScope(
			KotlinDeclaration.TypeDeclaration typeDecl,
			String packageName) {
		initClassScope(typeDecl, packageName, null, null);
	}

	/**
	 * Initializes a class scope with property type inference from
	 * initializer expressions. For properties without a type annotation,
	 * uses the {@code ExpressionTypeResolver} to infer the type from
	 * the property's initializer expression in the parse tree.
	 *
	 * @param typeDecl     the type declaration
	 * @param packageName  the package name of the enclosing file
	 * @param exprResolver the expression type resolver, or {@code null}
	 * @param parseTree    the full parse tree of the file, or {@code null}
	 */
	public void initClassScope(
			KotlinDeclaration.TypeDeclaration typeDecl,
			String packageName,
			ExpressionTypeResolver exprResolver,
			ParseTree parseTree) {
		// Build a map of property name → initializer expression from
		// the parse tree for properties without type annotations
		Map<String, KotlinParser.ExpressionContext> initializerMap =
				new HashMap<>();
		if (exprResolver != null && parseTree != null) {
			collectPropertyInitializers(parseTree, typeDecl,
					initializerMap);
		}

		pushScope();

		// Add "this" binding
		KotlinType thisType = new KotlinType(packageName,
				typeDecl.getName());
		addBinding("this", thisType);

		// Add members
		for (KotlinDeclaration member : typeDecl.getMembers()) {
			if (member instanceof KotlinDeclaration.PropertyDeclaration) {
				KotlinDeclaration.PropertyDeclaration prop = (KotlinDeclaration.PropertyDeclaration) member;
				String typeName = prop.getTypeName();
				if (typeName != null) {
					addBinding(prop.getName(), resolveTypeName(typeName));
				} else if (exprResolver != null
						&& initializerMap.containsKey(
								prop.getName())) {
					KotlinType inferred = exprResolver.resolve(
							initializerMap.get(prop.getName()));
					addBinding(prop.getName(), inferred);
				} else {
					addBinding(prop.getName(), KotlinType.UNKNOWN);
				}
			} else if (member instanceof KotlinDeclaration.MethodDeclaration) {
				addBinding(member.getName(), KotlinType.UNKNOWN);
			} else if (member instanceof KotlinDeclaration.TypeDeclaration) {
				KotlinDeclaration.TypeDeclaration nested = (KotlinDeclaration.TypeDeclaration) member;
				if ((nested.getModifiers()
						& KotlinDeclaration.COMPANION) != 0) {
					for (KotlinDeclaration companionMember : nested
							.getMembers()) {
						if (companionMember instanceof KotlinDeclaration.PropertyDeclaration) {
							KotlinDeclaration.PropertyDeclaration prop = (KotlinDeclaration.PropertyDeclaration) companionMember;
							String typeName = prop.getTypeName();
							if (typeName != null) {
								addBinding(prop.getName(),
										resolveTypeName(typeName));
							} else if (exprResolver != null
									&& initializerMap.containsKey(
											prop.getName())) {
								KotlinType inferred =
										exprResolver.resolve(
												initializerMap.get(
														prop.getName()));
								addBinding(prop.getName(), inferred);
							} else {
								addBinding(prop.getName(),
										KotlinType.UNKNOWN);
							}
						} else if (companionMember instanceof KotlinDeclaration.MethodDeclaration) {
							addBinding(companionMember.getName(),
									KotlinType.UNKNOWN);
						}
					}
				}
				addBinding(nested.getName(), new KotlinType(packageName,
						typeDecl.getName() + "." + nested.getName()));
			}
		}
	}

	/**
	 * Collects property initializer expressions from the ANTLR parse tree
	 * for a class declaration. Only collects initializers for properties
	 * that don't have an explicit type annotation.
	 */
	private void collectPropertyInitializers(ParseTree parseTree,
			KotlinDeclaration.TypeDeclaration typeDecl,
			Map<String, KotlinParser.ExpressionContext> result) {
		KotlinParser.ClassDeclarationContext classCtx =
				findClassDeclaration(parseTree,
						typeDecl.getStartOffset(),
						typeDecl.getEndOffset());
		if (classCtx == null || classCtx.classBody() == null) {
			return;
		}
		KotlinParser.ClassMemberDeclarationsContext members =
				classCtx.classBody().classMemberDeclarations();
		if (members == null) {
			return;
		}
		for (KotlinParser.ClassMemberDeclarationContext memberCtx
				: members.classMemberDeclaration()) {
			KotlinParser.DeclarationContext declCtx =
					memberCtx.declaration();
			if (declCtx == null) {
				continue;
			}
			KotlinParser.PropertyDeclarationContext propCtx =
					declCtx.propertyDeclaration();
			if (propCtx == null) {
				continue;
			}
			KotlinParser.VariableDeclarationContext varDecl =
					propCtx.variableDeclaration();
			if (varDecl == null
					|| varDecl.simpleIdentifier() == null) {
				continue;
			}
			// Only collect if no type annotation
			if (varDecl.type() != null) {
				continue;
			}
			KotlinParser.ExpressionContext initExpr =
					propCtx.expression();
			if (initExpr != null) {
				result.put(varDecl.simpleIdentifier().getText(),
						initExpr);
			}
		}
	}

	/**
	 * Finds a {@code ClassDeclarationContext} in the parse tree matching
	 * the given start/end offsets.
	 */
	private KotlinParser.ClassDeclarationContext findClassDeclaration(
			ParseTree node, int startOffset, int endOffset) {
		if (node instanceof KotlinParser.ClassDeclarationContext ctx) {
			if (ctx.getStart() != null && ctx.getStop() != null
					&& ctx.getStart().getStartIndex() == startOffset
					&& ctx.getStop().getStopIndex() == endOffset) {
				return ctx;
			}
		}
		for (int i = 0; i < node.getChildCount(); i++) {
			KotlinParser.ClassDeclarationContext result =
					findClassDeclaration(node.getChild(i),
							startOffset, endOffset);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	/**
	 * Initializes a function scope with the function's parameters and, for
	 * extension functions, a "this" binding to the receiver type.
	 *
	 * @param methodDecl the method declaration
	 */
	public void initFunctionScope(
			KotlinDeclaration.MethodDeclaration methodDecl) {
		pushScope();

		// Add extension function receiver as "this"
		String receiverTypeName = methodDecl.getReceiverTypeName();
		if (receiverTypeName != null) {
			addBinding("this", resolveTypeName(receiverTypeName));
		}

		// Add parameters
		for (KotlinDeclaration.MethodDeclaration.Parameter param : methodDecl
				.getParameters()) {
			String typeName = param.getTypeName();
			if (typeName != null) {
				addBinding(param.getName(), resolveTypeName(typeName));
			} else {
				addBinding(param.getName(), KotlinType.UNKNOWN);
			}
		}
	}

	/**
	 * Initializes a local variable scope with the given local variable
	 * declarations extracted from a function body. Variables with a type
	 * annotation are resolved via {@link #resolveTypeName(String)};
	 * variables without annotation but with an initializer expression
	 * are resolved via the given {@link ExpressionTypeResolver};
	 * remaining variables get {@link KotlinType#UNKNOWN}
	 * (conservative: allows match during receiver verification).
	 *
	 * @param locals       the local variable declarations
	 * @param exprResolver the expression type resolver for initializer
	 *                     inference, or {@code null} to skip inference
	 */
	public void initLocalVariableScope(
			List<LocalVariableExtractor.LocalVariable> locals,
			ExpressionTypeResolver exprResolver) {
		pushScope();
		for (LocalVariableExtractor.LocalVariable local : locals) {
			String typeName = local.typeName();
			if (typeName != null) {
				addBinding(local.name(), resolveTypeName(typeName));
			} else if (exprResolver != null
					&& local.initializerCtx() != null) {
				KotlinType inferred =
						exprResolver.resolve(local.initializerCtx());
				addBinding(local.name(), inferred);
			} else {
				addBinding(local.name(), KotlinType.UNKNOWN);
			}
		}
	}

	/**
	 * Initializes a local variable scope without initializer inference.
	 *
	 * @param locals the local variable declarations
	 */
	public void initLocalVariableScope(
			List<LocalVariableExtractor.LocalVariable> locals) {
		initLocalVariableScope(locals, null);
	}

	/**
	 * Returns the current depth of the scope stack.
	 *
	 * @return the number of scopes on the stack
	 */
	public int depth() {
		return scopes.size();
	}

	/**
	 * Resolves a type name string to a {@link KotlinType}, checking
	 * well-known types first, then falling back to import resolution.
	 */
	private KotlinType resolveTypeName(String typeName) {
		if (typeName == null) {
			return KotlinType.UNKNOWN;
		}
		if (typeName.endsWith("?")) {
			return resolveTypeName(
					typeName.substring(0, typeName.length() - 1))
					.withNullable(true);
		}
		// Handle type arguments (e.g., "List<Alpha>", "Map<String,List<Int>>")
		int angleIdx = typeName.indexOf('<');
		if (angleIdx >= 0 && typeName.endsWith(">")) {
			String baseName = typeName.substring(0, angleIdx);
			String argsStr = typeName.substring(angleIdx + 1,
					typeName.length() - 1);
			KotlinType baseType = resolveTypeName(baseName);
			List<KotlinType> typeArgs = new ArrayList<>();
			for (String arg : splitTypeArguments(argsStr)) {
				typeArgs.add(resolveTypeName(arg.trim()));
			}
			return baseType.withTypeArguments(typeArgs);
		}
		// Primitive array types (not in KotlinType.resolve)
		switch (typeName) {
		case "IntArray":
			return KotlinType.INT_ARRAY;
		case "LongArray":
			return KotlinType.LONG_ARRAY;
		case "DoubleArray":
			return KotlinType.DOUBLE_ARRAY;
		case "FloatArray":
			return KotlinType.FLOAT_ARRAY;
		case "BooleanArray":
			return KotlinType.BOOLEAN_ARRAY;
		case "CharArray":
			return KotlinType.CHAR_ARRAY;
		case "ByteArray":
			return KotlinType.BYTE_ARRAY;
		case "ShortArray":
			return KotlinType.SHORT_ARRAY;
		default:
			// Check scope bindings first (same-file types take
			// precedence over import resolution)
			for (Map<String, KotlinType> scope : scopes) {
				KotlinType scopeType = scope.get(typeName);
				if (scopeType != null && !scopeType.isUnknown()) {
					return scopeType;
				}
			}
			return KotlinType.resolve(typeName, importResolver);
		}
	}

	/**
	 * Splits a type argument string at top-level commas, respecting
	 * nested angle brackets (e.g., "String,List&lt;Int&gt;" splits into
	 * ["String", "List&lt;Int&gt;"], not ["String", "List&lt;Int"]).
	 */
	private static List<String> splitTypeArguments(String argsStr) {
		List<String> result = new ArrayList<>();
		int depth = 0;
		int start = 0;
		for (int i = 0; i < argsStr.length(); i++) {
			char c = argsStr.charAt(i);
			if (c == '<') {
				depth++;
			} else if (c == '>') {
				depth--;
			} else if (c == ',' && depth == 0) {
				result.add(argsStr.substring(start, i));
				start = i + 1;
			}
		}
		result.add(argsStr.substring(start));
		return result;
	}
}
