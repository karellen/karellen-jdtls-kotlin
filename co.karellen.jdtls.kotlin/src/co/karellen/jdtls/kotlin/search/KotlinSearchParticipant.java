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
package co.karellen.jdtls.kotlin.search;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import co.karellen.jdtls.kotlin.parser.KotlinParser;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeHierarchy;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.search.IJavaSearchScope;
import org.eclipse.jdt.core.search.SearchDocument;
import org.eclipse.jdt.core.search.SearchMatch;
import org.eclipse.jdt.core.search.SearchParticipant;
import org.eclipse.jdt.core.search.SearchPattern;
import org.eclipse.jdt.core.search.SearchRequestor;
import org.eclipse.jdt.internal.core.JavaModelManager;
import org.eclipse.jdt.internal.core.index.IndexLocation;
import org.eclipse.jdt.internal.core.search.indexing.IIndexConstants;
import org.eclipse.jdt.internal.core.search.indexing.IndexManager;
import org.eclipse.jdt.internal.core.search.matching.FieldPattern;
import org.eclipse.jdt.internal.core.search.matching.MethodPattern;
import org.eclipse.jdt.internal.core.search.matching.SuperTypeReferencePattern;
import org.eclipse.jdt.internal.core.search.matching.TypeDeclarationPattern;
import org.eclipse.jdt.internal.core.search.matching.TypeReferencePattern;

/**
 * Kotlin search participant for {@code .kt} and {@code .kts} files.
 * <p>
 * Registers via the {@code org.eclipse.jdt.core.searchParticipant} extension
 * point. Parses Kotlin source files using an ANTLR4-based parser to extract
 * declarations and emit JDT index entries for cross-language code intelligence.
 *
 * @author Arcadiy Ivanov
 */
public class KotlinSearchParticipant extends SearchParticipant {

	private record EnclosingContext(
			KotlinDeclaration decl,
			KotlinDeclaration.TypeDeclaration type) {
	}

	private final KotlinModelManager modelManager =
			KotlinModelManager.getInstance();

	public KotlinSearchParticipant() {
	}

	@Override
	public String getDescription() {
		return "Kotlin Search Participant";
	}

	@Override
	public SearchDocument getDocument(String documentPath) {
		return new KotlinSearchDocument(documentPath, this);
	}

	/**
	 * Returns a {@link KotlinCompilationUnit} for the given .kt file.
	 * Intended to override {@code SearchParticipant.getCompilationUnit()}
	 * once §1c of UPSTREAM-PATCHES.md lands in JDT Core PR #4938.
	 * Until then, called directly by tests and internal consumers.
	 */
	public ICompilationUnit getCompilationUnit(IFile file) {
		return modelManager.getCompilationUnit(file);
	}

	@Override
	public void indexDocument(SearchDocument document, IPath indexLocation) {
		document.removeAllIndexEntries();

		char[] contents = document.getCharContents();
		String path = document.getPath();

		if (contents == null || contents.length == 0) {
			// Empty file: fall back to path-based stub indexing
			indexFromPath(document, path);
			modelManager.invalidateFileModel(path);
			return;
		}

		KotlinFileModel fileModel = modelManager.getFileModel(
				path, contents);
		if (fileModel == null) {
			// Parse failure: fall back to path-based stub indexing
			indexFromPath(document, path);
			return;
		}

		String packageName = fileModel.getPackageName();

		// Update symbol table for this file
		List<SymbolTable.TypeSymbol> typeSymbols = new ArrayList<>();

		// Index declarations
		for (KotlinDeclaration decl : fileModel.getDeclarations()) {
			indexDeclaration(document, decl, packageName, null, typeSymbols);
		}

		// Register types in symbol table
		modelManager.getSymbolTable().addTypesFromFile(path, typeSymbols);

		// Register top-level functions and properties in symbol table
		for (KotlinDeclaration decl : fileModel.getDeclarations()) {
			if (decl instanceof KotlinDeclaration.MethodDeclaration md
					&& md.getEnclosingTypeName() == null) {
				String fqn = packageName != null
						? packageName + "." + md.getName() : md.getName();
				modelManager.getSymbolTable().addTopLevelFunction(new SymbolTable.MethodSymbol(
						md.getName(),
						md.getParameters().stream()
								.map(KotlinDeclaration.MethodDeclaration.Parameter::getTypeName)
								.toList(),
						md.getParameters().stream()
								.map(KotlinDeclaration.MethodDeclaration.Parameter::getName)
								.toList(),
						md.getReturnTypeName(),
						md.getReceiverTypeName(),
						md.getModifiers(),
						fqn,
						md.hasDefaultParams()));
			} else if (decl instanceof KotlinDeclaration.PropertyDeclaration pd
					&& pd.getEnclosingTypeName() == null) {
				String fqn = packageName != null
						? packageName + "." + pd.getName() : pd.getName();
				modelManager.getSymbolTable().addTopLevelProperty(new SymbolTable.FieldSymbol(
						pd.getName(), pd.getTypeName(),
						pd.getModifiers(), fqn));
			}
		}

		// Index expression references (METHOD_REF, CONSTRUCTOR_REF, REF)
		try {
			indexExpressionReferences(document, fileModel);
		} catch (Exception e) {
			org.eclipse.core.runtime.Platform.getLog(
					KotlinSearchParticipant.class).error(
					"Kotlin expression indexing failed for " + path,
					e);
		}
	}

	private void indexDeclaration(SearchDocument document, KotlinDeclaration decl,
			String packageName, char[][] enclosingTypes, List<SymbolTable.TypeSymbol> typeSymbols) {
		if (decl instanceof KotlinDeclaration.TypeDeclaration typeDecl) {
			indexTypeDeclaration(document, typeDecl, packageName, enclosingTypes, typeSymbols);
		} else if (decl instanceof KotlinDeclaration.MethodDeclaration methodDecl) {
			indexMethodDeclaration(document, methodDecl, packageName, enclosingTypes);
		} else if (decl instanceof KotlinDeclaration.PropertyDeclaration propDecl) {
			indexPropertyDeclaration(document, propDecl);
		} else if (decl instanceof KotlinDeclaration.ConstructorDeclaration ctorDecl) {
			indexConstructorDeclaration(document, ctorDecl, packageName, enclosingTypes);
		} else if (decl instanceof KotlinDeclaration.TypeAliasDeclaration aliasDecl) {
			indexTypeAliasDeclaration(document, aliasDecl, packageName, enclosingTypes);
		}
	}

