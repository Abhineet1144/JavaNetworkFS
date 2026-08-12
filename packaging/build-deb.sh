#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
PACKAGE_NAME="netfs"
APP_NAME="NetFS"
VERSION="${VERSION:-0.0.1}"
ARCH="${ARCH:-$(dpkg --print-architecture 2>/dev/null || echo amd64)}"
MAINTAINER="${MAINTAINER:-Tejas, Abhineet}"
BUILD_DIR="$ROOT_DIR/build/deb"
PKG_ROOT="$BUILD_DIR/${PACKAGE_NAME}_${VERSION}_${ARCH}"
OUT_DIR="$ROOT_DIR/out/production/JavaNetworkFS"
DEB_FILE="$BUILD_DIR/${PACKAGE_NAME}_${VERSION}_${ARCH}.deb"
TMP_DIR=""

cleanup() {
    if [[ -n "$TMP_DIR" ]]; then
        rm -rf "$TMP_DIR"
    fi
}
trap cleanup EXIT

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        echo "Missing required command: $1" >&2
        exit 1
    fi
}

require_file() {
    if [[ ! -f "$1" ]]; then
        echo "Missing required file: $1" >&2
        exit 1
    fi
}

require_command dpkg-deb
require_command find
require_command install
require_command java

require_file "$ROOT_DIR/assets/icons/netfs-24.png"
require_file "$ROOT_DIR/assets/icons/netfs-256.png"
require_file "$ROOT_DIR/assets/icons/netfs.svg"

"$ROOT_DIR/build.sh"

rm -rf "$PKG_ROOT"
if [[ -d "$BUILD_DIR" ]]; then
    find "$BUILD_DIR" -maxdepth 1 -type f -name "${PACKAGE_NAME}_*.deb" -delete
fi

install -d "$PKG_ROOT/DEBIAN"
install -d "$PKG_ROOT/usr/bin"
install -d "$PKG_ROOT/usr/share/netfs/app"
install -d "$PKG_ROOT/usr/share/netfs/lib"
install -d "$PKG_ROOT/usr/share/applications"
install -d "$PKG_ROOT/usr/share/icons/hicolor/24x24/apps"
install -d "$PKG_ROOT/usr/share/icons/hicolor/48x48/apps"
install -d "$PKG_ROOT/usr/share/icons/hicolor/256x256/apps"
install -d "$PKG_ROOT/usr/share/icons/hicolor/scalable/apps"

cp -a "$OUT_DIR/." "$PKG_ROOT/usr/share/netfs/app/"
cp -a "$ROOT_DIR/lib/." "$PKG_ROOT/usr/share/netfs/lib/"

install -m 0644 "$ROOT_DIR/assets/icons/netfs-24.png" \
    "$PKG_ROOT/usr/share/icons/hicolor/24x24/apps/netfs.png"
install -m 0644 "$ROOT_DIR/assets/icons/netfs-256.png" \
    "$PKG_ROOT/usr/share/icons/hicolor/256x256/apps/netfs.png"
install -m 0644 "$ROOT_DIR/assets/icons/netfs.svg" \
    "$PKG_ROOT/usr/share/icons/hicolor/scalable/apps/netfs.svg"

