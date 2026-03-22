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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.internal.core.search.indexing.SearchParticipantRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Cross-language type discovery tests. Verifies that Kotlin types from
 * resource files are indexed and findable via JDT search, including
 * nested types and version-specific constructs.
 *
 * <p>One {@code @Test} per Kotlin version (1.4 through 1.9). Each test
 * loads 4 interacting files (JavaA, JavaB, KotlinA, KotlinB), waits for
 * indexing, and asserts type discoverability.
 *
 * @author Arcadiy Ivanov
 */
public class CrossLanguageIndexingTest {

	private static final String PROJECT_NAME = "CrossLangTest";
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
	public void testV14TypeDiscovery() throws CoreException {
		loadVersionFiles("v14");

		// Kotlin types from KotlinA
		assertKotlinTypeFound("BaseService_V14");
		assertKotlinTypeFound("ServiceConfig_V14");
		assertKotlinTypeFound("ServiceProcessor_V14");
		assertKotlinTypeFound("ServiceStatus_V14");
		assertKotlinTypeFound("ProcessorType_V14");
		assertKotlinTypeFound("ServiceAnnotation_V14");
		assertKotlinTypeFound("DefaultCounter_V14");

		// Kotlin types from KotlinB
		assertKotlinTypeFound("EventHandler_V14");
		assertKotlinTypeFound("EventData_V14");
		assertKotlinTypeFound("BridgeAdapter_V14");
		assertKotlinTypeFound("AdapterFactory_V14");
		assertKotlinTypeFound("AdapterEvent_V14");

		// Java types should not come from .kt files
		assertJavaTypeNotFromKotlin("Repository_V14");
		assertJavaTypeNotFromKotlin("AbstractProcessor_V14");
		assertJavaTypeNotFromKotlin("ServiceBridge_V14");
		assertJavaTypeNotFromKotlin("DefaultBridge_V14");

		// Nested types discoverable by simple name
		assertKotlinTypeFound("ProcessorSnapshot_V14");
		assertKotlinTypeFound("AdapterStats_V14");
	}

	@Test
	public void testV15TypeDiscovery() throws CoreException {
		loadVersionFiles("v15");

		// Kotlin types from KotlinA
		assertKotlinTypeFound("MessageService_V15");
		assertKotlinTypeFound("MessageConfig_V15");
		assertKotlinTypeFound("MessageRouter_V15");
		assertKotlinTypeFound("RoutingStatus_V15");
		assertKotlinTypeFound("RouterMode_V15");
		assertKotlinTypeFound("MessageAnnotation_V15");

		// Kotlin types from KotlinB
		assertKotlinTypeFound("ChannelHandler_V15");
		assertKotlinTypeFound("MessagePayload_V15");
		assertKotlinTypeFound("ChannelAdapter_V15");
		assertKotlinTypeFound("ChannelFactory_V15");

		// Java types not from .kt
		assertJavaTypeNotFromKotlin("MessageSource_V15");
		assertJavaTypeNotFromKotlin("AbstractHandler_V15");
		assertJavaTypeNotFromKotlin("MessageBridge_V15");

		// Nested types
		assertKotlinTypeFound("RouterSnapshot_V15");
		assertKotlinTypeFound("AdapterStats_V15");

		// Version-specific: @JvmInline value classes
		assertKotlinTypeFound("MessageId_V15");
		assertKotlinTypeFound("Priority_V15");
	}

	@Test
	public void testV16TypeDiscovery() throws CoreException {
		loadVersionFiles("v16");

		// Kotlin types from KotlinA
		assertKotlinTypeFound("StorageService_V16");
		assertKotlinTypeFound("CacheConfig_V16");
		assertKotlinTypeFound("CacheManager_V16");
		assertKotlinTypeFound("CacheState_V16");
		assertKotlinTypeFound("CachePolicy_V16");
		assertKotlinTypeFound("CacheAnnotation_V16");

		// Kotlin types from KotlinB
		assertKotlinTypeFound("StorageHandler_V16");
		assertKotlinTypeFound("CacheEntry_V16");
		assertKotlinTypeFound("StorageAdapter_V16");
		assertKotlinTypeFound("StorageFactory_V16");

		// Java types not from .kt
		assertJavaTypeNotFromKotlin("CacheProvider_V16");
		assertJavaTypeNotFromKotlin("CacheBridge_V16");

		// Nested types
		assertKotlinTypeFound("CacheIterator_V16");

		// Version-specific: value class
		assertKotlinTypeFound("CacheKey_V16");
	}

