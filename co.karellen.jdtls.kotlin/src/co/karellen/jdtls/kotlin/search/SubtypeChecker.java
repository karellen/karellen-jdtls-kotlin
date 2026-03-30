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
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;

/**
 * Checks subtype relationships between {@link KotlinType} instances using
 * the symbol table's supertype hierarchy, Kotlin-Java type equivalence,
 * and nullable compatibility rules.
 *
 * @author Arcadiy Ivanov
 */
public class SubtypeChecker {

	private static final Map<String, Set<String>> NUMERIC_WIDENINGS;

	static {
		Map<String, Set<String>> map = new HashMap<>();
		Set<String> byteTargets = new HashSet<>();
		byteTargets.add("kotlin.Short");
		byteTargets.add("kotlin.Int");
		byteTargets.add("kotlin.Long");
		byteTargets.add("kotlin.Float");
		byteTargets.add("kotlin.Double");
		map.put("kotlin.Byte", Collections.unmodifiableSet(byteTargets));

		Set<String> shortTargets = new HashSet<>();
		shortTargets.add("kotlin.Int");
		shortTargets.add("kotlin.Long");
		shortTargets.add("kotlin.Float");
		shortTargets.add("kotlin.Double");
		map.put("kotlin.Short", Collections.unmodifiableSet(shortTargets));

		Set<String> intTargets = new HashSet<>();
		intTargets.add("kotlin.Long");
		intTargets.add("kotlin.Float");
		intTargets.add("kotlin.Double");
		map.put("kotlin.Int", Collections.unmodifiableSet(intTargets));

		Set<String> longTargets = new HashSet<>();
		longTargets.add("kotlin.Float");
		longTargets.add("kotlin.Double");
		map.put("kotlin.Long", Collections.unmodifiableSet(longTargets));

		Set<String> floatTargets = new HashSet<>();
		floatTargets.add("kotlin.Double");
		map.put("kotlin.Float", Collections.unmodifiableSet(floatTargets));

		NUMERIC_WIDENINGS = Collections.unmodifiableMap(map);
	}

	private final SymbolTable symbolTable;
	private final IJavaProject javaProject;

	public SubtypeChecker(SymbolTable symbolTable) {
		this(symbolTable, null);
	}

	public SubtypeChecker(SymbolTable symbolTable,
			IJavaProject javaProject) {
		this.symbolTable = symbolTable;
		this.javaProject = javaProject;
	}

	/**
	 * Checks whether {@code subType} is a subtype of {@code superType}.
	 * Unknown types are assumed compatible (returns {@code true}).
	 *
	 * @param subType   the potential subtype
	 * @param superType the potential supertype
	 * @return {@code true} if the subtype relationship holds or is assumed
	 */
	public boolean isSubtype(KotlinType subType, KotlinType superType) {
		if (subType == null || superType == null) {
			return false;
		}
		if (subType.isUnknown() || superType.isUnknown()) {
			return true;
		}

		// Same type (case-insensitive: JDT patterns store names in lowercase)
		if (subType.getFQN().equalsIgnoreCase(superType.getFQN())) {
			return true;
		}

		// Any is supertype of everything
		if ("kotlin.Any".equalsIgnoreCase(superType.getFQN())
				|| "java.lang.Object".equalsIgnoreCase(superType.getFQN())) {
			return true;
		}

		// Nothing is subtype of everything
		if ("kotlin.Nothing".equalsIgnoreCase(subType.getFQN())
				|| "java.lang.Void".equalsIgnoreCase(subType.getFQN())) {
			return true;
		}

		// Nullable compatibility: Foo <: Foo?, but Foo? </: Foo
		if (superType.isNullable() && !subType.isNullable()) {
			return isSubtype(subType, superType.withNullable(false));
		}
		if (subType.isNullable() && !superType.isNullable()) {
			return false;
		}

		// Check Kotlin-Java type equivalence
		String subJava = subType.getJavaFQN();
		String superJava = superType.getJavaFQN();
		if (subJava.equals(superJava)) {
			return true;
		}

		// Walk supertype hierarchy
		return walkHierarchy(subType.getFQN(), superType.getFQN(),
				new HashSet<>());
	}

