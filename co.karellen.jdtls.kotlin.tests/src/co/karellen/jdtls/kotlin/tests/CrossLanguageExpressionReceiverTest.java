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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.internal.core.search.indexing.SearchParticipantRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests that receiver type verification works for complex expression
 * receivers (chained method calls, factory patterns) via the
 * ExpressionTypeResolver path. Exercises the fix for the short-circuit
 * bug where {@code extractReceiverName} returning null caused receiver
 * verification to be skipped entirely.
 *
 * @author Arcadiy Ivanov
 */
public class CrossLanguageExpressionReceiverTest {

	private static final String PROJECT_NAME = "ExprReceiverTest";
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

	/**
	 * Diagnostic helper: returns match details for assertion messages.
	 */
	private static String describeMatches(List<SearchMatch> matches) {
		return matches.stream()
				.map(m -> String.format("[offset=%d len=%d resource=%s]",
						m.getOffset(), m.getLength(),
						m.getResource() != null
								? m.getResource().getName() : "null"))
				.collect(Collectors.joining(", "));
	}

	@Test
	public void testChainedMethodCallReceiverFiltersWrongType()
			throws CoreException {
		// Factory pattern: factory.create().doWork()
		// When searching for Alpha.doWork, only calls where the factory
		// returns Alpha should match — calls where factory returns
		// Beta should be filtered out by ExpressionTypeResolver.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprchain");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprchain/Defs.kt",
				"package exprchain\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun doWork(cmd: String) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun doWork(cmd: String) {}\n"
				+ "}\n"
				+ "\n"
				+ "class AlphaFactory {\n"
				+ "    fun create(): Alpha = Alpha()\n"
				+ "}\n"
				+ "\n"
				+ "class BetaFactory {\n"
				+ "    fun create(): Beta = Beta()\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprchain/Caller.kt",
				"package exprchain\n"
				+ "\n"
				+ "fun callChained() {\n"
				+ "    val af: AlphaFactory = AlphaFactory()\n"
				+ "    val bf: BetaFactory = BetaFactory()\n"
				+ "\n"
				+ "    af.create().doWork(\"a1\")\n"
				+ "    af.create().doWork(\"a2\")\n"
				+ "\n"
				+ "    bf.create().doWork(\"b1\")\n"
				+ "    bf.create().doWork(\"b2\")\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"exprchain.Alpha.doWork", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		// Before fix: all 4 calls pass (receiver verification skipped)
		// After fix: only the 2 af.create().doWork calls should match
		assertEquals(2, ktRefs.size(),
				"Should find exactly 2 Kotlin references to "
				+ "Alpha.doWork() via factory chain, filtering out "
				+ "Beta.doWork() calls. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testPropertyChainReceiverFiltersWrongType()
			throws CoreException {
		// Property chain: holder.alpha.process() vs holder.beta.process()
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprprop");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprprop/Defs.kt",
				"package exprprop\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun process(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun process(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Holder {\n"
				+ "    val alpha: Alpha = Alpha()\n"
				+ "    val beta: Beta = Beta()\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprprop/Caller.kt",
				"package exprprop\n"
				+ "\n"
				+ "fun callPropertyChain() {\n"
				+ "    val h: Holder = Holder()\n"
				+ "\n"
				+ "    h.alpha.process(1)\n"
				+ "    h.alpha.process(2)\n"
				+ "\n"
				+ "    h.beta.process(3)\n"
				+ "    h.beta.process(4)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"exprprop.Alpha.process", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find exactly 2 Kotlin references to "
				+ "Alpha.process() via property chain, filtering out "
				+ "Beta.process() calls. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testMemberMethodChainViaThis() throws CoreException {
		// Method chain within class: this.getAlpha().invoke()
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprmember");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprmember/Defs.kt",
				"package exprmember\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun invoke(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun invoke(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Service {\n"
				+ "    fun getAlpha(): Alpha = Alpha()\n"
				+ "    fun getBeta(): Beta = Beta()\n"
				+ "\n"
				+ "    fun run() {\n"
				+ "        this.getAlpha().invoke(1)\n"
				+ "        this.getAlpha().invoke(2)\n"
				+ "\n"
				+ "        this.getBeta().invoke(3)\n"
				+ "        this.getBeta().invoke(4)\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"exprmember.Alpha.invoke", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find exactly 2 Kotlin references to "
				+ "Alpha.invoke() via this.getAlpha() chain, "
				+ "filtering out Beta.invoke() calls. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testConstructorReceiverFiltersWrongType()
			throws CoreException {
		// Constructor receiver: Alpha().run() vs Beta().run()
		// This path was already handled by extractReceiverName for
		// uppercase, but verify end-to-end filtering works.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprctor");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprctor/Caller.kt",
				"package exprctor\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun run(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun run(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "fun callConstructorReceiver() {\n"
				+ "    Alpha().run(1)\n"
				+ "    Alpha().run(2)\n"
				+ "\n"
				+ "    Beta().run(3)\n"
				+ "    Beta().run(4)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"exprctor.Alpha.run", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find exactly 2 Kotlin references to "
				+ "Alpha.run() via constructor receiver, filtering "
				+ "out Beta.run() calls. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testConstructorChainWithLetLambda()
			throws CoreException {
		// Alpha().let { it.act() } — constructor into scope function
		// Tests ExpressionTypeResolver resolving the constructor call
		// receiver type for the lambda scope
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprctorlet");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprctorlet/Defs.kt",
				"package exprctorlet\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun act(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun act(x: Int) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprctorlet/Caller.kt",
				"package exprctorlet\n"
				+ "\n"
				+ "fun useLet() {\n"
				+ "    // it = Alpha — should match\n"
				+ "    Alpha().let { it.act(1) }\n"
				+ "    Alpha().let { it.act(2) }\n"
				+ "\n"
				+ "    // it = Beta — should NOT match\n"
				+ "    Beta().let { it.act(3) }\n"
				+ "    Beta().let { it.act(4) }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"exprctorlet.Alpha.act", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find exactly 2 references to Alpha.act() "
				+ "via Constructor().let { it.act() }. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testIfExpressionReceiver() throws CoreException {
		// (if (flag) alpha else beta).act() — if-expression as receiver
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprif");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprif/Caller.kt",
				"package exprif\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun act(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun act(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "fun useIfExpr(flag: Boolean) {\n"
				+ "    val a: Alpha = Alpha()\n"
				+ "    val b: Beta = Beta()\n"
				+ "\n"
				+ "    // Receiver resolves to Alpha (first branch)\n"
				+ "    (if (flag) a else a).act(1)\n"
				+ "    (if (flag) a else a).act(2)\n"
				+ "\n"
				+ "    // Direct calls for comparison\n"
				+ "    a.act(3)\n"
				+ "    b.act(4)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"exprif.Alpha.act", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		// The if-expression receiver resolves to Alpha (first branch).
		// The direct a.act(3) also matches. b.act(4) should be
		// filtered. Total: 3 matches (2 if-expr + 1 direct).
		assertEquals(3, ktRefs.size(),
				"Should find 3 references to Alpha.act() "
				+ "(2 via if-expression receiver + 1 direct). Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testElvisExpressionReceiver() throws CoreException {
		// (nullableAlpha ?: fallback).act() — elvis as receiver
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprelvis");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprelvis/Caller.kt",
				"package exprelvis\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun act(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun act(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "fun useElvis() {\n"
				+ "    val a: Alpha = Alpha()\n"
				+ "    val b: Beta = Beta()\n"
				+ "\n"
				+ "    // Elvis resolves to Alpha (non-nullable)\n"
				+ "    (a ?: a).act(1)\n"
				+ "    (a ?: a).act(2)\n"
				+ "\n"
				+ "    // Direct calls\n"
				+ "    a.act(3)\n"
				+ "    b.act(4)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"exprelvis.Alpha.act", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		// Elvis resolves to Alpha. Direct a.act matches.
		// b.act filtered. Total: 3.
		assertEquals(3, ktRefs.size(),
				"Should find 3 references to Alpha.act() "
				+ "(2 via elvis receiver + 1 direct). Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testStringLiteralMethodCall() throws CoreException {
		// "hello".length — string literal as receiver
		// Tests visitStringLiteral → KotlinType.STRING
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprstr");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprstr/Caller.kt",
				"package exprstr\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun length(): Int = 0\n"
				+ "}\n"
				+ "\n"
				+ "fun useStringMethod() {\n"
				+ "    val a: Alpha = Alpha()\n"
				+ "\n"
				+ "    // String literal receiver — should NOT match\n"
				+ "    // Alpha.length\n"
				+ "    \"hello\".length\n"
				+ "\n"
				+ "    // Direct call — should match\n"
				+ "    a.length()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for Alpha.length
		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"exprstr.Alpha.length", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		// "hello".length is a property access on String, not a method
		// call on Alpha. a.length() is the real match.
		assertTrue(ktRefs.size() >= 1,
				"Should find at least 1 reference to Alpha.length(). "
				+ "Got: " + describeMatches(ktRefs));
	}

	@Test
	public void testOverloadedMethodReturnType() throws CoreException {
		// Overloaded methods returning different types — overload
		// resolution by arity determines the receiver type
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exproverload");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exproverload/Defs.kt",
				"package exproverload\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun act(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun act(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Factory {\n"
				+ "    fun create(): Alpha = Alpha()\n"
				+ "    fun create(name: String): Beta = Beta()\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exproverload/Caller.kt",
				"package exproverload\n"
				+ "\n"
				+ "fun useOverloads() {\n"
				+ "    val f: Factory = Factory()\n"
				+ "\n"
				+ "    // create() returns Alpha — should match\n"
				+ "    f.create().act(1)\n"
				+ "    f.create().act(2)\n"
				+ "\n"
				+ "    // create(name) returns Beta — should NOT match\n"
				+ "    f.create(\"x\").act(3)\n"
				+ "    f.create(\"y\").act(4)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"exproverload.Alpha.act", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find exactly 2 references to Alpha.act() "
				+ "via overloaded factory.create(). Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testMixedReceiverPatterns() throws CoreException {
		// Mix: direct val, constructor, factory chain — all correctly
		// filtered against a single qualified method search
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprmix");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprmix/Defs.kt",
				"package exprmix\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun act(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun act(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class AlphaFactory {\n"
				+ "    fun build(): Alpha = Alpha()\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprmix/Caller.kt",
				"package exprmix\n"
				+ "\n"
				+ "fun mixedPatterns() {\n"
				+ "    val a: Alpha = Alpha()\n"
				+ "    val b: Beta = Beta()\n"
				+ "    val f: AlphaFactory = AlphaFactory()\n"
				+ "\n"
				+ "    // Direct val — should match\n"
				+ "    a.act(1)\n"
				+ "\n"
				+ "    // Constructor — should match\n"
				+ "    Alpha().act(2)\n"
				+ "\n"
				+ "    // Factory chain — should match\n"
				+ "    f.build().act(3)\n"
				+ "\n"
				+ "    // Wrong type — should NOT match\n"
				+ "    b.act(4)\n"
				+ "    Beta().act(5)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"exprmix.Alpha.act", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(3, ktRefs.size(),
				"Should find exactly 3 Kotlin references to "
				+ "Alpha.act() (direct, constructor, factory chain), "
				+ "filtering out Beta.act() calls. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testOverloadByArgumentTypeResolution()
			throws CoreException {
		// Two overloads with the same arity but different parameter types
		// returning different types. Argument type resolution must
		// distinguish them.
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/exprargtype");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprargtype/Defs.kt",
				"package exprargtype\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun compute(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun compute(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Converter {\n"
				+ "    fun transform(value: String): Alpha = Alpha()\n"
				+ "    fun transform(value: Int): Beta = Beta()\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprargtype/Caller.kt",
				"package exprargtype\n"
				+ "\n"
				+ "fun useArgTypeOverloads() {\n"
				+ "    val c: Converter = Converter()\n"
				+ "\n"
				+ "    // transform(String) returns Alpha — should match\n"
				+ "    c.transform(\"abc\").compute(1)\n"
				+ "    c.transform(\"def\").compute(2)\n"
				+ "\n"
				+ "    // transform(Int) returns Beta — should NOT match\n"
				+ "    c.transform(42).compute(3)\n"
				+ "    c.transform(99).compute(4)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"exprargtype.Alpha.compute", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find exactly 2 references to Alpha.compute() "
				+ "via argument-type-resolved overload "
				+ "transform(String). Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testOverloadByNamedArgumentResolution()
			throws CoreException {
		// Overloads disambiguated by named arguments
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/exprnamed");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprnamed/Defs.kt",
				"package exprnamed\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun execute(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun execute(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Builder {\n"
				+ "    fun make(label: String): Alpha = Alpha()\n"
				+ "    fun make(count: String): Beta = Beta()\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprnamed/Caller.kt",
				"package exprnamed\n"
				+ "\n"
				+ "fun useNamedArgs() {\n"
				+ "    val b: Builder = Builder()\n"
				+ "\n"
				+ "    // make(label=) returns Alpha — should match\n"
				+ "    b.make(label = \"x\").execute(1)\n"
				+ "    b.make(label = \"y\").execute(2)\n"
				+ "\n"
				+ "    // make(count=) returns Beta — should NOT match\n"
				+ "    b.make(count = \"z\").execute(3)\n"
				+ "    b.make(count = \"w\").execute(4)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"exprnamed.Alpha.execute", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find exactly 2 references to Alpha.execute() "
				+ "via named-argument-resolved overload "
				+ "make(label=). Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testStarImportReceiverResolution()
			throws CoreException {
		// Star imports in the Kotlin file — exercises ImportResolver's
		// star import path for type resolution
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/exprstar/defs");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprstar/defs/Defs.kt",
				"package exprstar.defs\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun perform(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun perform(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class AlphaFactory {\n"
				+ "    fun build(): Alpha = Alpha()\n"
				+ "}\n");
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/exprstar/caller");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
				+ "/src/exprstar/caller/Caller.kt",
				"package exprstar.caller\n"
				+ "\n"
				+ "import exprstar.defs.*\n"
				+ "\n"
				+ "fun useStarImported() {\n"
				+ "    val f: AlphaFactory = AlphaFactory()\n"
				+ "    val b: Beta = Beta()\n"
				+ "\n"
				+ "    // build() returns Alpha via star-imported type\n"
				+ "    f.build().perform(1)\n"
				+ "    f.build().perform(2)\n"
				+ "\n"
				+ "    // Direct Beta — should NOT match\n"
				+ "    b.perform(3)\n"
				+ "    b.perform(4)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"exprstar.defs.Alpha.perform", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find exactly 2 references to Alpha.perform() "
				+ "via star-imported factory. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testBooleanExpressionArgumentType()
			throws CoreException {
		// Arguments that are boolean expressions (||, &&, ==) exercise
		// the expression hierarchy pass-through methods
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/exprbool");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprbool/Defs.kt",
				"package exprbool\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun trigger(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun trigger(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Selector {\n"
				+ "    fun pick(flag: Boolean): Alpha = Alpha()\n"
				+ "    fun pick(value: Int): Beta = Beta()\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprbool/Caller.kt",
				"package exprbool\n"
				+ "\n"
				+ "fun useBoolArgs() {\n"
				+ "    val s: Selector = Selector()\n"
				+ "    val x: Int = 0\n"
				+ "\n"
				+ "    // pick(boolean expr) returns Alpha\n"
				+ "    s.pick(x > 0 || x < -1).trigger(1)\n"
				+ "    s.pick(x == 0 && x != 1).trigger(2)\n"
				+ "\n"
				+ "    // pick(int expr) returns Beta\n"
				+ "    s.pick(x).trigger(3)\n"
				+ "    s.pick(x).trigger(4)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"exprbool.Alpha.trigger", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find exactly 2 references to Alpha.trigger() "
				+ "via boolean expression argument. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testAsExpressionCastReceiver()
			throws CoreException {
		// "as" cast expression: (obj as Alpha).doWork()
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/exprcast");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprcast/Defs.kt",
				"package exprcast\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun operate(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun operate(x: Int) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprcast/Caller.kt",
				"package exprcast\n"
				+ "\n"
				+ "fun useCasts() {\n"
				+ "    val obj: Any = Alpha()\n"
				+ "\n"
				+ "    // Cast to Alpha — should match\n"
				+ "    (obj as Alpha).operate(1)\n"
				+ "    (obj as Alpha).operate(2)\n"
				+ "\n"
				+ "    // Cast to Beta — should NOT match\n"
				+ "    (obj as Beta).operate(3)\n"
				+ "    (obj as Beta).operate(4)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"exprcast.Alpha.operate", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find exactly 2 references to Alpha.operate() "
				+ "via 'as' cast expression. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testDefaultParamOverloadResolution()
			throws CoreException {
		// Method with default params: create(a, b = "x") can be called
		// as create(a) or create(a, b). A separate overload create()
		// returns a different type.
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/exprdefault");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprdefault/Defs.kt",
				"package exprdefault\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun invoke(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun invoke(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Maker {\n"
				+ "    fun produce(): Alpha = Alpha()\n"
				+ "    fun produce(name: String, flag: Boolean = true): Beta"
				+ " = Beta()\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprdefault/Caller.kt",
				"package exprdefault\n"
				+ "\n"
				+ "fun useDefaults() {\n"
				+ "    val m: Maker = Maker()\n"
				+ "\n"
				+ "    // produce() returns Alpha — should match\n"
				+ "    m.produce().invoke(1)\n"
				+ "    m.produce().invoke(2)\n"
				+ "\n"
				+ "    // produce(name) returns Beta — should NOT match\n"
				+ "    m.produce(\"x\").invoke(3)\n"
				+ "    m.produce(\"y\", false).invoke(4)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"exprdefault.Alpha.invoke", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find exactly 2 references to Alpha.invoke() "
				+ "via produce() overload, filtering out "
				+ "produce(name) with default params. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testExpressionArgumentTypeResolution()
			throws CoreException {
		// Argument is a chained expression — exercises
		// visitPostfixUnaryExpression through resolve(expression)
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/exprarg");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprarg/Defs.kt",
				"package exprarg\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun run(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun run(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Provider {\n"
				+ "    fun getAlpha(): Alpha = Alpha()\n"
				+ "    fun getBeta(): Beta = Beta()\n"
				+ "}\n"
				+ "\n"
				+ "class Router {\n"
				+ "    fun route(target: Alpha): Alpha = target\n"
				+ "    fun route(target: Beta): Beta = target\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprarg/Caller.kt",
				"package exprarg\n"
				+ "\n"
				+ "fun useExprArg() {\n"
				+ "    val p: Provider = Provider()\n"
				+ "    val r: Router = Router()\n"
				+ "\n"
				+ "    // route(p.getAlpha()) returns Alpha\n"
				+ "    r.route(p.getAlpha()).run(1)\n"
				+ "    r.route(p.getAlpha()).run(2)\n"
				+ "\n"
				+ "    // route(p.getBeta()) returns Beta\n"
				+ "    r.route(p.getBeta()).run(3)\n"
				+ "    r.route(p.getBeta()).run(4)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"exprarg.Alpha.run", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find exactly 2 references to Alpha.run() "
				+ "via expression-argument overload resolution. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testInheritedMethodResolution()
			throws CoreException {
		// Method inherited from supertype — overload resolution must
		// walk the type hierarchy
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/exprinherit");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprinherit/Defs.kt",
				"package exprinherit\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun work(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun work(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "open class BaseFactory {\n"
				+ "    open fun build(): Alpha = Alpha()\n"
				+ "}\n"
				+ "\n"
				+ "class DerivedFactory : BaseFactory() {\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprinherit/Caller.kt",
				"package exprinherit\n"
				+ "\n"
				+ "fun useInherited() {\n"
				+ "    val d: DerivedFactory = DerivedFactory()\n"
				+ "    val b: Beta = Beta()\n"
				+ "\n"
				+ "    // build() inherited from BaseFactory → Alpha\n"
				+ "    d.build().work(1)\n"
				+ "    d.build().work(2)\n"
				+ "\n"
				+ "    // Direct Beta call — should NOT match\n"
				+ "    b.work(3)\n"
				+ "    b.work(4)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"exprinherit.Alpha.work", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find exactly 2 references to Alpha.work() "
				+ "via inherited build() on DerivedFactory. Got: "
				+ describeMatches(ktRefs));
	}

	// ---- Coverage gap tests: ExpressionTypeResolver paths ----

	@Test
	public void testWhenExpressionAsReceiver() throws CoreException {
		// when expression result used as receiver should resolve type
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprwhen");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprwhen/Defs.kt",
				"package exprwhen\n"
				+ "class Alpha {\n"
				+ "    fun process(): String = \"\"\n"
				+ "}\n"
				+ "class Beta {\n"
				+ "    fun process(): String = \"\"\n"
				+ "}\n"
				+ "fun useWhen(flag: Int) {\n"
				+ "    val result = when(flag) {\n"
				+ "        1 -> Alpha()\n"
				+ "        else -> Alpha()\n"
				+ "    }\n"
				+ "    result.process()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"exprwhen.Alpha.process", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertTrue(ktRefs.size() >= 1,
				"Should find Alpha.process() via when expression "
						+ "receiver. Got: "
						+ describeMatches(ktRefs));
	}

	@Test
	public void testSuperExpressionReceiver() throws CoreException {
		// super.method() should resolve receiver to supertype
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprsuper");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprsuper/Defs.kt",
				"package exprsuper\n"
				+ "open class Parent {\n"
				+ "    open fun greet(): String = \"hello\"\n"
				+ "}\n"
				+ "class Child : Parent() {\n"
				+ "    override fun greet(): String {\n"
				+ "        return super.greet() + \" world\"\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"exprsuper.Parent.greet", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertTrue(ktRefs.size() >= 1,
				"Should find Parent.greet() via super.greet() "
						+ "call. Got: "
						+ describeMatches(ktRefs));
	}

	@Test
	public void testLongLiteralArgumentType() throws CoreException {
		// 123L argument should resolve to Long for overload resolution
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprlit");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprlit/Defs.kt",
				"package exprlit\n"
				+ "class Recorder {\n"
				+ "    fun record(value: Int): String = \"int\"\n"
				+ "    fun record(value: Long): String = \"long\"\n"
				+ "    fun record(value: Double): String = \"dbl\"\n"
				+ "    fun record(value: String): String = \"str\"\n"
				+ "}\n"
				+ "fun useLiterals() {\n"
				+ "    val rec = Recorder()\n"
				+ "    rec.record(42)\n"
				+ "    rec.record(123L)\n"
				+ "    rec.record(3.14)\n"
				+ "    rec.record(\"hello\")\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"exprlit.Recorder.record", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertTrue(ktRefs.size() >= 4,
				"Should find at least 4 Recorder.record() calls "
						+ "with different literal types. Got: "
						+ describeMatches(ktRefs));
	}

	@Test
	public void testWithScopeFunctionReceiver() throws CoreException {
		// with(obj) { method() } should bind this to obj's type
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprwith");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprwith/Defs.kt",
				"package exprwith\n"
				+ "class Config {\n"
				+ "    fun setPort(p: Int) {}\n"
				+ "    fun setHost(h: String) {}\n"
				+ "}\n"
				+ "fun configure() {\n"
				+ "    val cfg = Config()\n"
				+ "    with(cfg) {\n"
				+ "        setPort(8080)\n"
				+ "        setHost(\"localhost\")\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"exprwith.Config.setPort", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertTrue(ktRefs.size() >= 1,
				"Should find Config.setPort() via with(cfg) "
						+ "scope. Got: "
						+ describeMatches(ktRefs));
	}

	@Test
	public void testConstructorDelegationInCallHierarchy()
			throws CoreException {
		// class Foo : Bar() should report Bar as a callee
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/ctordel");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/ctordel/Defs.kt",
				"package ctordel\n"
				+ "open class Base(val name: String)\n"
				+ "class Derived(name: String) : Base(name) {\n"
				+ "    fun work() {}\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for type references to Base — constructor
		// delegation should produce a reference
		List<SearchMatch> refs = TestHelpers.searchTypeReferences(
				"Base", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertTrue(ktRefs.size() >= 1,
				"Should find Base type ref from constructor "
						+ "delegation. Got: "
						+ describeMatches(ktRefs));
	}

	@Test
	public void testEnumTypeIndexedWithCorrectSuffix()
			throws CoreException {
		// Enum types should be indexed with ENUM_SUFFIX
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/enumsuf");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/enumsuf/Defs.kt",
				"package enumsuf\n"
				+ "enum class Status { ACTIVE, INACTIVE }\n"
				+ "interface Checkable {\n"
				+ "    fun check(): Boolean\n"
				+ "}\n"
				+ "class StatusChecker : Checkable {\n"
				+ "    override fun check() = true\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for implementors of Checkable — should find
		// StatusChecker
		List<SearchMatch> impls = TestHelpers.searchImplementors(
				"Checkable", project);
		List<SearchMatch> ktImpls = TestHelpers.filterKotlinMatches(
				impls);
		assertTrue(ktImpls.size() >= 1,
				"Should find StatusChecker implementing Checkable");

		// Search for Status type — should be findable
		List<SearchMatch> types = TestHelpers.searchKotlinTypes(
				"Status", project);
		assertTrue(types.size() >= 1,
				"Should find enum class Status");
	}

	@Test
	public void testStarImportFallbackResolution()
			throws CoreException {
		// Types brought in via star import should be resolvable
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/starimport");
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/starimport/models");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/starimport/models/Widget.kt",
				"package starimport.models\n"
				+ "class Widget {\n"
				+ "    fun render(): String = \"\"\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/starimport/Usage.kt",
				"package starimport\n"
				+ "import starimport.models.*\n"
				+ "fun draw() {\n"
				+ "    val w = Widget()\n"
				+ "    w.render()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"starimport.models.Widget.render", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertTrue(ktRefs.size() >= 1,
				"Should find Widget.render() via star import "
						+ "resolution. Got: "
						+ describeMatches(ktRefs));
	}

	@Test
	public void testMethodReturningPrimitiveTypesInChain()
			throws CoreException {
		// Exercises ExpressionTypeResolver.resolveTypeName for
		// each primitive return type in chained calls. Methods
		// returning Int/Long/Double/Float/Boolean/String/Char
		// must resolve correctly for the next call in the chain.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprprim");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprprim/Defs.kt",
				"package exprprim\n"
				+ "class Converter {\n"
				+ "    fun toInt(): Int = 0\n"
				+ "    fun toLong(): Long = 0L\n"
				+ "    fun toDouble(): Double = 0.0\n"
				+ "    fun toFloat(): Float = 0.0f\n"
				+ "    fun toBool(): Boolean = true\n"
				+ "    fun toStr(): String = \"\"\n"
				+ "    fun toChar(): Char = 'a'\n"
				+ "    fun toByte(): Byte = 0\n"
				+ "    fun toShort(): Short = 0\n"
				+ "}\n"
				+ "fun useChains() {\n"
				+ "    val c = Converter()\n"
				+ "    c.toInt().plus(1)\n"
				+ "    c.toLong().plus(1L)\n"
				+ "    c.toDouble().plus(1.0)\n"
				+ "    c.toFloat().plus(1.0f)\n"
				+ "    c.toBool().not()\n"
				+ "    c.toStr().length\n"
				+ "    c.toChar().isUpperCase()\n"
				+ "    c.toByte().toInt()\n"
				+ "    c.toShort().toInt()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Verify that chained calls on primitive return types
		// are found. The key test: searching for
		// Converter.toStr should find the call because the
		// ExpressionTypeResolver resolves the chain correctly.
		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"exprprim.Converter.toStr", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertTrue(ktRefs.size() >= 1,
				"Should find Converter.toStr() in chain. Got: "
						+ describeMatches(ktRefs));

		// Also verify toInt chain works
		List<SearchMatch> intRefs =
				TestHelpers.searchQualifiedMethodReferences(
						"exprprim.Converter.toInt", project);
		List<SearchMatch> ktIntRefs = TestHelpers
				.filterKotlinMatches(intRefs);
		assertTrue(ktIntRefs.size() >= 1,
				"Should find Converter.toInt() in chain. Got: "
						+ describeMatches(ktIntRefs));
	}

	@Test
	public void testChainedLetReturnsReceiverType()
			throws CoreException {
		// Alpha().let { it }.doWork() — let returns R (last expr)
		// which is the receiver itself (Alpha), so doWork receiver
		// is Alpha. Exercises ExpressionTypeResolver lambda scope
		// tracking in walkSuffixes.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprlet");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprlet/Defs.kt",
				"package exprlet\n"
				+ "class Alpha {\n"
				+ "    fun doWork(): String = \"\"\n"
				+ "}\n"
				+ "class Beta {\n"
				+ "    fun doWork(): String = \"\"\n"
				+ "}\n"
				+ "fun useLetChain() {\n"
				+ "    val a = Alpha()\n"
				+ "    a.let { it }.doWork()\n"
				+ "    a.also { println(it) }.doWork()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"exprlet.Alpha.doWork", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertTrue(ktRefs.size() >= 2,
				"Should find Alpha.doWork() via let/also chain "
						+ "receiver. Got: "
						+ describeMatches(ktRefs));
	}

	@Test
	public void testPropertyAccessOnMethodResult()
			throws CoreException {
		// factory.create().name — member access (not a call) on
		// method return value. Exercises resolveMemberType in
		// ExpressionTypeResolver.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprfacprop");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprfacprop/Defs.kt",
				"package exprfacprop\n"
				+ "class User {\n"
				+ "    val name: String = \"\"\n"
				+ "    val age: Int = 0\n"
				+ "}\n"
				+ "class UserFactory {\n"
				+ "    fun create(): User = User()\n"
				+ "}\n"
				+ "fun useFactory() {\n"
				+ "    val f = UserFactory()\n"
				+ "    val n = f.create().name\n"
				+ "    val a = f.create().age\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchFieldReferences("name", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertTrue(ktRefs.size() >= 1,
				"Should find User.name via factory.create().name "
						+ "chain. Got: "
						+ describeMatches(ktRefs));
	}

	@Test
	public void testIfExpressionCommonSupertypeAsReceiver()
			throws CoreException {
		// if/else branches return different types that share a common
		// supertype. The common supertype should be used for receiver
		// type verification.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/ifsuper");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/ifsuper/Defs.kt",
				"package ifsuper\n"
				+ "\n"
				+ "open class Animal {\n"
				+ "    fun speak(): String = \"\"\n"
				+ "}\n"
				+ "class Dog : Animal()\n"
				+ "class Cat : Animal()\n"
				+ "\n"
				+ "class Unrelated {\n"
				+ "    fun speak(): String = \"\"\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/ifsuper/Caller.kt",
				"package ifsuper\n"
				+ "\n"
				+ "fun callIfBranch(flag: Boolean) {\n"
				+ "    val a = if (flag) Dog() else Cat()\n"
				+ "    a.speak()\n"
				+ "    val u = Unrelated()\n"
				+ "    u.speak()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"ifsuper.Animal.speak", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		// a.speak() should match because Dog and Cat are subtypes
		// of Animal. u.speak() should NOT match.
		assertTrue(ktRefs.size() >= 1,
				"Should find Animal.speak() via if-expression "
						+ "common supertype. Got: "
						+ describeMatches(ktRefs));
	}

	@Test
	public void testTryExpressionAsReceiver() throws CoreException {
		// try block result used as receiver
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/tryrecv");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/tryrecv/Defs.kt",
				"package tryrecv\n"
				+ "\n"
				+ "class Config {\n"
				+ "    fun getValue(): String = \"\"\n"
				+ "}\n"
				+ "class Other {\n"
				+ "    fun getValue(): String = \"\"\n"
				+ "}\n"
				+ "fun loadConfig(): Config = Config()\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/tryrecv/Caller.kt",
				"package tryrecv\n"
				+ "\n"
				+ "fun useTry() {\n"
				+ "    val c = try { loadConfig() } "
				+ "catch (e: Exception) { Config() }\n"
				+ "    c.getValue()\n"
				+ "    val o = Other()\n"
				+ "    o.getValue()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"tryrecv.Config.getValue", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		// c.getValue() should match (try returns Config)
		assertTrue(ktRefs.size() >= 1,
				"Should find Config.getValue() via try-expression "
						+ "receiver. Got: "
						+ describeMatches(ktRefs));
	}

	@Test
	public void testNotNullAssertionTypeStripping()
			throws CoreException {
		// nullable!!.method() should resolve receiver type correctly
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/notnull");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/notnull/Defs.kt",
				"package notnull\n"
				+ "\n"
				+ "class Target {\n"
				+ "    fun action(): String = \"\"\n"
				+ "}\n"
				+ "class Wrong {\n"
				+ "    fun action(): String = \"\"\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/notnull/Caller.kt",
				"package notnull\n"
				+ "\n"
				+ "fun callNotNull(t: Target?) {\n"
				+ "    t!!.action()\n"
				+ "    val w = Wrong()\n"
				+ "    w.action()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"notnull.Target.action", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		// t!!.action() should match Target.action
		assertTrue(ktRefs.size() >= 1,
				"Should find Target.action() via !! assertion. "
						+ "Got: " + describeMatches(ktRefs));
	}

	@Test
	public void testNumericWideningOverloadResolution()
			throws CoreException {
		// When a method has overloads for Long and Int,
		// passing an Int literal should select the Int overload
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/numwiden");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/numwiden/Defs.kt",
				"package numwiden\n"
				+ "\n"
				+ "class Converter {\n"
				+ "    fun convert(x: Int): String = \"\"\n"
				+ "    fun convert(x: Long): String = \"\"\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/numwiden/Caller.kt",
				"package numwiden\n"
				+ "\n"
				+ "fun doConvert() {\n"
				+ "    val c = Converter()\n"
				+ "    c.convert(42)\n"
				+ "    c.convert(42L)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"numwiden.Converter.convert", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertEquals(2, ktRefs.size(),
				"Should find both convert(Int) and convert(Long) "
						+ "references. Got: "
						+ describeMatches(ktRefs));
	}

	@Test
	public void testNullableParameterTypeResolution()
			throws CoreException {
		// Parameters declared with nullable types (String?) should
		// resolve correctly after !! assertion or safe call
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/nullparam");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/nullparam/Defs.kt",
				"package nullparam\n"
				+ "\n"
				+ "class Processor {\n"
				+ "    fun handle(input: String): Int = 0\n"
				+ "}\n"
				+ "class Other {\n"
				+ "    fun handle(input: String): Int = 0\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/nullparam/Caller.kt",
				"package nullparam\n"
				+ "\n"
				+ "fun useNullable(p: Processor?, o: Other?) {\n"
				+ "    p!!.handle(\"a\")\n"
				+ "    p?.handle(\"b\")\n"
				+ "    o!!.handle(\"c\")\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"nullparam.Processor.handle", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		// p!!.handle() and p?.handle() should match
		assertTrue(ktRefs.size() >= 2,
				"Should find Processor.handle() via !! and ?. "
						+ "Got: " + describeMatches(ktRefs));
	}

	@Test
	public void testWhenExpressionCommonSupertypeAsReceiver()
			throws CoreException {
		// when expression branches return different types that share
		// a common supertype
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/whensuper");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/whensuper/Defs.kt",
				"package whensuper\n"
				+ "\n"
				+ "open class Shape {\n"
				+ "    fun area(): Double = 0.0\n"
				+ "}\n"
				+ "class Rect : Shape()\n"
				+ "class Oval : Shape()\n"
				+ "class Tri : Shape()\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/whensuper/Caller.kt",
				"package whensuper\n"
				+ "\n"
				+ "fun calcArea(type: Int): Double {\n"
				+ "    val s = when (type) {\n"
				+ "        1 -> Rect()\n"
				+ "        2 -> Oval()\n"
				+ "        else -> Tri()\n"
				+ "    }\n"
				+ "    return s.area()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"whensuper.Shape.area", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertTrue(ktRefs.size() >= 1,
				"Should find Shape.area() via when-expression "
						+ "common supertype. Got: "
						+ describeMatches(ktRefs));
	}

	@Test
	public void testIfBlockBodyTypeResolution()
			throws CoreException {
		// if/when with block bodies should resolve the last
		// expression in the block
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/ifblock");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/ifblock/Defs.kt",
				"package ifblock\n"
				+ "\n"
				+ "class Processor {\n"
				+ "    fun run(): String = \"\"\n"
				+ "}\n"
				+ "class Other {\n"
				+ "    fun run(): String = \"\"\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/ifblock/Caller.kt",
				"package ifblock\n"
				+ "\n"
				+ "fun useBlockBody(flag: Boolean) {\n"
				+ "    val p = if (flag) {\n"
				+ "        val tmp = Processor()\n"
				+ "        tmp\n"
				+ "    } else {\n"
				+ "        Processor()\n"
				+ "    }\n"
				+ "    p.run()\n"
				+ "    val o = Other()\n"
				+ "    o.run()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"ifblock.Processor.run", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertTrue(ktRefs.size() >= 1,
				"Should find Processor.run() via if-block body. "
						+ "Got: " + describeMatches(ktRefs));
	}

	@Test
	public void testObjectLiteralTypeResolution()
			throws CoreException {
		// object : Foo() { }.method() should resolve to Foo
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/objlit");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/objlit/Defs.kt",
				"package objlit\n"
				+ "\n"
				+ "open class Handler {\n"
				+ "    open fun handle(): String = \"\"\n"
				+ "}\n"
				+ "class OtherHandler {\n"
				+ "    fun handle(): String = \"\"\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/objlit/Caller.kt",
				"package objlit\n"
				+ "\n"
				+ "fun useObjectLiteral() {\n"
				+ "    val h = object : Handler() {\n"
				+ "        override fun handle(): String = \"custom\"\n"
				+ "    }\n"
				+ "    h.handle()\n"
				+ "    val o = OtherHandler()\n"
				+ "    o.handle()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"objlit.Handler.handle", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertTrue(ktRefs.size() >= 1,
				"Should find Handler.handle() via object literal. "
						+ "Got: " + describeMatches(ktRefs));
	}

	@Test
	public void testPropertyDelegateReferenceIndexed()
			throws CoreException {
		// `by lazy { }` should index `lazy` as METHOD_REF
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/propdelg");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/propdelg/Defs.kt",
				"package propdelg\n"
				+ "\n"
				+ "fun <T> myLazy(init: () -> T): Lazy<T> "
				+ "= lazy(init)\n"
				+ "fun <T> myOther(init: () -> T): Lazy<T> "
				+ "= lazy(init)\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/propdelg/Caller.kt",
				"package propdelg\n"
				+ "\n"
				+ "val prop1: String by myLazy { \"hello\" }\n"
				+ "val prop2: String by myLazy { \"world\" }\n"
				+ "val prop3: String by myOther { \"other\" }\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchMethodReferences("myLazy",
						project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertEquals(2, ktRefs.size(),
				"Should find 2 references to myLazy via property "
						+ "delegates. Got: "
						+ describeMatches(ktRefs));
	}

	@Test
	public void testElvisExpressionAsReceiver() throws CoreException {
		// (x ?: y).method() — elvis result type drives receiver
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprelvis");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprelvis/Defs.kt",
				"package exprelvis\n"
				+ "class Config {\n"
				+ "    fun load(): String = \"\"\n"
				+ "}\n"
				+ "class Other {\n"
				+ "    fun load(): String = \"\"\n"
				+ "}\n"
				+ "fun getConfig(c: Config?, fallback: Config) {\n"
				+ "    val result = c ?: fallback\n"
				+ "    result.load()\n"
				+ "    result.load()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"exprelvis.Config.load", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertEquals(2, ktRefs.size(),
				"Should find Config.load() via elvis expression. "
						+ "Got: " + describeMatches(ktRefs));
	}

	@Test
	public void testParenthesizedExpressionAsReceiver()
			throws CoreException {
		// (expr).method() — parenthesized expression as receiver
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprparen");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprparen/Defs.kt",
				"package exprparen\n"
				+ "class Builder {\n"
				+ "    fun build(): String = \"\"\n"
				+ "}\n"
				+ "class Other {\n"
				+ "    fun build(): String = \"\"\n"
				+ "}\n"
				+ "fun useParen() {\n"
				+ "    val b = Builder()\n"
				+ "    (b).build()\n"
				+ "    (b).build()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"exprparen.Builder.build", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertEquals(2, ktRefs.size(),
				"Should find Builder.build() via parenthesized "
						+ "expression. Got: "
						+ describeMatches(ktRefs));
	}

	@Test
	public void testIndexingSuffixReceiver() throws CoreException {
		// list[0].method() — indexing suffix as receiver
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprindex");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprindex/Defs.kt",
				"package exprindex\n"
				+ "class Item {\n"
				+ "    fun process(x: Int) {}\n"
				+ "}\n"
				+ "class Other {\n"
				+ "    fun process(x: Int) {}\n"
				+ "}\n"
				+ "fun useIndexing() {\n"
				+ "    val items: List<Item> = listOf()\n"
				+ "    items[0].process(1)\n"
				+ "    items[1].process(2)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"exprindex.Item.process", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertEquals(2, ktRefs.size(),
				"Should find Item.process() via list[i] indexing. "
						+ "Got: " + describeMatches(ktRefs));
	}

	@Test
	public void testAsCastExpressionReceiver() throws CoreException {
		// (obj as Foo).method() — cast expression as receiver
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprascast");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprascast/Defs.kt",
				"package exprascast\n"
				+ "open class Base {\n"
				+ "    open fun action() {}\n"
				+ "}\n"
				+ "class Derived : Base() {\n"
				+ "    fun special(x: Int) {}\n"
				+ "}\n"
				+ "class Other : Base() {\n"
				+ "    fun special(x: Int) {}\n"
				+ "}\n"
				+ "fun useCast(b: Base) {\n"
				+ "    (b as Derived).special(1)\n"
				+ "    (b as Derived).special(2)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"exprascast.Derived.special", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertEquals(2, ktRefs.size(),
				"Should find Derived.special() via (b as Derived) "
						+ "cast. Got: " + describeMatches(ktRefs));
	}

	@Test
	public void testLongAndFloatLiteralArgumentResolution()
			throws CoreException {
		// Exercises Long/Float/Double literal type resolution
		// as arguments to receiver-verified methods
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprlit");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprlit/Defs.kt",
				"package exprlit\n"
				+ "\n"
				+ "class Recorder {\n"
				+ "    fun recordLong(a: Long) {}\n"
				+ "    fun recordDouble(a: Double) {}\n"
				+ "    fun recordFloat(a: Float) {}\n"
				+ "    fun recordChar(a: Char) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Other {\n"
				+ "    fun recordLong(a: Long) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprlit/Caller.kt",
				"package exprlit\n"
				+ "\n"
				+ "fun useRecorder() {\n"
				+ "    val r: Recorder = Recorder()\n"
				+ "    r.recordLong(42L)\n"
				+ "    r.recordLong(99L)\n"
				+ "    r.recordDouble(3.14)\n"
				+ "    r.recordFloat(1.5f)\n"
				+ "    r.recordChar('x')\n"
				+ "    val o: Other = Other()\n"
				+ "    o.recordLong(1L)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"exprlit.Recorder.recordLong", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertEquals(2, ktRefs.size(),
				"Should find 2 Recorder.recordLong() calls, "
						+ "filtering out Other. Got: "
						+ describeMatches(ktRefs));
	}

	@Test
	public void testComparisonOperatorProducesBoolean()
			throws CoreException {
		// x > y produces Boolean — exercises comparison visitor
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprcmp");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprcmp/Defs.kt",
				"package exprcmp\n"
				+ "class Validator {\n"
				+ "    fun check(flag: Boolean) {}\n"
				+ "}\n"
				+ "fun useComparison() {\n"
				+ "    val v = Validator()\n"
				+ "    v.check(1 > 0)\n"
				+ "    v.check(2 == 2)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"exprcmp.Validator.check", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertEquals(2, ktRefs.size(),
				"Should find Validator.check() with Boolean args "
						+ "from comparisons. Got: "
						+ describeMatches(ktRefs));
	}

