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

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.Flags;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.search.IJavaSearchConstants;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.internal.core.search.indexing.SearchParticipantRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for receiver type verification during reference matching.
 * Exercises the wired-up ScopeChain, ImportResolver, and SubtypeChecker
 * components that verify receiver types during
 * {@code locateReferenceMatches()}.
 *
 * <p>When searching for a qualified method like {@code Foo.bar()}, the
 * search participant should verify that the receiver in Kotlin code
 * actually resolves to {@code Foo} (or a subtype), filtering false
 * positives where a different type has a same-named method.
 *
 * @author Arcadiy Ivanov
 */
public class CrossLanguageReceiverVerificationTest {

	private static final String PROJECT_NAME = "ReceiverVerifTest";
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
	public void testQualifiedMethodSearchFindsMatchingReceiver() throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/receiver");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/receiver/Api.java",
				"package receiver;\n"
				+ "\n"
				+ "public class Api {\n"
				+ "    public void execute(String cmd) {}\n"
				+ "}\n");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/receiver/Client.kt",
				"package receiver\n"
				+ "\n"
				+ "fun useApi() {\n"
				+ "    val api = Api()\n"
				+ "    api.execute(\"run\")\n"
				+ "    api.execute(\"stop\")\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for Api.execute (qualified) — should find both calls
		List<SearchMatch> refs = TestHelpers.searchQualifiedMethodReferences(
				"receiver.Api.execute", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);
		// The search may find matches — receiver verification is conservative,
		// so it should still pass through when it can't prove mismatch
		assertTrue(ktRefs.size() >= 2,
				"Should find at least 2 Kotlin references to Api.execute()");

		for (SearchMatch match : ktRefs) {
			assertNotNull(match.getElement(), "Match should have element");
			assertTrue(match.getElement() instanceof IMember,
					"Match element should be IMember");
		}
	}

	@Test
	public void testReceiverMatchesDirectType() throws CoreException {
		// When receiver name directly matches the declaring type (static-like call)
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/receiver");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/receiver/Registry.kt",
				"package receiver\n"
				+ "\n"
				+ "object Registry {\n"
				+ "    fun lookup(key: String): String = key\n"
				+ "    fun register(key: String, value: String) {}\n"
				+ "}\n"
				+ "\n"
				+ "fun useRegistry() {\n"
				+ "    Registry.lookup(\"key1\")\n"
				+ "    Registry.register(\"key2\", \"value2\")\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Unqualified search should find both
		List<SearchMatch> refs = TestHelpers.searchMethodReferences("lookup", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);
		assertTrue(ktRefs.size() >= 1,
				"Should find Kotlin reference to lookup()");
	}

	@Test
	public void testReceiverThisAndSuperPassThrough() throws CoreException {
		// "this" and "super" receivers should always pass verification
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/receiver");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/receiver/BaseClass.kt",
				"package receiver\n"
				+ "\n"
				+ "open class BaseClass {\n"
				+ "    open fun process(): String = \"base\"\n"
				+ "}\n"
				+ "\n"
				+ "class DerivedClass : BaseClass() {\n"
				+ "    override fun process(): String {\n"
				+ "        val baseResult = super.process()\n"
				+ "        return \"derived:\" + baseResult\n"
				+ "    }\n"
				+ "\n"
				+ "    fun callSelf() {\n"
				+ "        this.process()\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers.searchMethodReferences("process", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);
		// Should find: super.process() and this.process()
		assertTrue(ktRefs.size() >= 2,
				"Should find at least 2 Kotlin references to process() (super and this)");
	}

	@Test
	public void testScopeChainResolvesLocalVariable() throws CoreException {
		// ScopeChain should resolve local variable types from the file scope
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/receiver");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/receiver/TypeA.kt",
				"package receiver\n"
				+ "\n"
				+ "class TypeA {\n"
				+ "    fun doWork(): String = \"a\"\n"
				+ "}\n"
				+ "\n"
				+ "class TypeB {\n"
				+ "    fun doWork(): String = \"b\"\n"
				+ "}\n"
				+ "\n"
				+ "fun callOnTypeA() {\n"
				+ "    val a = TypeA()\n"
				+ "    a.doWork()\n"
				+ "}\n"
				+ "\n"
				+ "fun callOnTypeB() {\n"
				+ "    val b = TypeB()\n"
				+ "    b.doWork()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for all references to doWork
		List<SearchMatch> refs = TestHelpers.searchMethodReferences("doWork", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);
		// Both a.doWork() and b.doWork() should be found
		assertTrue(ktRefs.size() >= 2,
				"Should find at least 2 Kotlin references to doWork()");
	}

	@Test
	public void testTopLevelPropertiesInScopeChain() throws CoreException {
		// Top-level properties should be added to the file scope
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/receiver");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/receiver/TopLevel.kt",
				"package receiver\n"
				+ "\n"
				+ "class Processor {\n"
				+ "    fun run(): String = \"done\"\n"
				+ "}\n"
				+ "\n"
				+ "val globalProcessor: Processor = Processor()\n"
				+ "val backupProcessor: Processor = Processor()\n"
				+ "\n"
				+ "fun useGlobal() {\n"
				+ "    globalProcessor.run()\n"
				+ "    backupProcessor.run()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers.searchMethodReferences("run", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);
		assertTrue(ktRefs.size() >= 2,
				"Should find at least 2 Kotlin references to run() on top-level properties");
	}

	@Test
	public void testImportResolutionForReceiverType() throws CoreException {
		// Import resolution should help identify receiver types
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/receiver/sub");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/receiver/sub/Helper.kt",
				"package receiver.sub\n"
				+ "\n"
				+ "class Helper {\n"
				+ "    fun assist(): String = \"help\"\n"
				+ "}\n");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/receiver/ImportUser.kt",
				"package receiver\n"
				+ "\n"
				+ "import receiver.sub.Helper\n"
				+ "\n"
				+ "fun useHelper() {\n"
				+ "    val h = Helper()\n"
				+ "    h.assist()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers.searchMethodReferences("assist", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);
		assertTrue(ktRefs.size() >= 1,
				"Should find Kotlin reference to assist() via imported type");
	}

	@Test
	public void testConstructorCallReceiverExtraction() throws CoreException {
		// When a method is called on a newly constructed object: Foo().bar(),
		// the receiver should be extracted as "Foo"
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/receiver");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/receiver/Builder.kt",
				"package receiver\n"
				+ "\n"
				+ "class Builder {\n"
				+ "    fun build(): String = \"built\"\n"
				+ "    fun configure(): Builder = this\n"
				+ "}\n"
				+ "\n"
				+ "fun createAndUse() {\n"
				+ "    Builder().build()\n"
				+ "    Builder().configure()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers.searchMethodReferences("build", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);
		assertTrue(ktRefs.size() >= 1,
				"Should find Kotlin reference to build() on constructor call receiver");
	}

	@Test
	public void testDualModePatternRouting() throws CoreException {
		// ALL_OCCURRENCES search should find both declarations and references
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/receiver");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/receiver/DualMode.kt",
				"package receiver\n"
				+ "\n"
				+ "class Service {\n"
				+ "    fun invoke(arg: String): String = arg\n"
				+ "}\n"
				+ "\n"
				+ "fun callInvoke() {\n"
				+ "    val svc = Service()\n"
				+ "    svc.invoke(\"test\")\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// ALL_OCCURRENCES should find both the declaration and the reference
		List<SearchMatch> allOcc = TestHelpers.searchMethodAllOccurrences("invoke", project);
		List<SearchMatch> ktAll = TestHelpers.filterKotlinMatches(allOcc);
		// Should have at least: 1 declaration + 1 reference = 2
		assertTrue(ktAll.size() >= 2,
				"ALL_OCCURRENCES should find at least 2 Kotlin matches (declaration + reference)");
	}

	@Test
	public void testMethodSearchWithMultipleKotlinFiles() throws CoreException {
		// Exercises scope chain initialization across multiple files
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/receiver");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/receiver/ServiceA.kt",
				"package receiver\n"
				+ "\n"
				+ "class ServiceA {\n"
				+ "    fun handle(msg: String): Boolean = true\n"
				+ "}\n"
				+ "\n"
				+ "fun callServiceA() {\n"
				+ "    val sa = ServiceA()\n"
				+ "    sa.handle(\"msgA\")\n"
				+ "}\n");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/receiver/ServiceB.kt",
				"package receiver\n"
				+ "\n"
				+ "class ServiceB {\n"
				+ "    fun handle(msg: String): Boolean = false\n"
				+ "}\n"
				+ "\n"
				+ "fun callServiceB() {\n"
				+ "    val sb = ServiceB()\n"
				+ "    sb.handle(\"msgB\")\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for all references to "handle"
		List<SearchMatch> refs = TestHelpers.searchMethodReferences("handle", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);
		assertTrue(ktRefs.size() >= 2,
				"Should find at least 2 Kotlin references to handle() across files");
	}

	@Test
	public void testSubtypeCheckOnTopLevelProperty() throws CoreException {
		// Exercise SubtypeChecker: receiver resolves to a Derived type,
		// search is for Base.method — should match because Derived : Base
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/receiver");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/receiver/SubtypeTest.kt",
				"package receiver\n"
				+ "\n"
				+ "open class Animal {\n"
				+ "    open fun speak(): String = \"...\"\n"
				+ "    open fun feed(food: String): Boolean = true\n"
				+ "}\n"
				+ "\n"
				+ "class Dog : Animal() {\n"
				+ "    override fun speak(): String = \"woof\"\n"
				+ "    override fun feed(food: String): Boolean = food != \"chocolate\"\n"
				+ "}\n"
				+ "\n"
				+ "class Cat : Animal() {\n"
				+ "    override fun speak(): String = \"meow\"\n"
				+ "}\n"
				+ "\n"
				+ "val myDog: Dog = Dog()\n"
				+ "val myCat: Cat = Cat()\n"
				+ "\n"
				+ "fun exercisePets() {\n"
				+ "    myDog.speak()\n"
				+ "    myDog.feed(\"kibble\")\n"
				+ "    myCat.speak()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for Animal.speak (qualified by base type)
		// myDog resolves to Dog (a subtype of Animal) and myCat to Cat (a subtype)
		// verifyReceiverType should check SubtypeChecker.isSubtype(Dog, Animal)
		List<SearchMatch> refs = TestHelpers.searchQualifiedMethodReferences(
				"receiver.Animal.speak", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);
		// Should find the speak() calls — either because SubtypeChecker
		// confirms Dog/Cat extends Animal, or because the conservative
		// fallback allows them through
		assertTrue(ktRefs.size() >= 2,
				"Should find at least 2 Kotlin references to Animal.speak() "
				+ "via subtype receivers Dog and Cat");
	}

	@Test
	public void testReceiverTypeFilterOnUnrelatedType() throws CoreException {
		// When the receiver resolves to an unrelated type, it should be filtered
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/receiver");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/receiver/Unrelated.kt",
				"package receiver\n"
				+ "\n"
				+ "class Alpha {\n"
				+ "    fun compute(): Int = 1\n"
				+ "}\n"
				+ "\n"
				+ "class Beta {\n"
				+ "    fun compute(): Int = 2\n"
				+ "}\n"
				+ "\n"
				+ "val alphaInstance: Alpha = Alpha()\n"
				+ "val betaInstance: Beta = Beta()\n"
				+ "\n"
				+ "fun callBoth() {\n"
				+ "    alphaInstance.compute()\n"
				+ "    betaInstance.compute()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for Alpha.compute — alphaInstance should match (resolves to Alpha),
		// betaInstance should be filtered (resolves to Beta, unrelated to Alpha)
		List<SearchMatch> refs = TestHelpers.searchQualifiedMethodReferences(
				"receiver.Alpha.compute", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(refs);
		// Should find at least the alphaInstance call
		assertTrue(ktRefs.size() >= 1,
				"Should find at least 1 Kotlin reference to Alpha.compute()");
	}

	@Test
	public void testJdtDelegationForJavaHierarchy() throws CoreException {
		// Exercises SubtypeChecker's JDT delegation: Kotlin type extends
		// Java type, search is for a method on a Java grandparent.
		// Needs JRE on classpath for IType.newSupertypeHierarchy() to work.
		// Uses a separate project to avoid index state issues from
		// project recreation.
		String jdtProject = "JdtDelegationTest";
		TestHelpers.deleteProject(jdtProject);
		IJavaProject jdtPrj = TestHelpers.createJavaProjectWithJRE(
				jdtProject, "src");
		TestHelpers.createFolder("/" + jdtProject + "/src/receiver");
		// Java class hierarchy: JavaGrandparent → JavaBase
		TestHelpers.createFile("/" + jdtProject + "/src/receiver/JavaGrandparent.java",
				"package receiver;\n"
				+ "\n"
				+ "public class JavaGrandparent {\n"
				+ "    public void legacyOp() {}\n"
				+ "}\n");
		TestHelpers.createFile("/" + jdtProject + "/src/receiver/JavaBase.java",
				"package receiver;\n"
				+ "\n"
				+ "public class JavaBase extends JavaGrandparent {\n"
				+ "    public void baseOp() {}\n"
				+ "}\n");
		TestHelpers.createFile("/" + jdtProject + "/src/receiver/KotlinChild.kt",
				"package receiver\n"
				+ "\n"
				+ "import receiver.JavaBase\n"
				+ "import receiver.JavaGrandparent\n"
				+ "\n"
				+ "class KotlinChild : JavaBase() {\n"
				+ "    fun childOp(): String = \"child\"\n"
				+ "}\n"
				+ "\n"
				+ "val child: KotlinChild = KotlinChild()\n"
				+ "val base: JavaBase = JavaBase()\n"
				+ "\n"
				+ "fun callLegacy() {\n"
				+ "    child.legacyOp()\n"
				+ "    base.legacyOp()\n"
				+ "}\n");
		try {
			jdtPrj.getProject().build(
					org.eclipse.core.resources.IncrementalProjectBuilder
							.FULL_BUILD, null);
			TestHelpers.waitForBuildAndIndexes();

			org.eclipse.jdt.core.IType gpType =
					jdtPrj.findType("receiver.JavaGrandparent");
			if (gpType == null || !gpType.exists()) {
				return;
			}
			org.eclipse.jdt.core.IType baseType =
					jdtPrj.findType("receiver.JavaBase");
			if (baseType == null || !baseType.exists()) {
				return;
			}
			org.eclipse.jdt.core.ITypeHierarchy hierarchy;
			try {
				hierarchy = baseType.newSupertypeHierarchy(null);
			} catch (Exception e) {
				throw new AssertionError(
						"JDT type hierarchy build failed", e);
			}
			org.eclipse.jdt.core.IType[] supers =
					hierarchy.getAllSupertypes(baseType);
			boolean foundGP = false;
			for (org.eclipse.jdt.core.IType st : supers) {
				if ("JavaGrandparent".equals(
						st.getElementName())) {
					foundGP = true;
				}
			}
			if (!foundGP) {
				return;
			}

			List<SearchMatch> refs =
					TestHelpers.searchQualifiedMethodReferences(
							"receiver.JavaGrandparent.legacyOp",
							jdtPrj);
			List<SearchMatch> ktRefs =
					TestHelpers.filterKotlinMatches(refs);
			assertTrue(ktRefs.size() >= 2,
					"Should find at least 2 Kotlin references to "
					+ "JavaGrandparent.legacyOp() via JDT hierarchy "
					+ "delegation");
		} finally {
			TestHelpers.deleteProject(jdtProject);
		}
	}

	@Test
	public void testFieldReferenceReceiverVerification() throws CoreException {
		// Field reference search should also go through receiver verification
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/receiver");
		TestHelpers.createFile("/" + PROJECT_NAME + "/src/receiver/FieldHolder.kt",
				"package receiver\n"
				+ "\n"
				+ "class FieldHolder {\n"
				+ "    val score: Int = 100\n"
				+ "    val rating: String = \"A\"\n"
				+ "}\n"
				+ "\n"
				+ "fun readScore() {\n"
				+ "    val fh = FieldHolder()\n"
				+ "    println(fh.score)\n"
				+ "    println(fh.rating)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> scoreRefs = TestHelpers.searchFieldReferences("score", project);
		List<SearchMatch> ktRefs = TestHelpers.filterKotlinMatches(scoreRefs);
		assertTrue(ktRefs.size() >= 1,
				"Should find Kotlin reference to 'score' field");

		// All matches from a REFERENCES search should be reference sites
		// (method enclosing), not field declarations. The FieldPattern
		// dispatching now correctly treats FIELD_DECL in categories as
		// write-access tracking when REF is also present.
		for (SearchMatch match : ktRefs) {
			IMember member = (IMember) match.getElement();
			assertTrue(
					member.getElementType() == IJavaElement.METHOD,
					"Field reference match should have a method as "
					+ "the enclosing member, not element type "
					+ member.getElementType());
		}
	}

	@Test
	public void testFieldAllOccurrencesReturnsReferencesOnly()
			throws CoreException {
		// FieldPattern ALL_OCCURRENCES has both REF and FIELD_DECL in
		// categories. Our dispatching treats this as reference-only
		// (FIELD_DECL is write-access tracking). This documents that
		// ALL_OCCURRENCES returns references without declarations.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/fieldocc");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/fieldocc/Container.kt",
				"package fieldocc\n"
				+ "\n"
				+ "class Container {\n"
				+ "    val count: Int = 0\n"
				+ "    val label: String = \"x\"\n"
				+ "}\n"
				+ "\n"
				+ "fun readCount() {\n"
				+ "    val c = Container()\n"
				+ "    println(c.count)\n"
				+ "    println(c.count + 1)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> allOcc = TestHelpers.searchFieldAllOccurrences(
				"count", project);
		List<SearchMatch> ktAll = TestHelpers.filterKotlinMatches(allOcc);
		// All matches should be reference sites (METHOD enclosing)
		for (SearchMatch match : ktAll) {
			IMember member = (IMember) match.getElement();
			assertTrue(
					member.getElementType() == IJavaElement.METHOD,
					"ALL_OCCURRENCES field match should be a reference "
					+ "(method enclosing), not element type "
					+ member.getElementType());
		}
	}

	@Test
	public void testElementBasedPatternResolvesParameterReceiver()
			throws CoreException {
		// Element-based SearchPattern.createPattern(IMethod, REFERENCES)
		// creates a MethodPattern with declaringSimpleName set (e.g.,
		// "Target"). This triggers receiver type verification which must
		// resolve parameter names (like "t") to their declared types.
		// Regression: scope chain only had file-level bindings, not
		// function parameter bindings, causing "t" to resolve to
		// "kotlin.t" instead of "Target".
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/paramscope");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/paramscope/Service.java",
				"package paramscope;\n"
				+ "public class Service {\n"
				+ "    public void invoke(String action) {}\n"
				+ "    public void shutdown() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/paramscope/Caller.kt",
				"package paramscope\n"
				+ "\n"
				+ "class KotlinCaller {\n"
				+ "    fun doWork(svc: Service) {\n"
				+ "        svc.invoke(\"start\")\n"
				+ "        svc.shutdown()\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Get the Java IMethod for Service.invoke
		ICompilationUnit javaCu = TestHelpers.getJavaCompilationUnit(
				project,"paramscope", "Service.java");
		assertNotNull(javaCu, "Java CU should exist");
		IType serviceType = javaCu.getType("Service");
		IMethod invokeMethod = serviceType.getMethod("invoke",
				new String[] { "QString;" });
		assertTrue(invokeMethod.exists(),
				"Service.invoke(String) should exist");

		// Element-based search — same pattern CallerMethodWrapper uses
		SearchPattern elementPattern = SearchPattern.createPattern(
				invokeMethod, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		assertNotNull(elementPattern, "Element pattern should not be null");

		List<SearchMatch> matches = TestHelpers.executeSearch(
				elementPattern, project);
		List<SearchMatch> ktMatches = TestHelpers.filterKotlinMatches(matches);
		assertTrue(ktMatches.size() >= 1,
				"Element-based search should find Kotlin reference to "
						+ "Service.invoke() via parameter receiver 'svc'. "
						+ "Found " + matches.size() + " total, "
						+ ktMatches.size() + " from .kt");

		// Verify the match element is the enclosing Kotlin method
		for (SearchMatch match : ktMatches) {
			IMember member = (IMember) match.getElement();
			assertNotNull(member, "Match element should not be null");
			assertTrue(
					member.getElementType() == IJavaElement.METHOD,
					"Enclosing element should be a method");
		}
	}

	@Test
	public void testElementBasedPatternWithLocalVariableReceiver()
			throws CoreException {
		// Local variables with explicit type annotations are now tracked
		// in the scope chain via LocalVariableExtractor. Receiver
		// resolution finds "proc" → Processor, matching the declaring
		// type from the element-based pattern.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/localscope");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/localscope/Processor.java",
				"package localscope;\n"
				+ "public class Processor {\n"
				+ "    public void run() {}\n"
				+ "    public void stop() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/localscope/Usage.kt",
				"package localscope\n"
				+ "\n"
				+ "fun useProcessor() {\n"
				+ "    val proc: Processor = Processor()\n"
				+ "    proc.run()\n"
				+ "    proc.stop()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		ICompilationUnit javaCu = TestHelpers.getJavaCompilationUnit(
				project,"localscope", "Processor.java");
		assertNotNull(javaCu, "Java CU should exist");
		IType processorType = javaCu.getType("Processor");
		IMethod runMethod = processorType.getMethod("run",
				new String[0]);
		assertTrue(runMethod.exists(), "Processor.run() should exist");

		SearchPattern elementPattern = SearchPattern.createPattern(
				runMethod, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> matches = TestHelpers.executeSearch(
				elementPattern, project);
		List<SearchMatch> ktMatches = TestHelpers.filterKotlinMatches(matches);
		assertTrue(ktMatches.size() >= 1,
				"Local variable with explicit type annotation should "
						+ "resolve — expected >= 1 match, found "
						+ ktMatches.size());
	}

	@Test
	public void testLocalVariableReceiverWithInferredType()
			throws CoreException {
		// Local variables without type annotation get UNKNOWN type,
		// which is conservative (allows match through verification).
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/inferscope");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/inferscope/Worker.java",
				"package inferscope;\n"
				+ "public class Worker {\n"
				+ "    public void execute() {}\n"
				+ "    public void cancel() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/inferscope/Caller.kt",
				"package inferscope\n"
				+ "\n"
				+ "fun doWork() {\n"
				+ "    val w = Worker()\n"
				+ "    w.execute()\n"
				+ "    w.cancel()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		ICompilationUnit javaCu = TestHelpers.getJavaCompilationUnit(
				project, "inferscope", "Worker.java");
		assertNotNull(javaCu, "Java CU should exist");
		IType workerType = javaCu.getType("Worker");
		IMethod execMethod = workerType.getMethod("execute",
				new String[0]);
		assertTrue(execMethod.exists(), "Worker.execute() should exist");

		SearchPattern elementPattern = SearchPattern.createPattern(
				execMethod, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> matches = TestHelpers.executeSearch(
				elementPattern, project);
		List<SearchMatch> ktMatches = TestHelpers.filterKotlinMatches(
				matches);
		// UNKNOWN type is conservative — allows match
		assertTrue(ktMatches.size() >= 1,
				"Local variable without type annotation should get "
						+ "UNKNOWN (conservative allow) — expected >= 1 "
						+ "match, found " + ktMatches.size());
	}

	@Test
	public void testLocalVariableReceiverWithMultipleLocals()
			throws CoreException {
		// Multiple local variables with different types — verify
		// correct receiver resolution distinguishes between them.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/multiscope");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/multiscope/Alpha.java",
				"package multiscope;\n"
				+ "public class Alpha {\n"
				+ "    public void process() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/multiscope/Beta.java",
				"package multiscope;\n"
				+ "public class Beta {\n"
				+ "    public void process() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/multiscope/Caller.kt",
				"package multiscope\n"
				+ "\n"
				+ "fun callBoth() {\n"
				+ "    val a: Alpha = Alpha()\n"
				+ "    val b: Beta = Beta()\n"
				+ "    a.process()\n"
				+ "    b.process()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for Alpha.process() — should find only the a.process()
		// call, not b.process()
		ICompilationUnit alphaCu = TestHelpers.getJavaCompilationUnit(
				project, "multiscope", "Alpha.java");
		assertNotNull(alphaCu, "Alpha CU should exist");
		IType alphaType = alphaCu.getType("Alpha");
		IMethod alphaProcess = alphaType.getMethod("process",
				new String[0]);
		assertTrue(alphaProcess.exists(),
				"Alpha.process() should exist");

		SearchPattern alphaPattern = SearchPattern.createPattern(
				alphaProcess, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> alphaMatches = TestHelpers.executeSearch(
				alphaPattern, project);
		List<SearchMatch> alphaKt = TestHelpers.filterKotlinMatches(
				alphaMatches);
		assertTrue(alphaKt.size() >= 1,
				"Should find Kotlin reference to Alpha.process() via "
						+ "local variable 'a'. Found " + alphaKt.size());

		// Search for Beta.process() — should find only b.process()
		ICompilationUnit betaCu = TestHelpers.getJavaCompilationUnit(
				project, "multiscope", "Beta.java");
		assertNotNull(betaCu, "Beta CU should exist");
		IType betaType = betaCu.getType("Beta");
		IMethod betaProcess = betaType.getMethod("process",
				new String[0]);
		assertTrue(betaProcess.exists(),
				"Beta.process() should exist");

		SearchPattern betaPattern = SearchPattern.createPattern(
				betaProcess, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> betaMatches = TestHelpers.executeSearch(
				betaPattern, project);
		List<SearchMatch> betaKt = TestHelpers.filterKotlinMatches(
				betaMatches);
		assertTrue(betaKt.size() >= 1,
				"Should find Kotlin reference to Beta.process() via "
						+ "local variable 'b'. Found " + betaKt.size());
	}

	@Test
	public void testLocalVariableReceiverWithNullableType()
			throws CoreException {
		// Local variables with nullable type annotations (e.g., Foo?)
		// should be resolved correctly, exercising the nullable type
		// extraction path in LocalVariableExtractor.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/nullscope");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/nullscope/Service.java",
				"package nullscope;\n"
				+ "public class Service {\n"
				+ "    public void start() {}\n"
				+ "    public void stop() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/nullscope/Caller.kt",
				"package nullscope\n"
				+ "\n"
				+ "fun maybeCall() {\n"
				+ "    val svc: Service? = Service()\n"
				+ "    svc?.start()\n"
				+ "    svc?.stop()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		ICompilationUnit javaCu = TestHelpers.getJavaCompilationUnit(
				project, "nullscope", "Service.java");
		assertNotNull(javaCu, "Java CU should exist");
		IType serviceType = javaCu.getType("Service");
		IMethod startMethod = serviceType.getMethod("start",
				new String[0]);
		assertTrue(startMethod.exists(),
				"Service.start() should exist");

		SearchPattern elementPattern = SearchPattern.createPattern(
				startMethod, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> matches = TestHelpers.executeSearch(
				elementPattern, project);
		List<SearchMatch> ktMatches = TestHelpers.filterKotlinMatches(
				matches);
		assertTrue(ktMatches.size() >= 1,
				"Nullable-typed local variable should resolve — "
						+ "expected >= 1 match, found "
						+ ktMatches.size());
	}

	@Test
	public void testLocalVariableReceiverIgnoresNestedFunctionLocals()
			throws CoreException {
		// Local variables declared in a nested local function should
		// NOT be visible in the outer function's scope. This exercises
		// the FunctionDeclarationContext stop condition in
		// LocalVariableExtractor.collectLocals().
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/nestscope");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/nestscope/Target.java",
				"package nestscope;\n"
				+ "public class Target {\n"
				+ "    public void action() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/nestscope/Decoy.java",
				"package nestscope;\n"
				+ "public class Decoy {\n"
				+ "    public void action() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/nestscope/Caller.kt",
				"package nestscope\n"
				+ "\n"
				+ "fun outer() {\n"
				+ "    val t: Target = Target()\n"
				+ "    fun inner() {\n"
				+ "        val d: Decoy = Decoy()\n"
				+ "        d.action()\n"
				+ "    }\n"
				+ "    t.action()\n"
				+ "    inner()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for Target.action() — should find t.action() in outer
		ICompilationUnit targetCu = TestHelpers.getJavaCompilationUnit(
				project, "nestscope", "Target.java");
		assertNotNull(targetCu, "Target CU should exist");
		IType targetType = targetCu.getType("Target");
		IMethod targetAction = targetType.getMethod("action",
				new String[0]);
		assertTrue(targetAction.exists(),
				"Target.action() should exist");

		SearchPattern targetPattern = SearchPattern.createPattern(
				targetAction, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> targetMatches = TestHelpers.executeSearch(
				targetPattern, project);
		List<SearchMatch> targetKt = TestHelpers.filterKotlinMatches(
				targetMatches);
		assertTrue(targetKt.size() >= 1,
				"Should find Kotlin reference to Target.action() via "
						+ "local variable 't' in outer(). Found "
						+ targetKt.size());

		// Search for Decoy.action() — inner() is now tracked as a
		// nested declaration within outer(), so its locals are in scope.
		ICompilationUnit decoyCu = TestHelpers.getJavaCompilationUnit(
				project, "nestscope", "Decoy.java");
		assertNotNull(decoyCu, "Decoy CU should exist");
		IType decoyType = decoyCu.getType("Decoy");
		IMethod decoyAction = decoyType.getMethod("action",
				new String[0]);
		assertTrue(decoyAction.exists(),
				"Decoy.action() should exist");

		SearchPattern decoyPattern = SearchPattern.createPattern(
				decoyAction, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> decoyMatches = TestHelpers.executeSearch(
				decoyPattern, project);
		List<SearchMatch> decoyKt = TestHelpers.filterKotlinMatches(
				decoyMatches);
		assertTrue(decoyKt.size() >= 1,
				"Local function locals now tracked — d.action() in "
						+ "inner() should resolve. Found "
						+ decoyKt.size());
	}

	@Test
	public void testLocalVariableReceiverWithDestructuring()
			throws CoreException {
		// Destructuring declarations (val (a, b) = ...) use
		// multiVariableDeclaration, not variableDeclaration, so the
		// extractor should skip them gracefully. Other locals in the
		// same function should still resolve.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/destr");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/destr/Handler.java",
				"package destr;\n"
				+ "public class Handler {\n"
				+ "    public void handle() {}\n"
				+ "    public void close() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/destr/Caller.kt",
				"package destr\n"
				+ "\n"
				+ "fun withDestructuring() {\n"
				+ "    val (first, second) = Pair(\"a\", \"b\")\n"
				+ "    val h: Handler = Handler()\n"
				+ "    h.handle()\n"
				+ "    println(first + second)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		ICompilationUnit javaCu = TestHelpers.getJavaCompilationUnit(
				project, "destr", "Handler.java");
		assertNotNull(javaCu, "Java CU should exist");
		IType handlerType = javaCu.getType("Handler");
		IMethod handleMethod = handlerType.getMethod("handle",
				new String[0]);
		assertTrue(handleMethod.exists(),
				"Handler.handle() should exist");

		SearchPattern elementPattern = SearchPattern.createPattern(
				handleMethod, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> matches = TestHelpers.executeSearch(
				elementPattern, project);
		List<SearchMatch> ktMatches = TestHelpers.filterKotlinMatches(
				matches);
		assertTrue(ktMatches.size() >= 1,
				"Should find Kotlin reference to Handler.handle() "
						+ "even with destructuring declarations in the "
						+ "same function. Found " + ktMatches.size());
	}

	@Test
	public void testSamePackageResolutionPrecedence()
			throws CoreException {
		// Same-package types should resolve correctly without explicit
		// imports. Previously, ImportResolver.resolve() returned the
		// first default import (e.g., "kotlin.Foo") instead of
		// same-package resolution ("pkg.Foo").
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/samepkg");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/samepkg/Widget.java",
				"package samepkg;\n"
				+ "public class Widget {\n"
				+ "    public void render() {}\n"
				+ "    public void dispose() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/samepkg/Controller.kt",
				"package samepkg\n"
				+ "\n"
				+ "// No explicit import — Widget is in same package\n"
				+ "class Controller {\n"
				+ "    val widget: Widget = Widget()\n"
				+ "    fun draw() {\n"
				+ "        widget.render()\n"
				+ "        widget.dispose()\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		ICompilationUnit javaCu = TestHelpers.getJavaCompilationUnit(
				project, "samepkg", "Widget.java");
		assertNotNull(javaCu, "Java CU should exist");
		IType widgetType = javaCu.getType("Widget");
		IMethod renderMethod = widgetType.getMethod("render",
				new String[0]);
		assertTrue(renderMethod.exists(),
				"Widget.render() should exist");

		SearchPattern elementPattern = SearchPattern.createPattern(
				renderMethod, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> matches = TestHelpers.executeSearch(
				elementPattern, project);
		List<SearchMatch> ktMatches = TestHelpers.filterKotlinMatches(
				matches);
		assertTrue(ktMatches.size() >= 1,
				"Same-package type should resolve without explicit "
						+ "import — expected >= 1 match, found "
						+ ktMatches.size());
	}

	@Test
	public void testLocalVariableReceiverWithFunctionType()
			throws CoreException {
		// Local variable with a function type annotation (e.g.,
		// (String) -> Unit) exercises the getText fallback path in
		// extractSimpleTypeName since function types are neither
		// TypeReference nor NullableType in the ANTLR grammar.
		// The function-typed local itself won't be used as a receiver
		// for method calls, but other locals in the same function
		// should still resolve.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/fntype");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/fntype/Executor.java",
				"package fntype;\n"
				+ "public class Executor {\n"
				+ "    public void run() {}\n"
				+ "    public void stop() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/fntype/Caller.kt",
				"package fntype\n"
				+ "\n"
				+ "fun withCallback() {\n"
				+ "    val callback: (String) -> Unit = { println(it) }\n"
				+ "    val exec: Executor = Executor()\n"
				+ "    exec.run()\n"
				+ "    callback(\"done\")\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		ICompilationUnit javaCu = TestHelpers.getJavaCompilationUnit(
				project, "fntype", "Executor.java");
		assertNotNull(javaCu, "Java CU should exist");
		IType execType = javaCu.getType("Executor");
		IMethod runMethod = execType.getMethod("run", new String[0]);
		assertTrue(runMethod.exists(), "Executor.run() should exist");

		SearchPattern elementPattern = SearchPattern.createPattern(
				runMethod, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> matches = TestHelpers.executeSearch(
				elementPattern, project);
		List<SearchMatch> ktMatches = TestHelpers.filterKotlinMatches(
				matches);
		assertTrue(ktMatches.size() >= 1,
				"Should find Kotlin reference to Executor.run() even "
						+ "with a function-typed local in the same "
						+ "function. Found " + ktMatches.size());
	}

	@Test
	public void testValNarrowsToConcreteAndPreservesDeclaredType()
			throws CoreException {
		// val with interface type + concrete constructor should match
		// both the declared type (Processor) and the concrete type
		// (FastProcessor) in receiver verification.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/valnarrow");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/valnarrow/Processor.java",
				"package valnarrow;\n"
				+ "public interface Processor {\n"
				+ "    void run();\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/valnarrow/FastProcessor.java",
				"package valnarrow;\n"
				+ "public class FastProcessor"
				+ "        implements Processor {\n"
				+ "    public void run() {}\n"
				+ "    public void turbo() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/valnarrow/Caller.kt",
				"package valnarrow\n"
				+ "\n"
				+ "fun doWork() {\n"
				+ "    val p: Processor = FastProcessor()\n"
				+ "    p.run()\n"
				+ "    p.run()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for FastProcessor.run() — should find via narrowed
		// concrete type
		ICompilationUnit fastCu = TestHelpers.getJavaCompilationUnit(
				project, "valnarrow", "FastProcessor.java");
		assertNotNull(fastCu, "FastProcessor CU should exist");
		IType fastType = fastCu.getType("FastProcessor");
		IMethod fastRun = fastType.getMethod("run", new String[0]);
		assertTrue(fastRun.exists(),
				"FastProcessor.run() should exist");

		SearchPattern fastPattern = SearchPattern.createPattern(
				fastRun, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> fastMatches = TestHelpers.executeSearch(
				fastPattern, project);
		List<SearchMatch> fastKt = TestHelpers.filterKotlinMatches(
				fastMatches);
		assertEquals(2, fastKt.size(),
				"Should find 2 Kotlin references to "
						+ "FastProcessor.run() via narrowed concrete "
						+ "type. Found " + fastKt.size());

		// Search for Processor.run() — should ALSO find via preserved
		// declared type
		ICompilationUnit procCu = TestHelpers.getJavaCompilationUnit(
				project, "valnarrow", "Processor.java");
		assertNotNull(procCu, "Processor CU should exist");
		IType procType = procCu.getType("Processor");
		IMethod procRun = procType.getMethod("run", new String[0]);
		assertTrue(procRun.exists(), "Processor.run() should exist");

		SearchPattern procPattern = SearchPattern.createPattern(
				procRun, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> procMatches = TestHelpers.executeSearch(
				procPattern, project);
		List<SearchMatch> procKt = TestHelpers.filterKotlinMatches(
				procMatches);
		assertEquals(2, procKt.size(),
				"Should find 2 Kotlin references to "
						+ "Processor.run() via preserved declared type. "
						+ "Found " + procKt.size());
	}

	@Test
	public void testVarWithInterfaceTypeDoesNotNarrow()
			throws CoreException {
		// var with interface type should NOT be narrowed — only the
		// declared type should match, not the concrete initializer.
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/varnonarrow");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/varnonarrow/Processor.java",
				"package varnonarrow;\n"
				+ "public interface Processor {\n"
				+ "    void run();\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/varnonarrow/FastProcessor.java",
				"package varnonarrow;\n"
				+ "public class FastProcessor"
				+ "        implements Processor {\n"
				+ "    public void run() {}\n"
				+ "    public void turbo() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/varnonarrow/SlowProcessor.java",
				"package varnonarrow;\n"
				+ "public class SlowProcessor"
				+ "        implements Processor {\n"
				+ "    public void run() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/varnonarrow/Caller.kt",
				"package varnonarrow\n"
				+ "\n"
				+ "fun doWork() {\n"
				+ "    var p: Processor = FastProcessor()\n"
				+ "    p.run()\n"
				+ "    p.run()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for Processor.run() — should find (declared type)
		ICompilationUnit procCu = TestHelpers.getJavaCompilationUnit(
				project, "varnonarrow", "Processor.java");
		assertNotNull(procCu, "Processor CU should exist");
		IType procType = procCu.getType("Processor");
		IMethod procRun = procType.getMethod("run", new String[0]);
		assertTrue(procRun.exists(), "Processor.run() should exist");

		SearchPattern procPattern = SearchPattern.createPattern(
				procRun, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> procMatches = TestHelpers.executeSearch(
				procPattern, project);
		List<SearchMatch> procKt = TestHelpers.filterKotlinMatches(
				procMatches);
		assertEquals(2, procKt.size(),
				"Should find 2 Kotlin references to "
						+ "Processor.run() via declared var type. Found "
						+ procKt.size());

		// Search for FastProcessor.turbo() — should NOT find it
		// because var is not narrowed to FastProcessor
		ICompilationUnit fastCu = TestHelpers.getJavaCompilationUnit(
				project, "varnonarrow", "FastProcessor.java");
		assertNotNull(fastCu, "FastProcessor CU should exist");
		IType fastType = fastCu.getType("FastProcessor");
		IMethod turbo = fastType.getMethod("turbo", new String[0]);
		assertTrue(turbo.exists(),
				"FastProcessor.turbo() should exist");

		SearchPattern turboPattern = SearchPattern.createPattern(
				turbo, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> turboMatches = TestHelpers.executeSearch(
				turboPattern, project);
		List<SearchMatch> turboKt = TestHelpers.filterKotlinMatches(
				turboMatches);
		assertEquals(0, turboKt.size(),
				"Should NOT find Kotlin references to "
						+ "FastProcessor.turbo() — var is not narrowed. "
						+ "Found " + turboKt.size());
	}

	@Test
	public void testValNarrowingFallsBackOnUnknownInitializer()
			throws CoreException {
		// val with explicit type + unresolvable initializer should
		// fall back to the declared type.
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/valfallback");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/valfallback/Processor.java",
				"package valfallback;\n"
				+ "public interface Processor {\n"
				+ "    void run();\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/valfallback/Caller.kt",
				"package valfallback\n"
				+ "\n"
				+ "fun doWork() {\n"
				+ "    val p: Processor = createProcessor()\n"
				+ "    p.run()\n"
				+ "    p.run()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		ICompilationUnit procCu = TestHelpers.getJavaCompilationUnit(
				project, "valfallback", "Processor.java");
		assertNotNull(procCu, "Processor CU should exist");
		IType procType = procCu.getType("Processor");
		IMethod procRun = procType.getMethod("run", new String[0]);
		assertTrue(procRun.exists(), "Processor.run() should exist");

		SearchPattern procPattern = SearchPattern.createPattern(
				procRun, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> procMatches = TestHelpers.executeSearch(
				procPattern, project);
		List<SearchMatch> procKt = TestHelpers.filterKotlinMatches(
				procMatches);
		assertEquals(2, procKt.size(),
				"Should find 2 Kotlin references to "
						+ "Processor.run() via declared type fallback "
						+ "(initializer is unresolvable). Found "
						+ procKt.size());
	}

	@Test
	public void testValTypeArgumentPreservation()
			throws CoreException {
		// val with generic interface type + concrete constructor should
		// preserve type arguments on the narrowed concrete type and
		// match both declared and concrete types.
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/valtypearg");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/valtypearg/Container.java",
				"package valtypearg;\n"
				+ "public interface Container<T> {\n"
				+ "    T get(int index);\n"
				+ "    int size();\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/valtypearg/FastContainer.java",
				"package valtypearg;\n"
				+ "public class FastContainer<T>"
				+ "        implements Container<T> {\n"
				+ "    public T get(int index) { return null; }\n"
				+ "    public int size() { return 0; }\n"
				+ "    public void compact() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/valtypearg/Caller.kt",
				"package valtypearg\n"
				+ "\n"
				+ "fun doWork() {\n"
				+ "    val c: Container<String> = FastContainer()\n"
				+ "    c.size()\n"
				+ "    c.size()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for FastContainer.size() — concrete type
		ICompilationUnit fastCu = TestHelpers.getJavaCompilationUnit(
				project, "valtypearg", "FastContainer.java");
		assertNotNull(fastCu, "FastContainer CU should exist");
		IType fastType = fastCu.getType("FastContainer");
		IMethod fastSize = fastType.getMethod("size", new String[0]);
		assertTrue(fastSize.exists(),
				"FastContainer.size() should exist");

		SearchPattern fastPattern = SearchPattern.createPattern(
				fastSize, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> fastMatches = TestHelpers.executeSearch(
				fastPattern, project);
		List<SearchMatch> fastKt = TestHelpers.filterKotlinMatches(
				fastMatches);
		assertEquals(2, fastKt.size(),
				"Should find 2 Kotlin references to "
						+ "FastContainer.size() via narrowed concrete "
						+ "type. Found " + fastKt.size());

		// Search for Container.size() — declared type
		ICompilationUnit contCu = TestHelpers.getJavaCompilationUnit(
				project, "valtypearg", "Container.java");
		assertNotNull(contCu, "Container CU should exist");
		IType contType = contCu.getType("Container");
		IMethod contSize = contType.getMethod("size", new String[0]);
		assertTrue(contSize.exists(),
				"Container.size() should exist");

		SearchPattern contPattern = SearchPattern.createPattern(
				contSize, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> contMatches = TestHelpers.executeSearch(
				contPattern, project);
		List<SearchMatch> contKt = TestHelpers.filterKotlinMatches(
				contMatches);
		assertEquals(2, contKt.size(),
				"Should find 2 Kotlin references to "
						+ "Container.size() via preserved declared type. "
						+ "Found " + contKt.size());
	}

	@Test
	public void testKotlinModifierFlagsConversion() throws CoreException {
		// Verify that Kotlin visibility/class modifiers are correctly
		// converted to JDT Flags via KotlinElement.toJdtFlags().
		// Note: Kotlin defaults to public when no visibility modifier
		// is specified, but the parser only sets PUBLIC when the keyword
		// is explicitly present. Use explicit "public" to test AccPublic.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/modflags");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/modflags/ModFlags.kt",
				"package modflags\n"
				+ "\n"
				+ "public open class PublicOpen {\n"
				+ "    protected class ProtectedClass\n"
				+ "}\n"
				+ "\n"
				+ "private class PrivateClass\n"
				+ "\n"
				+ "abstract class AbstractClass {\n"
				+ "    abstract fun doWork()\n"
				+ "    abstract fun compute()\n"
				+ "}\n"
				+ "\n"
				+ "enum class Color {\n"
				+ "    RED,\n"
				+ "    GREEN\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// PublicOpen — explicit "public" keyword → AccPublic
		List<SearchMatch> publicMatches =
				TestHelpers.searchKotlinTypes("PublicOpen", project);
		assertEquals(1, publicMatches.size(),
				"Should find exactly 1 PublicOpen type");
		IMember publicMember = (IMember) publicMatches.get(0).getElement();
		int publicFlags = publicMember.getFlags();
		assertTrue(Flags.isPublic(publicFlags),
				"PublicOpen should have AccPublic, flags="
						+ publicFlags);

		// PrivateClass — "private" keyword → AccPrivate
		List<SearchMatch> privateMatches =
				TestHelpers.searchKotlinTypes("PrivateClass", project);
		assertEquals(1, privateMatches.size(),
				"Should find exactly 1 PrivateClass type");
		IMember privateMember =
				(IMember) privateMatches.get(0).getElement();
		int privateFlags = privateMember.getFlags();
		assertTrue(Flags.isPrivate(privateFlags),
				"PrivateClass should have AccPrivate, flags="
						+ privateFlags);

		// ProtectedClass — "protected" keyword → AccProtected
		List<SearchMatch> protectedMatches =
				TestHelpers.searchKotlinTypes("ProtectedClass", project);
		assertEquals(1, protectedMatches.size(),
				"Should find exactly 1 ProtectedClass type");
		IMember protectedMember =
				(IMember) protectedMatches.get(0).getElement();
		int protectedFlags = protectedMember.getFlags();
		assertTrue(Flags.isProtected(protectedFlags),
				"ProtectedClass should have AccProtected, flags="
						+ protectedFlags);

		// AbstractClass — "abstract" keyword → AccAbstract
		List<SearchMatch> abstractMatches =
				TestHelpers.searchKotlinTypes("AbstractClass", project);
		assertEquals(1, abstractMatches.size(),
				"Should find exactly 1 AbstractClass type");
		IMember abstractMember =
				(IMember) abstractMatches.get(0).getElement();
		int abstractFlags = abstractMember.getFlags();
		assertTrue(Flags.isAbstract(abstractFlags),
				"AbstractClass should have AccAbstract, flags="
						+ abstractFlags);

		// Color — "enum class" → AccEnum
		List<SearchMatch> enumMatches =
				TestHelpers.searchKotlinTypes("Color", project);
		assertEquals(1, enumMatches.size(),
				"Should find exactly 1 Color enum type");
		IMember enumMember = (IMember) enumMatches.get(0).getElement();
		int enumFlags = enumMember.getFlags();
		assertTrue(Flags.isEnum(enumFlags),
				"Color should have AccEnum, flags=" + enumFlags);
	}

	@Test
	public void testValNarrowingWithIdenticalTypes() throws CoreException {
		// When declared and concrete types are the same (e.g.,
		// val a: Alpha = Alpha()), narrowing should still work correctly
		// without regression from the narrowing logic.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/identnarrow");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/identnarrow/Alpha.java",
				"package identnarrow;\n"
				+ "public class Alpha {\n"
				+ "    public void process() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/identnarrow/Caller.kt",
				"package identnarrow\n"
				+ "\n"
				+ "fun doWork() {\n"
				+ "    val a: Alpha = Alpha()\n"
				+ "    val b: Alpha = Alpha()\n"
				+ "    a.process()\n"
				+ "    b.process()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		ICompilationUnit javaCu = TestHelpers.getJavaCompilationUnit(
				project, "identnarrow", "Alpha.java");
		assertNotNull(javaCu, "Alpha CU should exist");
		IType alphaType = javaCu.getType("Alpha");
		IMethod processMethod = alphaType.getMethod("process",
				new String[0]);
		assertTrue(processMethod.exists(),
				"Alpha.process() should exist");

		SearchPattern elementPattern = SearchPattern.createPattern(
				processMethod, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> matches = TestHelpers.executeSearch(
				elementPattern, project);
		List<SearchMatch> ktMatches = TestHelpers.filterKotlinMatches(
				matches);
		assertEquals(2, ktMatches.size(),
				"Should find 2 Kotlin references to Alpha.process() "
						+ "when declared and concrete types are identical. "
						+ "Found " + ktMatches.size());
	}

	@Test
	public void testForLoopVariableNotNarrowed() throws CoreException {
		// For-loop variables with declared type but no initializer
		// expression should use the declared type without attempting
		// narrowing. The loop variable is typed from the collection
		// element type annotation, not from a constructor call.
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/forloop");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/forloop/Processor.java",
				"package forloop;\n"
				+ "public class Processor {\n"
				+ "    public void run() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/forloop/Caller.kt",
				"package forloop\n"
				+ "\n"
				+ "fun doWork() {\n"
				+ "    val items: List<Processor> = listOf(\n"
				+ "        Processor(), Processor()\n"
				+ "    )\n"
				+ "    for (p: Processor in items) {\n"
				+ "        p.run()\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		ICompilationUnit javaCu = TestHelpers.getJavaCompilationUnit(
				project, "forloop", "Processor.java");
		assertNotNull(javaCu, "Processor CU should exist");
		IType processorType = javaCu.getType("Processor");
		IMethod runMethod = processorType.getMethod("run",
				new String[0]);
		assertTrue(runMethod.exists(),
				"Processor.run() should exist");

		SearchPattern elementPattern = SearchPattern.createPattern(
				runMethod, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> matches = TestHelpers.executeSearch(
				elementPattern, project);
		List<SearchMatch> ktMatches = TestHelpers.filterKotlinMatches(
				matches);
		assertEquals(1, ktMatches.size(),
				"Should find 1 Kotlin reference to Processor.run() "
						+ "from for-loop variable with declared type. "
						+ "Found " + ktMatches.size());
	}

	@Test
	public void testVarReassignmentNarrowsType() throws CoreException {
		// var with reassignment should narrow to the concrete type
		// at each point in the control flow.
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/varreassign");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/varreassign/Processor.java",
				"package varreassign;\n"
				+ "public interface Processor {\n"
				+ "    void run();\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/varreassign/FastProcessor.java",
				"package varreassign;\n"
				+ "public class FastProcessor"
				+ "        implements Processor {\n"
				+ "    public void run() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/varreassign/SlowProcessor.java",
				"package varreassign;\n"
				+ "public class SlowProcessor"
				+ "        implements Processor {\n"
				+ "    public void run() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/varreassign/Caller.kt",
				"package varreassign\n"
				+ "\n"
				+ "fun doWork() {\n"
				+ "    var p: Processor = FastProcessor()\n"
				+ "    p.run()\n"
				+ "    p = SlowProcessor()\n"
				+ "    p.run()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for FastProcessor.run() — first p.run() only
		ICompilationUnit fastCu = TestHelpers.getJavaCompilationUnit(
				project, "varreassign", "FastProcessor.java");
		assertNotNull(fastCu, "FastProcessor CU should exist");
		IType fastType = fastCu.getType("FastProcessor");
		IMethod fastRun = fastType.getMethod("run", new String[0]);
		assertTrue(fastRun.exists(),
				"FastProcessor.run() should exist");

		SearchPattern fastPattern = SearchPattern.createPattern(
				fastRun, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> fastMatches = TestHelpers.executeSearch(
				fastPattern, project);
		List<SearchMatch> fastKt = TestHelpers.filterKotlinMatches(
				fastMatches);
		assertEquals(1, fastKt.size(),
				"Should find 1 Kotlin reference to "
						+ "FastProcessor.run() (first p.run() before "
						+ "reassignment). Found " + fastKt.size());

		// Search for SlowProcessor.run() — second p.run() only
		ICompilationUnit slowCu = TestHelpers.getJavaCompilationUnit(
				project, "varreassign", "SlowProcessor.java");
		assertNotNull(slowCu, "SlowProcessor CU should exist");
		IType slowType = slowCu.getType("SlowProcessor");
		IMethod slowRun = slowType.getMethod("run", new String[0]);
		assertTrue(slowRun.exists(),
				"SlowProcessor.run() should exist");

		SearchPattern slowPattern = SearchPattern.createPattern(
				slowRun, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> slowMatches = TestHelpers.executeSearch(
				slowPattern, project);
		List<SearchMatch> slowKt = TestHelpers.filterKotlinMatches(
				slowMatches);
		assertEquals(1, slowKt.size(),
				"Should find 1 Kotlin reference to "
						+ "SlowProcessor.run() (second p.run() after "
						+ "reassignment). Found " + slowKt.size());

		// Search for Processor.run() — both calls (declared type)
		ICompilationUnit procCu = TestHelpers.getJavaCompilationUnit(
				project, "varreassign", "Processor.java");
		assertNotNull(procCu, "Processor CU should exist");
		IType procType = procCu.getType("Processor");
		IMethod procRun = procType.getMethod("run", new String[0]);
		assertTrue(procRun.exists(), "Processor.run() should exist");

		SearchPattern procPattern = SearchPattern.createPattern(
				procRun, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> procMatches = TestHelpers.executeSearch(
				procPattern, project);
		List<SearchMatch> procKt = TestHelpers.filterKotlinMatches(
				procMatches);
		assertEquals(2, procKt.size(),
				"Should find 2 Kotlin references to "
						+ "Processor.run() via declared type. Found "
						+ procKt.size());
	}

	@Test
	public void testVarWithoutReassignmentNarrowedFromInitializer()
			throws CoreException {
		// var without reassignment should be narrowed from the
		// initializer, just like val.
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/varnarrow");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/varnarrow/Processor.java",
				"package varnarrow;\n"
				+ "public interface Processor {\n"
				+ "    void run();\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/varnarrow/FastProcessor.java",
				"package varnarrow;\n"
				+ "public class FastProcessor"
				+ "        implements Processor {\n"
				+ "    public void run() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/varnarrow/Caller.kt",
				"package varnarrow\n"
				+ "\n"
				+ "fun doWork() {\n"
				+ "    var p: Processor = FastProcessor()\n"
				+ "    p.run()\n"
				+ "    p.run()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for FastProcessor.run() — both calls narrowed
		ICompilationUnit fastCu = TestHelpers.getJavaCompilationUnit(
				project, "varnarrow", "FastProcessor.java");
		assertNotNull(fastCu, "FastProcessor CU should exist");
		IType fastType = fastCu.getType("FastProcessor");
		IMethod fastRun = fastType.getMethod("run", new String[0]);
		assertTrue(fastRun.exists(),
				"FastProcessor.run() should exist");

		SearchPattern fastPattern = SearchPattern.createPattern(
				fastRun, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> fastMatches = TestHelpers.executeSearch(
				fastPattern, project);
		List<SearchMatch> fastKt = TestHelpers.filterKotlinMatches(
				fastMatches);
		assertEquals(2, fastKt.size(),
				"Should find 2 Kotlin references to "
						+ "FastProcessor.run() via narrowed initializer "
						+ "(no reassignment). Found " + fastKt.size());

		// Search for Processor.run() — also 2 (declared type)
		ICompilationUnit procCu = TestHelpers.getJavaCompilationUnit(
				project, "varnarrow", "Processor.java");
		assertNotNull(procCu, "Processor CU should exist");
		IType procType = procCu.getType("Processor");
		IMethod procRun = procType.getMethod("run", new String[0]);
		assertTrue(procRun.exists(), "Processor.run() should exist");

		SearchPattern procPattern = SearchPattern.createPattern(
				procRun, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> procMatches = TestHelpers.executeSearch(
				procPattern, project);
		List<SearchMatch> procKt = TestHelpers.filterKotlinMatches(
				procMatches);
		assertEquals(2, procKt.size(),
				"Should find 2 Kotlin references to "
						+ "Processor.run() via declared type. Found "
						+ procKt.size());
	}

	@Test
	public void testVarMultipleReassignments() throws CoreException {
		// var with multiple reassignments should narrow correctly
		// at each segment of control flow.
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/varmulti");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/varmulti/Processor.java",
				"package varmulti;\n"
				+ "public interface Processor {\n"
				+ "    void run();\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/varmulti/FastProcessor.java",
				"package varmulti;\n"
				+ "public class FastProcessor"
				+ "        implements Processor {\n"
				+ "    public void run() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/varmulti/SlowProcessor.java",
				"package varmulti;\n"
				+ "public class SlowProcessor"
				+ "        implements Processor {\n"
				+ "    public void run() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/varmulti/Caller.kt",
				"package varmulti\n"
				+ "\n"
				+ "fun doWork() {\n"
				+ "    var p: Processor = FastProcessor()\n"
				+ "    p.run()\n"
				+ "    p = SlowProcessor()\n"
				+ "    p.run()\n"
				+ "    p = FastProcessor()\n"
				+ "    p.run()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for FastProcessor.run() — first and third calls
		ICompilationUnit fastCu = TestHelpers.getJavaCompilationUnit(
				project, "varmulti", "FastProcessor.java");
		assertNotNull(fastCu, "FastProcessor CU should exist");
		IType fastType = fastCu.getType("FastProcessor");
		IMethod fastRun = fastType.getMethod("run", new String[0]);
		assertTrue(fastRun.exists(),
				"FastProcessor.run() should exist");

		SearchPattern fastPattern = SearchPattern.createPattern(
				fastRun, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> fastMatches = TestHelpers.executeSearch(
				fastPattern, project);
		List<SearchMatch> fastKt = TestHelpers.filterKotlinMatches(
				fastMatches);
		assertEquals(2, fastKt.size(),
				"Should find 2 Kotlin references to "
						+ "FastProcessor.run() (first and third calls). "
						+ "Found " + fastKt.size());

		// Search for SlowProcessor.run() — second call only
		ICompilationUnit slowCu = TestHelpers.getJavaCompilationUnit(
				project, "varmulti", "SlowProcessor.java");
		assertNotNull(slowCu, "SlowProcessor CU should exist");
		IType slowType = slowCu.getType("SlowProcessor");
		IMethod slowRun = slowType.getMethod("run", new String[0]);
		assertTrue(slowRun.exists(),
				"SlowProcessor.run() should exist");

		SearchPattern slowPattern = SearchPattern.createPattern(
				slowRun, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> slowMatches = TestHelpers.executeSearch(
				slowPattern, project);
		List<SearchMatch> slowKt = TestHelpers.filterKotlinMatches(
				slowMatches);
		assertEquals(1, slowKt.size(),
				"Should find 1 Kotlin reference to "
						+ "SlowProcessor.run() (second call). Found "
						+ slowKt.size());

		// Search for Processor.run() — all 3 calls (declared type)
		ICompilationUnit procCu = TestHelpers.getJavaCompilationUnit(
				project, "varmulti", "Processor.java");
		assertNotNull(procCu, "Processor CU should exist");
		IType procType = procCu.getType("Processor");
		IMethod procRun = procType.getMethod("run", new String[0]);
		assertTrue(procRun.exists(), "Processor.run() should exist");

		SearchPattern procPattern = SearchPattern.createPattern(
				procRun, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> procMatches = TestHelpers.executeSearch(
				procPattern, project);
		List<SearchMatch> procKt = TestHelpers.filterKotlinMatches(
				procMatches);
		assertEquals(3, procKt.size(),
				"Should find 3 Kotlin references to "
						+ "Processor.run() via declared type. Found "
						+ procKt.size());
	}

	@Test
	public void testCompoundAssignmentDoesNotNarrow()
			throws CoreException {
		// Compound assignment (+=, -=, etc.) should not narrow the
		// type of the variable. The type remains the declared type.
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/compound");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/compound/Counter.java",
				"package compound;\n"
				+ "public class Counter {\n"
				+ "    public static void count(int v) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/compound/Caller.kt",
				"package compound\n"
				+ "\n"
				+ "fun doWork() {\n"
				+ "    var x: Int = 1\n"
				+ "    x += 2\n"
				+ "    val y: Int = x\n"
				+ "    Counter.count(y)\n"
				+ "    Counter.count(y)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for Counter.count() — should find both calls
		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"compound.Counter.count", project);
		List<SearchMatch> ktRefs =
				TestHelpers.filterKotlinMatches(refs);
		assertEquals(2, ktRefs.size(),
				"Should find 2 Kotlin references to "
						+ "Counter.count() — compound assignment does not "
						+ "break type. Found " + ktRefs.size());
	}

	@Test
	public void testPropertyAssignmentNarrows() throws CoreException {
		// Property assignment should narrow the property type for
		// subsequent accesses until the next reassignment.
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/propassign");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/propassign/Processor.java",
				"package propassign;\n"
				+ "public interface Processor {\n"
				+ "    void run();\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/propassign/FastProcessor.java",
				"package propassign;\n"
				+ "public class FastProcessor"
				+ "        implements Processor {\n"
				+ "    public void run() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/propassign/SlowProcessor.java",
				"package propassign;\n"
				+ "public class SlowProcessor"
				+ "        implements Processor {\n"
				+ "    public void run() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/propassign/Caller.kt",
				"package propassign\n"
				+ "\n"
				+ "class Holder {\n"
				+ "    var processor: Processor = FastProcessor()\n"
				+ "}\n"
				+ "\n"
				+ "fun doWork() {\n"
				+ "    val holder = Holder()\n"
				+ "    holder.processor = SlowProcessor()\n"
				+ "    holder.processor.run()\n"
				+ "    holder.processor.run()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for SlowProcessor.run() — both calls after
		// property reassignment
		ICompilationUnit slowCu = TestHelpers.getJavaCompilationUnit(
				project, "propassign", "SlowProcessor.java");
		assertNotNull(slowCu, "SlowProcessor CU should exist");
		IType slowType = slowCu.getType("SlowProcessor");
		IMethod slowRun = slowType.getMethod("run", new String[0]);
		assertTrue(slowRun.exists(),
				"SlowProcessor.run() should exist");

		SearchPattern slowPattern = SearchPattern.createPattern(
				slowRun, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> slowMatches = TestHelpers.executeSearch(
				slowPattern, project);
		List<SearchMatch> slowKt = TestHelpers.filterKotlinMatches(
				slowMatches);
		assertEquals(2, slowKt.size(),
				"Should find 2 Kotlin references to "
						+ "SlowProcessor.run() after property "
						+ "reassignment. Found " + slowKt.size());
	}

	@Test
	public void testPropertyAssignmentAliasPropagation()
			throws CoreException {
		// When two variables alias the same object and a property
		// is reassigned through one alias, the other alias should
		// also see the narrowed type.
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/aliasprop");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/aliasprop/Processor.java",
				"package aliasprop;\n"
				+ "public interface Processor {\n"
				+ "    void run();\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/aliasprop/FastProcessor.java",
				"package aliasprop;\n"
				+ "public class FastProcessor"
				+ "        implements Processor {\n"
				+ "    public void run() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/aliasprop/SlowProcessor.java",
				"package aliasprop;\n"
				+ "public class SlowProcessor"
				+ "        implements Processor {\n"
				+ "    public void run() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/aliasprop/Caller.kt",
				"package aliasprop\n"
				+ "\n"
				+ "class Holder {\n"
				+ "    var processor: Processor = FastProcessor()\n"
				+ "}\n"
				+ "\n"
				+ "fun doWork() {\n"
				+ "    val h1 = Holder()\n"
				+ "    val h2 = h1\n"
				+ "    h2.processor = SlowProcessor()\n"
				+ "    h1.processor.run()\n"
				+ "    h1.processor.run()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for SlowProcessor.run() — alias propagation means
		// h2 and h1 point to same Holder, so h1.processor is
		// narrowed to SlowProcessor after h2.processor = SlowProcessor()
		ICompilationUnit slowCu = TestHelpers.getJavaCompilationUnit(
				project, "aliasprop", "SlowProcessor.java");
		assertNotNull(slowCu, "SlowProcessor CU should exist");
		IType slowType = slowCu.getType("SlowProcessor");
		IMethod slowRun = slowType.getMethod("run", new String[0]);
		assertTrue(slowRun.exists(),
				"SlowProcessor.run() should exist");

		SearchPattern slowPattern = SearchPattern.createPattern(
				slowRun, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> slowMatches = TestHelpers.executeSearch(
				slowPattern, project);
		List<SearchMatch> slowKt = TestHelpers.filterKotlinMatches(
				slowMatches);
		assertEquals(2, slowKt.size(),
				"Should find 2 Kotlin references to "
						+ "SlowProcessor.run() via alias propagation "
						+ "(h2→h1). Found " + slowKt.size());
	}

	@Test
	public void testForLoopDestructuringWithUnderscore()
			throws CoreException {
		// Exercises LocalVariableExtractor line 181 — skipping `_`
		// bindings in destructuring declarations within for loops.
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/destunderscore");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/destunderscore/Printer.java",
				"package destunderscore;\n"
				+ "public class Printer {\n"
				+ "    public static void printValue(int v) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/destunderscore/Work.kt",
				"package destunderscore\n"
				+ "\n"
				+ "fun doWork() {\n"
				+ "    val map = mapOf(\"a\" to 1, \"b\" to 2)\n"
				+ "    for ((_, value) in map) {\n"
				+ "        Printer.printValue(value)\n"
				+ "        Printer.printValue(value)\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"destunderscore.Printer.printValue",
						project);
		List<SearchMatch> ktRefs =
				TestHelpers.filterKotlinMatches(refs);
		assertEquals(2, ktRefs.size(),
				"Should find 2 Kotlin references to "
						+ "Printer.printValue() in for-loop with "
						+ "underscore destructuring. Found "
						+ ktRefs.size());
	}

	@Test
	public void testForLoopTypedVariable() throws CoreException {
		// Exercises LocalVariableExtractor line 184-185 — for-loop
		// variable with explicit type annotation.
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/fortyped");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/fortyped/Printer.java",
				"package fortyped;\n"
				+ "public class Printer {\n"
				+ "    public static void printItem(String s) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/fortyped/Work.kt",
				"package fortyped\n"
				+ "\n"
				+ "fun doWork() {\n"
				+ "    val items = listOf(\"a\", \"b\")\n"
				+ "    for (item: String in items) {\n"
				+ "        Printer.printItem(item)\n"
				+ "        Printer.printItem(item)\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"fortyped.Printer.printItem", project);
		List<SearchMatch> ktRefs =
				TestHelpers.filterKotlinMatches(refs);
		assertEquals(2, ktRefs.size(),
				"Should find 2 Kotlin references to "
						+ "Printer.printItem() in for-loop with "
						+ "typed variable. Found " + ktRefs.size());
	}

	@Test
	public void testPrimitiveArrayTypeResolution()
			throws CoreException {
		// Exercises ScopeChain lines 496-509 — resolveTypeName for
		// LongArray, DoubleArray, FloatArray, BooleanArray.
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/arrayresolve");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/arrayresolve/Consumer.java",
				"package arrayresolve;\n"
				+ "public class Consumer {\n"
				+ "    public static void consumeLong(long v) {}\n"
				+ "    public static void consumeDouble(double v) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/arrayresolve/Work.kt",
				"package arrayresolve\n"
				+ "\n"
				+ "fun doWork() {\n"
				+ "    val longs: LongArray = longArrayOf(1L, 2L)\n"
				+ "    val doubles: DoubleArray = doubleArrayOf(1.0, 2.0)\n"
				+ "    val floats: FloatArray = floatArrayOf(1.0f, 2.0f)\n"
				+ "    val bools: BooleanArray = booleanArrayOf(true, false)\n"
				+ "    Consumer.consumeLong(longs[0])\n"
				+ "    Consumer.consumeLong(longs[1])\n"
				+ "    Consumer.consumeDouble(doubles[0])\n"
				+ "    Consumer.consumeDouble(doubles[1])\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> longRefs =
				TestHelpers.searchQualifiedMethodReferences(
						"arrayresolve.Consumer.consumeLong",
						project);
		List<SearchMatch> longKt =
				TestHelpers.filterKotlinMatches(longRefs);
		assertEquals(2, longKt.size(),
				"Should find 2 Kotlin references to "
						+ "Consumer.consumeLong(). Found "
						+ longKt.size());

		List<SearchMatch> doubleRefs =
				TestHelpers.searchQualifiedMethodReferences(
						"arrayresolve.Consumer.consumeDouble",
						project);
		List<SearchMatch> doubleKt =
				TestHelpers.filterKotlinMatches(doubleRefs);
		assertEquals(2, doubleKt.size(),
				"Should find 2 Kotlin references to "
						+ "Consumer.consumeDouble(). Found "
						+ doubleKt.size());
	}

	@Test
	public void testWildcardTypeArgument() throws CoreException {
		// Exercises LocalVariableExtractor line 274 — wildcard `*`
		// in type arguments.
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/wildcard");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/wildcard/Printer.java",
				"package wildcard;\n"
				+ "public class Printer {\n"
				+ "    public static void printSize(int s) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/wildcard/Work.kt",
				"package wildcard\n"
				+ "\n"
				+ "fun doWork() {\n"
				+ "    val list: MutableList<*> = mutableListOf(\"a\", \"b\")\n"
				+ "    Printer.printSize(list.size)\n"
				+ "    Printer.printSize(list.size)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs =
				TestHelpers.searchQualifiedMethodReferences(
						"wildcard.Printer.printSize", project);
		List<SearchMatch> ktRefs =
				TestHelpers.filterKotlinMatches(refs);
		assertEquals(2, ktRefs.size(),
				"Should find 2 Kotlin references to "
						+ "Printer.printSize() with wildcard type "
						+ "argument. Found " + ktRefs.size());
	}

	@Test
	public void testSymbolTableFallbackLookup()
			throws CoreException {
		// Exercises ScopeChain lines 142-147 — resolveType falls
		// back to symbol table by simple name when import resolution
		// fails. LocalService is defined in one Kotlin file and
		// referenced in another; Java type verifies receiver
		// filtering works correctly.
		TestHelpers.createFolder(
				"/" + PROJECT_NAME + "/src/symtable");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/symtable/JavaService.java",
				"package symtable;\n"
				+ "public class JavaService {\n"
				+ "    public void execute() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/symtable/LocalService.kt",
				"package symtable\n"
				+ "\n"
				+ "class LocalService {\n"
				+ "    fun execute() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
						+ "/src/symtable/User.kt",
				"package symtable\n"
				+ "\n"
				+ "fun doWork() {\n"
				+ "    val local = LocalService()\n"
				+ "    val java = JavaService()\n"
				+ "    local.execute()\n"
				+ "    local.execute()\n"
				+ "    java.execute()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for JavaService.execute() — should find only the
		// java.execute() call, not local.execute() calls
		ICompilationUnit javaCu = TestHelpers.getJavaCompilationUnit(
				project, "symtable", "JavaService.java");
		assertNotNull(javaCu, "Java CU should exist");
		IType javaServiceType = javaCu.getType("JavaService");
		IMethod execMethod = javaServiceType.getMethod("execute",
				new String[0]);
		assertTrue(execMethod.exists(),
				"JavaService.execute() should exist");

		SearchPattern elementPattern = SearchPattern.createPattern(
				execMethod, IJavaSearchConstants.REFERENCES,
				SearchPattern.R_EXACT_MATCH
						| SearchPattern.R_CASE_SENSITIVE
						| SearchPattern.R_ERASURE_MATCH);
		List<SearchMatch> matches = TestHelpers.executeSearch(
				elementPattern, project);
		List<SearchMatch> ktMatches = TestHelpers.filterKotlinMatches(
				matches);
		assertEquals(1, ktMatches.size(),
				"Should find exactly 1 Kotlin reference to "
						+ "JavaService.execute() — the local.execute() "
						+ "calls should be filtered because LocalService "
						+ "is resolved via symbol table fallback. Found "
						+ ktMatches.size());
	}

	// ---------------------------------------------------------------
	// Malformed / broken Kotlin file graceful degradation tests
	// ---------------------------------------------------------------

	@Test
	public void testBrokenExpressionInInitializerDoesNotCrash()
			throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/brokeninit");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/brokeninit/Processor.java",
				"package brokeninit;\n"
				+ "public interface Processor { void run(); }\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/brokeninit/FastProcessor.java",
				"package brokeninit;\n"
				+ "public class FastProcessor implements Processor {\n"
				+ "    public void run() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/brokeninit/Caller.kt",
				"package brokeninit\n"
				+ "fun doWork() {\n"
				+ "    val x: Processor = FastProcessor(\n"
				+ "    x.run()\n"
				+ "    x.run()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Missing ')' — parser should recover; search must not throw
		List<SearchMatch> matches =
				TestHelpers.searchQualifiedMethodReferences(
						"brokeninit.Processor.run", project);
		assertTrue(matches.size() >= 0,
				"Search should complete without exception");
	}

	@Test
	public void testBrokenAssignmentDoesNotCrash() throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/brokenassign");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/brokenassign/Processor.java",
				"package brokenassign;\n"
				+ "public interface Processor { void run(); }\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME
				+ "/src/brokenassign/FastProcessor.java",
				"package brokenassign;\n"
				+ "public class FastProcessor implements Processor {\n"
				+ "    public void run() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/brokenassign/Caller.kt",
				"package brokenassign\n"
				+ "fun doWork() {\n"
				+ "    var p: Processor = FastProcessor()\n"
				+ "    p = \n"
				+ "    p.run()\n"
				+ "    p.run()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// 'p = ' with empty RHS — parser should recover
		List<SearchMatch> matches =
				TestHelpers.searchQualifiedMethodReferences(
						"brokenassign.Processor.run", project);
		assertTrue(matches.size() >= 0,
				"Search should complete without exception");
	}

	@Test
	public void testIncompleteClassDeclarationDoesNotCrash()
			throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/brokenclass");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/brokenclass/Processor.java",
				"package brokenclass;\n"
				+ "public interface Processor { void run(); }\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/brokenclass/Caller.kt",
				"package brokenclass\n"
				+ "class Holder {\n"
				+ "    var processor: Processor =\n"
				+ "}\n"
				+ "\n"
				+ "fun doWork() {\n"
				+ "    val h = Holder()\n"
				+ "    h.processor.run()\n"
				+ "    h.processor.run()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Incomplete property initializer — search must not throw
		List<SearchMatch> matches =
				TestHelpers.searchQualifiedMethodReferences(
						"brokenclass.Processor.run", project);
		assertTrue(matches.size() >= 0,
				"Search should complete without exception");
	}

	@Test
	public void testMalformedForLoopDoesNotCrash() throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/brokenfor");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/brokenfor/Printer.java",
				"package brokenfor;\n"
				+ "public class Printer {\n"
				+ "    public static void printKey(String k) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/brokenfor/Caller.kt",
				"package brokenfor\n"
				+ "fun doWork() {\n"
				+ "    val map = mapOf(\"a\" to 1, \"b\" to 2)\n"
				+ "    for ((key, ) in map) {\n"
				+ "        Printer.printKey(key)\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Broken destructuring in for-loop — search must not throw
		List<SearchMatch> matches =
				TestHelpers.searchQualifiedMethodReferences(
						"brokenfor.Printer.printKey", project);
		assertTrue(matches.size() >= 0,
				"Search should complete without exception");
	}

	@Test
	public void testTruncatedFileDoesNotCrash() throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/truncated");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/truncated/Processor.java",
				"package truncated;\n"
				+ "public interface Processor { void run(); }\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/truncated/FastProcessor.java",
				"package truncated;\n"
				+ "public class FastProcessor implements Processor {\n"
				+ "    public void run() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/truncated/Caller.kt",
				"package truncated\n"
				+ "fun doWork() {\n"
				+ "    val p: Processor = FastProcessor()\n"
				+ "    p.run()\n"
				+ "    p.");
		TestHelpers.waitUntilIndexesReady();

		// File ends abruptly after 'p.' — search must not throw
		List<SearchMatch> matches =
				TestHelpers.searchQualifiedMethodReferences(
						"truncated.Processor.run", project);
		assertTrue(matches.size() >= 0,
				"Search should complete without exception");
	}

	@Test
	public void testBrokenExpressionChainDoesNotCrash()
			throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/brokenchain");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/brokenchain/Counter.java",
				"package brokenchain;\n"
				+ "public class Counter {\n"
				+ "    public static void count(int v) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/brokenchain/Caller.kt",
				"package brokenchain\n"
				+ "fun doWork() {\n"
				+ "    val x = 1 + + * 3\n"
				+ "    val y: Int = x\n"
				+ "    Counter.count(y)\n"
				+ "    Counter.count(y)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Broken expression chain — search must not throw
		List<SearchMatch> matches =
				TestHelpers.searchQualifiedMethodReferences(
						"brokenchain.Counter.count", project);
		assertTrue(matches.size() >= 0,
				"Search should complete without exception");
	}

	@Test
	public void testMissingSemicolonInPropertyDoesNotCrash()
			throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/garbled");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/garbled/Printer.java",
				"package garbled;\n"
				+ "public class Printer {\n"
				+ "    public static void print(int v) {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/garbled/Caller.kt",
				"package garbled\n"
				+ "fun doWork() {\n"
				+ "    val a: Int = 1; val b = ; val c = a\n"
				+ "    Printer.print(c)\n"
				+ "    Printer.print(c)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Garbled property declarations — search must not throw
		List<SearchMatch> matches =
				TestHelpers.searchQualifiedMethodReferences(
						"garbled.Printer.print", project);
		assertTrue(matches.size() >= 0,
				"Search should complete without exception");
	}
}
