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
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;
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

	// ---- Expression receiver type references (#21) ----

	@Test
	public void testTypeRefOnExpressionReceiver() throws CoreException {
		// Java enum with static members
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/exprref/MyEnum.java",
				"package exprref;\n"
				+ "public enum MyEnum {\n"
				+ "    ALPHA, BETA;\n"
				+ "    public static MyEnum[] all() { return values(); }\n"
				+ "}\n");

		// Kotlin uses MyEnum only in expression context (no type annotation)
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/exprref/UseEnum.kt",
				"package exprref\n\n"
				+ "fun processEnum() {\n"
				+ "    val a = MyEnum.ALPHA\n"
				+ "    val b = MyEnum.BETA\n"
				+ "    val all = MyEnum.all()\n"
				+ "}\n");

		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers.searchTypeReferences("MyEnum", project);
		assertHasKotlinMatchWithElement(refs, "MyEnum");

		// Should find multiple references (ALPHA, BETA, all() each have receiver)
		List<SearchMatch> ktRefs = refs.stream()
				.filter(m -> m.getResource() != null
						&& m.getResource().getName().endsWith(".kt"))
				.toList();
		assertTrue(ktRefs.size() >= 1,
				"Should find at least 1 type reference in expression context");
	}

	@Test
	public void testTypeRefOnCompanionObjectAccess() throws CoreException {
		// Java class with static factory
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/exprref/Factory.java",
				"package exprref;\n"
				+ "public class Factory {\n"
				+ "    public static Factory create() { return new Factory(); }\n"
				+ "    public static final String NAME = \"factory\";\n"
				+ "}\n");

		// Kotlin uses Factory only as expression receiver
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/exprref/UseFactory.kt",
				"package exprref\n\n"
				+ "fun getFactory() {\n"
				+ "    val f = Factory.create()\n"
				+ "    val n = Factory.NAME\n"
				+ "}\n");

		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers.searchTypeReferences("Factory", project);
		assertHasKotlinMatchWithElement(refs, "Factory");
	}

	@Test
	public void testTypeRefOnCallableReference() throws CoreException {
		// Java class with method
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/exprref/Converter.java",
				"package exprref;\n"
				+ "public class Converter {\n"
				+ "    public static String convert(int i) { return String.valueOf(i); }\n"
				+ "}\n");

		// Kotlin uses Type::method callable ref
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/exprref/UseCallableRef.kt",
				"package exprref\n\n"
				+ "fun useRef() {\n"
				+ "    val ref = Converter::convert\n"
				+ "    val mapped = listOf(1, 2).map(Converter::convert)\n"
				+ "}\n");

		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers.searchTypeReferences("Converter", project);
		assertHasKotlinMatchWithElement(refs, "Converter");
	}

	// ---- Import statement references (#19) ----

	@Test
	public void testTypeRefInImportStatement() throws CoreException {
		// Java type
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/importref/Target.java",
				"package importref;\n"
				+ "public class Target {\n"
				+ "    public static void doWork() {}\n"
				+ "}\n");

		// Kotlin imports and uses the type
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/importref/UseTarget.kt",
				"package importref\n\n"
				+ "import importref.Target\n\n"
				+ "fun work() {\n"
				+ "    Target.doWork()\n"
				+ "}\n");

		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers.searchTypeReferences("Target", project);
		List<SearchMatch> ktRefs = refs.stream()
				.filter(m -> m.getResource() != null
						&& m.getResource().getName().endsWith(".kt"))
				.toList();
		// Should find at least 2 refs: import statement + expression usage
		assertTrue(ktRefs.size() >= 2,
				"Should find import + expression refs, got " + ktRefs.size());
	}

	@Test
	public void testTypeRefImportOnly() throws CoreException {
		// Java type
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/importref/Unused.java",
				"package importref;\n"
				+ "public class Unused {\n"
				+ "    public static final int VALUE = 42;\n"
				+ "}\n");

		// Kotlin imports but doesn't use in a type annotation position
		// (only has the import, no usage in code — unused import)
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/importref/HasUnused.kt",
				"package importref\n\n"
				+ "import importref.Unused\n"
				+ "import importref.Target\n\n"
				+ "fun nothing() {}\n");

		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers.searchTypeReferences("Unused", project);
		List<SearchMatch> ktRefs = refs.stream()
				.filter(m -> m.getResource() != null
						&& m.getResource().getName().endsWith(".kt"))
				.toList();
		// Should find import-only reference
		assertTrue(ktRefs.size() >= 1,
				"Should find import ref even without usage, got " + ktRefs.size());
	}

	// ---- Element-based find-references reproducer (#11) ----
	// Reproduces: find-references from a Java type should find Kotlin
	// usages. Uses element-based pattern (matching real jdtls flow)
	// rather than string-based pattern.

	@Test
	public void testElementBasedFindReferencesFindsKotlinUsages()
			throws CoreException {
		// Java enum (pattern: AriesUserRole)
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/elemref/Role.java",
				"package elemref;\n"
				+ "public enum Role {\n"
				+ "    ADMIN, USER, GUEST;\n"
				+ "}\n");

		// Kotlin file using the Java enum in type annotation + expression
		// (pattern: AuthUtils.kt referencing AriesUserRole)
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/elemref/Auth.kt",
				"package elemref\n\n"
				+ "fun checkRole(role: Role): Boolean {\n"
				+ "    return role == Role.ADMIN\n"
				+ "}\n"
				+ "fun defaultRole(): Role = Role.USER\n");

		TestHelpers.waitUntilIndexesReady();

		// Find the Java IType element (mimics ReferencesHandler flow)
		List<SearchMatch> typeDecls = TestHelpers.searchAllTypes(
				"Role", project);
		SearchMatch javaDecl = typeDecls.stream()
				.filter(m -> m.getResource() != null
						&& m.getResource().getName().endsWith(".java"))
				.findFirst()
				.orElse(null);
		assertNotNull(javaDecl, "Should find Java Role type declaration");

		IJavaElement javaElement = (IJavaElement) javaDecl.getElement();
		SearchPattern pattern = SearchPattern.createPattern(
				javaElement, IJavaSearchConstants.REFERENCES);
		assertNotNull(pattern, "Should create reference pattern");

		// Search with all participants (mimics ReferencesHandler)
		List<SearchMatch> refs = TestHelpers.executeSearch(
				pattern, project);
		List<SearchMatch> ktRefs = refs.stream()
				.filter(m -> m.getResource() != null
						&& m.getResource().getName().endsWith(".kt"))
				.toList();
		assertTrue(ktRefs.size() >= 1,
				"Element-based find-references should find Kotlin "
				+ "usages of Java type; got " + ktRefs.size());
	}

	@Test
	public void testElementBasedFindRefsWithIncludeDeclaration()
			throws CoreException {
		// Same setup as testElementBasedFindReferencesFindsKotlinUsages
		// but with REFERENCES + DECLARATIONS (OrPattern), mimicking
		// jdtls ReferencesHandler with includeDeclaration=true.
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/elemref/Role.java",
				"package elemref;\n"
				+ "public enum Role {\n"
				+ "    ADMIN, USER, GUEST;\n"
				+ "}\n");

		TestHelpers.createFile("/" + PROJECT_NAME + "/src/elemref/Auth.kt",
				"package elemref\n\n"
				+ "fun checkRole(role: Role): Boolean {\n"
				+ "    return role == Role.ADMIN\n"
				+ "}\n"
				+ "fun defaultRole(): Role = Role.USER\n");

		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> typeDecls = TestHelpers.searchAllTypes(
				"Role", project);
		SearchMatch javaDecl = typeDecls.stream()
				.filter(m -> m.getResource() != null
						&& m.getResource().getName().endsWith(".java"))
				.findFirst()
				.orElse(null);
		assertNotNull(javaDecl, "Should find Java Role type declaration");

		// Create OrPattern: REFERENCES | DECLARATIONS (what jdtls does
		// when includeDeclaration=true)
		IJavaElement javaElement = (IJavaElement) javaDecl.getElement();
		SearchPattern refsPattern = SearchPattern.createPattern(
				javaElement, IJavaSearchConstants.REFERENCES);
		SearchPattern declPattern = SearchPattern.createPattern(
				javaElement, IJavaSearchConstants.DECLARATIONS);
		SearchPattern orPattern = SearchPattern.createOrPattern(
				refsPattern, declPattern);

		List<SearchMatch> refs = TestHelpers.executeSearch(
				orPattern, project);
		List<SearchMatch> ktRefs = refs.stream()
				.filter(m -> m.getResource() != null
						&& m.getResource().getName().endsWith(".kt"))
				.toList();
		assertTrue(ktRefs.size() >= 1,
				"OrPattern (refs+decls) should still find Kotlin "
				+ "usages of Java type; got " + ktRefs.size());
	}

	@Test
	public void testElementBasedFindRefsCrossPackage()
			throws CoreException {
		// Java enum in one package (pattern: AriesUserRole in
		// tourlandish.common.security)
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/crossref/security/UserRole.java",
				"package crossref.security;\n"
				+ "public enum UserRole {\n"
				+ "    ADMIN, USER, GUEST;\n"
				+ "}\n");

		// Kotlin in different package, references via import
		// (pattern: AuthUtils.kt in different module/package)
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/crossref/service/AuthHelper.kt",
				"package crossref.service\n\n"
				+ "import crossref.security.UserRole\n\n"
				+ "fun isAdmin(role: UserRole): Boolean {\n"
				+ "    return role == UserRole.ADMIN\n"
				+ "}\n");

		// Second Kotlin file in yet another package
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/crossref/util/RoleUtils.kt",
				"package crossref.util\n\n"
				+ "import crossref.security.UserRole\n\n"
				+ "fun defaultRole(): UserRole = UserRole.GUEST\n");

		TestHelpers.waitUntilIndexesReady();

		// Find the Java IType (mimics ReferencesHandler)
		List<SearchMatch> typeDecls = TestHelpers.searchAllTypes(
				"UserRole", project);
		SearchMatch javaDecl = typeDecls.stream()
				.filter(m -> m.getResource() != null
						&& m.getResource().getName().endsWith(".java"))
				.findFirst()
				.orElse(null);
		assertNotNull(javaDecl, "Should find Java UserRole");

		// Element-based reference search (what jdtls does)
		IJavaElement javaElement = (IJavaElement) javaDecl.getElement();
		SearchPattern pattern = SearchPattern.createPattern(
				javaElement, IJavaSearchConstants.REFERENCES);

		List<SearchMatch> refs = TestHelpers.executeSearch(
				pattern, project);
		List<SearchMatch> ktRefs = refs.stream()
				.filter(m -> m.getResource() != null
						&& m.getResource().getName().endsWith(".kt"))
				.toList();
		assertTrue(ktRefs.size() >= 2,
				"Cross-package element-based find-references should "
				+ "find Kotlin usages in both .kt files; got "
				+ ktRefs.size());
	}

	// ---- Multi-project find-references (#11) ----
	// Reproduces: find-references across multiple projects (modules).
	// In a multi-module Gradle workspace, a Java type in module A should
	// be found when referenced by Kotlin files in module B.

	@Test
	public void testElementBasedFindRefsCrossProject()
			throws CoreException {
		// Create a second project that depends on the first
		String projectB = "CrossLangRefTestB";
		IJavaProject projB = null;
		try {
			// Project A: Java type
			TestHelpers.createFile(
					"/" + PROJECT_NAME + "/src/crossproj/api/Status.java",
					"package crossproj.api;\n"
					+ "public enum Status {\n"
					+ "    ACTIVE, INACTIVE, PENDING;\n"
					+ "}\n");

			// Project B: depends on Project A, has Kotlin files referencing Status
			projB = TestHelpers.createJavaProject(projectB, "src");

			// Add project A to project B's classpath
			IClasspathEntry[] existing = projB.getRawClasspath();
			IClasspathEntry[] withDep = new IClasspathEntry[existing.length + 1];
			System.arraycopy(existing, 0, withDep, 0, existing.length);
			withDep[existing.length] = JavaCore.newProjectEntry(
					project.getProject().getFullPath());
			projB.setRawClasspath(withDep, null);

			// Kotlin file in project B referencing Java type from project A
			TestHelpers.createFile(
					"/" + projectB + "/src/crossproj/service/StatusHelper.kt",
					"package crossproj.service\n\n"
					+ "import crossproj.api.Status\n\n"
					+ "fun isActive(s: Status): Boolean {\n"
					+ "    return s == Status.ACTIVE\n"
					+ "}\n"
					+ "fun defaultStatus(): Status = Status.PENDING\n");

			// Second Kotlin file in project B
			TestHelpers.createFile(
					"/" + projectB + "/src/crossproj/util/StatusUtils.kt",
					"package crossproj.util\n\n"
					+ "import crossproj.api.Status\n\n"
					+ "fun allStatuses(): List<Status> {\n"
					+ "    return listOf(Status.ACTIVE, Status.INACTIVE, Status.PENDING)\n"
					+ "}\n");

			TestHelpers.waitUntilIndexesReady();

			// Find the Java IType element from project A
			List<SearchMatch> typeDecls = TestHelpers.searchAllTypes(
					"Status", project);
			SearchMatch javaDecl = typeDecls.stream()
					.filter(m -> m.getResource() != null
							&& m.getResource().getName().endsWith(".java"))
					.findFirst()
					.orElse(null);
			assertNotNull(javaDecl, "Should find Java Status type declaration");

			// Create element-based reference pattern
			IJavaElement javaElement = (IJavaElement) javaDecl.getElement();
			SearchPattern pattern = SearchPattern.createPattern(
					javaElement, IJavaSearchConstants.REFERENCES);
			assertNotNull(pattern, "Should create reference pattern");

			// Search across BOTH projects (mimics jdtls ReferencesHandler scope)
			List<SearchMatch> refs = TestHelpers.executeSearch(
					pattern, project, projB);
			List<SearchMatch> ktRefs = refs.stream()
					.filter(m -> m.getResource() != null
							&& m.getResource().getName().endsWith(".kt"))
					.toList();
			assertTrue(ktRefs.size() >= 2,
					"Cross-project element-based find-references should "
					+ "find Kotlin usages from project B; got "
					+ ktRefs.size());
		} finally {
			if (projB != null) {
				TestHelpers.deleteProject(projectB);
			}
		}
	}

	@Test
	public void testElementBasedFindRefsCrossProjectNoDependency()
			throws CoreException {
		// Two independent projects (no classpath dependency).
		// Mimics jdtls ReferencesHandler creating scope from ALL workspace projects.
		String projectB = "CrossLangRefTestNoDep";
		IJavaProject projB = null;
		try {
			// Project A: Java type
			TestHelpers.createFile(
					"/" + PROJECT_NAME + "/src/nodep/api/Event.java",
					"package nodep.api;\n"
					+ "public class Event {\n"
					+ "    public String type;\n"
					+ "    public String payload;\n"
					+ "}\n");

			// Project B: independent, Kotlin files reference Event
			projB = TestHelpers.createJavaProject(projectB, "src");

			// Kotlin file duplicates the package path (no classpath link)
			// This is how it works in large Gradle repos where multiple
			// modules can reference the same types via star-imports or
			// re-exported dependencies
			TestHelpers.createFile(
					"/" + projectB + "/src/nodep/handler/EventHandler.kt",
					"package nodep.handler\n\n"
					+ "import nodep.api.Event\n\n"
					+ "fun handle(e: Event): String {\n"
					+ "    return e.type + Event().payload\n"
					+ "}\n");

			TestHelpers.waitUntilIndexesReady();

			// Find the Java IType element from project A
			List<SearchMatch> typeDecls = TestHelpers.searchAllTypes(
					"Event", project);
			SearchMatch javaDecl = typeDecls.stream()
					.filter(m -> m.getResource() != null
							&& m.getResource().getName().endsWith(".java"))
					.findFirst()
					.orElse(null);
			assertNotNull(javaDecl, "Should find Java Event type");

			// Element-based reference search across both projects
			IJavaElement javaElement = (IJavaElement) javaDecl.getElement();
			SearchPattern pattern = SearchPattern.createPattern(
					javaElement, IJavaSearchConstants.REFERENCES);

			// Search with scope covering BOTH projects (no dependency)
			List<SearchMatch> refs = TestHelpers.executeSearch(
					pattern, project, projB);
			List<SearchMatch> ktRefs = refs.stream()
					.filter(m -> m.getResource() != null
							&& m.getResource().getName().endsWith(".kt"))
					.toList();
			assertTrue(ktRefs.size() >= 1,
					"Cross-project (no dependency) find-references should "
					+ "find Kotlin usages; got " + ktRefs.size());
		} finally {
			if (projB != null) {
				TestHelpers.deleteProject(projectB);
			}
		}
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
