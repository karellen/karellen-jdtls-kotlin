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
 * Tests that smart cast type narrowing is correctly applied during
 * receiver verification. Exercises the SmartCastExtractor through
 * the full locateReferenceMatches pipeline.
 *
 * @author Arcadiy Ivanov
 */
public class CrossLanguageSmartCastTest {

	private static final String PROJECT_NAME = "SmartCastTest";
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
	public void testIfIsNarrowsThenBranch() throws CoreException {
		// if (x is Foo) { x.fooMethod() } — should match Foo.fooMethod
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/smartcast1");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast1/Defs.kt",
				"package smartcast1\n"
				+ "\n"
				+ "open class Animal {\n"
				+ "    open fun speak(): String = \"\"\n"
				+ "}\n"
				+ "\n"
				+ "class Dog : Animal() {\n"
				+ "    fun fetch(item: String) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Cat : Animal() {\n"
				+ "    fun fetch(item: String) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast1/Caller.kt",
				"package smartcast1\n"
				+ "\n"
				+ "fun handleAnimal(x: Animal) {\n"
				+ "    if (x is Dog) {\n"
				+ "        x.fetch(\"ball\")\n"
				+ "        x.fetch(\"stick\")\n"
				+ "    }\n"
				+ "    if (x is Cat) {\n"
				+ "        x.fetch(\"mouse\")\n"
				+ "        x.fetch(\"yarn\")\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"smartcast1.Dog.fetch", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find 2 refs to Dog.fetch() in 'if (x is Dog)' "
				+ "branch, filtering out Cat.fetch(). Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testIfNotIsNarrowsElseBranch() throws CoreException {
		// if (x !is Foo) { ... } else { x.fooMethod() }
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/smartcast2");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast2/Defs.kt",
				"package smartcast2\n"
				+ "\n"
				+ "open class Shape\n"
				+ "\n"
				+ "class Circle : Shape() {\n"
				+ "    fun radius(): Double = 0.0\n"
				+ "}\n"
				+ "\n"
				+ "class Square : Shape() {\n"
				+ "    fun radius(): Double = 0.0\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast2/Caller.kt",
				"package smartcast2\n"
				+ "\n"
				+ "fun measureShape(s: Shape) {\n"
				+ "    if (s !is Circle) {\n"
				+ "        // not narrowed here\n"
				+ "    } else {\n"
				+ "        s.radius()\n"
				+ "        s.radius()\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"smartcast2.Circle.radius", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find 2 refs to Circle.radius() in else branch "
				+ "of 'if (s !is Circle)'. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testWhenIsPerBranchNarrowing() throws CoreException {
		// when (x) { is Foo -> x.fooMethod(); is Bar -> x.barMethod() }
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/smartcast3");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast3/Defs.kt",
				"package smartcast3\n"
				+ "\n"
				+ "interface Vehicle\n"
				+ "\n"
				+ "class Car : Vehicle {\n"
				+ "    fun honk(volume: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Bicycle : Vehicle {\n"
				+ "    fun honk(volume: Int) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast3/Caller.kt",
				"package smartcast3\n"
				+ "\n"
				+ "fun alertVehicle(v: Vehicle) {\n"
				+ "    when (v) {\n"
				+ "        is Car -> {\n"
				+ "            v.honk(10)\n"
				+ "            v.honk(20)\n"
				+ "        }\n"
				+ "        is Bicycle -> {\n"
				+ "            v.honk(1)\n"
				+ "            v.honk(2)\n"
				+ "        }\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"smartcast3.Car.honk", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find 2 refs to Car.honk() in 'is Car' branch, "
				+ "filtering out Bicycle.honk(). Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testNullCheckNarrows() throws CoreException {
		// if (x != null) { x.method() } — null narrowing
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/smartcast4");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast4/Defs.kt",
				"package smartcast4\n"
				+ "\n"
				+ "class Printer {\n"
				+ "    fun printLine(text: String) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Logger {\n"
				+ "    fun printLine(text: String) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast4/Caller.kt",
				"package smartcast4\n"
				+ "\n"
				+ "fun safePrint(p: Printer?, l: Logger?) {\n"
				+ "    if (p != null) {\n"
				+ "        p.printLine(\"hello\")\n"
				+ "        p.printLine(\"world\")\n"
				+ "    }\n"
				+ "    if (l != null) {\n"
				+ "        l.printLine(\"log1\")\n"
				+ "        l.printLine(\"log2\")\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"smartcast4.Printer.printLine", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find 2 refs to Printer.printLine() in null "
				+ "check branch, filtering out Logger.printLine(). "
				+ "Got: " + describeMatches(ktRefs));
	}

	@Test
	public void testNestedSmartCastInnermostWins() throws CoreException {
		// if (x is Foo) { if (x is SubFoo) { x.subMethod() } }
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/smartcast5");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast5/Defs.kt",
				"package smartcast5\n"
				+ "\n"
				+ "open class Base {\n"
				+ "    open fun action() {}\n"
				+ "}\n"
				+ "\n"
				+ "open class Middle : Base() {\n"
				+ "    fun middleAction(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Leaf : Middle() {\n"
				+ "    fun leafAction(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Other : Middle() {\n"
				+ "    fun leafAction(x: Int) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast5/Caller.kt",
				"package smartcast5\n"
				+ "\n"
				+ "fun handleBase(x: Base) {\n"
				+ "    if (x is Middle) {\n"
				+ "        if (x is Leaf) {\n"
				+ "            x.leafAction(1)\n"
				+ "            x.leafAction(2)\n"
				+ "        }\n"
				+ "        if (x is Other) {\n"
				+ "            x.leafAction(3)\n"
				+ "            x.leafAction(4)\n"
				+ "        }\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"smartcast5.Leaf.leafAction", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find 2 refs to Leaf.leafAction() in nested "
				+ "'is Leaf' branch, filtering out Other. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testNoLeakingAfterIfBlock() throws CoreException {
		// References after the if block should NOT be narrowed
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/smartcast6");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast6/Defs.kt",
				"package smartcast6\n"
				+ "\n"
				+ "open class Widget {\n"
				+ "    open fun render() {}\n"
				+ "}\n"
				+ "\n"
				+ "class Button : Widget() {\n"
				+ "    fun click(times: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Label : Widget() {\n"
				+ "    fun click(times: Int) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast6/Caller.kt",
				"package smartcast6\n"
				+ "\n"
				+ "fun useWidget(w: Widget) {\n"
				+ "    if (w is Button) {\n"
				+ "        w.click(1)\n"
				+ "        w.click(2)\n"
				+ "    }\n"
				+ "    // Outside the if — w is Widget again, not Button\n"
				+ "    // This unqualified click() should match both\n"
				+ "    // Button.click and Label.click (no narrowing)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"smartcast6.Button.click", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		// Only the 2 calls inside the if block should match
		assertEquals(2, ktRefs.size(),
				"Should find exactly 2 refs to Button.click() inside "
				+ "the 'if (w is Button)' block. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testWhenSubjectBindingNarrows() throws CoreException {
		// when (val y = expr) { is Foo -> y.method() }
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/smartcast7");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast7/Defs.kt",
				"package smartcast7\n"
				+ "\n"
				+ "interface Event\n"
				+ "\n"
				+ "class ClickEvent : Event {\n"
				+ "    fun getX(scale: Int): Int = 0\n"
				+ "}\n"
				+ "\n"
				+ "class KeyEvent : Event {\n"
				+ "    fun getX(scale: Int): Int = 0\n"
				+ "}\n"
				+ "\n"
				+ "fun createEvent(): Event = ClickEvent()\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast7/Caller.kt",
				"package smartcast7\n"
				+ "\n"
				+ "fun handleEvent() {\n"
				+ "    when (val e = createEvent()) {\n"
				+ "        is ClickEvent -> {\n"
				+ "            e.getX(1)\n"
				+ "            e.getX(2)\n"
				+ "        }\n"
				+ "        is KeyEvent -> {\n"
				+ "            e.getX(3)\n"
				+ "            e.getX(4)\n"
				+ "        }\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"smartcast7.ClickEvent.getX", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find 2 refs to ClickEvent.getX() in 'is "
				+ "ClickEvent' branch of when with subject binding. "
				+ "Got: " + describeMatches(ktRefs));
	}

	@Test
	public void testEqualsNullWithElseNarrowing() throws CoreException {
		// if (x == null) { ... } else { x.method() } — else branch
		// narrows to non-null
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/smartcast9");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast9/Defs.kt",
				"package smartcast9\n"
				+ "\n"
				+ "class Sender {\n"
				+ "    fun send(msg: String) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Receiver {\n"
				+ "    fun send(msg: String) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast9/Caller.kt",
				"package smartcast9\n"
				+ "\n"
				+ "fun trySend(s: Sender?, r: Receiver?) {\n"
				+ "    if (s == null) {\n"
				+ "        // s is null here\n"
				+ "    } else {\n"
				+ "        s.send(\"hello\")\n"
				+ "        s.send(\"world\")\n"
				+ "    }\n"
				+ "    if (r == null) {\n"
				+ "        // r is null here\n"
				+ "    } else {\n"
				+ "        r.send(\"log1\")\n"
				+ "        r.send(\"log2\")\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"smartcast9.Sender.send", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find 2 refs to Sender.send() in else branch "
				+ "of 'if (s == null)', filtering out Receiver. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testCompoundConditionNarrowing() throws CoreException {
		// if (x is Foo && y is Bar) { x.fooMethod(); y.barMethod() }
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/smartcast8");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast8/Defs.kt",
				"package smartcast8\n"
				+ "\n"
				+ "interface Handler\n"
				+ "\n"
				+ "class AlphaHandler : Handler {\n"
				+ "    fun handle(code: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class BetaHandler : Handler {\n"
				+ "    fun handle(code: Int) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast8/Caller.kt",
				"package smartcast8\n"
				+ "\n"
				+ "fun dispatch(a: Handler, b: Handler) {\n"
				+ "    if (a is AlphaHandler && b is BetaHandler) {\n"
				+ "        a.handle(1)\n"
				+ "        a.handle(2)\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"smartcast8.AlphaHandler.handle", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find 2 refs to AlphaHandler.handle() in "
				+ "compound 'a is AlphaHandler && b is BetaHandler' "
				+ "branch. Got: " + describeMatches(ktRefs));
	}

	@Test
	public void testUnsafeAsCastNarrows() throws CoreException {
		// obj as Foo narrows obj for the rest of the block
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/smartcast10");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast10/Defs.kt",
				"package smartcast10\n"
				+ "\n"
				+ "open class Transport {\n"
				+ "    open fun move() {}\n"
				+ "}\n"
				+ "\n"
				+ "class Train : Transport() {\n"
				+ "    fun loadCargo(tons: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Plane : Transport() {\n"
				+ "    fun loadCargo(tons: Int) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast10/Caller.kt",
				"package smartcast10\n"
				+ "\n"
				+ "fun dispatch(t: Transport) {\n"
				+ "    t as Train\n"
				+ "    t.loadCargo(100)\n"
				+ "    t.loadCargo(200)\n"
				+ "}\n"
				+ "\n"
				+ "fun dispatchPlane(t: Transport) {\n"
				+ "    t as Plane\n"
				+ "    t.loadCargo(50)\n"
				+ "    t.loadCargo(75)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"smartcast10.Train.loadCargo", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find 2 refs to Train.loadCargo() after "
				+ "'t as Train' cast, filtering out Plane. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testLocalFunctionScopedVariables() throws CoreException {
		// Verify locals in nested functions are properly scoped
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/smartcast11");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast11/Defs.kt",
				"package smartcast11\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun work(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun work(x: Int) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast11/Caller.kt",
				"package smartcast11\n"
				+ "\n"
				+ "fun outer() {\n"
				+ "    val a: Alpha = Alpha()\n"
				+ "    a.work(1)\n"
				+ "    a.work(2)\n"
				+ "\n"
				+ "    fun inner() {\n"
				+ "        val b: Beta = Beta()\n"
				+ "        b.work(3)\n"
				+ "        b.work(4)\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> alphaRefs = TestHelpers
				.searchQualifiedMethodReferences(
						"smartcast11.Alpha.work", project);
		List<SearchMatch> ktAlphaRefs = TestHelpers
				.filterKotlinMatches(alphaRefs);

		assertEquals(2, ktAlphaRefs.size(),
				"Should find 2 refs to Alpha.work() in outer(), "
				+ "filtering out Beta.work() in inner(). Got: "
				+ describeMatches(ktAlphaRefs));

		List<SearchMatch> betaRefs = TestHelpers
				.searchQualifiedMethodReferences(
						"smartcast11.Beta.work", project);
		List<SearchMatch> ktBetaRefs = TestHelpers
				.filterKotlinMatches(betaRefs);

		assertEquals(2, ktBetaRefs.size(),
				"Should find 2 refs to Beta.work() in inner(). Got: "
				+ describeMatches(ktBetaRefs));
	}

	@Test
	public void testAssignmentNarrowsVarType() throws CoreException {
		// var x: Base = ...; x = Derived(); x.derivedMethod()
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/smartcast12");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast12/Defs.kt",
				"package smartcast12\n"
				+ "\n"
				+ "open class Processor {\n"
				+ "    open fun run() {}\n"
				+ "}\n"
				+ "\n"
				+ "class FastProcessor : Processor() {\n"
				+ "    fun turbo(level: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class SlowProcessor : Processor() {\n"
				+ "    fun turbo(level: Int) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast12/Caller.kt",
				"package smartcast12\n"
				+ "\n"
				+ "fun reassign() {\n"
				+ "    var p: Processor = Processor()\n"
				+ "    p = FastProcessor()\n"
				+ "    p.turbo(1)\n"
				+ "    p.turbo(2)\n"
				+ "}\n"
				+ "\n"
				+ "fun reassignSlow() {\n"
				+ "    var p: Processor = Processor()\n"
				+ "    p = SlowProcessor()\n"
				+ "    p.turbo(3)\n"
				+ "    p.turbo(4)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"smartcast12.FastProcessor.turbo", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find 2 refs to FastProcessor.turbo() after "
				+ "'p = FastProcessor()' assignment, filtering out "
				+ "SlowProcessor. Got: " + describeMatches(ktRefs));
	}

	@Test
	public void testPolymorphicNarrowingMultipleTypesVsThird()
			throws CoreException {
		// var x assigned to TypeA then TypeB across branches;
		// search for TypeC.method() should find NO matches
		// (neither TypeA nor TypeB is TypeC)
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/smartcast13");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast13/Defs.kt",
				"package smartcast13\n"
				+ "\n"
				+ "interface Worker {\n"
				+ "    fun doJob(id: Int)\n"
				+ "}\n"
				+ "\n"
				+ "class AlphaWorker : Worker {\n"
				+ "    override fun doJob(id: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class BetaWorker : Worker {\n"
				+ "    override fun doJob(id: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class GammaWorker : Worker {\n"
				+ "    override fun doJob(id: Int) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast13/Caller.kt",
				"package smartcast13\n"
				+ "\n"
				+ "fun useAlpha() {\n"
				+ "    var w: Worker = AlphaWorker()\n"
				+ "    w = AlphaWorker()\n"
				+ "    w.doJob(1)\n"
				+ "    w.doJob(2)\n"
				+ "}\n"
				+ "\n"
				+ "fun useBeta() {\n"
				+ "    var w: Worker = BetaWorker()\n"
				+ "    w = BetaWorker()\n"
				+ "    w.doJob(3)\n"
				+ "    w.doJob(4)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for GammaWorker.doJob — neither function assigns
		// to GammaWorker, so should find 0 matches
		List<SearchMatch> gammaRefs = TestHelpers
				.searchQualifiedMethodReferences(
						"smartcast13.GammaWorker.doJob", project);
		List<SearchMatch> ktGammaRefs = TestHelpers
				.filterKotlinMatches(gammaRefs);

		assertEquals(0, ktGammaRefs.size(),
				"Should find 0 refs to GammaWorker.doJob() — "
				+ "neither function narrows to GammaWorker. Got: "
				+ describeMatches(ktGammaRefs));

		// Search for AlphaWorker.doJob — only useAlpha() matches
		List<SearchMatch> alphaRefs = TestHelpers
				.searchQualifiedMethodReferences(
						"smartcast13.AlphaWorker.doJob", project);
		List<SearchMatch> ktAlphaRefs = TestHelpers
				.filterKotlinMatches(alphaRefs);

		assertEquals(2, ktAlphaRefs.size(),
				"Should find 2 refs to AlphaWorker.doJob() in "
				+ "useAlpha(), filtering out useBeta(). Got: "
				+ describeMatches(ktAlphaRefs));

		// Search for BetaWorker.doJob — only useBeta() matches
		List<SearchMatch> betaRefs = TestHelpers
				.searchQualifiedMethodReferences(
						"smartcast13.BetaWorker.doJob", project);
		List<SearchMatch> ktBetaRefs = TestHelpers
				.filterKotlinMatches(betaRefs);

		assertEquals(2, ktBetaRefs.size(),
				"Should find 2 refs to BetaWorker.doJob() in "
				+ "useBeta(), filtering out useAlpha(). Got: "
				+ describeMatches(ktBetaRefs));
	}

	@Test
	public void testNullOnLeftSideOfNullCheck() throws CoreException {
		// null != x (reversed form) should narrow same as x != null
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/smartcast14");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast14/Defs.kt",
				"package smartcast14\n"
				+ "\n"
				+ "class Engine {\n"
				+ "    fun start(mode: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Motor {\n"
				+ "    fun start(mode: Int) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/smartcast14/Caller.kt",
				"package smartcast14\n"
				+ "\n"
				+ "fun useReversedNull(e: Engine?, m: Motor?) {\n"
				+ "    if (null != e) {\n"
				+ "        e.start(1)\n"
				+ "        e.start(2)\n"
				+ "    }\n"
				+ "    if (null != m) {\n"
				+ "        m.start(3)\n"
				+ "        m.start(4)\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"smartcast14.Engine.start", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find 2 Engine.start() via 'null != e' "
				+ "reversed null check. Got: "
				+ describeMatches(ktRefs));
	}
}
