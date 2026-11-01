/** Runner dos testes (host). Uso: java TestMain <dir-fixtures> */
public final class TestMain {
    public static void main(String[] args) {
        String fixtures = args.length > 0 ? args[0] : ".";
        int failures = 0;
        failures += runOne("StaticWebpParser", () -> TestStaticWebpParser.run(fixtures));
        failures += runOne("AnimatedWebpMuxer", () -> TestAnimatedWebpMuxer.run(fixtures));
        failures += runOne("GifDecoder", () -> TestGifDecoder.run(fixtures));
        if (failures > 0) {
            System.err.println(failures + " suíte(s) FALHARAM");
            System.exit(1);
        }
        System.out.println("TODOS OS TESTES PASSARAM");
    }

    private interface Check { void run() throws Exception; }

    private static int runOne(String name, Check c) {
        try {
            c.run();
            return 0;
        } catch (Throwable t) {
            System.err.println("[" + name + "] FALHOU: " + t);
            t.printStackTrace(System.err);
            return 1;
        }
    }
}
