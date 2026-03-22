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
import java.util.List;

/**
 * Abstract base class for Kotlin declarations extracted from source files.
 *
 * @author Arcadiy Ivanov
 */
public abstract class KotlinDeclaration {

	// Modifier flags
	public static final int PUBLIC = 1;
	public static final int PRIVATE = 2;
	public static final int PROTECTED = 4;
	public static final int INTERNAL = 8;
	public static final int ABSTRACT = 16;
	public static final int FINAL = 32;
	public static final int OPEN = 64;
	public static final int OVERRIDE = 128;
	public static final int STATIC = 256;
	public static final int DATA = 512;
	public static final int SEALED = 1024;
	public static final int ENUM = 2048;
	public static final int INNER = 4096;
	public static final int COMPANION = 8192;
	public static final int INLINE = 16384;
	public static final int OPERATOR = 32768;
	public static final int INFIX = 65536;
	public static final int SUSPEND = 131072;
	public static final int FUN_INTERFACE = 262144;
	public static final int VALUE = 524288;

	private final String name;
	private final int modifiers;
	private final int startOffset;
	private final int endOffset;
	private final String enclosingTypeName;

	protected KotlinDeclaration(String name, int modifiers, int startOffset,
			int endOffset, String enclosingTypeName) {
		this.name = name;
		this.modifiers = modifiers;
		this.startOffset = startOffset;
		this.endOffset = endOffset;
		this.enclosingTypeName = enclosingTypeName;
	}

	public String getName() {
		return name;
	}

	public int getModifiers() {
		return modifiers;
	}

	public int getStartOffset() {
		return startOffset;
	}

	public int getEndOffset() {
		return endOffset;
	}

	public String getEnclosingTypeName() {
		return enclosingTypeName;
	}

	/**
	 * A type declaration (class, interface, object, enum, annotation).
	 */
	public static class TypeDeclaration extends KotlinDeclaration {

		public enum Kind {
			CLASS, INTERFACE, OBJECT, ENUM, ANNOTATION
		}

		private final Kind kind;
		private final List<String> supertypes;
		private final List<String> typeParameters;
		private final List<KotlinDeclaration> members;

		public TypeDeclaration(String name, int modifiers, int startOffset,
				int endOffset, String enclosingTypeName, Kind kind,
				List<String> supertypes, List<String> typeParameters,
				List<KotlinDeclaration> members) {
			super(name, modifiers, startOffset, endOffset, enclosingTypeName);
			this.kind = kind;
			this.supertypes = supertypes != null
					? Collections.unmodifiableList(supertypes)
					: Collections.emptyList();
			this.typeParameters = typeParameters != null
					? Collections.unmodifiableList(typeParameters)
					: Collections.emptyList();
			this.members = members != null
					? Collections.unmodifiableList(members)
					: Collections.emptyList();
		}

		public Kind getKind() {
			return kind;
		}

		public List<String> getSupertypes() {
			return supertypes;
		}

		public List<String> getTypeParameters() {
			return typeParameters;
		}

		public List<KotlinDeclaration> getMembers() {
			return members;
		}
	}

	/**
	 * A function declaration.
	 */
	public static class MethodDeclaration extends KotlinDeclaration {

		/**
		 * A function parameter with a name and type name.
		 */
		public static class Parameter {

			private final String name;
			private final String typeName;

			public Parameter(String name, String typeName) {
				this.name = name;
				this.typeName = typeName;
			}

			public String getName() {
				return name;
			}

			public String getTypeName() {
				return typeName;
			}
		}

		private final List<Parameter> parameters;
		private final String returnTypeName;
		private final String receiverTypeName;
		private final List<String> typeParameters;
		private final boolean hasDefaultParams;
		private final List<KotlinDeclaration> members;

		public MethodDeclaration(String name, int modifiers, int startOffset,
				int endOffset, String enclosingTypeName,
				List<Parameter> parameters, String returnTypeName,
				String receiverTypeName, List<String> typeParameters,
				boolean hasDefaultParams) {
			this(name, modifiers, startOffset, endOffset,
					enclosingTypeName, parameters, returnTypeName,
					receiverTypeName, typeParameters, hasDefaultParams,
					null);
		}

		public MethodDeclaration(String name, int modifiers, int startOffset,
				int endOffset, String enclosingTypeName,
				List<Parameter> parameters, String returnTypeName,
				String receiverTypeName, List<String> typeParameters,
				boolean hasDefaultParams,
				List<KotlinDeclaration> members) {
			super(name, modifiers, startOffset, endOffset, enclosingTypeName);
			this.parameters = parameters != null
					? Collections.unmodifiableList(parameters)
					: Collections.emptyList();
			this.returnTypeName = returnTypeName;
			this.receiverTypeName = receiverTypeName;
			this.typeParameters = typeParameters != null
					? Collections.unmodifiableList(typeParameters)
					: Collections.emptyList();
			this.hasDefaultParams = hasDefaultParams;
			this.members = members != null
					? Collections.unmodifiableList(members)
					: Collections.emptyList();
		}

		public List<Parameter> getParameters() {
			return parameters;
		}

		public String getReturnTypeName() {
			return returnTypeName;
		}

		public String getReceiverTypeName() {
			return receiverTypeName;
		}

		public List<String> getTypeParameters() {
			return typeParameters;
		}

		public boolean hasDefaultParams() {
			return hasDefaultParams;
		}

		public List<KotlinDeclaration> getMembers() {
			return members;
		}
	}

	/**
	 * A property declaration (val or var).
	 */
	public static class PropertyDeclaration extends KotlinDeclaration {

		private final String typeName;
		private final boolean mutable;

		public PropertyDeclaration(String name, int modifiers, int startOffset,
				int endOffset, String enclosingTypeName, String typeName,
				boolean mutable) {
			super(name, modifiers, startOffset, endOffset, enclosingTypeName);
			this.typeName = typeName;
			this.mutable = mutable;
		}

		public String getTypeName() {
			return typeName;
		}

		public boolean isMutable() {
			return mutable;
		}
	}

	/**
	 * A type alias declaration.
	 */
	public static class TypeAliasDeclaration extends KotlinDeclaration {

		private final String aliasedTypeName;
		private final List<String> typeParameters;

		public TypeAliasDeclaration(String name, int modifiers,
				int startOffset, int endOffset, String enclosingTypeName,
				String aliasedTypeName, List<String> typeParameters) {
			super(name, modifiers, startOffset, endOffset, enclosingTypeName);
			this.aliasedTypeName = aliasedTypeName;
			this.typeParameters = typeParameters != null
					? Collections.unmodifiableList(typeParameters)
					: Collections.emptyList();
		}

		public String getAliasedTypeName() {
			return aliasedTypeName;
		}

		public List<String> getTypeParameters() {
			return typeParameters;
		}
	}

	/**
	 * A constructor declaration.
	 */
	public static class ConstructorDeclaration extends KotlinDeclaration {

		private final List<MethodDeclaration.Parameter> parameters;
		private final boolean primary;

		public ConstructorDeclaration(String name, int modifiers,
				int startOffset, int endOffset, String enclosingTypeName,
				List<MethodDeclaration.Parameter> parameters, boolean primary) {
			super(name, modifiers, startOffset, endOffset, enclosingTypeName);
			this.parameters = parameters != null
					? Collections.unmodifiableList(parameters)
					: Collections.emptyList();
			this.primary = primary;
		}

		public List<MethodDeclaration.Parameter> getParameters() {
			return parameters;
		}

		public boolean isPrimary() {
			return primary;
		}
	}
}
