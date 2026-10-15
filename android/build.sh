#!/usr/bin/env bash
# Builds build/Abac.apk: the web app in ../calculator wrapped in a WebView.
#
# Needs a JDK (javac, keytool) and the Android command-line tools. On Ubuntu:
#   sudo apt install aapt dalvik-exchange zipalign apksigner android-sdk-platform-23
#
# The APK is signed with abac-release.jks (next to this script), created on the first run.
# Keep that file: Android only installs an update signed with the same key.
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR=${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}
DX=${DX:-$(command -v dalvik-exchange || command -v dx)}
KEYSTORE=${KEYSTORE:-abac-release.jks}
KEY_ALIAS=${KEY_ALIAS:-abac}
KEY_PASS=${KEY_PASS:-abac-calculator}
OUT=build

rm -rf "$OUT"
mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/assets/www/fonts"

echo "1/6 Copying the web app"
cp ../calculator/index.html "$OUT/assets/www/"
cp ../calculator/fonts/fonts.css ../calculator/fonts/*.woff2 "$OUT/assets/www/fonts/"

echo "2/6 Packaging resources"
# resources.arsc must be stored uncompressed for targetSdk 30+ (-0 arsc)
aapt package -f -m -J "$OUT/gen" -M AndroidManifest.xml -S res -A "$OUT/assets" \
    -I "$ANDROID_JAR" -0 arsc -F "$OUT/unsigned.apk"

echo "3/6 Compiling Java"
javac -source 8 -target 8 -Xlint:-options -encoding UTF-8 \
    -bootclasspath "$ANDROID_JAR" -d "$OUT/classes" \
    $(find src "$OUT/gen" -name '*.java')

echo "4/6 Converting to dex"
"$DX" --dex --min-sdk-version=24 --output="$OUT/classes.dex" "$OUT/classes"
(cd "$OUT" && aapt add -f unsigned.apk classes.dex >/dev/null)

echo "5/6 Aligning"
zipalign -p -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "6/6 Signing"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair -keystore "$KEYSTORE" -alias "$KEY_ALIAS" -keyalg RSA -keysize 3072 \
        -validity 10000 -storepass "$KEY_PASS" -keypass "$KEY_PASS" \
        -dname "CN=Abac, O=florin9523, C=RO" >/dev/null 2>&1
fi
apksigner sign --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
    --ks-pass "pass:$KEY_PASS" --key-pass "pass:$KEY_PASS" \
    --out "$OUT/Abac.apk" "$OUT/aligned.apk"
apksigner verify "$OUT/Abac.apk"

echo "Done: $(pwd)/$OUT/Abac.apk ($(du -h "$OUT/Abac.apk" | cut -f1))"
