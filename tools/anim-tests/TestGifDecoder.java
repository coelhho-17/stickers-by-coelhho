import com.coelhho.sticker.GifDecoder;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

/** Testes do GifDecoder usando o fixture test.gif (2s, 5fps, 64x64). */
public final class TestGifDecoder {

    public static void run(String fixtures) throws Exception {
        byte[] gif = Files.readAllBytes(Paths.get(fixtures, "test.gif"));
        List<GifDecoder.Frame> frames = GifDecoder.decode(gif);

        Assert.notNull(frames, "decode não deve retornar null");
        Assert.eq(10, frames.size(), "test.gif deve ter 10 frames");

        for (GifDecoder.Frame f : frames) {
            Assert.eq(64, f.width, "largura do frame");
            Assert.eq(64, f.height, "altura do frame");
            Assert.notNull(f.argb, "pixels não podem ser null");
            Assert.eq(64 * 64, f.argb.length, "tamanho do array de pixels");
            Assert.eq(200, f.delayMs, "delay deve ser 200ms (5fps)");
        }

        // frames devem diferir entre si (animação real, não o mesmo quadro)
        Assert.isTrue(!Arrays.equals(frames.get(0).argb, frames.get(5).argb),
                "frame 0 e frame 5 devem ter pixels diferentes");

        // frame 0 deve ter pixels opacos (testsrc não é transparente)
        boolean hasOpaque = false;
        for (int p : frames.get(0).argb) {
            if ((p >>> 24) != 0) { hasOpaque = true; break; }
        }
        Assert.isTrue(hasOpaque, "frame 0 deve ter pixels opacos");

        System.out.println("TestGifDecoder: OK");
    }

    public static void main(String[] args) throws Exception {
        run(args[0]);
    }
}
