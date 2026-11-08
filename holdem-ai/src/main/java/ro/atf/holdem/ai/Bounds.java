package ro.atf.holdem.ai;

/** Numeric bounds shared with the Android Java 17 runtime. */
final class Bounds {
    private Bounds() {}

    static int clamp(int value, int minimum, int maximum) {
        if (minimum > maximum) throw new IllegalArgumentException("minimum exceeds maximum");
        return Math.max(minimum, Math.min(maximum, value));
    }

    static long clamp(long value, long minimum, long maximum) {
        if (minimum > maximum) throw new IllegalArgumentException("minimum exceeds maximum");
        return Math.max(minimum, Math.min(maximum, value));
    }

    static double clamp(double value, double minimum, double maximum) {
        if (minimum > maximum) throw new IllegalArgumentException("minimum exceeds maximum");
        return Math.max(minimum, Math.min(maximum, value));
    }
}