	private void indexTypeDeclaration(SearchDocument document,
			KotlinDeclaration.TypeDeclaration typeDecl, String packageName,
			char[][] enclosingTypes, List<SymbolTable.TypeSymbol> typeSymbols) {
		String name = typeDecl.getName();
		if (name == null || name.isEmpty()) {
			return;
		}

		int modifiers = typeDecl.getModifiers();
		boolean isSecondary = enclosingTypes != null && enclosingTypes.length > 0;

		char[] indexKey = TypeDeclarationPattern.createIndexKey(
				modifiers,
				name.toCharArray(),
				packageName != null ? packageName.toCharArray() : null,
				enclosingTypes,
				isSecondary
		);
		document.addIndexEntry(IIndexConstants.TYPE_DECL, indexKey);

		// Index supertypes as SUPER_REF
		char typeSuffix = getSupertypeKind(typeDecl);
		for (String supertype : typeDecl.getSupertypes()) {
			char[] superKey = SuperTypeReferencePattern.createIndexKey(
					modifiers,
					packageName != null ? packageName.toCharArray() : null,
					name.toCharArray(),
					enclosingTypes,
					null, // typeParameterSignatures
					typeSuffix,
					supertype.toCharArray(),
					IIndexConstants.CLASS_SUFFIX
			);
			document.addIndexEntry(IIndexConstants.SUPER_REF, superKey);
		}

		// Build TypeSymbol for the symbol table
		String fqn = packageName != null ? packageName + "." + name : name;
		List<SymbolTable.MethodSymbol> methods = new ArrayList<>();
		List<SymbolTable.FieldSymbol> fields = new ArrayList<>();
		List<SymbolTable.ConstructorSymbol> constructors = new ArrayList<>();

		for (KotlinDeclaration member : typeDecl.getMembers()) {
			if (member instanceof KotlinDeclaration.MethodDeclaration md) {
				methods.add(new SymbolTable.MethodSymbol(
						md.getName(),
						md.getParameters().stream().map(KotlinDeclaration.MethodDeclaration.Parameter::getTypeName).toList(),
						md.getParameters().stream().map(KotlinDeclaration.MethodDeclaration.Parameter::getName).toList(),
						md.getReturnTypeName(),
						md.getReceiverTypeName(),
						md.getModifiers(),
						fqn,
						md.hasDefaultParams()
				));
			} else if (member instanceof KotlinDeclaration.PropertyDeclaration pd) {
				fields.add(new SymbolTable.FieldSymbol(
						pd.getName(), pd.getTypeName(), pd.getModifiers(), fqn));
			} else if (member instanceof KotlinDeclaration.ConstructorDeclaration cd) {
				constructors.add(new SymbolTable.ConstructorSymbol(
						cd.getParameters().stream().map(KotlinDeclaration.MethodDeclaration.Parameter::getTypeName).toList(),
						cd.getParameters().stream().map(KotlinDeclaration.MethodDeclaration.Parameter::getName).toList(),
						cd.getModifiers(),
						fqn
				));
			}
		}

		typeSymbols.add(new SymbolTable.TypeSymbol(
				fqn, name, packageName, modifiers,
				typeDecl.getSupertypes(), methods, fields, constructors
		));

		// Recursively index nested declarations
		char[][] newEnclosing;
		if (enclosingTypes == null) {
			newEnclosing = new char[][] { name.toCharArray() };
		} else {
			newEnclosing = new char[enclosingTypes.length + 1][];
			System.arraycopy(enclosingTypes, 0, newEnclosing, 0, enclosingTypes.length);
			newEnclosing[enclosingTypes.length] = name.toCharArray();
		}

		for (KotlinDeclaration member : typeDecl.getMembers()) {
			if (member instanceof KotlinDeclaration.TypeDeclaration nestedType) {
				indexTypeDeclaration(document, nestedType, packageName, newEnclosing, typeSymbols);
			} else {
				indexDeclaration(document, member, packageName, newEnclosing, typeSymbols);
			}
		}
	}

	private void indexMethodDeclaration(SearchDocument document,
			KotlinDeclaration.MethodDeclaration methodDecl, String packageName,
			char[][] enclosingTypes) {
		String name = methodDecl.getName();
		if (name == null || name.isEmpty()) {
			return;
		}

		int paramCount = methodDecl.getParameters().size();
		char[] indexKey = MethodPattern.createIndexKey(
				name.toCharArray(),
				paramCount
		);
		document.addIndexEntry(IIndexConstants.METHOD_DECL, indexKey);
	}

	private void indexPropertyDeclaration(SearchDocument document,
			KotlinDeclaration.PropertyDeclaration propDecl) {
		String name = propDecl.getName();
		if (name == null || name.isEmpty()) {
			return;
		}
		document.addIndexEntry(IIndexConstants.FIELD_DECL, name.toCharArray());
	}

	private void indexConstructorDeclaration(SearchDocument document,
			KotlinDeclaration.ConstructorDeclaration ctorDecl, String packageName,
			char[][] enclosingTypes) {
		int paramCount = ctorDecl.getParameters().size();
		char[] typeName = (enclosingTypes != null && enclosingTypes.length > 0)
				? enclosingTypes[enclosingTypes.length - 1]
				: null;
		if (typeName != null) {
			char[] indexKey = MethodPattern.createIndexKey(typeName, paramCount);
			document.addIndexEntry(IIndexConstants.CONSTRUCTOR_DECL, indexKey);
		}
	}

	private void indexTypeAliasDeclaration(SearchDocument document,
			KotlinDeclaration.TypeAliasDeclaration aliasDecl, String packageName,
			char[][] enclosingTypes) {
		String name = aliasDecl.getName();
		if (name == null || name.isEmpty()) {
			return;
		}
		// Index as TYPE_DECL so type searches find type aliases
		char[] indexKey = TypeDeclarationPattern.createIndexKey(
				aliasDecl.getModifiers(),
				name.toCharArray(),
				packageName != null ? packageName.toCharArray() : null,
				enclosingTypes,
				false
		);
		document.addIndexEntry(IIndexConstants.TYPE_DECL, indexKey);

		// Also index the aliased type as a REF
		String aliasedType = aliasDecl.getAliasedTypeName();
		if (aliasedType != null && !aliasedType.isEmpty()) {
			// Strip type arguments and nullable suffix for the REF
			String simpleAliased = aliasedType;
			int angleIdx = simpleAliased.indexOf('<');
			if (angleIdx >= 0) {
				simpleAliased = simpleAliased.substring(0, angleIdx);
			}
			if (simpleAliased.endsWith("?")) {
				simpleAliased = simpleAliased.substring(0,
						simpleAliased.length() - 1);
			}
			int lastDot = simpleAliased.lastIndexOf('.');
			if (lastDot >= 0) {
				simpleAliased = simpleAliased.substring(lastDot + 1);
			}
			if (!simpleAliased.isEmpty()) {
				document.addIndexEntry(IIndexConstants.REF,
						simpleAliased.toCharArray());
			}
		}
	}

	private void indexExpressionReferences(SearchDocument document,
			KotlinFileModel fileModel) {
		KotlinReferenceIndexer indexer = new KotlinReferenceIndexer(
				document, fileModel.getImports());
		indexer.index(fileModel.getParseTree());
	}

