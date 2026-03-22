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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.search.SearchDocument;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.manipulation.JavaManipulation;
import org.eclipse.jdt.internal.core.search.indexing.SearchParticipantRegistry;
import org.eclipse.jdt.internal.corext.callhierarchy.CallHierarchyCore;
import org.eclipse.jdt.internal.corext.callhierarchy.MethodWrapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.karellen.jdtls.kotlin.search.KotlinCompilationUnit;
import co.karellen.jdtls.kotlin.search.KotlinElement;
import co.karellen.jdtls.kotlin.search.KotlinModelManager;

/**
 * E2E tests for callable reference ({@code ::fn}, {@code Type::method})
 * and infix function call ({@code a to b}) indexing, find-references,
 * and call hierarchy support.
 *
 * @author Arcadiy Ivanov
 */
public class CrossLanguageCallableRefInfixTest {

	private static final String PROJECT_NAME = "CallableRefInfixTest";
	private IJavaProject project;

	@BeforeEach
	public void setUp() throws CoreException {
		if (JavaManipulation.getPreferenceNodeId() == null) {
			JavaManipulation.setPreferenceNodeId(
					"co.karellen.jdtls.kotlin.tests");
		}
		SearchParticipantRegistry.reset();
		project = TestHelpers.createJavaProject(PROJECT_NAME, "src");
	}

	@AfterEach
	public void tearDown() throws CoreException {
		TestHelpers.deleteProject(PROJECT_NAME);
		project = null;
	}

	private static String describeMatches(List<SearchMatch> matches) {
		return matches.stream()
				.map(m -> String.format("[offset=%d len=%d resource=%s]",
						m.getOffset(), m.getLength(),
						m.getResource() != null
								? m.getResource().getName() : "null"))
				.collect(Collectors.joining(", "));
	}

	// ---- Callable reference tests ----

	@Test
	public void testCallableReferenceFindReferences()
			throws CoreException {
		// ::process should appear when searching for references to
		// "process"
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/callref");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/callref/Defs.kt",
				"package callref\n"
				+ "class Processor {\n"
				+ "    fun process(input: String): String = input\n"
				+ "    fun transform(input: String): String = input\n"
				+ "}\n"
				+ "fun useRef() {\n"
				+ "    val proc = Processor()\n"
				+ "    val ref = proc::process\n"
				+ "    val fn = ::transform\n"
				+ "}\n"
				+ "fun transform(s: String): String = s\n");
		TestHelpers.waitUntilIndexesReady();

		// Also search as all occurrences (declarations + references)
		// to see if the document is found at all
		List<SearchMatch> allRefs =
				TestHelpers.searchMethodAllOccurrences(
						"process", project);
		List<SearchMatch> refs = TestHelpers.searchMethodReferences(
				"process", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		List<SearchMatch> ktAll = TestHelpers.filterKotlinMatches(
				allRefs);
		assertTrue(ktRefs.size() >= 1,
				"Should find ::process callable reference. "
						+ "refs=" + describeMatches(refs)
						+ " allOccurrences="
						+ describeMatches(allRefs)
						+ " ktAll=" + describeMatches(ktAll));
	}

