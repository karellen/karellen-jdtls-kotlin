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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.core.search.indexing.SearchParticipantRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.karellen.jdtls.kotlin.search.KotlinSearchParticipant;

/**
 * Integration tests for the Kotlin search participant, validating the
 * end-to-end pipeline: extension point discovery, indexing, and search.
 *
 * @author Arcadiy Ivanov
 */
public class KotlinSearchParticipantIntegrationTest {

	private IJavaProject project;

	@BeforeEach
	public void setUp() throws CoreException {
		SearchParticipantRegistry.reset();
		project = TestHelpers.createJavaProject("KotlinTest", "src");
	}

	@AfterEach
	public void tearDown() throws CoreException {
		TestHelpers.deleteProject("KotlinTest");
		project = null;
	}

	// ---- Extension Point & Registry Tests ----

	@Test
	public void testRegistryHasParticipantForKt() {
		assertTrue(SearchParticipantRegistry.hasParticipant("kt"),
				"Registry should have participant for kt");
	}

	@Test
	public void testRegistryHasParticipantForKts() {
		assertTrue(SearchParticipantRegistry.hasParticipant("kts"),
				"Registry should have participant for kts");
	}

	@Test
	public void testRegistryNoParticipantForUnknownExtension() {
		assertFalse(SearchParticipantRegistry.hasParticipant("unknown_ext"),
				"Registry should not have participant for unknown_ext");
	}

	@Test
	public void testRegistryGetParticipantSingleton() {
		SearchParticipant p1 = SearchParticipantRegistry.getParticipant("kt");
		assertNotNull(p1, "Should return a participant for kt");
		assertTrue(p1 instanceof KotlinSearchParticipant,
				"Should be a KotlinSearchParticipant");
		SearchParticipant p2 = SearchParticipantRegistry.getParticipant("kt");
		assertSame(p1, p2, "Same instance should be returned on second call");
	}

	@Test
	public void testGetContributedParticipantsIncludesKotlin() {
		SearchParticipant[] contributed = SearchParticipantRegistry.getContributedParticipants();
		assertTrue(contributed.length >= 1,
				"Should have at least one contributed participant");
		boolean found = false;
		for (SearchParticipant p : contributed) {
			if (p instanceof KotlinSearchParticipant) {
				found = true;
				break;
			}
		}
		assertTrue(found, "Contributed participants should include KotlinSearchParticipant");
	}

	@Test
	public void testSearchEngineGetSearchParticipantsIncludesKotlin() {
		SearchParticipant[] participants = SearchEngine.getSearchParticipants();
		assertTrue(participants.length >= 2,
				"Should have at least 2 participants (default + contributed)");
		boolean hasDefault = false;
		boolean hasKotlin = false;
		SearchParticipant defaultP = SearchEngine.getDefaultSearchParticipant();
		for (SearchParticipant p : participants) {
			if (p.getClass() == defaultP.getClass()) {
				hasDefault = true;
			}
			if (p instanceof KotlinSearchParticipant) {
				hasKotlin = true;
			}
		}
		assertTrue(hasDefault, "Should include the default Java search participant");
		assertTrue(hasKotlin, "Should include the Kotlin search participant");
	}

	// ---- Indexing Pipeline Tests ----
	// Verify indexing by searching for the indexed types. This tests the full
	// pipeline (delta processing → indexDocument → index storage) without
	// requiring test instrumentation on the production class.

	@Test
	public void testIndexingTriggeredForKtFile() throws CoreException {
		TestHelpers.createFile("/KotlinTest/src/Hello.kt", "");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> matches = searchKotlinTypes("Hello");
		assertTrue(matches.size() >= 1,
				"Indexing should have produced a searchable Hello type from .kt file");
	}

	@Test
	public void testIndexingTriggeredForKtsFile() throws CoreException {
		TestHelpers.createFile("/KotlinTest/src/Script.kts", "");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> matches = searchKotlinTypes("Script");
		assertTrue(matches.size() >= 1,
				"Indexing should have produced a searchable Script type from .kts file");
	}

