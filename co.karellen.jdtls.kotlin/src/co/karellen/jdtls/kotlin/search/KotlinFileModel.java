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
import java.util.stream.Collectors;

import org.antlr.v4.runtime.tree.ParseTree;

/**
 * Parsed model of a Kotlin source file, containing the package declaration,
 * imports, and top-level declarations.
 *
 * @author Arcadiy Ivanov
 */
public class KotlinFileModel {

	/**
	 * An import entry from a Kotlin source file.
	 */
	public static class ImportEntry {

		private final String fqn;
		private final String alias;
		private final boolean star;

		public ImportEntry(String fqn, String alias, boolean star) {
			this.fqn = fqn;
			this.alias = alias;
			this.star = star;
		}

		public String getFqn() {
			return fqn;
		}

		public String getAlias() {
			return alias;
		}

		public boolean isStar() {
			return star;
		}

		/**
		 * Returns the alias if present, otherwise the last segment of the FQN.
		 */
		public String getSimpleName() {
			if (alias != null) {
				return alias;
			}
			int lastDot = fqn.lastIndexOf('.');
			return lastDot >= 0 ? fqn.substring(lastDot + 1) : fqn;
		}
	}

	private final String packageName;
	private final List<ImportEntry> imports;
	private final List<KotlinDeclaration> declarations;
	private final String source;
	private final ParseTree parseTree;

	public KotlinFileModel(String packageName, List<ImportEntry> imports,
			List<KotlinDeclaration> declarations, String source,
			ParseTree parseTree) {
		this.packageName = packageName;
		this.imports = imports != null
				? Collections.unmodifiableList(imports)
				: Collections.emptyList();
		this.declarations = declarations != null
				? Collections.unmodifiableList(declarations)
				: Collections.emptyList();
		this.source = source;
		this.parseTree = parseTree;
	}

	public String getPackageName() {
		return packageName;
	}

	public List<ImportEntry> getImports() {
		return imports;
	}

	public List<KotlinDeclaration> getDeclarations() {
		return declarations;
	}

	public String getSource() {
		return source;
	}

	/**
	 * Returns the ANTLR parse tree, retained for use by reference indexing
	 * and reference finding during {@code locateMatches()}.
	 */
	public ParseTree getParseTree() {
		return parseTree;
	}

	/**
	 * Returns top-level type declarations.
	 */
	public List<KotlinDeclaration.TypeDeclaration> getTopLevelTypes() {
		return declarations.stream()
				.filter(d -> d instanceof KotlinDeclaration.TypeDeclaration)
				.map(d -> (KotlinDeclaration.TypeDeclaration) d)
				.collect(Collectors.toList());
	}

	/**
	 * Returns top-level function declarations.
	 */
	public List<KotlinDeclaration.MethodDeclaration> getTopLevelFunctions() {
		return declarations.stream()
				.filter(d -> d instanceof KotlinDeclaration.MethodDeclaration)
				.map(d -> (KotlinDeclaration.MethodDeclaration) d)
				.collect(Collectors.toList());
	}

	/**
	 * Returns top-level property declarations.
	 */
	public List<KotlinDeclaration.PropertyDeclaration> getTopLevelProperties() {
		return declarations.stream()
				.filter(d -> d instanceof KotlinDeclaration.PropertyDeclaration)
				.map(d -> (KotlinDeclaration.PropertyDeclaration) d)
				.collect(Collectors.toList());
	}
}