	private boolean walkHierarchy(String subFQN, String targetFQN,
			Set<String> visited) {
		if (visited.contains(subFQN)) {
			return false;
		}
		visited.add(subFQN);

		SymbolTable.TypeSymbol typeSym = symbolTable.lookupType(subFQN);
		if (typeSym == null) {
			// Fall back to JDT type hierarchy for types not in the
			// Kotlin symbol table (e.g., Java types in multi-level
			// inheritance chains)
			return walkJdtHierarchy(subFQN, targetFQN, visited);
		}

		// Extract the target simple name for matching against unqualified
		// supertype names (the parser may store supertypes as simple names)
		String targetSimple = targetFQN;
		int targetDot = targetFQN.lastIndexOf('.');
		if (targetDot >= 0) {
			targetSimple = targetFQN.substring(targetDot + 1);
		}

		for (String supertypeName : typeSym.getSupertypeNames()) {
			if (supertypeName.equalsIgnoreCase(targetFQN)) {
				return true;
			}
			// Match simple name when supertype is unqualified
			// (case-insensitive because JDT patterns store names in lowercase)
			if (!supertypeName.contains(".")
					&& supertypeName.equalsIgnoreCase(targetSimple)) {
				return true;
			}
			// Try to resolve the supertype to a FQN for recursive walk
			String resolvedSuper = supertypeName;
			if (!supertypeName.contains(".")) {
				// Unqualified: assume same package as the subtype (this
				// works for both Kotlin symbol table lookups and JDT
				// delegation for Java types)
				String subPkg = subFQN.contains(".")
						? subFQN.substring(0, subFQN.lastIndexOf('.'))
						: null;
				if (subPkg != null) {
					resolvedSuper = subPkg + "." + supertypeName;
				}
			}
			if (walkHierarchy(resolvedSuper, targetFQN, visited)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Falls back to JDT's type hierarchy API for types not in the Kotlin
	 * symbol table. This handles multi-level Java inheritance chains
	 * (e.g., KotlinFoo → JavaBase → JavaGrandparent).
	 */
	private boolean walkJdtHierarchy(String typeFQN, String targetFQN,
			Set<String> visited) {
		if (javaProject == null) {
			return false;
		}
		try {
			IType jdtType = javaProject.findType(typeFQN);
			if (jdtType == null) {
				return false;
			}
			ITypeHierarchy hierarchy = jdtType
					.newSupertypeHierarchy(null);
			IType[] allSupertypes = hierarchy.getAllSupertypes(jdtType);
			for (IType supertype : allSupertypes) {
				String superFQN = supertype.getFullyQualifiedName();
				if (superFQN.equalsIgnoreCase(targetFQN)) {
					return true;
				}
				// Also check Kotlin equivalence
				String kotlinFQN = getKotlinEquivalent(superFQN);
				if (kotlinFQN != null
						&& kotlinFQN.equalsIgnoreCase(targetFQN)) {
					return true;
				}
			}
		} catch (JavaModelException e) {
			Platform.getLog(
					SubtypeChecker.class).warn(
					"JDT type hierarchy lookup failed", e);
		}
		return false;
	}

	/**
	 * Returns the Kotlin FQN equivalent for a Java FQN, or {@code null}.
	 */
	private static String getKotlinEquivalent(String javaFQN) {
		for (Map.Entry<String, String> entry : KotlinType
				.getKotlinToJavaMapping().entrySet()) {
			if (entry.getValue().equals(javaFQN)) {
				return entry.getKey();
			}
		}
		return null;
	}

	/**
	 * Checks whether a numeric widening conversion exists from
	 * {@code from} to {@code to}. Covers the Kotlin numeric hierarchy:
	 * Byte &lt; Short &lt; Int &lt; Long, Float &lt; Double, and
	 * integral types widening to floating-point types.
	 *
	 * @param from the source type
	 * @param to   the target type
	 * @return {@code true} if the widening is valid
	 */
	public boolean isNumericWidening(KotlinType from, KotlinType to) {
		if (from == null || to == null) {
			return false;
		}
		Set<String> targets = NUMERIC_WIDENINGS.get(from.getFQN());
		if (targets == null) {
			return false;
		}
		return targets.contains(to.getFQN());
	}

	/**
	 * Finds the common supertype of two types. Used for inferring the
	 * result type of if/when branches. Returns the narrower type if one
	 * is a subtype of the other, or {@link KotlinType#ANY} otherwise.
	 *
	 * @param a the first type
	 * @param b the second type
	 * @return the common supertype
	 */
	public KotlinType commonSupertype(KotlinType a, KotlinType b) {
		if (a == null || a.isUnknown()) {
			return b;
		}
		if (b == null || b.isUnknown()) {
			return a;
		}
		if (isSubtype(a, b)) {
			return b;
		}
		if (isSubtype(b, a)) {
			return a;
		}
		return KotlinType.ANY;
	}
}
