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
package co.karellen.jdtls.kotlin.tests;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Platform;
import org.eclipse.jdt.internal.core.search.indexing.IndexManager;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.core.search.TypeNameRequestor;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.osgi.framework.Bundle;

/**
 * Utility class for test workspace setup. Avoids depending on JDT Core's
 * internal test infrastructure ({@code ModifyingResourceTests}).
 *
 * @author Arcadiy Ivanov
 */
public final class TestHelpers {

	private TestHelpers() {
	}

	public static IJavaProject createJavaProject(String name, String... srcFolders) throws CoreException {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IProject project = root.getProject(name);
		if (project.exists()) {
			project.delete(true, true, null);
		}
		project.create(null);
		project.open(null);

		IProjectDescription description = project.getDescription();
		description.setNatureIds(new String[] { JavaCore.NATURE_ID });
		project.setDescription(description, null);

		IJavaProject javaProject = JavaCore.create(project);

		IClasspathEntry[] entries = new IClasspathEntry[srcFolders.length];
		for (int i = 0; i < srcFolders.length; i++) {
			IFolder srcFolder = project.getFolder(srcFolders[i]);
			if (!srcFolder.exists()) {
				createFolderRecursive(srcFolder);
			}
			entries[i] = JavaCore.newSourceEntry(srcFolder.getFullPath());
		}
		javaProject.setRawClasspath(entries, null);

		return javaProject;
	}

	/**
	 * Creates a Java project with source folders AND the JRE container on
	 * the classpath. Use this when tests need JDT type hierarchy APIs
	 * (e.g., IType.newSupertypeHierarchy) to resolve Java types.
	 * Note: JRE indexing adds significant time (~30s per project).
	 */
	public static IJavaProject createJavaProjectWithJRE(String name, String... srcFolders) throws CoreException {
		IJavaProject javaProject = createJavaProject(name, srcFolders);
		IClasspathEntry[] existing = javaProject.getRawClasspath();
		IClasspathEntry[] withJre = new IClasspathEntry[existing.length + 1];
		System.arraycopy(existing, 0, withJre, 0, existing.length);
		withJre[existing.length] = JavaCore.newContainerEntry(
				IPath.fromPortableString(
						"org.eclipse.jdt.launching.JRE_CONTAINER"));
		javaProject.setRawClasspath(withJre, null);
		return javaProject;
	}

	public static IFile getFile(String workspacePath) {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		return root.getFile(IPath.fromPortableString(workspacePath));
	}

	public static List<SearchMatch> filterKotlinMatches(
			List<SearchMatch> matches) {
		return matches.stream()
				.filter(m -> {
					if (m.getResource() == null) return false;
					String name = m.getResource().getName();
					return name.endsWith(".kt")
							|| name.endsWith(".kts");
				})
				.toList();
	}

	public static ICompilationUnit getJavaCompilationUnit(
			IJavaProject project,
			String packageName, String cuName) throws CoreException {
		for (IPackageFragmentRoot root : project
				.getPackageFragmentRoots()) {
			if (root.getKind() == IPackageFragmentRoot.K_SOURCE) {
				IPackageFragment pkg = root.getPackageFragment(
						packageName);
				if (pkg != null && pkg.exists()) {
					ICompilationUnit cu = pkg.getCompilationUnit(cuName);
					if (cu != null && cu.exists()) {
						return cu;
					}
				}
			}
		}
		return null;
	}

	public static IFile createFile(String workspacePath, String content) throws CoreException {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IFile file = root.getFile(IPath.fromPortableString(workspacePath));
		if (!file.getParent().exists()) {
			createFolderRecursive((IFolder) file.getParent());
		}
		ByteArrayInputStream stream = new ByteArrayInputStream(
				content.getBytes(StandardCharsets.UTF_8));
		if (file.exists()) {
			file.setContents(stream, true, false, null);
		} else {
			file.create(stream, true, null);
		}
		return file;
	}

	public static IFolder createFolder(String workspacePath) throws CoreException {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IFolder folder = root.getFolder(IPath.fromPortableString(workspacePath));
		if (!folder.exists()) {
			createFolderRecursive(folder);
		}
		return folder;
	}

	public static void deleteProject(String name) throws CoreException {
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IProject project = root.getProject(name);
		if (project.exists()) {
			project.delete(true, true, null);
		}
	}

