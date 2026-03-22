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

/**
 * Selects the correct method overload given a receiver type and argument
 * types. Considers member methods, inherited methods, and extension
 * functions in scope.
 *
 * @author Arcadiy Ivanov
 */
public class OverloadResolver {

	private final SymbolTable symbolTable;
	private final SubtypeChecker subtypeChecker;
	private final ImportResolver importResolver;

	public OverloadResolver(SymbolTable symbolTable,
			SubtypeChecker subtypeChecker, ImportResolver importResolver) {
		this.symbolTable = symbolTable;
		this.subtypeChecker = subtypeChecker;
		this.importResolver = importResolver;
	}

	/**
	 * Result of overload resolution.
	 */
	public static class Resolution {

		private final SymbolTable.MethodSymbol method;
		private final boolean ambiguous;
		private final List<SymbolTable.MethodSymbol> candidates;

		/**
		 * Constructor for successful resolution.
		 */
		public Resolution(SymbolTable.MethodSymbol method) {
			this.method = method;
			this.ambiguous = false;
			this.candidates = Collections.singletonList(method);
		}

		/**
		 * Constructor for ambiguous resolution (multiple candidates).
		 */
		public Resolution(List<SymbolTable.MethodSymbol> candidates) {
			this.method = null;
			this.ambiguous = true;
			this.candidates = candidates != null
					? Collections.unmodifiableList(candidates)
					: Collections.emptyList();
		}

		private Resolution() {
			this.method = null;
			this.ambiguous = false;
			this.candidates = Collections.emptyList();
		}

		/**
		 * Creates a failed resolution (no candidates).
		 */
		public static Resolution none() {
			return new Resolution();
		}

		public boolean isResolved() {
			return method != null;
		}

		public boolean isAmbiguous() {
			return ambiguous;
		}

		public SymbolTable.MethodSymbol getMethod() {
			return method;
		}

		public List<SymbolTable.MethodSymbol> getCandidates() {
			return candidates;
		}
	}

	/**
	 * Resolves a method call on a receiver type.
	 *
	 * @param receiverType  the type of the receiver expression
	 * @param methodName    the method name being called
	 * @param argumentTypes the types of positional arguments
	 * @param namedArguments the names of named arguments, or {@code null}
	 * @return the resolution result
	 */
	public Resolution resolve(KotlinType receiverType, String methodName,
			List<KotlinType> argumentTypes, List<String> namedArguments) {
		if (receiverType == null || methodName == null
				|| argumentTypes == null) {
			return Resolution.none();
		}

		// 1. Collect candidates from receiver type and supertypes
		List<SymbolTable.MethodSymbol> candidates =
				collectCandidates(receiverType, methodName);

		// 2. Also collect extension functions in scope
		List<SymbolTable.MethodSymbol> extensions =
				symbolTable.lookupTopLevelFunctions(methodName);
		for (SymbolTable.MethodSymbol ext : extensions) {
			if (ext.getReceiverTypeName() != null) {
				KotlinType extReceiver =
						resolveTypeName(ext.getReceiverTypeName());
				if (subtypeChecker.isSubtype(receiverType, extReceiver)) {
					candidates.add(ext);
				}
			}
		}

		if (candidates.isEmpty()) {
			return Resolution.none();
		}

		// 3. Filter by arity
		List<SymbolTable.MethodSymbol> arityMatched =
				filterByArity(candidates, argumentTypes.size());
		if (arityMatched.isEmpty()) {
			return Resolution.none();
		}

		// 4. Filter by named arguments
		if (namedArguments != null && !namedArguments.isEmpty()) {
			arityMatched = filterByNamedArgs(arityMatched, namedArguments);
			if (arityMatched.isEmpty()) {
				return Resolution.none();
			}
		}

		// 5. Filter by argument type compatibility
		List<SymbolTable.MethodSymbol> applicable =
				filterByArgumentTypes(arityMatched, argumentTypes);
		if (applicable.isEmpty()) {
			// Fall back to arity-matched candidates (we might not have
			// type info)
			applicable = arityMatched;
		}

		// 6. Select most specific
		if (applicable.size() == 1) {
			return new Resolution(applicable.get(0));
		}

		SymbolTable.MethodSymbol best = selectMostSpecific(applicable);
		if (best != null) {
			return new Resolution(best);
		}

		return new Resolution(applicable); // ambiguous
	}

