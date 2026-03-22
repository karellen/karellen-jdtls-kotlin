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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Project-wide symbol registry for Kotlin declarations. Thread-safe via
 * {@link ConcurrentHashMap}.
 *
 * @author Arcadiy Ivanov
 */
public class SymbolTable {

	private final ConcurrentHashMap<String, TypeSymbol> typesByFQN = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, List<TypeSymbol>> typesBySimpleName = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, List<MethodSymbol>> topLevelFunctions = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, List<FieldSymbol>> topLevelProperties = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, List<String>> fileToTypes = new ConcurrentHashMap<>();

	/**
	 * Type symbol in the registry.
	 */
	public static class TypeSymbol {

		private final String fqn;
		private final String simpleName;
		private final String packageName;
		private final int modifiers;
		private final List<String> supertypeNames;
		private final List<MethodSymbol> methods;
		private final List<FieldSymbol> fields;
		private final List<ConstructorSymbol> constructors;

		public TypeSymbol(String fqn, String simpleName, String packageName,
				int modifiers, List<String> supertypeNames,
				List<MethodSymbol> methods, List<FieldSymbol> fields,
				List<ConstructorSymbol> constructors) {
			this.fqn = fqn;
			this.simpleName = simpleName;
			this.packageName = packageName;
			this.modifiers = modifiers;
			this.supertypeNames = supertypeNames != null
					? Collections.unmodifiableList(supertypeNames)
					: Collections.emptyList();
			this.methods = methods != null
					? Collections.unmodifiableList(methods)
					: Collections.emptyList();
			this.fields = fields != null
					? Collections.unmodifiableList(fields)
					: Collections.emptyList();
			this.constructors = constructors != null
					? Collections.unmodifiableList(constructors)
					: Collections.emptyList();
		}

		public String getFqn() {
			return fqn;
		}

		public String getSimpleName() {
			return simpleName;
		}

		public String getPackageName() {
			return packageName;
		}

		public int getModifiers() {
			return modifiers;
		}

		public List<String> getSupertypeNames() {
			return supertypeNames;
		}

		public List<MethodSymbol> getMethods() {
			return methods;
		}

		public List<FieldSymbol> getFields() {
			return fields;
		}

		public List<ConstructorSymbol> getConstructors() {
			return constructors;
		}
	}

	/**
	 * Method symbol in the registry.
	 */
	public static class MethodSymbol {

		private final String name;
		private final List<String> parameterTypes;
		private final List<String> parameterNames;
		private final String returnTypeName;
		private final String receiverTypeName;
		private final int modifiers;
		private final String declaringTypeFQN;
		private final boolean hasDefaultParams;

		public MethodSymbol(String name, List<String> parameterTypes,
				List<String> parameterNames, String returnTypeName,
				String receiverTypeName, int modifiers,
				String declaringTypeFQN, boolean hasDefaultParams) {
			this.name = name;
			this.parameterTypes = parameterTypes != null
					? Collections.unmodifiableList(parameterTypes)
					: Collections.emptyList();
			this.parameterNames = parameterNames != null
					? Collections.unmodifiableList(parameterNames)
					: Collections.emptyList();
			this.returnTypeName = returnTypeName;
			this.receiverTypeName = receiverTypeName;
			this.modifiers = modifiers;
			this.declaringTypeFQN = declaringTypeFQN;
			this.hasDefaultParams = hasDefaultParams;
		}

		public String getName() {
			return name;
		}

		public List<String> getParameterTypes() {
			return parameterTypes;
		}

		public List<String> getParameterNames() {
			return parameterNames;
		}

		public String getReturnTypeName() {
			return returnTypeName;
		}

		public String getReceiverTypeName() {
			return receiverTypeName;
		}

		public int getModifiers() {
			return modifiers;
		}

		public String getDeclaringTypeFQN() {
			return declaringTypeFQN;
		}

		public boolean hasDefaultParams() {
			return hasDefaultParams;
		}
	}

	/**
	 * Field/property symbol in the registry.
	 */
	public static class FieldSymbol {

		private final String name;
		private final String typeName;
		private final int modifiers;
		private final String declaringTypeFQN;

		public FieldSymbol(String name, String typeName, int modifiers,
				String declaringTypeFQN) {
			this.name = name;
			this.typeName = typeName;
			this.modifiers = modifiers;
			this.declaringTypeFQN = declaringTypeFQN;
		}

		public String getName() {
			return name;
		}

