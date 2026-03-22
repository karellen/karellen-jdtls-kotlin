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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.resources.IMarker;
import org.eclipse.jdt.core.CompletionRequestor;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.IBufferFactory;
import org.eclipse.jdt.core.ICodeCompletionRequestor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.ICompletionRequestor;
import org.eclipse.jdt.core.IImportContainer;
import org.eclipse.jdt.core.IImportDeclaration;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaModel;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.IPackageDeclaration;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.IProblemRequestor;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.WorkingCopyOwner;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.text.edits.TextEdit;
import org.eclipse.text.edits.UndoEdit;

/**
 * {@link ICompilationUnit} implementation wrapping a {@code .kt} {@link IFile}.
 * Provides the minimal contract needed by jdtls handlers:
 * {@link #getResource()} returns the {@code .kt} file and
 * {@link #getBuffer()} returns a {@link KotlinBuffer} for content access.
 *
 * @author Arcadiy Ivanov
 */
@SuppressWarnings("deprecation")
public class KotlinCompilationUnit implements ICompilationUnit {

	private final IFile file;
	private volatile KotlinBuffer buffer;
	private volatile KotlinElement.KotlinTypeElement[] types;
	private volatile IJavaElement[] allChildren;

	public KotlinCompilationUnit(IFile file) {
		this.file = file;
	}

	/**
	 * Sets the top-level type elements parsed from this file.
	 * Called by {@link KotlinModelManager}.
	 */
	void setTypes(KotlinElement.KotlinTypeElement[] types) {
		this.types = types;
	}

	/**
	 * Sets all top-level children (types, functions, properties).
	 * Called by {@link KotlinModelManager}.
	 */
	void setAllChildren(IJavaElement[] children) {
		this.allChildren = children;
	}

	// ---- Key methods for jdtls handler compatibility ----

	@CoverageExcludeGenerated
	@Override
	public IResource getResource() {
		return file;
	}

	@Override
	public IBuffer getBuffer() throws JavaModelException {
		if (buffer == null) {
			String contents = readFileContents();
			buffer = new KotlinBuffer(contents, this);
		}
		return buffer;
	}

	@CoverageExcludeGenerated
	@Override
	public boolean exists() {
		return file != null && file.exists();
	}

	@CoverageExcludeGenerated
	@Override
	public String getElementName() {
		return file != null ? file.getName() : "";
	}

	@CoverageExcludeGenerated
	@Override
	public int getElementType() {
		return IJavaElement.COMPILATION_UNIT;
	}

	@CoverageExcludeGenerated
	@Override
	public IPath getPath() {
		return file != null ? file.getFullPath() : null;
	}

	@CoverageExcludeGenerated
	@Override
	public IJavaProject getJavaProject() {
		if (file != null && file.getProject() != null) {
			return JavaCore.create(file.getProject());
		}
		return null;
	}

	@CoverageExcludeGenerated
	@Override
	public IJavaModel getJavaModel() {
		IJavaProject project = getJavaProject();
		return project != null ? project.getJavaModel() : null;
	}

	@CoverageExcludeGenerated
	@Override
	public IJavaElement getAncestor(int ancestorType) {
		if (ancestorType == IJavaElement.COMPILATION_UNIT) {
			return this;
		}
		if (ancestorType == IJavaElement.PACKAGE_FRAGMENT) {
			return getPackageFragment();
		}
		if (ancestorType == IJavaElement.PACKAGE_FRAGMENT_ROOT) {
			IPackageFragment pf = getPackageFragment();
			return pf != null ? pf.getParent() : null;
		}
		if (ancestorType == IJavaElement.JAVA_PROJECT) {
			return getJavaProject();
		}
		if (ancestorType == IJavaElement.JAVA_MODEL) {
			return getJavaModel();
		}
		return null;
	}

	@CoverageExcludeGenerated
	@Override
	public IJavaElement getParent() {
		IPackageFragment pf = getPackageFragment();
		return pf != null ? pf : getJavaProject();
	}

