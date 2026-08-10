#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT_DIR="$ROOT_DIR/out/production/JavaNetworkFS"
LIB_DIR="$ROOT_DIR/lib"
JAVAFX_DIR="$LIB_DIR/javafx"
MAIN_CLASS="netfs.ui.NetFSApp"

if [[ ! -d "$OUT_DIR" ]]; then
    echo "No compiled output found. Run: ./build.sh"
    exit 1
fi

# NOTE: the actual FUSE mount runs in a separate, JavaFX-free child JVM process
# launched by MountTab (see netfs.net.MountWorker), not in this process. jnr-fuse's
# native FUSE callbacks reliably crash the JVM with a SIGSEGV when JavaFX is loaded
# in the same process (see jnr-fuse GitHub issue #162). This UI process itself never
# touches jnr-fuse/libfuse, so it needs no special native-compat flags.
java \
  --module-path "$JAVAFX_DIR" \
  --add-modules javafx.controls \
  -cp "$OUT_DIR:$LIB_DIR/*" \
  "$MAIN_CLASS"
