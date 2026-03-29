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
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Type representation for the Kotlin declaration resolver.
 *
 * @author Arcadiy Ivanov
 */
public class KotlinType {

	public static final KotlinType UNKNOWN = new KotlinType(null, "?");
	public static final KotlinType UNIT = new KotlinType("kotlin", "Unit");
	public static final KotlinType NOTHING = new KotlinType("kotlin", "Nothing");
	public static final KotlinType ANY = new KotlinType("kotlin", "Any");
	public static final KotlinType BOOLEAN = new KotlinType("kotlin", "Boolean");
	public static final KotlinType INT = new KotlinType("kotlin", "Int");
	public static final KotlinType LONG = new KotlinType("kotlin", "Long");
	public static final KotlinType DOUBLE = new KotlinType("kotlin", "Double");
	public static final KotlinType FLOAT = new KotlinType("kotlin", "Float");
	public static final KotlinType CHAR = new KotlinType("kotlin", "Char");
	public static final KotlinType BYTE = new KotlinType("kotlin", "Byte");
	public static final KotlinType SHORT = new KotlinType("kotlin", "Short");
	public static final KotlinType STRING = new KotlinType("kotlin", "String");
	public static final KotlinType INT_ARRAY = new KotlinType("kotlin", "IntArray");
	public static final KotlinType LONG_ARRAY = new KotlinType("kotlin", "LongArray");
	public static final KotlinType DOUBLE_ARRAY = new KotlinType("kotlin", "DoubleArray");
	public static final KotlinType FLOAT_ARRAY = new KotlinType("kotlin", "FloatArray");
	public static final KotlinType BOOLEAN_ARRAY = new KotlinType("kotlin", "BooleanArray");
	public static final KotlinType CHAR_ARRAY = new KotlinType("kotlin", "CharArray");
	public static final KotlinType BYTE_ARRAY = new KotlinType("kotlin", "ByteArray");
	public static final KotlinType SHORT_ARRAY = new KotlinType("kotlin", "ShortArray");

	private static final Map<String, String> KOTLIN_TO_JAVA;

