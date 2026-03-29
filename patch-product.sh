#!/bin/bash
# Patches the installed karellen-jdtls-kotlin product with locally-built JARs
# from the three fork repos and clears the OSGi cache for a clean restart.
#
# Usage: ./patch-product.sh

set -euo pipefail

PRODUCT_DIR="$HOME/.local/lib/karellen-jdtls-kotlin"
PLUGINS_DIR="$PRODUCT_DIR/plugins"
JDT_LS_DIR="$HOME/Documents/src/arcivanov/eclipse.jdt.ls"
THIS_DIR="$(cd "$(dirname "$0")" && pwd)"

if [ ! -d "$PLUGINS_DIR" ]; then
    echo "ERROR: Product not found at $PRODUCT_DIR" >&2
    exit 1
fi

patch_jar() {
    local name="$1"
    local src_pattern="$2"
    local src_dir="$3"

    # Find target JAR in product
    local target
    target=$(ls "$PLUGINS_DIR"/${name}_*.jar 2>/dev/null | head -1)
    if [ -z "$target" ]; then
        echo "SKIP: No ${name}_*.jar in product" >&2
        return
    fi

    # Find source JAR
    local src
    src=$(find "$src_dir" -name "$src_pattern" \
        -not -name "*sources*" -not -name "*antadapter*" \
        2>/dev/null | sort -V | tail -1)
    if [ -z "$src" ]; then
        echo "SKIP: No $src_pattern found under $src_dir" >&2
        return
    fi

    # Remove all matching target JARs and copy source
    rm -f "$PLUGINS_DIR"/${name}_*.jar
    cp "$src" "$PLUGINS_DIR/$(basename "$target")"
    echo "Patched $name"
}

patch_jar "org.eclipse.jdt.core" \
    "org.eclipse.jdt.core-*-SNAPSHOT.jar" \
    "$HOME/.m2/repository/org/eclipse/jdt/org.eclipse.jdt.core"

patch_jar "org.eclipse.jdt.core.manipulation" \
    "org.eclipse.jdt.core.manipulation-*-SNAPSHOT.jar" \
    "$HOME/.m2/repository/org/eclipse/jdt/org.eclipse.jdt.core.manipulation"

patch_jar "org.eclipse.jdt.ls.core" \
    "org.eclipse.jdt.ls.core-*-SNAPSHOT.jar" \
    "$JDT_LS_DIR/org.eclipse.jdt.ls.core/target"

patch_jar "co.karellen.jdtls.kotlin" \
    "co.karellen.jdtls.kotlin-*-SNAPSHOT.jar" \
    "$THIS_DIR/co.karellen.jdtls.kotlin/target"

# Clear OSGi cache
rm -rf "$PRODUCT_DIR/configuration/org.eclipse.osgi" 2>/dev/null
echo "Cleared OSGi cache"
