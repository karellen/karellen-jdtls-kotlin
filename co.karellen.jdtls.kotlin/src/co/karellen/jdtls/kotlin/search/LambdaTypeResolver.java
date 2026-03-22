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

import java.util.List;

import co.karellen.jdtls.kotlin.parser.KotlinParser;

/**
 * Infers lambda parameter types from the enclosing call context. Handles
 * Kotlin scope functions ({@code let}, {@code run}, {@code apply},
 * {@code also}, {@code with}) and collection operations ({@code map},
 * {@code filter}, etc.).
 *
 * @author Arcadiy Ivanov
 */
public class LambdaTypeResolver {

	private final SymbolTable symbolTable;
	private final ImportResolver importResolver;

	public LambdaTypeResolver(SymbolTable symbolTable,
			ImportResolver importResolver) {
		this.symbolTable = symbolTable;
		this.importResolver = importResolver;
	}

	/**
	 * Result of lambda type resolution, capturing the receiver type
	 * ({@code this} inside the lambda), implicit parameter type
	 * ({@code it}), and expected return type.
	 */
	public static class LambdaContext {

		private final KotlinType lambdaReceiverType;
		private final KotlinType implicitParameterType;
		private final KotlinType returnType;

		/**
		 * Creates a lambda context. Any parameter may be {@code null} to
		 * indicate it is not applicable.
		 *
		 * @param lambdaReceiverType    the {@code this} type inside the
		 *                              lambda, or {@code null}
		 * @param implicitParameterType the {@code it} type, or
		 *                              {@code null}
		 * @param returnType            the expected return type, or
		 *                              {@code null}
		 */
		public LambdaContext(KotlinType lambdaReceiverType,
				KotlinType implicitParameterType,
				KotlinType returnType) {
			this.lambdaReceiverType = lambdaReceiverType;
			this.implicitParameterType = implicitParameterType;
			this.returnType = returnType;
		}

		public KotlinType getLambdaReceiverType() {
			return lambdaReceiverType;
		}

		public KotlinType getImplicitParameterType() {
			return implicitParameterType;
		}

		public KotlinType getReturnType() {
			return returnType;
		}

		public boolean hasReceiver() {
			return lambdaReceiverType != null
					&& !lambdaReceiverType.isUnknown();
		}

		public boolean hasImplicitParameter() {
			return implicitParameterType != null
					&& !implicitParameterType.isUnknown();
		}
	}

	/**
	 * Resolves the lambda context from an enclosing call.
	 *
	 * @param receiverType    the type of the object the function is called
	 *                        on (e.g., {@code foo} in {@code foo.let { }})
	 * @param functionName    the name of the function being called
	 * @param lambdaParamIndex which parameter position the lambda occupies
	 * @return the resolved lambda context, or {@code null} if no context
	 *         could be determined
	 */
	public LambdaContext resolveLambdaContext(KotlinType receiverType,
			String functionName, int lambdaParamIndex) {
		if (functionName == null) {
			return null;
		}

		// 1. Check scope functions (hardcoded, most common)
		LambdaContext scopeResult =
				resolveScopeFunction(receiverType, functionName);
		if (scopeResult != null) {
			return scopeResult;
		}

		// 2. Check collection operations
		LambdaContext collectionResult =
				resolveCollectionOperation(receiverType, functionName);
		if (collectionResult != null) {
			return collectionResult;
		}

		// 3. Look up in symbol table for generic resolution
		return resolveFromSymbolTable(receiverType, functionName,
				lambdaParamIndex);
	}

	private LambdaContext resolveScopeFunction(KotlinType receiverType,
			String functionName) {
		if (receiverType == null || receiverType.isUnknown()) {
			return null;
		}

		switch (functionName) {
			case "let":
				// T.let { it: T -> R } => returns R
				return new LambdaContext(null, receiverType,
						KotlinType.UNKNOWN);
			case "run":
				// T.run { this: T -> R } => returns R
				return new LambdaContext(receiverType, null,
						KotlinType.UNKNOWN);
			case "apply":
				// T.apply { this: T -> Unit } => returns T
				return new LambdaContext(receiverType, null,
						receiverType);
			case "also":
				// T.also { it: T -> Unit } => returns T
				return new LambdaContext(null, receiverType,
						receiverType);
			case "with":
				// with(T) { this: T -> R } => returns R
				return new LambdaContext(receiverType, null,
						KotlinType.UNKNOWN);
			case "takeIf":
			case "takeUnless":
				return new LambdaContext(null, receiverType,
						KotlinType.BOOLEAN);
			default:
				return null;
		}
	}

