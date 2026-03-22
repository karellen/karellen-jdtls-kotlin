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
 * Cross-language implementation/subtype search tests. Verifies that
 * searching for implementors of Java interfaces finds Kotlin classes,
 * and searching for subtypes of Kotlin classes finds subclasses.
 *
 * <p>These tests exercise the not-yet-implemented implementation search
 * and are expected to fail authentically, serving as acceptance criteria
 * for the next implementation phase.
 *
 * @author Arcadiy Ivanov
 */
public class CrossLanguageImplementationTest {

	private static final String PROJECT_NAME = "CrossLangImplTest";
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
	public void testV14KotlinImplementsKotlinInterface() throws CoreException {
		loadVersionFiles("v14");

		// BaseService_V14 is a Kotlin interface; ServiceProcessor_V14 implements it
		List<SearchMatch> impls = TestHelpers.searchImplementors("BaseService_V14", project);
		assertHasKotlinImplementor(impls, "BaseService_V14");
	}

	@Test
	public void testV14KotlinImplementsKotlinInterfaceBridge() throws CoreException {
		loadVersionFiles("v14");

		// EventHandler_V14 is a Kotlin interface; BridgeAdapter_V14 implements it
		List<SearchMatch> impls = TestHelpers.searchImplementors("EventHandler_V14", project);
		assertHasKotlinImplementor(impls, "EventHandler_V14");
	}

	@Test
	public void testV15SealedClassSubtypes() throws CoreException {
		loadVersionFiles("v15");

		// RoutingStatus_V15 is a sealed class with subclasses
		List<SearchMatch> impls = TestHelpers.searchImplementors("RoutingStatus_V15", project);
		assertHasKotlinImplementor(impls, "RoutingStatus_V15");
	}

	@Test
	public void testV16KotlinImplementsKotlinInterface() throws CoreException {
		loadVersionFiles("v16");

		// StorageService_V16 is a Kotlin interface
		List<SearchMatch> impls = TestHelpers.searchImplementors("StorageService_V16", project);
		assertHasKotlinImplementor(impls, "StorageService_V16");
	}

	@Test
	public void testV17SealedClassSubtypes() throws CoreException {
		loadVersionFiles("v17");

		// TaskState_V17 is a sealed class with subclasses
		List<SearchMatch> impls = TestHelpers.searchImplementors("TaskState_V17", project);
		assertHasKotlinImplementor(impls, "TaskState_V17");
	}

	@Test
	public void testV18KotlinImplementsKotlinInterface() throws CoreException {
		loadVersionFiles("v18");

		// StreamService_V18 is a Kotlin interface
		List<SearchMatch> impls = TestHelpers.searchImplementors("StreamService_V18", project);
		assertHasKotlinImplementor(impls, "StreamService_V18");
	}

	@Test
	public void testV19SealedClassSubtypes() throws CoreException {
		loadVersionFiles("v19");

		// PipelineStatus_V19 is a sealed class with subclasses
		List<SearchMatch> impls = TestHelpers.searchImplementors("PipelineStatus_V19", project);
		assertHasKotlinImplementor(impls, "PipelineStatus_V19");
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

	private void assertHasKotlinImplementor(List<SearchMatch> matches, String typeName) {
		List<SearchMatch> ktMatches = matches.stream()
				.filter(m -> m.getResource() != null
						&& (m.getResource().getName().endsWith(".kt")
								|| m.getResource().getName().endsWith(".kts")))
				.toList();
		assertTrue(ktMatches.size() >= 1,
				"Should find at least one Kotlin implementor of '" + typeName + "'");
		for (SearchMatch match : ktMatches) {
			assertNotNull(match.getElement(),
					"Kotlin implementor of '" + typeName + "' should have a non-null IJavaElement");
			assertTrue(match.getElement() instanceof IJavaElement,
					"Implementor element for '" + typeName + "' should be an IJavaElement");
		}
	}
}
