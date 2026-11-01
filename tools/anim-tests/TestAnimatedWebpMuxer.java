import com.coelhho.sticker.AnimatedWebpMuxer;
import com.coelhho.sticker.StaticWebpParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Testes do AnimatedWebpMuxer (estrutura do container + validação ffmpeg no run.sh). */
public final class TestAnimatedWebpMuxer {

    static int le32(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8)
                | ((b[off + 2] & 0xff) << 16) | ((b[off + 3] & 0xff) << 24);
    }

    static int le24(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8) | ((b[off + 2] & 0xff) << 16);
    }

    static String fourcc(byte[] b, int off) {
        return new String(b, off, 4, StandardCharsets.US_ASCII);
    }

    /** Devolve o payload do primeiro chunk com o fourcc pedido (varre RIFF). */
    static byte[] chunkPayload(byte[] webp, String want) {
        int i = 12;
        while (i + 8 <= webp.length) {
            String cc = fourcc(webp, i);
            int size = le32(webp, i + 4);
            if (size < 0 || i + 8 + size > webp.length) break;
            if (cc.equals(want)) return Arrays.copyOfRange(webp, i + 8, i + 8 + size);
            i += 8 + size + (size & 1);
        }
        return null;
    }

    public static void run(String fixtures) throws Exception {
        byte[] red = Files.readAllBytes(Paths.get(fixtures, "lossy.webp"));
        byte[] alpha = Files.readAllBytes(Paths.get(fixtures, "lossy_alpha.webp"));
        StaticWebpParser.Result rRed = StaticWebpParser.parse(red);
        StaticWebpParser.Result rAlpha = StaticWebpParser.parse(alpha);

        // 3 frames lossy sem alpha, durações 100/200/300
        List<AnimatedWebpMuxer.Frame> frames = new ArrayList<>();
        frames.add(new AnimatedWebpMuxer.Frame(rRed.payload, null, false, 64, 64, 100));
        frames.add(new AnimatedWebpMuxer.Frame(rRed.payload, null, false, 64, 64, 200));
        frames.add(new AnimatedWebpMuxer.Frame(rRed.payload, null, false, 64, 64, 300));

        byte[] out = AnimatedWebpMuxer.mux(frames, 64, 64, 0);
        Assert.notNull(out, "mux não deve retornar null");
        Assert.eq("RIFF", fourcc(out, 0), "header RIFF");
        Assert.eq("WEBP", fourcc(out, 8), "header WEBP");

        byte[] vp8x = chunkPayload(out, "VP8X");
        Assert.notNull(vp8x, "deve haver chunk VP8X");
        Assert.eq(10, vp8x.length, "VP8X deve ter 10 bytes de payload");
        Assert.isTrue((vp8x[0] & 0x02) != 0, "flag ANIMATION deve estar setada");
        Assert.isTrue((vp8x[0] & 0x10) == 0, "sem alpha => flag ALPHA não setada");
        Assert.eq(64, le24(vp8x, 4) + 1, "canvas width (VP8X)");
        Assert.eq(64, le24(vp8x, 7) + 1, "canvas height (VP8X)");

        Assert.notNull(chunkPayload(out, "ANIM"), "deve haver chunk ANIM");

        // conta ANMF e checa durações
        List<Integer> durations = new ArrayList<>();
        int i = 12;
        while (i + 8 <= out.length) {
            String cc = fourcc(out, i);
            int size = le32(out, i + 4);
            if (size < 0 || i + 8 + size > out.length) break;
            if (cc.equals("ANMF")) {
                durations.add(le24(out, i + 8 + 12)); // duração no offset 12 do payload ANMF
            }
            i += 8 + size + (size & 1);
        }
        Assert.eq(3, durations.size(), "deve haver exatamente 3 chunks ANMF");
        Assert.eq(Arrays.asList(100, 200, 300), durations, "durações dos frames");

        // caso com alpha: 1 frame com ALPH
        List<AnimatedWebpMuxer.Frame> fa = new ArrayList<>();
        fa.add(new AnimatedWebpMuxer.Frame(rAlpha.payload, rAlpha.alph, false, 64, 64, 100));
        byte[] outA = AnimatedWebpMuxer.mux(fa, 64, 64, 0);
        byte[] vp8xA = chunkPayload(outA, "VP8X");
        Assert.notNull(vp8xA, "mux(alpha) deve ter VP8X");
        Assert.isTrue((vp8xA[0] & 0x10) != 0, "com alpha => flag ALPHA setada no VP8X");

        // escreve para validação externa no run.sh (ffmpeg/ffprobe)
        Files.write(Paths.get(fixtures, "muxed.webp"), out);
        Files.write(Paths.get(fixtures, "muxed_alpha.webp"), outA);

        System.out.println("TestAnimatedWebpMuxer: OK");
    }

    public static void main(String[] args) throws Exception {
        run(args[0]);
    }
}
