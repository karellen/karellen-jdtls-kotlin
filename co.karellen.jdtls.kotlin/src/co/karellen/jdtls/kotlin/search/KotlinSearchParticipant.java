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
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchDocument;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.internal.core.index.IndexLocation;
import org.eclipse.jdt.internal.core.search.indexing.IIndexConstants;
import org.eclipse.jdt.internal.core.search.indexing.IndexManager;
import org.eclipse.jdt.internal.core.search.matching.TypeDeclarationPattern;

/**
 * Kotlin search participant for {@code .kt} and {@code .kts} files.
 * <p>
 * Registers via the {@code org.eclipse.jdt.core.searchParticipant} extension
 * point. Currently emits index entries derived from file paths (stub parsing);
 * will be replaced with kotlinx/ast-based parsing.
 *
 * @author Arcadiy Ivanov
 */
public class KotlinSearchParticipant extends SearchParticipant {

	public KotlinSearchParticipant() {
	}

	@Override
	public String getDescription() {
		return "Kotlin Search Participant";
	}

	@Override
	public SearchDocument getDocument(String documentPath) {
		return new KotlinSearchDocument(documentPath, this);
	}

	@Override
	public void indexDocument(SearchDocument document, IPath indexLocation) {
		document.removeAllIndexEntries();

		String path = document.getPath();
		String className = deriveClassName(path);
		if (className == null) {
			return;
		}

		String packageName = derivePackageName(path);
		char[] indexKey = TypeDeclarationPattern.createIndexKey(
				0, // modifiers — stub; real impl will extract from AST
				className.toCharArray(),
				packageName != null ? packageName.toCharArray() : null,
				null, // no enclosing types
				false // not secondary
		);
		document.addIndexEntry(IIndexConstants.TYPE_DECL, indexKey);
	}

	@Override
	public void locateMatches(SearchDocument[] documents, SearchPattern pattern,
			IJavaSearchScope scope, SearchRequestor requestor,
			IProgressMonitor monitor) throws CoreException {
		for (SearchDocument document : documents) {
			if (monitor != null && monitor.isCanceled()) {
				return;
			}
			String path = document.getPath();
			String className = deriveClassName(path);
			if (className == null) {
				continue;
			}
			IResource resource = ResourcesPlugin.getWorkspace().getRoot()
					.findMember(IPath.fromPortableString(path));
			if (resource == null) {
				continue;
			}
			SearchMatch match = new SearchMatch(
					null, // element — stub; real impl will resolve IJavaElement
					SearchMatch.A_ACCURATE,
					0, // offset
					className.length(), // length
					this,
					resource
			);
			requestor.acceptSearchMatch(match);
		}
	}

	@Override
	public IPath[] selectIndexes(SearchPattern query, IJavaSearchScope scope) {
		IndexManager indexManager = JavaModelManager.getIndexManager();
		List<IPath> indexes = new ArrayList<>();
		IPath[] enclosingPaths = scope.enclosingProjectsAndJars();
		for (IPath containerPath : enclosingPaths) {
			IndexLocation indexLocation = indexManager.computeIndexLocation(containerPath);
			if (indexLocation != null && indexLocation.getIndexFile() != null) {
				indexes.add(IPath.fromOSString(
						indexLocation.getIndexFile().getAbsolutePath()));
			}
		}
		return indexes.toArray(new IPath[0]);
	}

	private static String derivePackageName(String path) {
		if (path == null) {
			return null;
		}
		// Extract package from path: /Project/src/pkg/sub/Hello.kt -> pkg.sub
		// Stub heuristic; real impl will read the Kotlin package declaration
		int srcIdx = path.indexOf("/src/");
		if (srcIdx < 0) {
			return null;
		}
		String afterSrc = path.substring(srcIdx + 5);
		int lastSlash = afterSrc.lastIndexOf('/');
		if (lastSlash <= 0) {
			return null;
		}
		return afterSrc.substring(0, lastSlash).replace('/', '.');
	}

	private static String deriveClassName(String path) {
		if (path == null) {
			return null;
		}
		int lastSlash = path.lastIndexOf('/');
		String fileName = (lastSlash >= 0) ? path.substring(lastSlash + 1) : path;
		int dot = fileName.lastIndexOf('.');
		if (dot <= 0) {
			return null;
		}
		return fileName.substring(0, dot);
	}
}