		public String getTypeName() {
			return typeName;
		}

		public int getModifiers() {
			return modifiers;
		}

		public String getDeclaringTypeFQN() {
			return declaringTypeFQN;
		}
	}

	/**
	 * Constructor symbol in the registry.
	 */
	public static class ConstructorSymbol {

		private final List<String> parameterTypes;
		private final List<String> parameterNames;
		private final int modifiers;
		private final String declaringTypeFQN;

		public ConstructorSymbol(List<String> parameterTypes,
				List<String> parameterNames, int modifiers,
				String declaringTypeFQN) {
			this.parameterTypes = parameterTypes != null
					? Collections.unmodifiableList(parameterTypes)
					: Collections.emptyList();
			this.parameterNames = parameterNames != null
					? Collections.unmodifiableList(parameterNames)
					: Collections.emptyList();
			this.modifiers = modifiers;
			this.declaringTypeFQN = declaringTypeFQN;
		}

		public List<String> getParameterTypes() {
			return parameterTypes;
		}

		public List<String> getParameterNames() {
			return parameterNames;
		}

		public int getModifiers() {
			return modifiers;
		}

		public String getDeclaringTypeFQN() {
			return declaringTypeFQN;
		}
	}

	/**
	 * Adds a type symbol to the registry.
	 */
	public void addType(TypeSymbol type) {
		typesByFQN.put(type.getFqn(), type);
		typesBySimpleName
				.computeIfAbsent(type.getSimpleName(),
						k -> Collections.synchronizedList(new ArrayList<>()))
				.add(type);
	}

	/**
	 * Removes all type symbols that were registered from the given file.
	 *
	 * @param filePath the source file path
	 */
	public void removeTypesFromFile(String filePath) {
		List<String> fqns = fileToTypes.remove(filePath);
		if (fqns == null) {
			return;
		}
		for (String fqn : fqns) {
			TypeSymbol removed = typesByFQN.remove(fqn);
			if (removed != null) {
				List<TypeSymbol> byName = typesBySimpleName
						.get(removed.getSimpleName());
				if (byName != null) {
					byName.removeIf(t -> fqn.equals(t.getFqn()));
					if (byName.isEmpty()) {
						typesBySimpleName.remove(removed.getSimpleName());
					}
				}
			}
		}
	}

	/**
	 * Registers type symbols from a file, replacing any previous registrations
	 * for that file.
	 *
	 * @param filePath the source file path
	 * @param types    the type symbols to register
	 */
	public void addTypesFromFile(String filePath, List<TypeSymbol> types) {
		removeTypesFromFile(filePath);
		List<String> fqns = Collections
				.synchronizedList(new ArrayList<>());
		for (TypeSymbol type : types) {
			addType(type);
			fqns.add(type.getFqn());
		}
		fileToTypes.put(filePath, fqns);
	}

	/**
	 * Looks up a type by its fully qualified name.
	 *
	 * @param fqn the fully qualified name
	 * @return the type symbol, or {@code null} if not found
	 */
	public TypeSymbol lookupType(String fqn) {
		return typesByFQN.get(fqn);
	}

	/**
	 * Looks up types by simple name.
	 *
	 * @param simpleName the simple name
	 * @return the matching type symbols (never {@code null})
	 */
	public List<TypeSymbol> lookupTypesBySimpleName(String simpleName) {
		List<TypeSymbol> result = typesBySimpleName.get(simpleName);
		return result != null ? Collections.unmodifiableList(result)
				: Collections.emptyList();
	}

	/**
	 * Looks up methods on a type by name.
	 *
	 * @param typeFQN    the fully qualified name of the declaring type
	 * @param methodName the method name
	 * @return the matching method symbols (never {@code null})
	 */
	public List<MethodSymbol> lookupMethods(String typeFQN,
			String methodName) {
		TypeSymbol type = typesByFQN.get(typeFQN);
		if (type == null) {
			return Collections.emptyList();
		}
		List<MethodSymbol> result = new ArrayList<>();
		for (MethodSymbol method : type.getMethods()) {
			if (methodName.equals(method.getName())) {
				result.add(method);
			}
		}
		return result;
	}

	/**
	 * Looks up a field on a type by name.
	 *
	 * @param typeFQN   the fully qualified name of the declaring type
	 * @param fieldName the field name
	 * @return the field symbol, or {@code null} if not found
	 */
	public FieldSymbol lookupField(String typeFQN, String fieldName) {
		TypeSymbol type = typesByFQN.get(typeFQN);
		if (type == null) {
			return null;
		}
		for (FieldSymbol field : type.getFields()) {
			if (fieldName.equals(field.getName())) {
				return field;
			}
		}
		return null;
	}

