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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Resolves simple names to fully qualified names using import declarations,
 * star imports, default Kotlin imports, and same-package resolution.
 *
 * @author Arcadiy Ivanov
 */
public class ImportResolver {

	private static final List<String> DEFAULT_IMPORTS = List.of(
			"kotlin",
			"kotlin.annotation",
			"kotlin.collections",
			"kotlin.comparisons",
			"kotlin.io",
			"kotlin.ranges",
			"kotlin.sequences",
			"kotlin.text",
			"java.lang");

	private final String packageName;
	private final Map<String, String> explicitImports;
	private final List<String> starImports;

	/**
	 * Creates an import resolver from the given package name and import list.
	 *
	 * @param packageName the package of the current file, or {@code null}
	 * @param imports     the import entries from the file
	 */
	public ImportResolver(String packageName,
			List<KotlinFileModel.ImportEntry> imports) {
		this.packageName = packageName;
		this.explicitImports = new LinkedHashMap<>();
		this.starImports = new ArrayList<>();

		if (imports != null) {
			for (KotlinFileModel.ImportEntry entry : imports) {
				if (entry.isStar()) {
					starImports.add(entry.getFqn());
				} else {
					explicitImports.put(entry.getSimpleName(), entry.getFqn());
				}
			}
		}
	}

	/**
	 * Resolves a simple name to a fully qualified name. Returns the best
	 * candidate or the simple name itself if unresolved.
	 *
	 * @param simpleName the simple name to resolve
	 * @return the resolved FQN, or the simple name if unresolved
	 */
	public String resolve(String simpleName) {
		if (simpleName == null) {
			return null;
		}

		// Check explicit imports first
		String explicit = explicitImports.get(simpleName);
		if (explicit != null) {
			return explicit;
		}

		// Already qualified
		if (simpleName.contains(".")) {
			return simpleName;
		}

		// Same package takes precedence over star/default imports
		// (Kotlin resolves same-package names first)
		if (packageName != null) {
			return packageName + "." + simpleName;
		}

		// Try star imports (first user-declared star import wins)
		if (!starImports.isEmpty()) {
			return starImports.get(0) + "." + simpleName;
		}

		// Try default imports (first default import wins)
		if (!DEFAULT_IMPORTS.isEmpty()) {
			return DEFAULT_IMPORTS.get(0) + "." + simpleName;
		}

		return simpleName;
	}

	/**
	 * Returns all possible FQN candidates for the given simple name, from
	 * explicit imports, star imports (including defaults), and same-package
	 * resolution.
	 *
	 * @param simpleName the simple name to resolve
	 * @return all candidate FQNs (never empty; at minimum contains the simple
	 *         name itself)
	 */
	public List<String> resolveAllCandidates(String simpleName) {
		if (simpleName == null) {
			return Collections.emptyList();
		}

		List<String> candidates = new ArrayList<>();

		// Explicit import
		String explicit = explicitImports.get(simpleName);
		if (explicit != null) {
			candidates.add(explicit);
		}

		// Already qualified
		if (simpleName.contains(".")) {
			if (!candidates.contains(simpleName)) {
				candidates.add(simpleName);
			}
			return candidates;
		}

		// Star imports
		for (String prefix : starImports) {
			candidates.add(prefix + "." + simpleName);
		}

		// Default imports
		for (String prefix : DEFAULT_IMPORTS) {
			candidates.add(prefix + "." + simpleName);
		}

		// Same package
		if (packageName != null) {
			candidates.add(packageName + "." + simpleName);
		}

		// Last resort
		if (candidates.isEmpty()) {
			candidates.add(simpleName);
		}

		return candidates;
	}

	/**
	 * Returns the package name of the current file.
	 */
	public String getPackageName() {
		return packageName;
	}

	/**
	 * Returns the explicit imports map (simple name to FQN).
	 */
	public Map<String, String> getExplicitImports() {
		return Collections.unmodifiableMap(explicitImports);
	}

	/**
	 * Returns the star import prefixes.
	 */
	public List<String> getStarImports() {
		return Collections.unmodifiableList(starImports);
	}
}
