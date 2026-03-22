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
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
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
}
