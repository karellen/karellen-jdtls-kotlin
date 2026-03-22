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

import java.io.InputStream;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.jdt.core.CompletionRequestor;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IAnnotation;
import org.eclipse.jdt.core.IClassFile;
import org.eclipse.jdt.core.ICompletionRequestor;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IInitializer;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.ILocalVariable;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IOrdinaryClassFile;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.ITypeParameter;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.SourceRange;
import org.eclipse.jdt.core.WorkingCopyOwner;

/**
 * Abstract {@link IJavaElement} implementation for Kotlin declarations.
 * Each subclass represents a specific element kind (TYPE, METHOD, FIELD)
 * and is backed by a {@link KotlinCompilationUnit}.
 *
 * @author Arcadiy Ivanov
 */
public abstract class KotlinElement implements IMember {

	private final String name;
	private final int elementType;
	private final KotlinCompilationUnit compilationUnit;
	private final int sourceOffset;
	private final int sourceLength;
	private final int kotlinModifiers;

	protected KotlinElement(String name, int elementType,
			KotlinCompilationUnit compilationUnit,
			int sourceOffset, int sourceLength, int kotlinModifiers) {
		this.name = name;
		this.elementType = elementType;
		this.compilationUnit = compilationUnit;
		this.sourceOffset = sourceOffset;
		this.sourceLength = sourceLength;
		this.kotlinModifiers = kotlinModifiers;
	}

	@CoverageExcludeGenerated
	@Override
	public ISourceRange getSourceRange() {
		return new SourceRange(sourceOffset, sourceLength);
	}

	@CoverageExcludeGenerated
	@Override
	public ISourceRange getNameRange() {
		return new SourceRange(sourceOffset,
				name != null ? name.length() : 0);
	}

	@CoverageExcludeGenerated
	@Override
	public String getSource() {
		return null;
	}

	@CoverageExcludeGenerated
	@Override
	public String getElementName() {
		return name;
	}

	@CoverageExcludeGenerated
	@Override
	public int getElementType() {
		return elementType;
	}

	@CoverageExcludeGenerated
	@Override
	public boolean exists() {
		return true;
	}

	@Override
	public IJavaElement getAncestor(int ancestorType) {
		if (ancestorType == elementType) {
			return this;
		}
		if (ancestorType == IJavaElement.COMPILATION_UNIT) {
			return compilationUnit;
		}
		if (compilationUnit != null) {
			return compilationUnit.getAncestor(ancestorType);
		}
		return null;
	}

	@CoverageExcludeGenerated
	@Override
	public IJavaElement getParent() {
		return compilationUnit;
	}

	@CoverageExcludeGenerated
	@Override
	public IResource getResource() {
		return compilationUnit != null ? compilationUnit.getResource() : null;
	}

	@CoverageExcludeGenerated
	@Override
	public IResource getCorrespondingResource() {
		return getResource();
	}

	@CoverageExcludeGenerated
	@Override
	public IResource getUnderlyingResource() {
		return getResource();
	}

	@CoverageExcludeGenerated
	@Override
	public IJavaProject getJavaProject() {
		return compilationUnit != null ? compilationUnit.getJavaProject()
				: null;
	}

	@CoverageExcludeGenerated
	@Override
	public IJavaModel getJavaModel() {
		IJavaProject project = getJavaProject();
		return project != null ? project.getJavaModel() : null;
	}

	@CoverageExcludeGenerated
	@Override
	public IOpenable getOpenable() {
		return compilationUnit;
	}

	@CoverageExcludeGenerated
	@Override
	public IPath getPath() {
		return compilationUnit != null ? compilationUnit.getPath() : null;
	}

	@CoverageExcludeGenerated
	@Override
	public IJavaElement getPrimaryElement() {
		return this;
	}

	@Override
	public String getHandleIdentifier() {
		StringBuilder sb = new StringBuilder();
		if (compilationUnit != null) {
			sb.append(compilationUnit.getHandleIdentifier());
		}
		switch (elementType) {
		case IJavaElement.TYPE:
			sb.append('[');
			break;
		case IJavaElement.METHOD:
			sb.append('~');
			break;
		case IJavaElement.FIELD:
			sb.append('^');
			break;
		default:
			sb.append('/');
			break;
		}
		if (name != null) {
			sb.append(name);
		}
		return sb.toString();
	}

	@CoverageExcludeGenerated
	@Override
	public boolean isReadOnly() {
		return true;
	}

	@CoverageExcludeGenerated
	@Override
	public boolean isStructureKnown() {
		return true;
	}

