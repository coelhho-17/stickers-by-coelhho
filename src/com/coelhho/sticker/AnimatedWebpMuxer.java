package com.coelhho.sticker;

import java.io.ByteArrayOutputStream;
import java.util.List;

/**
 * Monta um WebP ANIMADO (container VP8X + ANIM + ANMF) a partir de frames já
 * codificados como WebP estático (payloads VP8/VP8L + ALPH extraídos pelo
 * StaticWebpParser). Puro Java, sem android.*.
 */
public final class AnimatedWebpMuxer {

    private AnimatedWebpMuxer() {}

    /** Um frame do WebP animado. */
    public static final class Frame {
        public final byte[] payload;   // bitstream VP8 (lossy) ou VP8L (lossless)
        public final byte[] alph;      // payload ALPH, ou null
        public final boolean lossless; // true = VP8L
        public final int width;
        public final int height;
        public final int durationMs;

        public Frame(byte[] payload, byte[] alph, boolean lossless,
                     int width, int height, int durationMs) {
            this.payload = payload;
            this.alph = alph;
            this.lossless = lossless;
            this.width = width;
            this.height = height;
            this.durationMs = durationMs;
        }
    }

    /**
     * @param frames       frames na ordem de exibição
     * @param canvasWidth  largura do canvas (igual p/ todos no nosso caso)
     * @param canvasHeight altura do canvas
     * @param loopCount    0 = loop infinito; N = repete N vezes
     * @return bytes do arquivo WebP animado completo (RIFF)
     */
    public static byte[] mux(List<Frame> frames, int canvasWidth, int canvasHeight, int loopCount) {
        if (frames == null || frames.isEmpty()) {
            throw new IllegalArgumentException("frames vazio");
        }

        boolean hasAlpha = false;
        for (Frame f : frames) {
            if (f.alph != null) hasAlpha = true;
        }

        ByteArrayOutputStream body = new ByteArrayOutputStream(); // VP8X + ANIM + ANMFs

        // --- VP8X (flags + dimensões do canvas) ---
        byte[] vp8x = new byte[10];
        int flags = 0x02; // ANIMATION
        if (hasAlpha) flags |= 0x10; // ALPHA
        vp8x[0] = (byte) flags;
        writeU24(vp8x, 4, canvasWidth - 1);
        writeU24(vp8x, 7, canvasHeight - 1);
        writeChunk(body, "VP8X", vp8x);

        // --- ANIM (cor de fundo + loop count) ---
        byte[] anim = new byte[6];
        writeU16(anim, 4, loopCount & 0xffff);
        writeChunk(body, "ANIM", anim);

        // --- ANMF (um por frame) ---
        for (Frame f : frames) {
            ByteArrayOutputStream fd = new ByteArrayOutputStream();
            if (f.alph != null) writeChunk(fd, "ALPH", f.alph);
            writeChunk(fd, f.lossless ? "VP8L" : "VP8 ", f.payload);
            byte[] frameData = fd.toByteArray();

            byte[] h = new byte[16];
            writeU24(h, 0, 0);            // X
            writeU24(h, 3, 0);            // Y
            writeU24(h, 6, f.width - 1);  // width - 1
            writeU24(h, 9, f.height - 1); // height - 1
            writeU24(h, 12, f.durationMs);// duração em ms
            h[15] = 0x02;                 // disposal = dispose to background

            byte[] anmf = new byte[16 + frameData.length];
            System.arraycopy(h, 0, anmf, 0, 16);
            System.arraycopy(frameData, 0, anmf, 16, frameData.length);
            writeChunk(body, "ANMF", anmf);
        }

        // --- RIFF header + corpo ---
        byte[] bodyBytes = body.toByteArray();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeFourcc(out, "RIFF");
        writeU32(out, 4 + bodyBytes.length); // tamanho após o campo de tamanho ("WEBP" + chunks)
        writeFourcc(out, "WEBP");
        out.write(bodyBytes, 0, bodyBytes.length);
        return out.toByteArray();
    }

    private static void writeChunk(ByteArrayOutputStream out, String fourcc, byte[] payload) {
        writeFourcc(out, fourcc);
        writeU32(out, payload.length);
        out.write(payload, 0, payload.length);
        if ((payload.length & 1) == 1) out.write(0); // pad p/ tamanho par (RIFF)
    }

    private static void writeFourcc(ByteArrayOutputStream out, String s) {
        for (int i = 0; i < 4; i++) out.write(s.charAt(i) & 0xff);
    }

    private static void writeU32(ByteArrayOutputStream out, int v) {
        out.write(v & 0xff);
        out.write((v >>> 8) & 0xff);
        out.write((v >>> 16) & 0xff);
        out.write((v >>> 24) & 0xff);
    }

    private static void writeU24(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xff);
        b[off + 1] = (byte) ((v >>> 8) & 0xff);
        b[off + 2] = (byte) ((v >>> 16) & 0xff);
    }

    private static void writeU16(byte[] b, int off, int v) {
        b[off] = (byte) (v & 0xff);
        b[off + 1] = (byte) ((v >>> 8) & 0xff);
    }
}
