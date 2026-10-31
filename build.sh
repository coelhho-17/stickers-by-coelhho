#!/usr/bin/env bash
# Build limpo do Sticker Limpo (sem Gradle): aapt2 + javac + d8 + apksigner.
set -euo pipefail

SDK="C:/Users/thiag/tools/android-sdk"
BT="$SDK/build-tools/35.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
OUT="build"
PKG_PATH="com/coelhho/sticker"

AAPT2="$BT/aapt2.exe"
ZIPALIGN="$BT/zipalign.exe"

cd "$(dirname "$0")"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/gen"

echo "==> 1/6 aapt2 compile (recursos)"
"$AAPT2" compile --dir res -o "$OUT/res.zip"

echo "==> 2/6 aapt2 link (manifest + recursos -> APK base)"
"$AAPT2" link \
  -o "$OUT/app.unsigned.apk" \
  -I "$PLATFORM" \
  --manifest AndroidManifest.xml \
  -R "$OUT/res.zip" \
  --java "$OUT/gen" \
  --auto-add-overlay

echo "==> 3/6 javac (fontes -> bytecode Java 8)"
javac -source 8 -target 8 \
  -bootclasspath "$PLATFORM" \
  -classpath "$PLATFORM" \
  -d "$OUT/classes" \
  "$OUT/gen/$PKG_PATH/R.java" \
  "src/$PKG_PATH/StickerContentProvider.java" \
  "src/$PKG_PATH/EraseView.java" \
  "src/$PKG_PATH/MainActivity.java"

echo "==> 4/6 d8 (bytecode -> classes.dex)"
jar cf "$OUT/classes.jar" -C "$OUT/classes" .
java -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
  --lib "$PLATFORM" \
  --min-api 21 \
  --output "$OUT" \
  "$OUT/classes.jar"

echo "==> 5/6 adicionar classes.dex ao APK"
cd "$OUT"
jar uf app.unsigned.apk classes.dex
cd ..

echo "==> 6/6 zipalign + assinatura (release)"
"$ZIPALIGN" -f 4 "$OUT/app.unsigned.apk" "$OUT/app.aligned.apk"

if [ ! -f release.keystore ]; then
  echo "ERRO: release.keystore não encontrado."
  echo "Ele NÃO é versionado. Gere com:"
  echo "  keytool -genkeypair -keystore release.keystore -alias stickers -keyalg RSA -keysize 2048 -validity 36500"
  exit 1
fi

if [ ! -f keystore.properties ]; then
  echo "ERRO: keystore.properties não encontrado (guarda a senha do keystore; não versionar)."
  exit 1
fi

# senhas/alias do keystore.properties (gitignored)
. ./keystore.properties

java -jar "$BT/lib/apksigner.jar" sign \
  --ks release.keystore \
  --ks-pass "pass:$storePassword" \
  --key-pass "pass:$keyPassword" \
  --ks-key-alias "$keyAlias" \
  --out "$OUT/StickerLimpo.apk" \
  "$OUT/app.aligned.apk"

echo "==> PRONTO: $OUT/StickerLimpo.apk"
ls -la "$OUT/StickerLimpo.apk"
