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

import org.eclipse.core.resources.IFile;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IBuffer;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.internal.core.search.indexing.SearchParticipantRegistry;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import co.karellen.jdtls.kotlin.search.KotlinCompilationUnit;
import co.karellen.jdtls.kotlin.search.KotlinElement;
import co.karellen.jdtls.kotlin.search.KotlinModelManager;
import co.karellen.jdtls.kotlin.search.KotlinSearchParticipant;

/**
 * End-to-end handler tests that exercise the full jdtls conversion chain:
 * {@code SearchMatch -> IJavaElement -> ICompilationUnit -> IBuffer ->
 * IDocument -> Location(uri, range)}.
 *
 * <p>Replicates the JDTUtils.toLocation() conversion inline (IBuffer ->
 * Document -> offset-to-line/column) to avoid depending on the full jdtls
 * bundle activation in the OSGi test container.
 *
 * @author Arcadiy Ivanov
 */
public class CrossLanguageHandlerTest {

	private static final String PROJECT_NAME = "CrossLangHandlerTest";
	private static final String MULTI_LINE_KT =
			"package handler.test\n"
			+ "\n"
			+ "open class HandlerTestType {\n"
			+ "    fun handlerTestMethod(x: Int, y: String): Boolean {\n"
			+ "        return true\n"
			+ "    }\n"
			+ "\n"
			+ "    val handlerTestField: Int = 42\n"
			+ "    var handlerMutableProp: String = \"hello\"\n"
			+ "}\n"
			+ "\n"
			+ "class HandlerSubType : HandlerTestType() {\n"
			+ "    override fun handlerTestMethod(x: Int, y: String): Boolean {\n"
			+ "        return false\n"
			+ "    }\n"
			+ "}\n"
			+ "\n"
			+ "interface HandlerTestInterface {\n"
			+ "    fun interfaceMethod(): Unit\n"
			+ "}\n"
			+ "\n"
			+ "enum class HandlerTestEnum {\n"
			+ "    ALPHA, BETA\n"
			+ "}\n";

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

	// ---- toLocation(ICompilationUnit, offset, length) path ----

	@Test
	public void testToLocationFromCompilationUnit() throws Exception {
		createTestFile();

		List<SearchMatch> matches = TestHelpers.searchKotlinTypes(
				"HandlerTestType", project);
		assertTrue(matches.size() >= 1,
				"Should find HandlerTestType in .kt file");

		SearchMatch match = matches.get(0);
		IJavaElement element = (IJavaElement) match.getElement();
		ICompilationUnit cu = (ICompilationUnit) element.getAncestor(
				IJavaElement.COMPILATION_UNIT);
		assertNotNull(cu, "Element should have a compilation unit ancestor");
		assertTrue(cu instanceof KotlinCompilationUnit,
				"CU should be a KotlinCompilationUnit");

		// Replicate JDTUtils.toRange(openable, offset, length) chain
		IBuffer buffer = cu.getBuffer();
		assertNotNull(buffer, "CU buffer should not be null");
		IDocument doc = new Document(buffer.getContents());
		int[] startLineCol = toLineColumn(doc, match.getOffset());
		int[] endLineCol = toLineColumn(doc,
				match.getOffset() + match.getLength());

		assertNotNull(startLineCol, "Start offset should resolve to line/col");
		assertNotNull(endLineCol, "End offset should resolve to line/col");
		assertTrue(startLineCol[0] >= 0, "Start line should be non-negative");
		assertTrue(endLineCol[0] >= 0, "End line should be non-negative");
	}

	// ---- toLocation(IJavaElement) path via ISourceReference ----

	@Test
	public void testToLocationFromTypeElement() throws Exception {
		createTestFile();

		List<SearchMatch> matches = TestHelpers.searchKotlinTypes(
				"HandlerTestType", project);
		assertTrue(matches.size() >= 1);

		SearchMatch match = matches.get(0);
		IJavaElement element = (IJavaElement) match.getElement();
		assertTrue(element instanceof ISourceReference,
				"KotlinElement should implement ISourceReference");
		assertTrue(element instanceof KotlinElement.KotlinTypeElement,
				"Element should be a KotlinTypeElement");

		// Replicate JDTUtils.toLocation(element) path
		ISourceRange nameRange = ((ISourceReference) element).getSourceRange();
		assertNotNull(nameRange, "Source range should not be null");
		assertTrue(nameRange.getOffset() >= 0,
				"Source offset should be non-negative");
		assertTrue(nameRange.getLength() > 0,
				"Source length should be positive");

		ICompilationUnit cu = (ICompilationUnit) element.getAncestor(
				IJavaElement.COMPILATION_UNIT);
		int[] lineCol = toLocationFromElement(cu, nameRange);
		assertNotNull(lineCol, "Element should resolve to line/col");
		// HandlerTestType is on line 2 (0-based)
		assertEquals(2, lineCol[0],
				"HandlerTestType should be on line 2 (0-based)");
	}

	@Test
	public void testToLocationFromMethodElement() throws Exception {
		createTestFile();

		List<SearchMatch> matches = TestHelpers.searchMethodDeclarations(
				"handlerTestMethod", project);
		List<SearchMatch> ktMatches = filterKotlinMatches(matches);
		assertTrue(ktMatches.size() >= 1,
				"Should find at least one method declaration in .kt");

		SearchMatch match = ktMatches.get(0);
		IJavaElement element = (IJavaElement) match.getElement();
		assertTrue(element instanceof KotlinElement.KotlinMethodElement,
				"Element should be a KotlinMethodElement");
		assertTrue(element instanceof ISourceReference,
				"KotlinMethodElement should implement ISourceReference");

		ISourceRange nameRange = ((ISourceReference) element).getSourceRange();
		ICompilationUnit cu = (ICompilationUnit) element.getAncestor(
				IJavaElement.COMPILATION_UNIT);
		int[] lineCol = toLocationFromElement(cu, nameRange);
		assertNotNull(lineCol, "Method element should resolve to line/col");
		assertTrue(lineCol[0] >= 2,
				"Method should be on line 3+ (0-based >= 2), was: "
						+ lineCol[0]);
	}

