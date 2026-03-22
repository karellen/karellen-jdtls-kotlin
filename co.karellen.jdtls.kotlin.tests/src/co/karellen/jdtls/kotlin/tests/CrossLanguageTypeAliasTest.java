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

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.internal.core.search.indexing.SearchParticipantRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for type alias indexing and search. Verifies that Kotlin
 * {@code typealias} declarations are indexed as TYPE_DECL and
 * findable via JDT type declaration search, and that the aliased
 * type is indexed as a REF.
 *
 * @author Arcadiy Ivanov
 */
public class CrossLanguageTypeAliasTest {

	private static final String PROJECT_NAME = "TypeAliasTest";
	private IJavaProject project;

	@BeforeEach
	public void setUp() throws CoreException {
		SearchParticipantRegistry.reset();
		project = TestHelpers.createJavaProject(PROJECT_NAME, "src");
	}

	@AfterEach
	public void tearDown() throws CoreException {
		TestHelpers.deleteProject(PROJECT_NAME);
		project = null;
	}

	@Test
	public void testSimpleTypeAliasFoundAsTypeDeclaration() throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/aliases");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/aliases/Aliases.kt",
				"package aliases\n"
				+ "\n"
				+ "typealias StringProcessor = (String) -> Boolean\n"
				+ "typealias EventCallback = (String, Int) -> Unit\n"
				+ "\n"
				+ "class AliasUser {\n"
				+ "    fun process(handler: StringProcessor): Boolean {\n"
				+ "        return handler(\"test\")\n"
				+ "    }\n"
				+ "    fun listen(callback: EventCallback) {\n"
				+ "        callback(\"event\", 42)\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Type alias should be findable as type declaration
		List<SearchMatch> processorMatches = TestHelpers.searchKotlinTypes("StringProcessor", project);
		assertTrue(processorMatches.size() >= 1,
				"typealias StringProcessor should be findable as type declaration");

		List<SearchMatch> callbackMatches = TestHelpers.searchKotlinTypes("EventCallback", project);
		assertTrue(callbackMatches.size() >= 1,
				"typealias EventCallback should be findable as type declaration");

