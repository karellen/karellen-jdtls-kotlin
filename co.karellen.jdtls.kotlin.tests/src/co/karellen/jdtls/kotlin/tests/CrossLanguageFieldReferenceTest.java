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
 * Tests for cross-language field/property reference finding.
 * Verifies that Kotlin property access expressions ({@code obj.property})
 * are found by JDT field reference search, and that Kotlin property
 * declarations are found by field declaration search.
 *
 * @author Arcadiy Ivanov
 */
public class CrossLanguageFieldReferenceTest {

	private static final String PROJECT_NAME = "FieldRefTest";
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
	public void testPropertyAccessFoundAsFieldReference() throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/fieldref");
		// Kotlin file with property access
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/fieldref/FieldUser.kt",
				"package fieldref\n"
				+ "\n"
				+ "class DataHolder {\n"
				+ "    val targetField: String = \"default\"\n"
				+ "    val metricCount: Int = 0\n"
				+ "}\n"
				+ "\n"
				+ "fun readFields() {\n"
				+ "    val holder = DataHolder()\n"
				+ "    val n = holder.targetField\n"
				+ "    val c = holder.metricCount\n"
				+ "    println(\"$n: $c\")\n"
				+ "}\n"
				+ "\n"
				+ "fun readFieldsAgain() {\n"
				+ "    val holder = DataHolder()\n"
				+ "    println(holder.targetField)\n"
				+ "    println(holder.metricCount)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for field references to "targetField"
		List<SearchMatch> fieldRefs = TestHelpers.searchFieldReferences("targetField", project);
		List<SearchMatch> ktFieldRefs = TestHelpers.filterKotlinMatches(fieldRefs);
		assertTrue(ktFieldRefs.size() >= 1,
				"Should find at least 1 Kotlin reference to 'targetField' property access");

		for (SearchMatch match : ktFieldRefs) {
			assertNotNull(match.getElement(), "Field reference match should have an element");
			assertTrue(match.getElement() instanceof IMember,
					"Field reference match element should be IMember");
			assertTrue(match.getOffset() >= 0, "Should have valid offset");
			assertTrue(match.getLength() > 0, "Should have positive length");
		}

