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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.TypeNameMatch;
import org.eclipse.jdt.core.search.TypeNameMatchRequestor;
import org.eclipse.jdt.internal.core.search.indexing.SearchParticipantRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests verifying that workspace/symbol queries find Kotlin types.
 * <p>
 * The jdtls workspace/symbol handler uses {@code searchAllTypeNames} which
 * only queries the default (Java) search participant. This test class
 * demonstrates that Kotlin types are invisible to that API and validates
 * that {@code search()} with contributed participants finds them.
 *
 * @author Arcadiy Ivanov
 */
public class WorkspaceSymbolSearchTest {

	private IJavaProject project;

	@BeforeEach
	public void setUp() throws CoreException {
		SearchParticipantRegistry.reset();
		project = TestHelpers.createJavaProject("WorkspaceSymbolTest", "src");
	}

	@AfterEach
	public void tearDown() throws CoreException {
		TestHelpers.deleteProject("WorkspaceSymbolTest");
		project = null;
	}

	@Test
	public void testSearchAllTypeNamesDoesNotFindKotlinTypes() throws CoreException {
		TestHelpers.createFile("/WorkspaceSymbolTest/src/com/example/InventoryPopulationService.kt",
				"package com.example\n\nclass InventoryPopulationService {\n    fun populate() {}\n}");
		TestHelpers.waitUntilIndexesReady();

		List<TypeNameMatch> typeNameMatches = searchAllTypeNames(
				null, "InventoryPopulationService".toCharArray(),
				SearchPattern.R_EXACT_MATCH);

		assertTrue(typeNameMatches.isEmpty(),
				"searchAllTypeNames (default participant only) should NOT find Kotlin types - "
						+ "this is the workspace/symbol bug");
	}

	@Test
	public void testSearchAllTypeNamesCamelCaseDoesNotFindKotlinTypes() throws CoreException {
		TestHelpers.createFile("/WorkspaceSymbolTest/src/com/example/InventoryPopulationService.kt",
				"package com.example\n\nclass InventoryPopulationService {\n    fun populate() {}\n}");
		TestHelpers.waitUntilIndexesReady();

		List<TypeNameMatch> typeNameMatches = searchAllTypeNames(
				null, "IPS".toCharArray(),
				SearchPattern.R_CAMELCASE_MATCH);

		assertTrue(typeNameMatches.isEmpty(),
				"searchAllTypeNames with camelcase (default participant only) should NOT find Kotlin types");
	}

	@Test
	public void testSearchWithParticipantsFindsKotlinTypeExact() throws CoreException {
		TestHelpers.createFile("/WorkspaceSymbolTest/src/com/example/InventoryPopulationService.kt",
				"package com.example\n\nclass InventoryPopulationService {\n    fun populate() {}\n}");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> matches = TestHelpers.searchAllTypes("InventoryPopulationService", project);
		List<SearchMatch> ktMatches = TestHelpers.filterKotlinMatches(matches);

		assertFalse(ktMatches.isEmpty(),
				"search() with participants should find Kotlin type with exact match");
		assertEquals("InventoryPopulationService.kt", ktMatches.get(0).getResource().getName());
	}

	@Test
	public void testSearchWithParticipantsFindsKotlinTypeCamelCase() throws CoreException {
		TestHelpers.createFile("/WorkspaceSymbolTest/src/com/example/InventoryPopulationService.kt",
				"package com.example\n\nclass InventoryPopulationService {\n    fun populate() {}\n}");
		TestHelpers.waitUntilIndexesReady();

		SearchPattern pattern = SearchPattern.createPattern(
				"IPS",
				IJavaSearchConstants.TYPE,
				IJavaSearchConstants.DECLARATIONS,
				SearchPattern.R_CAMELCASE_MATCH);
		List<SearchMatch> matches = TestHelpers.executeSearch(pattern, project);
		List<SearchMatch> ktMatches = TestHelpers.filterKotlinMatches(matches);

		assertFalse(ktMatches.isEmpty(),
				"search() with participants should find Kotlin type with camelcase match");
		assertEquals("InventoryPopulationService.kt", ktMatches.get(0).getResource().getName());
	}