	@CoverageExcludeGenerated
	@Override
	public ISchedulingRule getSchedulingRule() {
		IResource resource = getResource();
		if (resource != null) {
			return resource;
		}
		return null;
	}

	@CoverageExcludeGenerated
	@Override
	public String getAttachedJavadoc(IProgressMonitor monitor) {
		return null;
	}

	@CoverageExcludeGenerated
	@SuppressWarnings("unchecked")
	@Override
	public <T> T getAdapter(Class<T> adapter) {
		if (adapter.isInstance(this)) {
			return (T) this;
		}
		return null;
	}

	/**
	 * Returns the parent compilation unit.
	 */
	@CoverageExcludeGenerated
	@Override
	public KotlinCompilationUnit getCompilationUnit() {
		return compilationUnit;
	}

	/**
	 * Returns the Kotlin modifier flags from {@link KotlinDeclaration}.
	 */
	@CoverageExcludeGenerated
	public int getKotlinModifiers() {
		return kotlinModifiers;
	}

	// ---- IMember methods ----

	@CoverageExcludeGenerated
	@Override
	public IType getDeclaringType() {
		return null;
	}

	@Override
	public int getFlags() {
		return toJdtFlags(kotlinModifiers);
	}

	@CoverageExcludeGenerated
	@Override
	public ITypeRoot getTypeRoot() {
		return compilationUnit;
	}

	@CoverageExcludeGenerated
	@Override
	public IClassFile getClassFile() {
		return null;
	}

	@CoverageExcludeGenerated
	@Override
	public String[] getCategories() throws JavaModelException {
		return new String[0];
	}

	@CoverageExcludeGenerated
	@Override
	public ISourceRange getJavadocRange() {
		return null;
	}

	@CoverageExcludeGenerated
	@Override
	public int getOccurrenceCount() {
		return 1;
	}

	@CoverageExcludeGenerated
	@Override
	public IType getType(String name, int occurrenceCount) {
		return null;
	}

	@CoverageExcludeGenerated
	@Override
	public boolean isBinary() {
		return false;
	}

	// ---- ISourceManipulation (read-only) ----

	@CoverageExcludeGenerated
	@Override
	public void copy(IJavaElement container, IJavaElement sibling,
			String rename, boolean replace, IProgressMonitor monitor)
			throws JavaModelException {
		throw new JavaModelException(new CoreException(
				org.eclipse.core.runtime.Status.error(
						"Kotlin elements are read-only")));
	}

	@CoverageExcludeGenerated
	@Override
	public void delete(boolean force, IProgressMonitor monitor)
			throws JavaModelException {
		throw new JavaModelException(new CoreException(
				org.eclipse.core.runtime.Status.error(
						"Kotlin elements are read-only")));
	}

	@CoverageExcludeGenerated
	@Override
	public void move(IJavaElement container, IJavaElement sibling,
			String rename, boolean replace, IProgressMonitor monitor)
			throws JavaModelException {
		throw new JavaModelException(new CoreException(
				org.eclipse.core.runtime.Status.error(
						"Kotlin elements are read-only")));
	}

	@CoverageExcludeGenerated
	@Override
	public void rename(String name, boolean replace,
			IProgressMonitor monitor) throws JavaModelException {
		throw new JavaModelException(new CoreException(
				org.eclipse.core.runtime.Status.error(
						"Kotlin elements are read-only")));
	}

	// ---- IParent ----

	@CoverageExcludeGenerated
	@Override
	public IJavaElement[] getChildren() {
		return new IJavaElement[0];
	}

	@CoverageExcludeGenerated
	@Override
	public boolean hasChildren() {
		return false;
	}

	// ---- Handle encoding helpers ----

	/**
	 * Converts Kotlin modifier flags to JDT {@link Flags} bit mask.
	 */
	static int toJdtFlags(int kotlinMods) {
		int flags = 0;
		if ((kotlinMods & KotlinDeclaration.PUBLIC) != 0) {
			flags |= Flags.AccPublic;
		}
		if ((kotlinMods & KotlinDeclaration.PRIVATE) != 0) {
			flags |= Flags.AccPrivate;
		}
		if ((kotlinMods & KotlinDeclaration.PROTECTED) != 0) {
			flags |= Flags.AccProtected;
		}
		if ((kotlinMods & KotlinDeclaration.ABSTRACT) != 0) {
			flags |= Flags.AccAbstract;
		}
		if ((kotlinMods & KotlinDeclaration.FINAL) != 0) {
			flags |= Flags.AccFinal;
		}
		if ((kotlinMods & KotlinDeclaration.STATIC) != 0) {
			flags |= Flags.AccStatic;
		}
		if ((kotlinMods & KotlinDeclaration.ENUM) != 0) {
			flags |= Flags.AccEnum;
		}
		return flags;
	}