	static {
		Map<String, String> map = new HashMap<>();
		// Primitive types
		map.put("kotlin.Int", "java.lang.Integer");
		map.put("kotlin.Long", "java.lang.Long");
		map.put("kotlin.Double", "java.lang.Double");
		map.put("kotlin.Float", "java.lang.Float");
		map.put("kotlin.Char", "java.lang.Character");
		map.put("kotlin.Byte", "java.lang.Byte");
		map.put("kotlin.Short", "java.lang.Short");
		map.put("kotlin.Boolean", "java.lang.Boolean");
		map.put("kotlin.String", "java.lang.String");
		map.put("kotlin.Any", "java.lang.Object");
		map.put("kotlin.Nothing", "java.lang.Void");
		map.put("kotlin.Unit", "void");
		// Collection types
		map.put("kotlin.collections.Iterable", "java.lang.Iterable");
		map.put("kotlin.collections.MutableIterable", "java.lang.Iterable");
		map.put("kotlin.collections.Collection", "java.util.Collection");
		map.put("kotlin.collections.MutableCollection", "java.util.Collection");
		map.put("kotlin.collections.List", "java.util.List");
		map.put("kotlin.collections.MutableList", "java.util.List");
		map.put("kotlin.collections.Set", "java.util.Set");
		map.put("kotlin.collections.MutableSet", "java.util.Set");
		map.put("kotlin.collections.Map", "java.util.Map");
		map.put("kotlin.collections.MutableMap", "java.util.Map");
		map.put("kotlin.collections.Iterator", "java.util.Iterator");
		map.put("kotlin.collections.MutableIterator", "java.util.Iterator");
		map.put("kotlin.collections.ListIterator", "java.util.ListIterator");
		map.put("kotlin.collections.MutableListIterator",
				"java.util.ListIterator");
		map.put("kotlin.collections.Map.Entry", "java.util.Map$Entry");
		map.put("kotlin.collections.MutableMap.MutableEntry",
				"java.util.Map$Entry");
		// Concrete collection typealiases (kotlin/collections/TypeAliases.kt)
		map.put("kotlin.collections.ArrayList", "java.util.ArrayList");
		map.put("kotlin.collections.HashMap", "java.util.HashMap");
		map.put("kotlin.collections.LinkedHashMap",
				"java.util.LinkedHashMap");
		map.put("kotlin.collections.HashSet", "java.util.HashSet");
		map.put("kotlin.collections.LinkedHashSet",
				"java.util.LinkedHashSet");
		map.put("kotlin.collections.RandomAccess",
				"java.util.RandomAccess");
		// Text typealiases (kotlin/text/TypeAliases.kt)
		map.put("kotlin.text.StringBuilder",
				"java.lang.StringBuilder");
		map.put("kotlin.text.Appendable", "java.lang.Appendable");
		map.put("kotlin.text.CharacterCodingException",
				"java.nio.charset.CharacterCodingException");
		// Core equivalences
		map.put("kotlin.Number", "java.lang.Number");
		map.put("kotlin.Comparable", "java.lang.Comparable");
		map.put("kotlin.Comparator", "java.util.Comparator");
		map.put("kotlin.CharSequence", "java.lang.CharSequence");
		map.put("kotlin.Throwable", "java.lang.Throwable");
		map.put("kotlin.Enum", "java.lang.Enum");
		map.put("kotlin.Cloneable", "java.lang.Cloneable");
		map.put("kotlin.Annotation", "java.lang.annotation.Annotation");
		// Exception typealiases (kotlin/TypeAliases.kt)
		map.put("kotlin.Error", "java.lang.Error");
		map.put("kotlin.Exception", "java.lang.Exception");
		map.put("kotlin.RuntimeException",
				"java.lang.RuntimeException");
		map.put("kotlin.IllegalArgumentException",
				"java.lang.IllegalArgumentException");
		map.put("kotlin.IllegalStateException",
				"java.lang.IllegalStateException");
		map.put("kotlin.IndexOutOfBoundsException",
				"java.lang.IndexOutOfBoundsException");
		map.put("kotlin.UnsupportedOperationException",
				"java.lang.UnsupportedOperationException");
		map.put("kotlin.ArithmeticException",
				"java.lang.ArithmeticException");
		map.put("kotlin.NumberFormatException",
				"java.lang.NumberFormatException");
		map.put("kotlin.NullPointerException",
				"java.lang.NullPointerException");
		map.put("kotlin.ClassCastException",
				"java.lang.ClassCastException");
		map.put("kotlin.AssertionError", "java.lang.AssertionError");
		map.put("kotlin.NoSuchElementException",
				"java.util.NoSuchElementException");
		map.put("kotlin.ConcurrentModificationException",
				"java.util.ConcurrentModificationException");
		KOTLIN_TO_JAVA = Collections.unmodifiableMap(map);

		// Reverse lookup: simple name → Kotlin FQN for auto-imported
		// types. Used by resolve() to map e.g. "Map" → "kotlin.collections.Map"
		// before the ImportResolver fallback which would misresolve
		// it to the current package.
		Map<String, String> autoMap = new HashMap<>();
		for (String kotlinFqn : map.keySet()) {
			int dot = kotlinFqn.lastIndexOf('.');
			String simpleName = dot >= 0
					? kotlinFqn.substring(dot + 1) : kotlinFqn;
			// Don't overwrite: first entry wins (non-Mutable
			// variants are listed first)
			autoMap.putIfAbsent(simpleName, kotlinFqn);
		}
		KOTLIN_AUTO_IMPORTS = Collections.unmodifiableMap(autoMap);
	}

	private static final Map<String, String> KOTLIN_AUTO_IMPORTS;

	private final String packageName;
	private final String simpleName;
	private final boolean nullable;
	private final List<KotlinType> typeArguments;

	public KotlinType(String packageName, String simpleName) {
		this(packageName, simpleName, false, Collections.emptyList());
	}

	public KotlinType(String packageName, String simpleName, boolean nullable) {
		this(packageName, simpleName, nullable, Collections.emptyList());
	}

	public KotlinType(String packageName, String simpleName, boolean nullable,
			List<KotlinType> typeArguments) {
		this.packageName = packageName;
		this.simpleName = simpleName;
		this.nullable = nullable;
		this.typeArguments = typeArguments != null
				? Collections.unmodifiableList(typeArguments)
				: Collections.emptyList();
	}

	public String getPackageName() {
		return packageName;
	}

	public String getSimpleName() {
		return simpleName;
	}

	public boolean isNullable() {
		return nullable;
	}

	public List<KotlinType> getTypeArguments() {
		return typeArguments;
	}

	public String getFQN() {
		if (packageName == null || packageName.isEmpty()) {
			return simpleName;
		}
		return packageName + "." + simpleName;
	}

	public boolean isUnknown() {
		return packageName == null && "?".equals(simpleName);
	}

	public KotlinType withNullable(boolean nullable) {
		return new KotlinType(packageName, simpleName, nullable, typeArguments);
	}

