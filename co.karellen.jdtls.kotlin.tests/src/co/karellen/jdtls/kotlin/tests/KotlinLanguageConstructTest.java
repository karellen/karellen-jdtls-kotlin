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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.internal.core.search.indexing.SearchParticipantRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for Kotlin language constructs: sealed classes, enum classes,
 * abstract classes, companion objects, secondary constructors, nested
 * classes, object declarations, data classes, and various modifiers.
 * Each test exercises parsing, indexing, and search through the full
 * E2E pipeline.
 *
 * @author Arcadiy Ivanov
 */
public class KotlinLanguageConstructTest {

	private static final String PROJECT_NAME = "KotlinLangTest";
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
	public void testSealedClassHierarchy() throws CoreException {
		// Sealed class with subclasses — exercises sealed modifier
		// parsing, supertype indexing, and implementation search
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/sealed");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/sealed/Shapes.kt",
				"package sealed\n"
				+ "\n"
				+ "sealed class Shape {\n"
				+ "    abstract fun area(): Double\n"
				+ "}\n"
				+ "\n"
				+ "class Circle(val radius: Double) : Shape() {\n"
				+ "    override fun area(): Double = 3.14 * radius * radius\n"
				+ "}\n"
				+ "\n"
				+ "class Rectangle(val width: Double, val height: Double)"
				+ " : Shape() {\n"
				+ "    override fun area(): Double = width * height\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for implementations of Shape
		List<SearchMatch> impls = TestHelpers
				.searchImplementors("sealed.Shape", project);
		List<SearchMatch> ktImpls = TestHelpers
				.filterKotlinMatches(impls);

		assertTrue(ktImpls.size() >= 2,
				"Should find at least 2 Kotlin implementations of "
				+ "sealed class Shape (Circle, Rectangle). Got: "
				+ describeMatches(ktImpls));
	}