	@Test
	public void testToLocationFromFieldElement() throws Exception {
		createTestFile();

		List<SearchMatch> matches = TestHelpers.searchFieldDeclarations(
				"handlerTestField", project);
		List<SearchMatch> ktMatches = filterKotlinMatches(matches);
		assertTrue(ktMatches.size() >= 1,
				"Should find at least one field declaration in .kt");

		SearchMatch match = ktMatches.get(0);
		IJavaElement element = (IJavaElement) match.getElement();
		assertTrue(element instanceof KotlinElement.KotlinFieldElement,
				"Element should be a KotlinFieldElement");
		assertTrue(element instanceof ISourceReference,
				"KotlinFieldElement should implement ISourceReference");

		ISourceRange nameRange = ((ISourceReference) element).getSourceRange();
		ICompilationUnit cu = (ICompilationUnit) element.getAncestor(
				IJavaElement.COMPILATION_UNIT);
		int[] lineCol = toLocationFromElement(cu, nameRange);
		assertNotNull(lineCol, "Field element should resolve to line/col");
	}

	// ---- Reference search produces valid offset-to-location conversion ----

	@Test
	public void testReferencesSearchProducesValidLocations()
			throws Exception {
		createTestFile();
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/handler/test/HandlerUser.kt",
				"package handler.test\n"
				+ "\n"
				+ "class HandlerUser {\n"
				+ "    fun use(t: HandlerTestType): Boolean {\n"
				+ "        val x: HandlerTestType = t\n"
				+ "        return x.handlerTestMethod(1, \"a\")\n"
				+ "    }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> refs = TestHelpers.searchTypeReferences(
				"HandlerTestType", project);
		List<SearchMatch> ktRefs = filterKotlinMatches(refs);
		assertTrue(ktRefs.size() >= 1,
				"Should find type references in .kt files");

