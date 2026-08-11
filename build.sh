#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
SRC_DIR="$ROOT_DIR/src"
OUT_DIR="$ROOT_DIR/out/production/JavaNetworkFS"
LIB_DIR="$ROOT_DIR/lib"
JAVAFX_DIR="$LIB_DIR/javafx"

if ! ls "$JAVAFX_DIR"/javafx-controls-*-linux.jar >/dev/null 2>&1; then
    echo "JavaFX jars not found in $JAVAFX_DIR"
    echo "Run: ./setup-javafx.sh"
    exit 1
fi

mkdir -p "$OUT_DIR"

mapfile -t sources < <(find "$SRC_DIR" -type f -name '*.java')
if [[ ${#sources[@]} -eq 0 ]]; then
    echo "No Java source files found in $SRC_DIR"
    exit 1
fi

javac \
  --enable-preview \
  --release 21 \
  --module-path "$JAVAFX_DIR" \
  --add-modules javafx.controls \
  -cp "$LIB_DIR/*" \
  -d "$OUT_DIR" \
  "${sources[@]}"

# Copy non-Java resources (e.g. CSS) alongside compiled classes, preserving package layout.
while IFS= read -r -d '' resource; do
    rel="${resource#"$SRC_DIR"/}"
    mkdir -p "$OUT_DIR/$(dirname "$rel")"
    cp -f "$resource" "$OUT_DIR/$rel"
done < <(find "$SRC_DIR" -type f -name '*.css' -print0)

if [[ -d "$ROOT_DIR/assets" ]]; then
    mkdir -p "$OUT_DIR/assets"
    cp -R "$ROOT_DIR/assets/." "$OUT_DIR/assets/"
fi

echo "Build successful. Classes in: $OUT_DIR"
