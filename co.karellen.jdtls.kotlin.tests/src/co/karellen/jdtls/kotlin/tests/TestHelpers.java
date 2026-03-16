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
import java.nio.charset.StandardCharsets;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameRequestor;
import org.eclipse.jdt.internal.core.JavaModelManager;

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

	public static void waitUntilIndexesReady() {
		SearchEngine engine = new SearchEngine();
		try {
			JavaModelManager.getIndexManager().waitForIndex(false, null);
			engine.searchAllTypeNames(
					null,
					SearchPattern.R_EXACT_MATCH,
					"!@$#!@".toCharArray(),
					SearchPattern.R_PATTERN_MATCH | SearchPattern.R_CASE_SENSITIVE,
					IJavaSearchConstants.CLASS,
					SearchEngine.createWorkspaceScope(),
					new TypeNameRequestor() {
						@Override
						public void acceptType(int modifiers, char[] packageName,
								char[] simpleTypeName, char[][] enclosingTypeNames,
								String path) {
						}
					},
					IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH,
					null);
		} catch (CoreException e) {
			throw new RuntimeException("Failed waiting for indexes", e);
		}
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
