#!/usr/bin/env bash
set -euo pipefail

# 1. Create portable tarball from createDistributable output
tar -czf Iskra-Linux-x86_64.tar.gz -C build/compose/binaries/main/app iskra

# 2. Prepare AppDir
rm -rf AppDir
mkdir -p AppDir/usr
cp -r build/compose/binaries/main/app/iskra/* AppDir/usr/

cat << 'EOF' > AppDir/AppRun
#!/bin/sh
SELF=$(readlink -f "$0")
HERE=${SELF%/*}
export _JAVA_AWT_WM_NONREPARENTING=1
export AWT_TOOLKIT=XToolkit
exec "$HERE/usr/bin/iskra" "$@"
EOF
chmod +x AppDir/AppRun

cat << 'EOF' > AppDir/iskra.desktop
[Desktop Entry]
Name=Iskra
Exec=AppRun
Icon=iskra
Type=Application
Categories=Network;
EOF

cp icons/icon.png AppDir/iskra.png
cp icons/icon.png AppDir/.DirIcon

# 3. Download appimagetool and generate AppImage
wget -q https://github.com/AppImage/AppImageKit/releases/download/continuous/appimagetool-x86_64.AppImage -O appimagetool
chmod +x appimagetool
./appimagetool --appimage-extract-and-run AppDir Iskra-Linux-x86_64.AppImage || true