	private LambdaContext resolveCollectionOperation(
			KotlinType receiverType, String functionName) {
		if (receiverType == null || receiverType.isUnknown()) {
			return null;
		}

		// Extract element type from collection type arguments
		KotlinType elementType = getCollectionElementType(receiverType);
		if (elementType == null || elementType.isUnknown()) {
			return null;
		}

		switch (functionName) {
			case "map":
			case "flatMap":
			case "forEach":
			case "forEachIndexed":
			case "onEach":
			case "filter":
			case "filterNot":
			case "filterIsInstance":
			case "find":
			case "findLast":
			case "first":
			case "firstOrNull":
			case "last":
			case "lastOrNull":
			case "single":
			case "singleOrNull":
			case "any":
			case "all":
			case "none":
			case "count":
			case "sumOf":
			case "maxOf":
			case "minOf":
			case "maxBy":
			case "minBy":
			case "maxByOrNull":
			case "minByOrNull":
			case "sortedBy":
			case "sortedByDescending":
			case "groupBy":
			case "associateBy":
			case "associateWith":
			case "associate":
			case "partition":
			case "zip":
			case "unzip":
			case "joinToString":
			case "reduce":
			case "fold":
			case "scan":
			case "dropWhile":
			case "takeWhile":
				return new LambdaContext(null, elementType,
						KotlinType.UNKNOWN);
			default:
				return null;
		}
	}

	private KotlinType getCollectionElementType(
			KotlinType collectionType) {
		return collectionType.getElementType();
	}

	private LambdaContext resolveFromSymbolTable(KotlinType receiverType,
			String functionName, int lambdaParamIndex) {
		if (receiverType == null || receiverType.isUnknown()) {
			return null;
		}

		SymbolTable.TypeSymbol typeSym =
				symbolTable.lookupType(receiverType.getFQN());
		if (typeSym == null) {
			return null;
		}

		for (SymbolTable.MethodSymbol method : typeSym.getMethods()) {
			if (method.getName().equals(functionName)) {
				List<String> paramTypes = method.getParameterTypes();
				if (lambdaParamIndex < paramTypes.size()) {
					String paramType =
							paramTypes.get(lambdaParamIndex);
					if (paramType.contains("->")) {
						// Function type "(T) -> R": extract input type
						// as implicit parameter
						KotlinType inputType =
								extractFunctionInputType(paramType);
						KotlinType outputType =
								extractFunctionOutputType(paramType);
						return new LambdaContext(null, inputType,
								outputType);
					}
				}
			}
		}
		return null;
	}

	/**
	 * Extracts the input type from a function type string like
	 * "(T) -> R". Returns the resolved type, or
	 * {@link KotlinType#UNKNOWN} if parsing fails.
	 */
	private KotlinType extractFunctionInputType(String functionType) {
		int arrowIndex = functionType.indexOf("->");
		if (arrowIndex < 0) {
			return KotlinType.UNKNOWN;
		}
		String inputPart = functionType.substring(0, arrowIndex).trim();
		// Strip surrounding parentheses
		if (inputPart.startsWith("(") && inputPart.endsWith(")")) {
			inputPart = inputPart.substring(1,
					inputPart.length() - 1).trim();
		}
		if (inputPart.isEmpty()) {
			return KotlinType.UNKNOWN;
		}
		// Handle multi-parameter functions: take the first parameter
		int commaIndex = inputPart.indexOf(',');
		if (commaIndex >= 0) {
			inputPart = inputPart.substring(0, commaIndex).trim();
		}
		return resolveTypeName(inputPart);
	}

	/**
	 * Extracts the output type from a function type string like
	 * "(T) -> R". Returns the resolved type, or
	 * {@link KotlinType#UNKNOWN} if parsing fails.
	 */
	private KotlinType extractFunctionOutputType(String functionType) {
		int arrowIndex = functionType.indexOf("->");
		if (arrowIndex < 0) {
			return KotlinType.UNKNOWN;
		}
		String outputPart =
				functionType.substring(arrowIndex + 2).trim();
		if (outputPart.isEmpty()) {
			return KotlinType.UNKNOWN;
		}
		return resolveTypeName(outputPart);
	}

	/**
	 * Pushes a lambda context's bindings into the scope chain. Adds
	 * {@code this} for receiver lambdas and {@code it} (or a named
	 * parameter) for implicit parameter lambdas.
	 *
	 * @param lambdaCtx  the resolved lambda context (must not be null)
	 * @param scopeChain the scope chain to push bindings into
	 * @param lambdaLiteral the ANTLR lambda literal for named param
	 *                      extraction, or {@code null}
	 */
	public static void pushLambdaBindings(LambdaContext lambdaCtx,
			ScopeChain scopeChain,
			KotlinParser.LambdaLiteralContext lambdaLiteral) {
		scopeChain.pushScope();
		if (lambdaCtx.hasReceiver()) {
			scopeChain.addBinding("this",
					lambdaCtx.getLambdaReceiverType());
		}
		if (lambdaCtx.hasImplicitParameter()) {
			KotlinType paramType =
					lambdaCtx.getImplicitParameterType();
			if (lambdaLiteral != null
					&& lambdaLiteral.lambdaParameters() != null) {
				List<KotlinParser.LambdaParameterContext> params =
						lambdaLiteral.lambdaParameters()
								.lambdaParameter();
				if (params != null && params.size() == 1) {
					KotlinParser.LambdaParameterContext param =
							params.get(0);
					if (param.variableDeclaration() != null
							&& param.variableDeclaration()
									.simpleIdentifier() != null) {
						String paramName = param
								.variableDeclaration()
								.simpleIdentifier().getText();
						scopeChain.addBinding(paramName, paramType);
						return;
					}
				}
			}
			scopeChain.addBinding("it", paramType);
		}
	}

	private KotlinType resolveTypeName(String typeName) {
		return KotlinType.resolve(typeName, importResolver);
	}
}
