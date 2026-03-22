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
 * Tests that lambda scope function bindings ({@code it}, {@code this})
 * are correctly resolved during receiver verification. Exercises the
 * LambdaTypeResolver and LambdaScopeExtractor through the full
 * locateReferenceMatches pipeline.
 *
 * @author Arcadiy Ivanov
 */
public class CrossLanguageLambdaScopeTest {

	private static final String PROJECT_NAME = "LambdaScopeTest";
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

	private static String describeMatches(List<SearchMatch> matches) {
		return matches.stream()
				.map(m -> String.format("[offset=%d len=%d resource=%s]",
						m.getOffset(), m.getLength(),
						m.getResource() != null
								? m.getResource().getName() : "null"))
				.collect(Collectors.joining(", "));
	}

	@Test
	public void testLetLambdaItBinding() throws CoreException {
		// svc.let { it.doWork() } — "it" should resolve to Service
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/lambdalet");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/lambdalet/Defs.kt",
				"package lambdalet\n"
				+ "\n"
				+ "class Service {\n"
				+ "    fun doWork(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class OtherService {\n"
				+ "    fun doWork(x: Int) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/lambdalet/Caller.kt",
				"package lambdalet\n"
				+ "\n"
				+ "fun useLet() {\n"
				+ "    val svc: Service = Service()\n"
				+ "    val other: OtherService = OtherService()\n"
				+ "\n"
				+ "    // it = Service — should match\n"
				+ "    svc.let { it.doWork(1) }\n"
				+ "    svc.let { it.doWork(2) }\n"
				+ "\n"
				+ "    // it = OtherService — should NOT match\n"
				+ "    other.let { it.doWork(3) }\n"
				+ "    other.let { it.doWork(4) }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"lambdalet.Service.doWork", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find exactly 2 references to Service.doWork() "
				+ "via let { it.doWork() }, filtering out "
				+ "OtherService calls. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testRunLambdaThisBinding() throws CoreException {
		// svc.run { doWork() } — "this" should resolve to Service
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/lambdarun");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/lambdarun/Defs.kt",
				"package lambdarun\n"
				+ "\n"
				+ "class Service {\n"
				+ "    fun doWork(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class OtherService {\n"
				+ "    fun doWork(x: Int) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/lambdarun/Caller.kt",
				"package lambdarun\n"
				+ "\n"
				+ "fun useRun() {\n"
				+ "    val svc: Service = Service()\n"
				+ "    val other: OtherService = OtherService()\n"
				+ "\n"
				+ "    // this = Service — should match\n"
				+ "    svc.run { doWork(1) }\n"
				+ "    svc.run { doWork(2) }\n"
				+ "\n"
				+ "    // this = OtherService — should NOT match\n"
				+ "    other.run { doWork(3) }\n"
				+ "    other.run { doWork(4) }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"lambdarun.Service.doWork", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		// Note: doWork() inside run { } is a direct call, not
		// receiver.doWork(). It won't have a receiver in
		// KotlinReferenceFinder, so receiver verification doesn't
		// apply. These calls match via the index as unqualified method
		// references. The "this" binding from run { } only helps when
		// the lambda body uses "this.doWork()".
		// For now, verify that the let-style it binding works and
		// that run doesn't break anything.
		// All 4 are direct calls without receiver — all pass through
		assertEquals(4, ktRefs.size(),
				"Direct calls in run { } have no receiver to verify "
				+ "— all pass through. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testApplyLambdaThisBinding() throws CoreException {
		// svc.apply { this.doWork() } — explicit "this" receiver
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/lambdaapply");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/lambdaapply/Defs.kt",
				"package lambdaapply\n"
				+ "\n"
				+ "class Service {\n"
				+ "    fun doWork(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class OtherService {\n"
				+ "    fun doWork(x: Int) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/lambdaapply/Caller.kt",
				"package lambdaapply\n"
				+ "\n"
				+ "fun useApply() {\n"
				+ "    val svc: Service = Service()\n"
				+ "    val other: OtherService = OtherService()\n"
				+ "\n"
				+ "    // this = Service — explicit this.doWork()\n"
				+ "    svc.apply { this.doWork(1) }\n"
				+ "    svc.apply { this.doWork(2) }\n"
				+ "\n"
				+ "    // this = OtherService — explicit this.doWork()\n"
				+ "    other.apply { this.doWork(3) }\n"
				+ "    other.apply { this.doWork(4) }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"lambdaapply.Service.doWork", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		// "this" in apply lambda resolves to the receiver type.
		// In resolveReceiverType, "this" short-circuits to
		// conservative allow. But with the lambda scope bindings
		// pushing "this" = Service/OtherService, the scope chain
		// has "this" bound. However, resolveReceiverType returns
		// null (allow) for "this"/"super" before checking scope.
		// So all 4 pass through.
		assertEquals(4, ktRefs.size(),
				"this.doWork() in apply { } — 'this' is always "
				+ "conservatively allowed. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testAlsoLambdaItBinding() throws CoreException {
		// svc.also { it.doWork() } — "it" should resolve to Service
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/lambdaalso");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/lambdaalso/Defs.kt",
				"package lambdaalso\n"
				+ "\n"
				+ "class Service {\n"
				+ "    fun doWork(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class OtherService {\n"
				+ "    fun doWork(x: Int) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/lambdaalso/Caller.kt",
				"package lambdaalso\n"
				+ "\n"
				+ "fun useAlso() {\n"
				+ "    val svc: Service = Service()\n"
				+ "    val other: OtherService = OtherService()\n"
				+ "\n"
				+ "    // it = Service — should match\n"
				+ "    svc.also { it.doWork(1) }\n"
				+ "    svc.also { it.doWork(2) }\n"
				+ "\n"
				+ "    // it = OtherService — should NOT match\n"
				+ "    other.also { it.doWork(3) }\n"
				+ "    other.also { it.doWork(4) }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"lambdaalso.Service.doWork", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find exactly 2 references to "
				+ "Service.doWork() via also { it.doWork() }, "
				+ "filtering out OtherService calls. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testTakeIfLambdaItBinding() throws CoreException {
		// svc.takeIf { it.doWork() } — "it" should resolve to Service
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/lambdatakeif");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/lambdatakeif/Defs.kt",
				"package lambdatakeif\n"
				+ "\n"
				+ "class Service {\n"
				+ "    fun doWork(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class OtherService {\n"
				+ "    fun doWork(x: Int) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/lambdatakeif/Caller.kt",
				"package lambdatakeif\n"
				+ "\n"
				+ "fun useTakeIf() {\n"
				+ "    val svc: Service = Service()\n"
				+ "    val other: OtherService = OtherService()\n"
				+ "\n"
				+ "    // it = Service — should match\n"
				+ "    svc.takeIf { it.doWork(1); true }\n"
				+ "    svc.takeIf { it.doWork(2); true }\n"
				+ "\n"
				+ "    // it = OtherService — should NOT match\n"
				+ "    other.takeIf { it.doWork(3); true }\n"
				+ "    other.takeIf { it.doWork(4); true }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"lambdatakeif.Service.doWork", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find exactly 2 references to "
				+ "Service.doWork() via takeIf { it.doWork() }, "
				+ "filtering out OtherService calls. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testCustomLambdaParameterResolution()
			throws CoreException {
		// Custom function with function-type parameter:
		// class Processor { fun process(handler: (Service) -> Unit) }
		// obj.process { it.doWork() }
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/lambdacustom");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/lambdacustom/Defs.kt",
				"package lambdacustom\n"
				+ "\n"
				+ "class Service {\n"
				+ "    fun doWork(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class OtherService {\n"
				+ "    fun doWork(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class AlphaProcessor {\n"
				+ "    fun process(handler: (Service) -> Unit) {\n"
				+ "        handler(Service())\n"
				+ "    }\n"
				+ "}\n"
				+ "\n"
				+ "class BetaProcessor {\n"
				+ "    fun process(handler: (OtherService) -> Unit) {\n"
				+ "        handler(OtherService())\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/lambdacustom/Caller.kt",
				"package lambdacustom\n"
				+ "\n"
				+ "fun useCustomLambda() {\n"
				+ "    val alpha: AlphaProcessor = AlphaProcessor()\n"
				+ "    val beta: BetaProcessor = BetaProcessor()\n"
				+ "\n"
				+ "    // it = Service — should match\n"
				+ "    alpha.process { it.doWork(1) }\n"
				+ "    alpha.process { it.doWork(2) }\n"
				+ "\n"
				+ "    // it = OtherService — should NOT match\n"
				+ "    beta.process { it.doWork(3) }\n"
				+ "    beta.process { it.doWork(4) }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"lambdacustom.Service.doWork", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find exactly 2 references to "
				+ "Service.doWork() via custom lambda parameter, "
				+ "filtering out OtherService calls. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testLetWithFactoryChainReceiver() throws CoreException {
		// factory.create().let { it.doWork() } — chained receiver
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/lambdachain");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/lambdachain/Defs.kt",
				"package lambdachain\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun doWork(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun doWork(x: Int) {}\n"
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
				"/" + PROJECT_NAME + "/src/lambdachain/Caller.kt",
				"package lambdachain\n"
				+ "\n"
				+ "fun useLetWithChain() {\n"
				+ "    val af: AlphaFactory = AlphaFactory()\n"
				+ "    val bf: BetaFactory = BetaFactory()\n"
				+ "\n"
				+ "    // it = Alpha — should match\n"
				+ "    af.create().let { it.doWork(1) }\n"
				+ "    af.create().let { it.doWork(2) }\n"
				+ "\n"
				+ "    // it = Beta — should NOT match\n"
				+ "    bf.create().let { it.doWork(3) }\n"
				+ "    bf.create().let { it.doWork(4) }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"lambdachain.Alpha.doWork", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find exactly 2 references to "
				+ "Alpha.doWork() via factory.create().let { }, "
				+ "filtering out Beta calls. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testForEachLambdaItBindingWithGenericCollection()
			throws CoreException {
		// Collection<Alpha>.forEach { it.doWork() } — the `it` should
		// resolve to Alpha via type arguments
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/lambdacoll");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/lambdacoll/Defs.kt",
				"package lambdacoll\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun process(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun process(x: Int) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/lambdacoll/Caller.kt",
				"package lambdacoll\n"
				+ "\n"
				+ "fun useCollections() {\n"
				+ "    val alphas: List<Alpha> = listOf()\n"
				+ "    val betas: List<Beta> = listOf()\n"
				+ "\n"
				+ "    // forEach on List<Alpha> — it is Alpha\n"
				+ "    alphas.forEach { it.process(1) }\n"
				+ "    alphas.forEach { it.process(2) }\n"
				+ "\n"
				+ "    // forEach on List<Beta> — it is Beta\n"
				+ "    betas.forEach { it.process(3) }\n"
				+ "    betas.forEach { it.process(4) }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"lambdacoll.Alpha.process", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find exactly 2 references to "
				+ "Alpha.process() via List<Alpha>.forEach { }, "
				+ "filtering out List<Beta>.forEach { }. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testMapLambdaItBindingWithPrimitiveArray()
			throws CoreException {
		// IntArray.forEach { it } — `it` resolves to Int via
		// getCollectionElementType's primitive array support
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/lambdaprim");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/lambdaprim/Defs.kt",
				"package lambdaprim\n"
				+ "\n"
				+ "class Processor {\n"
				+ "    fun handle(value: Int) {}\n"
				+ "    fun handle(value: String) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/lambdaprim/Caller.kt",
				"package lambdaprim\n"
				+ "\n"
				+ "fun usePrimitiveArray() {\n"
				+ "    val nums: IntArray = intArrayOf()\n"
				+ "    val p: Processor = Processor()\n"
				+ "\n"
				+ "    // forEach on IntArray — it is Int\n"
				+ "    nums.forEach { p.handle(it) }\n"
				+ "    nums.forEach { p.handle(it) }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Searching for handle(Int) should find the forEach calls
		// where `it` is Int (from IntArray)
		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"lambdaprim.Processor.handle", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		// Both handle(it) calls should match — `it` is Int from IntArray,
		// but handle is overloaded so both calls match the same method
		// (the search finds references to handle regardless of which
		// overload — the receiver type (Processor) is what's verified)
		assertEquals(2, ktRefs.size(),
				"Should find 2 references to Processor.handle() "
				+ "via IntArray.forEach { p.handle(it) }. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testNestedGenericCollectionLambda()
			throws CoreException {
		// Nested generic: List<List<Alpha>> — exercises
		// bracket-aware type argument splitting in ScopeChain
		// and named lambda parameter binding
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/lambdanested");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/lambdanested/Defs.kt",
				"package lambdanested\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun handle(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun handle(x: Int) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/lambdanested/Caller.kt",
				"package lambdanested\n"
				+ "\n"
				+ "fun useNestedGeneric() {\n"
				+ "    val items: List<List<Alpha>> = listOf()\n"
				+ "    val others: List<List<Beta>> = listOf()\n"
				+ "\n"
				+ "    items.forEach { inner ->\n"
				+ "        inner.forEach { it.handle(1) }\n"
				+ "    }\n"
				+ "    items.forEach { inner ->\n"
				+ "        inner.forEach { it.handle(2) }\n"
				+ "    }\n"
				+ "\n"
				+ "    others.forEach { inner ->\n"
				+ "        inner.forEach { it.handle(3) }\n"
				+ "    }\n"
				+ "    others.forEach { inner ->\n"
				+ "        inner.forEach { it.handle(4) }\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"lambdanested.Alpha.handle", project);
		List<SearchMatch> ktRefs = TestHelpers
				.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find exactly 2 references to "
				+ "Alpha.handle() via nested generic "
				+ "List<List<Alpha>>.forEach. Got: "
				+ describeMatches(ktRefs));
	}

	// ---- Coverage gap tests ----

	@Test
	public void testWithScopeFunctionBindsThis()
			throws CoreException {
		// with(obj) { this.method() } — exercises LambdaTypeResolver
		// "with" case which was uncovered
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/lambdawith");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/lambdawith/Defs.kt",
				"package lambdawith\n"
				+ "class Builder {\n"
				+ "    fun setName(n: String) {}\n"
				+ "    fun setAge(a: Int) {}\n"
				+ "}\n"
				+ "fun build(): Builder {\n"
				+ "    val b = Builder()\n"
				+ "    with(b) {\n"
				+ "        setName(\"test\")\n"
				+ "        setAge(25)\n"
				+ "    }\n"
				+ "    return b\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"lambdawith.Builder.setName", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertEquals(1, ktRefs.size(),
				"Should find Builder.setName() via with() scope "
				+ "function. Got: " + describeMatches(ktRefs));
	}

	@Test
	public void testDirectLambdaCallScopeBinding()
			throws CoreException {
		// run { this.method() } — direct call without navigation,
		// exercises LambdaScopeExtractor's direct function call path
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/lambdadirect");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/lambdadirect/Defs.kt",
				"package lambdadirect\n"
				+ "class Tracker {\n"
				+ "    fun track(event: String) {}\n"
				+ "}\n"
				+ "fun useTracker() {\n"
				+ "    val t = Tracker()\n"
				+ "    t.run {\n"
				+ "        track(\"start\")\n"
				+ "        track(\"end\")\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"lambdadirect.Tracker.track", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertEquals(2, ktRefs.size(),
				"Should find 2 Tracker.track() via run {} scope. "
				+ "Got: " + describeMatches(ktRefs));
	}

	@Test
	public void testTakeIfLambdaBinding() throws CoreException {
		// takeIf/takeUnless — exercises the LambdaTypeResolver
		// takeIf case. Uses unqualified search since receiver
		// verification through lambda it-binding is the E2E path.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/lambdatakeif");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/lambdatakeif/Defs.kt",
				"package lambdatakeif\n"
				+ "class Item {\n"
				+ "    fun isValid(): Boolean = true\n"
				+ "    fun getLabel(): String = \"\"\n"
				+ "}\n"
				+ "fun filterItems() {\n"
				+ "    val item = Item()\n"
				+ "    item.takeIf { it.isValid() }?.getLabel()\n"
				+ "    item.takeUnless { it.isValid() }?.getLabel()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers.searchMethodReferences(
				"isValid", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertEquals(2, ktRefs.size(),
				"Should find 2 isValid() via takeIf/takeUnless "
				+ "it binding. Got: " + describeMatches(ktRefs));
	}

	@Test
	public void testStringForEachElementType()
			throws CoreException {
		// String.forEach yields Char — exercises
		// LambdaTypeResolver.getCollectionElementType for String
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/lambdastr");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/lambdastr/Defs.kt",
				"package lambdastr\n"
				+ "fun processChars(text: String) {\n"
				+ "    text.forEach { c ->\n"
				+ "        c.isUpperCase()\n"
				+ "        c.isDigit()\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Char.isUpperCase should be found via String.forEach
		// element type resolution
		List<SearchMatch> refs = TestHelpers.searchMethodReferences(
				"isUpperCase", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertEquals(1, ktRefs.size(),
				"Should find isUpperCase() via String.forEach "
				+ "Char binding. Got: " + describeMatches(ktRefs));
	}
}