	private char getSupertypeKind(KotlinDeclaration.TypeDeclaration typeDecl) {
		return switch (typeDecl.getKind()) {
			case INTERFACE -> IIndexConstants.INTERFACE_SUFFIX;
			case ENUM -> IIndexConstants.ENUM_SUFFIX;
			case ANNOTATION -> IIndexConstants.ANNOTATION_TYPE_SUFFIX;
			default -> IIndexConstants.CLASS_SUFFIX;
		};
	}

	/**
	 * Fallback: derive index entries from the file path when parsing fails
	 * or the file is empty.
	 */
	private void indexFromPath(SearchDocument document, String path) {
		String className = deriveClassName(path);
		if (className == null) {
			return;
		}
		String packageName = derivePackageName(path);
		char[] indexKey = TypeDeclarationPattern.createIndexKey(
				0,
				className.toCharArray(),
				packageName != null ? packageName.toCharArray() : null,
				null,
				false
		);
		document.addIndexEntry(IIndexConstants.TYPE_DECL, indexKey);
	}

	// ---- locateMatches: pattern-aware match reporting ----

	@Override
	public void locateMatches(SearchDocument[] documents, SearchPattern pattern,
			IJavaSearchScope scope, SearchRequestor requestor,
			IProgressMonitor monitor) throws CoreException {
		for (SearchDocument document : documents) {
			if (monitor != null && monitor.isCanceled()) {
				return;
			}
			String path = document.getPath();
			IResource resource = ResourcesPlugin.getWorkspace().getRoot()
					.findMember(IPath.fromPortableString(path));
			if (resource == null || !(resource instanceof IFile)) {
				continue;
			}
			IFile file = (IFile) resource;

			// Get or parse the file model
			KotlinFileModel fileModel = modelManager.getFileModel(
					path, document.getCharContents());
			if (fileModel == null) {
				// Empty/unparseable file: report path-derived match
				reportPathDerivedMatch(path, pattern, resource, requestor);
				continue;
			}

			KotlinCompilationUnit cu = modelManager.getCompilationUnit(file);
			String packageName = fileModel.getPackageName();

			boolean doDeclarations = false;
			boolean doReferences = false;

			if (pattern instanceof TypeDeclarationPattern
					|| pattern instanceof SuperTypeReferencePattern) {
				doDeclarations = true;
			} else if (pattern instanceof TypeReferencePattern) {
				doReferences = true;
			} else if (pattern instanceof MethodPattern
					|| pattern instanceof FieldPattern) {
				// Determine search mode from the pattern's index categories.
				// Categories tell us which index tables JDT queries:
				// - REF/METHOD_REF: reference search
				// - METHOD_DECL alone: declaration-only search
				// - FIELD_DECL: for MethodPattern means declaration search,
				//   but for FieldPattern it also appears in reference searches
				//   (write-access tracking), so we need type-specific logic.
				boolean isField = pattern instanceof FieldPattern;
				boolean hasRef = false;
				boolean hasDecl = false;
				char[][] categories = pattern.getIndexCategories();
				if (categories != null) {
					for (char[] cat : categories) {
						if (Arrays.equals(cat,
								IIndexConstants.METHOD_REF)
								|| Arrays.equals(cat,
										IIndexConstants.REF)) {
							hasRef = true;
						} else if (Arrays.equals(cat,
								IIndexConstants.METHOD_DECL)
								|| Arrays.equals(cat,
										IIndexConstants.FIELD_DECL)) {
							hasDecl = true;
						}
					}
				}
				if (isField) {
					// FieldPattern: REF present → reference search (FIELD_DECL
					// is just write-access tracking, not "find declarations").
					// FIELD_DECL without REF → pure declaration search.
					if (hasRef) {
						doReferences = true;
					} else if (hasDecl) {
						doDeclarations = true;
					} else {
						doDeclarations = true;
					}
				} else {
					// MethodPattern: straightforward category mapping
					doReferences = hasRef;
					doDeclarations = hasDecl;
					if (!doDeclarations && !doReferences) {
						doDeclarations = true;
					}
				}
			}

			if (doDeclarations) {
				for (KotlinDeclaration decl : fileModel.getDeclarations()) {
					locateMatchesInDeclaration(decl, pattern, cu,
							requestor, packageName);
				}
			}
			if (doReferences) {
				locateReferenceMatches(fileModel, pattern, cu, requestor);
			}
		}
	}


	private void reportPathDerivedMatch(String path, SearchPattern pattern,
			IResource resource, SearchRequestor requestor)
			throws CoreException {
		String className = deriveClassName(path);
		if (className == null) {
			return;
		}
		// Only report for type declaration patterns
		if (pattern instanceof TypeDeclarationPattern) {
			KotlinCompilationUnit cu = resource instanceof IFile
					? new KotlinCompilationUnit((IFile) resource) : null;
			KotlinElement.KotlinTypeElement element = cu != null
					? new KotlinElement.KotlinTypeElement(className, cu,
							derivePackageName(path), null,
							0, className.length())
					: null;
			SearchMatch match = new SearchMatch(
					element,
					SearchMatch.A_ACCURATE,
					0,
					className.length(),
					this,
					resource
			);
			requestor.acceptSearchMatch(match);
		}
	}


	private void locateMatchesInDeclaration(KotlinDeclaration decl,
			SearchPattern pattern, KotlinCompilationUnit cu,
			SearchRequestor requestor, String packageName)
			throws CoreException {

		if (decl instanceof KotlinDeclaration.TypeDeclaration typeDecl) {
			boolean report = false;

			if (pattern instanceof TypeDeclarationPattern tdp) {
				report = matchesTypeName(typeDecl, tdp);
			} else if (pattern instanceof SuperTypeReferencePattern stp) {
				report = hasSupertype(typeDecl, stp);
			}

			if (report) {
				reportTypeMatch(typeDecl, cu, requestor, packageName);
			}

			// Recurse into all members (nested types, methods, properties)
			for (KotlinDeclaration member : typeDecl.getMembers()) {
				locateMatchesInDeclaration(member, pattern, cu, requestor,
						packageName);
			}
		} else if (decl instanceof KotlinDeclaration.MethodDeclaration methodDecl) {
			if (pattern instanceof MethodPattern mp) {
				if (matchesMethodName(methodDecl, mp)) {
					reportMethodMatch(methodDecl, cu, requestor, packageName);
				}
			}
		} else if (decl instanceof KotlinDeclaration.PropertyDeclaration propDecl) {
			if (pattern instanceof FieldPattern) {
				if (matchesFieldName(propDecl, pattern)) {
					reportFieldMatch(propDecl, cu, requestor, packageName);
				}
			}
		} else if (decl instanceof KotlinDeclaration.TypeAliasDeclaration aliasDecl) {
			if (pattern instanceof TypeDeclarationPattern tdp) {
				if (matchesTypeAliasName(aliasDecl, tdp)) {
					reportTypeAliasMatch(aliasDecl, cu, requestor,
							packageName);
				}
			}
		}
	}