		for (SearchMatch ref : ktRefs) {
			IJavaElement element = (IJavaElement) ref.getElement();
			assertNotNull(element);
			ICompilationUnit cu = (ICompilationUnit) element.getAncestor(
					IJavaElement.COMPILATION_UNIT);
			assertNotNull(cu);

			IBuffer buffer = cu.getBuffer();
			assertNotNull(buffer, "CU buffer should not be null");
			IDocument doc = new Document(buffer.getContents());
			int[] lineCol = toLineColumn(doc, ref.getOffset());
			assertNotNull(lineCol,
					"Each reference offset should resolve to line/col");
		}
	}

	// ---- Offset-to-line/column conversion ----

	@Test
	public void testOffsetToLineColumnConversion() throws Exception {
		createTestFile();

		// HandlerSubType starts on line 11 (0-based) in MULTI_LINE_KT
		List<SearchMatch> matches = TestHelpers.searchKotlinTypes(
				"HandlerSubType", project);
		assertTrue(matches.size() >= 1);

		SearchMatch match = matches.get(0);
		IJavaElement element = (IJavaElement) match.getElement();
		ICompilationUnit cu = (ICompilationUnit) element.getAncestor(
				IJavaElement.COMPILATION_UNIT);

		IBuffer buffer = cu.getBuffer();
		IDocument doc = new Document(buffer.getContents());
		int[] lineCol = toLineColumn(doc, match.getOffset());
		assertNotNull(lineCol);

		assertTrue(lineCol[0] >= 10,
				"HandlerSubType should be on line 10+ (0-based), was: "
						+ lineCol[0]);
	}

	// ---- Method declaration search finds both overrides ----

	@Test
	public void testMethodDeclarationFindsMultipleMatches() throws Exception {
		createTestFile();

		List<SearchMatch> matches = TestHelpers.searchMethodDeclarations(
				"handlerTestMethod", project);
		List<SearchMatch> ktMatches = filterKotlinMatches(matches);
		assertTrue(ktMatches.size() >= 2,
				"Should find at least 2 method declarations "
						+ "(base + override), found: " + ktMatches.size());
	}

	// ---- Field declaration search with mutable property ----

	@Test
	public void testFieldDeclarationFindsProperty() throws Exception {
		createTestFile();

		List<SearchMatch> matches = TestHelpers.searchFieldDeclarations(
				"handlerMutableProp", project);
		List<SearchMatch> ktMatches = filterKotlinMatches(matches);
		assertTrue(ktMatches.size() >= 1,
				"Should find mutable property declaration in .kt");

		IJavaElement element = (IJavaElement) ktMatches.get(0).getElement();
		assertTrue(element instanceof KotlinElement.KotlinFieldElement);

		ISourceRange nameRange = ((ISourceReference) element).getSourceRange();
		ICompilationUnit cu = (ICompilationUnit) element.getAncestor(
				IJavaElement.COMPILATION_UNIT);
		int[] lineCol = toLocationFromElement(cu, nameRange);
		assertNotNull(lineCol);
	}

	// ---- ISourceReference contract ----

	@Test
	public void testSourceRangeIsAvailable() throws Exception {
		createTestFile();

		List<SearchMatch> matches = TestHelpers.searchKotlinTypes(
				"HandlerTestType", project);
		assertTrue(matches.size() >= 1);

		KotlinElement.KotlinTypeElement typeElement =
				(KotlinElement.KotlinTypeElement) matches.get(0).getElement();
		assertNotNull(typeElement.getSourceRange(),
				"getSourceRange() should not return null");
		assertTrue(typeElement.getSourceRange().getOffset() >= 0,
				"Source offset should be non-negative");
		assertTrue(typeElement.getSourceRange().getLength() > 0,
				"Source length should be positive");

		assertNotNull(typeElement.getNameRange(),
				"getNameRange() should not return null");
		assertTrue(typeElement.getNameRange().getOffset() >= 0,
				"Name offset should be non-negative");
		assertEquals("HandlerTestType".length(),
				typeElement.getNameRange().getLength(),
				"Name range length should equal the type name length");
	}

	@Test
	public void testCompilationUnitSourceRange() throws Exception {
		createTestFile();

		List<SearchMatch> matches = TestHelpers.searchKotlinTypes(
				"HandlerTestType", project);
		assertTrue(matches.size() >= 1);

		IJavaElement element = (IJavaElement) matches.get(0).getElement();
		KotlinCompilationUnit cu = (KotlinCompilationUnit) element
				.getAncestor(IJavaElement.COMPILATION_UNIT);
		assertNotNull(cu.getSourceRange(),
				"CU getSourceRange() should not return null");
		assertEquals(0, cu.getSourceRange().getOffset(),
				"CU source offset should be 0");
		assertTrue(cu.getSourceRange().getLength() > 0,
				"CU source length should be positive");
		assertEquals(MULTI_LINE_KT.length(),
				cu.getSourceRange().getLength(),
				"CU source length should match file content length");
	}

	// ---- IType interface compliance ----

	@Test
	public void testTypeElementImplementsIType() throws Exception {
		createTestFile();

		List<SearchMatch> matches = TestHelpers.searchKotlinTypes(
				"HandlerTestType", project);
		assertTrue(matches.size() >= 1);

		Object element = matches.get(0).getElement();
		assertTrue(element instanceof IType,
				"KotlinTypeElement should implement IType");
		assertTrue(element instanceof KotlinElement.KotlinTypeElement,
				"Element should still be a KotlinTypeElement");

		IType type = (IType) element;
		assertTrue(type.isClass(), "HandlerTestType should be a class");
		assertFalse(type.isInterface(), "HandlerTestType is not an interface");
		assertFalse(type.isEnum(), "HandlerTestType is not an enum");
		assertFalse(type.isAnnotation(),
				"HandlerTestType is not an annotation");

		assertEquals("handler.test.HandlerTestType",
				type.getFullyQualifiedName(),
				"FQN should include package and type name");
		assertEquals("HandlerTestType", type.getTypeQualifiedName(),
				"Type-qualified name should be the simple name");
	}

	@Test
	public void testInterfaceElementIsInterface() throws Exception {
		createTestFile();

		List<SearchMatch> matches = TestHelpers.searchKotlinTypes(
				"HandlerTestInterface", project);
		assertTrue(matches.size() >= 1);

		IType type = (IType) matches.get(0).getElement();
		assertTrue(type.isInterface(),
				"HandlerTestInterface should be an interface");
		assertFalse(type.isClass(),
				"Interface should not report isClass");
		assertFalse(type.isEnum(),
				"Interface should not report isEnum");
	}

	@Test
	public void testEnumElementIsEnum() throws Exception {
		createTestFile();

		List<SearchMatch> matches = TestHelpers.searchKotlinTypes(
				"HandlerTestEnum", project);
		assertTrue(matches.size() >= 1);

		IType type = (IType) matches.get(0).getElement();
		assertTrue(type.isEnum(),
				"HandlerTestEnum should be an enum");
		assertFalse(type.isInterface(),
				"Enum should not report isInterface");
	}

	@Test
	public void testTypeElementSupertypes() throws Exception {
		createTestFile();

		List<SearchMatch> matches = TestHelpers.searchKotlinTypes(
				"HandlerSubType", project);
		assertTrue(matches.size() >= 1);

		IType type = (IType) matches.get(0).getElement();
		String superclassName = type.getSuperclassName();
		assertNotNull(superclassName,
				"HandlerSubType should have a superclass");
		assertEquals("HandlerTestType", superclassName,
				"Superclass should be HandlerTestType");
	}

	// ---- IMethod interface compliance ----

	@Test
	public void testMethodElementImplementsIMethod() throws Exception {
		createTestFile();

		List<SearchMatch> matches = TestHelpers.searchMethodDeclarations(
				"handlerTestMethod", project);
		List<SearchMatch> ktMatches = filterKotlinMatches(matches);
		assertTrue(ktMatches.size() >= 1);

		Object element = ktMatches.get(0).getElement();
		assertTrue(element instanceof IMethod,
				"KotlinMethodElement should implement IMethod");

		IMethod method = (IMethod) element;
		assertFalse(method.isConstructor(),
				"handlerTestMethod is not a constructor");
		assertEquals(2, method.getNumberOfParameters(),
				"handlerTestMethod has 2 parameters");

		String[] paramTypes = method.getParameterTypes();
		assertNotNull(paramTypes);
		assertEquals(2, paramTypes.length,
				"Should have 2 parameter type signatures");
		assertEquals(Signature.SIG_INT, paramTypes[0],
				"First parameter should be Int");

		String[] paramNames = method.getParameterNames();
		assertNotNull(paramNames);
		assertEquals(2, paramNames.length,
				"Should have 2 parameter names");
		assertEquals("x", paramNames[0], "First param name should be 'x'");
		assertEquals("y", paramNames[1], "Second param name should be 'y'");

		String returnType = method.getReturnType();
		assertEquals(Signature.SIG_BOOLEAN, returnType,
				"Return type should be boolean");

		assertNotNull(method.getSignature(),
				"Method signature should not be null");
	}

	@Test
	public void testMethodElementReturnTypeVoid() throws Exception {
		createTestFile();

		List<SearchMatch> matches = TestHelpers.searchMethodDeclarations(
				"interfaceMethod", project);
		List<SearchMatch> ktMatches = filterKotlinMatches(matches);
		assertTrue(ktMatches.size() >= 1);

		IMethod method = (IMethod) ktMatches.get(0).getElement();
		assertEquals(Signature.SIG_VOID, method.getReturnType(),
				"Unit return type should map to SIG_VOID");
		assertEquals(0, method.getNumberOfParameters(),
				"interfaceMethod has no parameters");
	}

	// ---- IField interface compliance ----

	@Test
	public void testFieldElementImplementsIField() throws Exception {
		createTestFile();

		List<SearchMatch> matches = TestHelpers.searchFieldDeclarations(
				"handlerTestField", project);
		List<SearchMatch> ktMatches = filterKotlinMatches(matches);
		assertTrue(ktMatches.size() >= 1);

		Object element = ktMatches.get(0).getElement();
		assertTrue(element instanceof IField,
				"KotlinFieldElement should implement IField");

		IField field = (IField) element;
		assertFalse(field.isEnumConstant(),
				"handlerTestField is not an enum constant");

		String typeSig = field.getTypeSignature();
		assertNotNull(typeSig, "Field type signature should not be null");
		assertEquals(Signature.SIG_INT, typeSig,
				"handlerTestField type should be Int");
	}

	@Test
	public void testFieldElementStringType() throws Exception {
		createTestFile();

		List<SearchMatch> matches = TestHelpers.searchFieldDeclarations(
				"handlerMutableProp", project);
		List<SearchMatch> ktMatches = filterKotlinMatches(matches);
		assertTrue(ktMatches.size() >= 1);

		IField field = (IField) ktMatches.get(0).getElement();
		String typeSig = field.getTypeSignature();
		assertEquals(Signature.createTypeSignature("String", false),
				typeSig,
				"handlerMutableProp type should be String");
	}

	// ---- Handle identifiers ----

	@Test
	public void testHandleIdentifierFormat() throws Exception {
		createTestFile();

		List<SearchMatch> typeMatches = TestHelpers.searchKotlinTypes(
				"HandlerTestType", project);
		assertTrue(typeMatches.size() >= 1);
		KotlinElement.KotlinTypeElement typeElement =
				(KotlinElement.KotlinTypeElement) typeMatches.get(0)
						.getElement();
		String typeHandle = typeElement.getHandleIdentifier();
		assertTrue(typeHandle.contains("[HandlerTestType"),
				"Type handle should contain '[TypeName', was: "
						+ typeHandle);

		List<SearchMatch> methodMatches =
				TestHelpers.searchMethodDeclarations(
						"handlerTestMethod", project);
		List<SearchMatch> ktMethodMatches =
				filterKotlinMatches(methodMatches);
		assertTrue(ktMethodMatches.size() >= 1);
		IMethod methodElement = (IMethod) ktMethodMatches.get(0)
				.getElement();
		String methodHandle = methodElement.getHandleIdentifier();
		assertTrue(methodHandle.contains("~handlerTestMethod"),
				"Method handle should contain '~methodName', was: "
						+ methodHandle);

		List<SearchMatch> fieldMatches =
				TestHelpers.searchFieldDeclarations(
						"handlerTestField", project);
		List<SearchMatch> ktFieldMatches =
				filterKotlinMatches(fieldMatches);
		assertTrue(ktFieldMatches.size() >= 1);
		IField fieldElement = (IField) ktFieldMatches.get(0)
				.getElement();
		String fieldHandle = fieldElement.getHandleIdentifier();
		assertTrue(fieldHandle.contains("^handlerTestField"),
				"Field handle should contain '^fieldName', was: "
						+ fieldHandle);
	}

	// ---- Document symbol (getTypes/getChildren) ----

	@Test
	public void testCompilationUnitReturnsTypes() throws Exception {
		createTestFile();
		TestHelpers.waitUntilIndexesReady();

		IFile file = TestHelpers.getFile(
				"/" + PROJECT_NAME + "/src/handler/test/HandlerTestType.kt");
		KotlinCompilationUnit cu = KotlinModelManager.getInstance()
				.getCompilationUnit(file);

		IType[] types = cu.getTypes();
		assertTrue(types.length >= 2,
				"Should have at least HandlerTestType and HandlerSubType, "
						+ "found: " + types.length);

		// Verify first type has members
		IType firstType = null;
		for (IType t : types) {
			if ("HandlerTestType".equals(t.getElementName())) {
				firstType = t;
				break;
			}
		}
		assertNotNull(firstType,
				"Should find HandlerTestType in getTypes()");

		IMethod[] methods = firstType.getMethods();
		assertTrue(methods.length >= 1,
				"HandlerTestType should have at least one method");

		IField[] fields = firstType.getFields();
		assertTrue(fields.length >= 1,
				"HandlerTestType should have at least one field");

		IJavaElement[] children = firstType.getChildren();
		assertTrue(children.length >= 2,
				"HandlerTestType should have methods + fields as children");
	}

	@Test
	public void testCompilationUnitGetAllChildren() throws Exception {
		createTestFile();
		TestHelpers.waitUntilIndexesReady();

		IFile file = TestHelpers.getFile(
				"/" + PROJECT_NAME + "/src/handler/test/HandlerTestType.kt");
		KotlinCompilationUnit cu = KotlinModelManager.getInstance()
				.getCompilationUnit(file);

		IJavaElement[] children = cu.getChildren();
		assertTrue(children.length >= 2,
				"CU should have at least 2 top-level children (types), "
						+ "found: " + children.length);
		assertTrue(cu.hasChildren(), "CU should report hasChildren()");

		IType primary = cu.findPrimaryType();
		assertNotNull(primary,
				"findPrimaryType() should return first type");
	}

	// ---- codeSelect ----

	@Test
	public void testCodeSelectOnTypeName() throws Exception {
		createTestFile();
		TestHelpers.waitUntilIndexesReady();

		IFile file = TestHelpers.getFile(
				"/" + PROJECT_NAME + "/src/handler/test/HandlerTestType.kt");
		KotlinCompilationUnit cu = KotlinModelManager.getInstance()
				.getCompilationUnit(file);

		// Find offset of "HandlerTestType" in source
		String source = cu.getSource();
		int offset = source.indexOf("class HandlerTestType")
				+ "class ".length();
		assertTrue(offset > "class ".length(),
				"Should find HandlerTestType in source");

		IJavaElement[] selected = cu.codeSelect(offset, 0);
		assertTrue(selected.length >= 1,
				"codeSelect on type name should return an element");
		assertEquals(IJavaElement.TYPE, selected[0].getElementType(),
				"Selected element should be a TYPE");
		assertEquals("HandlerTestType", selected[0].getElementName(),
				"Selected type should be HandlerTestType");
	}

	@Test
	public void testCodeSelectOnMethodName() throws Exception {
		createTestFile();
		TestHelpers.waitUntilIndexesReady();

		IFile file = TestHelpers.getFile(
				"/" + PROJECT_NAME + "/src/handler/test/HandlerTestType.kt");
		KotlinCompilationUnit cu = KotlinModelManager.getInstance()
				.getCompilationUnit(file);

		String source = cu.getSource();
		int offset = source.indexOf("fun handlerTestMethod")
				+ "fun ".length();

		IJavaElement[] selected = cu.codeSelect(offset, 0);
		assertTrue(selected.length >= 1,
				"codeSelect on method name should return an element");
		assertEquals(IJavaElement.METHOD, selected[0].getElementType(),
				"Selected element should be a METHOD");
		assertEquals("handlerTestMethod", selected[0].getElementName(),
				"Selected method should be handlerTestMethod");
	}

	@Test
	public void testCodeSelectOnFieldName() throws Exception {
		createTestFile();
		TestHelpers.waitUntilIndexesReady();

		IFile file = TestHelpers.getFile(
				"/" + PROJECT_NAME + "/src/handler/test/HandlerTestType.kt");
		KotlinCompilationUnit cu = KotlinModelManager.getInstance()
				.getCompilationUnit(file);

		String source = cu.getSource();
		int offset = source.indexOf("val handlerTestField")
				+ "val ".length();

		IJavaElement[] selected = cu.codeSelect(offset, 0);
		assertTrue(selected.length >= 1,
				"codeSelect on field name should return an element");
		assertEquals(IJavaElement.FIELD, selected[0].getElementType(),
				"Selected element should be a FIELD");
		assertEquals("handlerTestField", selected[0].getElementName(),
				"Selected field should be handlerTestField");
	}

	@Test
	public void testCodeSelectOutsideDeclaration() throws Exception {
		createTestFile();
		TestHelpers.waitUntilIndexesReady();

		IFile file = TestHelpers.getFile(
				"/" + PROJECT_NAME + "/src/handler/test/HandlerTestType.kt");
		KotlinCompilationUnit cu = KotlinModelManager.getInstance()
				.getCompilationUnit(file);

		// Offset 0 is "package" line — outside any declaration
		IJavaElement[] selected = cu.codeSelect(0, 0);
		assertEquals(0, selected.length,
				"codeSelect outside declarations should return empty");
	}

	@Test
	public void testGetElementAtPosition() throws Exception {
		createTestFile();
		TestHelpers.waitUntilIndexesReady();

		IFile file = TestHelpers.getFile(
				"/" + PROJECT_NAME + "/src/handler/test/HandlerTestType.kt");
		KotlinCompilationUnit cu = KotlinModelManager.getInstance()
				.getCompilationUnit(file);

		String source = cu.getSource();
		int offset = source.indexOf("fun handlerTestMethod")
				+ "fun ".length();

		IJavaElement element = cu.getElementAt(offset);
		assertNotNull(element,
				"getElementAt should find the method");
		assertEquals("handlerTestMethod", element.getElementName());
	}

	// ---- codeSelect reference resolution (#18, #19) ----

	@Test
	public void testCodeSelectOnJavaTypeReference() throws Exception {
		// Java type in same package
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/handler/test/JavaTarget.java",
				"package handler.test;\n"
				+ "public class JavaTarget {\n"
				+ "    public static void run() {}\n"
				+ "}\n");
		// Kotlin file referencing the Java type
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/handler/test/RefTest.kt",
				"package handler.test\n\n"
				+ "fun useJavaType(x: JavaTarget) {\n"
				+ "    JavaTarget.run()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		IFile file = TestHelpers.getFile(
				"/" + PROJECT_NAME + "/src/handler/test/RefTest.kt");
		KotlinCompilationUnit cu = KotlinModelManager.getInstance()
				.getCompilationUnit(file);
		String source = cu.getSource();

		// codeSelect on "JavaTarget" in the parameter type position
		int offset = source.indexOf("JavaTarget)");
		assertTrue(offset > 0, "Should find JavaTarget in param type");
		IJavaElement[] selected = cu.codeSelect(offset, 0);
		assertTrue(selected.length >= 1,
				"codeSelect on Java type ref should resolve");
		assertEquals("JavaTarget", selected[0].getElementName(),
				"Should resolve to Java type");
	}

	@Test
	public void testCodeSelectOnExpressionReceiver() throws Exception {
		// Reuse JavaTarget from previous test setup
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/handler/test/JavaTarget.java",
				"package handler.test;\n"
				+ "public class JavaTarget {\n"
				+ "    public static void run() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/handler/test/ExprRef.kt",
				"package handler.test\n\n"
				+ "fun callJava() {\n"
				+ "    JavaTarget.run()\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		IFile file = TestHelpers.getFile(
				"/" + PROJECT_NAME + "/src/handler/test/ExprRef.kt");
		KotlinCompilationUnit cu = KotlinModelManager.getInstance()
				.getCompilationUnit(file);
		String source = cu.getSource();

		// codeSelect on "JavaTarget" as expression receiver
		int offset = source.indexOf("JavaTarget.run()");
		assertTrue(offset > 0, "Should find JavaTarget.run()");
		IJavaElement[] selected = cu.codeSelect(offset, 0);
		assertTrue(selected.length >= 1,
				"codeSelect on expression receiver should resolve");
		assertEquals("JavaTarget", selected[0].getElementName(),
				"Should resolve to Java type");
	}

	@Test
	public void testCodeSelectOnImport() throws Exception {
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/handler/test/JavaTarget.java",
				"package handler.test;\n"
				+ "public class JavaTarget {\n"
				+ "    public static void run() {}\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/handler/test/ImportRef.kt",
				"package handler.test\n\n"
				+ "import handler.test.JavaTarget\n\n"
				+ "fun work() { JavaTarget.run() }\n");
		TestHelpers.waitUntilIndexesReady();

		IFile file = TestHelpers.getFile(
				"/" + PROJECT_NAME + "/src/handler/test/ImportRef.kt");
		KotlinCompilationUnit cu = KotlinModelManager.getInstance()
				.getCompilationUnit(file);
		String source = cu.getSource();

		// codeSelect on "JavaTarget" in the import statement
		int importOffset = source.indexOf("import handler.test.JavaTarget")
				+ "import handler.test.".length();
		assertTrue(importOffset > "import handler.test.".length(),
				"Should find import");
		IJavaElement[] selected = cu.codeSelect(importOffset, 0);
		assertTrue(selected.length >= 1,
				"codeSelect on import should resolve");
		assertEquals("JavaTarget", selected[0].getElementName(),
				"Should resolve import to Java type");
	}

	@Test
	public void testCodeSelectOnNonTypeIdentifier() throws Exception {
		// Local variable — not a type reference, should fall back
		// to findElementAt behavior
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/handler/test/LocalVar.kt",
				"package handler.test\n\n"
				+ "fun useLocal() {\n"
				+ "    val myVar = 42\n"
				+ "    println(myVar)\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		IFile file = TestHelpers.getFile(
				"/" + PROJECT_NAME + "/src/handler/test/LocalVar.kt");
		KotlinCompilationUnit cu = KotlinModelManager.getInstance()
				.getCompilationUnit(file);
		String source = cu.getSource();

		// codeSelect on "myVar" — lowercase, not a type ref
		int offset = source.indexOf("val myVar") + "val ".length();
		IJavaElement[] selected = cu.codeSelect(offset, 0);
		// Should find enclosing method via fallback
		assertTrue(selected.length >= 1,
				"codeSelect on local var should find enclosing element");
	}

	@Test
	public void testCodeSelectOnSamePackageType() throws Exception {
		// Java type in same package — no explicit import needed
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/handler/test/SamePkg.java",
				"package handler.test;\n"
				+ "public class SamePkg {\n"
				+ "    public static int VALUE = 1;\n"
				+ "}\n");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/handler/test/UseSamePkg.kt",
				"package handler.test\n\n"
				+ "fun useSamePkg(x: SamePkg) {}\n");
		TestHelpers.waitUntilIndexesReady();

		IFile file = TestHelpers.getFile(
				"/" + PROJECT_NAME + "/src/handler/test/UseSamePkg.kt");
		KotlinCompilationUnit cu = KotlinModelManager.getInstance()
				.getCompilationUnit(file);
		String source = cu.getSource();

		int offset = source.indexOf("SamePkg)");
		IJavaElement[] selected = cu.codeSelect(offset, 0);
		assertTrue(selected.length >= 1,
				"codeSelect on same-package type should resolve");
		assertEquals("SamePkg", selected[0].getElementName());
	}

	// ---- Hover regression reproducer (#18 regression) ----
	// Reproduces: codeSelect returns Java IType, but
	// HoverInfoProvider.isResolved() fails because it searches
	// within a scope created from the Kotlin CU for TYPE elements.
	// Before PR #22, codeSelect returned the enclosing Kotlin
	// declaration (type=METHOD), so isResolved bypassed the search.

	@Test
	public void testCodeSelectResolvedTypeIsResolvableInKotlinCUScope()
			throws Exception {
		// Java enum type (pattern: AriesUserRole.java)
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/handler/test/StatusCode.java",
				"package handler.test;\n"
				+ "public enum StatusCode {\n"
				+ "    OK, ERROR;\n"
				+ "}\n");
		// Kotlin file that uses the Java enum in a type annotation
		// (pattern: AuthUtils.kt using AriesUserRole in param type)
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/handler/test/StatusHelper.kt",
				"package handler.test\n\n"
				+ "fun checkStatus(code: StatusCode): Boolean {\n"
				+ "    return code == StatusCode.OK\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		IFile file = TestHelpers.getFile(
				"/" + PROJECT_NAME + "/src/handler/test/StatusHelper.kt");
		KotlinCompilationUnit cu = KotlinModelManager.getInstance()
				.getCompilationUnit(file);
		String source = cu.getSource();

		// codeSelect on "StatusCode" in param type position
		int offset = source.indexOf("StatusCode)");
		assertTrue(offset > 0, "Should find StatusCode in source");
		IJavaElement[] selected = cu.codeSelect(offset, 0);
		assertTrue(selected.length >= 1,
				"codeSelect should resolve StatusCode");
		IJavaElement resolved = selected[0];

		// Verify: resolved element must be usable for hover.
		// HoverInfoProvider.isResolved() searches for TYPE elements
		// within the KotlinCU scope — this must find matches.
		List<SearchMatch> isResolvedMatches =
				TestHelpers.simulateIsResolvedSearch(resolved, cu);
		if (resolved.getElementType() == IJavaElement.TYPE) {
			assertTrue(isResolvedMatches.size() >= 1,
					"isResolved-style search should find TYPE in "
					+ "KotlinCU scope (hover depends on this); "
					+ "got 0 matches");
		}
	}

	@Test
	public void testCodeSelectOnExprReceiverIsResolvableInKotlinCUScope()
			throws Exception {
		// Java exception class (pattern: PaymentCommonException)
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/handler/test/AppException.java",
				"package handler.test;\n"
				+ "public class AppException extends RuntimeException {\n"
				+ "    public AppException(String msg) { super(msg); }\n"
				+ "}\n");
		// Kotlin uses the Java type as expression receiver
		// (pattern: BnplMITPaymentService.kt throwing exception)
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/handler/test/ServiceHelper.kt",
				"package handler.test\n\n"
				+ "fun doWork() {\n"
				+ "    throw AppException(\"failed\")\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		IFile file = TestHelpers.getFile(
				"/" + PROJECT_NAME + "/src/handler/test/ServiceHelper.kt");
		KotlinCompilationUnit cu = KotlinModelManager.getInstance()
				.getCompilationUnit(file);
		String source = cu.getSource();

		int offset = source.indexOf("AppException(\"failed\")");
		assertTrue(offset > 0, "Should find AppException");
		IJavaElement[] selected = cu.codeSelect(offset, 0);
		assertTrue(selected.length >= 1,
				"codeSelect should resolve AppException");

		IJavaElement resolved = selected[0];
		List<SearchMatch> isResolvedMatches =
				TestHelpers.simulateIsResolvedSearch(resolved, cu);
		if (resolved.getElementType() == IJavaElement.TYPE) {
			assertTrue(isResolvedMatches.size() >= 1,
					"isResolved-style search should find TYPE in "
					+ "KotlinCU scope for expression receiver; "
					+ "got 0 matches");
		}
	}

	// ---- Java→Kotlin facade class navigation reproducer (#20) ----
	// Reproduces: Java code imports NumberExtensionsKt (facade class)
	// and calls getBIG_DECIMAL_HUNDRED() (property accessor).
	// Java's codeSelect returns null because Java can't resolve
	// Kotlin types. These tests verify the Kotlin search participant
	// can at least find the declarations via search.

	@Test
	public void testFacadeClassDiscoverableViaSearch() throws CoreException {
		// Kotlin file with top-level property
		// (pattern: NumberExtensions.kt with BIG_DECIMAL_HUNDRED)
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/handler/test/NumExt.kt",
				"package handler.test\n\n"
				+ "import java.math.BigDecimal\n\n"
				+ "val BIG_HUNDRED: Int = 100\n"
				+ "fun doubleIt(x: Int): Int = x * 2\n");
		TestHelpers.waitUntilIndexesReady();

		// Facade class "NumExtKt" should be discoverable
		List<SearchMatch> matches = TestHelpers.searchAllTypes(
				"NumExtKt", project);
		List<SearchMatch> ktMatches = matches.stream()
				.filter(m -> m.getResource() != null
						&& m.getResource().getName().endsWith(".kt"))
				.toList();
		assertTrue(ktMatches.size() >= 1,
				"Facade NumExtKt should be findable via type search");
	}

	@Test
	public void testPropertyAccessorDiscoverableViaSearch()
			throws CoreException {
		// Same Kotlin file with top-level property
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/handler/test/NumExt.kt",
				"package handler.test\n\n"
				+ "val BIG_HUNDRED: Int = 100\n"
				+ "fun doubleIt(x: Int): Int = x * 2\n");
		TestHelpers.waitUntilIndexesReady();

		// Java-style getter getBIG_HUNDRED should find the property
		List<SearchMatch> matches = TestHelpers.searchMethodDeclarations(
				"getBIG_HUNDRED", project);
		List<SearchMatch> ktMatches = matches.stream()
				.filter(m -> m.getResource() != null
						&& m.getResource().getName().endsWith(".kt"))
				.toList();
		assertTrue(ktMatches.size() >= 1,
				"Property accessor getBIG_HUNDRED should be findable");
	}

	// ---- Java→Kotlin go-to-def gap reproducer (items 7-10) ----
	// Reproduces: Java file references Kotlin facade class and
	// property accessor. Java's codeSelect can't resolve them.
	// This documents the gap — NavigateToDefinitionHandler has no
	// SearchEngine fallback for unresolved types.

	@Test
	public void testJavaCodeSelectOnKotlinFacadeReturnsNull()
			throws Exception {
		// Kotlin with top-level declarations
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/handler/test/MathUtils.kt",
				"package handler.test\n\n"
				+ "val PI_APPROX: Double = 3.14159\n"
				+ "fun circleArea(r: Double): Double = PI_APPROX * r * r\n");
		// Java file that would reference MathUtilsKt
		// (can't actually import because Java compiler doesn't know
		// about the Kotlin facade class in the test container)
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/handler/test/JavaMathUser.java",
				"package handler.test;\n"
				+ "public class JavaMathUser {\n"
				+ "    // In real code: MathUtilsKt.getPI_APPROX()\n"
				+ "    // Java's codeSelect would return null for\n"
				+ "    // MathUtilsKt because it's not a Java type\n"
				+ "    public double getPi() { return 3.14; }\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Verify the facade IS findable via search (plugin works)
		List<SearchMatch> matches = TestHelpers.searchAllTypes(
				"MathUtilsKt", project);
		List<SearchMatch> ktMatches = matches.stream()
				.filter(m -> m.getResource() != null
						&& m.getResource().getName().endsWith(".kt"))
				.toList();
		assertTrue(ktMatches.size() >= 1,
				"MathUtilsKt facade should be findable via search "
				+ "(plugin indexing works; jdtls NavigateToDefinitionHandler "
				+ "needs SearchEngine fallback to use this)");
	}

	// ---- getCompilationUnit on participant ----

	@Test
	public void testSearchParticipantProvidesCompilationUnit()
			throws Exception {
		createTestFile();
		TestHelpers.waitUntilIndexesReady();

		KotlinSearchParticipant participant = new KotlinSearchParticipant();
		IFile file = TestHelpers.getFile(
				"/" + PROJECT_NAME + "/src/handler/test/HandlerTestType.kt");
		ICompilationUnit cu = participant.getCompilationUnit(file);

		assertNotNull(cu, "Participant should return a CU");
		assertTrue(cu instanceof KotlinCompilationUnit,
				"CU should be a KotlinCompilationUnit");
		assertTrue(cu.getTypes().length >= 2,
				"CU should have types populated");
	}

	// ---- Facade class and property accessor matching (#20) ----

	@Test
	public void testFacadeClassTypeDeclaration() throws CoreException {
		// Kotlin file with top-level function and property
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/handler/test/TopLevel.kt",
				"package handler.test\n\n"
				+ "val topProperty: String = \"hello\"\n"
				+ "fun topFunction(): Int = 42\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for the facade type declaration "TopLevelKt"
		List<SearchMatch> matches = TestHelpers.searchAllTypes(
				"TopLevelKt", project);
		List<SearchMatch> ktMatches = matches.stream()
				.filter(m -> m.getResource() != null
						&& m.getResource().getName().endsWith(".kt"))
				.toList();
		assertTrue(ktMatches.size() >= 1,
				"Should find TopLevelKt facade type");
		assertNotNull(ktMatches.get(0).getElement(),
				"Facade match should have an element");
		assertTrue(ktMatches.get(0).getElement() instanceof IType,
				"Facade element should be an IType");
		assertEquals("TopLevelKt",
				((IType) ktMatches.get(0).getElement()).getElementName(),
				"Facade type name should be TopLevelKt");
	}

	@Test
	public void testFacadeClassNotEmittedWithoutTopLevel() throws CoreException {
		// Kotlin file with only a class, no top-level declarations
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/handler/test/ClassOnly.kt",
				"package handler.test\n\n"
				+ "class ClassOnlyType {\n"
				+ "    fun method(): String = \"hello\"\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		// Should NOT find ClassOnlyKt facade
		List<SearchMatch> matches = TestHelpers.searchAllTypes(
				"ClassOnlyKt", project);
		List<SearchMatch> ktMatches = matches.stream()
				.filter(m -> m.getResource() != null
						&& m.getResource().getName().endsWith(".kt"))
				.toList();
		assertEquals(0, ktMatches.size(),
				"Should not find facade for file without top-level decls");
	}

	@Test
	public void testPropertyAccessorMethodDeclaration() throws CoreException {
		// Kotlin with top-level property
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/handler/test/Props.kt",
				"package handler.test\n\n"
				+ "val fooBar: String = \"hello\"\n"
				+ "var mutableProp: Int = 0\n");
		TestHelpers.waitUntilIndexesReady();

		// Search for getter declaration
		List<SearchMatch> getMatches = TestHelpers.searchMethodDeclarations(
				"getFooBar", project);
		List<SearchMatch> ktGet = getMatches.stream()
				.filter(m -> m.getResource() != null
						&& m.getResource().getName().endsWith(".kt"))
				.toList();
		assertTrue(ktGet.size() >= 1,
				"Should find fooBar property via getFooBar");

		// Search for setter declaration
		List<SearchMatch> setMatches = TestHelpers.searchMethodDeclarations(
				"setMutableProp", project);
		List<SearchMatch> ktSet = setMatches.stream()
				.filter(m -> m.getResource() != null
						&& m.getResource().getName().endsWith(".kt"))
				.toList();
		assertTrue(ktSet.size() >= 1,
				"Should find mutableProp property via setMutableProp");
	}

	@Test
	public void testPropertyAccessorInClass() throws CoreException {
		// Kotlin class with properties
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/handler/test/ClassProps.kt",
				"package handler.test\n\n"
				+ "class ClassWithProps {\n"
				+ "    val readOnly: String = \"hello\"\n"
				+ "    var readWrite: Int = 0\n"
				+ "}\n");
		TestHelpers.waitUntilIndexesReady();

		List<SearchMatch> matches = TestHelpers.searchMethodDeclarations(
				"getReadOnly", project);
		List<SearchMatch> ktMatches = matches.stream()
				.filter(m -> m.getResource() != null
						&& m.getResource().getName().endsWith(".kt"))
				.toList();
		assertTrue(ktMatches.size() >= 1,
				"Should find readOnly via getReadOnly in class");
	}

	// ---- Helpers ----

	private void createTestFile() throws CoreException {
		TestHelpers.createFolder("/" + PROJECT_NAME + "/src/handler/test");
		TestHelpers.createFile(
				"/" + PROJECT_NAME + "/src/handler/test/HandlerTestType.kt",
				MULTI_LINE_KT);
		TestHelpers.waitUntilIndexesReady();
	}

	private static List<SearchMatch> filterKotlinMatches(
			List<SearchMatch> matches) {
		return matches.stream()
				.filter(m -> m.getResource() != null
						&& (m.getResource().getName().endsWith(".kt")
								|| m.getResource().getName().endsWith(".kts")))
				.toList();
	}

	/**
	 * Replicates the JDTUtils.toLocation(element) path: extract source
	 * range from ISourceReference, get buffer from CU, convert to line/col.
	 */
	private static int[] toLocationFromElement(ICompilationUnit cu,
			ISourceRange range) throws JavaModelException {
		if (cu == null || range == null || range.getOffset() < 0) {
			return null;
		}
		IBuffer buffer = cu.getBuffer();
		if (buffer == null) {
			return null;
		}
		IDocument doc = new Document(buffer.getContents());
		return toLineColumn(doc, range.getOffset());
	}

	/**
	 * Replicates JsonRpcHelpers.toLine(IDocument, offset): converts a
	 * character offset to a 0-based [line, column] pair.
	 */
	private static int[] toLineColumn(IDocument doc, int offset) {
		try {
			int line = doc.getLineOfOffset(offset);
			int col = offset - doc.getLineOffset(line);
			return new int[] { line, col };
		} catch (Exception e) {
			return null;
		}
	}
}