	@Test
	public void testIndexingTriggeredForMultipleKtFiles() throws CoreException {
		TestHelpers.createFile("/KotlinTest/src/Alpha.kt", "");
		TestHelpers.createFile("/KotlinTest/src/Beta.kt", "");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> alphaMatches = searchKotlinTypes("Alpha");
		List<SearchMatch> betaMatches = searchKotlinTypes("Beta");
		assertTrue(alphaMatches.size() >= 1,
				"Indexing should have produced a searchable Alpha type");
		assertTrue(betaMatches.size() >= 1,
				"Indexing should have produced a searchable Beta type");
	}

	@Test
	public void testIndexingInSubPackage() throws CoreException {
		TestHelpers.createFolder("/KotlinTest/src/pkg");
		TestHelpers.createFile("/KotlinTest/src/pkg/InPackage.kt", "");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> matches = searchKotlinTypes("InPackage");
		assertTrue(matches.size() >= 1,
				"Indexing should have produced a searchable InPackage type in subpackage");
	}

	@Test
	public void testKtFileDeletion() throws CoreException {
		TestHelpers.createFile("/KotlinTest/src/ToDelete.kt", "");
		TestHelpers.waitUntilIndexesReady();

		// delete the file — should not throw
		TestHelpers.createFile("/KotlinTest/src/ToDelete.kt", "").delete(true, null);
		TestHelpers.waitUntilIndexesReady();
		// If we get here without an exception, the delta processor handled removal correctly
	}

	@Test
	public void testJavaFilesNotRoutedToKotlinParticipant() throws CoreException {
		TestHelpers.createFile("/KotlinTest/src/Regular.java", "public class Regular {}");
		TestHelpers.waitUntilIndexesReady();

		// Search for Regular — should find it via the default Java participant,
		// but no match should have a .kt resource (proving our participant wasn't invoked)
		List<SearchMatch> matches = searchAllTypes("Regular");
		for (SearchMatch match : matches) {
			IResource resource = match.getResource();
			if (resource != null) {
				assertFalse(resource.getName().endsWith(".kt"),
						"Java file should not produce a .kt match");
				assertFalse(resource.getName().endsWith(".kts"),
						"Java file should not produce a .kts match");
			}
		}
	}

	// ---- Search Pipeline Tests ----

	@Test
	public void testSearchFindsKtTypeDeclaration() throws CoreException {
		TestHelpers.createFile("/KotlinTest/src/Hello.kt", "");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> matches = searchKotlinTypes("Hello");
		assertTrue(matches.size() >= 1,
				"Should find at least one .kt match for Hello type declaration");
		assertTrue(matches.stream().anyMatch(m ->
				m.getResource() != null && m.getResource().getName().equals("Hello.kt")),
				"Should have a match from Hello.kt");
	}

	@Test
	public void testSearchFindsMultipleKtTypes() throws CoreException {
		TestHelpers.createFile("/KotlinTest/src/Foo.kt", "");
		TestHelpers.createFile("/KotlinTest/src/Bar.kt", "");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> fooMatches = searchKotlinTypes("Foo");
		List<SearchMatch> barMatches = searchKotlinTypes("Bar");
		assertTrue(fooMatches.size() >= 1,
				"Should find Foo type from .kt file");
		assertTrue(barMatches.size() >= 1,
				"Should find Bar type from .kt file");
	}

	// ---- Lifecycle Tests ----

	@Test
	public void testRegistryResetClearsCache() {
		SearchParticipant before = SearchParticipantRegistry.getParticipant("kt");
		assertNotNull(before);
		SearchParticipantRegistry.reset();

		SearchParticipant after = SearchParticipantRegistry.getParticipant("kt");
		assertNotNull(after);
		assertNotSame(before, after, "After reset, a new instance should be created");
	}

	// ---- Helpers ----

	private List<SearchMatch> searchKotlinTypes(String typeName) throws CoreException {
		List<SearchMatch> allMatches = searchAllTypes(typeName);
		return allMatches.stream()
				.filter(m -> m.getResource() != null
						&& (m.getResource().getName().endsWith(".kt")
								|| m.getResource().getName().endsWith(".kts")))
				.toList();
	}

	private List<SearchMatch> searchAllTypes(String typeName) throws CoreException {
		SearchPattern pattern = SearchPattern.createPattern(
				typeName,
				IJavaSearchConstants.TYPE,
				IJavaSearchConstants.DECLARATIONS,
				SearchPattern.R_EXACT_MATCH);
		IJavaSearchScope scope = SearchEngine.createJavaSearchScope(
				new IJavaProject[] { project });

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
}