	private boolean matchesTypeName(KotlinDeclaration.TypeDeclaration typeDecl,
			TypeDeclarationPattern tdp) {
		String name = typeDecl.getName();
		if (name == null || tdp.simpleName == null) {
			return false;
		}
		// QualifiedTypeDeclarationPattern stores simpleName in lowercase
		return name.equalsIgnoreCase(new String(tdp.simpleName));
	}

	private boolean hasSupertype(KotlinDeclaration.TypeDeclaration typeDecl,
			SuperTypeReferencePattern stp) {
		if (stp.superSimpleName == null) {
			return false;
		}
		String searchedSuper = new String(stp.superSimpleName);
		for (String supertype : typeDecl.getSupertypes()) {
			// Compare simple names: the supertype may be qualified (e.g. "pkg.Type")
			String simpleSuperName = supertype;
			int lastDot = supertype.lastIndexOf('.');
			if (lastDot >= 0) {
				simpleSuperName = supertype.substring(lastDot + 1);
			}
			// Pattern stores names in lowercase
			if (simpleSuperName.equalsIgnoreCase(searchedSuper)) {
				return true;
			}
		}
		return false;
	}

	private void reportTypeMatch(KotlinDeclaration.TypeDeclaration typeDecl,
			KotlinCompilationUnit cu, SearchRequestor requestor,
			String packageName) throws CoreException {
		// endOffset is from ANTLR getStopIndex() which is inclusive
		int sourceLength = typeDecl.getEndOffset()
				- typeDecl.getStartOffset() + 1;
		KotlinElement.KotlinTypeElement element = new KotlinElement.KotlinTypeElement(
				typeDecl.getName(), cu, packageName,
				typeDecl.getEnclosingTypeName(),
				typeDecl.getStartOffset(), sourceLength,
				typeDecl.getKind(),
				typeDecl.getSupertypes().toArray(new String[0]),
				typeDecl.getModifiers());
		SearchMatch match = new SearchMatch(element, SearchMatch.A_ACCURATE,
				typeDecl.getStartOffset(), sourceLength,
				this, cu.getResource());
		requestor.acceptSearchMatch(match);
	}

	private boolean matchesMethodName(
			KotlinDeclaration.MethodDeclaration methodDecl,
			MethodPattern mp) {
		String name = methodDecl.getName();
		if (name == null || mp.selector == null) {
			return false;
		}
		return name.equalsIgnoreCase(new String(mp.selector));
	}

	private boolean matchesFieldName(
			KotlinDeclaration.PropertyDeclaration propDecl,
			SearchPattern pattern) {
		String name = propDecl.getName();
		if (name == null) {
			return false;
		}
		char[] fieldName = pattern.getIndexKey();
		if (fieldName == null) {
			return false;
		}
		return name.equalsIgnoreCase(new String(fieldName));
	}

	private void reportMethodMatch(
			KotlinDeclaration.MethodDeclaration methodDecl,
			KotlinCompilationUnit cu, SearchRequestor requestor,
			String packageName) throws CoreException {
		int sourceLength = methodDecl.getEndOffset()
				- methodDecl.getStartOffset() + 1;
		KotlinElement.KotlinMethodElement element = createMethodElement(
				methodDecl, cu);
		SearchMatch match = new SearchMatch(element, SearchMatch.A_ACCURATE,
				methodDecl.getStartOffset(), sourceLength,
				this, cu.getResource());
		requestor.acceptSearchMatch(match);
	}

	private void reportFieldMatch(
			KotlinDeclaration.PropertyDeclaration propDecl,
			KotlinCompilationUnit cu, SearchRequestor requestor,
			String packageName) throws CoreException {
		int sourceLength = propDecl.getEndOffset()
				- propDecl.getStartOffset() + 1;
		KotlinElement.KotlinFieldElement element = new KotlinElement.KotlinFieldElement(
				propDecl.getName(), cu,
				propDecl.getStartOffset(), sourceLength,
				KotlinElement.toTypeSignature(propDecl.getTypeName()),
				false, propDecl.getModifiers());
		SearchMatch match = new SearchMatch(element, SearchMatch.A_ACCURATE,
				propDecl.getStartOffset(), sourceLength,
				this, cu.getResource());
		requestor.acceptSearchMatch(match);
	}

	private boolean matchesTypeAliasName(
			KotlinDeclaration.TypeAliasDeclaration aliasDecl,
			TypeDeclarationPattern tdp) {
		String name = aliasDecl.getName();
		if (name == null || tdp.simpleName == null) {
			return false;
		}
		return name.equalsIgnoreCase(new String(tdp.simpleName));
	}

	private void reportTypeAliasMatch(
			KotlinDeclaration.TypeAliasDeclaration aliasDecl,
			KotlinCompilationUnit cu, SearchRequestor requestor,
			String packageName) throws CoreException {
		int sourceLength = aliasDecl.getEndOffset()
				- aliasDecl.getStartOffset() + 1;
		KotlinElement.KotlinTypeElement element = new KotlinElement.KotlinTypeElement(
				aliasDecl.getName(), cu, packageName,
				aliasDecl.getEnclosingTypeName(),
				aliasDecl.getStartOffset(), sourceLength,
				null, null, aliasDecl.getModifiers());
		SearchMatch match = new SearchMatch(element, SearchMatch.A_ACCURATE,
				aliasDecl.getStartOffset(), sourceLength,
				this, cu.getResource());
		requestor.acceptSearchMatch(match);
	}

	// ---- Reference matching ----