		// Search for field references to "metricCount"
		List<SearchMatch> countRefs = TestHelpers.searchFieldReferences("metricCount", project);
		List<SearchMatch> ktCountRefs = TestHelpers.filterKotlinMatches(countRefs);
		assertTrue(ktCountRefs.size() >= 1,
				"Should find at least 1 Kotlin reference to 'metricCount' property access");
	}

	@Test
	public void testPropertyDeclarationFoundAsFieldDeclaration() throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/fieldref");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/fieldref/KotlinProps.kt",
				"package fieldref\n"
				+ "\n"
				+ "class KotlinConfig {\n"
				+ "    val appName: String = \"myapp\"\n"
				+ "    var maxRetries: Int = 3\n"
				+ "    val isEnabled: Boolean = true\n"
				+ "}\n"
				+ "\n"
				+ "val globalTimeout: Long = 5000L\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for field declaration "appName"
		List<SearchMatch> appNameDecls = TestHelpers.searchFieldDeclarations("appName", project);
		List<SearchMatch> ktAppName = TestHelpers.filterKotlinMatches(appNameDecls);
		assertTrue(ktAppName.size() >= 1,
				"Should find Kotlin property 'appName' as field declaration");
		IMember appNameMember = (IMember) ktAppName.get(0).getElement();
		assertTrue(appNameMember.getElementType() == IJavaElement.FIELD,
				"Property declaration should be FIELD type");

		// Search for field declaration "maxRetries"
		List<SearchMatch> maxRetriesDecls = TestHelpers.searchFieldDeclarations("maxRetries", project);
		List<SearchMatch> ktMaxRetries = TestHelpers.filterKotlinMatches(maxRetriesDecls);
		assertTrue(ktMaxRetries.size() >= 1,
				"Should find Kotlin property 'maxRetries' as field declaration");

		// Top-level property
		List<SearchMatch> timeoutDecls = TestHelpers.searchFieldDeclarations("globalTimeout", project);
		List<SearchMatch> ktTimeout = TestHelpers.filterKotlinMatches(timeoutDecls);
		assertTrue(ktTimeout.size() >= 1,
				"Should find top-level property 'globalTimeout' as field declaration");
	}

	@Test
	public void testFieldAllOccurrencesFindsDeclarationAndReferences() throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/fieldref");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/fieldref/PropOwner.kt",
				"package fieldref\n"
				+ "\n"
				+ "class PropOwner {\n"
				+ "    val status: String = \"ready\"\n"
				+ "\n"
				+ "    fun report(): String {\n"
				+ "        return \"Status: \" + status\n"
				+ "    }\n"
				+ "}\n"
				+ "\n"
				+ "fun externalAccess() {\n"
				+ "    val owner = PropOwner()\n"
				+ "    println(owner.status)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Field declaration should be found
		List<SearchMatch> decls = TestHelpers.searchFieldDeclarations("status", project);
		List<SearchMatch> ktDecls = TestHelpers.filterKotlinMatches(decls);
		assertTrue(ktDecls.size() >= 1,
				"Should find 'status' property declaration");

		// Field reference should be found (owner.status in externalAccess)
		List<SearchMatch> refs = TestHelpers.searchFieldReferences("status", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);
		assertTrue(ktRefs.size() >= 1,
				"Should find at least one reference to 'status' property");
	}

	@Test
	public void testChainedPropertyAccess() throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/fieldref");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/fieldref/Chain.kt",
				"package fieldref\n"
				+ "\n"
				+ "class Inner {\n"
				+ "    val value: String = \"inner\"\n"
				+ "}\n"
				+ "\n"
				+ "class Outer {\n"
				+ "    val inner: Inner = Inner()\n"
				+ "    val label: String = \"outer\"\n"
				+ "}\n"
				+ "\n"
				+ "fun chainedAccess() {\n"
				+ "    val obj = Outer()\n"
				+ "    println(obj.inner)\n"
				+ "    println(obj.label)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Field reference to "inner"
		List<SearchMatch> innerRefs = TestHelpers.searchFieldReferences("inner", project);
		List<SearchMatch> ktInnerRefs = TestHelpers.filterKotlinMatches(innerRefs);
		assertTrue(ktInnerRefs.size() >= 1,
				"Should find reference to 'inner' property in chained access");

		// Field reference to "label"
		List<SearchMatch> labelRefs = TestHelpers.searchFieldReferences("label", project);
		List<SearchMatch> ktLabelRefs = TestHelpers.filterKotlinMatches(labelRefs);
		assertTrue(ktLabelRefs.size() >= 1,
				"Should find reference to 'label' property");
	}

	@Test
	public void testEnclosingDeclarationForFieldReference() throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/fieldref");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/fieldref/Enclosing.kt",
				"package fieldref\n"
				+ "\n"
				+ "class Container {\n"
				+ "    val data: String = \"hello\"\n"
				+ "}\n"
				+ "\n"
				+ "fun functionA() {\n"
				+ "    val c = Container()\n"
				+ "    println(c.data)\n"
				+ "}\n"
				+ "\n"
				+ "fun functionB() {\n"
				+ "    val c = Container()\n"
				+ "    println(c.data)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers.searchFieldReferences("data", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);
		assertTrue(ktRefs.size() >= 2,
				"Should find at least 2 references to 'data' field");

		// Verify enclosing members are the functions
		List<String> enclosingNames = ktRefs.stream()
				.map(m -> ((IMember) m.getElement()).getElementName())
				.distinct()
				.toList();
		assertTrue(enclosingNames.contains("functionA"),
				"Should find functionA as enclosing member for data access");
		assertTrue(enclosingNames.contains("functionB"),
				"Should find functionB as enclosing member for data access");
	}
}
