#!/usr/bin/env bash
# Harness de teste host para os componentes puros (WebP/GIF), sem Android.
# Gera fixtures com ffmpeg, compila as classes puras + testes e roda.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd -W)"
SRC="$ROOT/src/com/coelhho/sticker"
HERE="$(cd "$(dirname "$0")" && pwd -W)"
OUT="$ROOT/build/anim-tests"
FIX="$OUT/fixtures"
mkdir -p "$FIX" "$OUT/classes"

echo "==> gerando fixtures (ffmpeg)"
ffmpeg -y -hide_banner -loglevel error -f lavfi -i "color=c=red:size=64x64:d=1" -frames:v 1 -c:v libwebp -lossless 0 "$FIX/lossy.webp"
ffmpeg -y -hide_banner -loglevel error -f lavfi -i "color=c=red:size=64x64:d=1,format=rgba,colorchannelmixer=aa=0.5" -frames:v 1 -c:v libwebp -lossless 0 "$FIX/lossy_alpha.webp"
# GIF de teste: 2s, 5fps, 64x64 (usado pelo TestGifDecoder)
ffmpeg -y -hide_banner -loglevel error -f lavfi -i "testsrc=duration=2:size=64x64:rate=5" -loop 0 "$FIX/test.gif"

echo "==> compilando"
javac -encoding UTF-8 -d "$OUT/classes" \
  "$SRC/StaticWebpParser.java" \
  "$SRC/AnimatedWebpMuxer.java" \
  "$SRC/GifDecoder.java" \
  "$HERE/Assert.java" \
  "$HERE/TestStaticWebpParser.java" \
  "$HERE/TestAnimatedWebpMuxer.java" \
  "$HERE/TestGifDecoder.java" \
  "$HERE/TestMain.java"

echo "==> rodando testes"
java -cp "$OUT/classes" TestMain "$FIX"

echo "==> validação externa (ffmpeg decodifica o WebP animado)"
for f in muxed.webp muxed_alpha.webp; do
  if [ -f "$FIX/$f" ]; then
    codec=$(ffprobe -v error -select_streams v:0 -show_entries stream=codec_name -of csv=p=0 "$FIX/$f")
    echo "$f -> codec=$codec"
    ffmpeg -y -hide_banner -loglevel error -i "$FIX/$f" -f null - && echo "  $f: decode OK" || echo "  $f: decode FALHOU"
  fi
done