	private void locateReferenceMatches(KotlinFileModel fileModel,
			SearchPattern pattern, KotlinCompilationUnit cu,
			SearchRequestor requestor) throws CoreException {
		String targetName = extractTargetName(pattern);
		if (targetName == null) {
			return;
		}

		boolean matchTypes = (pattern instanceof TypeReferencePattern);
		boolean matchMethods = (pattern instanceof MethodPattern);
		boolean matchFields = (pattern instanceof FieldPattern);

		// For type reference patterns, also match methods (constructor calls look
		// like method calls to the parser)
		if (pattern instanceof TypeReferencePattern) {
			matchMethods = true;
		}

		KotlinReferenceFinder finder = new KotlinReferenceFinder(
				targetName, matchTypes, matchMethods, matchFields,
				fileModel.getImports());
		List<KotlinReferenceFinder.ReferenceMatch> refMatches = finder
				.find(fileModel.getParseTree());

		String packageName = fileModel.getPackageName();

		// Build type resolution infrastructure for receiver verification
		ImportResolver importResolver = new ImportResolver(
				packageName, fileModel.getImports());
		SymbolTable symbolTable = modelManager.getSymbolTable();
		ScopeChain scopeChain = new ScopeChain(importResolver, symbolTable);
		scopeChain.initFileScope(fileModel);

		// Create expression type resolver for receiver type inference
		// with overload resolution support via SubtypeChecker
		SubtypeChecker subtypeChecker = new SubtypeChecker(symbolTable,
				cu.getJavaProject());
		ExpressionTypeResolver exprResolver = new ExpressionTypeResolver(
				scopeChain, symbolTable, importResolver, subtypeChecker);
		LambdaTypeResolver lambdaResolver = new LambdaTypeResolver(
				symbolTable, importResolver);

		// Extract declaring type name from pattern for receiver verification.
		// MethodPattern exposes declaringSimpleName publicly; for FieldPattern
		// (which doesn't), we verify by resolving the receiver type from our
		// AST and checking if it has the field via SymbolTable / JDT IType.
		String declaringSimpleName = extractDeclaringTypeName(pattern);

		// Track last enclosing declaration to avoid re-pushing identical
		// class/function scopes for consecutive references in the same method
		KotlinDeclaration lastEnclosingDecl = null;
		KotlinDeclaration.TypeDeclaration lastEnclosingType = null;
		int scopesBefore = scopeChain.depth();
		// Cache JDT type hierarchies per FQN to avoid repeated builds
		Map<String, ITypeHierarchy> hierarchyCache =
				new HashMap<>();
		// Lambda scopes for the current enclosing function
		List<LambdaScopeExtractor.LambdaScope> lambdaScopes =
				Collections.emptyList();
		// Smart cast scopes for the current enclosing function
		List<SmartCastExtractor.SmartCastScope> smartCastScopes =
				Collections.emptyList();
		// Track depth after function scope setup (before lambda push)
		int functionScopeDepth = scopesBefore;

		try {
			for (KotlinReferenceFinder.ReferenceMatch ref : refMatches) {
				// Find the enclosing declaration and its parent type
				EnclosingContext enclosing =
						findEnclosingDeclarationAndType(
								fileModel.getDeclarations(),
								ref.getOffset());
				KotlinDeclaration enclosingDecl = enclosing.decl();
				KotlinDeclaration.TypeDeclaration enclosingType =
						enclosing.type();
				if (enclosingDecl == null) {
					continue;
				}

				// Only re-push scopes when the enclosing context changes
				if (enclosingDecl != lastEnclosingDecl
						|| enclosingType != lastEnclosingType) {
					// Pop previous scopes back to file-level
					while (scopeChain.depth() > scopesBefore) {
						scopeChain.popScope();
					}
					if (enclosingType != null) {
						scopeChain.initClassScope(
								enclosingType, packageName);
					}
					if (enclosingDecl instanceof KotlinDeclaration.MethodDeclaration md) {
						scopeChain.initFunctionScope(md);
						List<LocalVariableExtractor.LocalVariable> locals =
								LocalVariableExtractor.extract(
										fileModel.getParseTree(),
										md.getStartOffset(),
										md.getEndOffset());
						if (!locals.isEmpty()) {
							scopeChain.initLocalVariableScope(locals,
									exprResolver);
						}
						lambdaScopes = LambdaScopeExtractor.extract(
								fileModel.getParseTree(),
								md.getStartOffset(),
								md.getEndOffset());
						smartCastScopes = SmartCastExtractor.extract(
								fileModel.getParseTree(),
								md.getStartOffset(),
								md.getEndOffset());
					} else {
						lambdaScopes = Collections.emptyList();
						smartCastScopes = Collections.emptyList();
					}
					functionScopeDepth = scopeChain.depth();
					lastEnclosingDecl = enclosingDecl;
					lastEnclosingType = enclosingType;
				}

				// Pop any lambda scope from a previous reference
				while (scopeChain.depth() > functionScopeDepth) {
					scopeChain.popScope();
				}

				// Push lambda scope bindings if reference is inside
				// a lambda body
				if (!lambdaScopes.isEmpty()) {
					pushLambdaScopeBindings(ref.getOffset(),
							lambdaScopes, scopeChain, exprResolver,
							lambdaResolver);
				}

				// Push smart cast bindings if reference is inside
				// a smart-cast narrowed region
				if (!smartCastScopes.isEmpty()) {
					pushSmartCastBindings(ref.getOffset(),
							smartCastScopes, scopeChain,
							importResolver);
				}

				// Verify receiver type if we have declaring type info
				if (ref.hasReceiver()) {
					if (declaringSimpleName != null) {
						if (!verifyReceiverType(ref.getReceiverName(),
								declaringSimpleName, scopeChain,
								exprResolver, subtypeChecker, ref,
								importResolver, fileModel,
								cu.getJavaProject())) {
							continue;
						}
					} else if (matchFields) {
						if (!verifyReceiverHasField(ref.getReceiverName(),
								targetName, scopeChain, exprResolver,
								subtypeChecker, ref, symbolTable,
								cu.getJavaProject(),
								hierarchyCache)) {
							continue;
						}
					}
				}

				KotlinElement element = createElementForDeclaration(
						enclosingDecl, enclosingType, cu, packageName);
				if (element == null) {
					continue;
				}

				SearchMatch match = new SearchMatch(element,
						SearchMatch.A_ACCURATE, ref.getOffset(),
						ref.getLength(), this, cu.getResource());
				requestor.acceptSearchMatch(match);
			}
		} finally {
			while (scopeChain.depth() > scopesBefore) {
				scopeChain.popScope();
			}
		}
	}

	/**
	 * Pushes lambda scope bindings ({@code it}, {@code this}) into the
	 * scope chain if the given offset falls inside a lambda body.
	 * Resolves the lambda's enclosing call receiver type and uses
	 * {@link LambdaTypeResolver} to determine what {@code it} and
	 * {@code this} should refer to inside the lambda.
	 */
	private void pushLambdaScopeBindings(int offset,
			List<LambdaScopeExtractor.LambdaScope> lambdaScopes,
			ScopeChain scopeChain,
			ExpressionTypeResolver exprResolver,
			LambdaTypeResolver lambdaResolver) {
		// Push ALL enclosing lambda scopes from outermost to innermost
		// so that named parameters from outer lambdas are visible
		// in inner lambda bodies
		List<LambdaScopeExtractor.LambdaScope> enclosing =
				LambdaScopeExtractor.findAllEnclosing(lambdaScopes,
						offset);
		for (LambdaScopeExtractor.LambdaScope lambdaScope : enclosing) {
			pushSingleLambdaScope(lambdaScope, scopeChain,
					exprResolver, lambdaResolver);
		}
	}