	@CoverageExcludeGenerated
	private IPackageFragment getPackageFragment() {
		IJavaProject project = getJavaProject();
		if (project == null || file == null) {
			return null;
		}
		try {
			IPackageFragmentRoot[] roots = project.getPackageFragmentRoots();
			for (IPackageFragmentRoot root : roots) {
				if (root.getPath().isPrefixOf(file.getFullPath())) {
					IPath relativePath = file.getFullPath()
							.makeRelativeTo(root.getPath())
							.removeLastSegments(1);
					String packageName = relativePath.segmentCount() > 0
							? relativePath.toString().replace('/', '.')
							: "";
					return root.getPackageFragment(packageName);
				}
			}
		} catch (JavaModelException e) {
			org.eclipse.core.runtime.Platform.getLog(
					KotlinCompilationUnit.class).warn(
					"Failed to resolve package fragment", e);
		}
		return null;
	}

	@CoverageExcludeGenerated
	@Override
	public IOpenable getOpenable() {
		return this;
	}

	@CoverageExcludeGenerated
	@Override
	public IResource getCorrespondingResource() {
		return file;
	}

	@CoverageExcludeGenerated
	@Override
	public IResource getUnderlyingResource() {
		return file;
	}

	@CoverageExcludeGenerated
	@Override
	public IJavaElement getPrimaryElement() {
		return this;
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

	@Override
	public String getHandleIdentifier() {
		return file != null ? file.getFullPath().toString() : "";
	}

	@CoverageExcludeGenerated
	@Override
	public ISchedulingRule getSchedulingRule() {
		return file;
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

	// ---- IOpenable stubs ----

	@CoverageExcludeGenerated
	@Override
	public void close() {
		buffer = null;
	}

	@CoverageExcludeGenerated
	@Override
	public String findRecommendedLineSeparator() {
		return "\n";
	}

	@CoverageExcludeGenerated
	@Override
	public boolean hasUnsavedChanges() {
		return false;
	}

	@CoverageExcludeGenerated
	@Override
	public boolean isConsistent() {
		return true;
	}

	@CoverageExcludeGenerated
	@Override
	public boolean isOpen() {
		return buffer != null;
	}

	@CoverageExcludeGenerated
	@Override
	public void makeConsistent(IProgressMonitor progress) {
		// No-op
	}

	@CoverageExcludeGenerated
	@Override
	public void open(IProgressMonitor progress) {
		// No-op: buffer created lazily
	}

	@CoverageExcludeGenerated
	@Override
	public void save(IProgressMonitor progress, boolean force) {
		// No-op: read-only
	}

	// ---- ISourceReference stubs ----

	@Override
	public String getSource() throws JavaModelException {
		IBuffer buf = getBuffer();
		return buf != null ? buf.getContents() : null;
	}

	@CoverageExcludeGenerated
	@Override
	public ISourceRange getSourceRange() {
		try {
			IBuffer buf = getBuffer();
			if (buf != null) {
				return new org.eclipse.jdt.core.SourceRange(0,
						buf.getLength());
			}
		} catch (JavaModelException e) {
			org.eclipse.core.runtime.Platform.getLog(
					KotlinCompilationUnit.class).warn(
					"Failed to get source range", e);
		}
		return new org.eclipse.jdt.core.SourceRange(0, 0);
	}

	@CoverageExcludeGenerated
	@Override
	public ISourceRange getNameRange() {
		return null;
	}

	// ---- IParent ----

	@Override
	public IJavaElement[] getChildren() {
		return allChildren != null ? allChildren.clone()
				: new IJavaElement[0];
	}

	@Override
	public boolean hasChildren() {
		return allChildren != null && allChildren.length > 0;
	}

	// ---- ITypeRoot ----

	@Override
	public IType findPrimaryType() {
		if (types != null && types.length > 0) {
			return types[0];
		}
		return null;
	}

	@Override
	public IJavaElement getElementAt(int position) {
		return findElementAt(position);
	}

	// ---- ICompilationUnit stubs ----

	@CoverageExcludeGenerated
	@Override
	public ICompilationUnit getWorkingCopy(IProgressMonitor monitor) {
		return this;
	}

	@CoverageExcludeGenerated
	@Override
	public ICompilationUnit getWorkingCopy(WorkingCopyOwner owner,
			IProgressMonitor monitor) {
		return this;
	}

	@CoverageExcludeGenerated
	@Override
	public ICompilationUnit getPrimary() {
		return this;
	}

	@CoverageExcludeGenerated
	@Override
	public WorkingCopyOwner getOwner() {
		return null;
	}

	@CoverageExcludeGenerated
	@Override
	public boolean isWorkingCopy() {
		return false;
	}

	@CoverageExcludeGenerated
	@Override
	public boolean hasResourceChanged() {
		return false;
	}

	@CoverageExcludeGenerated
	@Override
	public void becomeWorkingCopy(IProgressMonitor monitor) {
		// No-op
	}

	@CoverageExcludeGenerated
	@Override
	public void becomeWorkingCopy(IProblemRequestor problemRequestor,
			IProgressMonitor monitor) {
		// No-op
	}

	@CoverageExcludeGenerated
	@Override
	public ICompilationUnit getWorkingCopy(WorkingCopyOwner owner,
			IProblemRequestor problemRequestor, IProgressMonitor monitor) {
		return this;
	}

	@CoverageExcludeGenerated
	@Override
	public void commitWorkingCopy(boolean force, IProgressMonitor monitor) {
		// No-op
	}

	@CoverageExcludeGenerated
	@Override
	public void discardWorkingCopy() {
		// No-op
	}

	@CoverageExcludeGenerated
	@Override
	public ICompilationUnit findWorkingCopy(WorkingCopyOwner owner) {
		return null;
	}

	@Override
	public IType[] getAllTypes() {
		return types != null ? types.clone() : new IType[0];
	}

	@CoverageExcludeGenerated
	@Override
	public IImportDeclaration getImport(String name) {
		return null;
	}

	@CoverageExcludeGenerated
	@Override
	public IImportContainer getImportContainer() {
		return null;
	}

	@CoverageExcludeGenerated
	@Override
	public IImportDeclaration[] getImports() {
		return new IImportDeclaration[0];
	}

	@CoverageExcludeGenerated
	@Override
	public IPackageDeclaration getPackageDeclaration(String name) {
		return null;
	}

	@CoverageExcludeGenerated
	@Override
	public IPackageDeclaration[] getPackageDeclarations() {
		return new IPackageDeclaration[0];
	}

	@CoverageExcludeGenerated
	@Override
	public IType getType(String name) {
		return null;
	}

	@Override
	public IType[] getTypes() {
		return types != null ? types.clone() : new IType[0];
	}

	@CoverageExcludeGenerated
	@Override
	public IImportDeclaration createImport(String name, IJavaElement sibling,
			IProgressMonitor monitor) throws JavaModelException {
		throw new JavaModelException(new CoreException(
				org.eclipse.core.runtime.Status.error(
						"Cannot create import in Kotlin file")));
	}

	@CoverageExcludeGenerated
	@Override
	public IImportDeclaration createImport(String name, IJavaElement sibling,
			int flags, IProgressMonitor monitor) throws JavaModelException {
		throw new JavaModelException(new CoreException(
				org.eclipse.core.runtime.Status.error(
						"Cannot create import in Kotlin file")));
	}

	@CoverageExcludeGenerated
	@Override
	public IPackageDeclaration createPackageDeclaration(String name,
			IProgressMonitor monitor) throws JavaModelException {
		throw new JavaModelException(new CoreException(
				org.eclipse.core.runtime.Status.error(
						"Cannot create package declaration in Kotlin file")));
	}

	@CoverageExcludeGenerated
	@Override
	public IType createType(String contents, IJavaElement sibling,
			boolean force, IProgressMonitor monitor)
			throws JavaModelException {
		throw new JavaModelException(new CoreException(
				org.eclipse.core.runtime.Status.error(
						"Cannot create type in Kotlin file")));
	}

	@CoverageExcludeGenerated
	@Override
	public UndoEdit applyTextEdit(TextEdit edit, IProgressMonitor monitor)
			throws JavaModelException {
		throw new JavaModelException(new CoreException(
				org.eclipse.core.runtime.Status.error(
						"Cannot apply text edit to Kotlin file")));
	}

	@CoverageExcludeGenerated
	@Override
	public CompilationUnit reconcile(int astLevel,
			boolean forceProblemDetection, WorkingCopyOwner owner,
			IProgressMonitor monitor) {
		return null;
	}

	@CoverageExcludeGenerated
	@Override
	public CompilationUnit reconcile(int astLevel,
			boolean forceProblemDetection,
			boolean enableStatementsRecovery, WorkingCopyOwner owner,
			IProgressMonitor monitor) {
		return null;
	}

	@CoverageExcludeGenerated
	@Override
	public CompilationUnit reconcile(int astLevel, int reconcileFlags,
			WorkingCopyOwner owner, IProgressMonitor monitor) {
		return null;
	}

	@CoverageExcludeGenerated
	@Override
	public IJavaElement[] findElements(IJavaElement element) {
		return null;
	}

	@CoverageExcludeGenerated
	@Override
	public void restore() {
		// No-op
	}

	// ---- IWorkingCopy deprecated methods ----

	@CoverageExcludeGenerated
	@Override
	public void commit(boolean force, IProgressMonitor monitor) {
		// No-op
	}

	@CoverageExcludeGenerated
	@Override
	public void destroy() {
		// No-op
	}

	@CoverageExcludeGenerated
	@Override
	public IJavaElement findSharedWorkingCopy(IBufferFactory bufferFactory) {
		return null;
	}

	@CoverageExcludeGenerated
	@Override
	public IJavaElement getOriginal(IJavaElement workingCopyElement) {
		return workingCopyElement;
	}

	@CoverageExcludeGenerated
	@Override
	public IJavaElement getOriginalElement() {
		return this;
	}

	@CoverageExcludeGenerated
	@Override
	public IJavaElement getSharedWorkingCopy(IProgressMonitor monitor,
			IBufferFactory factory, IProblemRequestor problemRequestor) {
		return this;
	}

	@CoverageExcludeGenerated
	@Override
	public IJavaElement getWorkingCopy() {
		return this;
	}

	@CoverageExcludeGenerated
	@Override
	public IJavaElement getWorkingCopy(IProgressMonitor monitor,
			IBufferFactory factory, IProblemRequestor problemRequestor) {
		return this;
	}

	@CoverageExcludeGenerated
	@Override
	public boolean isBasedOn(IResource resource) {
		return file != null && file.equals(resource);
	}

	@CoverageExcludeGenerated
	@Override
	public IMarker[] reconcile() {
		return new IMarker[0];
	}

	@CoverageExcludeGenerated
	@Override
	public void reconcile(boolean forceProblemDetection,
			IProgressMonitor monitor) {
		// No-op
	}

	// ---- ICodeAssist stubs ----

	@CoverageExcludeGenerated
	@Override
	public void codeComplete(int offset, ICodeCompletionRequestor requestor)
			throws JavaModelException {
		// No-op
	}

	@CoverageExcludeGenerated
	@Override
	public void codeComplete(int offset, ICompletionRequestor requestor)
			throws JavaModelException {
		// No-op
	}

	@CoverageExcludeGenerated
	@Override
	public void codeComplete(int offset, ICompletionRequestor requestor,
			WorkingCopyOwner owner) throws JavaModelException {
		// No-op
	}

	@CoverageExcludeGenerated
	@Override
	public void codeComplete(int offset, CompletionRequestor requestor)
			throws JavaModelException {
		// No-op
	}

	@CoverageExcludeGenerated
	@Override
	public void codeComplete(int offset, CompletionRequestor requestor,
			IProgressMonitor monitor) throws JavaModelException {
		// No-op
	}

	@CoverageExcludeGenerated
	@Override
	public void codeComplete(int offset, CompletionRequestor requestor,
			WorkingCopyOwner owner) throws JavaModelException {
		// No-op
	}

	@CoverageExcludeGenerated
	@Override
	public void codeComplete(int offset, CompletionRequestor requestor,
			WorkingCopyOwner owner, IProgressMonitor monitor)
			throws JavaModelException {
		// No-op
	}

	@Override
	public IJavaElement[] codeSelect(int offset, int length)
			throws JavaModelException {
		IJavaElement element = findElementAt(offset);
		return element != null ? new IJavaElement[] { element }
				: new IJavaElement[0];
	}

	@CoverageExcludeGenerated
	@Override
	public IJavaElement[] codeSelect(int offset, int length,
			WorkingCopyOwner owner) throws JavaModelException {
		return codeSelect(offset, length);
	}

	// ---- ISourceManipulation stubs ----

	@CoverageExcludeGenerated
	@Override
	public void copy(IJavaElement container, IJavaElement sibling,
			String rename, boolean replace, IProgressMonitor monitor)
			throws JavaModelException {
		throw new JavaModelException(new CoreException(
				org.eclipse.core.runtime.Status.error(
						"Cannot copy Kotlin compilation unit")));
	}

	@CoverageExcludeGenerated
	@Override
	public void delete(boolean force, IProgressMonitor monitor)
			throws JavaModelException {
		throw new JavaModelException(new CoreException(
				org.eclipse.core.runtime.Status.error(
						"Cannot delete Kotlin compilation unit")));
	}

	@CoverageExcludeGenerated
	@Override
	public void move(IJavaElement container, IJavaElement sibling,
			String rename, boolean replace, IProgressMonitor monitor)
			throws JavaModelException {
		throw new JavaModelException(new CoreException(
				org.eclipse.core.runtime.Status.error(
						"Cannot move Kotlin compilation unit")));
	}

	@CoverageExcludeGenerated
	@Override
	public void rename(String name, boolean replace,
			IProgressMonitor monitor) throws JavaModelException {
		throw new JavaModelException(new CoreException(
				org.eclipse.core.runtime.Status.error(
						"Cannot rename Kotlin compilation unit")));
	}

	// ---- Private helpers ----

	private IJavaElement findElementAt(int offset) {
		if (allChildren == null) {
			return null;
		}
		return findElementInChildren(allChildren, offset);
	}

	private static IJavaElement findElementInChildren(
			IJavaElement[] children, int offset) {
		for (IJavaElement child : children) {
			if (child instanceof KotlinElement ke) {
				ISourceRange range = ke.getSourceRange();
				if (range != null
						&& offset >= range.getOffset()
						&& offset < range.getOffset()
								+ range.getLength()) {
					// Check nested children for more specific match
					if (ke.hasChildren()) {
						IJavaElement nested = findElementInChildren(
								ke.getChildren(), offset);
						if (nested != null) {
							return nested;
						}
					}
					return ke;
				}
			}
		}
		return null;
	}

	private String readFileContents() {
		if (file == null || !file.exists()) {
			return "";
		}
		try (InputStream is = file.getContents(true)) {
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		} catch (CoreException | IOException e) {
			org.eclipse.core.runtime.Platform.getLog(
					KotlinCompilationUnit.class).warn(
					"Failed to read file contents: "
							+ file.getFullPath(), e);
			return "";
		}
	}
}
