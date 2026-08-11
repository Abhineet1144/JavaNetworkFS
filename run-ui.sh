#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="$ROOT_DIR/out/production/JavaNetworkFS"
LIB_DIR="$ROOT_DIR/lib"
JAVAFX_DIR="$LIB_DIR/javafx"
MAIN_CLASS="netfs.ui.NetFSLauncher"
CACHE_BASE="${XDG_CACHE_HOME:-$HOME/.cache}"
OPENJFX_CACHE="$CACHE_BASE/netfs/openjfx"
LOG_FILE="$CACHE_BASE/netfs/netfs-ui.log"

if [[ ! -d "$OUT_DIR" ]]; then
    echo "No compiled output found. Run: ./build.sh"
    exit 1
fi

mkdir -p "$OPENJFX_CACHE" "$(dirname "$LOG_FILE")"

# NOTE: the actual FUSE mount runs in a separate, JavaFX-free child JVM process
# launched by MountTab (see netfs.net.MountWorker), not in this process. jnr-fuse's
# native FUSE callbacks reliably crash the JVM with a SIGSEGV when JavaFX is loaded
# in the same process (see jnr-fuse GitHub issue #162). This UI process itself never
# touches jnr-fuse/libfuse, so it needs no special native-compat flags.
JAVA_CMD=(
  java
  --enable-preview
  --enable-native-access=javafx.graphics,ALL-UNNAMED
  -XX:ErrorFile="$CACHE_BASE/netfs/hs_err_pid%p.log"
  -Djavafx.cachedir="$OPENJFX_CACHE"
  -Xms32m
  -Xmx192m
  -XX:+UseG1GC
  -XX:+UseStringDeduplication
  -XX:ReservedCodeCacheSize=64m
  --module-path "$JAVAFX_DIR"
  --add-modules javafx.controls
  -cp "$OUT_DIR:$LIB_DIR/*"
  "$MAIN_CLASS"
)

if [[ -t 2 ]]; then
  exec "${JAVA_CMD[@]}"
else
  exec "${JAVA_CMD[@]}" >> "$LOG_FILE" 2>&1
fi