	@Test
	public void testV17TypeDiscovery() throws CoreException {
		loadVersionFiles("v17");

		// Kotlin types from KotlinA
		assertKotlinTypeFound("TaskService_V17");
		assertKotlinTypeFound("TaskConfig_V17");
		assertKotlinTypeFound("TaskExecutor_V17");
		assertKotlinTypeFound("TaskState_V17");
		assertKotlinTypeFound("ExecutorMode_V17");
		assertKotlinTypeFound("TaskAnnotation_V17");

		// Kotlin types from KotlinB
		assertKotlinTypeFound("CoroutineHandler_V17");
		assertKotlinTypeFound("TaskResult_V17");
		assertKotlinTypeFound("CoroutineAdapter_V17");
		assertKotlinTypeFound("AdapterFactory_V17");

		// Java types not from .kt
		assertJavaTypeNotFromKotlin("TaskScheduler_V17");
		assertJavaTypeNotFromKotlin("AbstractTaskRunner_V17");
		assertJavaTypeNotFromKotlin("TaskBridge_V17");

		// Nested types
		assertKotlinTypeFound("ExecutionBatch_V17");

		// Version-specific: value class, NonNullProcessor (uses T & Any)
		assertKotlinTypeFound("TaskId_V17");
		assertKotlinTypeFound("NonNullProcessor_V17");
	}

	@Test
	public void testV18TypeDiscovery() throws CoreException {
		loadVersionFiles("v18");

		// Kotlin types from KotlinA
		assertKotlinTypeFound("StreamService_V18");
		assertKotlinTypeFound("StreamSettings_V18");
		assertKotlinTypeFound("StreamEngine_V18");
		assertKotlinTypeFound("StreamState_V18");
		assertKotlinTypeFound("EngineMode_V18");
		assertKotlinTypeFound("StreamAnnotation_V18");

		// Kotlin types from KotlinB
		assertKotlinTypeFound("FlowHandler_V18");
		assertKotlinTypeFound("StreamData_V18");
		assertKotlinTypeFound("FlowAdapter_V18");
		assertKotlinTypeFound("FlowFactory_V18");

		// Java types not from .kt
		assertJavaTypeNotFromKotlin("StreamProvider_V18");
		assertJavaTypeNotFromKotlin("AbstractStreamProcessor_V18");
		assertJavaTypeNotFromKotlin("StreamBridge_V18");

		// Nested types
		assertKotlinTypeFound("EngineContext_V18");
		assertKotlinTypeFound("AdapterContext_V18");

		// Version-specific: value class
		assertKotlinTypeFound("StreamId_V18");
	}

	@Test
	public void testV19TypeDiscovery() throws CoreException {
		loadVersionFiles("v19");

		// Kotlin types from KotlinA
		assertKotlinTypeFound("PipelineService_V19");
		assertKotlinTypeFound("PipelineSettings_V19");
		assertKotlinTypeFound("PipelineExecutor_V19");
		assertKotlinTypeFound("PipelineStatus_V19");
		assertKotlinTypeFound("ExecutionMode_V19");
		assertKotlinTypeFound("PipelineAnnotation_V19");

		// Kotlin types from KotlinB
		assertKotlinTypeFound("StageHandler_V19");
		assertKotlinTypeFound("PipelineData_V19");
		assertKotlinTypeFound("StageAdapter_V19");

		// Java types not from .kt
		assertJavaTypeNotFromKotlin("PipelineSource_V19");
		assertJavaTypeNotFromKotlin("AbstractPipelineStage_V19");
		assertJavaTypeNotFromKotlin("PipelineBridge_V19");

		// Nested types
		assertKotlinTypeFound("ExecutorContext_V19");
		assertKotlinTypeFound("AdapterContext_V19");

		// Version-specific: value class, data object
		assertKotlinTypeFound("PipelineId_V19");
		assertKotlinTypeFound("PipelineDefaults_V19");
		assertKotlinTypeFound("StageRegistry_V19");
	}

	// ---- Helpers ----

	private void loadVersionFiles(String version) throws CoreException {
		String pkg = "crosslang/" + version;
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

	private void assertKotlinTypeFound(String typeName) throws CoreException {
		List<SearchMatch> matches = TestHelpers.searchKotlinTypes(typeName, project);
		assertTrue(matches.size() >= 1,
				"Kotlin type '" + typeName + "' should be indexed and findable from .kt file");
	}

	private void assertJavaTypeNotFromKotlin(String typeName) throws CoreException {
		List<SearchMatch> allMatches = TestHelpers.searchAllTypes(typeName, project);
		for (SearchMatch match : allMatches) {
			if (match.getResource() != null) {
				assertFalse(match.getResource().getName().endsWith(".kt"),
						"Java type '" + typeName + "' should not appear from a .kt file");
				assertFalse(match.getResource().getName().endsWith(".kts"),
						"Java type '" + typeName + "' should not appear from a .kts file");
			}
		}
	}
}
