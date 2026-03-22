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
 * Cross-language hover tests. Verifies that type declarations found
 * via search have resolvable {@link IJavaElement}s that can provide
 * type signatures for hover information.
 *
 * <p>These tests exercise the not-yet-implemented element resolution
 * and are expected to fail authentically, serving as acceptance criteria
 * for the next implementation phase.
 *
 * @author Arcadiy Ivanov
 */
public class CrossLanguageHoverTest {

	private static final String PROJECT_NAME = "CrossLangHoverTest";
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
	public void testV14HoverOverKotlinType() throws CoreException {
		loadVersionFiles("v14");

		// Searching for KotlinA type declarations should return matches
		// with resolvable IJavaElement for hover
		List<SearchMatch> matches = TestHelpers.searchKotlinTypes("BaseService_V14", project);
		assertMatchHasResolvableElement(matches, "BaseService_V14");
	}

	@Test
	public void testV14HoverOverKotlinDataClass() throws CoreException {
		loadVersionFiles("v14");

		List<SearchMatch> matches = TestHelpers.searchKotlinTypes("ServiceConfig_V14", project);
		assertMatchHasResolvableElement(matches, "ServiceConfig_V14");
	}

	@Test
	public void testV15HoverOverValueClass() throws CoreException {
		loadVersionFiles("v15");

		// v1.5 value class should be resolvable for hover
		List<SearchMatch> matches = TestHelpers.searchKotlinTypes("MessageId_V15", project);
		assertMatchHasResolvableElement(matches, "MessageId_V15");
	}

	@Test
	public void testV16HoverOverKotlinType() throws CoreException {
		loadVersionFiles("v16");

		List<SearchMatch> matches = TestHelpers.searchKotlinTypes("CacheManager_V16", project);
		assertMatchHasResolvableElement(matches, "CacheManager_V16");
	}

	@Test
	public void testV17HoverOverSuspendType() throws CoreException {
		loadVersionFiles("v17");

		// v1.7 type with suspend function types
		List<SearchMatch> matches = TestHelpers.searchKotlinTypes("TaskService_V17", project);
		assertMatchHasResolvableElement(matches, "TaskService_V17");
	}

	@Test
	public void testV18HoverOverKotlinType() throws CoreException {
		loadVersionFiles("v18");

		List<SearchMatch> matches = TestHelpers.searchKotlinTypes("StreamEngine_V18", project);
		assertMatchHasResolvableElement(matches, "StreamEngine_V18");
	}

	@Test
	public void testV19HoverOverDataObject() throws CoreException {
		loadVersionFiles("v19");

		// v1.9 data object should be resolvable for hover
		List<SearchMatch> matches = TestHelpers.searchKotlinTypes("PipelineDefaults_V19", project);
		assertMatchHasResolvableElement(matches, "PipelineDefaults_V19");
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

	private void assertMatchHasResolvableElement(List<SearchMatch> matches, String typeName) {
		assertTrue(matches.size() >= 1,
				"Should find at least one .kt match for '" + typeName + "'");
		for (SearchMatch match : matches) {
			assertNotNull(match.getElement(),
					"Match for '" + typeName + "' should have a non-null IJavaElement for hover resolution");
			assertTrue(match.getElement() instanceof IJavaElement,
					"Match element for '" + typeName + "' should be an IJavaElement");
		}
	}
}
