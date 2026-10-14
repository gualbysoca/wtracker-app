#!/usr/bin/env bash
# Publica una nueva versión de WTracker Field para la autoactualización.
#
# Uso:   scripts/publish-release.sh "Notas de la versión (opcional)"
# Antes: subir versionCode (y versionName) en app/build.gradle.kts.
#
# Genera releases/wtracker-<versionName>.apk firmado y releases/version.json.
# Luego hay que hacer commit y push de la carpeta releases/ (el script NO lo hace).
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

NOTES="${1:-}"
GRADLE_FILE="app/build.gradle.kts"
RELEASES_DIR="releases"
MANIFEST_FILE="$RELEASES_DIR/version.json"

fail() { echo "ERROR: $1" >&2; exit 1; }

VERSION_CODE="$(sed -n 's/.*versionCode = \([0-9][0-9]*\).*/\1/p' "$GRADLE_FILE" | head -1)"
VERSION_NAME="$(sed -n 's/.*versionName = "\([^"]*\)".*/\1/p' "$GRADLE_FILE" | head -1)"
[ -n "$VERSION_CODE" ] && [ -n "$VERSION_NAME" ] || fail "No se pudo leer versionCode/versionName de $GRADLE_FILE"
[ -f keystore.properties ] || fail "Falta keystore.properties (credenciales de firma). Sin la misma firma Android rechaza la actualización."

if [ -f "$MANIFEST_FILE" ]; then
  PUBLISHED_CODE="$(sed -n 's/.*"versionCode"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' "$MANIFEST_FILE" | head -1)"
  if [ -n "$PUBLISHED_CODE" ] && [ "$VERSION_CODE" -le "$PUBLISHED_CODE" ]; then
    fail "versionCode $VERSION_CODE no supera al publicado ($PUBLISHED_CODE). Súbelo en $GRADLE_FILE."
  fi
fi

# Gradle necesita JDK 11+; se prefiere el JDK 17 del usuario si existe.
JDK17="$(ls -d "$HOME"/Library/Java/JavaVirtualMachines/jdk-17*/Contents/Home 2>/dev/null | tail -1 || true)"
[ -n "$JDK17" ] && export JAVA_HOME="$JDK17"

echo "Compilando versión $VERSION_NAME (code $VERSION_CODE)..."
./gradlew assembleRelease

BUILT_APK="app/build/outputs/apk/release/app-release.apk"
[ -f "$BUILT_APK" ] || fail "No se generó $BUILT_APK (¿el build release quedó sin firmar?)"

APK_NAME="wtracker-$VERSION_NAME.apk"
mkdir -p "$RELEASES_DIR"
find "$RELEASES_DIR" -maxdepth 1 -name '*.apk' ! -name "$APK_NAME" -delete
cp "$BUILT_APK" "$RELEASES_DIR/$APK_NAME"

SHA256="$(shasum -a 256 "$RELEASES_DIR/$APK_NAME" | cut -d' ' -f1)"
ESCAPED_NOTES="$(printf '%s' "$NOTES" | sed 's/\\/\\\\/g; s/"/\\"/g')"

cat > "$MANIFEST_FILE" <<EOF
{
  "versionCode": $VERSION_CODE,
  "versionName": "$VERSION_NAME",
  "apk": "$APK_NAME",
  "sha256": "$SHA256",
  "notes": "$ESCAPED_NOTES"
}
EOF

echo
echo "Publicado en $RELEASES_DIR/:"
echo "  $APK_NAME  (sha256 $SHA256)"
echo "  version.json"
echo
echo "Siguiente paso: git add $RELEASES_DIR && git commit && git push"
