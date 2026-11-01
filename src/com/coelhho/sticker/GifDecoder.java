package com.coelhho.sticker;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Decoder de GIF (GIF87a/GIF89a) puro Java: extrai os frames compostos
 * (canvas completo), duração e transparência. Sem android.*, testável no host.
 */
public final class GifDecoder {

    private GifDecoder() {}

    /** Um frame composto (canvas completo em ARGB) + duração. */
    public static final class Frame {
        public final int[] argb;   // pixels 0xAARRGGBB, tamanho = width*height
        public final int width;    // largura do canvas
        public final int height;   // altura do canvas
        public final int delayMs;  // duração em ms

        public Frame(int[] argb, int width, int height, int delayMs) {
            this.argb = argb;
            this.width = width;
            this.height = height;
            this.delayMs = delayMs;
        }
    }

    /** Decodifica o GIF inteiro em frames compostos. */
    public static List<Frame> decode(byte[] gif) {
        if (gif == null || gif.length < 13) throw new IllegalArgumentException("GIF muito curto");
        String hdr = ascii(gif, 0, 6);
        if (!"GIF87a".equals(hdr) && !"GIF89a".equals(hdr)) {
            throw new IllegalArgumentException("não é um GIF");
        }

        int screenW = u16(gif, 6);
        int screenH = u16(gif, 8);
        if (screenW <= 0 || screenH <= 0) throw new IllegalArgumentException("dimensões inválidas");
        int packed = gif[10] & 0xff;
        boolean gctFlag = (packed & 0x80) != 0;
        int gctSize = 1 << ((packed & 0x07) + 1);

        int pos = 13;
        int[] gct = null;
        if (gctFlag) {
            gct = readColorTable(gif, pos, gctSize);
            pos += 3 * gctSize;
        }

        List<Frame> frames = new ArrayList<>();
        int[] canvas = new int[screenW * screenH]; // fundo transparente (0)

        int pendDisposal = 0, pendDelay = 0, pendTransparent = -1;
        int prevDisposal = 0, prevLeft = 0, prevTop = 0, prevW = 0, prevH = 0;
        int[] restore = null; // para disposal=3

        while (pos < gif.length) {
            int b = gif[pos] & 0xff;
            if (b == 0x3B) break; // trailer
            if (b == 0x21) { // extensão
                int label = gif[pos + 1] & 0xff;
                if (label == 0xF9) { // Graphic Control Extension (bloco fixo de 4 bytes)
                    int gp = gif[pos + 3] & 0xff;
                    pendDisposal = (gp >> 2) & 0x07;
                    pendDelay = u16(gif, pos + 4);
                    pendTransparent = ((gp & 0x01) != 0) ? (gif[pos + 6] & 0xff) : -1;
                    pos += 8;
                } else { // pula extensão genérica (sub-blocos)
                    pos += 2;
                    while (pos < gif.length && (gif[pos] & 0xff) != 0) pos += 1 + (gif[pos] & 0xff);
                    if (pos < gif.length) pos++;
                }
            } else if (b == 0x2C) { // image descriptor
                int left = u16(gif, pos + 1);
                int top = u16(gif, pos + 3);
                int width = u16(gif, pos + 5);
                int height = u16(gif, pos + 7);
                int ip = gif[pos + 9] & 0xff;
                boolean lctFlag = (ip & 0x80) != 0;
                boolean interlace = (ip & 0x40) != 0;
                int lctSize = 1 << ((ip & 0x07) + 1);

                int dataPos = pos + 10;
                int[] lct = null;
                if (lctFlag) {
                    lct = readColorTable(gif, dataPos, lctSize);
                    dataPos += 3 * lctSize;
                }
                int[] ct = (lct != null) ? lct : gct;
                if (ct == null) throw new IllegalArgumentException("sem color table");

                int minCodeSize = gif[dataPos] & 0xff;
                dataPos++;

                ByteArrayOutputStream lzw = new ByteArrayOutputStream();
                while (dataPos < gif.length) {
                    int sz = gif[dataPos++] & 0xff;
                    if (sz == 0) break;
                    if (dataPos + sz > gif.length) break;
                    lzw.write(gif, dataPos, sz);
                    dataPos += sz;
                }
                pos = dataPos;

                byte[] indices = lzwDecode(lzw.toByteArray(), minCodeSize, width * height);
                if (interlace) indices = deinterlace(indices, width, height);

                // aplica disposal do frame anterior
                if (prevDisposal == 2) {
                    clearRect(canvas, screenW, screenH, prevLeft, prevTop, prevW, prevH);
                } else if (prevDisposal == 3 && restore != null) {
                    copyRectBack(canvas, restore, screenW, screenH, prevLeft, prevTop, prevW, prevH);
                }

                int[] before = (pendDisposal == 3)
                        ? copyRect(canvas, screenW, screenH, left, top, width, height)
                        : null;

                // desenha o frame (pixels transparentes são pulados)
                for (int y = 0; y < height; y++) {
                    int cy = top + y;
                    if (cy < 0 || cy >= screenH) continue;
                    for (int x = 0; x < width; x++) {
                        int idx = indices[y * width + x] & 0xff;
                        if (idx == pendTransparent) continue;
                        int cx = left + x;
                        if (cx >= 0 && cx < screenW) canvas[cy * screenW + cx] = ct[idx];
                    }
                }

                int delayMs = pendDelay * 10;
                if (delayMs <= 0) delayMs = 100; // delay 0 = default comum
                frames.add(new Frame(canvas.clone(), screenW, screenH, delayMs));

                prevDisposal = pendDisposal;
                prevLeft = left; prevTop = top; prevW = width; prevH = height;
                restore = before;
                pendDisposal = 0; pendDelay = 0; pendTransparent = -1;
            } else {
                pos++;
            }
        }

        if (frames.isEmpty()) throw new IllegalArgumentException("nenhum frame encontrado");
        return frames;
    }

