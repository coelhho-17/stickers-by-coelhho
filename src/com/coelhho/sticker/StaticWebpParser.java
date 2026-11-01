package com.coelhho.sticker;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Extrai de um WebP ESTÁTICO (saída do Bitmap.compress) o payload que será
 * embutido como frame num WebP animado: o bitstream VP8 (lossy) ou VP8L
 * (lossless), mais o chunk ALPH (alpha) se houver.
 *
 * Puro Java (sem android.*) para poder ser testado no host.
 */
public final class StaticWebpParser {

    private StaticWebpParser() {}

    /** Payload de um frame extraído de um WebP estático. */
    public static final class Result {
        public final byte[] payload;   // bitstream VP8 (lossy) ou VP8L (lossless)
        public final byte[] alph;      // payload ALPH, ou null se sem alpha
        public final boolean lossless; // true = VP8L, false = VP8

        public Result(byte[] payload, byte[] alph, boolean lossless) {
            this.payload = payload;
            this.alph = alph;
            this.lossless = lossless;
        }
    }

    public static Result parse(byte[] webp) {
        if (webp == null || webp.length < 20) {
            throw new IllegalArgumentException("WebP muito curto");
        }
        if (!"RIFF".equals(fourcc(webp, 0)) || !"WEBP".equals(fourcc(webp, 8))) {
            throw new IllegalArgumentException("não é um arquivo WebP (RIFF/WEBP ausente)");
        }

        byte[] vp8 = null, vp8l = null, alph = null;
        int i = 12; // pula "RIFF"(4) + size(4) + "WEBP"(4)
        while (i + 8 <= webp.length) {
            String cc = fourcc(webp, i);
            int size = le32(webp, i + 4);
            if (size < 0 || i + 8 + size > webp.length) break;
            int start = i + 8;
            if ("VP8 ".equals(cc)) {
                vp8 = Arrays.copyOfRange(webp, start, start + size);
            } else if ("VP8L".equals(cc)) {
                vp8l = Arrays.copyOfRange(webp, start, start + size);
            } else if ("ALPH".equals(cc)) {
                alph = Arrays.copyOfRange(webp, start, start + size);
            }
            i += 8 + size + (size & 1); // chunks RIFF são padronizados p/ tamanho par
        }

        if (vp8l != null) return new Result(vp8l, alph, true);
        if (vp8 != null) return new Result(vp8, alph, false);
        throw new IllegalArgumentException("nenhum chunk VP8/VP8L encontrado");
    }

    private static String fourcc(byte[] b, int off) {
        return new String(b, off, 4, StandardCharsets.US_ASCII);
    }

    private static int le32(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8)
                | ((b[off + 2] & 0xff) << 16) | ((b[off + 3] & 0xff) << 24);
    }
}