	/**
	 * Converts a Kotlin type name to a JDT type signature.
	 * Simple name mapping: "Int" -> "I", "String" -> "QString;", etc.
	 */
	static String toTypeSignature(String kotlinTypeName) {
		if (kotlinTypeName == null) {
			return Signature.SIG_VOID;
		}
		String baseName = kotlinTypeName;
		if (baseName.endsWith("?")) {
			baseName = baseName.substring(0, baseName.length() - 1);
		}
		switch (baseName) {
		case "Unit":
		case "kotlin.Unit":
			return Signature.SIG_VOID;
		case "Int":
		case "kotlin.Int":
			return Signature.SIG_INT;
		case "Long":
		case "kotlin.Long":
			return Signature.SIG_LONG;
		case "Short":
		case "kotlin.Short":
			return Signature.SIG_SHORT;
		case "Byte":
		case "kotlin.Byte":
			return Signature.SIG_BYTE;
		case "Float":
		case "kotlin.Float":
			return Signature.SIG_FLOAT;
		case "Double":
		case "kotlin.Double":
			return Signature.SIG_DOUBLE;
		case "Char":
		case "kotlin.Char":
			return Signature.SIG_CHAR;
		case "Boolean":
		case "kotlin.Boolean":
			return Signature.SIG_BOOLEAN;
		default:
			// Strip generic type arguments and function type syntax
			// that Signature.createTypeSignature cannot handle
			String simpleName = stripComplexTypeSyntax(baseName);
			return Signature.createTypeSignature(simpleName, false);
		}
	}

	private static String stripComplexTypeSyntax(String typeName) {
		// Remove generic type parameters: List<String> -> List
		int genericStart = typeName.indexOf('<');
		if (genericStart >= 0) {
			typeName = typeName.substring(0, genericStart);
		}
		// Handle function types: (A) -> B -> Function
		if (typeName.startsWith("(") || typeName.contains("->")) {
			return "Function";
		}
		// Handle companion member types: Foo.Companion.() -> Foo
		if (typeName.contains("(") || typeName.contains(")")) {
			int parenStart = typeName.indexOf('(');
			if (parenStart > 0) {
				typeName = typeName.substring(0, parenStart);
			} else {
				return "Function";
			}
		}
		return typeName;
	}

	// ---- Concrete subclasses ----