	@Test
	public void testSearchWithParticipantsFindsKotlinTypePatternMatch() throws CoreException {
		TestHelpers.createFile("/WorkspaceSymbolTest/src/com/example/InventoryPopulationService.kt",
				"package com.example\n\nclass InventoryPopulationService {\n    fun populate() {}\n}");
		TestHelpers.waitUntilIndexesReady();

		SearchPattern pattern = SearchPattern.createPattern(
				"*Population*",
				IJavaSearchConstants.TYPE,
				IJavaSearchConstants.DECLARATIONS,
				SearchPattern.R_PATTERN_MATCH);
		List<SearchMatch> matches = TestHelpers.executeSearch(pattern, project);
		List<SearchMatch> ktMatches = TestHelpers.filterKotlinMatches(matches);

		assertFalse(ktMatches.isEmpty(),
				"search() with participants should find Kotlin type with pattern match");
		assertEquals("InventoryPopulationService.kt", ktMatches.get(0).getResource().getName());
	}

	@Test
	public void testSearchWithParticipantsFindsMultipleKotlinTypes() throws CoreException {
		TestHelpers.createFile("/WorkspaceSymbolTest/src/com/example/services.kt",
				"package com.example\n\n"
						+ "class InventoryPopulationService {\n    fun populate() {}\n}\n\n"
						+ "class InventoryValidationService {\n    fun validate() {}\n}");
		TestHelpers.waitUntilIndexesReady();

		SearchPattern pattern = SearchPattern.createPattern(
				"Inventory*Service",
				IJavaSearchConstants.TYPE,
				IJavaSearchConstants.DECLARATIONS,
				SearchPattern.R_PATTERN_MATCH);
		List<SearchMatch> matches = TestHelpers.executeSearch(pattern, project);
		List<SearchMatch> ktMatches = TestHelpers.filterKotlinMatches(matches);

		assertTrue(ktMatches.size() >= 2,
				"search() with participants should find multiple Kotlin types matching pattern, found: "
						+ ktMatches.size());
	}

	@Test
	public void testSearchWithParticipantsFindsKotlinInterface() throws CoreException {
		TestHelpers.createFile("/WorkspaceSymbolTest/src/com/example/Repository.kt",
				"package com.example\n\n"
						+ "interface InventoryRepository {\n    fun findAll(): List<Any>\n}\n\n"
						+ "class InventoryRepositoryImpl : InventoryRepository {\n"
						+ "    override fun findAll(): List<Any> = emptyList()\n}");
		TestHelpers.waitUntilIndexesReady();

		SearchPattern pattern = SearchPattern.createPattern(
				"IR",
				IJavaSearchConstants.TYPE,
				IJavaSearchConstants.DECLARATIONS,
				SearchPattern.R_CAMELCASE_MATCH);
		List<SearchMatch> matches = TestHelpers.executeSearch(pattern, project);
		List<SearchMatch> ktMatches = TestHelpers.filterKotlinMatches(matches);

		assertTrue(ktMatches.size() >= 2,
				"search() with participants should find Kotlin interface and implementation via camelcase");
	}

	@Test
	public void testSearchWithParticipantsFindsKotlinTypeWithPackageQualifier() throws CoreException {
		TestHelpers.createFile("/WorkspaceSymbolTest/src/com/example/service/InventoryPopulationService.kt",
				"package com.example.service\n\nclass InventoryPopulationService {\n    fun populate() {}\n}");
		TestHelpers.waitUntilIndexesReady();

		SearchPattern pattern = SearchPattern.createPattern(
				"InventoryPopulationService",
				IJavaSearchConstants.TYPE,
				IJavaSearchConstants.DECLARATIONS,
				SearchPattern.R_EXACT_MATCH);
		List<SearchMatch> matches = TestHelpers.executeSearch(pattern, project);
		List<SearchMatch> ktMatches = TestHelpers.filterKotlinMatches(matches);

		assertFalse(ktMatches.isEmpty(),
				"search() with participants should find Kotlin type in sub-package");
	}

	private List<TypeNameMatch> searchAllTypeNames(char[] packageName, char[] typeName,
			int matchRule) throws CoreException {
		SearchEngine engine = new SearchEngine();
		List<TypeNameMatch> results = new ArrayList<>();
		engine.searchAllTypeNames(
				packageName,
				SearchPattern.R_PATTERN_MATCH,
				typeName,
				matchRule,
				IJavaSearchConstants.TYPE,
				SearchEngine.createJavaSearchScope(new IJavaProject[] { project }),
				new TypeNameMatchRequestor() {
					@Override
					public void acceptTypeNameMatch(TypeNameMatch match) {
						results.add(match);
					}
				},
				IJavaSearchConstants.WAIT_UNTIL_READY_TO_SEARCH,
				null);
		return results;
	}
}