	private void pushSingleLambdaScope(
			LambdaScopeExtractor.LambdaScope lambdaScope,
			ScopeChain scopeChain,
			ExpressionTypeResolver exprResolver,
			LambdaTypeResolver lambdaResolver) {
		KotlinType receiverType = null;
		String receiverName = lambdaScope.getReceiverName();
		if (receiverName != null) {
			receiverType = scopeChain.resolveType(receiverName);
		}
		if ((receiverType == null || receiverType.isUnknown())
				&& lambdaScope.getCallExprCtx() != null
				&& lambdaScope.getCallNavSuffixIndex() >= 0) {
			try {
				receiverType = exprResolver.resolveReceiverUpTo(
						lambdaScope.getCallExprCtx(),
						lambdaScope.getCallNavSuffixIndex());
			} catch (Exception e) {
				org.eclipse.core.runtime.Platform.getLog(
						KotlinSearchParticipant.class).warn(
						"Lambda receiver resolution failed for '"
								+ lambdaScope.getFunctionName()
								+ "'", e);
			}
		}
		if (receiverType == null || receiverType.isUnknown()) {
			return;
		}

		LambdaTypeResolver.LambdaContext lambdaCtx =
				lambdaResolver.resolveLambdaContext(receiverType,
						lambdaScope.getFunctionName(), 0);
		if (lambdaCtx == null) {
			return;
		}

		LambdaTypeResolver.pushLambdaBindings(lambdaCtx,
				scopeChain, lambdaScope.getLambdaCtx());
	}

	/**
	 * Pushes smart cast narrowed type bindings into the scope chain if
	 * the given offset falls inside a smart-cast narrowed region (e.g.,
	 * inside the then-branch of {@code if (x is Foo)}).
	 */
	private void pushSmartCastBindings(int offset,
			List<SmartCastExtractor.SmartCastScope> smartCastScopes,
			ScopeChain scopeChain,
			ImportResolver importResolver) {
		List<SmartCastExtractor.SmartCastScope> enclosing =
				SmartCastExtractor.findAllEnclosing(smartCastScopes,
						offset);
		if (enclosing.isEmpty()) {
			return;
		}
		scopeChain.pushScope();
		for (SmartCastExtractor.SmartCastScope sc : enclosing) {
			KotlinType narrowedType;
			if (sc.isNullNarrowing()) {
				narrowedType = scopeChain
						.resolveType(sc.getVariableName())
						.withNullable(false);
			} else {
				narrowedType = KotlinType.resolve(
						sc.getNarrowedTypeName(), importResolver);
			}
			scopeChain.addBinding(sc.getVariableName(), narrowedType);
		}
	}

	/**
	 * Extracts the declaring type's simple name from a search pattern,
	 * if available. Returns {@code null} if the pattern has no declaring
	 * type constraint.
	 */
	private String extractDeclaringTypeName(SearchPattern pattern) {
		if (pattern instanceof MethodPattern mp) {
			return mp.declaringSimpleName != null
					? new String(mp.declaringSimpleName) : null;
		}
		if (pattern instanceof FieldPattern) {
			// FieldPattern.declaringSimpleName is protected with no public
			// accessor; skip receiver verification (conservative: allow all)
			return null;
		}
		return null;
	}

	/**
	 * Resolves the receiver name to a type using the scope chain and,
	 * if that fails, the expression type resolver. Returns {@code null}
	 * if the caller should bail out with {@code true} (this/super or
	 * unresolvable receiver).
	 */
	private KotlinType resolveReceiverType(String receiverName,
			ScopeChain scopeChain, ExpressionTypeResolver exprResolver,
			KotlinReferenceFinder.ReferenceMatch ref) {
		if ("this".equals(receiverName) || "super".equals(receiverName)) {
			return null; // conservative: allow
		}

		KotlinType resolvedType;
		if (receiverName != null) {
			resolvedType = scopeChain.resolveType(receiverName);
		} else {
			resolvedType = KotlinType.UNKNOWN;
		}

		if (resolvedType.isUnknown() && ref.getReceiverExprCtx() != null) {
			try {
				if (ref.getNavSuffixIndex() >= 0) {
					resolvedType = exprResolver.resolveReceiverUpTo(
							ref.getReceiverExprCtx(),
							ref.getNavSuffixIndex());
				} else {
					resolvedType = exprResolver.visit(
							ref.getReceiverExprCtx());
				}
				if (resolvedType == null) {
					resolvedType = KotlinType.UNKNOWN;
				}
			} catch (Exception e) {
				org.eclipse.core.runtime.Platform.getLog(
						KotlinSearchParticipant.class).warn(
						"Expression type resolution failed for receiver '"
								+ receiverName + "'", e);
				resolvedType = KotlinType.UNKNOWN;
			}
		}

		if (resolvedType.isUnknown()) {
			return null; // conservative: allow
		}

		// Any/Object means "could be anything" — treat as
		// conservative allow, same as UNKNOWN
		String fqn = resolvedType.getFQN();
		if ("kotlin.Any".equals(fqn)
				|| "java.lang.Object".equals(fqn)) {
			return null;
		}

		return resolvedType;
	}

	private boolean verifyReceiverType(String receiverName,
			String declaringSimpleName, ScopeChain scopeChain,
			ExpressionTypeResolver exprResolver,
			SubtypeChecker subtypeChecker,
			KotlinReferenceFinder.ReferenceMatch ref,
			ImportResolver importResolver, KotlinFileModel fileModel,
			IJavaProject javaProject) {
		// If receiver name directly matches the declaring type name
		// (e.g., Foo.method() where declaring type is Foo)
		if (receiverName != null
				&& receiverName.equalsIgnoreCase(declaringSimpleName)) {
			return true;
		}

		KotlinType resolvedType = resolveReceiverType(receiverName,
				scopeChain, exprResolver, ref);
		if (resolvedType == null) {
			return true; // conservative: allow
		}

		// Check if the resolved type matches the declaring type
		String resolvedSimple = resolvedType.getSimpleName();
		if (resolvedSimple != null
				&& resolvedSimple.equalsIgnoreCase(declaringSimpleName)) {
			return true;
		}

		// Check against Java FQN equivalence
		String resolvedFQN = resolvedType.getFQN();
		String javaFQN = resolvedType.getJavaFQN();

		// Try to match via FQN (declaring type might be a package-qualified name)
		if (resolvedFQN.endsWith("." + declaringSimpleName)
				|| javaFQN.endsWith("." + declaringSimpleName)) {
			return true;
		}

		// Check supertype hierarchy — the receiver could be a subtype
		// of the declaring type
		List<String> candidates = importResolver
				.resolveAllCandidates(declaringSimpleName);
		for (String candidateFQN : candidates) {
			KotlinType declaringType;
			int lastDot = candidateFQN.lastIndexOf('.');
			if (lastDot >= 0) {
				declaringType = new KotlinType(
						candidateFQN.substring(0, lastDot),
						candidateFQN.substring(lastDot + 1));
			} else {
				declaringType = new KotlinType(null, candidateFQN);
			}
			if (subtypeChecker.isSubtype(resolvedType, declaringType)) {
				return true;
			}
		}

		// Resolved to a different type — filter out
		return false;
	}