	/**
	 * Waits for workspace auto-build to complete, then waits for
	 * search indexes. Use this when the test needs compiled Java
	 * types (e.g., for IType.newSupertypeHierarchy()).
	 */
	public static void waitForBuildAndIndexes() {
		try {
			org.eclipse.core.runtime.jobs.Job.getJobManager().join(
					ResourcesPlugin.FAMILY_AUTO_BUILD, null);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		waitUntilIndexesReady();
	}

	public static void waitUntilIndexesReady() {
		SearchEngine engine = new SearchEngine();
		IndexManager indexManager =
				JavaModelManager.getIndexManager();
		try {
			// Explicitly trigger indexing for all .kt/.kts files
			// in case DeltaProcessor hasn't fired yet. This is
			// deterministic — no sleep/polling needed.
			ensureKotlinFilesIndexed(indexManager);

			indexManager.waitForIndex(false, null);
			engine.searchAllTypeNames(
					null,
					SearchPattern.R_EXACT_MATCH,
					"!@$#!@".toCharArray(),
					SearchPattern.R_PATTERN_MATCH
							| SearchPattern.R_CASE_SENSITIVE,
					IJavaSearchConstants.CLASS,
					SearchEngine.createWorkspaceScope(),
					new TypeNameRequestor() {
						@Override
						public void acceptType(int modifiers,
								char[] packageName,
								char[] simpleTypeName,
								char[][] enclosingTypeNames,
								String path) {
						}
					},
					IJavaSearchConstants
							.WAIT_UNTIL_READY_TO_SEARCH,
					null);
			indexManager.waitForIndex(false, null);
		} catch (CoreException e) {
			throw new RuntimeException(
					"Failed waiting for indexes", e);
		}
	}

	private static void ensureKotlinFilesIndexed(
			IndexManager indexManager) {
		try {
			IWorkspaceRoot root =
					ResourcesPlugin.getWorkspace().getRoot();
			for (org.eclipse.core.resources.IProject project :
					root.getProjects()) {
				if (!project.isOpen()) {
					continue;
				}
				project.accept(resource -> {
					if (resource instanceof IFile file) {
						String name = file.getName();
						if (name.endsWith(".kt")
								|| name.endsWith(".kts")) {
							indexManager.addDerivedSource(file,
									file.getProject().getFullPath());
						}
					}
					return true;
				});
			}
		} catch (CoreException e) {
			// Non-fatal: delta processing may handle it
		}
	}

	/**
	 * Loads the content of a resource file from the test bundle.
	 */
	public static String loadResource(String resourcePath) {
		Bundle bundle = Platform.getBundle("co.karellen.jdtls.kotlin.tests");
		URL url = bundle.getEntry(resourcePath);
		if (url == null) {
			throw new RuntimeException("Resource not found: " + resourcePath);
		}
		try (InputStream is = url.openStream()) {
			return new String(is.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new RuntimeException("Failed to read resource: " + resourcePath, e);
		}
	}

	/**
	 * Creates a workspace file from a test bundle resource.
	 */
	public static IFile createFileFromResource(String workspacePath, String resourcePath) throws CoreException {
		return createFile(workspacePath, loadResource(resourcePath));
	}

	/**
	 * Searches for all type declarations matching the given name using all
	 * search participants (including contributed ones like Kotlin).
	 */
	public static List<SearchMatch> searchAllTypes(String typeName, IJavaProject project) throws CoreException {
		SearchPattern pattern = SearchPattern.createPattern(
				typeName,
				IJavaSearchConstants.TYPE,
				IJavaSearchConstants.DECLARATIONS,
				SearchPattern.R_EXACT_MATCH);
		return executeSearch(pattern, project);
	}

	/**
	 * Searches for type declarations matching the given name, returning only
	 * matches from .kt or .kts files.
	 */
	public static List<SearchMatch> searchKotlinTypes(String typeName, IJavaProject project) throws CoreException {
		List<SearchMatch> allMatches = searchAllTypes(typeName, project);
		return allMatches.stream()
				.filter(m -> m.getResource() != null
						&& (m.getResource().getName().endsWith(".kt")
								|| m.getResource().getName().endsWith(".kts")))
				.toList();
	}

	/**
	 * Searches for references to a type with the given name using all
	 * search participants.
	 */
	public static List<SearchMatch> searchTypeReferences(String typeName, IJavaProject project) throws CoreException {
		SearchPattern pattern = SearchPattern.createPattern(
				typeName,
				IJavaSearchConstants.TYPE,
				IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH);
		return executeSearch(pattern, project);
	}

	/**
	 * Searches for references to a method with the given name using all
	 * search participants.
	 */
	public static List<SearchMatch> searchMethodReferences(String methodName, IJavaProject project) throws CoreException {
		SearchPattern pattern = SearchPattern.createPattern(
				methodName,
				IJavaSearchConstants.METHOD,
				IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH);
		return executeSearch(pattern, project);
	}

	/**
	 * Searches for implementors of a type with the given name using all
	 * search participants.
	 */
	public static List<SearchMatch> searchImplementors(String typeName, IJavaProject project) throws CoreException {
		SearchPattern pattern = SearchPattern.createPattern(
				typeName,
				IJavaSearchConstants.TYPE,
				IJavaSearchConstants.IMPLEMENTORS,
				SearchPattern.R_EXACT_MATCH);
		return executeSearch(pattern, project);
	}

	/**
	 * Searches for method declarations matching the given name using all
	 * search participants.
	 */
	public static List<SearchMatch> searchMethodDeclarations(String methodName, IJavaProject project) throws CoreException {
		SearchPattern pattern = SearchPattern.createPattern(
				methodName,
				IJavaSearchConstants.METHOD,
				IJavaSearchConstants.DECLARATIONS,
				SearchPattern.R_EXACT_MATCH);
		return executeSearch(pattern, project);
	}

	/**
	 * Searches for field declarations matching the given name using all
	 * search participants.
	 */
	public static List<SearchMatch> searchFieldDeclarations(String fieldName, IJavaProject project) throws CoreException {
		SearchPattern pattern = SearchPattern.createPattern(
				fieldName,
				IJavaSearchConstants.FIELD,
				IJavaSearchConstants.DECLARATIONS,
				SearchPattern.R_EXACT_MATCH);
		return executeSearch(pattern, project);
	}

	/**
	 * Searches for field references matching the given name using all
	 * search participants.
	 */
	public static List<SearchMatch> searchFieldReferences(String fieldName, IJavaProject project) throws CoreException {
		SearchPattern pattern = SearchPattern.createPattern(
				fieldName,
				IJavaSearchConstants.FIELD,
				IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH);
		return executeSearch(pattern, project);
	}

	/**
	 * Searches for all occurrences (declarations + references) of a field
	 * using all search participants.
	 */
	public static List<SearchMatch> searchFieldAllOccurrences(String fieldName, IJavaProject project) throws CoreException {
		SearchPattern pattern = SearchPattern.createPattern(
				fieldName,
				IJavaSearchConstants.FIELD,
				IJavaSearchConstants.ALL_OCCURRENCES,
				SearchPattern.R_EXACT_MATCH);
		return executeSearch(pattern, project);
	}

	/**
	 * Searches for qualified method references (e.g., "TypeName.methodName")
	 * using all search participants. This sets the declaringSimpleName on
	 * the MethodPattern, enabling receiver type verification.
	 */
	public static List<SearchMatch> searchQualifiedMethodReferences(String qualifiedName, IJavaProject project) throws CoreException {
		SearchPattern pattern = SearchPattern.createPattern(
				qualifiedName,
				IJavaSearchConstants.METHOD,
				IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH);
		return executeSearch(pattern, project);
	}

	/**
	 * Searches for all occurrences (declarations + references) of a method
	 * using all search participants.
	 */
	public static List<SearchMatch> searchMethodAllOccurrences(String methodName, IJavaProject project) throws CoreException {
		SearchPattern pattern = SearchPattern.createPattern(
				methodName,
				IJavaSearchConstants.METHOD,
				IJavaSearchConstants.ALL_OCCURRENCES,
				SearchPattern.R_EXACT_MATCH);
		return executeSearch(pattern, project);
	}

	/**
	 * Executes a search using all search participants (default + contributed).
	 */
	public static List<SearchMatch> executeSearch(SearchPattern pattern, IJavaProject project) throws CoreException {
		return executeSearch(pattern, new IJavaProject[] { project });
	}

	/**
	 * Executes a search using all search participants across multiple projects.
	 * Mimics jdtls ReferencesHandler which creates a scope from ALL workspace projects.
	 */
	public static List<SearchMatch> executeSearch(SearchPattern pattern, IJavaProject... projects) throws CoreException {
		IJavaSearchScope scope = SearchEngine.createJavaSearchScope(projects);

		List<SearchMatch> matches = new ArrayList<>();
		SearchRequestor requestor = new SearchRequestor() {
			@Override
			public void acceptSearchMatch(SearchMatch match) {
				matches.add(match);
			}
		};

		new SearchEngine().search(
				pattern,
				SearchEngine.getSearchParticipants(),
				scope,
				requestor,
				null);

		return matches;
	}

	/**
	 * Simulates HoverInfoProvider.isResolved() logic: if the element is a
	 * TYPE, searches for it within a scope created from the given CU.
	 * Returns matches found; empty list means hover would fail.
	 */
	public static List<SearchMatch> simulateIsResolvedSearch(
			IJavaElement resolved, ICompilationUnit cu) throws CoreException {
		List<SearchMatch> matches = new ArrayList<>();
		if (resolved.getElementType() != IJavaElement.TYPE) {
			return matches;
		}
		SearchPattern pattern = SearchPattern.createPattern(
				resolved, IJavaSearchConstants.ALL_OCCURRENCES);
		if (pattern == null) {
			return matches;
		}
		IJavaSearchScope scope = SearchEngine.createJavaSearchScope(
				new IJavaElement[] { cu },
				IJavaSearchScope.SOURCES
				| IJavaSearchScope.APPLICATION_LIBRARIES
				| IJavaSearchScope.SYSTEM_LIBRARIES);
		new SearchEngine().search(pattern,
				SearchEngine.getSearchParticipants(), scope,
				new SearchRequestor() {
					@Override
					public void acceptSearchMatch(SearchMatch m) {
						if (m.getAccuracy()
								!= SearchMatch.A_INACCURATE) {
							matches.add(m);
						}
					}
				}, null);
		return matches;
	}

	private static void createFolderRecursive(IFolder folder) throws CoreException {
		if (!folder.getParent().exists() && folder.getParent() instanceof IFolder) {
			createFolderRecursive((IFolder) folder.getParent());
		}
		if (!folder.exists()) {
			folder.create(true, true, null);
		}
	}
}