TMP_DIR="$(mktemp -d)"
cat > "$TMP_DIR/ScaleTrayIcon.java" <<'EOF'
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class ScaleTrayIcon {
    private static final int SIZE = 48;

    public static void main(String[] args) throws Exception {
        BufferedImage source = ImageIO.read(new File(args[0]));
        BufferedImage trimmed = trimTransparentPadding(source);
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(trimmed, 0, 0, SIZE, SIZE, null);
        graphics.dispose();
        ImageIO.write(image, "png", new File(args[1]));
    }

    private static BufferedImage trimTransparentPadding(BufferedImage source) {
        int minX = source.getWidth();
        int minY = source.getHeight();
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int alpha = (source.getRGB(x, y) >>> 24) & 0xff;
                if (alpha > 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        if (maxX < minX || maxY < minY) {
            return source;
        }
        return source.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }
}
EOF
java -Djava.awt.headless=true "$TMP_DIR/ScaleTrayIcon.java" \
    "$ROOT_DIR/assets/icons/netfs-256.png" \
    "$PKG_ROOT/usr/share/icons/hicolor/48x48/apps/netfs-tray.png"

cat > "$PKG_ROOT/usr/bin/netfs-ui" <<'EOF'
#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/usr/share/netfs"
MAIN_CLASS="netfs.ui.NetFSLauncher"
CACHE_BASE="${XDG_CACHE_HOME:-$HOME/.cache}"
OPENJFX_CACHE="$CACHE_BASE/netfs/openjfx"
LOG_FILE="$CACHE_BASE/netfs/netfs-ui.log"
JAVA_BIN="/usr/lib/jvm/java-21-openjdk-amd64/bin/java"

if [[ ! -x "$JAVA_BIN" ]]; then
  JAVA_BIN="$(command -v java)"
fi

mkdir -p "$OPENJFX_CACHE" "$(dirname "$LOG_FILE")"

JAVA_CMD=(
  "$JAVA_BIN"
  --enable-preview
  --enable-native-access=javafx.graphics,ALL-UNNAMED
  -XX:ErrorFile="$CACHE_BASE/netfs/hs_err_pid%p.log"
  -Djavafx.cachedir="$OPENJFX_CACHE"
  -Dnetfs.installed=true
  -Xms32m
  -Xmx192m
  -XX:+UseG1GC
  -XX:+UseStringDeduplication
  -XX:ReservedCodeCacheSize=64m
  --module-path "$APP_DIR/lib/javafx"
  --add-modules javafx.controls
  -cp "$APP_DIR/app:$APP_DIR/lib/*"
  "$MAIN_CLASS"
)

if [[ -t 2 ]]; then
  exec "${JAVA_CMD[@]}" "$@"
else
  exec "${JAVA_CMD[@]}" "$@" >> "$LOG_FILE" 2>&1
fi
EOF
chmod 0755 "$PKG_ROOT/usr/bin/netfs-ui"

cat > "$PKG_ROOT/usr/share/applications/netfs.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=$APP_NAME
Comment=Java Network FS
Exec=netfs-ui
Icon=netfs
Terminal=false
StartupWMClass=netfs.ui.NetFSApp
Categories=Utility;Network;
EOF

cat > "$PKG_ROOT/DEBIAN/control" <<EOF
Package: $PACKAGE_NAME
Version: $VERSION
Section: utils
Priority: optional
Architecture: $ARCH
Maintainer: $MAINTAINER
Depends: openjdk-21-jre | java21-runtime, libayatana-appindicator3-1
Description: Java Network FS desktop UI
 NetFS is a JavaFX desktop UI for hosting and mounting Java Network FS shares.
EOF

cat > "$PKG_ROOT/DEBIAN/postinst" <<'EOF'
#!/usr/bin/env bash
set -e

if command -v gtk-update-icon-cache >/dev/null 2>&1; then
    gtk-update-icon-cache -q -t -f /usr/share/icons/hicolor || true
fi

if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database -q /usr/share/applications || true
fi

exit 0
EOF
chmod 0755 "$PKG_ROOT/DEBIAN/postinst"

cat > "$PKG_ROOT/DEBIAN/postrm" <<'EOF'
#!/usr/bin/env bash
set -e

if command -v gtk-update-icon-cache >/dev/null 2>&1; then
    gtk-update-icon-cache -q -t -f /usr/share/icons/hicolor || true
fi

if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database -q /usr/share/applications || true
fi

exit 0
EOF
chmod 0755 "$PKG_ROOT/DEBIAN/postrm"

find "$PKG_ROOT" -type d -exec chmod 0755 {} +
find "$PKG_ROOT" -type f ! -path "$PKG_ROOT/DEBIAN/postinst" ! -path "$PKG_ROOT/DEBIAN/postrm" -exec chmod 0644 {} +
chmod 0755 "$PKG_ROOT/usr/bin/netfs-ui" "$PKG_ROOT/DEBIAN/postinst" "$PKG_ROOT/DEBIAN/postrm"

dpkg-deb --root-owner-group --build "$PKG_ROOT" "$DEB_FILE"

echo "Created: $DEB_FILE"
echo "Install with: sudo apt install $DEB_FILE"
