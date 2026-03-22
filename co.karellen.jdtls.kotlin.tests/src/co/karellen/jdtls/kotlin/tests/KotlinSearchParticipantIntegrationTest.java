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

import java.util.List;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchParticipant;
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

	@Test
	public void testIndexingTriggeredForKtFile() throws CoreException {
		TestHelpers.createFile("/KotlinTest/src/Hello.kt", "class Hello");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> matches = searchKotlinTypes("Hello");
		assertTrue(matches.size() >= 1,
				"Indexing should have produced a searchable Hello type from .kt file");
	}

	@Test
	public void testIndexingTriggeredForKtsFile() throws CoreException {
		TestHelpers.createFile("/KotlinTest/src/Script.kts", "class Script");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> matches = searchKotlinTypes("Script");
		assertTrue(matches.size() >= 1,
				"Indexing should have produced a searchable Script type from .kts file");
	}

	@Test
	public void testIndexingTriggeredForMultipleKtFiles() throws CoreException {
		TestHelpers.createFile("/KotlinTest/src/Alpha.kt", "class Alpha\nclass AlphaHelper");
		TestHelpers.createFile("/KotlinTest/src/Beta.kt", "class Beta\ninterface BetaService");
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
		TestHelpers.createFile("/KotlinTest/src/pkg/InPackage.kt",
				"package pkg\n\nclass InPackage");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> matches = searchKotlinTypes("InPackage");
		assertTrue(matches.size() >= 1,
				"Indexing should have produced a searchable InPackage type in subpackage");
	}

	@Test
	public void testKtFileDeletion() throws CoreException {
		TestHelpers.createFile("/KotlinTest/src/ToDelete.kt", "class ToDelete");
		TestHelpers.waitUntilIndexesReady();

		// delete the file — should not throw
		TestHelpers.createFile("/KotlinTest/src/ToDelete.kt", "class ToDelete").delete(true, null);
		TestHelpers.waitUntilIndexesReady();
		// If we get here without an exception, the delta processor handled removal correctly
	}

	@Test
	public void testJavaFilesNotRoutedToKotlinParticipant() throws CoreException {
		TestHelpers.createFile("/KotlinTest/src/Regular.java", "public class Regular {}");
		TestHelpers.waitUntilIndexesReady();

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
		TestHelpers.createFile("/KotlinTest/src/Hello.kt", "class Hello {\n    fun greet() {}\n}");
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
		TestHelpers.createFile("/KotlinTest/src/Foo.kt",
				"class Foo {\n    val x: Int = 1\n    val y: String = \"hello\"\n}");
		TestHelpers.createFile("/KotlinTest/src/Bar.kt",
				"interface Bar {\n    fun doSomething()\n    fun doMore()\n}");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> fooMatches = searchKotlinTypes("Foo");
		List<SearchMatch> barMatches = searchKotlinTypes("Bar");
		assertTrue(fooMatches.size() >= 1,
				"Should find Foo type from .kt file");
		assertTrue(barMatches.size() >= 1,
				"Should find Bar type from .kt file");
	}

	// ---- ANTLR Parser Integration Tests ----

	@Test
	public void testIndexingWithPackageDeclaration() throws CoreException {
		TestHelpers.createFolder("/KotlinTest/src/com/example");
		TestHelpers.createFile("/KotlinTest/src/com/example/MyClass.kt",
				"package com.example\n\nclass MyClass {\n    fun hello(): String = \"world\"\n}");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> matches = searchKotlinTypes("MyClass");
		assertTrue(matches.size() >= 1,
				"Should find MyClass type with package declaration");
	}

	@Test
	public void testIndexingWithInheritance() throws CoreException {
		TestHelpers.createFile("/KotlinTest/src/Base.kt",
				"open class Base {\n    open fun doIt() {}\n}");
		TestHelpers.createFile("/KotlinTest/src/Derived.kt",
				"class Derived : Base() {\n    override fun doIt() {}\n}");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> baseMatches = searchKotlinTypes("Base");
		List<SearchMatch> derivedMatches = searchKotlinTypes("Derived");
		assertTrue(baseMatches.size() >= 1, "Should find Base type");
		assertTrue(derivedMatches.size() >= 1, "Should find Derived type");
	}

	@Test
	public void testIndexingEmptyFileStillWorks() throws CoreException {
		TestHelpers.createFile("/KotlinTest/src/Empty.kt", "");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> matches = searchKotlinTypes("Empty");
		assertTrue(matches.size() >= 1,
				"Empty file should still produce a type from path-based fallback");
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

	@Test
	public void testKtsScriptLevelDeclarationsIndexed()
			throws CoreException {
		// .kts files with top-level declarations should be indexed
		// using the script() parser entry point.
		TestHelpers.createFile("/KotlinTest/src/script.kts",
				"class ScriptGreeter {\n"
				+ "    fun greet(): String = \"Hello\"\n"
				+ "}\n"
				+ "\n"
				+ "class ScriptHelper {\n"
				+ "    fun assist(): String = \"Helping\"\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> greeterTypes = searchKotlinTypes(
				"ScriptGreeter");
		assertTrue(greeterTypes.size() >= 1,
				"Script-level class 'ScriptGreeter' should be "
						+ "indexed. Found " + greeterTypes.size());

		List<SearchMatch> helperTypes = searchKotlinTypes(
				"ScriptHelper");
		assertTrue(helperTypes.size() >= 1,
				"Script-level class 'ScriptHelper' should be "
						+ "indexed. Found " + helperTypes.size());
	}

	@Test
	public void testKtsScriptExpressionReferencesIndexed()
			throws CoreException {
		// .kts files should also index expression references (REF,
		// METHOD_REF, CONSTRUCTOR_REF) from the script body.
		TestHelpers.createFile("/KotlinTest/src/build.kts",
				"class Target {\n"
				+ "    fun execute(): String = \"done\"\n"
				+ "}\n"
				+ "\n"
				+ "val t = Target()\n"
				+ "t.execute()\n");
		TestHelpers.waitUntilIndexesReady();

		// TYPE_DECL should work (already proven)
		List<SearchMatch> targetTypes = searchKotlinTypes("Target");
		assertTrue(targetTypes.size() >= 1,
				"Script class 'Target' should be indexed. Found "
						+ targetTypes.size());

		// TYPE REF should also work — Target() constructor call
		// emits CONSTRUCTOR_REF + REF
		List<SearchMatch> refs = TestHelpers.searchTypeReferences(
				"Target", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertTrue(ktRefs.size() >= 1,
				"Script-level 'Target()' constructor call should "
						+ "produce a type reference. Found "
						+ ktRefs.size());
	}

	@Test
	public void testKtsScriptLevelClassDeclaration()
			throws CoreException {
		// Existing behavior: class declarations in .kts files should
		// still work after the script parsing changes.
		TestHelpers.createFile("/KotlinTest/src/model.kts",
				"class Model {\n"
				+ "    val name: String = \"test\"\n"
				+ "}\n"
				+ "\n"
				+ "class Helper {\n"
				+ "    fun assist(): String = \"helping\"\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> types = searchKotlinTypes("Model");
		assertTrue(types.size() >= 1,
				"Script-level class 'Model' should be found. Found "
						+ types.size());

		List<SearchMatch> helpers = searchKotlinTypes("Helper");
		assertTrue(helpers.size() >= 1,
				"Script-level class 'Helper' should be found. Found "
						+ helpers.size());
	}

	@Test
	public void testImportAliasTypeReferenceSearch()
			throws CoreException {
		// Import aliases should be resolved during indexing:
		// both the alias name and the original name are indexed.
		// Searching for the original type should find alias usages.
		TestHelpers.createFolder("/KotlinTest/src/aliasref");
		TestHelpers.createFile(
				"/KotlinTest/src/aliasref/Original.kt",
				"package aliasref\n"
				+ "\n"
				+ "class Original {\n"
				+ "    fun method() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/KotlinTest/src/aliasref/Consumer.kt",
				"package aliasref\n"
				+ "\n"
				+ "import aliasref.Original as Alias\n"
				+ "\n"
				+ "fun useAlias() {\n"
				+ "    val x: Alias = Alias()\n"
				+ "    x.method()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers.searchTypeReferences(
				"Original", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		// Consumer.kt uses "Alias" but the indexer emits REF for both
		// "Alias" and "Original", so searching for "Original" finds it
		assertTrue(ktRefs.size() >= 1,
				"Searching for 'Original' should find aliased "
						+ "references via dual index emission. Found "
						+ ktRefs.size());
	}

	@Test
	public void testIsExpressionTypeReferenceIndexed()
			throws CoreException {
		// "is" type checks should index the type reference
		TestHelpers.createFolder("/KotlinTest/src/ischeck");
		TestHelpers.createFile(
				"/KotlinTest/src/ischeck/Animal.kt",
				"package ischeck\n"
				+ "\n"
				+ "open class Animal\n"
				+ "class Dog : Animal()\n"
				+ "class Cat : Animal()\n"
				+ "\n"
				+ "fun checkType(obj: Any) {\n"
				+ "    if (obj is Dog) {\n"
				+ "        println(\"dog\")\n"
				+ "    } else if (obj !is Cat) {\n"
				+ "        println(\"not cat\")\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> dogRefs = TestHelpers.searchTypeReferences(
				"Dog", project);
		List<SearchMatch> ktDogRefs = TestHelpers.filterKotlinMatches(
				dogRefs);
		assertTrue(ktDogRefs.size() >= 1,
				"'is Dog' should be indexed as a type reference. "
						+ "Found " + ktDogRefs.size());

		List<SearchMatch> catRefs = TestHelpers.searchTypeReferences(
				"Cat", project);
		List<SearchMatch> ktCatRefs = TestHelpers.filterKotlinMatches(
				catRefs);
		assertTrue(ktCatRefs.size() >= 1,
				"'!is Cat' should be indexed as a type reference. "
						+ "Found " + ktCatRefs.size());
	}

	// ---- Helpers ----

	private List<SearchMatch> searchKotlinTypes(String typeName) throws CoreException {
		return TestHelpers.searchKotlinTypes(typeName, project);
	}

	private List<SearchMatch> searchAllTypes(String typeName) throws CoreException {
		return TestHelpers.searchAllTypes(typeName, project);
	}
}
