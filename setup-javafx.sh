#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
LIB_DIR="$ROOT_DIR/lib/javafx"
M2_ROOT="${HOME}/.m2/repository/org/openjfx"
VERSION="${1:-21.0.6}"

mkdir -p "$LIB_DIR"

copy_module_jar() {
    local module="$1"
    local candidate="$M2_ROOT/javafx-${module}/${VERSION}/javafx-${module}-${VERSION}-linux.jar"

    if [[ -f "$candidate" ]]; then
        cp -f "$candidate" "$LIB_DIR/"
        return
    fi

    local fallback
    fallback=$(find "$M2_ROOT/javafx-${module}" -type f -name "javafx-${module}-*-linux.jar" 2>/dev/null | sort | tail -n 1 || true)
    if [[ -n "$fallback" && -f "$fallback" ]]; then
        cp -f "$fallback" "$LIB_DIR/"
        return
    fi

    echo "Missing javafx-${module} Linux jar in local Maven cache." >&2
    echo "Expected around: $M2_ROOT/javafx-${module}/<version>/javafx-${module}-<version>-linux.jar" >&2
    exit 1
}

copy_module_jar base
copy_module_jar graphics
copy_module_jar controls

echo "JavaFX jars copied into: $LIB_DIR"
ls -1 "$LIB_DIR" | sed 's/^/ - /'