	@Test
	public void testMapIndexingReturnsValueType() throws CoreException {
		// map["key"].method() — exercises getElementType for Map
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprmap");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprmap/Defs.kt",
				"package exprmap\n"
				+ "\n"
				+ "class Config {\n"
				+ "    fun getValue(): String = \"\"\n"
				+ "}\n"
				+ "\n"
				+ "class Other {\n"
				+ "    fun getValue(): String = \"\"\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprmap/Caller.kt",
				"package exprmap\n"
				+ "\n"
				+ "fun useMap() {\n"
				+ "    val configs: Map<String, Config> = mapOf()\n"
				+ "    configs[\"a\"]?.getValue()\n"
				+ "    configs[\"b\"]?.getValue()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"exprmap.Config.getValue", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertEquals(2, ktRefs.size(),
				"Should find Config.getValue() via map[key] "
						+ "indexing. Got: "
						+ describeMatches(ktRefs));
	}

	@Test
	public void testCommonSupertypeWithUnrelatedTypes()
			throws CoreException {
		// if/else with unrelated types → common supertype is Any
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprunrel");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprunrel/Defs.kt",
				"package exprunrel\n"
				+ "\n"
				+ "class Cat {\n"
				+ "    fun meow(): String = \"\"\n"
				+ "}\n"
				+ "class Fish {\n"
				+ "    fun swim(): String = \"\"\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprunrel/Caller.kt",
				"package exprunrel\n"
				+ "\n"
				+ "fun useUnrelated(flag: Boolean) {\n"
				+ "    val x = if (flag) Cat() else Fish()\n"
				+ "    // x is Any — toString should match\n"
				+ "    x.toString()\n"
				+ "    x.hashCode()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Any.toString() should match since common supertype
		// is Any (unrelated Cat and Fish)
		List<SearchMatch> refs =
				TestHelpers.searchMethodReferences(
						"toString", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertTrue(ktRefs.size() >= 1,
				"Should find toString() via Any common "
						+ "supertype. Got: "
						+ describeMatches(ktRefs));
	}

	@Test
	public void testSafeCallChainReceiver() throws CoreException {
		// obj?.method()?.next() — safe call chain
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprsafe");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprsafe/Defs.kt",
				"package exprsafe\n"
				+ "class Node {\n"
				+ "    fun next(): Node? = null\n"
				+ "    fun value(): String = \"\"\n"
				+ "}\n"
				+ "class Other {\n"
				+ "    fun value(): String = \"\"\n"
				+ "}\n"
				+ "fun traverse(start: Node?) {\n"
				+ "    start?.next()?.value()\n"
				+ "    start?.next()?.value()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"exprsafe.Node.value", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertEquals(2, ktRefs.size(),
				"Should find Node.value() via safe call chain. "
						+ "Got: " + describeMatches(ktRefs));
	}

	@Test
	public void testLocalVariableTypeInferenceFromInitializer()
			throws CoreException {
		// val x = Foo() — type inferred from constructor call
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprinfer");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprinfer/Defs.kt",
				"package exprinfer\n"
				+ "\n"
				+ "class Service {\n"
				+ "    fun execute(cmd: String) {}\n"
				+ "}\n"
				+ "\n"
				+ "class OtherService {\n"
				+ "    fun execute(cmd: String) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprinfer/Caller.kt",
				"package exprinfer\n"
				+ "\n"
				+ "fun useInferred() {\n"
				+ "    val svc = Service()\n"
				+ "    val other = OtherService()\n"
				+ "    svc.execute(\"run\")\n"
				+ "    svc.execute(\"stop\")\n"
				+ "    other.execute(\"start\")\n"
				+ "    other.execute(\"halt\")\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"exprinfer.Service.execute", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertEquals(2, ktRefs.size(),
				"Should find 2 Service.execute() calls with "
						+ "type inferred from val svc = Service(), "
						+ "filtering out OtherService. Got: "
						+ describeMatches(ktRefs));
	}

	@Test
	public void testLocalVariableTypeInferenceFromMethodCall()
			throws CoreException {
		// val result = factory.create() — type inferred from method
		// return type
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprinfer2");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprinfer2/Defs.kt",
				"package exprinfer2\n"
				+ "\n"
				+ "class Widget {\n"
				+ "    fun render(mode: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class OtherWidget {\n"
				+ "    fun render(mode: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class WidgetFactory {\n"
				+ "    fun create(): Widget = Widget()\n"
				+ "}\n"
				+ "\n"
				+ "class OtherFactory {\n"
				+ "    fun create(): OtherWidget = OtherWidget()\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprinfer2/Caller.kt",
				"package exprinfer2\n"
				+ "\n"
				+ "fun useFactory() {\n"
				+ "    val factory: WidgetFactory = WidgetFactory()\n"
				+ "    val w = factory.create()\n"
				+ "    w.render(1)\n"
				+ "    w.render(2)\n"
				+ "    val otherFactory: OtherFactory = OtherFactory()\n"
				+ "    val ow = otherFactory.create()\n"
				+ "    ow.render(3)\n"
				+ "    ow.render(4)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"exprinfer2.Widget.render", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertEquals(2, ktRefs.size(),
				"Should find 2 Widget.render() calls with type "
						+ "inferred from factory.create(), filtering "
						+ "out OtherWidget. Got: "
						+ describeMatches(ktRefs));
	}

	@Test
	public void testCustomLambdaWithMultiMethodType()
			throws CoreException {
		// Exercises LambdaTypeResolver:244 loop with 2+ methods
		// on the receiver type — the method lookup iterates past
		// the first non-matching method to find the correct one
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprmulti");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprmulti/Defs.kt",
				"package exprmulti\n"
				+ "\n"
				+ "class Item {\n"
				+ "    fun label(): String = \"\"\n"
				+ "}\n"
				+ "\n"
				+ "class Other {\n"
				+ "    fun label(): String = \"\"\n"
				+ "}\n"
				+ "\n"
				+ "class Container {\n"
				+ "    fun first(selector: (Item) -> Boolean): Item"
				+ " = Item()\n"
				+ "    fun transform(mapper: (Item) -> String):"
				+ " String = \"\"\n"
				+ "    fun process(handler: (Item) -> Unit) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprmulti/Caller.kt",
				"package exprmulti\n"
				+ "\n"
				+ "fun useMultiMethod() {\n"
				+ "    val c: Container = Container()\n"
				+ "    // process is the 3rd method — loop must iterate\n"
				+ "    // past first() and transform() to find it\n"
				+ "    c.process { it.label() }\n"
				+ "    c.process { it.label() }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"exprmulti.Item.label", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertEquals(2, ktRefs.size(),
				"Should find 2 Item.label() via lambda in "
						+ "process() (3rd method on Container). Got: "
						+ describeMatches(ktRefs));
	}

	@Test
	public void testNoPackageTopLevelFunction() throws CoreException {
		// Exercises KotlinSearchParticipant:139 ternary —
		// top-level function without package
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/nopkg");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/nopkg/Defs.kt",
				"class NoPkgService {\n"
				+ "    fun execute(cmd: String) {}\n"
				+ "}\n"
				+ "\n"
				+ "class NoPkgOther {\n"
				+ "    fun execute(cmd: String) {}\n"
				+ "}\n"
				+ "\n"
				+ "fun callNoPkg() {\n"
				+ "    val s: NoPkgService = NoPkgService()\n"
				+ "    s.execute(\"run\")\n"
				+ "    s.execute(\"stop\")\n"
				+ "    val o: NoPkgOther = NoPkgOther()\n"
				+ "    o.execute(\"go\")\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"NoPkgService.execute", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertEquals(2, ktRefs.size(),
				"Should find 2 NoPkgService.execute() in "
						+ "no-package file. Got: "
						+ describeMatches(ktRefs));
	}

	@Test
	public void testIfElseExpressionAsDirectReceiver()
			throws CoreException {
		// (if (flag) Foo() else Bar()).method() — exercises
		// ExpressionTypeResolver:573 bodies.size() > 1
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprife");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprife/Defs.kt",
				"package exprife\n"
				+ "\n"
				+ "open class Shape {\n"
				+ "    fun area(): Double = 0.0\n"
				+ "}\n"
				+ "class Circle : Shape()\n"
				+ "class Square : Shape()\n"
				+ "\n"
				+ "class Other {\n"
				+ "    fun area(): Double = 0.0\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprife/Caller.kt",
				"package exprife\n"
				+ "\n"
				+ "fun useIfElseReceiver(flag: Boolean) {\n"
				+ "    (if (flag) Circle() else Square()).area()\n"
				+ "    (if (flag) Square() else Circle()).area()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"exprife.Shape.area", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertTrue(ktRefs.size() >= 2,
				"Should find Shape.area() via if-else expression "
						+ "as direct receiver. Got: "
						+ describeMatches(ktRefs));
	}

	@Test
	public void testNestedClassMethodScopePushesMultipleScopes()
			throws CoreException {
		// Exercises KotlinSearchParticipant:875 while loop —
		// nested class + function pushes 2+ scopes that must all
		// be popped in the finally block
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/exprnested");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprnested/Defs.kt",
				"package exprnested\n"
				+ "\n"
				+ "class Outer {\n"
				+ "    class Inner {\n"
				+ "        fun doWork(x: Int) {}\n"
				+ "    }\n"
				+ "}\n"
				+ "\n"
				+ "class OtherOuter {\n"
				+ "    class OtherInner {\n"
				+ "        fun doWork(x: Int) {}\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/exprnested/Caller.kt",
				"package exprnested\n"
				+ "\n"
				+ "class Host {\n"
				+ "    fun method1() {\n"
				+ "        val i: Outer.Inner = Outer.Inner()\n"
				+ "        i.doWork(1)\n"
				+ "        i.doWork(2)\n"
				+ "    }\n"
				+ "    fun method2() {\n"
				+ "        val o: OtherOuter.OtherInner "
				+ "= OtherOuter.OtherInner()\n"
				+ "        o.doWork(3)\n"
				+ "        o.doWork(4)\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search using simple method name — the nested class
		// receiver verification exercises the multi-scope push/pop
		List<SearchMatch> refs =
				TestHelpers.searchMethodReferences(
						"doWork", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		// All 4 doWork calls should be found (receiver verification
		// sees Inner vs OtherInner — both match doWork by name)
		assertTrue(ktRefs.size() >= 4,
				"Should find 4+ doWork() calls across nested "
						+ "classes with multi-scope push/pop. Got: "
						+ describeMatches(ktRefs));
	}
}