	/**
	 * A Kotlin type element (class, interface, object, enum, annotation).
	 * Implements {@link IType} so jdtls handlers can access type metadata.
	 */
	@SuppressWarnings("deprecation")
	public static class KotlinTypeElement extends KotlinElement
			implements IType {

		private final String packageName;
		private final String enclosingTypeName;
		private final KotlinDeclaration.TypeDeclaration.Kind kind;
		private final String[] supertypeNames;
		private IJavaElement[] children;

		public KotlinTypeElement(String name,
				KotlinCompilationUnit compilationUnit, String packageName,
				String enclosingTypeName,
				int sourceOffset, int sourceLength) {
			this(name, compilationUnit, packageName, enclosingTypeName,
					sourceOffset, sourceLength, null, null, 0);
		}

		public KotlinTypeElement(String name,
				KotlinCompilationUnit compilationUnit, String packageName,
				String enclosingTypeName,
				int sourceOffset, int sourceLength,
				KotlinDeclaration.TypeDeclaration.Kind kind,
				String[] supertypeNames, int kotlinModifiers) {
			super(name, IJavaElement.TYPE, compilationUnit,
					sourceOffset, sourceLength, kotlinModifiers);
			this.packageName = packageName;
			this.enclosingTypeName = enclosingTypeName;
			this.kind = kind;
			this.supertypeNames = supertypeNames;
		}

		/**
		 * Sets the child elements of this type. Called during
		 * document symbol construction.
		 */
		public void setChildren(IJavaElement[] children) {
			this.children = children;
		}

		@CoverageExcludeGenerated
		public String getPackageName() {
			return packageName;
		}

		@CoverageExcludeGenerated
		public String getEnclosingTypeName() {
			return enclosingTypeName;
		}

		// ---- IType critical methods ----

		@Override
		public String getFullyQualifiedName() {
			return getFullyQualifiedName('.');
		}

		@Override
		public String getFullyQualifiedName(char enclosingTypeSeparator) {
			StringBuilder sb = new StringBuilder();
			if (packageName != null && !packageName.isEmpty()) {
				sb.append(packageName).append('.');
			}
			if (enclosingTypeName != null && !enclosingTypeName.isEmpty()) {
				sb.append(enclosingTypeName)
						.append(enclosingTypeSeparator);
			}
			sb.append(getElementName());
			return sb.toString();
		}

		@Override
		public boolean isInterface() throws JavaModelException {
			return kind == KotlinDeclaration.TypeDeclaration.Kind.INTERFACE;
		}

		@Override
		public boolean isEnum() throws JavaModelException {
			return kind == KotlinDeclaration.TypeDeclaration.Kind.ENUM;
		}

		@Override
		public boolean isAnnotation() throws JavaModelException {
			return kind == KotlinDeclaration.TypeDeclaration.Kind.ANNOTATION;
		}

		@Override
		public boolean isClass() throws JavaModelException {
			return kind == KotlinDeclaration.TypeDeclaration.Kind.CLASS
					|| kind == KotlinDeclaration.TypeDeclaration.Kind.OBJECT
					|| kind == null;
		}

		@CoverageExcludeGenerated
		@Override
		public boolean isRecord() throws JavaModelException {
			return false;
		}

		@Override
		public boolean isSealed() throws JavaModelException {
			return (getKotlinModifiers()
					& KotlinDeclaration.SEALED) != 0;
		}

		@Override
		public String getSuperclassName() throws JavaModelException {
			if (supertypeNames != null && supertypeNames.length > 0) {
				return supertypeNames[0];
			}
			return null;
		}

		@Override
		public String getSuperclassTypeSignature()
				throws JavaModelException {
			String superName = getSuperclassName();
			return superName != null
					? Signature.createTypeSignature(superName, false)
					: null;
		}

		@Override
		public String[] getSuperInterfaceNames()
				throws JavaModelException {
			if (supertypeNames == null || supertypeNames.length <= 1) {
				return new String[0];
			}
			String[] result = new String[supertypeNames.length - 1];
			System.arraycopy(supertypeNames, 1, result, 0, result.length);
			return result;
		}

		@Override
		public String[] getSuperInterfaceTypeSignatures()
				throws JavaModelException {
			String[] names = getSuperInterfaceNames();
			String[] sigs = new String[names.length];
			for (int i = 0; i < names.length; i++) {
				sigs[i] = Signature.createTypeSignature(names[i], false);
			}
			return sigs;
		}

		@Override
		public String getTypeQualifiedName() {
			return getTypeQualifiedName('.');
		}

		@Override
		public String getTypeQualifiedName(char enclosingTypeSeparator) {
			if (enclosingTypeName != null && !enclosingTypeName.isEmpty()) {
				return enclosingTypeName + enclosingTypeSeparator
						+ getElementName();
			}
			return getElementName();
		}

		// ---- IParent override ----

		@Override
		public IJavaElement[] getChildren() {
			return children != null ? children.clone()
					: new IJavaElement[0];
		}

		@Override
		public boolean hasChildren() {
			return children != null && children.length > 0;
		}

		// ---- IType member accessors ----

		@Override
		public IField[] getFields() throws JavaModelException {
			if (children == null) {
				return new IField[0];
			}
			java.util.List<IField> result = new java.util.ArrayList<>();
			for (IJavaElement child : children) {
				if (child instanceof IField f) {
					result.add(f);
				}
			}
			return result.toArray(new IField[0]);
		}

		@Override
		public IMethod[] getMethods() throws JavaModelException {
			if (children == null) {
				return new IMethod[0];
			}
			java.util.List<IMethod> result = new java.util.ArrayList<>();
			for (IJavaElement child : children) {
				if (child instanceof IMethod m) {
					result.add(m);
				}
			}
			return result.toArray(new IMethod[0]);
		}

		@Override
		public IType[] getTypes() throws JavaModelException {
			if (children == null) {
				return new IType[0];
			}
			java.util.List<IType> result = new java.util.ArrayList<>();
			for (IJavaElement child : children) {
				if (child instanceof IType t) {
					result.add(t);
				}
			}
			return result.toArray(new IType[0]);
		}

		@CoverageExcludeGenerated
		@Override
		public IField getField(String name) {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public IMethod getMethod(String name,
				String[] parameterTypeSignatures) {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public IType getType(String name) {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public IInitializer getInitializer(int occurrenceCount) {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public IInitializer[] getInitializers()
				throws JavaModelException {
			return new IInitializer[0];
		}

		@Override
		public IPackageFragment getPackageFragment() {
			IJavaElement parent = getParent();
			if (parent != null) {
				return (IPackageFragment) parent.getAncestor(
						IJavaElement.PACKAGE_FRAGMENT);
			}
			return null;
		}

		@Override
		public String getKey() {
			StringBuilder sb = new StringBuilder("L");
			if (packageName != null && !packageName.isEmpty()) {
				sb.append(packageName.replace('.', '/'));
				sb.append('/');
			}
			sb.append(getElementName());
			sb.append(';');
			return sb.toString();
		}

		@CoverageExcludeGenerated
		@Override
		public IMethod[] findMethods(IMethod method) {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public IJavaElement[] getChildrenForCategory(String category)
				throws JavaModelException {
			return new IJavaElement[0];
		}

		@CoverageExcludeGenerated
		@Override
		public String getFullyQualifiedParameterizedName()
				throws JavaModelException {
			return getFullyQualifiedName();
		}

		@CoverageExcludeGenerated
		@Override
		public String[] getPermittedSubtypeNames()
				throws JavaModelException {
			return new String[0];
		}

		@CoverageExcludeGenerated
		@Override
		public String[] getTypeParameterSignatures()
				throws JavaModelException {
			return new String[0];
		}

		@CoverageExcludeGenerated
		@Override
		public ITypeParameter[] getTypeParameters()
				throws JavaModelException {
			return new ITypeParameter[0];
		}

		@CoverageExcludeGenerated
		@Override
		public ITypeParameter getTypeParameter(String name) {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public boolean isAnonymous() throws JavaModelException {
			return false;
		}

		@CoverageExcludeGenerated
		@Override
		public boolean isLocal() throws JavaModelException {
			return false;
		}

		@Override
		public boolean isMember() throws JavaModelException {
			return enclosingTypeName != null
					&& !enclosingTypeName.isEmpty();
		}

		@CoverageExcludeGenerated
		@Override
		public boolean isResolved() {
			return false;
		}

		@CoverageExcludeGenerated
		@Override
		public boolean isLambda() {
			return false;
		}

		@CoverageExcludeGenerated
		@Override
		public boolean isImplicitlyDeclared() throws JavaModelException {
			return false;
		}

		@CoverageExcludeGenerated
		@Override
		public IField getRecordComponent(String name) {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public IOrdinaryClassFile getClassFile() {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public ITypeHierarchy loadTypeHierachy(InputStream input,
				IProgressMonitor monitor) throws JavaModelException {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public ITypeHierarchy newSupertypeHierarchy(
				IProgressMonitor monitor) throws JavaModelException {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public ITypeHierarchy newSupertypeHierarchy(
				org.eclipse.jdt.core.ICompilationUnit[] workingCopies,
				IProgressMonitor monitor) throws JavaModelException {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public ITypeHierarchy newSupertypeHierarchy(
				org.eclipse.jdt.core.IWorkingCopy[] workingCopies,
				IProgressMonitor monitor) throws JavaModelException {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public ITypeHierarchy newSupertypeHierarchy(
				WorkingCopyOwner owner, IProgressMonitor monitor)
				throws JavaModelException {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public ITypeHierarchy newTypeHierarchy(IJavaProject project,
				IProgressMonitor monitor) throws JavaModelException {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public ITypeHierarchy newTypeHierarchy(IJavaProject project,
				WorkingCopyOwner owner, IProgressMonitor monitor)
				throws JavaModelException {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public ITypeHierarchy newTypeHierarchy(
				IProgressMonitor monitor) throws JavaModelException {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public ITypeHierarchy newTypeHierarchy(
				org.eclipse.jdt.core.ICompilationUnit[] workingCopies,
				IProgressMonitor monitor) throws JavaModelException {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public ITypeHierarchy newTypeHierarchy(
				org.eclipse.jdt.core.IWorkingCopy[] workingCopies,
				IProgressMonitor monitor) throws JavaModelException {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public ITypeHierarchy newTypeHierarchy(WorkingCopyOwner owner,
				IProgressMonitor monitor) throws JavaModelException {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public String[][] resolveType(String typeName)
				throws JavaModelException {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public String[][] resolveType(String typeName,
				WorkingCopyOwner owner) throws JavaModelException {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public IField createField(String contents, IJavaElement sibling,
				boolean force, IProgressMonitor monitor)
				throws JavaModelException {
			throw readOnlyException();
		}

		@CoverageExcludeGenerated
		@Override
		public IInitializer createInitializer(String contents,
				IJavaElement sibling, IProgressMonitor monitor)
				throws JavaModelException {
			throw readOnlyException();
		}

		@CoverageExcludeGenerated
		@Override
		public IMethod createMethod(String contents,
				IJavaElement sibling, boolean force,
				IProgressMonitor monitor) throws JavaModelException {
			throw readOnlyException();
		}

		@CoverageExcludeGenerated
		@Override
		public IType createType(String contents,
				IJavaElement sibling, boolean force,
				IProgressMonitor monitor) throws JavaModelException {
			throw readOnlyException();
		}

		@CoverageExcludeGenerated
		@Override
		public void codeComplete(char[] snippet, int insertion,
				int position, char[][] localVariableTypeNames,
				char[][] localVariableNames,
				int[] localVariableModifiers, boolean isStatic,
				ICompletionRequestor requestor)
				throws JavaModelException {
			// No-op
		}

		@CoverageExcludeGenerated
		@Override
		public void codeComplete(char[] snippet, int insertion,
				int position, char[][] localVariableTypeNames,
				char[][] localVariableNames,
				int[] localVariableModifiers, boolean isStatic,
				ICompletionRequestor requestor,
				WorkingCopyOwner owner) throws JavaModelException {
			// No-op
		}

		@CoverageExcludeGenerated
		@Override
		public void codeComplete(char[] snippet, int insertion,
				int position, char[][] localVariableTypeNames,
				char[][] localVariableNames,
				int[] localVariableModifiers, boolean isStatic,
				CompletionRequestor requestor)
				throws JavaModelException {
			// No-op
		}

		@CoverageExcludeGenerated
		@Override
		public void codeComplete(char[] snippet, int insertion,
				int position, char[][] localVariableTypeNames,
				char[][] localVariableNames,
				int[] localVariableModifiers, boolean isStatic,
				CompletionRequestor requestor,
				IProgressMonitor monitor) throws JavaModelException {
			// No-op
		}

		@CoverageExcludeGenerated
		@Override
		public void codeComplete(char[] snippet, int insertion,
				int position, char[][] localVariableTypeNames,
				char[][] localVariableNames,
				int[] localVariableModifiers, boolean isStatic,
				CompletionRequestor requestor,
				WorkingCopyOwner owner) throws JavaModelException {
			// No-op
		}

		@CoverageExcludeGenerated
		@Override
		public void codeComplete(char[] snippet, int insertion,
				int position, char[][] localVariableTypeNames,
				char[][] localVariableNames,
				int[] localVariableModifiers, boolean isStatic,
				CompletionRequestor requestor,
				WorkingCopyOwner owner, IProgressMonitor monitor)
				throws JavaModelException {
			// No-op
		}

		@CoverageExcludeGenerated
		@Override
		public IAnnotation getAnnotation(String name) {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public IAnnotation[] getAnnotations()
				throws JavaModelException {
			return new IAnnotation[0];
		}
	}

	/**
	 * A Kotlin method/function element.
	 * Implements {@link IMethod} so jdtls handlers can access method metadata.
	 */
	public static class KotlinMethodElement extends KotlinElement
			implements IMethod {

		private final int parameterCount;
		private final String[] parameterTypes;
		private final String[] parameterNames;
		private final String returnType;
		private final boolean constructor;
		private KotlinTypeElement declaringType;

		public KotlinMethodElement(String name,
				KotlinCompilationUnit compilationUnit, int parameterCount,
				int sourceOffset, int sourceLength) {
			this(name, compilationUnit, parameterCount, sourceOffset,
					sourceLength, null, null, null, false, 0);
		}

		public KotlinMethodElement(String name,
				KotlinCompilationUnit compilationUnit, int parameterCount,
				int sourceOffset, int sourceLength,
				String[] parameterTypes, String[] parameterNames,
				String returnType, boolean constructor,
				int kotlinModifiers) {
			super(name, IJavaElement.METHOD, compilationUnit,
					sourceOffset, sourceLength, kotlinModifiers);
			this.parameterCount = parameterCount;
			this.parameterTypes = parameterTypes;
			this.parameterNames = parameterNames;
			this.returnType = returnType;
			this.constructor = constructor;
		}

		void setDeclaringType(KotlinTypeElement type) {
			this.declaringType = type;
		}

		@CoverageExcludeGenerated
		@Override
		public IType getDeclaringType() {
			return declaringType;
		}

		@CoverageExcludeGenerated
		@Override
		public IJavaElement getParent() {
			return declaringType != null ? declaringType : super.getParent();
		}

		@CoverageExcludeGenerated
		public int getParameterCount() {
			return parameterCount;
		}

		// ---- IMethod critical methods ----

		@Override
		public String[] getParameterTypes() {
			if (parameterTypes != null) {
				return parameterTypes.clone();
			}
			return new String[0];
		}

		@Override
		public String[] getParameterNames() throws JavaModelException {
			if (parameterNames != null) {
				return parameterNames.clone();
			}
			return new String[0];
		}

		@CoverageExcludeGenerated
		@Override
		public String[] getRawParameterNames()
				throws JavaModelException {
			return getParameterNames();
		}

		@Override
		public int getNumberOfParameters() {
			return parameterCount;
		}

		@Override
		public String getReturnType() throws JavaModelException {
			return returnType != null ? returnType : Signature.SIG_VOID;
		}

		@Override
		public boolean isConstructor() throws JavaModelException {
			return constructor;
		}

		@Override
		public String getSignature() throws JavaModelException {
			String[] pTypes = getParameterTypes();
			String ret = getReturnType();
			return Signature.createMethodSignature(pTypes, ret);
		}

		@CoverageExcludeGenerated
		@Override
		public String getKey() {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public IMemberValuePair getDefaultValue()
				throws JavaModelException {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public String[] getExceptionTypes() throws JavaModelException {
			return new String[0];
		}

		@CoverageExcludeGenerated
		@Override
		public String[] getTypeParameterSignatures()
				throws JavaModelException {
			return new String[0];
		}

		@CoverageExcludeGenerated
		@Override
		public ITypeParameter[] getTypeParameters()
				throws JavaModelException {
			return new ITypeParameter[0];
		}

		@CoverageExcludeGenerated
		@Override
		public ITypeParameter getTypeParameter(String name) {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public ILocalVariable[] getParameters()
				throws JavaModelException {
			return new ILocalVariable[0];
		}

		@CoverageExcludeGenerated
		@Override
		public boolean isMainMethod() throws JavaModelException {
			return false;
		}

		@CoverageExcludeGenerated
		@Override
		public boolean isMainMethodCandidate()
				throws JavaModelException {
			return false;
		}

		@CoverageExcludeGenerated
		@Override
		public boolean isLambdaMethod() {
			return false;
		}

		@CoverageExcludeGenerated
		@Override
		public boolean isResolved() {
			return false;
		}

		@CoverageExcludeGenerated
		@Override
		public boolean isSimilar(IMethod method) {
			return false;
		}

		@CoverageExcludeGenerated
		@Override
		public IAnnotation getAnnotation(String name) {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public IAnnotation[] getAnnotations()
				throws JavaModelException {
			return new IAnnotation[0];
		}
	}

	/**
	 * A Kotlin property/field element.
	 * Implements {@link IField} so jdtls handlers can access field metadata.
	 */
	public static class KotlinFieldElement extends KotlinElement
			implements IField {

		private final String typeSignature;
		private final boolean enumConstant;
		private KotlinTypeElement declaringType;

		public KotlinFieldElement(String name,
				KotlinCompilationUnit compilationUnit,
				int sourceOffset, int sourceLength) {
			this(name, compilationUnit, sourceOffset, sourceLength,
					null, false, 0);
		}

		public KotlinFieldElement(String name,
				KotlinCompilationUnit compilationUnit,
				int sourceOffset, int sourceLength,
				String typeSignature, boolean enumConstant,
				int kotlinModifiers) {
			super(name, IJavaElement.FIELD, compilationUnit,
					sourceOffset, sourceLength, kotlinModifiers);
			this.typeSignature = typeSignature;
			this.enumConstant = enumConstant;
		}

		void setDeclaringType(KotlinTypeElement type) {
			this.declaringType = type;
		}

		@CoverageExcludeGenerated
		@Override
		public IType getDeclaringType() {
			return declaringType;
		}

		@CoverageExcludeGenerated
		@Override
		public IJavaElement getParent() {
			return declaringType != null ? declaringType : super.getParent();
		}

		// ---- IField critical methods ----

		@Override
		public String getTypeSignature() throws JavaModelException {
			return typeSignature != null ? typeSignature
					: Signature.createTypeSignature("Object", false);
		}

		@Override
		public boolean isEnumConstant() throws JavaModelException {
			return enumConstant;
		}

		@CoverageExcludeGenerated
		@Override
		public boolean isRecordComponent() throws JavaModelException {
			return false;
		}

		@CoverageExcludeGenerated
		@Override
		public boolean isResolved() {
			return false;
		}

		@CoverageExcludeGenerated
		@Override
		public Object getConstant() throws JavaModelException {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public String getKey() {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public IAnnotation getAnnotation(String name) {
			return null;
		}

		@CoverageExcludeGenerated
		@Override
		public IAnnotation[] getAnnotations()
				throws JavaModelException {
			return new IAnnotation[0];
		}
	}

	/**
	 * A stub element representing a callee for outgoing call hierarchy.
	 * Returns {@code false} from {@link #exists()} so that the call
	 * hierarchy engine resolves it via declaration search.
	 */
	static class CalleeStub extends KotlinElement {

		CalleeStub(String name, int elementType) {
			super(name, elementType, null, 0, 0, 0);
		}

		@CoverageExcludeGenerated
		@Override
		public boolean exists() {
			return false;
		}
	}

	/**
	 * Builds a {@link KotlinTypeElement} with its child members from a
	 * {@link KotlinDeclaration.TypeDeclaration}.
	 */
	public static KotlinTypeElement buildTypeElement(
			KotlinDeclaration.TypeDeclaration typeDecl,
			KotlinCompilationUnit cu, String packageName) {
		int sourceLength = typeDecl.getEndOffset()
				- typeDecl.getStartOffset() + 1;
		KotlinTypeElement typeElement = new KotlinTypeElement(
				typeDecl.getName(), cu, packageName,
				typeDecl.getEnclosingTypeName(),
				typeDecl.getStartOffset(), sourceLength,
				typeDecl.getKind(),
				typeDecl.getSupertypes().toArray(new String[0]),
				typeDecl.getModifiers());

		java.util.List<KotlinDeclaration> members = typeDecl.getMembers();
		if (members != null && !members.isEmpty()) {
			java.util.List<IJavaElement> childElements =
					new java.util.ArrayList<>();
			String typeFqn = typeDecl.getName();
			for (KotlinDeclaration member : members) {
				if (member instanceof KotlinDeclaration.MethodDeclaration md) {
					KotlinMethodElement me = buildMethodElement(md, cu);
					me.setDeclaringType(typeElement);
					childElements.add(me);
				} else if (member instanceof KotlinDeclaration.ConstructorDeclaration cd) {
					KotlinMethodElement me = buildConstructorElement(cd, cu);
					me.setDeclaringType(typeElement);
					childElements.add(me);
				} else if (member instanceof KotlinDeclaration.PropertyDeclaration pd) {
					int len = pd.getEndOffset() - pd.getStartOffset() + 1;
					KotlinFieldElement fe = new KotlinFieldElement(
							pd.getName(), cu,
							pd.getStartOffset(), len,
							toTypeSignature(pd.getTypeName()),
							false, pd.getModifiers());
					fe.setDeclaringType(typeElement);
					childElements.add(fe);
				} else if (member instanceof KotlinDeclaration.TypeDeclaration nested) {
					KotlinTypeElement nestedType = buildTypeElement(
							nested, cu, packageName);
					childElements.add(nestedType);
				}
			}
			typeElement.setChildren(
					childElements.toArray(new IJavaElement[0]));
		}
		return typeElement;
	}

	static KotlinMethodElement buildMethodElement(
			KotlinDeclaration.MethodDeclaration methodDecl,
			KotlinCompilationUnit cu) {
		int sourceLength = methodDecl.getEndOffset()
				- methodDecl.getStartOffset() + 1;
		java.util.List<KotlinDeclaration.MethodDeclaration.Parameter> params =
				methodDecl.getParameters();
		String[] paramTypes = new String[params.size()];
		String[] paramNames = new String[params.size()];
		for (int i = 0; i < params.size(); i++) {
			paramTypes[i] = toTypeSignature(params.get(i).getTypeName());
			paramNames[i] = params.get(i).getName();
		}
		return new KotlinMethodElement(
				methodDecl.getName(), cu, params.size(),
				methodDecl.getStartOffset(), sourceLength,
				paramTypes, paramNames,
				toTypeSignature(methodDecl.getReturnTypeName()),
				false, methodDecl.getModifiers());
	}

	static KotlinMethodElement buildConstructorElement(
			KotlinDeclaration.ConstructorDeclaration ctorDecl,
			KotlinCompilationUnit cu) {
		int sourceLength = ctorDecl.getEndOffset()
				- ctorDecl.getStartOffset() + 1;
		java.util.List<KotlinDeclaration.MethodDeclaration.Parameter> params =
				ctorDecl.getParameters();
		String[] paramTypes = new String[params.size()];
		String[] paramNames = new String[params.size()];
		for (int i = 0; i < params.size(); i++) {
			paramTypes[i] = toTypeSignature(params.get(i).getTypeName());
			paramNames[i] = params.get(i).getName();
		}
		return new KotlinMethodElement(
				ctorDecl.getName(), cu, params.size(),
				ctorDecl.getStartOffset(), sourceLength,
				paramTypes, paramNames, Signature.SIG_VOID,
				true, ctorDecl.getModifiers());
	}

	@CoverageExcludeGenerated
	private static JavaModelException readOnlyException() {
		return new JavaModelException(new CoreException(
				org.eclipse.core.runtime.Status.error(
						"Kotlin elements are read-only")));
	}
}
