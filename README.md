# Stickers by coelhho

Android app to create WhatsApp stickers **without the white outline** that the native maker adds. No ads, no trackers — a clean rebuild.

> [Leia em português](README.pt.md)

## What it does

- Pick an image from the gallery (or receive it via the share sheet / `SEND` intent).
- Manual eraser to remove the background (transparent alpha).
- Generates a 512x512 WebP with transparency and delivers it straight to WhatsApp as a sticker pack — bypassing WhatsApp's native editor, which is what adds the automatic white outline.

## Build (no Gradle)

Requirements:

- JDK 21
- Android SDK (build-tools 35.0.0 and platforms/android-34)
- `bash` (Git Bash / MSYS)

```bash
bash build.sh
```

The APK is written to `build/StickerLimpo.apk`, signed with the release keystore (`release.keystore` — **not** committed).

> Release signing: `build.sh` reads the keystore path, alias and passwords from `keystore.properties` (git-ignored). Keep **both** `release.keystore` and `keystore.properties` backed up — losing them means you can no longer ship updates with the same signature.

## Project layout

- `src/com/coelhho/sticker/` — source (`MainActivity`, `StickerContentProvider`, `EraseView`)
- `res/` — resources and adaptive icon
- `build.sh` — Gradle-free build (aapt2 + javac + d8 + apksigner)
- `tools/gen_icons.py` — regenerates the app icons (adaptive + per-density fallback)

## Technical notes

Reverse-engineering findings about WhatsApp (v2.26.x) that make third-party sticker import possible:

1. **Ownership check**: the `ENABLE_STICKER_PACK` intent must be fired via `startActivityForResult` from the app's own activity (not `startActivity`, not the shell), and the calling package must own the `ContentProvider` authority. Otherwise WhatsApp closes silently in ~7 ms.
2. **Size limit**: static stickers are capped at ~100 KB (103,423 bytes). `WEBP_LOSSLESS` blows past that on detailed images; use `WEBP_LOSSY` (which keeps alpha) and lower the quality until it fits.

## License

MIT — see [LICENSE](LICENSE).