    // ---- helpers ----

    private static String ascii(byte[] b, int off, int len) {
        return new String(b, off, len, StandardCharsets.US_ASCII);
    }

    private static int u16(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8);
    }

    private static int[] readColorTable(byte[] gif, int pos, int size) {
        int[] ct = new int[size];
        for (int i = 0; i < size; i++) {
            int r = gif[pos + 3 * i] & 0xff;
            int g = gif[pos + 3 * i + 1] & 0xff;
            int bl = gif[pos + 3 * i + 2] & 0xff;
            ct[i] = 0xFF000000 | (r << 16) | (g << 8) | bl;
        }
        return ct;
    }

    private static void clearRect(int[] canvas, int stride, int canvasH, int left, int top, int w, int h) {
        for (int y = 0; y < h; y++) {
            int cy = top + y;
            if (cy < 0 || cy >= canvasH) continue;
            for (int x = 0; x < w; x++) {
                int cx = left + x;
                if (cx >= 0 && cx < stride) canvas[cy * stride + cx] = 0;
            }
        }
    }

    private static int[] copyRect(int[] canvas, int stride, int canvasH, int left, int top, int w, int h) {
        int[] out = new int[w * h];
        for (int y = 0; y < h; y++) {
            int cy = top + y;
            for (int x = 0; x < w; x++) {
                int cx = left + x;
                out[y * w + x] = (cx >= 0 && cx < stride && cy >= 0 && cy < canvasH)
                        ? canvas[cy * stride + cx] : 0;
            }
        }
        return out;
    }

    private static void copyRectBack(int[] canvas, int[] src, int stride, int canvasH,
                                     int left, int top, int w, int h) {
        for (int y = 0; y < h; y++) {
            int cy = top + y;
            if (cy < 0 || cy >= canvasH) continue;
            for (int x = 0; x < w; x++) {
                int cx = left + x;
                if (cx >= 0 && cx < stride) canvas[cy * stride + cx] = src[y * w + x];
            }
        }
    }

    /** Descomprime o stream LZW do GIF (códigos LSB-first, tamanho variável). */
    private static byte[] lzwDecode(byte[] data, int minCodeSize, int expectedPixels) {
        int clearCode = 1 << minCodeSize;
        int endCode = clearCode + 1;
        int[] prefix = new int[4096];
        byte[] suffix = new byte[4096];
        byte[] first = new byte[4096];
        for (int i = 0; i < clearCode; i++) {
            prefix[i] = -1;
            suffix[i] = (byte) i;
            first[i] = (byte) i;
        }
        int nextCode = endCode + 1;
        int codeSize = minCodeSize + 1;

        byte[] out = new byte[expectedPixels];
        int outPos = 0;
        int[] stack = new int[4096];
        BitReader br = new BitReader(data);
        int old = -1;

        while (outPos < expectedPixels) {
            int code = br.read(codeSize);
            if (code < 0) break;
            if (code == clearCode) {
                nextCode = endCode + 1;
                codeSize = minCodeSize + 1;
                old = -1;
                continue;
            }
            if (code == endCode) break;

            if (old < 0) {
                outPos = emit(out, outPos, stack, prefix, suffix, code);
                old = code;
                continue;
            }

            byte appended;
            if (code < nextCode) {
                appended = first[code];
            } else if (code == nextCode) {
                appended = first[old];
            } else {
                break; // código inválido
            }

            if (code < nextCode) {
                outPos = emit(out, outPos, stack, prefix, suffix, code);
            } else { // code == nextCode: seq(old) + first(old)
                outPos = emit(out, outPos, stack, prefix, suffix, old);
                if (outPos < out.length) out[outPos++] = first[old];
            }

            if (nextCode < 4096) {
                prefix[nextCode] = old;
                suffix[nextCode] = appended;
                first[nextCode] = first[old];
                nextCode++;
                if (nextCode == (1 << codeSize) && codeSize < 12) codeSize++;
            }
            old = code;
        }
        return out;
    }

    private static int emit(byte[] out, int outPos, int[] stack, int[] prefix, byte[] suffix, int code) {
        int sp = 0;
        int c = code;
        while (c >= 0 && prefix[c] >= 0) {
            stack[sp++] = suffix[c] & 0xff;
            c = prefix[c];
        }
        stack[sp++] = c & 0xff;
        while (sp > 0) {
            if (outPos < out.length) out[outPos++] = (byte) stack[--sp];
        }
        return outPos;
    }

    /** Reordena os índices de uma imagem entrelaçada (4 passes do GIF). */
    private static byte[] deinterlace(byte[] src, int w, int h) {
        byte[] dst = new byte[w * h];
        int[] starts = {0, 4, 2, 1};
        int[] steps = {8, 8, 4, 2};
        int srcPos = 0;
        for (int pass = 0; pass < 4; pass++) {
            for (int y = starts[pass]; y < h; y += steps[pass]) {
                System.arraycopy(src, srcPos, dst, y * w, w);
                srcPos += w;
            }
        }
        return dst;
    }

    /** Leitor de bits LSB-first (empacotamento de códigos LZW do GIF). */
    private static final class BitReader {
        private final byte[] data;
        private int pos;
        private int bitBuf;
        private int bitCnt;

        BitReader(byte[] data) { this.data = data; }

        int read(int nbits) {
            while (bitCnt < nbits) {
                if (pos >= data.length) return -1;
                bitBuf |= (data[pos++] & 0xff) << bitCnt;
                bitCnt += 8;
            }
            int v = bitBuf & ((1 << nbits) - 1);
            bitBuf >>>= nbits;
            bitCnt -= nbits;
            return v;
        }
    }
}
