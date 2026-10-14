#!/usr/bin/env bash
#
# Build the APK with Android SDK build-tools only — no Gradle, no network.
#
# Requirements: a JDK (11+) and Android SDK build-tools + one platform.
# Override with ANDROID_HOME / ANDROID_SDK_ROOT, BT_VERSION and PLATFORM.
#
set -euo pipefail

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
BT_VERSION="${BT_VERSION:-36.0.0}"
PLATFORM="${PLATFORM:-android-37.0}"

BT="$SDK/build-tools/$BT_VERSION"
ANDROID_JAR="$SDK/platforms/$PLATFORM/android.jar"
JAVAC="${JAVAC:-javac}"

HERE="$(cd "$(dirname "$0")" && pwd)"
cd "$HERE"

APP="lynk-assistant"
OUT="build"

[ -d "$BT" ] || { echo "build-tools not found: $BT"; exit 1; }
[ -f "$ANDROID_JAR" ] || { echo "android.jar not found: $ANDROID_JAR"; exit 1; }

echo "== clean =="
rm -rf "$OUT" "$APP-unsigned.apk" "$APP-aligned.apk" "$APP.apk"
mkdir -p "$OUT/classes" "$OUT/dex"

echo "== aapt2 (compile + link, emits R.java) =="
"$BT/aapt2" compile --dir res -o "$OUT/res.zip"
"$BT/aapt2" link -o "$APP-unsigned.apk" \
    -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    -R "$OUT/res.zip" \
    --auto-add-overlay \
    --java "$OUT/gen" \
    --min-sdk-version 21 --target-sdk-version 31
# versionCode / versionName come from AndroidManifest.xml (single source of truth)

echo "== javac =="
"$JAVAC" --release 11 -classpath "$ANDROID_JAR" -encoding UTF-8 \
    -d "$OUT/classes" $(find java "$OUT/gen" -name '*.java')

echo "== d8 =="
"$BT/d8" --lib "$ANDROID_JAR" --min-api 21 --output "$OUT/dex" \
    $(find "$OUT/classes" -name '*.class')

echo "== add dex =="
( cd "$OUT/dex" && zip -q -X "$HERE/$APP-unsigned.apk" classes.dex )

echo "== zipalign =="
"$BT/zipalign" -f -p 4 "$APP-unsigned.apk" "$APP-aligned.apk"

echo "== sign =="
KS="${LYNK_KEYSTORE:-$HOME/.android/debug.keystore}"
if [ ! -f "$KS" ]; then
  echo "  generating keystore at $KS"
  mkdir -p "$(dirname "$KS")"
  keytool -genkeypair -v -keystore "$KS" -storepass android -keypass android \
      -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
      -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --key-pass pass:android \
    --ks-key-alias androiddebugkey --min-sdk-version 21 \
    --out "$APP.apk" "$APP-aligned.apk"

echo "== verify =="
"$BT/apksigner" verify "$APP.apk" >/dev/null
"$BT/aapt2" dump badging "$APP.apk" | head -8
ls -l "$APP.apk"
echo "OK -> $HERE/$APP.apk"
