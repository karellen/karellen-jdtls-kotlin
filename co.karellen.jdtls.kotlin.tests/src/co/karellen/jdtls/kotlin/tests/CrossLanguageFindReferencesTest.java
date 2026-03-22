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
 * Cross-language find references tests. Verifies that searching for
 * references to Java types/methods finds matching Kotlin call sites
 * with resolvable {@link IJavaElement}s.
 *
 * <p>These tests exercise the not-yet-implemented reference resolution
 * in {@code locateMatches()} and are expected to fail authentically,
 * serving as acceptance criteria for the next implementation phase.
 *
 * @author Arcadiy Ivanov
 */
public class CrossLanguageFindReferencesTest {

	private static final String PROJECT_NAME = "CrossLangRefTest";
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
	public void testV14FindReferencesToJavaType() throws CoreException {
		loadVersionFiles("v14");

		// KotlinB references ServiceBridge_V14 (JavaB type)
		// Expect at least one match from .kt with a resolvable IJavaElement
		List<SearchMatch> refs = TestHelpers.searchTypeReferences("ServiceBridge_V14", project);
		assertHasKotlinMatchWithElement(refs, "ServiceBridge_V14");
	}

	@Test
	public void testV14FindReferencesToKotlinType() throws CoreException {
		loadVersionFiles("v14");

		// JavaB references BridgeAdapter_V14 (KotlinB type) in its method signatures
		// KotlinA references EventHandler_V14 (KotlinB type)
		List<SearchMatch> refs = TestHelpers.searchTypeReferences("EventHandler_V14", project);
		assertHasKotlinMatchWithElement(refs, "EventHandler_V14");
	}

	@Test
	public void testV15FindReferencesToJavaMethod() throws CoreException {
		loadVersionFiles("v15");

		// KotlinB calls methods on MessageBridge_V15 (JavaB type)
		List<SearchMatch> refs = TestHelpers.searchMethodReferences("getMetrics", project);
		assertHasKotlinMatchWithElement(refs, "getMetrics");
	}

	@Test
	public void testV16FindReferencesToJavaType() throws CoreException {
		loadVersionFiles("v16");

		// KotlinB references CacheBridge_V16 (JavaB type)
		List<SearchMatch> refs = TestHelpers.searchTypeReferences("CacheBridge_V16", project);
		assertHasKotlinMatchWithElement(refs, "CacheBridge_V16");
	}

	@Test
	public void testV17FindReferencesToJavaType() throws CoreException {
		loadVersionFiles("v17");

		// KotlinB references TaskBridge_V17 (JavaB type)
		List<SearchMatch> refs = TestHelpers.searchTypeReferences("TaskBridge_V17", project);
		assertHasKotlinMatchWithElement(refs, "TaskBridge_V17");
	}

	@Test
	public void testV18FindReferencesToKotlinType() throws CoreException {
		loadVersionFiles("v18");

		// KotlinA references FlowHandler_V18 (KotlinB type)
		List<SearchMatch> refs = TestHelpers.searchTypeReferences("FlowHandler_V18", project);
		assertHasKotlinMatchWithElement(refs, "FlowHandler_V18");
	}

	@Test
	public void testV19FindReferencesToJavaType() throws CoreException {
		loadVersionFiles("v19");

		// KotlinB references PipelineBridge_V19 (JavaB type)
		List<SearchMatch> refs = TestHelpers.searchTypeReferences("PipelineBridge_V19", project);
		assertHasKotlinMatchWithElement(refs, "PipelineBridge_V19");
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

	private void assertHasKotlinMatchWithElement(List<SearchMatch> matches, String symbolName) {
		List<SearchMatch> ktMatches = matches.stream()
				.filter(m -> m.getResource() != null
						&& (m.getResource().getName().endsWith(".kt")
								|| m.getResource().getName().endsWith(".kts")))
				.toList();
		assertTrue(ktMatches.size() >= 1,
				"Should find at least one .kt reference to '" + symbolName + "'");
		for (SearchMatch match : ktMatches) {
			assertNotNull(match.getElement(),
					"Kotlin match for '" + symbolName + "' should have a non-null IJavaElement");
			assertTrue(match.getElement() instanceof IJavaElement,
					"Match element for '" + symbolName + "' should be an IJavaElement");
			assertTrue(match.getOffset() >= 0,
					"Match for '" + symbolName + "' should have a valid source offset");
			assertTrue(match.getLength() > 0,
					"Match for '" + symbolName + "' should have a positive length");
		}
	}
}
