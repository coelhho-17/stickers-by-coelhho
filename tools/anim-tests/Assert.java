/** Helpers de asserção mínimos (sem dependências). */
public final class Assert {
    private Assert() {}

    public static void isTrue(boolean cond, String msg) {
        if (!cond) throw new AssertionError(msg);
    }

    public static void eq(Object expected, Object actual, String msg) {
        boolean same = (expected == null) ? (actual == null) : expected.equals(actual);
        if (!same) throw new AssertionError(msg + " — esperado=" + expected + " actual=" + actual);
    }

    public static void notNull(Object o, String msg) {
        if (o == null) throw new AssertionError(msg);
    }
}
