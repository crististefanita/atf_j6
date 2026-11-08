package ro.atf.j6.release;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Single release-version mapping used by the DRAFT qualification runner. */
public final class ReleaseVersionPolicy {
    private static final Pattern VERSION = Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$");

    private ReleaseVersionPolicy() { }

    public enum ChangeClass { PATCH_FIX, REFACTOR_ONLY, COMPATIBLE_FEATURE, BREAKING_CHANGE }

    public static String expected(String previousVersion, String changeClass) {
        int[] previous = parse(previousVersion);
        ChangeClass change = ChangeClass.valueOf(changeClass.toUpperCase(Locale.ROOT));
        return switch (change) {
            case PATCH_FIX -> previous[0] + "." + previous[1] + "." + Math.addExact(previous[2], 1);
            case REFACTOR_ONLY, COMPATIBLE_FEATURE -> previous[0] + "." + Math.addExact(previous[1], 1) + ".0";
            case BREAKING_CHANGE -> Math.addExact(previous[0], 1) + ".0.0";
        };
    }

    public static void requireExpected(String previousVersion, String changeClass, String candidateVersion) {
        String expected = expected(previousVersion, changeClass);
        if (!expected.equals(candidateVersion)) {
            throw new IllegalArgumentException("Change class " + changeClass + " from " + previousVersion
                    + " requires candidate " + expected + ", got " + candidateVersion);
        }
    }

    public static void main(String[] args) {
        if (args.length != 3) throw new IllegalArgumentException("Expected: <previous-version> <change-class> <candidate-version>");
        requireExpected(args[0], args[1], args[2]);
        System.out.println("Expected candidate version: " + args[2]);
    }

    private static int[] parse(String version) {
        Matcher matcher = VERSION.matcher(version == null ? "" : version);
        if (!matcher.matches()) throw new IllegalArgumentException("Invalid stable semantic version: " + version);
        try {
            return new int[]{Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3))};
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Semantic version component is outside the supported range: " + version, error);
        }
    }
}