	/**
	 * Adds a top-level function to the registry.
	 */
	public void addTopLevelFunction(MethodSymbol method) {
		topLevelFunctions
				.computeIfAbsent(method.getName(),
						k -> Collections.synchronizedList(new ArrayList<>()))
				.add(method);
	}

	/**
	 * Adds a top-level property to the registry.
	 */
	public void addTopLevelProperty(FieldSymbol field) {
		topLevelProperties
				.computeIfAbsent(field.getName(),
						k -> Collections.synchronizedList(new ArrayList<>()))
				.add(field);
	}

	/**
	 * Looks up top-level functions by name.
	 *
	 * @param name the function name
	 * @return the matching method symbols (never {@code null})
	 */
	public List<MethodSymbol> lookupTopLevelFunctions(String name) {
		List<MethodSymbol> result = topLevelFunctions.get(name);
		return result != null ? Collections.unmodifiableList(result)
				: Collections.emptyList();
	}

	/**
	 * Clears all symbols from the registry.
	 */
	public void clear() {
		typesByFQN.clear();
		typesBySimpleName.clear();
		topLevelFunctions.clear();
		topLevelProperties.clear();
		fileToTypes.clear();
	}

	/**
	 * Pre-populates the symbol table with well-known Kotlin stdlib type
	 * hierarchies. This enables {@link SubtypeChecker} to walk the
	 * supertype chain for common types (collections, numerics, etc.)
	 * without requiring the Kotlin stdlib JAR on the classpath.
	 */
	public void initStdlib() {
		// Root types
		addStdlibType("kotlin", "Any", List.of());
		addStdlibType("kotlin", "Nothing", List.of("Any"));
		addStdlibType("kotlin", "Comparable", List.of("Any"));
		addStdlibType("kotlin", "Enum", List.of("Comparable"));
		addStdlibType("kotlin", "Throwable", List.of("Any"));
		addStdlibType("kotlin", "Number", List.of("Any"));
		addStdlibType("kotlin", "CharSequence", List.of("Any"));

		// Primitive types
		addStdlibType("kotlin", "Int", List.of("Number", "Comparable"));
		addStdlibType("kotlin", "Long", List.of("Number", "Comparable"));
		addStdlibType("kotlin", "Double", List.of("Number", "Comparable"));
		addStdlibType("kotlin", "Float", List.of("Number", "Comparable"));
		addStdlibType("kotlin", "Short", List.of("Number", "Comparable"));
		addStdlibType("kotlin", "Byte", List.of("Number", "Comparable"));
		addStdlibType("kotlin", "Char", List.of("Comparable"));
		addStdlibType("kotlin", "Boolean", List.of("Comparable"));
		addStdlibType("kotlin", "String",
				List.of("Comparable", "CharSequence"));

		// Collection hierarchy
		addStdlibType("kotlin.collections", "Iterable", List.of("Any"));
		addStdlibType("kotlin.collections", "MutableIterable",
				List.of("Iterable"));
		addStdlibType("kotlin.collections", "Collection",
				List.of("Iterable"));
		addStdlibType("kotlin.collections", "MutableCollection",
				List.of("Collection", "MutableIterable"));
		addStdlibType("kotlin.collections", "List",
				List.of("Collection"));
		addStdlibType("kotlin.collections", "MutableList",
				List.of("List", "MutableCollection"));
		addStdlibType("kotlin.collections", "Set",
				List.of("Collection"));
		addStdlibType("kotlin.collections", "MutableSet",
				List.of("Set", "MutableCollection"));
		addStdlibType("kotlin.collections", "Map", List.of("Any"));
		addStdlibType("kotlin.collections", "MutableMap",
				List.of("Map"));
		addStdlibType("kotlin.collections", "Iterator", List.of("Any"));
		addStdlibType("kotlin.collections", "MutableIterator",
				List.of("Iterator"));
		addStdlibType("kotlin.collections", "ListIterator",
				List.of("Iterator"));
		addStdlibType("kotlin.collections", "MutableListIterator",
				List.of("ListIterator", "MutableIterator"));

		// Sequences
		addStdlibType("kotlin.sequences", "Sequence", List.of("Any"));

		// Ranges
		addStdlibType("kotlin.ranges", "ClosedRange", List.of("Any"));
		addStdlibType("kotlin.ranges", "IntRange",
				List.of("IntProgression", "ClosedRange"));
		addStdlibType("kotlin.ranges", "LongRange",
				List.of("LongProgression", "ClosedRange"));
		addStdlibType("kotlin.ranges", "CharRange",
				List.of("CharProgression", "ClosedRange"));
		addStdlibType("kotlin.ranges", "IntProgression",
				List.of("Iterable"));
		addStdlibType("kotlin.ranges", "LongProgression",
				List.of("Iterable"));
		addStdlibType("kotlin.ranges", "CharProgression",
				List.of("Iterable"));

		// Function types
		addStdlibType("kotlin", "Function", List.of("Any"));

		// Kotlin reflection
		addStdlibType("kotlin.reflect", "KClass", List.of("Any"));
		addStdlibType("kotlin.reflect", "KFunction",
				List.of("Function"));
		addStdlibType("kotlin.reflect", "KProperty", List.of("Any"));

		// Common Java types that Kotlin code interacts with
		addStdlibType("java.lang", "Object", List.of());
		addStdlibType("java.lang", "String",
				List.of("Comparable", "CharSequence"));
		addStdlibType("java.lang", "Number", List.of("Object"));
		addStdlibType("java.lang", "Integer",
				List.of("Number", "Comparable"));
		addStdlibType("java.lang", "Long",
				List.of("Number", "Comparable"));
		addStdlibType("java.lang", "Double",
				List.of("Number", "Comparable"));
		addStdlibType("java.lang", "Float",
				List.of("Number", "Comparable"));
		addStdlibType("java.lang", "Short",
				List.of("Number", "Comparable"));
		addStdlibType("java.lang", "Byte",
				List.of("Number", "Comparable"));
		addStdlibType("java.lang", "Character",
				List.of("Comparable"));
		addStdlibType("java.lang", "Boolean",
				List.of("Comparable"));
		addStdlibType("java.lang", "Enum",
				List.of("Comparable"));
		addStdlibType("java.lang", "Throwable", List.of("Object"));
		addStdlibType("java.lang", "Exception",
				List.of("Throwable"));
		addStdlibType("java.lang", "RuntimeException",
				List.of("Exception"));
		addStdlibType("java.lang", "Comparable", List.of("Object"));
		addStdlibType("java.lang", "CharSequence", List.of("Object"));
		addStdlibType("java.lang", "Iterable", List.of("Object"));
		addStdlibType("java.util", "Collection",
				List.of("Iterable"));
		addStdlibType("java.util", "List",
				List.of("Collection"));
		addStdlibType("java.util", "Set",
				List.of("Collection"));
		addStdlibType("java.util", "Map", List.of("Object"));
		addStdlibType("java.util", "Iterator", List.of("Object"));
		addStdlibType("java.util", "ArrayList",
				List.of("List", "RandomAccess"));
		addStdlibType("java.util", "LinkedList",
				List.of("List"));
		addStdlibType("java.util", "HashSet", List.of("Set"));
		addStdlibType("java.util", "LinkedHashSet",
				List.of("HashSet"));
		addStdlibType("java.util", "TreeSet", List.of("Set"));
		addStdlibType("java.util", "HashMap", List.of("Map"));
		addStdlibType("java.util", "LinkedHashMap",
				List.of("HashMap"));
		addStdlibType("java.util", "TreeMap", List.of("Map"));
		addStdlibType("java.io", "Serializable", List.of("Object"));
		addStdlibType("java.lang", "Cloneable", List.of("Object"));
		addStdlibType("java.lang", "AutoCloseable", List.of("Object"));
		addStdlibType("java.io", "Closeable",
				List.of("AutoCloseable"));
	}

	private void addStdlibType(String packageName, String simpleName,
			List<String> supertypeSimpleNames) {
		String fqn = packageName + "." + simpleName;
		if (typesByFQN.containsKey(fqn)) {
			return;
		}
		// Store supertypes as FQN where possible (same package first,
		// then well-known packages)
		List<String> resolvedSupertypes = new ArrayList<>();
		for (String superSimple : supertypeSimpleNames) {
			resolvedSupertypes.add(superSimple);
		}
		addType(new TypeSymbol(fqn, simpleName, packageName, 0,
				resolvedSupertypes,
				Collections.emptyList(), Collections.emptyList(),
				Collections.emptyList()));
	}
}