	/**
	 * Verifies that the receiver type has a field with the given name.
	 * Used for FieldPattern where the declaring type is not publicly
	 * accessible from the pattern — we resolve the receiver type from
	 * our AST and check if it declares the field via SymbolTable or
	 * JDT IType.
	 *
	 * @return {@code true} if the receiver type has the field (or we
	 *         can't determine — conservative), {@code false} if the
	 *         resolved type definitely doesn't have it
	 */
	private boolean verifyReceiverHasField(String receiverName,
			String fieldName, ScopeChain scopeChain,
			ExpressionTypeResolver exprResolver,
			SubtypeChecker subtypeChecker,
			KotlinReferenceFinder.ReferenceMatch ref,
			SymbolTable symbolTable, IJavaProject javaProject,
			Map<String, ITypeHierarchy> hierarchyCache) {
		KotlinType resolvedType = resolveReceiverType(receiverName,
				scopeChain, exprResolver, ref);
		if (resolvedType == null) {
			return true; // conservative: allow
		}

		// Check if the resolved type has the field in our SymbolTable
		String fqn = resolvedType.getFQN();
		if (symbolTable.lookupField(fqn, fieldName) != null) {
			return true;
		}
		// Also check with Java FQN mapping
		String javaFQN = resolvedType.getJavaFQN();
		if (!javaFQN.equals(fqn)
				&& symbolTable.lookupField(javaFQN, fieldName) != null) {
			return true;
		}

		// Fall back to JDT IType for Java types not in our SymbolTable
		if (javaProject != null) {
			try {
				IType jdtType = javaProject.findType(javaFQN);
				if (jdtType == null && !javaFQN.equals(fqn)) {
					jdtType = javaProject.findType(fqn);
				}
				if (jdtType != null) {
					if (jdtType.getField(fieldName).exists()) {
						return true;
					}
					// Check supertypes — the field might be inherited
					final IType hierType = jdtType;
					ITypeHierarchy hierarchy = hierarchyCache
							.computeIfAbsent(hierType
									.getFullyQualifiedName(),
									k -> {
								try {
									return hierType
											.newSupertypeHierarchy(null);
								} catch (JavaModelException e) {
									org.eclipse.core.runtime.Platform
											.getLog(KotlinSearchParticipant.class)
											.warn("JDT supertype hierarchy "
													+ "failed", e);
									return null;
								}
							});
					if (hierarchy != null) {
						for (IType superType : hierarchy
								.getAllSupertypes(hierType)) {
							if (superType.getField(fieldName).exists()) {
								return true;
							}
						}
					}
					// JDT type exists but doesn't have the field
					return false;
				}
			} catch (Exception e) {
				org.eclipse.core.runtime.Platform.getLog(
						KotlinSearchParticipant.class).warn(
						"JDT field lookup failed for receiver '"
								+ receiverName + "' field '"
								+ fieldName + "'", e);
				return true;
			}
		}

		// Type not found in SymbolTable or JDT — conservative allow
		return true;
	}

	private EnclosingContext findEnclosingDeclarationAndType(
			List<KotlinDeclaration> declarations, int offset) {
		return findEnclosingDeclarationAndType(declarations, offset, null);
	}

	private EnclosingContext findEnclosingDeclarationAndType(
			List<KotlinDeclaration> declarations, int offset,
			KotlinDeclaration.TypeDeclaration currentType) {
		for (KotlinDeclaration decl : declarations) {
			if (offset >= decl.getStartOffset()
					&& offset <= decl.getEndOffset()) {
				if (decl instanceof KotlinDeclaration.TypeDeclaration typeDecl) {
					EnclosingContext nested =
							findEnclosingDeclarationAndType(
									typeDecl.getMembers(), offset,
									typeDecl);
					if (nested.decl() != null) {
						return nested;
					}
					return new EnclosingContext(typeDecl, currentType);
				}
				if (decl instanceof KotlinDeclaration.MethodDeclaration methodDecl) {
					// Check nested declarations (local functions)
					if (!methodDecl.getMembers().isEmpty()) {
						EnclosingContext nested =
								findEnclosingDeclarationAndType(
										methodDecl.getMembers(),
										offset, currentType);
						if (nested.decl() != null) {
							return nested;
						}
					}
					return new EnclosingContext(decl, currentType);
				}
				if (decl instanceof KotlinDeclaration.ConstructorDeclaration
						|| decl instanceof KotlinDeclaration.PropertyDeclaration
						|| decl instanceof KotlinDeclaration.TypeAliasDeclaration) {
					return new EnclosingContext(decl, currentType);
				}
			}
		}
		return new EnclosingContext(null, currentType);
	}

	/**
	 * Creates a KotlinElement for the given declaration, with proper
	 * declaring type set for members inside a type.
	 *
	 * @param decl          the declaration to create an element for
	 * @param enclosingType the parent type, or {@code null} for top-level
	 * @param cu            the compilation unit
	 * @param packageName   the package name
	 */
	private KotlinElement createElementForDeclaration(
			KotlinDeclaration decl,
			KotlinDeclaration.TypeDeclaration enclosingType,
			KotlinCompilationUnit cu, String packageName) {
		if (decl instanceof KotlinDeclaration.MethodDeclaration methodDecl) {
			KotlinElement.KotlinMethodElement me =
					createMethodElement(methodDecl, cu);
			me.setDeclaringType(buildDeclaringTypeElement(
					enclosingType, cu, packageName));
			return me;
		} else if (decl instanceof KotlinDeclaration.TypeDeclaration typeDecl) {
			int sourceLength = typeDecl.getEndOffset()
					- typeDecl.getStartOffset() + 1;
			return new KotlinElement.KotlinTypeElement(
					typeDecl.getName(), cu, packageName,
					typeDecl.getEnclosingTypeName(),
					typeDecl.getStartOffset(), sourceLength,
					typeDecl.getKind(),
					typeDecl.getSupertypes().toArray(new String[0]),
					typeDecl.getModifiers());
		} else if (decl instanceof KotlinDeclaration.ConstructorDeclaration ctorDecl) {
			KotlinElement.KotlinMethodElement me =
					createConstructorElement(ctorDecl, cu);
			me.setDeclaringType(buildDeclaringTypeElement(
					enclosingType, cu, packageName));
			return me;
		} else if (decl instanceof KotlinDeclaration.PropertyDeclaration propDecl) {
			int sourceLength = propDecl.getEndOffset()
					- propDecl.getStartOffset() + 1;
			KotlinElement.KotlinFieldElement fe =
					new KotlinElement.KotlinFieldElement(
							propDecl.getName(), cu,
							propDecl.getStartOffset(), sourceLength,
							KotlinElement.toTypeSignature(
									propDecl.getTypeName()),
							false, propDecl.getModifiers());
			fe.setDeclaringType(buildDeclaringTypeElement(
					enclosingType, cu, packageName));
			return fe;
		} else if (decl instanceof KotlinDeclaration.TypeAliasDeclaration aliasDecl) {
			int sourceLength = aliasDecl.getEndOffset()
					- aliasDecl.getStartOffset() + 1;
			return new KotlinElement.KotlinTypeElement(
					aliasDecl.getName(), cu, packageName,
					aliasDecl.getEnclosingTypeName(),
					aliasDecl.getStartOffset(), sourceLength,
					null, null, aliasDecl.getModifiers());
		}
		return null;
	}

