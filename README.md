# karellen-jdtls-kotlin

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

The participant implements:
- **`indexDocument()`** — parses `.kt` files and emits index entries (`TYPE_DECL`, `METHOD_REF`, `SUPER_REF`, etc.)
- **`locateMatches()`** — resolves index hits back to `.kt` source positions
- **`selectIndexes()`** — returns index file paths for this participant's entries

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
| `co.karellen.jdtls.kotlin` | OSGi plugin bundle with `KotlinSearchParticipant` |
| `co.karellen.jdtls.kotlin.tests` | Integration tests (15 JUnit 5 tests) |
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

- **Test results**: 15 integration tests covering extension point discovery, indexing pipeline, search pipeline, and lifecycle
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

The plugin is functional with a stub Kotlin parser that derives type declarations from file paths. The full Kotlin indexer (kotlinx/ast + lightweight declaration resolver) is the next milestone.

## License

Apache License 2.0. Copyright 2026 Karellen, Inc.
