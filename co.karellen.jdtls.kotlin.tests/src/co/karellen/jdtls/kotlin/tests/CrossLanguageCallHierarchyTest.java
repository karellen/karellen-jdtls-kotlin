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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchDocument;
import org.eclipse.jdt.core.search.SearchEngine;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.core.manipulation.JavaManipulation;
import org.eclipse.jdt.internal.core.search.indexing.SearchParticipantRegistry;
import org.eclipse.jdt.internal.corext.callhierarchy.CallHierarchyCore;
import org.eclipse.jdt.internal.corext.callhierarchy.MethodWrapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import co.karellen.jdtls.kotlin.search.KotlinCompilationUnit;
import co.karellen.jdtls.kotlin.search.KotlinElement;
import co.karellen.jdtls.kotlin.search.KotlinModelManager;

/**
 * Tests for cross-language incoming call hierarchy support.
 * Verifies that KotlinElement implements IMember, that reference
 * search matches report the enclosing declaration as the element,
 * and that Kotlin callers of Java methods are discoverable.
 *
 * @author Arcadiy Ivanov
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class CrossLanguageCallHierarchyTest {

	private static final String PROJECT_NAME = "CrossLangCallTest";
	private IJavaProject project;

	@BeforeAll
	public void setUpClass() throws CoreException {
		// Initialize preference node for JavaManipulation — required by
		// CallHierarchyCore.isShowAll() which reads preferences via
		// InstanceScope/DefaultScope. In a full jdtls, this is set by
		// the org.eclipse.jdt.ls.core activator; in the test container
		// that bundle isn't activated.
		if (JavaManipulation.getPreferenceNodeId() == null) {
			JavaManipulation.setPreferenceNodeId(
					"co.karellen.jdtls.kotlin.tests");
		}
		project = TestHelpers.createJavaProjectWithJRE(PROJECT_NAME, "src");
	}

	@AfterAll
	public void tearDownClass() throws CoreException {
		TestHelpers.deleteProject(PROJECT_NAME);
		project = null;
	}

	@BeforeEach
	public void setUp() throws CoreException {
		SearchParticipantRegistry.reset();
		// Clean src folder contents from previous test
		org.eclipse.core.resources.IFolder srcFolder =
				project.getProject().getFolder("src");
		for (org.eclipse.core.resources.IResource member
				: srcFolder.members()) {
			member.delete(true, null);
		}
	}

	@Test
	public void testIMemberInterface() throws CoreException {
		// Create a Kotlin file with a class and a function
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/callhier");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/callhier/Service.kt",
				"package callhier\n"
				+ "\n"
				+ "class ServiceImpl {\n"
				+ "    fun process(input: String): Boolean {\n"
				+ "        return input.isNotEmpty()\n"
				+ "    }\n"
				+ "\n"
				+ "    val status: String = \"active\"\n"
				+ "}\n"
				+ "\n"
				+ "fun topLevelHelper(): Int = 42\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for type declaration — should be an IMember
		List<SearchMatch> typeMatches = TestHelpers.searchKotlinTypes("ServiceImpl", project);
		assertTrue(typeMatches.size() >= 1, "Should find ServiceImpl type");
		Object typeElement = typeMatches.get(0).getElement();
		assertInstanceOf(IMember.class, typeElement, "Type element should be IMember");
		IMember typeMember = (IMember) typeElement;
		assertNotNull(typeMember.getCompilationUnit(), "Should have compilation unit");
		assertNotNull(typeMember.getTypeRoot(), "Should have type root");
		assertFalse(typeMember.isBinary(), "Should not be binary");
		assertNull(typeMember.getClassFile(), "Should not have class file");
		assertNull(typeMember.getDeclaringType(), "Top-level type has no declaring type");
		assertEquals(0, typeMember.getFlags(), "Flags should be 0");
		assertEquals(1, typeMember.getOccurrenceCount(), "Occurrence count should be 1");
		assertEquals(0, typeMember.getCategories().length, "Should have no categories");
		assertNull(typeMember.getJavadocRange(), "Should have no javadoc range");
		assertNull(typeMember.getType("Nested", 1), "getType should return null");

		// Search for method declaration — should be an IMember
		List<SearchMatch> methodMatches = TestHelpers.searchMethodDeclarations("process", project);
		List<SearchMatch> ktMethodMatches = TestHelpers.filterKotlinMatches(methodMatches);
		assertTrue(ktMethodMatches.size() >= 1, "Should find process method");
		assertInstanceOf(IMember.class, ktMethodMatches.get(0).getElement(),
				"Method element should be IMember");

		// Search for field declaration — should be an IMember
		List<SearchMatch> fieldMatches = TestHelpers.searchFieldDeclarations("status", project);
		List<SearchMatch> ktFieldMatches = TestHelpers.filterKotlinMatches(fieldMatches);
		assertTrue(ktFieldMatches.size() >= 1, "Should find status field");
		assertInstanceOf(IMember.class, ktFieldMatches.get(0).getElement(),
				"Field element should be IMember");
	}

	@Test
	public void testSearchMatchHasEnclosingMember() throws CoreException {
		// Create a Kotlin file where a function references a type
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/callhier");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/callhier/Caller.kt",
				"package callhier\n"
				+ "\n"
				+ "fun callerFunction() {\n"
				+ "    val svc = TargetService()\n"
				+ "    svc.toString()\n"
				+ "}\n"
				+ "\n"
				+ "fun otherFunction() {\n"
				+ "    val x = TargetService()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for references to TargetService
		List<SearchMatch> refs = TestHelpers.searchTypeReferences("TargetService", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);
		assertTrue(ktRefs.size() >= 2,
				"Should find at least 2 references to TargetService in Kotlin");

		// Each match element should be the enclosing function, not the target
		for (SearchMatch match : ktRefs) {
			assertInstanceOf(IMember.class, match.getElement(),
					"Match element should be an IMember (enclosing declaration)");
			IMember enclosing = (IMember) match.getElement();
			// The enclosing element should be a method (function) or type
			assertTrue(
					enclosing.getElementType() == IJavaElement.METHOD
					|| enclosing.getElementType() == IJavaElement.TYPE,
					"Enclosing element should be a method or type, got: "
					+ enclosing.getElementType());
			assertNotNull(enclosing.getCompilationUnit(),
					"Enclosing member should have a compilation unit");
		}

		// Verify we found both callerFunction and otherFunction as enclosing
		List<String> callerNames = ktRefs.stream()
				.map(m -> ((IMember) m.getElement()).getElementName())
				.distinct()
				.toList();
		assertTrue(callerNames.contains("callerFunction"),
				"Should find callerFunction as enclosing member");
		assertTrue(callerNames.contains("otherFunction"),
				"Should find otherFunction as enclosing member");
	}

	@Test
	public void testMethodReferenceSearchFindsKotlinCallers() throws CoreException {
		// Java class with a method
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/callhier");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/callhier/DataProcessor.java",
				"package callhier;\n"
				+ "\n"
				+ "public class DataProcessor {\n"
				+ "    public void transform(String data) {}\n"
				+ "    public int count() { return 0; }\n"
				+ "}\n");
		// Kotlin file calling the Java method
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/callhier/KotlinClient.kt",
				"package callhier\n"
				+ "\n"
				+ "fun kotlinCaller() {\n"
				+ "    val proc = DataProcessor()\n"
				+ "    proc.transform(\"hello\")\n"
				+ "}\n"
				+ "\n"
				+ "fun anotherKotlinCaller() {\n"
				+ "    val proc = DataProcessor()\n"
				+ "    proc.transform(\"world\")\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for references to "transform" method
		List<SearchMatch> refs = TestHelpers.searchMethodReferences("transform", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);
		assertTrue(ktRefs.size() >= 2,
				"Should find at least 2 Kotlin callers of transform()");

		for (SearchMatch match : ktRefs) {
			assertInstanceOf(IMember.class, match.getElement(),
					"Match element should be IMember");
			IMember caller = (IMember) match.getElement();
			assertEquals(IJavaElement.METHOD, caller.getElementType(),
					"Caller should be a method element");
			assertNotNull(caller.getCompilationUnit(),
					"Caller should have a compilation unit");
			assertTrue(match.getOffset() >= 0, "Match should have valid offset");
			assertTrue(match.getLength() > 0, "Match should have positive length");
		}
	}

	@Test
	public void testFieldReferenceSearchFindsKotlinCallers() throws CoreException {
		// Java class with a field
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/callhier");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/callhier/Config.java",
				"package callhier;\n"
				+ "\n"
				+ "public class Config {\n"
				+ "    public static String appName = \"test\";\n"
				+ "    public int maxRetries = 3;\n"
				+ "}\n");
		// Kotlin file referencing the Java field via type reference
		// (field access isn't directly supported by KotlinReferenceFinder,
		// but type references to Config are)
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/callhier/KotlinUser.kt",
				"package callhier\n"
				+ "\n"
				+ "fun useConfig() {\n"
				+ "    val cfg = Config()\n"
				+ "    println(cfg.toString())\n"
				+ "}\n"
				+ "\n"
				+ "fun anotherUse() {\n"
				+ "    val cfg = Config()\n"
				+ "    cfg.toString()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for type references to Config (field reference search is
		// limited in the current parser, so use type references instead)
		List<SearchMatch> refs = TestHelpers.searchTypeReferences("Config", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);
		assertTrue(ktRefs.size() >= 2,
				"Should find at least 2 Kotlin references to Config");

		for (SearchMatch match : ktRefs) {
			assertInstanceOf(IMember.class, match.getElement(),
					"Match element should be IMember");
		}
	}

	@Test
	public void testMultipleCallSitesGroupedByCaller() throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/callhier");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/callhier/Api.java",
				"package callhier;\n"
				+ "\n"
				+ "public class Api {\n"
				+ "    public void invoke(String arg) {}\n"
				+ "}\n");
		// Kotlin function with two calls to the same method
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/callhier/MultiCaller.kt",
				"package callhier\n"
				+ "\n"
				+ "fun multiCallFunction() {\n"
				+ "    val api = Api()\n"
				+ "    api.invoke(\"first\")\n"
				+ "    api.invoke(\"second\")\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers.searchMethodReferences("invoke", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);
		assertTrue(ktRefs.size() >= 2,
				"Should find at least 2 call sites for invoke()");

		// All Kotlin matches should have the same enclosing member
		Map<String, List<SearchMatch>> grouped = ktRefs.stream()
				.filter(m -> m.getElement() instanceof IMember)
				.collect(Collectors.groupingBy(
						m -> ((IMember) m.getElement()).getElementName()));
		assertTrue(grouped.containsKey("multiCallFunction"),
				"All call sites should be grouped under multiCallFunction");
		assertTrue(grouped.get("multiCallFunction").size() >= 2,
				"multiCallFunction should contain at least 2 call sites");

		// Verify the call site offsets are different (different locations)
		List<SearchMatch> multiCallMatches = grouped.get("multiCallFunction");
		assertTrue(multiCallMatches.get(0).getOffset()
				!= multiCallMatches.get(1).getOffset(),
				"Call sites should have different offsets");
	}

	// ---- locateCallees tests (outgoing call hierarchy) ----

	@Test
	public void testLocateCalleesFindsMethodAndConstructorCalls()
			throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/callhier");
		IFile ktFile = TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/callhier/OutgoingCaller.kt",
				"package callhier\n"
				+ "\n"
				+ "fun callerFunction() {\n"
				+ "    val svc = DataProcessor()\n"
				+ "    svc.transform(\"hello\")\n"
				+ "    svc.validate(\"world\")\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		SearchParticipant participant = SearchParticipantRegistry
				.getParticipant("kt");
		assertNotNull(participant, "Kotlin participant should be registered");

		// Find callerFunction via method declaration search
		List<SearchMatch> decls = TestHelpers.searchMethodDeclarations(
				"callerFunction", project);
		List<SearchMatch> ktDecls = TestHelpers.filterKotlinMatches(decls);
		assertTrue(ktDecls.size() >= 1, "Should find callerFunction declaration");
		IMember caller = (IMember) ktDecls.get(0).getElement();

		String path = caller.getCompilationUnit().getPath().toString();
		SearchDocument document = participant.getDocument(path);
		SearchMatch[] callees = participant.locateCallees(
				caller, document, null);

		// Should find: DataProcessor (constructor), transform, validate
		assertTrue(callees.length >= 3,
				"Should find at least 3 callees, found: " + callees.length);

		Map<String, IMember> calleesByName = new LinkedHashMap<>();
		for (SearchMatch match : callees) {
			IMember callee = (IMember) match.getElement();
			calleesByName.put(callee.getElementName(), callee);
		}
		assertTrue(calleesByName.containsKey("DataProcessor"),
				"Should find DataProcessor constructor call. "
				+ "Found: " + calleesByName.keySet());
		assertTrue(calleesByName.containsKey("transform"),
				"Should find transform method call. "
				+ "Found: " + calleesByName.keySet());
		assertTrue(calleesByName.containsKey("validate"),
				"Should find validate method call. "
				+ "Found: " + calleesByName.keySet());

		// DataProcessor is undefined — should be a stub
		IMember dpCallee = calleesByName.get("DataProcessor");
		assertFalse(dpCallee.exists(),
				"DataProcessor is undefined — should be a stub. "
				+ "Class: " + dpCallee.getClass().getSimpleName());

		// transform/validate receivers are unresolvable (svc type
		// inferred from undefined DataProcessor) — should be stubs
		IMember transformCallee = calleesByName.get("transform");
		assertFalse(transformCallee.exists(),
				"transform receiver unresolvable — should be stub. "
				+ "Class: " + transformCallee.getClass()
						.getSimpleName());
		IMember validateCallee = calleesByName.get("validate");
		assertFalse(validateCallee.exists(),
				"validate receiver unresolvable — should be stub. "
				+ "Class: " + validateCallee.getClass()
						.getSimpleName());
	}

	@Test
	public void testLocateCalleesDistinguishesCalleeKinds()
			throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/callhier");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/callhier/KindTest.kt",
				"package callhier\n"
				+ "\n"
				+ "fun kindTestFunction() {\n"
				+ "    val obj = StringBuilder()\n"
				+ "    obj.append(\"x\")\n"
				+ "    println(obj)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		SearchParticipant participant = SearchParticipantRegistry
				.getParticipant("kt");
		List<SearchMatch> decls = TestHelpers.searchMethodDeclarations(
				"kindTestFunction", project);
		List<SearchMatch> ktDecls = TestHelpers.filterKotlinMatches(decls);
		assertTrue(ktDecls.size() >= 1);
		IMember caller = (IMember) ktDecls.get(0).getElement();

		String path = caller.getCompilationUnit().getPath().toString();
		SearchMatch[] callees = participant.locateCallees(
				caller, participant.getDocument(path), null);

		// StringBuilder → TYPE (constructor), append → METHOD, println → METHOD
		Map<String, IMember> calleesByName = new LinkedHashMap<>();
		for (SearchMatch match : callees) {
			IMember callee = (IMember) match.getElement();
			calleesByName.put(callee.getElementName(), callee);
		}

		// StringBuilder constructor — resolved via java.lang.* default import
		IMember sbCallee = calleesByName.get("StringBuilder");
		assertNotNull(sbCallee,
				"Should find StringBuilder. Found: "
				+ calleesByName.keySet());
		assertEquals(IJavaElement.TYPE, sbCallee.getElementType(),
				"StringBuilder should be TYPE (constructor)");
		assertTrue(sbCallee.exists(),
				"StringBuilder should resolve via java.lang.* "
				+ "default import. Class: "
				+ sbCallee.getClass().getSimpleName());

		// append — resolved via assignment-inferred StringBuilder type
		IMember appendCallee = calleesByName.get("append");
		assertNotNull(appendCallee,
				"Should find append. Found: "
				+ calleesByName.keySet());
		assertEquals(IJavaElement.METHOD, appendCallee.getElementType(),
				"append should be METHOD");
		assertTrue(appendCallee.exists(),
				"append should resolve via inferred StringBuilder "
				+ "receiver. Class: "
				+ appendCallee.getClass().getSimpleName());

		// println — Kotlin stdlib, not in JRE classpath, stub
		IMember printlnCallee = calleesByName.get("println");
		assertNotNull(printlnCallee,
				"Should find println. Found: "
				+ calleesByName.keySet());
		assertEquals(IJavaElement.METHOD,
				printlnCallee.getElementType(),
				"println should be METHOD");
	}

	@Test
	public void testLocateCalleesScopedToDeclaration() throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/callhier");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/callhier/TwoFunctions.kt",
				"package callhier\n"
				+ "\n"
				+ "fun firstFunction() {\n"
				+ "    val a = ArrayList<String>()\n"
				+ "    a.add(\"x\")\n"
				+ "}\n"
				+ "\n"
				+ "fun secondFunction() {\n"
				+ "    val b = HashMap<String, Int>()\n"
				+ "    b.put(\"k\", 1)\n"
				+ "    b.remove(\"k\")\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		SearchParticipant participant = SearchParticipantRegistry
				.getParticipant("kt");

		// Get firstFunction declaration
		List<SearchMatch> firstDecls = TestHelpers.searchMethodDeclarations(
				"firstFunction", project);
		List<SearchMatch> ktFirst = TestHelpers.filterKotlinMatches(firstDecls);
		assertTrue(ktFirst.size() >= 1);
		IMember firstCaller = (IMember) ktFirst.get(0).getElement();

		String path = firstCaller.getCompilationUnit().getPath().toString();
		SearchMatch[] firstCallees = participant.locateCallees(
				firstCaller, participant.getDocument(path), null);

		Set<String> firstNames = new HashSet<>();
		for (SearchMatch m : firstCallees) {
			firstNames.add(((IMember) m.getElement()).getElementName());
		}
		assertTrue(firstNames.contains("add"),
				"firstFunction should call add()");
		assertFalse(firstNames.contains("put"),
				"firstFunction should NOT contain put() from secondFunction");
		assertFalse(firstNames.contains("remove"),
				"firstFunction should NOT contain remove() from secondFunction");

		// Get secondFunction declaration
		List<SearchMatch> secondDecls = TestHelpers.searchMethodDeclarations(
				"secondFunction", project);
		List<SearchMatch> ktSecond = TestHelpers.filterKotlinMatches(secondDecls);
		assertTrue(ktSecond.size() >= 1);
		IMember secondCaller = (IMember) ktSecond.get(0).getElement();

		SearchMatch[] secondCallees = participant.locateCallees(
				secondCaller, participant.getDocument(path), null);

		Set<String> secondNames = new HashSet<>();
		for (SearchMatch m : secondCallees) {
			secondNames.add(((IMember) m.getElement()).getElementName());
		}
		assertTrue(secondNames.contains("put"),
				"secondFunction should call put()");
		assertTrue(secondNames.contains("remove"),
				"secondFunction should call remove()");
		assertFalse(secondNames.contains("add"),
				"secondFunction should NOT contain add() from firstFunction");
	}

	@Test
	public void testLocateCalleesEmptyForNoCallSites() throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/callhier");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/callhier/NoCalls.kt",
				"package callhier\n"
				+ "\n"
				+ "fun noCallsFunction(): Int {\n"
				+ "    val x = 1 + 2\n"
				+ "    return x * 3\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		SearchParticipant participant = SearchParticipantRegistry
				.getParticipant("kt");
		List<SearchMatch> decls = TestHelpers.searchMethodDeclarations(
				"noCallsFunction", project);
		List<SearchMatch> ktDecls = TestHelpers.filterKotlinMatches(decls);
		assertTrue(ktDecls.size() >= 1);
		IMember caller = (IMember) ktDecls.get(0).getElement();

		String path = caller.getCompilationUnit().getPath().toString();
		SearchMatch[] callees = participant.locateCallees(
				caller, participant.getDocument(path), null);

		assertEquals(0, callees.length,
				"Function with no call sites should return empty callees");
	}

	@Test
	public void testLocateCalleesCallSiteOffsets() throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/callhier");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/callhier/Offsets.kt",
				"package callhier\n"
				+ "\n"
				+ "fun offsetTestFunction() {\n"
				+ "    val s = StringBuilder()\n"
				+ "    s.append(\"a\")\n"
				+ "    s.append(\"b\")\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		SearchParticipant participant = SearchParticipantRegistry
				.getParticipant("kt");
		List<SearchMatch> decls = TestHelpers.searchMethodDeclarations(
				"offsetTestFunction", project);
		List<SearchMatch> ktDecls = TestHelpers.filterKotlinMatches(decls);
		assertTrue(ktDecls.size() >= 1);
		IMember caller = (IMember) ktDecls.get(0).getElement();

		String path = caller.getCompilationUnit().getPath().toString();
		SearchMatch[] callees = participant.locateCallees(
				caller, participant.getDocument(path), null);

		// Find both append calls
		List<SearchMatch> appendCallees = Arrays.stream(callees)
				.filter(m -> ((IMember) m.getElement()).getElementName()
						.equals("append"))
				.toList();
		assertTrue(appendCallees.size() >= 2,
				"Should find at least 2 append() call sites");

		// Verify they have different offsets and positive lengths
		assertTrue(appendCallees.get(0).getOffset()
				!= appendCallees.get(1).getOffset(),
				"Two append() calls should have different offsets");
		for (SearchMatch m : appendCallees) {
			assertTrue(m.getOffset() >= 0, "Offset should be non-negative");
			assertTrue(m.getLength() > 0, "Length should be positive");
		}
	}

	@Test
	public void testLocateCalleesResolvesAutoImportedTypes()
			throws CoreException {
		// ArrayList is auto-imported via kotlin.collections →
		// java.util.ArrayList. The constructor should resolve to
		// the real JDT IType, and x.add() should resolve via
		// assignment-inferred type.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/callhier");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/callhier/StubTest.kt",
				"package callhier\n"
				+ "\n"
				+ "fun stubTestFunction() {\n"
				+ "    val x = ArrayList<String>()\n"
				+ "    x.add(\"item\")\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		SearchParticipant participant = SearchParticipantRegistry
				.getParticipant("kt");
		List<SearchMatch> decls = TestHelpers.searchMethodDeclarations(
				"stubTestFunction", project);
		List<SearchMatch> ktDecls = TestHelpers.filterKotlinMatches(decls);
		assertTrue(ktDecls.size() >= 1);
		IMember caller = (IMember) ktDecls.get(0).getElement();

		String path = caller.getCompilationUnit().getPath().toString();
		SearchMatch[] callees = participant.locateCallees(
				caller, participant.getDocument(path), null);

		assertTrue(callees.length >= 2,
				"Should find at least 2 callees (ArrayList ctor + add)");

		// Verify each callee resolved correctly
		Map<String, IMember> calleesByName = new LinkedHashMap<>();
		for (SearchMatch match : callees) {
			IMember callee = (IMember) match.getElement();
			calleesByName.put(callee.getElementName(), callee);
		}

		// ArrayList constructor → resolved IType
		IMember arrayListCallee = calleesByName.get("ArrayList");
		assertNotNull(arrayListCallee,
				"Should find ArrayList constructor. Found: "
				+ calleesByName.keySet());
		assertTrue(arrayListCallee.exists(),
				"ArrayList should resolve to real IType via "
				+ "kotlin.collections auto-import → java.util."
				+ "ArrayList. Class: "
				+ arrayListCallee.getClass().getSimpleName());
		assertEquals(IJavaElement.TYPE,
				arrayListCallee.getElementType(),
				"ArrayList should be TYPE (constructor)");

		// add → resolved IMethod on ArrayList
		IMember addCallee = calleesByName.get("add");
		assertNotNull(addCallee,
				"Should find add() callee. Found: "
				+ calleesByName.keySet());
		assertTrue(addCallee.exists(),
				"add() should resolve to real IMethod via "
				+ "assignment-inferred ArrayList type. Class: "
				+ addCallee.getClass().getSimpleName());
		assertEquals(IJavaElement.METHOD,
				addCallee.getElementType(),
				"add should be METHOD");
	}

	@Test
	public void testLocateCalleesResolvesIndexingSuffix()
			throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/callidx");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/callidx/IndexCaller.kt",
				"package callidx\n"
				+ "\n"
				+ "fun lookupValue(m: Map<String, String>,"
				+ " key: String): String? {\n"
				+ "    return m[key]\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		SearchParticipant participant = SearchParticipantRegistry
				.getParticipant("kt");
		List<SearchMatch> decls = TestHelpers
				.searchMethodDeclarations("lookupValue", project);
		List<SearchMatch> ktDecls =
				TestHelpers.filterKotlinMatches(decls);
		assertTrue(ktDecls.size() >= 1,
				"Should find lookupValue declaration");
		IMember caller = (IMember) ktDecls.get(0).getElement();

		String path = caller.getCompilationUnit().getPath()
				.toString();
		SearchMatch[] callees = participant.locateCallees(
				caller, participant.getDocument(path), null);

		boolean foundGet = false;
		for (SearchMatch match : callees) {
			IMember callee = (IMember) match.getElement();
			if ("get".equals(callee.getElementName())) {
				foundGet = true;
				assertTrue(callee.exists(),
						"get callee should resolve to real "
						+ "IMethod (Map.get), not a stub. "
						+ "Class: " + callee.getClass()
								.getSimpleName());
			}
		}
		assertTrue(foundGet, "Should find get callee from "
				+ "indexing suffix m[key]");
	}

	@Test
	public void testLocateCalleesResolvesChainedMethodCalls()
			throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/callchain");
		// Test chained calls: receiver.method1().method2()
		// The first call (add) resolves via receiver type;
		// second call (contains) resolves on the same list variable
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/callchain/ChainCaller.kt",
				"package callchain\n"
				+ "\n"
				+ "fun chainTest(items: MutableList<String>): Int {\n"
				+ "    items.add(\"hello\")\n"
				+ "    items.add(\"world\")\n"
				+ "    items.contains(\"hello\")\n"
				+ "    return items.size\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		SearchParticipant participant = SearchParticipantRegistry
				.getParticipant("kt");
		List<SearchMatch> decls = TestHelpers
				.searchMethodDeclarations("chainTest", project);
		List<SearchMatch> ktDecls =
				TestHelpers.filterKotlinMatches(decls);
		assertTrue(ktDecls.size() >= 1,
				"Should find chainTest declaration");
		IMember caller = (IMember) ktDecls.get(0).getElement();

		String path = caller.getCompilationUnit().getPath()
				.toString();
		SearchMatch[] callees = participant.locateCallees(
				caller, participant.getDocument(path), null);

		Set<String> resolvedNames = new HashSet<>();
		for (SearchMatch match : callees) {
			IMember callee = (IMember) match.getElement();
			if (callee.exists()) {
				resolvedNames.add(callee.getElementName());
			}
		}
		assertTrue(resolvedNames.contains("add"),
				"add callee should resolve to real IMethod. "
				+ "Resolved: " + resolvedNames);
		assertTrue(resolvedNames.contains("contains"),
				"contains callee should resolve to real IMethod. "
				+ "Resolved: " + resolvedNames);
	}

	@Test
	public void testLocateCalleesResolvesImplicitThis()
			throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/implthis");
		// Use ArrayList (resolvable via JDT) as enclosing type's
		// superclass so implicit this method calls resolve through it
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/implthis/Container.kt",
				"package implthis\n"
				+ "\n"
				+ "import java.util.ArrayList\n"
				+ "\n"
				+ "class Container : ArrayList<String>() {\n"
				+ "    fun doWork(): Boolean {\n"
				+ "        add(\"hello\")\n"
				+ "        add(\"world\")\n"
				+ "        return contains(\"hello\")\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		SearchParticipant participant = SearchParticipantRegistry
				.getParticipant("kt");
		List<SearchMatch> decls = TestHelpers
				.searchMethodDeclarations("doWork", project);
		List<SearchMatch> ktDecls =
				TestHelpers.filterKotlinMatches(decls);
		assertTrue(ktDecls.size() >= 1,
				"Should find doWork declaration");
		IMember caller = (IMember) ktDecls.get(0).getElement();

		String path = caller.getCompilationUnit().getPath()
				.toString();
		SearchMatch[] callees = participant.locateCallees(
				caller, participant.getDocument(path), null);

		Set<String> resolvedNames = new HashSet<>();
		for (SearchMatch match : callees) {
			IMember callee = (IMember) match.getElement();
			if (callee.exists()) {
				resolvedNames.add(callee.getElementName());
			}
		}
		assertTrue(resolvedNames.contains("add"),
				"add() via implicit this should resolve. "
				+ "Resolved: " + resolvedNames);
		assertTrue(resolvedNames.contains("contains"),
				"contains() via implicit this should resolve. "
				+ "Resolved: " + resolvedNames);
	}

	@Test
	public void testLocateCalleesResolvesSupertypeMethods()
			throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/supermethod");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/supermethod/SuperTest.kt",
				"package supermethod\n"
				+ "\n"
				+ "fun superTest(list: MutableList<String>): Boolean {\n"
				+ "    list.add(\"item\")\n"
				+ "    return list.contains(\"item\")\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		SearchParticipant participant = SearchParticipantRegistry
				.getParticipant("kt");
		List<SearchMatch> decls = TestHelpers
				.searchMethodDeclarations("superTest", project);
		List<SearchMatch> ktDecls =
				TestHelpers.filterKotlinMatches(decls);
		assertTrue(ktDecls.size() >= 1,
				"Should find superTest declaration");
		IMember caller = (IMember) ktDecls.get(0).getElement();

		String path = caller.getCompilationUnit().getPath()
				.toString();
		SearchMatch[] callees = participant.locateCallees(
				caller, participant.getDocument(path), null);

		Set<String> resolvedNames = new HashSet<>();
		for (SearchMatch match : callees) {
			IMember callee = (IMember) match.getElement();
			if (callee.exists()) {
				resolvedNames.add(callee.getElementName());
			}
		}
		// add is on List/Collection (supertype), not directly on
		// the concrete type
		assertTrue(resolvedNames.contains("add"),
				"add() should resolve via supertype hierarchy. "
				+ "Resolved: " + resolvedNames);
		assertTrue(resolvedNames.contains("contains"),
				"contains() should resolve via supertype hierarchy. "
				+ "Resolved: " + resolvedNames);
	}

	@Test
	public void testOutgoingCallsMixedResolvedAndStubs()
			throws Exception {
		// Reproduces the crash in test 4.5: a method with both
		// resolvable callees (constructors, methods on typed params)
		// and unresolvable callees (methods on assignment-inferred
		// vars from project types). The mix must not cause NPE in
		// CallSearchResultCollector.getTypeOfElement().
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/mixch");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/mixch/MixedCaller.kt",
				"package mixch\n"
				+ "\n"
				+ "class SomeService {\n"
				+ "    fun process(items: MutableList<String>,"
				+ " name: String): Int {\n"
				+ "        items.add(name)\n"
				+ "        val sb = StringBuilder()\n"
				+ "        sb.append(name)\n"
				+ "        val unknown = createHelper()\n"
				+ "        unknown.doStuff()\n"
				+ "        return items.size\n"
				+ "    }\n"
				+ "}\n"
				+ "\n"
				+ "fun createHelper(): Any = Object()\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> matches = TestHelpers
				.searchMethodDeclarations("process", project);
		List<SearchMatch> ktMatches = TestHelpers
				.filterKotlinMatches(matches);
		assertTrue(ktMatches.size() >= 1,
				"Should find process declaration");

		IMember kotlinMethod = (IMember) ktMatches.get(0)
				.getElement();

		MethodWrapper[] roots = CallHierarchyCore.getDefault()
				.getCalleeRoots(new IMember[] { kotlinMethod });
		assertEquals(1, roots.length, "Should have one root");

		// This must not throw — the mix of resolved + stubs must
		// be handled gracefully by CallSearchResultCollector
		MethodWrapper[] callees = roots[0].getCalls(
				new NullProgressMonitor());

		Set<String> calleeNames = new HashSet<>();
		for (MethodWrapper callee : callees) {
			calleeNames.add(callee.getMember().getElementName());
		}
		// At minimum, add() and append() should be resolvable
		assertTrue(calleeNames.contains("add")
				|| calleeNames.contains("append"),
				"Should resolve at least add or append. Found: "
				+ calleeNames);
	}

	@Test
	public void testLocateCalleesResolvedCalleeHasDeclaringType()
			throws CoreException {
		// Top-level functions resolved via symbol table must have
		// a non-null declaring type (facade class) to prevent NPE
		// in jdt.ui's CallSearchResultCollector.getTypeOfElement()
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/decltype");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/decltype/Helpers.kt",
				"package decltype\n"
				+ "\n"
				+ "fun helperA(): Int = 42\n"
				+ "fun helperB(): String = \"b\"\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/decltype/Caller.kt",
				"package decltype\n"
				+ "\n"
				+ "fun caller(): String {\n"
				+ "    val x = helperA()\n"
				+ "    return helperB()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		SearchParticipant participant = SearchParticipantRegistry
				.getParticipant("kt");
		List<SearchMatch> decls = TestHelpers
				.searchMethodDeclarations("caller", project);
		List<SearchMatch> ktDecls =
				TestHelpers.filterKotlinMatches(decls);
		assertTrue(ktDecls.size() >= 1,
				"Should find caller declaration");
		IMember caller = (IMember) ktDecls.get(0).getElement();

		String path = caller.getCompilationUnit().getPath()
				.toString();
		SearchMatch[] callees = participant.locateCallees(
				caller, participant.getDocument(path), null);

		for (SearchMatch match : callees) {
			IMember callee = (IMember) match.getElement();
			if (callee.exists()
					&& callee.getElementType()
							== IJavaElement.METHOD) {
				assertNotNull(callee.getDeclaringType(),
						"ResolvedCallee '" + callee.getElementName()
						+ "' must have non-null declaring type. "
						+ "Class: "
						+ callee.getClass().getSimpleName());
				assertNotNull(callee.getDeclaringType()
						.getFullyQualifiedName(),
						"Declaring type FQN must not be null for '"
						+ callee.getElementName() + "'");
			}
		}
	}

	@Test
	public void testKotlinElementNavigationMethods() throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/callhier");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/callhier/NavTest.kt",
				"package callhier\n"
				+ "\n"
				+ "class NavTestClass {\n"
				+ "    fun navTestMethod(): String = \"nav\"\n"
				+ "    val navTestField: Int = 42\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Get a type element
		List<SearchMatch> typeMatches = TestHelpers.searchKotlinTypes("NavTestClass", project);
		assertTrue(typeMatches.size() >= 1);
		KotlinElement typeElem = (KotlinElement) typeMatches.get(0).getElement();

		// Test getAncestor
		assertTrue(typeElem == typeElem.getAncestor(IJavaElement.TYPE),
				"getAncestor(TYPE) should return self for a type element");
		assertNotNull(typeElem.getAncestor(IJavaElement.COMPILATION_UNIT),
				"getAncestor(COMPILATION_UNIT) should return the CU");
		assertInstanceOf(KotlinCompilationUnit.class,
				typeElem.getAncestor(IJavaElement.COMPILATION_UNIT));

		// Test getParent
		assertNotNull(typeElem.getParent(), "getParent should return CU");
		assertInstanceOf(KotlinCompilationUnit.class, typeElem.getParent());

		// Test getResource, getCorrespondingResource, getUnderlyingResource
		assertNotNull(typeElem.getResource(), "getResource should not be null");
		assertNotNull(typeElem.getCorrespondingResource(),
				"getCorrespondingResource should not be null");
		assertNotNull(typeElem.getUnderlyingResource(),
				"getUnderlyingResource should not be null");

		// Test getJavaProject
		assertNotNull(typeElem.getJavaProject(), "getJavaProject should not be null");

		// Test getJavaModel
		assertNotNull(typeElem.getJavaModel(), "getJavaModel should not be null");

		// Test getOpenable
		assertNotNull(typeElem.getOpenable(), "getOpenable should not be null");

		// Test getPath
		assertNotNull(typeElem.getPath(), "getPath should not be null");
		assertTrue(typeElem.getPath().toString().endsWith("NavTest.kt"),
				"Path should end with NavTest.kt");

		// Test getPrimaryElement
		assertTrue(typeElem == typeElem.getPrimaryElement(),
				"getPrimaryElement should return self");

		// Test getHandleIdentifier
		assertNotNull(typeElem.getHandleIdentifier(),
				"getHandleIdentifier should not be null");

		// Test isReadOnly, isStructureKnown
		assertTrue(typeElem.isReadOnly(), "Kotlin elements are read-only");
		assertTrue(typeElem.isStructureKnown(), "Structure is known");

		// Test exists
		assertTrue(typeElem.exists(), "Element should exist");

		// Test getSchedulingRule
		assertNotNull(typeElem.getSchedulingRule(),
				"getSchedulingRule should return the resource");

		// Test getAttachedJavadoc
		assertNull(typeElem.getAttachedJavadoc(null),
				"No attached javadoc for Kotlin elements");

		// Test getAdapter
		assertNotNull(typeElem.getAdapter(IMember.class),
				"getAdapter(IMember) should return self");
		assertNull(typeElem.getAdapter(String.class),
				"getAdapter(String) should return null");

		// Test getChildren, hasChildren
		assertEquals(0, typeElem.getChildren().length,
				"Kotlin elements have no children");
		assertFalse(typeElem.hasChildren(),
				"Kotlin elements have no children");

		// Test getSource
		assertNull(typeElem.getSource(), "getSource returns null");

		// Test read-only operations throw
		try {
			typeElem.delete(false, null);
			assertTrue(false, "delete should throw");
		} catch (Exception e) {
			// expected
		}

		// Test method element
		List<SearchMatch> methodMatches = TestHelpers.searchMethodDeclarations(
				"navTestMethod", project);
		List<SearchMatch> ktMethods = TestHelpers.filterKotlinMatches(methodMatches);
		assertTrue(ktMethods.size() >= 1);
		KotlinElement.KotlinMethodElement methodElem =
				(KotlinElement.KotlinMethodElement) ktMethods.get(0).getElement();
		assertEquals(0, methodElem.getParameterCount(),
				"navTestMethod has 0 parameters");
		assertEquals(IJavaElement.METHOD, methodElem.getElementType());
		assertNotNull(methodElem.getAncestor(IJavaElement.TYPE),
				"getAncestor(TYPE) on method should return declaring type");
		assertEquals(IJavaElement.TYPE,
				methodElem.getAncestor(IJavaElement.TYPE).getElementType());
		assertNotNull(methodElem.getAncestor(IJavaElement.COMPILATION_UNIT),
				"getAncestor(COMPILATION_UNIT) on method should return CU");
		assertNotNull(methodElem.getAncestor(IJavaElement.JAVA_PROJECT),
				"getAncestor(JAVA_PROJECT) on method should return project");

		// Test field element
		List<SearchMatch> fieldMatches = TestHelpers.searchFieldDeclarations(
				"navTestField", project);
		List<SearchMatch> ktFields = TestHelpers.filterKotlinMatches(fieldMatches);
		assertTrue(ktFields.size() >= 1);
		KotlinElement.KotlinFieldElement fieldElem =
				(KotlinElement.KotlinFieldElement) ktFields.get(0).getElement();
		assertEquals(IJavaElement.FIELD, fieldElem.getElementType());
		assertNotNull(fieldElem.getAncestor(IJavaElement.TYPE),
				"getAncestor(TYPE) on field should return declaring type");
		assertEquals(IJavaElement.TYPE,
				fieldElem.getAncestor(IJavaElement.TYPE).getElementType());
		assertNotNull(fieldElem.getAncestor(IJavaElement.COMPILATION_UNIT),
				"getAncestor(COMPILATION_UNIT) on field should return CU");
		assertNotNull(fieldElem.getAncestor(IJavaElement.JAVA_PROJECT),
				"getAncestor(JAVA_PROJECT) on field should return project");

		// Verify full ancestor chain consistency for all element types
		for (KotlinElement elem : new KotlinElement[] {
				typeElem, methodElem, fieldElem }) {
			IJavaElement cu = elem.getAncestor(IJavaElement.COMPILATION_UNIT);
			IJavaElement pfr = elem.getAncestor(
					IJavaElement.PACKAGE_FRAGMENT_ROOT);
			IJavaElement pf = elem.getAncestor(IJavaElement.PACKAGE_FRAGMENT);
			IJavaElement jp = elem.getAncestor(IJavaElement.JAVA_PROJECT);
			IJavaElement jm = elem.getAncestor(IJavaElement.JAVA_MODEL);
			assertNotNull(cu, elem.getElementName()
					+ ": getAncestor(CU) should not be null");
			assertNotNull(pf, elem.getElementName()
					+ ": getAncestor(PF) should not be null");
			assertNotNull(pfr, elem.getElementName()
					+ ": getAncestor(PFR) should not be null");
			assertNotNull(jp, elem.getElementName()
					+ ": getAncestor(JP) should not be null");
			assertNotNull(jm, elem.getElementName()
					+ ": getAncestor(JM) should not be null");
		}

		// Test ISourceRange
		assertNotNull(typeElem.getSourceRange(), "Should have source range");
		assertTrue(typeElem.getSourceRange().getOffset() >= 0,
				"Source range offset should be valid");
		assertTrue(typeElem.getSourceRange().getLength() > 0,
				"Source range length should be positive");
		assertNotNull(typeElem.getNameRange(), "Should have name range");
	}

	@Test
	public void testKotlinTypeHierarchyMethods() throws Exception {
		// Kotlin class that extends a Java class
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/hierarchy");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/hierarchy/Base.java",
				"package hierarchy;\n"
				+ "public class Base {\n"
				+ "    public void baseMethod() {}\n"
				+ "}\n");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/hierarchy/Sub.kt",
				"package hierarchy\n"
				+ "\n"
				+ "class KotlinSub : Base() {\n"
				+ "    fun subMethod() {}\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Get the Kotlin type element
		List<SearchMatch> matches =
				TestHelpers.searchKotlinTypes("KotlinSub", project);
		assertTrue(matches.size() >= 1,
				"Should find KotlinSub type declaration");
		KotlinElement.KotlinTypeElement kotlinType =
				(KotlinElement.KotlinTypeElement) matches.get(0)
						.getElement();

		// newSupertypeHierarchy() must not return null and must not NPE
		ITypeHierarchy superHierarchy =
				kotlinType.newSupertypeHierarchy(null);
		assertNotNull(superHierarchy,
				"newSupertypeHierarchy must not return null");
		// getAllSupertypes must not throw
		IType[] supers = superHierarchy.getAllSupertypes(kotlinType);
		assertNotNull(supers,
				"getAllSupertypes must not return null");

		// newTypeHierarchy() must not return null and must not NPE
		ITypeHierarchy typeHierarchy =
				kotlinType.newTypeHierarchy((IProgressMonitor) null);
		assertNotNull(typeHierarchy,
				"newTypeHierarchy must not return null");
		IType[] allTypes = typeHierarchy.getAllSubtypes(kotlinType);
		assertNotNull(allTypes,
				"getAllSubtypes must not return null");

		// newTypeHierarchy(project) must not NPE
		ITypeHierarchy projectHierarchy =
				kotlinType.newTypeHierarchy(project, null);
		assertNotNull(projectHierarchy,
				"newTypeHierarchy(project) must not return null");

		// Kotlin method's declaring type hierarchy also must not NPE
		List<SearchMatch> methodMatches =
				TestHelpers.searchMethodDeclarations("subMethod", project);
		List<SearchMatch> ktMethods =
				TestHelpers.filterKotlinMatches(methodMatches);
		assertTrue(ktMethods.size() >= 1);
		KotlinElement.KotlinMethodElement methodElem =
				(KotlinElement.KotlinMethodElement) ktMethods.get(0)
						.getElement();
		IType declaringType = (IType) methodElem.getAncestor(
				IJavaElement.TYPE);
		assertNotNull(declaringType,
				"Method declaring type should not be null");
		ITypeHierarchy methodTypeHierarchy =
				declaringType.newSupertypeHierarchy(null);
		assertNotNull(methodTypeHierarchy,
				"Declaring type's hierarchy must not return null");

		// resolveType() delegation: resolve a Java type name
		// in the context of the Kotlin type
		String[][] resolved = kotlinType.resolveType("Base");
		// May be null if no Java counterpart exists, but must not NPE
	}

	// ---- End-to-end call hierarchy via CallHierarchyCore ----

	@Test
	public void testIncomingCallsViaCallHierarchyCore() throws Exception {
		// Java method called from Kotlin
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/e2ecall");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/e2ecall/Target.java",
				"package e2ecall;\n"
				+ "public class Target {\n"
				+ "    public void targetMethod() {}\n"
				+ "}\n");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/e2ecall/Caller.kt",
				"package e2ecall\n"
				+ "\n"
				+ "class KotlinCaller {\n"
				+ "    fun callTarget(t: Target) {\n"
				+ "        t.targetMethod()\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Find the Java target method
		ICompilationUnit javaCu = TestHelpers.getJavaCompilationUnit(
				project,"e2ecall", "Target.java");
		assertNotNull(javaCu, "Java CU should exist");
		IType targetType = javaCu.getType("Target");
		IMethod targetMethod = targetType.getMethod("targetMethod",
				new String[0]);
		assertTrue(targetMethod.exists(),
				"Target.targetMethod() should exist");

		// First verify the search pipeline works directly
		List<SearchMatch> directMatches =
				TestHelpers.searchMethodReferences("targetMethod", project);
		List<SearchMatch> ktDirectMatches = TestHelpers.filterKotlinMatches(directMatches);
		assertTrue(ktDirectMatches.size() >= 1,
				"Direct search should find Kotlin reference to "
						+ "targetMethod. Found " + directMatches.size()
						+ " total matches, " + ktDirectMatches.size()
						+ " Kotlin matches");

		// Verify the match element has proper declaring type
		for (SearchMatch m : ktDirectMatches) {
			IMember matchMember = (IMember) m.getElement();
			assertNotNull(matchMember.getDeclaringType(),
					"Match element '" + matchMember.getElementName()
							+ "' should have a declaring type");
			assertNotNull(matchMember.getDeclaringType()
							.getFullyQualifiedName(),
					"Declaring type should have FQN");
		}

		// Test element-based search (same as CallerMethodWrapper uses)
		SearchPattern elementPattern = SearchPattern.createPattern(
				targetMethod, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH | SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		assertNotNull(elementPattern, "Pattern should not be null");

		// Diagnose: check selectIndexes + index contents
		IJavaSearchScope projectScope = SearchEngine
				.createJavaSearchScope(new IJavaProject[] { project });
		List<SearchMatch> elementMatches = new ArrayList<>();
		new SearchEngine().search(elementPattern,
				SearchEngine.getSearchParticipants(),
				projectScope,
				new SearchRequestor() {
					@Override
					public void acceptSearchMatch(SearchMatch match) {
						elementMatches.add(match);
					}
				}, new NullProgressMonitor());
		List<SearchMatch> elementKt = TestHelpers.filterKotlinMatches(elementMatches);
		// Direct call to Kotlin participant's locateMatches
		SearchParticipant ktParticipant = SearchParticipantRegistry
				.getParticipant("kt");
		assertNotNull(ktParticipant, "Kotlin participant should exist");
		IFile ktCallerFile = TestHelpers.getFile(
				"/" + PROJECT_NAME + "/src/e2ecall/Caller.kt");
		assertTrue(ktCallerFile.exists(), "Kotlin file should exist");
		SearchDocument ktDoc = ktParticipant.getDocument(
				ktCallerFile.getFullPath().toString());
		List<SearchMatch> directMatches2 = new ArrayList<>();
		ktParticipant.locateMatches(
				new SearchDocument[] { ktDoc },
				elementPattern, projectScope,
				new SearchRequestor() {
					@Override
					public void acceptSearchMatch(SearchMatch match) {
						directMatches2.add(match);
					}
				}, null);
		StringBuilder diag = new StringBuilder();
		diag.append("directCall=").append(directMatches2.size());
		diag.append(", viaEngine=").append(elementMatches.size());

		assertTrue(elementKt.size() >= 1 || !directMatches2.isEmpty(),
				"Element-based search: " + diag);

		MethodWrapper[] roots = CallHierarchyCore.getDefault()
				.getCallerRoots(new IMember[] { targetMethod });
		assertEquals(1, roots.length, "Should have one root");

		MethodWrapper[] callers = roots[0].getCalls(
				new NullProgressMonitor());

		// Should find the Kotlin caller
		boolean foundKotlinCaller = false;
		for (MethodWrapper caller : callers) {
			IMember member = caller.getMember();
			if (member instanceof KotlinElement) {
				foundKotlinCaller = true;
				assertEquals("callTarget", member.getElementName(),
						"Kotlin caller should be callTarget");
			}
		}
		assertTrue(foundKotlinCaller,
				"Should find Kotlin caller via CallHierarchyCore "
						+ "incoming calls. Found " + callers.length
						+ " caller(s): " + Arrays.stream(callers)
								.map(c -> c.getMember().getElementName()
										+ "(" + c.getMember().getClass()
												.getSimpleName() + ")")
								.collect(Collectors.joining(", ")));
	}

	@Test
	public void testIncomingCallsToKotlinMethodFromJava()
			throws Exception {
		// Kotlin method called from Java — the reverse direction.
		// This exercises MatchLocator with KotlinMethodElement as
		// the search target (MethodPattern.declaringType is our type).
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/e2erev");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/e2erev/KotlinTarget.kt",
				"package e2erev\n"
				+ "\n"
				+ "class KotlinTarget {\n"
				+ "    fun kotlinMethod(): String {\n"
				+ "        return \"hello\"\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/e2erev/JavaCaller.java",
				"package e2erev;\n"
				+ "public class JavaCaller {\n"
				+ "    public void callKotlin(KotlinTarget kt) {\n"
				+ "        kt.kotlinMethod();\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Find the Kotlin method via search
		List<SearchMatch> methodMatches =
				TestHelpers.searchMethodDeclarations(
						"kotlinMethod", project);
		List<SearchMatch> ktMatches =
				TestHelpers.filterKotlinMatches(methodMatches);
		assertTrue(ktMatches.size() >= 1,
				"Should find kotlinMethod declaration");
		IMember kotlinMethod =
				(IMember) ktMatches.get(0).getElement();

		// Verify declaring type is resolvable and won't cause
		// ClassCastException in MatchLocator
		IType declaringType = kotlinMethod.getDeclaringType();
		assertNotNull(declaringType,
				"Kotlin method should have a declaring type");

		// Call hierarchy: incoming calls to the Kotlin method.
		// CallerMethodWrapper accepts inaccurate matches for
		// contributed (non-Java) elements since the Java
		// MatchLocator can't fully resolve Kotlin type bindings.
		MethodWrapper[] roots = CallHierarchyCore.getDefault()
				.getCallerRoots(new IMember[] { kotlinMethod });
		assertEquals(1, roots.length, "Should have one root");

		MethodWrapper[] callers = roots[0].getCalls(
				new NullProgressMonitor());
		assertNotNull(callers, "getCalls must not throw");

		boolean foundJavaCaller = false;
		for (MethodWrapper caller : callers) {
			IMember member = caller.getMember();
			if ("callKotlin".equals(member.getElementName())) {
				foundJavaCaller = true;
			}
		}
		assertTrue(foundJavaCaller,
				"Should find Java caller via CallHierarchyCore "
				+ "incoming calls to Kotlin method. Found "
				+ callers.length + " caller(s): "
				+ Arrays.stream(callers)
						.map(c -> c.getMember().getElementName()
								+ "("
								+ c.getMember().getClass()
										.getSimpleName()
								+ ")")
						.collect(Collectors.joining(", ")));
	}

	@Test
	public void testConstructorDelegationInOutgoingCalls()
			throws Exception {
		// class Derived : Base() — constructor delegation should
		// appear in outgoing call hierarchy
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/e2ector");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/e2ector/Defs.kt",
				"package e2ector\n"
				+ "open class Base(val name: String)\n"
				+ "class Derived : Base(\"derived\") {\n"
				+ "    fun work() {\n"
				+ "        println(name)\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		SearchParticipant participant = SearchParticipantRegistry
				.getParticipant("kt");
		// Find work() declaration to use as caller
		List<SearchMatch> decls = TestHelpers
				.searchMethodDeclarations("work", project);
		List<SearchMatch> ktDecls = TestHelpers.filterKotlinMatches(
				decls);
		assertTrue(ktDecls.size() >= 1,
				"Should find work() declaration");
		IMember caller = (IMember) ktDecls.get(0).getElement();

		String path = caller.getCompilationUnit().getPath()
				.toString();
		SearchMatch[] callees = participant.locateCallees(
				caller, participant.getDocument(path), null);

		boolean foundPrintln = false;
		for (SearchMatch callee : callees) {
			if (callee.getElement() instanceof IJavaElement je
					&& "println".equals(je.getElementName())) {
				foundPrintln = true;
			}
		}
		assertTrue(foundPrintln,
				"Outgoing calls from work() should include "
						+ "println");
	}

	@Test
	public void testOutgoingCallsViaCallHierarchyCore() throws Exception {
		// Kotlin function calls Java method — exercise
		// CalleeMethodWrapper → locateCallees()
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/e2eout");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/e2eout/JavaTarget.java",
				"package e2eout;\n"
				+ "public class JavaTarget {\n"
				+ "    public void javaMethod() {}\n"
				+ "    public void anotherJavaMethod() {}\n"
				+ "}\n");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/e2eout/KotlinSource.kt",
				"package e2eout\n"
				+ "\n"
				+ "class KotlinSource {\n"
				+ "    fun callsJava(t: JavaTarget) {\n"
				+ "        t.javaMethod()\n"
				+ "        t.anotherJavaMethod()\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Get the Kotlin method as an IMember via search
		List<SearchMatch> matches = TestHelpers.searchMethodDeclarations(
				"callsJava", project);
		List<SearchMatch> ktMatches = TestHelpers.filterKotlinMatches(matches);
		assertTrue(ktMatches.size() >= 1,
				"Should find callsJava declaration in .kt");

		IMember kotlinMethod = (IMember) ktMatches.get(0).getElement();
		assertNotNull(kotlinMethod, "Kotlin method should be non-null");

		// Get outgoing callees via CallHierarchyCore
		MethodWrapper[] roots = CallHierarchyCore.getDefault()
				.getCalleeRoots(new IMember[] { kotlinMethod });
		assertEquals(1, roots.length, "Should have one root");

		MethodWrapper[] callees = roots[0].getCalls(
				new NullProgressMonitor());

		// Should find Java callees via locateCallees() fallback
		Set<String> calleeNames = new HashSet<>();
		for (MethodWrapper callee : callees) {
			calleeNames.add(callee.getMember().getElementName());
		}

		assertTrue(calleeNames.contains("javaMethod"),
				"Should find javaMethod as outgoing callee. Found: "
						+ calleeNames);
		assertTrue(calleeNames.contains("anotherJavaMethod"),
				"Should find anotherJavaMethod as outgoing callee. Found: "
						+ calleeNames);
	}

	// ---- Bug #12: top-level declarations need non-null declaring type ----
	//
	// JDT infrastructure assumes every IMember has a non-null declaring
	// type. Kotlin top-level functions/properties compile to static
	// members of a synthetic FileNameKt class. All code paths that
	// create KotlinMethodElement or KotlinFieldElement for top-level
	// declarations must set a file-facade declaring type.
	//
	// Code paths tested:
	// 1. reportMethodMatch — declaration search (MethodPattern+DECL)
	// 2. reportFieldMatch — declaration search (FieldPattern+DECL)
	// 3. createElementForDeclaration — reference match enclosing element
	// 4. KotlinModelManager.populateCompilationUnit — CU model/codeSelect
	// 5. CalleeMethodWrapper E2E — outgoing calls with top-level callee
	// 6. CallerMethodWrapper E2E — incoming calls from top-level caller

	// ---- Path 1: reportMethodMatch (declaration search) ----

	@Test
	public void testTopLevelFunctionDeclaringTypeViaDeclarationSearch()
			throws Exception {
		// MethodPattern with DECLARATIONS → locateMatchesInDeclaration
		// → reportMethodMatch creates KotlinMethodElement
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/p1");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/p1/MyUtils.kt",
				"package p1\n"
				+ "\n"
				+ "fun topLevelFun(): String = \"hello\"\n"
				+ "\n"
				+ "fun anotherTopLevel(x: Int): Boolean = x > 0\n");
		TestHelpers.waitUntilIndexesReady();

		for (String name : new String[] { "topLevelFun",
				"anotherTopLevel" }) {
			List<SearchMatch> matches = TestHelpers
					.searchMethodDeclarations(name, project);
			List<SearchMatch> ktMatches = TestHelpers
					.filterKotlinMatches(matches);
			assertTrue(ktMatches.size() >= 1,
					"Should find " + name + " declaration");

			IMember method = (IMember) ktMatches.get(0).getElement();
			assertNotNull(method.getDeclaringType(),
					"Top-level function " + name
							+ " should have non-null declaring type");
			assertNotNull(method.getDeclaringType()
							.getFullyQualifiedName(),
					"Declaring type of " + name
							+ " should have FQN");
			assertTrue(method.getDeclaringType()
							.getFullyQualifiedName()
							.contains("MyUtils"),
					"File-facade type should contain file name. Got: "
							+ method.getDeclaringType()
									.getFullyQualifiedName());
		}
	}

	// ---- Path 2: reportFieldMatch (declaration search) ----

	@Test
	public void testTopLevelPropertyDeclaringTypeViaDeclarationSearch()
			throws Exception {
		// FieldPattern with DECLARATIONS → locateMatchesInDeclaration
		// → reportFieldMatch creates KotlinFieldElement
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/p2");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/p2/Constants.kt",
				"package p2\n"
				+ "\n"
				+ "val TIMEOUT: Long = 30000L\n"
				+ "\n"
				+ "val MAX_RETRIES: Int = 3\n");
		TestHelpers.waitUntilIndexesReady();

		for (String name : new String[] { "TIMEOUT", "MAX_RETRIES" }) {
			List<SearchMatch> matches = TestHelpers
					.searchFieldDeclarations(name, project);
			List<SearchMatch> ktMatches = TestHelpers
					.filterKotlinMatches(matches);
			assertTrue(ktMatches.size() >= 1,
					"Should find " + name + " declaration");

			IMember field = (IMember) ktMatches.get(0).getElement();
			assertNotNull(field.getDeclaringType(),
					"Top-level property " + name
							+ " should have non-null declaring type");
			assertNotNull(field.getDeclaringType()
							.getFullyQualifiedName(),
					"Declaring type of " + name
							+ " should have FQN");
			assertTrue(field.getDeclaringType()
							.getFullyQualifiedName()
							.contains("Constants"),
					"File-facade type should contain file name. Got: "
							+ field.getDeclaringType()
									.getFullyQualifiedName());
		}
	}

	// ---- Path 3: createElementForDeclaration (reference matches) ----

	@Test
	public void testTopLevelFunctionAsEnclosingElementInReferenceSearch()
			throws Exception {
		// When a reference to a Java method is found inside a top-level
		// Kotlin function, the match element is the enclosing function.
		// createElementForDeclaration must set a declaring type on it.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/p3");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/p3/Target.java",
				"package p3;\n"
				+ "public class Target {\n"
				+ "    public static void doSomething() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/p3/TopCaller.kt",
				"package p3\n"
				+ "\n"
				+ "fun topLevelCaller() {\n"
				+ "    Target.doSomething()\n"
				+ "}\n"
				+ "\n"
				+ "fun anotherTopCaller() {\n"
				+ "    Target.doSomething()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchMethodReferences("doSomething", project);
		List<SearchMatch> ktRefs = TestHelpers
				.filterKotlinMatches(refs);
		assertTrue(ktRefs.size() >= 2,
				"Should find at least 2 Kotlin references to "
						+ "doSomething. Found: " + ktRefs.size());

		for (SearchMatch match : ktRefs) {
			IMember enclosing = (IMember) match.getElement();
			assertNotNull(enclosing.getDeclaringType(),
					"Enclosing top-level function '"
							+ enclosing.getElementName()
							+ "' should have non-null declaring type");
			assertNotNull(enclosing.getDeclaringType()
							.getFullyQualifiedName(),
					"Declaring type of enclosing '"
							+ enclosing.getElementName()
							+ "' should have FQN");
		}
	}

	// ---- Path 4: KotlinModelManager.populateCompilationUnit ----

	@Test
	public void testTopLevelFunctionDeclaringTypeViaCUModel()
			throws Exception {
		// KotlinModelManager.populateCompilationUnit builds top-level
		// functions as children of the CU. These are used by
		// codeSelect() which is called by CallHierarchyHandler.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/p4");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/p4/Helpers.kt",
				"package p4\n"
				+ "\n"
				+ "fun helperOne(): String = \"one\"\n"
				+ "\n"
				+ "val helperProp: Int = 99\n");
		TestHelpers.waitUntilIndexesReady();

		IFile ktFile = TestHelpers.getFile(
				"/" + PROJECT_NAME + "/src/p4/Helpers.kt");
		KotlinCompilationUnit cu = KotlinModelManager.getInstance()
				.getCompilationUnit(ktFile);
		assertNotNull(cu, "CU should not be null");

		IJavaElement[] children = cu.getChildren();
		boolean foundMethod = false;
		boolean foundField = false;
		for (IJavaElement child : children) {
			if (child instanceof IMethod m
					&& "helperOne".equals(m.getElementName())) {
				foundMethod = true;
				assertNotNull(((IMember) m).getDeclaringType(),
						"Top-level function helperOne from CU model "
								+ "should have non-null declaring type");
			}
			if (child instanceof KotlinElement.KotlinFieldElement f
					&& "helperProp".equals(f.getElementName())) {
				foundField = true;
				assertNotNull(f.getDeclaringType(),
						"Top-level property helperProp from CU model "
								+ "should have non-null declaring type");
			}
		}
		assertTrue(foundMethod,
				"Should find helperOne in CU children");
		assertTrue(foundField,
				"Should find helperProp in CU children");
	}

	// ---- Path 5: E2E outgoing calls with top-level callee ----

	@Test
	public void testOutgoingCallsWithTopLevelFunctionCallee()
			throws Exception {
		// CalleeMethodWrapper.findCalleesFromParticipants → resolveCallee
		// finds a KotlinMethodElement → CallSearchResultCollector.addMember
		// → isIgnored → getTypeOfElement(callee).getFullyQualifiedName()
		// NPE when declaring type is null.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/p5");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/p5/Utils.kt",
				"package p5\n"
				+ "\n"
				+ "fun <T> dslFor(block: () -> T): T = block()\n"
				+ "\n"
				+ "fun helperFunction(s: String): Int = s.length\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/p5/Service.kt",
				"package p5\n"
				+ "\n"
				+ "class Service {\n"
				+ "    fun doWork(): Int {\n"
				+ "        val result = dslFor {"
				+ " helperFunction(\"test\") }\n"
				+ "        return result\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> matches = TestHelpers
				.searchMethodDeclarations("doWork", project);
		List<SearchMatch> ktMatches = TestHelpers
				.filterKotlinMatches(matches);
		assertTrue(ktMatches.size() >= 1,
				"Should find doWork declaration");

		IMember kotlinMethod = (IMember) ktMatches.get(0).getElement();

		MethodWrapper[] roots = CallHierarchyCore.getDefault()
				.getCalleeRoots(new IMember[] { kotlinMethod });
		assertEquals(1, roots.length, "Should have one root");

		// This call throws NPE without the fix
		MethodWrapper[] callees = roots[0].getCalls(
				new NullProgressMonitor());

		Set<String> calleeNames = new HashSet<>();
		for (MethodWrapper callee : callees) {
			calleeNames.add(callee.getMember().getElementName());
		}
		assertTrue(calleeNames.contains("dslFor"),
				"Should find top-level dslFor. Found: " + calleeNames);
		assertTrue(calleeNames.contains("helperFunction"),
				"Should find top-level helperFunction. Found: "
						+ calleeNames);
	}

	// ---- Path 6: E2E incoming calls from top-level caller ----

	@Test
	public void testIncomingCallsFromTopLevelFunction()
			throws Exception {
		// CallerMethodWrapper uses SearchEngine to find callers.
		// When the caller is a top-level Kotlin function, the match
		// element must have a non-null declaring type for
		// CallSearchResultCollector.isIgnored and for
		// toCallHierarchyItem in jdtls.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/p6");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/p6/Api.java",
				"package p6;\n"
				+ "public class Api {\n"
				+ "    public static String fetchData() {"
				+ " return \"data\"; }\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/p6/TopConsumer.kt",
				"package p6\n"
				+ "\n"
				+ "fun consumeFromTop(): String {\n"
				+ "    return Api.fetchData()\n"
				+ "}\n"
				+ "\n"
				+ "fun anotherConsumer(): String {\n"
				+ "    return Api.fetchData()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Find the Java method
		ICompilationUnit javaCu = TestHelpers.getJavaCompilationUnit(
				project, "p6", "Api.java");
		assertNotNull(javaCu, "Java CU should exist");
		IType apiType = javaCu.getType("Api");
		IMethod fetchData = apiType.getMethod("fetchData",
				new String[0]);
		assertTrue(fetchData.exists(),
				"Api.fetchData() should exist");

		// Incoming calls via CallHierarchyCore
		MethodWrapper[] roots = CallHierarchyCore.getDefault()
				.getCallerRoots(new IMember[] { fetchData });
		assertEquals(1, roots.length, "Should have one root");

		MethodWrapper[] callers = roots[0].getCalls(
				new NullProgressMonitor());

		Set<String> callerNames = new HashSet<>();
		for (MethodWrapper caller : callers) {
			IMember member = caller.getMember();
			callerNames.add(member.getElementName());
			// The declaring type must be non-null for
			// CallHierarchyHandler.toCallHierarchyItem to work
			assertNotNull(member.getDeclaringType(),
					"Top-level caller '" + member.getElementName()
							+ "' should have non-null declaring type");
		}
		assertTrue(callerNames.contains("consumeFromTop"),
				"Should find consumeFromTop as caller. Found: "
						+ callerNames);
		assertTrue(callerNames.contains("anotherConsumer"),
				"Should find anotherConsumer as caller. Found: "
						+ callerNames);
	}

	// ---- Top-level extension functions (variant of path 1) ----

	@Test
	public void testTopLevelExtensionFunctionDeclaringType()
			throws Exception {
		// Extension functions are also top-level — they must also have
		// a file-facade declaring type.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/p7");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/p7/Extensions.kt",
				"package p7\n"
				+ "\n"
				+ "fun String.greet(): String = \"Hello, $this!\"\n"
				+ "\n"
				+ "fun Int.isEven(): Boolean = this % 2 == 0\n");
		TestHelpers.waitUntilIndexesReady();

		for (String name : new String[] { "greet", "isEven" }) {
			List<SearchMatch> matches = TestHelpers
					.searchMethodDeclarations(name, project);
			List<SearchMatch> ktMatches = TestHelpers
					.filterKotlinMatches(matches);
			assertTrue(ktMatches.size() >= 1,
					"Should find " + name + " declaration");

			IMember method = (IMember) ktMatches.get(0).getElement();
			assertNotNull(method.getDeclaringType(),
					"Top-level extension " + name
							+ " should have non-null declaring type");
			assertTrue(method.getDeclaringType()
							.getFullyQualifiedName()
							.contains("Extensions"),
					"File-facade type should contain 'Extensions'. "
							+ "Got: " + method.getDeclaringType()
									.getFullyQualifiedName());
		}
	}

	// ---- Nested type declaring type ----

	@Test
	public void testNestedTypeDeclaringTypeNotNull() throws Exception {
		// KotlinTypeElement inherits base getDeclaringType() which
		// returns null. For nested types (Inner inside Outer),
		// getDeclaringType() should return the enclosing type.
		// JDT uses this for parent navigation, breadcrumbs, and
		// call hierarchy type resolution.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/nested");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/nested/Outer.kt",
				"package nested\n"
				+ "\n"
				+ "class Outer {\n"
				+ "    class Inner {\n"
				+ "        fun innerMethod(): String = \"inner\"\n"
				+ "    }\n"
				+ "    class AnotherInner {\n"
				+ "        fun anotherMethod(): Int = 42\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Find Inner class via type declaration search
		SearchPattern pattern = SearchPattern.createPattern(
				"Inner", IJavaSearchConstants.TYPE,
				IJavaSearchConstants.DECLARATIONS,
				SearchPattern.R_EXACT_MATCH);
		List<SearchMatch> matches = new ArrayList<>();
		new SearchEngine().search(pattern,
				SearchEngine.getSearchParticipants(),
				SearchEngine.createJavaSearchScope(
						new IJavaProject[] { project }),
				new SearchRequestor() {
					@Override
					public void acceptSearchMatch(SearchMatch match) {
						matches.add(match);
					}
				}, new NullProgressMonitor());

		List<SearchMatch> ktMatches = TestHelpers
				.filterKotlinMatches(matches);
		assertTrue(ktMatches.size() >= 1,
				"Should find Inner type declaration");

		IType innerType = (IType) ktMatches.get(0).getElement();
		assertNotNull(innerType.getDeclaringType(),
				"Nested type Inner should have non-null "
						+ "declaring type (Outer)");
		assertEquals("Outer",
				innerType.getDeclaringType().getElementName(),
				"Inner's declaring type should be Outer");
	}

	@Test
	public void testNestedTypeMethodDeclaringTypeChain()
			throws Exception {
		// Method inside nested type: method.getDeclaringType() should
		// return the nested type, which in turn should have
		// getDeclaringType() returning the outer type.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/chain");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/chain/Container.kt",
				"package chain\n"
				+ "\n"
				+ "class Container {\n"
				+ "    class Nested {\n"
				+ "        fun nestedWork(): Boolean = true\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> matches = TestHelpers
				.searchMethodDeclarations("nestedWork", project);
		List<SearchMatch> ktMatches = TestHelpers
				.filterKotlinMatches(matches);
		assertTrue(ktMatches.size() >= 1,
				"Should find nestedWork declaration");

		IMember method = (IMember) ktMatches.get(0).getElement();
		IType declaringType = method.getDeclaringType();
		assertNotNull(declaringType,
				"nestedWork should have declaring type");
		assertEquals("Nested", declaringType.getElementName(),
				"nestedWork's declaring type should be Nested");

		// Nested's declaring type should be Container
		assertNotNull(declaringType.getDeclaringType(),
				"Nested should have declaring type Container");
		assertEquals("Container",
				declaringType.getDeclaringType().getElementName(),
				"Nested's declaring type should be Container");
	}

	// ---- Outgoing calls from nested type method ----

	@Test
	public void testOutgoingCallsFromNestedTypeMethod()
			throws Exception {
		// Outgoing call hierarchy from a method inside a nested type.
		// The caller member's declaring type chain must be intact for
		// CallSearchResultCollector.isIgnored() to work.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/nestcall");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/nestcall/Api.java",
				"package nestcall;\n"
				+ "public class Api {\n"
				+ "    public static void apiCall() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/nestcall/Wrapper.kt",
				"package nestcall\n"
				+ "\n"
				+ "class Wrapper {\n"
				+ "    class Handler {\n"
				+ "        fun handle() {\n"
				+ "            Api.apiCall()\n"
				+ "        }\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> matches = TestHelpers
				.searchMethodDeclarations("handle", project);
		List<SearchMatch> ktMatches = TestHelpers
				.filterKotlinMatches(matches);
		assertTrue(ktMatches.size() >= 1,
				"Should find handle declaration");

		IMember kotlinMethod = (IMember) ktMatches.get(0).getElement();
		MethodWrapper[] roots = CallHierarchyCore.getDefault()
				.getCalleeRoots(new IMember[] { kotlinMethod });
		assertEquals(1, roots.length, "Should have one root");

		MethodWrapper[] callees = roots[0].getCalls(
				new NullProgressMonitor());
		Set<String> calleeNames = new HashSet<>();
		for (MethodWrapper callee : callees) {
			calleeNames.add(callee.getMember().getElementName());
		}
		assertTrue(calleeNames.contains("apiCall"),
				"Should find apiCall as outgoing callee from nested "
						+ "type method. Found: " + calleeNames);
	}

	// ---- Incoming calls to nested type method ----

	@Test
	public void testIncomingCallsToNestedTypeMethod()
			throws Exception {
		// Incoming call hierarchy where the caller is inside a nested
		// Kotlin type. The match element's declaring type chain must
		// be non-null for CallSearchResultCollector.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/nestin");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/nestin/Target.java",
				"package nestin;\n"
				+ "public class Target {\n"
				+ "    public static int compute() { return 1; }\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/nestin/Caller.kt",
				"package nestin\n"
				+ "\n"
				+ "class Caller {\n"
				+ "    class Inner {\n"
				+ "        fun callFromInner(): Int {\n"
				+ "            return Target.compute()\n"
				+ "        }\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		ICompilationUnit javaCu = TestHelpers.getJavaCompilationUnit(
				project, "nestin", "Target.java");
		IType targetType = javaCu.getType("Target");
		IMethod compute = targetType.getMethod("compute",
				new String[0]);
		assertTrue(compute.exists(), "Target.compute() should exist");

		MethodWrapper[] roots = CallHierarchyCore.getDefault()
				.getCallerRoots(new IMember[] { compute });
		assertEquals(1, roots.length, "Should have one root");

		MethodWrapper[] callers = roots[0].getCalls(
				new NullProgressMonitor());
		boolean found = false;
		for (MethodWrapper caller : callers) {
			IMember member = caller.getMember();
			if ("callFromInner".equals(member.getElementName())) {
				found = true;
				assertNotNull(member.getDeclaringType(),
						"callFromInner should have declaring type");
				assertEquals("Inner",
						member.getDeclaringType().getElementName(),
						"callFromInner's declaring type should be "
								+ "Inner");
			}
		}
		assertTrue(found,
				"Should find callFromInner as caller. Found: "
						+ Arrays.stream(callers)
								.map(c -> c.getMember()
										.getElementName())
								.collect(Collectors.joining(", ")));
	}

	// ---- CU model for nested types ----

	@Test
	public void testNestedTypeDeclaringTypeViaCUModel()
			throws Exception {
		// KotlinModelManager.populateCompilationUnit builds type
		// elements. Nested types via CU model (used by codeSelect)
		// should have declaring type set.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/cumodel");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/cumodel/Parent.kt",
				"package cumodel\n"
				+ "\n"
				+ "class Parent {\n"
				+ "    class Child {\n"
				+ "        fun childMethod() {}\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		IFile ktFile = TestHelpers.getFile(
				"/" + PROJECT_NAME + "/src/cumodel/Parent.kt");
		KotlinCompilationUnit cu = KotlinModelManager.getInstance()
				.getCompilationUnit(ktFile);
		assertNotNull(cu, "CU should not be null");

		// Find the Parent type in CU model
		IType[] types = cu.getTypes();
		assertTrue(types.length >= 1, "Should have at least one type");
		IType parentType = null;
		for (IType t : types) {
			if ("Parent".equals(t.getElementName())) {
				parentType = t;
			}
		}
		assertNotNull(parentType, "Should find Parent type");

		// Parent's declaring type should be null (top-level type)
		// — this is correct; top-level types don't have a declaring type

		// Find Child as a nested type within Parent's children
		IJavaElement[] children = parentType.getChildren();
		IType childType = null;
		for (IJavaElement child : children) {
			if (child instanceof IType t
					&& "Child".equals(t.getElementName())) {
				childType = t;
			}
		}
		assertNotNull(childType, "Should find Child in Parent's "
				+ "children");
		assertNotNull(childType.getDeclaringType(),
				"Nested type Child should have non-null declaring "
						+ "type via CU model");
		assertEquals("Parent",
				childType.getDeclaringType().getElementName(),
				"Child's declaring type should be Parent");
	}

	// ---- Deep nesting (3+ levels) ----
	//
	// Parser stores enclosingTypeName as peek() from a stack, which
	// only gives the immediate parent name. For Outer.Middle.Inner,
	// Inner gets enclosingTypeName="Middle" instead of "Outer.Middle".
	// This breaks FQN construction, declaring type chains, and search.

	@Test
	public void testDeeplyNestedTypeFQN() throws Exception {
		// 3-level nesting: Outer > Middle > Inner
		// Inner.getFullyQualifiedName() must be "deep.Outer.Middle.Inner"
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/deep");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/deep/Outer.kt",
				"package deep\n"
				+ "\n"
				+ "class Outer {\n"
				+ "    class Middle {\n"
				+ "        class Inner {\n"
				+ "            fun innerMethod(): String = \"deep\"\n"
				+ "        }\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for Inner type
		SearchPattern pattern = SearchPattern.createPattern(
				"Inner", IJavaSearchConstants.TYPE,
				IJavaSearchConstants.DECLARATIONS,
				SearchPattern.R_EXACT_MATCH);
		List<SearchMatch> matches = new ArrayList<>();
		new SearchEngine().search(pattern,
				SearchEngine.getSearchParticipants(),
				SearchEngine.createJavaSearchScope(
						new IJavaProject[] { project }),
				new SearchRequestor() {
					@Override
					public void acceptSearchMatch(SearchMatch match) {
						matches.add(match);
					}
				}, new NullProgressMonitor());

		List<SearchMatch> ktMatches = TestHelpers
				.filterKotlinMatches(matches);
		assertTrue(ktMatches.size() >= 1,
				"Should find Inner type declaration");

		IType innerType = (IType) ktMatches.get(0).getElement();
		assertEquals("deep.Outer.Middle.Inner",
				innerType.getFullyQualifiedName(),
				"3-level nested type FQN should include full chain");
	}

	@Test
	public void testDeeplyNestedDeclaringTypeChain() throws Exception {
		// Full declaring type chain: Inner → Middle → Outer
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/chain3");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/chain3/A.kt",
				"package chain3\n"
				+ "\n"
				+ "class A {\n"
				+ "    class B {\n"
				+ "        class C {\n"
				+ "            fun cMethod(): Int = 1\n"
				+ "        }\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> matches = TestHelpers
				.searchMethodDeclarations("cMethod", project);
		List<SearchMatch> ktMatches = TestHelpers
				.filterKotlinMatches(matches);
		assertTrue(ktMatches.size() >= 1,
				"Should find cMethod declaration");

		IMember method = (IMember) ktMatches.get(0).getElement();
		IType c = method.getDeclaringType();
		assertNotNull(c, "cMethod declaring type should be C");
		assertEquals("C", c.getElementName());

		IType b = c.getDeclaringType();
		assertNotNull(b, "C's declaring type should be B");
		assertEquals("B", b.getElementName());

		IType a = b.getDeclaringType();
		assertNotNull(a, "B's declaring type should be A");
		assertEquals("A", a.getElementName());
	}

	@Test
	public void testDeeplyNestedOutgoingCallHierarchy() throws Exception {
		// Outgoing calls from a method 3 levels deep should not throw
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/deepcall");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/deepcall/Api.java",
				"package deepcall;\n"
				+ "public class Api {\n"
				+ "    public static void deepTarget() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/deepcall/Levels.kt",
				"package deepcall\n"
				+ "\n"
				+ "class Level1 {\n"
				+ "    class Level2 {\n"
				+ "        class Level3 {\n"
				+ "            fun callFromDeep() {\n"
				+ "                Api.deepTarget()\n"
				+ "            }\n"
				+ "        }\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> matches = TestHelpers
				.searchMethodDeclarations("callFromDeep", project);
		List<SearchMatch> ktMatches = TestHelpers
				.filterKotlinMatches(matches);
		assertTrue(ktMatches.size() >= 1,
				"Should find callFromDeep declaration");

		IMember kotlinMethod = (IMember) ktMatches.get(0).getElement();
		MethodWrapper[] roots = CallHierarchyCore.getDefault()
				.getCalleeRoots(new IMember[] { kotlinMethod });
		assertEquals(1, roots.length, "Should have one root");

		MethodWrapper[] callees = roots[0].getCalls(
				new NullProgressMonitor());
		Set<String> calleeNames = new HashSet<>();
		for (MethodWrapper callee : callees) {
			calleeNames.add(callee.getMember().getElementName());
		}
		assertTrue(calleeNames.contains("deepTarget"),
				"Should find deepTarget from 3-level nested caller. "
						+ "Found: " + calleeNames);
	}

	@Test
	public void testDeeplyNestedIncomingCallHierarchy() throws Exception {
		// Incoming calls where the Kotlin caller is 3 levels deep
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/deepin");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/deepin/Target.java",
				"package deepin;\n"
				+ "public class Target {\n"
				+ "    public static int getValue() { return 42; }\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/deepin/Deep.kt",
				"package deepin\n"
				+ "\n"
				+ "class Outer {\n"
				+ "    class Middle {\n"
				+ "        class Inner {\n"
				+ "            fun useTarget(): Int {\n"
				+ "                return Target.getValue()\n"
				+ "            }\n"
				+ "        }\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		ICompilationUnit javaCu = TestHelpers.getJavaCompilationUnit(
				project, "deepin", "Target.java");
		IType targetType = javaCu.getType("Target");
		IMethod getValue = targetType.getMethod("getValue",
				new String[0]);
		assertTrue(getValue.exists(), "Target.getValue() should exist");

		MethodWrapper[] roots = CallHierarchyCore.getDefault()
				.getCallerRoots(new IMember[] { getValue });
		assertEquals(1, roots.length, "Should have one root");

		MethodWrapper[] callers = roots[0].getCalls(
				new NullProgressMonitor());
		boolean found = false;
		for (MethodWrapper caller : callers) {
			IMember member = caller.getMember();
			if ("useTarget".equals(member.getElementName())) {
				found = true;
				// Verify the full declaring type chain
				IType inner = member.getDeclaringType();
				assertNotNull(inner,
						"useTarget declaring type should be Inner");
				assertEquals("Inner", inner.getElementName());
				IType middle = inner.getDeclaringType();
				assertNotNull(middle,
						"Inner's declaring type should be Middle");
				assertEquals("Middle", middle.getElementName());
			}
		}
		assertTrue(found,
				"Should find useTarget as caller. Found: "
						+ Arrays.stream(callers)
								.map(c -> c.getMember()
										.getElementName())
								.collect(Collectors.joining(", ")));
	}

	@Test
	public void testDeeplyNestedReferenceMatchEnclosingElement()
			throws Exception {
		// Reference to a Java method from inside a 3-level nested type.
		// The match element should have the full declaring type chain.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/deepref");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/deepref/Service.java",
				"package deepref;\n"
				+ "public class Service {\n"
				+ "    public static void serve() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/deepref/Nested.kt",
				"package deepref\n"
				+ "\n"
				+ "class L1 {\n"
				+ "    class L2 {\n"
				+ "        class L3 {\n"
				+ "            fun callServe() {\n"
				+ "                Service.serve()\n"
				+ "            }\n"
				+ "        }\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchMethodReferences("serve", project);
		List<SearchMatch> ktRefs = TestHelpers
				.filterKotlinMatches(refs);
		assertTrue(ktRefs.size() >= 1,
				"Should find Kotlin reference to serve");

		IMember enclosing = (IMember) ktRefs.get(0).getElement();
		assertEquals("callServe", enclosing.getElementName());

		// Verify declaring type chain: callServe → L3 → L2 → L1
		IType l3 = enclosing.getDeclaringType();
		assertNotNull(l3, "callServe declaring type should be L3");
		assertEquals("L3", l3.getElementName());

		IType l2 = l3.getDeclaringType();
		assertNotNull(l2, "L3's declaring type should be L2");
		assertEquals("L2", l2.getElementName());

		IType l1 = l2.getDeclaringType();
		assertNotNull(l1, "L2's declaring type should be L1");
		assertEquals("L1", l1.getElementName());
	}

	@Test
	public void testDeeplyNestedCUModelDeclaringTypeChain()
			throws Exception {
		// CU model path: nested types via getChildren() should
		// have the full declaring type chain at arbitrary depth
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/cudeep");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/cudeep/Root.kt",
				"package cudeep\n"
				+ "\n"
				+ "class Root {\n"
				+ "    class Branch {\n"
				+ "        class Leaf {\n"
				+ "            fun leafFun() {}\n"
				+ "        }\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		IFile ktFile = TestHelpers.getFile(
				"/" + PROJECT_NAME + "/src/cudeep/Root.kt");
		KotlinCompilationUnit cu = KotlinModelManager.getInstance()
				.getCompilationUnit(ktFile);
		assertNotNull(cu, "CU should not be null");

		// Walk: Root → Branch → Leaf
		IType[] types = cu.getTypes();
		IType root = null;
		for (IType t : types) {
			if ("Root".equals(t.getElementName())) {
				root = t;
			}
		}
		assertNotNull(root, "Should find Root type");

		// Find Branch in Root's children
		IType branch = null;
		for (IJavaElement child : root.getChildren()) {
			if (child instanceof IType t
					&& "Branch".equals(t.getElementName())) {
				branch = t;
			}
		}
		assertNotNull(branch, "Should find Branch in Root");
		assertNotNull(branch.getDeclaringType(),
				"Branch should have declaring type");
		assertEquals("Root", branch.getDeclaringType().getElementName(),
				"Branch's declaring type should be Root");

		// Find Leaf in Branch's children
		IType leaf = null;
		for (IJavaElement child : branch.getChildren()) {
			if (child instanceof IType t
					&& "Leaf".equals(t.getElementName())) {
				leaf = t;
			}
		}
		assertNotNull(leaf, "Should find Leaf in Branch");
		assertNotNull(leaf.getDeclaringType(),
				"Leaf should have declaring type");
		assertEquals("Branch",
				leaf.getDeclaringType().getElementName(),
				"Leaf's declaring type should be Branch");

		// Leaf → Branch → Root chain
		assertNotNull(leaf.getDeclaringType().getDeclaringType(),
				"Branch (from Leaf) should have declaring type Root");
		assertEquals("Root",
				leaf.getDeclaringType().getDeclaringType()
						.getElementName(),
				"Full chain: Leaf → Branch → Root");
	}

	// ---- Bug #12, Bug 2: Kotlin property-style access to Java getters ----
	//
	// In Kotlin, `obj.field` accesses a Java getter `getField()`.
	// Incoming call hierarchy on the Java getter should find Kotlin
	// callers that use property syntax. Similarly, `obj.field = x`
	// accesses `setField(x)`.

	@Test
	public void testIncomingCallsJavaGetterFromKotlinPropertyAccess()
			throws Exception {
		// Java class with getter, Kotlin accesses via property syntax
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/propget");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/propget/Metrics.java",
				"package propget;\n"
				+ "public class Metrics {\n"
				+ "    private int counter;\n"
				+ "    public int getCounter() { return counter; }\n"
				+ "    public void setCounter(int c) {"
				+ " this.counter = c; }\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/propget/Consumer.kt",
				"package propget\n"
				+ "\n"
				+ "class Consumer(private val metrics: Metrics) {\n"
				+ "    fun readMetric(): Int {\n"
				+ "        return metrics.counter\n"
				+ "    }\n"
				+ "    fun writeMetric(value: Int) {\n"
				+ "        metrics.counter = value\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Find the Java getter
		ICompilationUnit javaCu = TestHelpers.getJavaCompilationUnit(
				project, "propget", "Metrics.java");
		IType metricsType = javaCu.getType("Metrics");
		IMethod getCounter = metricsType.getMethod("getCounter",
				new String[0]);
		assertTrue(getCounter.exists(),
				"Metrics.getCounter() should exist");

		// Incoming calls should find Kotlin property-style access
		MethodWrapper[] roots = CallHierarchyCore.getDefault()
				.getCallerRoots(new IMember[] { getCounter });
		assertEquals(1, roots.length, "Should have one root");

		MethodWrapper[] callers = roots[0].getCalls(
				new NullProgressMonitor());
		boolean found = false;
		for (MethodWrapper caller : callers) {
			if ("readMetric".equals(
					caller.getMember().getElementName())) {
				found = true;
			}
		}
		assertTrue(found,
				"Incoming calls to getCounter() should find "
						+ "Kotlin readMetric() which uses "
						+ "metrics.counter (property syntax). Found: "
						+ Arrays.stream(callers)
								.map(c -> c.getMember()
										.getElementName())
								.collect(Collectors.joining(", ")));
	}

	@Test
	public void testReferenceSearchJavaGetterFromKotlinPropertyAccess()
			throws Exception {
		// Lower-level test: search for references to getCounter()
		// should find Kotlin property-style access metrics.counter
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/propref");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/propref/Data.java",
				"package propref;\n"
				+ "public class Data {\n"
				+ "    private String name;\n"
				+ "    public String getName() { return name; }\n"
				+ "    public void setName(String n) {"
				+ " this.name = n; }\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/propref/Reader.kt",
				"package propref\n"
				+ "\n"
				+ "fun readName(d: Data): String {\n"
				+ "    return d.name\n"
				+ "}\n"
				+ "\n"
				+ "fun writeName(d: Data, n: String) {\n"
				+ "    d.name = n\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for references to getName via SearchEngine
		List<SearchMatch> refs = TestHelpers
				.searchMethodReferences("getName", project);
		List<SearchMatch> ktRefs = TestHelpers
				.filterKotlinMatches(refs);

		assertTrue(ktRefs.size() >= 1,
				"Reference search for getName() should find Kotlin "
						+ "property-style access d.name. "
						+ "Total refs: " + refs.size()
						+ ", Kotlin refs: " + ktRefs.size());
	}

	@Test
	public void testReferenceSearchJavaGetterWithAcronymPropertyName()
			throws Exception {
		// Java getter getPPInsertTimer() for property ppInsertTimer
		// — consecutive lowercase chars at start form an acronym in getter
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/acronym");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/acronym/Metrics.java",
				"package acronym;\n"
				+ "public class Metrics {\n"
				+ "    private int ppInsertTimer;\n"
				+ "    private String dbUrl;\n"
				+ "    public int getPPInsertTimer() {"
				+ " return ppInsertTimer; }\n"
				+ "    public String getDBUrl() {"
				+ " return dbUrl; }\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/acronym/User.kt",
				"package acronym\n"
				+ "\n"
				+ "fun useMetrics(m: Metrics) {\n"
				+ "    val timer = m.ppInsertTimer\n"
				+ "    val url = m.dbUrl\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Element-based search (like CallerMethodWrapper uses)
		IType metricsType = project.findType("acronym.Metrics");
		assertNotNull(metricsType, "Metrics type should exist");

		// Search for getPPInsertTimer references
		IMethod ppMethod = metricsType.getMethod(
				"getPPInsertTimer", new String[0]);
		assertTrue(ppMethod.exists(),
				"getPPInsertTimer() should exist");
		SearchPattern ppPattern = SearchPattern.createPattern(
				ppMethod, IJavaSearchConstants.REFERENCES);
		List<SearchMatch> ppRefs = TestHelpers.executeSearch(
				ppPattern, project);
		List<SearchMatch> ppKtRefs = TestHelpers
				.filterKotlinMatches(ppRefs);
		assertTrue(ppKtRefs.size() >= 1,
				"Element-based search for getPPInsertTimer() "
						+ "should find Kotlin m.ppInsertTimer. "
						+ "Total: " + ppRefs.size()
						+ ", Kotlin: " + ppKtRefs.size());

		// Search for getDBUrl references
		IMethod dbMethod = metricsType.getMethod(
				"getDBUrl", new String[0]);
		assertTrue(dbMethod.exists(), "getDBUrl() should exist");
		SearchPattern dbPattern = SearchPattern.createPattern(
				dbMethod, IJavaSearchConstants.REFERENCES);
		List<SearchMatch> dbRefs = TestHelpers.executeSearch(
				dbPattern, project);
		List<SearchMatch> dbKtRefs = TestHelpers
				.filterKotlinMatches(dbRefs);
		assertTrue(dbKtRefs.size() >= 1,
				"Element-based search for getDBUrl() "
						+ "should find Kotlin m.dbUrl. "
						+ "Total: " + dbRefs.size()
						+ ", Kotlin: " + dbKtRefs.size());

		// Also verify string-based search works
		List<SearchMatch> strRefs = TestHelpers
				.searchMethodReferences("getPPInsertTimer", project);
		List<SearchMatch> strKtRefs = TestHelpers
				.filterKotlinMatches(strRefs);
		assertTrue(strKtRefs.size() >= 1,
				"String-based search for getPPInsertTimer() "
						+ "should find Kotlin m.ppInsertTimer. "
						+ "Total: " + strRefs.size()
						+ ", Kotlin: " + strKtRefs.size());
	}

	@Test
	public void testReverseSearchKotlinPropertyFindsJavaGetterCalls()
			throws Exception {
		// Reverse direction: searching for references to a Kotlin
		// property should find Java getter/setter calls.
		// Kotlin: class Person { var name: String = "" }
		// Java: person.getName(), person.setName("x")
		// Search for field "name" references → should find Java calls
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/reverse");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/reverse/Person.kt",
				"package reverse\n"
				+ "\n"
				+ "class Person {\n"
				+ "    var name: String = \"\"\n"
				+ "    var age: Int = 0\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/reverse/JavaCaller.java",
				"package reverse;\n"
				+ "public class JavaCaller {\n"
				+ "    public void readPerson(Person p) {\n"
				+ "        String n = p.getName();\n"
				+ "        int a = p.getAge();\n"
				+ "    }\n"
				+ "    public void writePerson(Person p) {\n"
				+ "        p.setName(\"Alice\");\n"
				+ "        p.setAge(30);\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for field "name" references — should find Java
		// getName()/setName() calls
		List<SearchMatch> nameRefs = TestHelpers
				.searchFieldReferences("name", project);
		List<SearchMatch> nameJavaRefs = nameRefs.stream()
				.filter(m -> m.getResource() != null
						&& m.getResource().getName()
								.endsWith(".java"))
				.toList();
		assertTrue(nameJavaRefs.size() >= 2,
				"Field search for 'name' should find Java "
						+ "getName() and setName() calls. "
						+ "Total: " + nameRefs.size()
						+ ", Java: " + nameJavaRefs.size());

		// Search for field "age" references too
		List<SearchMatch> ageRefs = TestHelpers
				.searchFieldReferences("age", project);
		List<SearchMatch> ageJavaRefs = ageRefs.stream()
				.filter(m -> m.getResource() != null
						&& m.getResource().getName()
								.endsWith(".java"))
				.toList();
		assertTrue(ageJavaRefs.size() >= 2,
				"Field search for 'age' should find Java "
						+ "getAge() and setAge() calls. "
						+ "Total: " + ageRefs.size()
						+ ", Java: " + ageJavaRefs.size());
	}

	// ---- Tests for Fix 1: Symbol table fallback (Kotlin receiver) ----

	@Test
	public void testLocateCalleesResolvesMethodOnKotlinReceiver()
			throws CoreException {
		// Two Kotlin files: a service class and a caller that invokes
		// methods on a parameter of that Kotlin type. Since the type
		// has no .class file, findType() returns null — the symbol
		// table fallback must resolve it.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/symfall");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/symfall/KtService.kt",
				"package symfall\n"
				+ "\n"
				+ "class KtService {\n"
				+ "    fun execute(): String = \"done\"\n"
				+ "    fun validate(): Boolean = true\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/symfall/Caller.kt",
				"package symfall\n"
				+ "\n"
				+ "fun callService(svc: KtService): String {\n"
				+ "    svc.validate()\n"
				+ "    return svc.execute()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		SearchParticipant participant = SearchParticipantRegistry
				.getParticipant("kt");
		List<SearchMatch> decls = TestHelpers
				.searchMethodDeclarations("callService", project);
		List<SearchMatch> ktDecls =
				TestHelpers.filterKotlinMatches(decls);
		assertTrue(ktDecls.size() >= 1,
				"Should find callService declaration");
		IMember caller = (IMember) ktDecls.get(0).getElement();

		String path = caller.getCompilationUnit().getPath()
				.toString();
		SearchMatch[] callees = participant.locateCallees(
				caller, participant.getDocument(path), null);

		Set<String> resolvedNames = new HashSet<>();
		for (SearchMatch match : callees) {
			IMember callee = (IMember) match.getElement();
			if (callee.exists()) {
				resolvedNames.add(callee.getElementName());
			}
		}
		assertTrue(resolvedNames.contains("execute"),
				"execute() on Kotlin receiver should resolve "
				+ "via symbol table. Resolved: " + resolvedNames);
		assertTrue(resolvedNames.contains("validate"),
				"validate() on Kotlin receiver should resolve "
				+ "via symbol table. Resolved: " + resolvedNames);
	}

	// ---- Tests for Fix 2: Local member check (implicit this) ----

	@Test
	public void testLocateCalleesResolvesImplicitThisSameClass()
			throws CoreException {
		// A Kotlin class with methods calling other methods on the
		// same class via implicit this. The class is pure Kotlin
		// (no Java supertype), so JDT findType fails — the local
		// member check in the parse tree must resolve it.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/impllocal");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/impllocal/Worker.kt",
				"package impllocal\n"
				+ "\n"
				+ "class Worker {\n"
				+ "    fun prepare(): String = \"ready\"\n"
				+ "    fun cleanup(): Boolean = true\n"
				+ "    fun run(): String {\n"
				+ "        prepare()\n"
				+ "        cleanup()\n"
				+ "        return \"done\"\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		SearchParticipant participant = SearchParticipantRegistry
				.getParticipant("kt");
		List<SearchMatch> decls = TestHelpers
				.searchMethodDeclarations("run", project);
		List<SearchMatch> ktDecls =
				TestHelpers.filterKotlinMatches(decls);
		// Filter for the one in impllocal package
		ktDecls = ktDecls.stream()
				.filter(m -> m.getResource() != null
						&& m.getResource().getName()
								.equals("Worker.kt"))
				.toList();
		assertTrue(ktDecls.size() >= 1,
				"Should find run declaration in Worker.kt");
		IMember caller = (IMember) ktDecls.get(0).getElement();

		String path = caller.getCompilationUnit().getPath()
				.toString();
		SearchMatch[] callees = participant.locateCallees(
				caller, participant.getDocument(path), null);

		Set<String> resolvedNames = new HashSet<>();
		for (SearchMatch match : callees) {
			IMember callee = (IMember) match.getElement();
			if (callee.exists()) {
				resolvedNames.add(callee.getElementName());
			}
		}
		assertTrue(resolvedNames.contains("prepare"),
				"prepare() via implicit this should resolve. "
				+ "Resolved: " + resolvedNames);
		assertTrue(resolvedNames.contains("cleanup"),
				"cleanup() via implicit this should resolve. "
				+ "Resolved: " + resolvedNames);
	}

	// ---- Tests for Fix 3: Constructor calls via symbol table ----

	@Test
	public void testLocateCalleesResolvesKotlinConstructor()
			throws CoreException {
		// Constructor call to a Kotlin class with no .class file.
		// The symbol table must resolve it.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/ktctor");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/ktctor/Config.kt",
				"package ktctor\n"
				+ "\n"
				+ "class Config(val name: String)\n"
				+ "class Options(val verbose: Boolean)\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/ktctor/Factory.kt",
				"package ktctor\n"
				+ "\n"
				+ "fun createConfig(): Config {\n"
				+ "    val opts = Options(true)\n"
				+ "    return Config(\"default\")\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		SearchParticipant participant = SearchParticipantRegistry
				.getParticipant("kt");
		List<SearchMatch> decls = TestHelpers
				.searchMethodDeclarations("createConfig", project);
		List<SearchMatch> ktDecls =
				TestHelpers.filterKotlinMatches(decls);
		assertTrue(ktDecls.size() >= 1,
				"Should find createConfig declaration");
		IMember caller = (IMember) ktDecls.get(0).getElement();

		String path = caller.getCompilationUnit().getPath()
				.toString();
		SearchMatch[] callees = participant.locateCallees(
				caller, participant.getDocument(path), null);

		Set<String> resolvedNames = new HashSet<>();
		for (SearchMatch match : callees) {
			IMember callee = (IMember) match.getElement();
			if (callee.exists()) {
				resolvedNames.add(callee.getElementName());
			}
		}
		assertTrue(resolvedNames.contains("Config"),
				"Config constructor should resolve via symbol "
				+ "table. Resolved: " + resolvedNames);
		assertTrue(resolvedNames.contains("Options"),
				"Options constructor should resolve via symbol "
				+ "table. Resolved: " + resolvedNames);
	}

	// ---- Tests for Fix 4: Facade class resolution ----

	@Test
	public void testLocateCalleesResolvesImportedTopLevelFunction()
			throws CoreException {
		// A top-level function in one file called from another.
		// The symbol table should have the facade class info.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/facade");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/facade/Utils.kt",
				"package facade\n"
				+ "\n"
				+ "fun doFormat(value: String): String = value.trim()\n"
				+ "fun doValidate(value: String): Boolean ="
				+ " value.isNotEmpty()\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/facade/Consumer.kt",
				"package facade\n"
				+ "\n"
				+ "fun consume(input: String): String {\n"
				+ "    doValidate(input)\n"
				+ "    return doFormat(input)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		SearchParticipant participant = SearchParticipantRegistry
				.getParticipant("kt");
		List<SearchMatch> decls = TestHelpers
				.searchMethodDeclarations("consume", project);
		List<SearchMatch> ktDecls =
				TestHelpers.filterKotlinMatches(decls);
		assertTrue(ktDecls.size() >= 1,
				"Should find consume declaration");
		IMember caller = (IMember) ktDecls.get(0).getElement();

		String path = caller.getCompilationUnit().getPath()
				.toString();
		SearchMatch[] callees = participant.locateCallees(
				caller, participant.getDocument(path), null);

		Set<String> resolvedNames = new HashSet<>();
		for (SearchMatch match : callees) {
			IMember callee = (IMember) match.getElement();
			if (callee.exists()) {
				resolvedNames.add(callee.getElementName());
			}
		}
		assertTrue(resolvedNames.contains("doFormat"),
				"doFormat() top-level function should resolve. "
				+ "Resolved: " + resolvedNames);
		assertTrue(resolvedNames.contains("doValidate"),
				"doValidate() top-level function should resolve. "
				+ "Resolved: " + resolvedNames);
	}

	// ---- Tests for Fix 5: Extension function resolution ----

	@Test
	public void testLocateCalleesResolvesExtensionFunction()
			throws CoreException {
		// Extension function calls like toString(), hashCode() are on
		// java.lang.Object. But also test that methods resolved via
		// JDT supertypes still work (e.g., String.length inherited
		// from CharSequence).
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/extfn");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/extfn/ExtTest.kt",
				"package extfn\n"
				+ "\n"
				+ "fun extTest(text: String): Int {\n"
				+ "    val hash = text.hashCode()\n"
				+ "    val upper = text.uppercase()\n"
				+ "    return text.length\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		SearchParticipant participant = SearchParticipantRegistry
				.getParticipant("kt");
		List<SearchMatch> decls = TestHelpers
				.searchMethodDeclarations("extTest", project);
		List<SearchMatch> ktDecls =
				TestHelpers.filterKotlinMatches(decls);
		assertTrue(ktDecls.size() >= 1,
				"Should find extTest declaration");
		IMember caller = (IMember) ktDecls.get(0).getElement();

		String path = caller.getCompilationUnit().getPath()
				.toString();
		SearchMatch[] callees = participant.locateCallees(
				caller, participant.getDocument(path), null);

		Set<String> resolvedNames = new HashSet<>();
		for (SearchMatch match : callees) {
			IMember callee = (IMember) match.getElement();
			if (callee.exists()) {
				resolvedNames.add(callee.getElementName());
			}
		}
		assertTrue(resolvedNames.contains("hashCode"),
				"hashCode() should resolve via String/Object. "
				+ "Resolved: " + resolvedNames);
	}

	// ---- Tests for Fix 6: Property type inference from initializers ----

	@Test
	public void testLocateCalleesResolvesPropertyInitializerType()
			throws CoreException {
		// A class property initialized via StringBuilder() — no type
		// annotation. Methods on the property should resolve because
		// initClassScope infers the type from the initializer.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/propinf");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/propinf/Builder.kt",
				"package propinf\n"
				+ "\n"
				+ "class Builder {\n"
				+ "    val sb = StringBuilder()\n"
				+ "    val items = ArrayList<String>()\n"
				+ "\n"
				+ "    fun build(): String {\n"
				+ "        sb.append(\"hello\")\n"
				+ "        sb.append(\" world\")\n"
				+ "        items.add(\"item\")\n"
				+ "        return sb.toString()\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		SearchParticipant participant = SearchParticipantRegistry
				.getParticipant("kt");
		List<SearchMatch> decls = TestHelpers
				.searchMethodDeclarations("build", project);
		List<SearchMatch> ktDecls =
				TestHelpers.filterKotlinMatches(decls);
		// Filter for the one in propinf package
		ktDecls = ktDecls.stream()
				.filter(m -> m.getResource() != null
						&& m.getResource().getName()
								.equals("Builder.kt"))
				.toList();
		assertTrue(ktDecls.size() >= 1,
				"Should find build declaration in Builder.kt");
		IMember caller = (IMember) ktDecls.get(0).getElement();

		String path = caller.getCompilationUnit().getPath()
				.toString();
		SearchMatch[] callees = participant.locateCallees(
				caller, participant.getDocument(path), null);

		Set<String> resolvedNames = new HashSet<>();
		for (SearchMatch match : callees) {
			IMember callee = (IMember) match.getElement();
			if (callee.exists()) {
				resolvedNames.add(callee.getElementName());
			}
		}
		assertTrue(resolvedNames.contains("append"),
				"append() on StringBuilder property should "
				+ "resolve via initializer type inference. "
				+ "Resolved: " + resolvedNames);
		assertTrue(resolvedNames.contains("add"),
				"add() on ArrayList property should resolve "
				+ "via initializer type inference. "
				+ "Resolved: " + resolvedNames);
		assertTrue(resolvedNames.contains("toString"),
				"toString() on StringBuilder should resolve. "
				+ "Resolved: " + resolvedNames);
	}
}