	private KotlinElement.KotlinTypeElement buildDeclaringTypeElement(
			KotlinDeclaration.TypeDeclaration enclosingType,
			KotlinCompilationUnit cu, String packageName) {
		if (enclosingType == null) {
			return null;
		}
		int tLen = enclosingType.getEndOffset()
				- enclosingType.getStartOffset() + 1;
		return new KotlinElement.KotlinTypeElement(
				enclosingType.getName(), cu, packageName,
				enclosingType.getEnclosingTypeName(),
				enclosingType.getStartOffset(), tLen,
				enclosingType.getKind(),
				enclosingType.getSupertypes().toArray(new String[0]),
				enclosingType.getModifiers());
	}

	private KotlinElement.KotlinMethodElement createMethodElement(
			KotlinDeclaration.MethodDeclaration methodDecl,
			KotlinCompilationUnit cu) {
		return KotlinElement.buildMethodElement(methodDecl, cu);
	}

	private KotlinElement.KotlinMethodElement createConstructorElement(
			KotlinDeclaration.ConstructorDeclaration ctorDecl,
			KotlinCompilationUnit cu) {
		return KotlinElement.buildConstructorElement(ctorDecl, cu);
	}

	private String extractTargetName(SearchPattern pattern) {
		if (pattern instanceof TypeReferencePattern trp) {
			char[] key = trp.getIndexKey();
			return key != null ? new String(key) : null;
		} else if (pattern instanceof MethodPattern mp) {
			return mp.selector != null
					? new String(mp.selector) : null;
		} else if (pattern instanceof FieldPattern fp) {
			char[] key = fp.getIndexKey();
			return key != null ? new String(key) : null;
		}
		return null;
	}


	@Override
	public IPath[] selectIndexes(SearchPattern query, IJavaSearchScope scope) {
		IndexManager indexManager = JavaModelManager.getIndexManager();
		List<IPath> indexes = new ArrayList<>();
		IPath[] enclosingPaths = scope.enclosingProjectsAndJars();
		for (IPath containerPath : enclosingPaths) {
			IndexLocation indexLocation = indexManager.computeIndexLocation(containerPath);
			if (indexLocation != null && indexLocation.getIndexFile() != null) {
				indexes.add(IPath.fromOSString(
						indexLocation.getIndexFile().getAbsolutePath()));
			}
		}
		return indexes.toArray(new IPath[0]);
	}

	// ---- Outgoing call hierarchy (locateCallees) ----

	@Override
	public SearchMatch[] locateCallees(IMember caller,
			SearchDocument document, IProgressMonitor monitor)
			throws CoreException {
		String path = document.getPath();

		KotlinFileModel fileModel = modelManager.getFileModel(
				path, document.getCharContents());
		if (fileModel == null) {
			return new SearchMatch[0];
		}

		KotlinDeclaration callerDecl = findCallerDeclaration(
				fileModel.getDeclarations(), caller);
		if (callerDecl == null) {
			return new SearchMatch[0];
		}

		KotlinCalleeFinder finder = new KotlinCalleeFinder(
				callerDecl.getStartOffset(), callerDecl.getEndOffset());
		List<KotlinCalleeFinder.CalleeMatch> callees = finder
				.find(fileModel.getParseTree());

		if (callees.isEmpty()) {
			return new SearchMatch[0];
		}

		IResource resource = caller.getResource();
		if (resource == null) {
			resource = ResourcesPlugin.getWorkspace().getRoot()
					.findMember(IPath.fromPortableString(path));
		}
		if (resource == null) {
			org.eclipse.core.runtime.Platform.getLog(
					KotlinSearchParticipant.class).warn(
					"Could not resolve resource for callee matches: "
							+ path);
			return new SearchMatch[0];
		}

		List<SearchMatch> matches = new ArrayList<>();
		for (KotlinCalleeFinder.CalleeMatch callee : callees) {
			int elementType =
					callee.getKind() == KotlinCalleeFinder.CalleeMatch.Kind.CONSTRUCTOR_CALL
					? IJavaElement.TYPE : IJavaElement.METHOD;
			KotlinElement.CalleeStub stub = new KotlinElement.CalleeStub(
					callee.getName(), elementType);
			matches.add(new SearchMatch(stub, SearchMatch.A_ACCURATE,
					callee.getOffset(), callee.getLength(),
					this, resource));
		}

		return matches.toArray(new SearchMatch[0]);
	}

	private KotlinDeclaration findCallerDeclaration(
			List<KotlinDeclaration> declarations, IMember caller) {
		String name = caller.getElementName();
		if (name == null) {
			org.eclipse.core.runtime.Platform.getLog(
					KotlinSearchParticipant.class).warn(
					"Caller element has null name: " + caller);
			return null;
		}
		int targetOffset = -1;
		try {
			ISourceRange range = caller.getSourceRange();
			if (range != null) {
				targetOffset = range.getOffset();
			}
		} catch (JavaModelException e) {
			org.eclipse.core.runtime.Platform.getLog(
					KotlinSearchParticipant.class).warn(
					"Failed to get source range for callee lookup",
					e);
		}
		return findDeclarationByName(declarations, name, targetOffset);
	}

	private KotlinDeclaration findDeclarationByName(
			List<KotlinDeclaration> declarations, String name,
			int preferredOffset) {
		KotlinDeclaration firstMatch = null;
		for (KotlinDeclaration decl : declarations) {
			if (name.equals(decl.getName())) {
				if (preferredOffset >= 0
						&& decl.getStartOffset() == preferredOffset) {
					return decl;
				}
				if (firstMatch == null) {
					firstMatch = decl;
				}
			}
			if (decl instanceof KotlinDeclaration.TypeDeclaration typeDecl) {
				KotlinDeclaration nested = findDeclarationByName(
						typeDecl.getMembers(), name, preferredOffset);
				if (nested != null) {
					return nested;
				}
			}
		}
		return firstMatch;
	}

	static String derivePackageName(String path) {
		if (path == null) {
			return null;
		}
		int srcIdx = path.indexOf("/src/");
		if (srcIdx < 0) {
			return null;
		}
		String afterSrc = path.substring(srcIdx + 5);
		int lastSlash = afterSrc.lastIndexOf('/');
		if (lastSlash <= 0) {
			return null;
		}
		return afterSrc.substring(0, lastSlash).replace('/', '.');
	}

	static String deriveClassName(String path) {
		if (path == null) {
			return null;
		}
		int lastSlash = path.lastIndexOf('/');
		String fileName = (lastSlash >= 0) ? path.substring(lastSlash + 1) : path;
		int dot = fileName.lastIndexOf('.');
		if (dot <= 0) {
			return null;
		}
		return fileName.substring(0, dot);
	}
}
