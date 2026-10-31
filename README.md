# Stickers by coelhho

App Android para criar figurinhas (stickers) do WhatsApp **sem o contorno branco** que o criador nativo aplica. Sem anúncios e sem trackers — uma versão limpa.

## O que faz

- Escolhe uma imagem da galeria (ou recebe via compartilhar / intent `SEND`).
- Borracha manual para apagar o fundo (alpha transparente).
- Gera WebP 512x512 com transparência e entrega direto ao WhatsApp via pacote de figurinhas — sem passar pelo editor nativo, que é o que adiciona o contorno branco automático.

## Build (sem Gradle)

Requisitos:

- JDK 21
- Android SDK (build-tools 35.0.0 e platforms/android-34)
- `bash` (Git Bash / MSYS)

```bash
bash build.sh
```

APK gerado em `build/StickerLimpo.apk`, assinado com keystore de debug gerado automaticamente (não versionado).

## Estrutura

- `src/com/coelhho/sticker/` — código (`MainActivity`, `StickerContentProvider`, `EraseView`)
- `res/` — recursos e ícone adaptativo
- `build.sh` — build sem Gradle (aapt2 + javac + d8 + apksigner)
- `tools/gen_icons.py` — gera os ícones (adaptativo + fallback por densidade)

## Notas técnicas

Descobertas de engenharia reversa do WhatsApp (v2.26.x) que viabilizam a importação de figurinhas de um app pessoal:

1. **Ownership check**: a intent `ENABLE_STICKER_PACK` precisa ser disparada via `startActivityForResult` a partir da própria activity do app (não `startActivity`, não shell), e o pacote chamador precisa ser o dono da authority do `ContentProvider`. Caso contrário o WhatsApp fecha silenciosamente em ~7ms.
2. **Limite de tamanho**: sticker estático é limitado a ~100 KB (103.423 bytes). `WEBP_LOSSLESS` estoura fácil em imagem detalhada; usa-se `WEBP_LOSSY` (que preserva alpha) com redução de qualidade até caber.

## Licença

MIT — veja [LICENSE](LICENSE).
