# CLAUDE.md — karellen-jdtls-kotlin

## Project Overview

Kotlin search participant plugin for Eclipse JDT Language Server (jdtls). Registers
a `KotlinSearchParticipant` for `.kt`/`.kts` files via JDT Core's
`org.eclipse.jdt.core.searchParticipant` extension point. Enables cross-language
type hierarchy, call hierarchy, find references, hover, and go-to-definition
between Java and Kotlin source files.

## Repository Layout

```
co.karellen.jdtls.kotlin/          # OSGi plugin bundle (eclipse-plugin packaging)
  src/co/karellen/jdtls/kotlin/
    search/
      KotlinSearchParticipant.java  # SearchParticipant implementation
      KotlinSearchDocument.java     # SearchDocument stub
  META-INF/MANIFEST.MF             # Bundle manifest (singleton, JavaSE-21)
  plugin.xml                       # Extension point registrations
  build.properties

co.karellen.jdtls.kotlin.tests/    # Integration tests (eclipse-test-plugin)
  src/co/karellen/jdtls/kotlin/tests/
    KotlinSearchParticipantIntegrationTest.java  # JUnit 5 integration tests
    TestHelpers.java                             # Project/file creation utilities

co.karellen.jdtls.kotlin.product/  # Product distribution (eclipse-repository)
  karellen-jdtls-kotlin.product    # Product definition (native launcher, all bundles)
  publish-assembly.xml             # tar.gz assembly descriptor
  pom.xml                          # tycho-p2-director + maven-assembly

co.karellen.jdtls.kotlin.target/   # Target platform definition
  co.karellen.jdtls.kotlin.tp.target

pom.xml                            # Root reactor POM (Tycho 5.0.2)
```

## Build System

Eclipse Tycho 5.0.2. All modules use Tycho packaging types (`eclipse-plugin`,
`eclipse-test-plugin`, `eclipse-repository`, `eclipse-target-definition`).

### Full Build Chain

The project depends on two upstream forks that must be built first:

```bash
# 1. JDT Core fork (search participant extension point)
cd ~/Documents/src/arcivanov/eclipse.jdt.core
mvn clean install -DskipTests -Dtycho.baseline.skip=true

# 2. jdtls fork (SearchEngine.getSearchParticipants() usage)
cd ~/Documents/src/arcivanov/eclipse.jdt.ls
mvn clean install -DskipTests

# 3. This project
cd ~/Documents/src/karellen/karellen-jdtls-kotlin
mvn clean verify
```

### Key Build Properties

- `tycho.localArtifacts=consider` in root pom.xml — prefers locally-installed fork
  bundles (higher SNAPSHOT versions) over P2 repo versions
- Target platform JRE: JavaSE-25 (required by transitive dependencies)
- Plugin MANIFEST.MF: JavaSE-21 minimum
- All target platform locations use `includeSource="false"` (must be uniform)

### Selective Test Execution

```bash
# Install everything first, then run specific test module
mvn clean install -DskipTests
mvn verify -pl co.karellen.jdtls.kotlin.tests
```

## Fork Dependencies

| Component | Path | Branch | Version |
|-----------|------|--------|---------|
| JDT Core | `~/Documents/src/arcivanov/eclipse.jdt.core` | `search-participant-extension-point` | `4.40.0-SNAPSHOT` |
| jdtls | `~/Documents/src/arcivanov/eclipse.jdt.ls` | `feature/search-participant-extension-point` | `1.58.0-SNAPSHOT` |

## Design Document

Full architecture, rationale, and implementation details:
`~/Documents/src/karellen/karellen-lsp-mcp/kotlin-jdt-analysis.md`

## Known Build Issues

- **Eclipse 4.39 I-builds URL removed**: Use release URL
  `https://download.eclipse.org/eclipse/updates/4.39/` (not `4.39-I-builds`)
- **`org.eclipse.jdt.core.javac` excluded**: Its CI repo has a broken source jar (404).
  Excluded from both target platform and product. Not needed (experimental javac support).
- **jdtls fork product module fails**: Same javac source jar issue. Only core modules
  need to install successfully (Core, Filesystem, Logback.appender).
- **`-rf` without `-pl`**: Don't use. Target platform artifact may not resolve. Use
  two-phase build (install first, then verify specific module).

## Testing Notes

- Tests run in OSGi container via tycho-surefire (JUnit 5)
- `TestHelpers.createJavaProject()` creates projects with Java nature + source folders
  only (no JRE container needed)
- `TestHelpers.waitUntilIndexesReady()` uses `IndexManager.waitForIndex()` +
  `searchAllTypeNames()` with `WAIT_UNTIL_READY_TO_SEARCH`
- Indexing is verified through the search pipeline (no test counters on production code)
- `-Dtest=` supports Ant-style wildcards, NOT comma-separated lists
- Individual test methods (`#methodName`) silently ignored by tycho-surefire

## Current State

ANTLR4-based Kotlin parser with 7-phase pipeline: declaration extraction, symbol
table, scope-based type resolution, overload resolution, lambda type propagation,
smart cast narrowing, index emission, and IJavaElement resolution. All cross-language
LSP features working: find references, go-to-definition, hover, call hierarchy, type
hierarchy, document symbols, and code lens. Bidirectional Java↔Kotlin property/getter
interop: searching for Java `getName()` finds Kotlin `obj.name` access and vice
versa. `codeSelect()` resolves type references, import targets, and expression
receivers to Java `IType` elements. File-facade classes (`FileNameKt`) indexed as
TYPE_DECL; JVM-generated property accessors (`get`/`set`/`is`) indexed as METHOD_DECL.
Import statements indexed as REF entries. `OrPattern` (REFERENCES + DECLARATIONS
combined) unwrapped and dispatched to sub-patterns for correct `includeDeclaration`
handling. 299 integration tests, 87% instruction / 68% branch coverage. Product
module produces a self-contained distribution (~48MB tar.gz) with native Eclipse
launcher (`jdtls`), all jdtls bundles, and the Kotlin plugin.