	private List<SymbolTable.MethodSymbol> collectCandidates(
			KotlinType receiverType, String methodName) {
		List<SymbolTable.MethodSymbol> result = new ArrayList<>();
		if (receiverType.isUnknown()) {
			return result;
		}

		Set<String> visited = new HashSet<>();
		collectFromHierarchy(receiverType.getFQN(), methodName, result,
				visited);

		// Also try Java FQN
		String javaFQN = receiverType.getJavaFQN();
		if (!javaFQN.equals(receiverType.getFQN())) {
			collectFromHierarchy(javaFQN, methodName, result, visited);
		}

		return result;
	}

	private void collectFromHierarchy(String typeFQN, String methodName,
			List<SymbolTable.MethodSymbol> result, Set<String> visited) {
		if (visited.contains(typeFQN)) {
			return;
		}
		visited.add(typeFQN);

		SymbolTable.TypeSymbol typeSym = symbolTable.lookupType(typeFQN);
		if (typeSym == null) {
			return;
		}

		for (SymbolTable.MethodSymbol method : typeSym.getMethods()) {
			if (method.getName().equals(methodName)) {
				result.add(method);
			}
		}

		for (String superFQN : typeSym.getSupertypeNames()) {
			collectFromHierarchy(superFQN, methodName, result, visited);
		}
	}

	private List<SymbolTable.MethodSymbol> filterByArity(
			List<SymbolTable.MethodSymbol> candidates, int argCount) {
		List<SymbolTable.MethodSymbol> result = new ArrayList<>();
		for (SymbolTable.MethodSymbol method : candidates) {
			int paramCount = method.getParameterTypes().size();
			if (paramCount == argCount) {
				result.add(method);
			} else if (method.hasDefaultParams()
					&& argCount <= paramCount) {
				result.add(method);
			}
		}
		return result;
	}

	private List<SymbolTable.MethodSymbol> filterByNamedArgs(
			List<SymbolTable.MethodSymbol> candidates,
			List<String> namedArgs) {
		List<SymbolTable.MethodSymbol> result = new ArrayList<>();
		for (SymbolTable.MethodSymbol method : candidates) {
			boolean allMatch = true;
			for (String namedArg : namedArgs) {
				if (!method.getParameterNames().contains(namedArg)) {
					allMatch = false;
					break;
				}
			}
			if (allMatch) {
				result.add(method);
			}
		}
		return result;
	}

	private List<SymbolTable.MethodSymbol> filterByArgumentTypes(
			List<SymbolTable.MethodSymbol> candidates,
			List<KotlinType> argumentTypes) {
		List<SymbolTable.MethodSymbol> result = new ArrayList<>();
		for (SymbolTable.MethodSymbol method : candidates) {
			if (isApplicable(method, argumentTypes)) {
				result.add(method);
			}
		}
		return result;
	}

	private boolean isApplicable(SymbolTable.MethodSymbol method,
			List<KotlinType> argumentTypes) {
		List<String> paramTypes = method.getParameterTypes();
		int checkCount = Math.min(argumentTypes.size(), paramTypes.size());
		for (int i = 0; i < checkCount; i++) {
			KotlinType argType = argumentTypes.get(i);
			if (argType.isUnknown()) {
				continue;
			}
			KotlinType paramType = resolveTypeName(paramTypes.get(i));
			if (!subtypeChecker.isSubtype(argType, paramType)
					&& !subtypeChecker.isNumericWidening(argType,
							paramType)) {
				return false;
			}
		}
		return true;
	}

	private SymbolTable.MethodSymbol selectMostSpecific(
			List<SymbolTable.MethodSymbol> candidates) {
		SymbolTable.MethodSymbol best = null;
		for (SymbolTable.MethodSymbol candidate : candidates) {
			if (best == null) {
				best = candidate;
				continue;
			}
			if (isMoreSpecific(candidate, best)) {
				best = candidate;
			}
		}
		// Verify best is actually more specific than all others
		for (SymbolTable.MethodSymbol candidate : candidates) {
			if (candidate != best
					&& !isMoreSpecific(best, candidate)) {
				return null; // ambiguous
			}
		}
		return best;
	}

	private boolean isMoreSpecific(SymbolTable.MethodSymbol a,
			SymbolTable.MethodSymbol b) {
		// Member beats extension
		boolean aIsExtension = a.getReceiverTypeName() != null;
		boolean bIsExtension = b.getReceiverTypeName() != null;
		if (!aIsExtension && bIsExtension) {
			return true;
		}
		if (aIsExtension && !bIsExtension) {
			return false;
		}

		// Fewer default params used is more specific
		if (!a.hasDefaultParams() && b.hasDefaultParams()) {
			return true;
		}
		if (a.hasDefaultParams() && !b.hasDefaultParams()) {
			return false;
		}

		return false;
	}

	private KotlinType resolveTypeName(String typeName) {
		return KotlinType.resolve(typeName, importResolver);
	}
}