	public KotlinType withTypeArguments(List<KotlinType> typeArguments) {
		return new KotlinType(packageName, simpleName, nullable, typeArguments);
	}

	/**
	 * Returns the Java FQN corresponding to this Kotlin type, or the Kotlin
	 * FQN if no mapping exists.
	 */
	public String getJavaFQN() {
		String fqn = getFQN();
		String javaFQN = KOTLIN_TO_JAVA.get(fqn);
		return javaFQN != null ? javaFQN : fqn;
	}

	/**
	 * Returns the Kotlin-to-Java type mapping table.
	 */
	public static Map<String, String> getKotlinToJavaMapping() {
		return KOTLIN_TO_JAVA;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		KotlinType that = (KotlinType) o;
		return nullable == that.nullable
				&& Objects.equals(packageName, that.packageName)
				&& Objects.equals(simpleName, that.simpleName)
				&& Objects.equals(typeArguments, that.typeArguments);
	}

	@Override
	public int hashCode() {
		return Objects.hash(packageName, simpleName, nullable, typeArguments);
	}

	@Override
	public String toString() {
		String fqn = getFQN();
		return nullable ? fqn + "?" : fqn;
	}

	/**
	 * Returns the element type of a collection or array type. For
	 * generic types, returns the first type argument (or the second
	 * for Map types). For primitive arrays, returns the corresponding
	 * primitive type. For String, returns Char.
	 *
	 * @return the element type, or {@link #UNKNOWN} if not determinable
	 */
	public KotlinType getElementType() {
		if (!typeArguments.isEmpty()) {
			String fqn = getFQN();
			if (fqn != null
					&& (fqn.contains("Map") || fqn.contains("map"))
					&& typeArguments.size() > 1) {
				return typeArguments.get(1);
			}
			return typeArguments.get(0);
		}
		String fqn = getFQN();
		if (fqn == null) {
			return UNKNOWN;
		}
		switch (fqn) {
		case "kotlin.String":
		case "java.lang.String":
			return CHAR;
		case "kotlin.IntArray":
			return INT;
		case "kotlin.LongArray":
			return LONG;
		case "kotlin.DoubleArray":
			return DOUBLE;
		case "kotlin.FloatArray":
			return FLOAT;
		case "kotlin.BooleanArray":
			return BOOLEAN;
		case "kotlin.CharArray":
			return CHAR;
		case "kotlin.ByteArray":
			return BYTE;
		case "kotlin.ShortArray":
			return SHORT;
		default:
			return UNKNOWN;
		}
	}

	/**
	 * Resolves a type name string to a {@link KotlinType}. Handles
	 * nullable suffixes ({@code "String?"}), well-known primitive and
	 * standard library types, and falls back to import resolution.
	 *
	 * @param typeName       the type name to resolve
	 * @param importResolver the import resolver for non-primitive types
	 * @return the resolved type, or {@link #UNKNOWN} if unresolvable
	 */
	public static KotlinType resolve(String typeName,
			ImportResolver importResolver) {
		if (typeName == null) {
			return UNKNOWN;
		}
		if (typeName.endsWith("?")) {
			return resolve(
					typeName.substring(0, typeName.length() - 1),
					importResolver).withNullable(true);
		}
		switch (typeName) {
		case "Int":
			return INT;
		case "Long":
			return LONG;
		case "Double":
			return DOUBLE;
		case "Float":
			return FLOAT;
		case "Boolean":
			return BOOLEAN;
		case "String":
			return STRING;
		case "Char":
			return CHAR;
		case "Byte":
			return BYTE;
		case "Short":
			return SHORT;
		case "Unit":
			return UNIT;
		case "Any":
			return ANY;
		case "Nothing":
			return NOTHING;
		default:
			String fqn = importResolver.resolve(typeName);
			if (fqn != null && fqn.contains(".")) {
				// Check if this is a same-package fallback that
				// should be overridden by a Kotlin auto-import.
				String autoFqn = KOTLIN_AUTO_IMPORTS.get(typeName);
				if (autoFqn != null && !fqn.equals(autoFqn)
						&& !importResolver.isExplicitImport(
								typeName)) {
					int dot = autoFqn.lastIndexOf('.');
					return new KotlinType(
							autoFqn.substring(0, dot),
							autoFqn.substring(dot + 1));
				}
				int lastDot = fqn.lastIndexOf('.');
				return new KotlinType(fqn.substring(0, lastDot),
						fqn.substring(lastDot + 1));
			}
			return new KotlinType(null, typeName);
		}
	}
}