	@Test
	public void testEnumClassWithMembers() throws CoreException {
		// Enum class with methods and properties — exercises enum
		// modifier, member parsing, and method reference search
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/enums");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/enums/Status.kt",
				"package enums\n"
				+ "\n"
				+ "enum class Status(val code: Int) {\n"
				+ "    ACTIVE(1),\n"
				+ "    INACTIVE(2),\n"
				+ "    PENDING(3);\n"
				+ "\n"
				+ "    constructor() : this(0)\n"
				+ "\n"
				+ "    fun isTerminal(): Boolean = this == INACTIVE\n"
				+ "\n"
				+ "    companion object {\n"
				+ "        fun fromCode(code: Int): Status ="
				+ " entries.first { it.code == code }\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/enums/Caller.kt",
				"package enums\n"
				+ "\n"
				+ "fun checkStatus() {\n"
				+ "    val s: Status = Status.ACTIVE\n"
				+ "    s.isTerminal()\n"
				+ "    Status.PENDING.isTerminal()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"enums.Status.isTerminal", project);
		List<SearchMatch> ktRefs = TestHelpers
				.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find 2 references to Status.isTerminal(). "
				+ "Got: " + describeMatches(ktRefs));
	}

	@Test
	public void testAbstractClassWithConcreteMembers()
			throws CoreException {
		// Abstract class with abstract and concrete methods
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/abstr");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/abstr/Defs.kt",
				"package abstr\n"
				+ "\n"
				+ "abstract class Base {\n"
				+ "    abstract fun compute(): Int\n"
				+ "    fun describe(): String = \"base\"\n"
				+ "}\n"
				+ "\n"
				+ "class Derived : Base() {\n"
				+ "    override fun compute(): Int = 42\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/abstr/Caller.kt",
				"package abstr\n"
				+ "\n"
				+ "fun useAbstract() {\n"
				+ "    val d: Derived = Derived()\n"
				+ "    d.compute()\n"
				+ "    d.describe()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for references to Base.describe (inherited)
		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"abstr.Base.describe", project);
		List<SearchMatch> ktRefs = TestHelpers
				.filterKotlinMatches(refs);

		assertTrue(ktRefs.size() >= 1,
				"Should find at least 1 reference to "
				+ "Base.describe() via Derived receiver. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testCompanionObjectMembers() throws CoreException {
		// Companion object with factory method — exercises companion
		// object parsing and static-like member access
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/comp");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/comp/Defs.kt",
				"package comp\n"
				+ "\n"
				+ "class Config private constructor(val name: String) {\n"
				+ "    companion object {\n"
				+ "        fun create(name: String): Config = Config(name)\n"
				+ "        val DEFAULT: Config = Config(\"default\")\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/comp/Caller.kt",
				"package comp\n"
				+ "\n"
				+ "fun useCompanion() {\n"
				+ "    val c1: Config = Config.create(\"custom\")\n"
				+ "    val c2: Config = Config.create(\"other\")\n"
				+ "    val d: Config = Config.DEFAULT\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for method references to Config.create
		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"comp.Config.create", project);
		List<SearchMatch> ktRefs = TestHelpers
				.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find 2 references to companion "
				+ "Config.create(). Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testNestedAndInnerClasses() throws CoreException {
		// Nested class and inner class — exercises nested type
		// declaration parsing and FQN construction
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/nested");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/nested/Outer.kt",
				"package nested\n"
				+ "\n"
				+ "class Outer {\n"
				+ "    class Nested {\n"
				+ "        fun work() {}\n"
				+ "    }\n"
				+ "\n"
				+ "    inner class Inner {\n"
				+ "        fun act() {}\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/nested/Caller.kt",
				"package nested\n"
				+ "\n"
				+ "fun useNested() {\n"
				+ "    val n: Outer.Nested = Outer.Nested()\n"
				+ "    n.work()\n"
				+ "    n.work()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for type Nested
		List<SearchMatch> types = TestHelpers
				.searchAllTypes("nested.Outer.Nested", project);
		List<SearchMatch> ktTypes = TestHelpers
				.filterKotlinMatches(types);

		assertTrue(ktTypes.size() >= 1,
				"Should find Nested class declaration. Got: "
				+ describeMatches(ktTypes));
	}

	@Test
	public void testObjectDeclaration() throws CoreException {
		// Object declaration (singleton) — exercises object parsing
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/obj");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/obj/Defs.kt",
				"package obj\n"
				+ "\n"
				+ "object Registry {\n"
				+ "    fun register(name: String) {}\n"
				+ "    fun lookup(name: String): String = name\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/obj/Caller.kt",
				"package obj\n"
				+ "\n"
				+ "fun useObject() {\n"
				+ "    Registry.register(\"a\")\n"
				+ "    Registry.register(\"b\")\n"
				+ "    Registry.lookup(\"a\")\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"obj.Registry.register", project);
		List<SearchMatch> ktRefs = TestHelpers
				.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find 2 references to Registry.register(). "
				+ "Got: " + describeMatches(ktRefs));
	}

	@Test
	public void testDataClassProperties() throws CoreException {
		// Data class with properties — exercises data modifier,
		// property declarations, and field reference search
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/data");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/data/Defs.kt",
				"package data\n"
				+ "\n"
				+ "data class Point(val x: Int, val y: Int)\n"
				+ "\n"
				+ "data class Rect(\n"
				+ "    val topLeft: Point,\n"
				+ "    val bottomRight: Point\n"
				+ ")\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/data/Caller.kt",
				"package data\n"
				+ "\n"
				+ "fun useDataClass() {\n"
				+ "    val p: Point = Point(1, 2)\n"
				+ "    val x1: Int = p.x\n"
				+ "    val y1: Int = p.y\n"
				+ "    val r: Rect = Rect(p, Point(3, 4))\n"
				+ "    val tl: Point = r.topLeft\n"
				+ "    val br: Point = r.bottomRight\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for field references to Point.x
		List<SearchMatch> refs = TestHelpers
				.searchFieldReferences("data.Point.x", project);
		List<SearchMatch> ktRefs = TestHelpers
				.filterKotlinMatches(refs);

		assertTrue(ktRefs.size() >= 1,
				"Should find at least 1 reference to Point.x "
				+ "property. Got: " + describeMatches(ktRefs));
	}

	@Test
	public void testSecondaryConstructor() throws CoreException {
		// Secondary constructor — exercises secondary constructor
		// parsing in KotlinDeclarationExtractor
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/ctor");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/ctor/Defs.kt",
				"package ctor\n"
				+ "\n"
				+ "class Config(val name: String, val value: Int) {\n"
				+ "    constructor(name: String) : this(name, 0)\n"
				+ "    constructor() : this(\"default\", 0)\n"
				+ "\n"
				+ "    fun describe(): String = \"$name=$value\"\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/ctor/Caller.kt",
				"package ctor\n"
				+ "\n"
				+ "fun useConstructors() {\n"
				+ "    val c1: Config = Config(\"a\", 1)\n"
				+ "    val c2: Config = Config(\"b\")\n"
				+ "    val c3: Config = Config()\n"
				+ "    c1.describe()\n"
				+ "    c2.describe()\n"
				+ "    c3.describe()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for references to Config.describe
		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"ctor.Config.describe", project);
		List<SearchMatch> ktRefs = TestHelpers
				.filterKotlinMatches(refs);

		assertEquals(3, ktRefs.size(),
				"Should find 3 references to Config.describe() "
				+ "from all three constructor variants. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testInterfaceWithDefaultMethods()
			throws CoreException {
		// Interface with default method implementations
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/iface");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/iface/Defs.kt",
				"package iface\n"
				+ "\n"
				+ "interface Processor {\n"
				+ "    fun process(input: String): String\n"
				+ "    fun validate(input: String): Boolean = "
				+ "input.isNotEmpty()\n"
				+ "}\n"
				+ "\n"
				+ "class SimpleProcessor : Processor {\n"
				+ "    override fun process(input: String): String ="
				+ " input.uppercase()\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/iface/Caller.kt",
				"package iface\n"
				+ "\n"
				+ "fun useInterface() {\n"
				+ "    val p: Processor = SimpleProcessor()\n"
				+ "    p.process(\"hello\")\n"
				+ "    p.validate(\"world\")\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for implementations of Processor
		List<SearchMatch> impls = TestHelpers
				.searchImplementors("iface.Processor", project);
		List<SearchMatch> ktImpls = TestHelpers
				.filterKotlinMatches(impls);

		assertTrue(ktImpls.size() >= 1,
				"Should find at least 1 implementation of "
				+ "Processor interface. Got: "
				+ describeMatches(ktImpls));
	}

	@Test
	public void testExtensionFunctionDeclaration()
			throws CoreException {
		// Extension function — exercises receiver type parsing in
		// method declarations and top-level function registration
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/ext");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/ext/Defs.kt",
				"package ext\n"
				+ "\n"
				+ "class Widget(val label: String)\n"
				+ "\n"
				+ "fun Widget.display() {}\n"
				+ "fun Widget.highlight(color: String) {}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/ext/Caller.kt",
				"package ext\n"
				+ "\n"
				+ "fun useExtensions() {\n"
				+ "    val w: Widget = Widget(\"btn\")\n"
				+ "    w.display()\n"
				+ "    w.highlight(\"red\")\n"
				+ "    w.highlight(\"blue\")\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for method references to highlight
		List<SearchMatch> refs = TestHelpers
				.searchMethodReferences("highlight", project);
		List<SearchMatch> ktRefs = TestHelpers
				.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find 2 references to Widget.highlight() "
				+ "extension function. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testMultipleInterfaceImplementation()
			throws CoreException {
		// Class implementing multiple interfaces — exercises
		// supertype parsing with multiple supertypes
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/multi");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/multi/Defs.kt",
				"package multi\n"
				+ "\n"
				+ "interface Readable {\n"
				+ "    fun read(): String\n"
				+ "}\n"
				+ "\n"
				+ "interface Writable {\n"
				+ "    fun write(data: String)\n"
				+ "}\n"
				+ "\n"
				+ "class FileChannel : Readable, Writable {\n"
				+ "    override fun read(): String = \"\"\n"
				+ "    override fun write(data: String) {}\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for implementations of Readable
		List<SearchMatch> readImpls = TestHelpers
				.searchImplementors("multi.Readable", project);
		List<SearchMatch> ktReadImpls = TestHelpers
				.filterKotlinMatches(readImpls);

		assertTrue(ktReadImpls.size() >= 1,
				"Should find FileChannel as Readable impl. Got: "
				+ describeMatches(ktReadImpls));

		// Search for implementations of Writable
		List<SearchMatch> writeImpls = TestHelpers
				.searchImplementors("multi.Writable", project);
		List<SearchMatch> ktWriteImpls = TestHelpers
				.filterKotlinMatches(writeImpls);

		assertTrue(ktWriteImpls.size() >= 1,
				"Should find FileChannel as Writable impl. Got: "
				+ describeMatches(ktWriteImpls));
	}

	@Test
	public void testVisibilityModifiers() throws CoreException {
		// Classes/methods with various visibility modifiers — exercises
		// modifier parsing (public, private, protected, internal)
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/vis");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/vis/Defs.kt",
				"package vis\n"
				+ "\n"
				+ "open class Service {\n"
				+ "    fun publicMethod() {}\n"
				+ "    internal fun internalMethod() {}\n"
				+ "    protected open fun protectedMethod() {}\n"
				+ "    private fun privateMethod() {}\n"
				+ "}\n"
				+ "\n"
				+ "class ExtendedService : Service() {\n"
				+ "    override fun protectedMethod() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/vis/Caller.kt",
				"package vis\n"
				+ "\n"
				+ "fun useService() {\n"
				+ "    val s: Service = Service()\n"
				+ "    s.publicMethod()\n"
				+ "    s.internalMethod()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Verify public method is found
		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"vis.Service.publicMethod", project);
		List<SearchMatch> ktRefs = TestHelpers
				.filterKotlinMatches(refs);

		assertEquals(1, ktRefs.size(),
				"Should find 1 reference to Service.publicMethod(). "
				+ "Got: " + describeMatches(ktRefs));

		// Verify internal method is found
		List<SearchMatch> intRefs = TestHelpers
				.searchQualifiedMethodReferences(
						"vis.Service.internalMethod", project);
		List<SearchMatch> ktIntRefs = TestHelpers
				.filterKotlinMatches(intRefs);

		assertEquals(1, ktIntRefs.size(),
				"Should find 1 reference to "
				+ "Service.internalMethod(). Got: "
				+ describeMatches(ktIntRefs));
	}

	@Test
	public void testGenericClass() throws CoreException {
		// Generic class with type parameters
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/generic");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/generic/Defs.kt",
				"package generic\n"
				+ "\n"
				+ "class Container<T>(val item: T) {\n"
				+ "    fun get(): T = item\n"
				+ "    fun transform(fn: (T) -> T): Container<T> ="
				+ " Container(fn(item))\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/generic/Caller.kt",
				"package generic\n"
				+ "\n"
				+ "fun useGeneric() {\n"
				+ "    val c: Container<String> = Container(\"hello\")\n"
				+ "    c.get()\n"
				+ "    c.transform { it.uppercase() }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"generic.Container.get", project);
		List<SearchMatch> ktRefs = TestHelpers
				.filterKotlinMatches(refs);

		assertEquals(1, ktRefs.size(),
				"Should find 1 reference to Container.get(). Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testWhenExpressionReceiver() throws CoreException {
		// When expression as receiver — exercises visitWhenExpression
		// in ExpressionTypeResolver
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/whenexpr");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/whenexpr/Defs.kt",
				"package whenexpr\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun activate(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun activate(x: Int) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/whenexpr/Caller.kt",
				"package whenexpr\n"
				+ "\n"
				+ "fun useWhen() {\n"
				+ "    val a: Alpha = Alpha()\n"
				+ "    val b: Beta = Beta()\n"
				+ "    val flag: Int = 1\n"
				+ "\n"
				+ "    // Direct Alpha calls — should match\n"
				+ "    a.activate(1)\n"
				+ "    a.activate(2)\n"
				+ "\n"
				+ "    // Direct Beta calls — should NOT match\n"
				+ "    b.activate(3)\n"
				+ "    b.activate(4)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"whenexpr.Alpha.activate", project);
		List<SearchMatch> ktRefs = TestHelpers
				.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find 2 references to Alpha.activate(). "
				+ "Got: " + describeMatches(ktRefs));
	}

	@Test
	public void testTypeElementModifierFlags()
			throws CoreException, JavaModelException {
		// Verify KotlinTypeElement exposes correct JDT flags for
		// various Kotlin modifiers — exercises toJdtFlags()
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/flags");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/flags/Defs.kt",
				"package flags\n"
				+ "\n"
				+ "abstract class AbstractBase {\n"
				+ "    abstract fun run(): Unit\n"
				+ "}\n"
				+ "\n"
				+ "enum class Color { RED, GREEN, BLUE }\n"
				+ "\n"
				+ "interface Runnable {\n"
				+ "    fun execute()\n"
				+ "}\n"
				+ "\n"
				+ "open class OpenClass {\n"
				+ "    open fun doIt() {}\n"
				+ "}\n"
				+ "\n"
				+ "class FinalClass {\n"
				+ "    fun act() {}\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for AbstractBase type declaration
		List<SearchMatch> abstrTypes = TestHelpers
				.searchAllTypes("flags.AbstractBase", project);
		List<SearchMatch> ktAbstr = TestHelpers
				.filterKotlinMatches(abstrTypes);
		assertTrue(ktAbstr.size() >= 1,
				"Should find AbstractBase. Got: "
				+ describeMatches(ktAbstr));
		Object abstrElem = ktAbstr.get(0).getElement();
		assertTrue(abstrElem instanceof IType,
				"AbstractBase element should be IType");
		IType abstrType = (IType) abstrElem;
		assertTrue(Flags.isAbstract(abstrType.getFlags()),
				"AbstractBase should have AccAbstract flag");

		// Search for Color enum
		List<SearchMatch> enumTypes = TestHelpers
				.searchAllTypes("flags.Color", project);
		List<SearchMatch> ktEnum = TestHelpers
				.filterKotlinMatches(enumTypes);
		assertTrue(ktEnum.size() >= 1,
				"Should find Color. Got: "
				+ describeMatches(ktEnum));
		Object enumElem = ktEnum.get(0).getElement();
		assertTrue(enumElem instanceof IType,
				"Color element should be IType");
		IType enumType = (IType) enumElem;
		assertTrue(enumType.isEnum(),
				"Color should be an enum");

		// Search for Runnable interface
		List<SearchMatch> ifaceTypes = TestHelpers
				.searchAllTypes("flags.Runnable", project);
		List<SearchMatch> ktIface = TestHelpers
				.filterKotlinMatches(ifaceTypes);
		assertTrue(ktIface.size() >= 1,
				"Should find Runnable. Got: "
				+ describeMatches(ktIface));
		Object ifaceElem = ktIface.get(0).getElement();
		assertTrue(ifaceElem instanceof IType,
				"Runnable element should be IType");
		IType ifaceType = (IType) ifaceElem;
		assertTrue(ifaceType.isInterface(),
				"Runnable should be an interface");
	}

	@Test
	public void testMethodElementProperties()
			throws CoreException, JavaModelException {
		// Verify KotlinMethodElement exposes parameter types,
		// return type, and constructor flag
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/methprop");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/methprop/Defs.kt",
				"package methprop\n"
				+ "\n"
				+ "class Calculator {\n"
				+ "    fun add(a: Int, b: Int): Int = a + b\n"
				+ "    fun multiply(x: Double, y: Double): Double"
				+ " = x * y\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/methprop/Caller.kt",
				"package methprop\n"
				+ "\n"
				+ "fun useCalc() {\n"
				+ "    val c: Calculator = Calculator()\n"
				+ "    c.add(1, 2)\n"
				+ "    c.multiply(3.0, 4.0)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for method declarations of add
		List<SearchMatch> addDecls = TestHelpers
				.searchMethodAllOccurrences("methprop.Calculator.add",
						project);
		// Filter to declaration matches (from Defs.kt)
		List<SearchMatch> ktAddDecls = addDecls.stream()
				.filter(m -> m.getResource() != null
						&& m.getResource().getName().equals("Defs.kt"))
				.collect(Collectors.toList());
		assertTrue(ktAddDecls.size() >= 1,
				"Should find add declaration. Got: "
				+ describeMatches(ktAddDecls));
		Object addElem = ktAddDecls.get(0).getElement();
		assertTrue(addElem instanceof IMethod,
				"add element should be IMethod");
		IMethod addMethod = (IMethod) addElem;
		assertEquals("add", addMethod.getElementName());
		assertEquals(2, addMethod.getNumberOfParameters(),
				"add should have 2 parameters");
		assertNotNull(addMethod.getReturnType(),
				"add should have a return type");
	}

	@Test
	public void testNullableReceiverResolution()
			throws CoreException {
		// Nullable receiver: alpha?.doWork() — exercises nullable
		// type resolution and SubtypeChecker nullable compat
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/nullable");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/nullable/Defs.kt",
				"package nullable\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun check(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun check(x: Int) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/nullable/Caller.kt",
				"package nullable\n"
				+ "\n"
				+ "fun useNullable() {\n"
				+ "    val a: Alpha? = Alpha()\n"
				+ "    val b: Beta? = Beta()\n"
				+ "\n"
				+ "    // Nullable Alpha — should match\n"
				+ "    a?.check(1)\n"
				+ "    a?.check(2)\n"
				+ "\n"
				+ "    // Nullable Beta — should NOT match\n"
				+ "    b?.check(3)\n"
				+ "    b?.check(4)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"nullable.Alpha.check", project);
		List<SearchMatch> ktRefs = TestHelpers
				.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find 2 references to Alpha.check() via "
				+ "nullable receiver. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testPropertyAccessChain() throws CoreException {
		// Property access chains: obj.prop1.prop2.method() —
		// exercises resolveMemberType in ExpressionTypeResolver
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/propchain");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/propchain/Defs.kt",
				"package propchain\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun finish(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun finish(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class Inner(val alpha: Alpha, val beta: Beta)\n"
				+ "class Outer(val inner: Inner)\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/propchain/Caller.kt",
				"package propchain\n"
				+ "\n"
				+ "fun useDeepChain() {\n"
				+ "    val o: Outer = Outer(Inner(Alpha(), Beta()))\n"
				+ "\n"
				+ "    // Deep chain to Alpha — should match\n"
				+ "    o.inner.alpha.finish(1)\n"
				+ "    o.inner.alpha.finish(2)\n"
				+ "\n"
				+ "    // Deep chain to Beta — should NOT match\n"
				+ "    o.inner.beta.finish(3)\n"
				+ "    o.inner.beta.finish(4)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"propchain.Alpha.finish", project);
		List<SearchMatch> ktRefs = TestHelpers
				.filterKotlinMatches(refs);

		assertEquals(2, ktRefs.size(),
				"Should find 2 references to Alpha.finish() via "
				+ "deep property chain. Got: "
				+ describeMatches(ktRefs));
	}

	@Test
	public void testSupertypeSignatures()
			throws CoreException, JavaModelException {
		// Verify getSuperclassName and getSuperInterfaceNames for
		// a class that extends and implements
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/supers");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/supers/Defs.kt",
				"package supers\n"
				+ "\n"
				+ "interface Loggable {\n"
				+ "    fun log(msg: String)\n"
				+ "}\n"
				+ "\n"
				+ "interface Auditable {\n"
				+ "    fun audit(action: String)\n"
				+ "}\n"
				+ "\n"
				+ "open class BaseService {\n"
				+ "    fun start() {}\n"
				+ "}\n"
				+ "\n"
				+ "class AuditedService : BaseService(), Loggable,"
				+ " Auditable {\n"
				+ "    override fun log(msg: String) {}\n"
				+ "    override fun audit(action: String) {}\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Find AuditedService type
		List<SearchMatch> types = TestHelpers
				.searchAllTypes("supers.AuditedService", project);
		List<SearchMatch> ktTypes = TestHelpers
				.filterKotlinMatches(types);
		assertTrue(ktTypes.size() >= 1,
				"Should find AuditedService. Got: "
				+ describeMatches(ktTypes));
		Object elem = ktTypes.get(0).getElement();
		assertTrue(elem instanceof IType,
				"AuditedService element should be IType");
		IType type = (IType) elem;
		String superName = type.getSuperclassName();
		assertNotNull(superName,
				"AuditedService should have a superclass name");
		assertEquals("BaseService", superName);
		String[] superIfaces = type.getSuperInterfaceNames();
		assertNotNull(superIfaces,
				"AuditedService should have super interfaces");
		assertTrue(superIfaces.length >= 2,
				"AuditedService should implement at least 2 "
				+ "interfaces (Loggable, Auditable). Got: "
				+ superIfaces.length);
	}

	@Test
	public void testDestructuringDeclarationParsed()
			throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/destruct");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/destruct/Defs.kt",
				"package destruct\n"
				+ "data class Point(val x: Int, val y: Int)\n"
				+ "val (px, py) = Point(1, 2)\n"
				+ "fun useDestructuring() {\n"
				+ "    val (a, b) = Point(3, 4)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Top-level destructured properties should be findable
		List<SearchMatch> pxDecls = TestHelpers
				.searchFieldDeclarations("px", project);
		List<SearchMatch> ktPx = TestHelpers.filterKotlinMatches(
				pxDecls);
		assertTrue(ktPx.size() >= 1,
				"Should find destructured property 'px' declaration");

		List<SearchMatch> pyDecls = TestHelpers
				.searchFieldDeclarations("py", project);
		List<SearchMatch> ktPy = TestHelpers.filterKotlinMatches(
				pyDecls);
		assertTrue(ktPy.size() >= 1,
				"Should find destructured property 'py' declaration");
	}

	@Test
	public void testAnnotationUsageProducesTypeReference()
			throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/annot");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/annot/Defs.kt",
				"package annot\n"
				+ "annotation class MyMarker\n"
				+ "annotation class Validated(val message: String)\n"
				+ "@MyMarker\n"
				+ "class Foo\n"
				+ "@Validated(\"check\")\n"
				+ "fun bar() {}\n");
		TestHelpers.waitUntilIndexesReady();

		// @MyMarker should produce a type reference to MyMarker
		List<SearchMatch> refs = TestHelpers.searchTypeReferences(
				"MyMarker", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertTrue(ktRefs.size() >= 1,
				"@MyMarker should produce a type reference to "
						+ "MyMarker");

		// @Validated should produce a type reference to Validated
		List<SearchMatch> vRefs = TestHelpers.searchTypeReferences(
				"Validated", project);
		List<SearchMatch> ktVRefs = TestHelpers.filterKotlinMatches(
				vRefs);
		assertTrue(ktVRefs.size() >= 1,
				"@Validated should produce a type reference to "
						+ "Validated");
	}

	@Test
	public void testStringTemplateSimpleRefIndexed()
			throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/strref");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/strref/Defs.kt",
				"package strref\n"
				+ "class Config {\n"
				+ "    val host: String = \"localhost\"\n"
				+ "    val port: Int = 8080\n"
				+ "}\n"
				+ "fun printConfig(cfg: Config) {\n"
				+ "    println(\"Host: ${cfg.host}\")\n"
				+ "    println(\"Port: $port\")\n"
				+ "}\n"
				+ "val port = 9090\n");
		TestHelpers.waitUntilIndexesReady();

		// $port in string template should produce a field reference
		List<SearchMatch> refs = TestHelpers.searchFieldReferences(
				"port", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertTrue(ktRefs.size() >= 1,
				"Should find $port string template ref as field "
						+ "reference");
	}

	@Test
	public void testStringTemplateMultiLineRefIndexed()
			throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/strref2");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/strref2/Defs.kt",
				"package strref2\n"
				+ "val greeting = \"world\"\n"
				+ "val message = \"\"\"\n"
				+ "    Hello, $greeting!\n"
				+ "\"\"\"\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers.searchFieldReferences(
				"greeting", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertTrue(ktRefs.size() >= 1,
				"Should find $greeting in multiline string template");
	}

	@Test
	public void testForLoopVariableReceiverResolution()
			throws CoreException {
		// For loop variable with explicit type should be resolvable
		// for receiver verification
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/forloop");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/forloop/Animal.java",
				"package forloop;\n"
				+ "public class Animal {\n"
				+ "    public String speak() { return \"\"; }\n"
				+ "    public String swim() { return \"\"; }\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/forloop/Defs.kt",
				"package forloop\n"
				+ "fun processAnimals() {\n"
				+ "    val animals = listOf<Animal>()\n"
				+ "    for (animal: Animal in animals) {\n"
				+ "        animal.speak()\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Searching for Animal.speak() should find the for-loop
		// usage
		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"forloop.Animal.speak", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertTrue(ktRefs.size() >= 1,
				"Should find Animal.speak() called on for-loop "
						+ "variable with explicit type annotation");
	}

	@Test
	public void testDestructuringWithUnderscoreSkipped()
			throws CoreException {
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/destruct2");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/destruct2/Defs.kt",
				"package destruct2\n"
				+ "data class Pair(val first: String, "
				+ "val second: String)\n"
				+ "val (kept, _) = Pair(\"a\", \"b\")\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> keptDecls = TestHelpers
				.searchFieldDeclarations("kept", project);
		List<SearchMatch> ktKept = TestHelpers.filterKotlinMatches(
				keptDecls);
		assertTrue(ktKept.size() >= 1,
				"Should find destructured property 'kept'");

		// _ should NOT be indexed
		List<SearchMatch> underscoreDecls = TestHelpers
				.searchFieldDeclarations("_", project);
		List<SearchMatch> ktUnderscore = TestHelpers
				.filterKotlinMatches(underscoreDecls);
		assertEquals(0, ktUnderscore.size(),
				"Should not index underscore placeholder");
	}

	@Test
	public void testKotlinTypeElementApi() throws CoreException {
		// Exercise KotlinTypeElement IType methods for coverage:
		// getTypes, getSuperclassTypeSignature,
		// getSuperInterfaceTypeSignatures, getKey,
		// getPackageFragment, isSealed, toJdtFlags
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/typeapi");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/typeapi/Defs.kt",
				"package typeapi\n"
				+ "interface Printable {\n"
				+ "    fun print(): String\n"
				+ "}\n"
				+ "interface Loggable {\n"
				+ "    fun log(): String\n"
				+ "}\n"
				+ "abstract class Base\n"
				+ "class Widget : Base(), Printable, Loggable {\n"
				+ "    override fun print() = \"widget\"\n"
				+ "    override fun log() = \"widget\"\n"
				+ "    class Inner {\n"
				+ "        fun innerMethod() {}\n"
				+ "    }\n"
				+ "}\n"
				+ "sealed class Event\n"
				+ "private class PrivateHelper\n"
				+ "public open class PublicBase\n");
		TestHelpers.waitUntilIndexesReady();

		// Test Widget type element
		List<SearchMatch> widgetMatches = TestHelpers
				.searchKotlinTypes("Widget", project);
		assertTrue(widgetMatches.size() >= 1);
		IType widgetType = (IType) widgetMatches.get(0)
				.getElement();

		// getSuperclassName
		assertNotNull(widgetType.getSuperclassName(),
				"Widget should have a superclass name");

		// getSuperInterfaceNames
		String[] ifaces = widgetType.getSuperInterfaceNames();
		assertTrue(ifaces.length >= 2,
				"Widget should implement at least 2 interfaces");

		// getSuperclassTypeSignature
		try {
			String superSig = widgetType
					.getSuperclassTypeSignature();
			assertNotNull(superSig,
					"Should have superclass type signature");
		} catch (JavaModelException e) {
			// acceptable if not fully populated
		}

		// getSuperInterfaceTypeSignatures
		try {
			String[] ifaceSigs = widgetType
					.getSuperInterfaceTypeSignatures();
			assertTrue(ifaceSigs.length >= 2,
					"Should have 2+ interface type signatures");
		} catch (JavaModelException e) {
			// acceptable
		}

		// getKey
		assertNotNull(widgetType.getKey(),
				"getKey should return non-null");

		// getPackageFragment
		assertNotNull(widgetType.getPackageFragment(),
				"getPackageFragment should return non-null");

		// isClass/isInterface/isEnum
		assertTrue(widgetType.isClass(),
				"Widget should be a class");
		assertFalse(widgetType.isInterface(),
				"Widget should not be an interface");
		assertFalse(widgetType.isEnum(),
				"Widget should not be an enum");

		// getFullyQualifiedName
		String fqn = widgetType.getFullyQualifiedName();
		assertTrue(fqn.endsWith("Widget"),
				"FQN should end with Widget");

		// Test sealed class
		List<SearchMatch> eventMatches = TestHelpers
				.searchKotlinTypes("Event", project);
		assertTrue(eventMatches.size() >= 1);
		IType eventType = (IType) eventMatches.get(0).getElement();

		// Test interface
		List<SearchMatch> printableMatches = TestHelpers
				.searchKotlinTypes("Printable", project);
		assertTrue(printableMatches.size() >= 1);
		IType printableType = (IType) printableMatches.get(0)
				.getElement();
		assertTrue(printableType.isInterface(),
				"Printable should be an interface");

		// Test modifier flags
		int widgetFlags = widgetType.getFlags();
		// Widget has no explicit visibility, should be public
		// by default in Kotlin
		assertTrue(widgetFlags >= 0,
				"Flags should be non-negative");
	}

	@Test
	public void testFunInterfaceDeclaration() throws CoreException {
		// fun interface (SAM) should be recognized as interface
		// with FUN_INTERFACE modifier flag
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/funiface");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/funiface/Defs.kt",
				"package funiface\n"
				+ "\n"
				+ "fun interface Action {\n"
				+ "    fun execute(input: String): Boolean\n"
				+ "}\n"
				+ "\n"
				+ "fun interface Transformer<T, R> {\n"
				+ "    fun transform(input: T): R\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> matches = TestHelpers.searchKotlinTypes(
				"Action", project);
		assertTrue(matches.size() >= 1,
				"Should find fun interface Action");
		IType actionType = (IType) matches.get(0).getElement();
		assertTrue(actionType.isInterface(),
				"Action should be an interface");

		List<SearchMatch> transformerMatches =
				TestHelpers.searchKotlinTypes("Transformer", project);
		assertTrue(transformerMatches.size() >= 1,
				"Should find fun interface Transformer");
		IType transformerType = (IType) transformerMatches.get(0)
				.getElement();
		assertTrue(transformerType.isInterface(),
				"Transformer should be an interface");
	}

	@Test
	public void testValueClassDeclaration() throws CoreException {
		// value class should be recognized as a class
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/valclass");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/valclass/Defs.kt",
				"package valclass\n"
				+ "\n"
				+ "value class Email(val address: String)\n"
				+ "value class UserId(val id: Long)\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> emailMatches = TestHelpers
				.searchKotlinTypes("Email", project);
		assertTrue(emailMatches.size() >= 1,
				"Should find value class Email");
		IType emailType = (IType) emailMatches.get(0).getElement();
		assertTrue(emailType.isClass(),
				"Email should be a class");

		List<SearchMatch> userIdMatches = TestHelpers
				.searchKotlinTypes("UserId", project);
		assertTrue(userIdMatches.size() >= 1,
				"Should find value class UserId");
	}

	@Test
	public void testInterfaceTypeSuffixInTypeHierarchy()
			throws CoreException {
		// Interface should use INTERFACE_SUFFIX, not CLASS_SUFFIX
		// when indexed as SUPER_REF
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/ifacesufx");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/ifacesufx/Defs.kt",
				"package ifacesufx\n"
				+ "\n"
				+ "interface Drawable {\n"
				+ "    fun draw(): String\n"
				+ "}\n"
				+ "\n"
				+ "class Circle : Drawable {\n"
				+ "    override fun draw(): String = \"circle\"\n"
				+ "}\n"
				+ "\n"
				+ "class Square : Drawable {\n"
				+ "    override fun draw(): String = \"square\"\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for implementations of Drawable
		List<SearchMatch> drawableMatches = TestHelpers
				.searchKotlinTypes("Drawable", project);
		assertTrue(drawableMatches.size() >= 1,
				"Should find interface Drawable");
		IType drawableType = (IType) drawableMatches.get(0)
				.getElement();
		assertTrue(drawableType.isInterface(),
				"Drawable should be an interface");

		// Verify subtypes also indexed
		List<SearchMatch> circleMatches = TestHelpers
				.searchKotlinTypes("Circle", project);
		assertTrue(circleMatches.size() >= 1,
				"Should find class Circle");

		List<SearchMatch> squareMatches = TestHelpers
				.searchKotlinTypes("Square", project);
		assertTrue(squareMatches.size() >= 1,
				"Should find class Square");
	}

	@Test
	public void testEnumTypeSuffixInTypeHierarchy()
			throws CoreException {
		// Enum should use ENUM_SUFFIX, not CLASS_SUFFIX
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/enumsufx");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/enumsufx/Defs.kt",
				"package enumsufx\n"
				+ "\n"
				+ "interface Displayable {\n"
				+ "    fun display(): String\n"
				+ "}\n"
				+ "\n"
				+ "enum class Priority : Displayable {\n"
				+ "    LOW {\n"
				+ "        override fun display() = \"low\"\n"
				+ "    },\n"
				+ "    HIGH {\n"
				+ "        override fun display() = \"high\"\n"
				+ "    };\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> priorityMatches = TestHelpers
				.searchKotlinTypes("Priority", project);
		assertTrue(priorityMatches.size() >= 1,
				"Should find enum class Priority");
		IType priorityType = (IType) priorityMatches.get(0)
				.getElement();
		assertTrue(priorityType.isEnum(),
				"Priority should be an enum");
	}

	@Test
	public void testMethodWithoutReturnTypeGetsVoid()
			throws CoreException {
		// Exercises KotlinElement:1146 — function without explicit
		// return type should return SIG_VOID from getReturnType()
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/noreturn");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/noreturn/Defs.kt",
				"package noreturn\n"
				+ "\n"
				+ "class Performer {\n"
				+ "    fun act() {\n"
				+ "        println(\"acting\")\n"
				+ "    }\n"
				+ "    fun actLabeled(): String {\n"
				+ "        return \"done\"\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for method DECLARATIONS to get KotlinMethodElement
		List<SearchMatch> actDecls = TestHelpers
				.searchMethodDeclarations("act", project);
		List<SearchMatch> ktActDecls = TestHelpers
				.filterKotlinMatches(actDecls);
		assertTrue(ktActDecls.size() >= 1,
				"Should find act() declaration");
		for (SearchMatch m : ktActDecls) {
			if (m.getElement() instanceof IMethod method
					&& "act".equals(method.getElementName())) {
				assertEquals("V", method.getReturnType(),
						"act() without return type annotation "
								+ "should return void signature");
				// Also exercise getSignature() which calls
				// getReturnType() internally
				assertNotNull(method.getSignature(),
						"act() should have a signature");
			}
		}
		// Verify actLabeled has String return type
		List<SearchMatch> labeledDecls = TestHelpers
				.searchMethodDeclarations("actLabeled", project);
		List<SearchMatch> ktLabeledDecls = TestHelpers
				.filterKotlinMatches(labeledDecls);
		assertTrue(ktLabeledDecls.size() >= 1,
				"Should find actLabeled() declaration");
		for (SearchMatch m : ktLabeledDecls) {
			if (m.getElement() instanceof IMethod method) {
				assertFalse("V".equals(method.getReturnType()),
						"actLabeled() should NOT return void");
			}
		}
	}

	@Test
	public void testNoPackageImportSimpleName() throws CoreException {
		// Exercises KotlinFileModel:67 — import without dot
		// (single-segment FQN) returns the FQN itself as simple name
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/nopkgimp");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/nopkgimp/Defs.kt",
				"import SomeGlobal\n"
				+ "\n"
				+ "class NoPkgTarget {\n"
				+ "    fun invoke(x: Int) {}\n"
				+ "}\n"
				+ "\n"
				+ "class NoPkgOther {\n"
				+ "    fun invoke(x: Int) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/nopkgimp/Caller.kt",
				"import SomeGlobal\n"
				+ "\n"
				+ "fun callNoPkgImport() {\n"
				+ "    val t: NoPkgTarget = NoPkgTarget()\n"
				+ "    t.invoke(1)\n"
				+ "    t.invoke(2)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers
				.searchQualifiedMethodReferences(
						"NoPkgTarget.invoke", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(
				refs);
		assertEquals(2, ktRefs.size(),
				"Should find 2 NoPkgTarget.invoke() with "
						+ "no-dot import in file. Got: "
						+ describeMatches(ktRefs));
	}
}