		// Verify the match elements are proper KotlinElements
		for (SearchMatch match : processorMatches) {
			assertNotNull(match.getElement(), "Type alias match should have an element");
			assertTrue(match.getElement() instanceof IMember,
					"Type alias match element should be IMember");
			IMember member = (IMember) match.getElement();
			assertTrue(member.getElementType() == IJavaElement.TYPE,
					"Type alias element should be TYPE");
			assertTrue(match.getOffset() >= 0, "Should have valid offset");
			assertTrue(match.getLength() > 0, "Should have positive length");
		}
	}

	@Test
	public void testTypeAliasWithGenericType() throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/aliases");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/aliases/GenericAliases.kt",
				"package aliases\n"
				+ "\n"
				+ "typealias StringMap = Map<String, String>\n"
				+ "typealias IntList = List<Int>\n"
				+ "\n"
				+ "fun useAliases(map: StringMap, list: IntList) {\n"
				+ "    println(map.size)\n"
				+ "    println(list.size)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> mapMatches = TestHelpers.searchKotlinTypes("StringMap", project);
		assertTrue(mapMatches.size() >= 1,
				"typealias StringMap should be findable");

		List<SearchMatch> listMatches = TestHelpers.searchKotlinTypes("IntList", project);
		assertTrue(listMatches.size() >= 1,
				"typealias IntList should be findable");
	}

	@Test
	public void testTypeAliasWithNullableType() throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/aliases");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/aliases/NullableAliases.kt",
				"package aliases\n"
				+ "\n"
				+ "typealias NullableString = String?\n"
				+ "typealias OptionalInt = Int?\n"
				+ "\n"
				+ "fun processNullable(s: NullableString, i: OptionalInt) {\n"
				+ "    println(s ?: \"default\")\n"
				+ "    println(i ?: 0)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> nullableMatches = TestHelpers.searchKotlinTypes("NullableString", project);
		assertTrue(nullableMatches.size() >= 1,
				"typealias NullableString should be findable");
	}

	@Test
	public void testTypeAliasWithQualifiedType() throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/aliases");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/aliases/QualifiedAliases.kt",
				"package aliases\n"
				+ "\n"
				+ "typealias JavaList = java.util.ArrayList<String>\n"
				+ "typealias JavaMap = java.util.HashMap<String, Int>\n"
				+ "\n"
				+ "fun useJavaTypes(list: JavaList, map: JavaMap) {\n"
				+ "    list.add(\"item\")\n"
				+ "    map.put(\"key\", 1)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> listMatches = TestHelpers.searchKotlinTypes("JavaList", project);
		assertTrue(listMatches.size() >= 1,
				"typealias JavaList should be findable");

		List<SearchMatch> mapMatches = TestHelpers.searchKotlinTypes("JavaMap", project);
		assertTrue(mapMatches.size() >= 1,
				"typealias JavaMap should be findable");
	}

	@Test
	public void testAliasedTypeIndexedAsRef() throws CoreException {
		// The aliased type name should be indexed as a REF, so searching
		// for references to the aliased type should find the typealias
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/aliases");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/aliases/AliasRef.kt",
				"package aliases\n"
				+ "\n"
				+ "class OriginalType {\n"
				+ "    fun doWork(): String = \"done\"\n"
				+ "}\n"
				+ "\n"
				+ "typealias AliasForOriginal = OriginalType\n"
				+ "typealias AnotherAlias = OriginalType\n"
				+ "\n"
				+ "fun useAlias(obj: AliasForOriginal): String {\n"
				+ "    return obj.doWork()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// The typealias should be findable by its own name
		List<SearchMatch> aliasMatches = TestHelpers.searchKotlinTypes("AliasForOriginal", project);
		assertTrue(aliasMatches.size() >= 1,
				"typealias AliasForOriginal should be findable");

		// References to OriginalType should include the typealias (via REF)
		List<SearchMatch> origRefs = TestHelpers.searchTypeReferences("OriginalType", project);
		List<SearchMatch> ktOrigRefs = TestHelpers.filterKotlinMatches(origRefs);
		assertTrue(ktOrigRefs.size() >= 1,
				"Should find at least one reference to OriginalType from .kt (from typealias or usage)");
	}

	@Test
	public void testTypeAliasInResourceFiles() throws CoreException {
		// v14/KotlinB has: typealias EventProcessor_V14 = (EventData_V14) -> Boolean
		loadVersionFiles("v14");

		// The type alias from the resource file should be findable
		// Note: EventProcessor_V14 is a typealias for a function type
		// The aliased type's simple name "EventData_V14" should be indexed as REF
		// This exercises the real resource file parsing + type alias indexing
		List<SearchMatch> eventDataRefs = TestHelpers.searchTypeReferences("EventData_V14", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(eventDataRefs);
		assertTrue(ktRefs.size() >= 1,
				"Should find references to EventData_V14 in .kt files");
	}

	// ---- Helpers ----

	private void loadVersionFiles(String version) throws CoreException {
		String suffix = version.replace("v", "_V");
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/crosslang/" + version);

		TestHelpers.createFileFromResource(
				"/" + PROJECT_NAME + "/src/crosslang/" + version + "/JavaA" + suffix + ".java",
				"resources/crosslang/" + version + "/JavaA" + suffix + ".java");
		TestHelpers.createFileFromResource(
				"/" + PROJECT_NAME + "/src/crosslang/" + version + "/JavaB" + suffix + ".java",
				"resources/crosslang/" + version + "/JavaB" + suffix + ".java");
		TestHelpers.createFileFromResource(
				"/" + PROJECT_NAME + "/src/crosslang/" + version + "/KotlinA" + suffix + ".kt",
				"resources/crosslang/" + version + "/KotlinA" + suffix + ".kt");
		TestHelpers.createFileFromResource(
				"/" + PROJECT_NAME + "/src/crosslang/" + version + "/KotlinB" + suffix + ".kt",
				"resources/crosslang/" + version + "/KotlinB" + suffix + ".kt");

		TestHelpers.waitUntilIndexesReady();
	}
}