	@Test
	public void testCallableReferenceToTopLevelFunction()
			throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/callref2");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/callref2/Defs.kt",
				"package callref2\n"
				+ "fun compute(x: Int): Int = x * 2\n"
				+ "fun caller() {\n"
				+ "    val items = listOf(1, 2, 3)\n"
				+ "    items.map(::compute)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers.searchMethodReferences(
				"compute", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertTrue(ktRefs.size() >= 1,
				"Should find ::compute callable reference, got: "
						+ describeMatches(refs));
	}

	@Test
	public void testCallableReferenceInOutgoingCallHierarchy()
			throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/callrefh");
		IFile ktFile = TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/callrefh/Defs.kt",
				"package callrefh\n"
				+ "fun targetFn(s: String): String = s\n"
				+ "fun callerFn() {\n"
				+ "    val ref = ::targetFn\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		KotlinCompilationUnit cu =
				KotlinModelManager.getInstance()
						.getCompilationUnit(ktFile);
		assertNotNull(cu, "CU should not be null");

		// Find callerFn element via codeSelect
		IJavaElement[] elements = cu.codeSelect(
				"fun callerFn".length() + "package callrefh\n"
						.length() + "fun targetFn(s: String): String = s\n"
						.length() - "callerFn".length(),
				"callerFn".length());
		assertTrue(elements.length >= 1,
				"Should find callerFn element");

		// Get outgoing calls
		SearchParticipant participant =
				SearchParticipantRegistry.getParticipant("kt");
		SearchDocument doc = participant.getDocument(
				ktFile.getFullPath().toString());
		SearchMatch[] callees = participant.locateCallees(
				(org.eclipse.jdt.core.IMember) elements[0],
				doc, new NullProgressMonitor());
		boolean foundTarget = false;
		for (SearchMatch callee : callees) {
			if (callee.getElement() instanceof IJavaElement je
					&& "targetFn".equals(je.getElementName())) {
				foundTarget = true;
			}
		}
		assertTrue(foundTarget,
				"Outgoing calls from callerFn should include "
						+ "::targetFn callable reference");
	}

	// ---- Infix function call tests ----

	@Test
	public void testInfixCallFindReferences() throws CoreException {
		// `a combine b` should appear when searching for references
		// to "combine"
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/infixref");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/infixref/Defs.kt",
				"package infixref\n"
				+ "class Pair(val first: String, val second: String)\n"
				+ "infix fun String.combine(other: String): Pair "
				+ "= Pair(this, other)\n"
				+ "fun useInfix() {\n"
				+ "    val result = \"hello\" combine \"world\"\n"
				+ "    val pair = \"foo\" combine \"bar\"\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers.searchMethodReferences(
				"combine", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertTrue(ktRefs.size() >= 2,
				"Should find at least 2 infix 'combine' references,"
						+ " got: " + describeMatches(refs));
	}

	@Test
	public void testInfixCallToOperator() throws CoreException {
		// `key to value` should appear when searching for "to"
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/infixto");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/infixto/Defs.kt",
				"package infixto\n"
				+ "infix fun String.to(other: String): String "
				+ "= this + other\n"
				+ "fun useTo() {\n"
				+ "    val x = \"key\" to \"value\"\n"
				+ "    val y = \"a\" to \"b\"\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers.searchMethodReferences(
				"to", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertTrue(ktRefs.size() >= 2,
				"Should find at least 2 infix 'to' references, got: "
						+ describeMatches(refs));
	}

	@Test
	public void testInfixCallInOutgoingCallHierarchy()
			throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/infixch");
		IFile ktFile = TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/infixch/Defs.kt",
				"package infixch\n"
				+ "infix fun Int.power(exp: Int): Int = "
				+ "if (exp == 0) 1 else this * (this power (exp - 1))\n"
				+ "fun callerFn(): Int {\n"
				+ "    return 2 power 10\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		KotlinCompilationUnit cu =
				KotlinModelManager.getInstance()
						.getCompilationUnit(ktFile);
		assertNotNull(cu, "CU should not be null");

		SearchParticipant participant =
				SearchParticipantRegistry.getParticipant("kt");
		SearchDocument doc = participant.getDocument(
				ktFile.getFullPath().toString());

		// Find callerFn via codeSelect
		String source = "package infixch\n"
				+ "infix fun Int.power(exp: Int): Int = "
				+ "if (exp == 0) 1 else this * (this power (exp - 1))\n"
				+ "fun callerFn";
		IJavaElement[] elements = cu.codeSelect(
				source.length() - "callerFn".length(),
				"callerFn".length());
		assertTrue(elements.length >= 1,
				"Should find callerFn element");

		SearchMatch[] callees = participant.locateCallees(
				(org.eclipse.jdt.core.IMember) elements[0],
				doc, new NullProgressMonitor());
		boolean foundPower = false;
		for (SearchMatch callee : callees) {
			if (callee.getElement() instanceof IJavaElement je
					&& "power".equals(je.getElementName())) {
				foundPower = true;
			}
		}
		assertTrue(foundPower,
				"Outgoing calls from callerFn should include "
						+ "'power' infix call");
	}

	// ---- Operator call tests ----

	@Test
	public void testIndexingSuffixMapsToGet() throws CoreException {
		// arr[i] should produce a METHOD_REF for "get"
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/opidx");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/opidx/Defs.kt",
				"package opidx\n"
				+ "class Container {\n"
				+ "    operator fun get(i: Int): String = \"\"\n"
				+ "    operator fun get(i: Int, j: Int): String "
				+ "= \"\"\n"
				+ "}\n"
				+ "fun useIndex() {\n"
				+ "    val c = Container()\n"
				+ "    val x = c[0]\n"
				+ "    val y = c[1, 2]\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers.searchMethodReferences(
				"get", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertTrue(ktRefs.size() >= 2,
				"Should find at least 2 'get' references from [] "
						+ "indexing, got: " + describeMatches(refs));
	}

	@Test
	public void testArithmeticOperatorsMappedToFunctions()
			throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/oparith");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/oparith/Defs.kt",
				"package oparith\n"
				+ "class Vec(val x: Int, val y: Int) {\n"
				+ "    operator fun plus(other: Vec): Vec "
				+ "= Vec(x + other.x, y + other.y)\n"
				+ "    operator fun minus(other: Vec): Vec "
				+ "= Vec(x - other.x, y - other.y)\n"
				+ "    operator fun times(scalar: Int): Vec "
				+ "= Vec(x * scalar, y * scalar)\n"
				+ "}\n"
				+ "fun useOps() {\n"
				+ "    val a = Vec(1, 2)\n"
				+ "    val b = Vec(3, 4)\n"
				+ "    val sum = a + b\n"
				+ "    val diff = a - b\n"
				+ "    val scaled = a * 3\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> plusRefs =
				TestHelpers.searchMethodReferences("plus", project);
		List<SearchMatch> ktPlus =
				TestHelpers.filterKotlinMatches(plusRefs);
		assertTrue(ktPlus.size() >= 1,
				"Should find 'plus' from + operator, got: "
						+ describeMatches(plusRefs));

		List<SearchMatch> minusRefs =
				TestHelpers.searchMethodReferences("minus", project);
		List<SearchMatch> ktMinus =
				TestHelpers.filterKotlinMatches(minusRefs);
		assertTrue(ktMinus.size() >= 1,
				"Should find 'minus' from - operator, got: "
						+ describeMatches(minusRefs));

		List<SearchMatch> timesRefs =
				TestHelpers.searchMethodReferences("times", project);
		List<SearchMatch> ktTimes =
				TestHelpers.filterKotlinMatches(timesRefs);
		assertTrue(ktTimes.size() >= 1,
				"Should find 'times' from * operator, got: "
						+ describeMatches(timesRefs));
	}

	@Test
	public void testRangeOperatorMappedToRangeTo()
			throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/oprange");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/oprange/Defs.kt",
				"package oprange\n"
				+ "fun useRange() {\n"
				+ "    val r1 = 1..10\n"
				+ "    val r2 = 1..<10\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> rangeToRefs =
				TestHelpers.searchMethodReferences(
						"rangeTo", project);
		List<SearchMatch> ktRangeTo =
				TestHelpers.filterKotlinMatches(rangeToRefs);
		assertTrue(ktRangeTo.size() >= 1,
				"Should find 'rangeTo' from .. operator, got: "
						+ describeMatches(rangeToRefs));
	}
}
