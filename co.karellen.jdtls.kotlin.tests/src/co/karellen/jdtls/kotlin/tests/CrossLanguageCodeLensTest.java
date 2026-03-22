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
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.internal.core.search.indexing.SearchParticipantRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Cross-language code lens reference counting tests. Verifies that
 * reference counts for types and methods include cross-language
 * references from Kotlin files.
 *
 * <p>These tests exercise the not-yet-implemented reference counting
 * and are expected to fail authentically, serving as acceptance criteria
 * for the next implementation phase.
 *
 * @author Arcadiy Ivanov
 */
public class CrossLanguageCodeLensTest {

	private static final String PROJECT_NAME = "CrossLangLensTest";
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
	public void testV14TypeRefCountIncludesKotlin() throws CoreException {
		loadVersionFiles("v14");

		// ServiceBridge_V14 (JavaB) is referenced from KotlinB
		List<SearchMatch> refs = TestHelpers.searchTypeReferences("ServiceBridge_V14", project);
		List<SearchMatch> ktRefs = filterKotlinMatches(refs);
		assertTrue(ktRefs.size() >= 1,
				"Reference count for 'ServiceBridge_V14' should include Kotlin references");
		assertAllHaveElement(ktRefs, "ServiceBridge_V14");
	}

	@Test
	public void testV15TypeRefCountIncludesKotlin() throws CoreException {
		loadVersionFiles("v15");

		// MessageBridge_V15 (JavaB) is referenced from KotlinB
		List<SearchMatch> refs = TestHelpers.searchTypeReferences("MessageBridge_V15", project);
		List<SearchMatch> ktRefs = filterKotlinMatches(refs);
		assertTrue(ktRefs.size() >= 1,
				"Reference count for 'MessageBridge_V15' should include Kotlin references");
		assertAllHaveElement(ktRefs, "MessageBridge_V15");
	}

	@Test
	public void testV16TypeRefCountIncludesKotlin() throws CoreException {
		loadVersionFiles("v16");

		// CacheBridge_V16 (JavaB) is referenced from KotlinB
		List<SearchMatch> refs = TestHelpers.searchTypeReferences("CacheBridge_V16", project);
		List<SearchMatch> ktRefs = filterKotlinMatches(refs);
		assertTrue(ktRefs.size() >= 1,
				"Reference count for 'CacheBridge_V16' should include Kotlin references");
		assertAllHaveElement(ktRefs, "CacheBridge_V16");
	}

	@Test
	public void testV17TypeRefCountIncludesKotlin() throws CoreException {
		loadVersionFiles("v17");

		// TaskBridge_V17 (JavaB) is referenced from KotlinB
		List<SearchMatch> refs = TestHelpers.searchTypeReferences("TaskBridge_V17", project);
		List<SearchMatch> ktRefs = filterKotlinMatches(refs);
		assertTrue(ktRefs.size() >= 1,
				"Reference count for 'TaskBridge_V17' should include Kotlin references");
		assertAllHaveElement(ktRefs, "TaskBridge_V17");
	}

	@Test
	public void testV18MethodRefCountIncludesKotlin() throws CoreException {
		loadVersionFiles("v18");

		// StreamBridge_V18 (JavaB) is referenced from KotlinB
		List<SearchMatch> refs = TestHelpers.searchTypeReferences("StreamBridge_V18", project);
		List<SearchMatch> ktRefs = filterKotlinMatches(refs);
		assertTrue(ktRefs.size() >= 1,
				"Reference count for 'StreamBridge_V18' should include Kotlin references");
		assertAllHaveElement(ktRefs, "StreamBridge_V18");
	}

	@Test
	public void testV19TypeRefCountIncludesKotlin() throws CoreException {
		loadVersionFiles("v19");

		// PipelineBridge_V19 (JavaB) is referenced from KotlinB
		List<SearchMatch> refs = TestHelpers.searchTypeReferences("PipelineBridge_V19", project);
		List<SearchMatch> ktRefs = filterKotlinMatches(refs);
		assertTrue(ktRefs.size() >= 1,
				"Reference count for 'PipelineBridge_V19' should include Kotlin references");
		assertAllHaveElement(ktRefs, "PipelineBridge_V19");
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

	private List<SearchMatch> filterKotlinMatches(List<SearchMatch> matches) {
		return matches.stream()
				.filter(m -> m.getResource() != null
						&& (m.getResource().getName().endsWith(".kt")
								|| m.getResource().getName().endsWith(".kts")))
				.toList();
	}

	private void assertAllHaveElement(List<SearchMatch> matches, String symbolName) {
		for (SearchMatch match : matches) {
			assertNotNull(match.getElement(),
					"Kotlin reference to '" + symbolName + "' should have a non-null IJavaElement for code lens");
			assertTrue(match.getElement() instanceof IJavaElement,
					"Reference element for '" + symbolName + "' should be an IJavaElement");
		}
	}
}
