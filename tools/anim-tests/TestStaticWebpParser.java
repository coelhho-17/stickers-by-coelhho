import com.coelhho.sticker.StaticWebpParser;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;

/** Testes do StaticWebpParser. Fixtures geradas pelo run.sh (ffmpeg). */
public final class TestStaticWebpParser {

    // ---- helper independente (reimplementação simples p/ validar) ----

    static int le32(byte[] b, int off) {
        return (b[off] & 0xff) | ((b[off + 1] & 0xff) << 8)
                | ((b[off + 2] & 0xff) << 16) | ((b[off + 3] & 0xff) << 24);
    }

    static String fourcc(byte[] b, int off) {
        return new String(b, off, 4, StandardCharsets.US_ASCII);
    }

    /** Varre os chunks RIFF e devolve o payload do fourcc pedido (ou null). */
    static byte[] chunkPayload(byte[] webp, String want) {
        int i = 12; // pula "RIFF"(4) + size(4) + "WEBP"(4)
        while (i + 8 <= webp.length) {
            String cc = fourcc(webp, i);
            int size = le32(webp, i + 4);
            if (size < 0 || i + 8 + size > webp.length) break;
            if (cc.equals(want)) return Arrays.copyOfRange(webp, i + 8, i + 8 + size);
            i += 8 + size + (size & 1); // chunks RIFF são padronizados p/ tamanho par
        }
        return null;
    }

    public static void run(String fixtures) throws Exception {
        // lossy sem alpha
        byte[] lossy = Files.readAllBytes(Paths.get(fixtures, "lossy.webp"));
        StaticWebpParser.Result r1 = StaticWebpParser.parse(lossy);
        Assert.notNull(r1, "parse(lossy) não deve retornar null");
        Assert.eq(Boolean.FALSE, r1.lossless, "lossy.webp deve ser VP8 (lossy)");
        Assert.eq(null, r1.alph, "lossy.webp sem alpha não deve ter ALPH");
        Assert.notNull(r1.payload, "payload VP8 não pode ser null");
        byte[] vp8 = chunkPayload(lossy, "VP8 ");
        Assert.notNull(vp8, "fixture lossy.webp deveria conter chunk VP8");
        Assert.eq(vp8.length, r1.payload.length, "tamanho do payload VP8");
        Assert.isTrue(Arrays.equals(vp8, r1.payload), "payload VP8 deve bater com o chunk real");

        // lossy com alpha
        byte[] alpha = Files.readAllBytes(Paths.get(fixtures, "lossy_alpha.webp"));
        StaticWebpParser.Result r2 = StaticWebpParser.parse(alpha);
        Assert.eq(Boolean.FALSE, r2.lossless, "lossy_alpha.webp deve ser lossy");
        Assert.notNull(r2.alph, "lossy_alpha.webp deve ter ALPH");
        Assert.notNull(r2.payload, "payload VP8 não pode ser null");
        byte[] alphReal = chunkPayload(alpha, "ALPH");
        Assert.notNull(alphReal, "fixture lossy_alpha.webp deveria conter chunk ALPH");
        Assert.eq(alphReal.length, r2.alph.length, "tamanho do payload ALPH");
        Assert.isTrue(Arrays.equals(alphReal, r2.alph), "payload ALPH deve bater com o chunk real");

        System.out.println("TestStaticWebpParser: OK");
    }

    public static void main(String[] args) throws Exception {
        run(args[0]);
    }
}
