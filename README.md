# karellen-jdtls-kotlin

[![CI](https://github.com/karellen/karellen-jdtls-kotlin/actions/workflows/ci.yml/badge.svg)](https://github.com/karellen/karellen-jdtls-kotlin/actions/workflows/ci.yml)
[![Coverage Status](https://coveralls.io/repos/github/karellen/karellen-jdtls-kotlin/badge.svg?branch=master)](https://coveralls.io/github/karellen/karellen-jdtls-kotlin?branch=master)

Kotlin language support for [Eclipse JDT Language Server](https://github.com/eclipse-jdtls/eclipse.jdt.ls) (jdtls) via JDT Core's `SearchParticipant` extension point.

This project provides cross-language code intelligence between Java and Kotlin source files: type hierarchy, call hierarchy, find references, hover, and go-to-definition all work bidirectionally across the Java/Kotlin boundary.

## How It Works

The plugin registers a `KotlinSearchParticipant` for `.kt`/`.kts` files via a new `org.eclipse.jdt.core.searchParticipant` extension point in JDT Core. When JDT Core's `IndexManager` discovers Kotlin files in source folders, it routes them to the participant for indexing. The participant parses Kotlin source, emits index entries (type declarations, method references, supertype references, etc.), and JDT's search infrastructure automatically includes Kotlin results in all cross-language queries.

No AspectJ weaving, no light classes, no IntelliJ platform dependency.

## Architecture and Integration Points

The system spans three repositories with changes at two integration boundaries:

### Write Side — JDT Core Extension Point (PR 1)

JDT Core's indexing pipeline is extended to discover `javaDerivedSource` files (`.kt`, `.kts`) alongside Java files and route them to contributed `SearchParticipant` implementations:

- **Extension point**: `org.eclipse.jdt.core.searchParticipant` — maps file extensions to `SearchParticipant` classes
- **`SearchParticipantRegistry`**: lazily loads contributed participants, maps each file extension to a singleton participant instance
- **File discovery**: `IndexAllProject`, `AddFolderToIndex`, `DeltaProcessor` — add `isJavaDerivedFileName()` checks alongside existing `isJavaLikeFileName()` checks
- **Index routing**: `IndexManager.addDerivedSource()` — routes discovered files to the registered participant via `scheduleDocumentIndexing()`
- **Search API**: `SearchEngine.getSearchParticipants()` — returns `[default Java participant, ...contributed participants]`

The `javaDerivedSource` content type (base-type `org.eclipse.core.runtime.text`, NOT a subtype of `javaSource`) ensures the Java builder ignores `.kt` files while the indexer processes them.

### Read Side — jdtls Search Dispatch (PR 2)

Four jdtls handler call sites are updated from:
```java
new SearchParticipant[] { SearchEngine.getDefaultSearchParticipant() }
```
to:
```java
SearchEngine.getSearchParticipants()
```

This ensures Kotlin index results are included in all search operations:

| Handler | LSP Method |
|---------|-----------|
| `ReferencesHandler` | `textDocument/references` |
| `HoverInfoProvider` | `textDocument/hover` |
| `CodeLensHandler` | `textDocument/codeLens` |
| `ImplementationCollector` | `textDocument/implementation` |

### Kotlin Plugin — `co.karellen.jdtls.kotlin`

The OSGi bundle registers with JDT Core via `plugin.xml`:

```xml
<!-- Register .kt/.kts under javaDerivedSource content type -->
<extension point="org.eclipse.core.contenttype.contentTypes">
   <file-association content-type="org.eclipse.jdt.core.javaDerivedSource"
         file-extensions="kt,kts"/>
</extension>

<!-- Register KotlinSearchParticipant for .kt/.kts files -->
<extension point="org.eclipse.jdt.core.searchParticipant">
   <searchParticipant
         id="co.karellen.jdtls.kotlin.searchParticipant"
         class="co.karellen.jdtls.kotlin.search.KotlinSearchParticipant"
         fileExtensions="kt,kts"/>
</extension>
```

The participant uses an ANTLR4-based parser (official Kotlin grammar from `kotlin-spec`, supporting v1.4 through v1.9) and implements:
- **`indexDocument()`** — parses `.kt` files via ANTLR4, extracts declarations and expression references, emits index entries (`TYPE_DECL`, `SUPER_REF`, `METHOD_DECL`, `FIELD_DECL`, `CONSTRUCTOR_DECL`, `REF`, `METHOD_REF`, `CONSTRUCTOR_REF`)
- **`locateMatches()`** — pattern-aware match reporting with `IJavaElement` resolution: dispatches on `TypeDeclarationPattern`, `SuperTypeReferencePattern`, `TypeReferencePattern`, `MethodPattern`, `FieldPattern`; returns `KotlinElement` instances backed by `KotlinCompilationUnit` with proper parent chain; includes receiver type verification via scope chain, import resolution, and subtype checking
- **`selectIndexes()`** — returns index file paths for this participant's entries

The parser pipeline includes a symbol table, scope-walking type resolver, overload resolver, and lambda type propagation for ~80-85% call site coverage without full Kotlin type inference.

### Data Flow

```
.kt file created/modified in workspace
  → JDT Core DeltaProcessor detects change
    → isJavaDerivedFileName() matches .kt
      → SearchParticipantRegistry looks up participant for "kt"
        → IndexManager.addDerivedSource() schedules indexing
          → KotlinSearchParticipant.indexDocument() parses and emits entries

LSP client requests textDocument/references
  → jdtls ReferencesHandler calls SearchEngine.search()
    → SearchEngine.getSearchParticipants() returns [Java, Kotlin]
      → Index queried for both participants
        → KotlinSearchParticipant.locateMatches() maps hits to .kt source
          → Results merged and returned to client
```

## Project Structure

| Module | Description |
|--------|-------------|
| `co.karellen.jdtls.kotlin` | OSGi plugin bundle with ANTLR4 parser and `KotlinSearchParticipant` |
| `co.karellen.jdtls.kotlin.tests` | Integration tests (200 JUnit 5 tests) |
| `co.karellen.jdtls.kotlin.coverage` | JaCoCo code coverage aggregation |
| `co.karellen.jdtls.kotlin.product` | Self-contained product distribution with native launcher |
| `co.karellen.jdtls.kotlin.target` | Target platform definition |

## Prerequisites

This project depends on two upstream forks that must be built and installed locally:

1. **JDT Core fork** — adds `org.eclipse.jdt.core.searchParticipant` extension point and wires the indexing pipeline for `javaDerivedSource` files
   - Repository: `eclipse.jdt.core`, branch `search-participant-extension-point`

2. **jdtls fork** — updates search call sites to use `SearchEngine.getSearchParticipants()` instead of hardcoding the default Java participant
   - Repository: `eclipse.jdt.ls`, branch `feature/search-participant-extension-point`
   - PR: [#3732](https://github.com/eclipse-jdtls/eclipse.jdt.ls/pull/3732)

## Building

Requires JDK 21+ and Maven 3.9+.

```bash
# Step 1: Build and install JDT Core fork
cd path/to/eclipse.jdt.core
mvn clean install -DskipTests -Dtycho.baseline.skip=true

# Step 2: Build and install jdtls fork
cd path/to/eclipse.jdt.ls
mvn clean install -DskipTests

# Step 3: Build karellen-jdtls-kotlin (plugin + tests + product)
cd path/to/karellen-jdtls-kotlin
mvn clean verify
```

The root `pom.xml` sets `tycho.localArtifacts=consider`, which makes Tycho prefer the locally-installed fork bundles (higher SNAPSHOT versions) over the P2 repository versions.

## Build Output

- **Test results**: 200 integration tests, all passing — extension point discovery, indexing pipeline, search pipeline, lifecycle, cross-language type discovery (v1.4-v1.9), hover, implementation search, find references, code lens, call hierarchy (incoming/outgoing), receiver type verification, local variable resolution, field references, type aliases, document symbols, code select
- **Distribution archive**: `co.karellen.jdtls.kotlin.product/distro/karellen-jdtls-kotlin-<timestamp>.tar.gz` (~48MB)
- **Materialized products**: platform-specific directories under `co.karellen.jdtls.kotlin.product/target/products/`

The distribution contains ~128 OSGi bundles including jdtls core (with PR #2 changes), JDT Core (with PR #1 extension point), and the Kotlin plugin, plus platform-specific OSGi configuration directories.

## Running

### From the Distribution Archive

```bash
# Extract the distribution
tar xzf karellen-jdtls-kotlin-<timestamp>.tar.gz -C /path/to/install
cd /path/to/install

# Run with platform-appropriate config
./jdtls \
  -configuration ./config_linux \
  -data /path/to/workspace \
  --add-modules=ALL-SYSTEM \
  --add-opens java.base/java.util=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED
```

Platform config directories: `config_linux`, `config_linux_arm`, `config_mac`, `config_mac_arm`, `config_win`.

### From the Build Tree

```bash
cd co.karellen.jdtls.kotlin.product/target/products/karellen-jdtls-kotlin.product/linux/gtk/x86_64/
./jdtls \
  -configuration ./configuration \
  -data /path/to/workspace \
  --add-modules=ALL-SYSTEM \
  --add-opens java.base/java.util=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED
```

### End-to-End with an LSP Client

The launcher speaks the Language Server Protocol over stdio. Point any LSP client (VS Code, Neovim, Emacs, etc.) at the `jdtls` binary:

1. **Extract** the distribution archive to a permanent location
2. **Configure** your LSP client to launch `jdtls` as the Java language server, passing:
   - `-configuration <path>/config_<platform>` — platform-specific OSGi config
   - `-data <path>/workspace` — jdtls workspace data directory (per-project)
   - `--add-modules=ALL-SYSTEM` and `--add-opens` flags for JDK module access
3. **Open a project** containing both `.java` and `.kt` files in source folders
4. **Verify** cross-language features:
   - Find references on a Java type should include usages from `.kt` files
   - Type hierarchy should show Kotlin classes extending Java interfaces
   - Hover on a Kotlin type reference should show type information

The `jdtls` binary reads `jdtls.ini` for default VM arguments. The product uses `org.eclipse.jdt.ls.core.id1` as its OSGi application, which is the standard jdtls language server entry point.

## Current Status

The plugin has a working ANTLR4-based Kotlin parser with a 7-phase pipeline (declaration extraction, symbol table, scope-walking type resolution, overload resolution, lambda propagation, index emission, IJavaElement resolution). All cross-language search features work bidirectionally across the Java/Kotlin boundary: type hierarchy, call hierarchy (incoming and outgoing), find references, hover, go-to-definition, implementation search, code lens, and document symbols. 200 integration tests pass with 84% instruction / 63% branch coverage.

Key capabilities:
- **Receiver type verification** filters false positives by resolving receiver expressions to types via scope chain (file, class, function, and local variable scopes), import resolution, and subtype hierarchy checking with JDT delegation for Java types
- **`KotlinElement` hierarchy** implements `IType`, `IMethod`, `IField` with full type metadata, parameter types/names, return types, and JDT modifier flag mapping
- **`KotlinCompilationUnit`** provides a populated model (`getTypes()`, `getChildren()`, `codeSelect()`, `getElementAt()`) for document symbol and navigation features
- **Call hierarchy** via `locateCallees()` for outgoing calls and element-based `MethodPattern` search for incoming calls

## License

Apache License 2.0. Copyright 2026 Karellen, Inc.
