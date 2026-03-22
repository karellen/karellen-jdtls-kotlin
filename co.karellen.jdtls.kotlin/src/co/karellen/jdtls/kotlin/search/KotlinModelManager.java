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

import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.eclipse.core.resources.IFile;
import org.eclipse.jdt.core.IJavaElement;

/**
 * Central cache for parsed Kotlin file models and compilation units.
 * All consumers (search participant, compilation unit, future codeSelect)
 * access parsed results through this singleton.
 *
 * @author Arcadiy Ivanov
 */
public final class KotlinModelManager {

	private static final KotlinModelManager INSTANCE = new KotlinModelManager();

	private final KotlinFileParser parser = new KotlinFileParser();
	private final SymbolTable symbolTable = new SymbolTable();
	private final ConcurrentHashMap<String, SoftReference<KotlinFileModel>>
			fileModelCache = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<String, SoftReference<KotlinCompilationUnit>>
			cuCache = new ConcurrentHashMap<>();

	private KotlinModelManager() {
		symbolTable.initStdlib();
	}

	public static KotlinModelManager getInstance() {
		return INSTANCE;
	}

	/**
	 * Returns the shared parser instance.
	 */
	public KotlinFileParser getParser() {
		return parser;
	}

	/**
	 * Returns the shared symbol table.
	 */
	public SymbolTable getSymbolTable() {
		return symbolTable;
	}

	/**
	 * Returns a cached or freshly parsed file model for the given source.
	 *
	 * @param path   the workspace-relative path used as cache key
	 * @param source the file contents to parse on cache miss
	 * @return parsed file model, or {@code null} if parsing fails
	 */
	public KotlinFileModel getFileModel(String path, String source) {
		if (warnIfNull(path, "getFileModel")) {
			return null;
		}
		purgeStaleEntries();

		SoftReference<KotlinFileModel> ref = fileModelCache.get(path);
		if (ref != null) {
			KotlinFileModel cached = ref.get();
			if (cached != null) {
				return cached;
			}
			fileModelCache.remove(path);
		}

		if (source == null || source.isEmpty()) {
			return null;
		}
		try {
			boolean isScript = path.endsWith(".kts");
			KotlinFileModel fileModel = parser.parse(source,
					isScript);
			fileModelCache.put(path,
					new SoftReference<>(fileModel));
			return fileModel;
		} catch (Exception e) {
			org.eclipse.core.runtime.Platform.getLog(
					KotlinModelManager.class).error(
					"Kotlin file parse failed for " + path, e);
			return null;
		}
	}

	/**
	 * Returns a cached or freshly parsed file model for the given
	 * char array contents (used by SearchDocument).
	 */
	public KotlinFileModel getFileModel(String path, char[] contents) {
		if (contents == null || contents.length == 0) {
			return null;
		}
		return getFileModel(path, new String(contents));
	}

	/**
	 * Stores a pre-parsed file model in the cache.
	 */
	public void putFileModel(String path, KotlinFileModel fileModel) {
		if (warnIfNull(path, "putFileModel")) {
			return;
		}
		fileModelCache.put(path, new SoftReference<>(fileModel));
	}

	/**
	 * Removes a file model from the cache.
	 */
	public void invalidateFileModel(String path) {
		if (warnIfNull(path, "invalidateFileModel")) {
			return;
		}
		fileModelCache.remove(path);
		cuCache.remove(path);
	}

	/**
	 * Returns a {@link KotlinCompilationUnit} for the given file,
	 * with its element tree populated from the parsed file model.
	 *
	 * @param file the workspace .kt file
	 * @return compilation unit with populated types, or a bare CU
	 *         if parsing fails
	 */
	public KotlinCompilationUnit getCompilationUnit(IFile file) {
		if (warnIfNull(file, "getCompilationUnit")) {
			return null;
		}
		String path = file.getFullPath().toString();
		purgeStaleEntries();

		SoftReference<KotlinCompilationUnit> cuRef = cuCache.get(path);
		if (cuRef != null) {
			KotlinCompilationUnit cached = cuRef.get();
			if (cached != null) {
				return cached;
			}
			cuCache.remove(path);
		}

		KotlinCompilationUnit cu = new KotlinCompilationUnit(file);
		populateCompilationUnit(cu, path);
		cuCache.put(path, new SoftReference<>(cu));
		return cu;
	}

	/**
	 * Populates a {@link KotlinCompilationUnit} with its element tree
	 * from the cached (or freshly parsed) file model.
	 */
	void populateCompilationUnit(KotlinCompilationUnit cu, String path) {
		KotlinFileModel model = fileModelCache.get(path) != null
				? fileModelCache.get(path).get() : null;
		if (model == null) {
			// Try parsing from the file contents
			try {
				String source = cu.getSource();
				if (source != null && !source.isEmpty()) {
					model = getFileModel(path, source);
				}
			} catch (Exception e) {
				org.eclipse.core.runtime.Platform.getLog(
						KotlinModelManager.class).warn(
						"Failed to parse Kotlin source for CU "
								+ "population: " + path, e);
			}
		}
		if (model == null) {
			return;
		}

		String packageName = model.getPackageName();
		List<KotlinElement.KotlinTypeElement> types = new ArrayList<>();

		for (KotlinDeclaration decl : model.getDeclarations()) {
			if (decl instanceof KotlinDeclaration.TypeDeclaration td) {
				types.add(KotlinElement.buildTypeElement(
						td, cu, packageName));
			}
		}

		cu.setTypes(types.toArray(
				new KotlinElement.KotlinTypeElement[0]));

		// Also build top-level functions and properties as children
		List<IJavaElement> topLevelChildren = new ArrayList<>(types);
		for (KotlinDeclaration decl : model.getDeclarations()) {
			if (decl instanceof KotlinDeclaration.MethodDeclaration md) {
				topLevelChildren.add(
						KotlinElement.buildMethodElement(md, cu));
			} else if (decl instanceof KotlinDeclaration.PropertyDeclaration pd) {
				int len = pd.getEndOffset() - pd.getStartOffset() + 1;
				topLevelChildren.add(
						new KotlinElement.KotlinFieldElement(
								pd.getName(), cu,
								pd.getStartOffset(), len,
								KotlinElement.toTypeSignature(
										pd.getTypeName()),
								false, pd.getModifiers()));
			}
		}
		cu.setAllChildren(topLevelChildren.toArray(
				new IJavaElement[0]));
	}

	private static boolean warnIfNull(Object value, String method) {
		if (value == null) {
			org.eclipse.core.runtime.Platform.getLog(
					KotlinModelManager.class).warn(
					method + " called with null argument");
			return true;
		}
		return false;
	}

	private void purgeStaleEntries() {
		fileModelCache.entrySet()
				.removeIf(e -> e.getValue().get() == null);
		cuCache.entrySet()
				.removeIf(e -> e.getValue().get() == null);
	}
}
