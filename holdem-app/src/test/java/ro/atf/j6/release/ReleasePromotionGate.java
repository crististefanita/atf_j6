package ro.atf.j6.release;

import atf.reporting.contract.AtfAssertion;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** Verifies the exact staged package against an independently supplied qualification receipt. */
public final class ReleasePromotionGate {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String SHA256 = "[0-9a-f]{64}";
    private static final String GATE = "J6ReleasePromotionGate";
    private static final String SCENARIO = "Promote the qualified APK and its checksum";

    private ReleasePromotionGate() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new IllegalArgumentException("Expected: <candidate-dir> <qualification.json> <version> <source-commit> <report.html>");
        }
        if (!run(Path.of(args[0]), Path.of(args[1]), args[2], args[3], Path.of(args[4]))) {
            System.err.println("Promotion blocked. See the Extent report: " + args[4]);
            System.exit(1);
        }
    }

    static boolean run(Path candidate, Path qualification, String version, String commit, Path report) throws Exception {
        Path output = report.toAbsolutePath().normalize();
        if (!output.getFileName().toString().endsWith(".html") || Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Promotion requires a fresh Extent .html report path.");
        }
        var checks = new ArrayList<AtfAssertion>();
        try {
            verify(candidate, qualification, version, commit, checks);
        } catch (IOException unavailable) {
            compare(checks, "Qualification and candidate can be read", "readable files", unavailable.getMessage());
        }
        Files.createDirectories(output.getParent());
        ReleaseAuditExtent.write(output, GATE, SCENARIO, checks);
        return !checks.isEmpty() && checks.stream().allMatch(AtfAssertion::passed);
    }

    private static void verify(Path candidate, Path qualification, String version, String commit,
                               List<AtfAssertion> checks) throws Exception {
        if (!version.matches("[0-9]+\\.[0-9]+\\.[0-9]+") || !commit.matches("[0-9a-f]{40}")) {
            compare(checks, "Pinned version and source commit", "valid release version and Git commit", "invalid");
            return;
        }
        if (!Files.isRegularFile(qualification, LinkOption.NOFOLLOW_LINKS)) {
            compare(checks, "Qualification receipt", "regular qualification JSON", "missing or indirect");
            return;
        }
        JsonNode receipt = JSON.readTree(qualification.toFile());
        if (receipt == null || !receipt.isObject()) {
            compare(checks, "Qualification receipt", "JSON object", "missing or invalid");
            return;
        }
        String apkName = "HoldemLab-" + version + ".apk";
        compare(checks, "Qualification schema", "1", receipt.path("schemaVersion").asText());
        compare(checks, "Qualification status", "PASS", receipt.path("status").asText());
        compare(checks, "Qualified version", version, receipt.path("versionName").asText());
        compare(checks, "Qualified source commit", commit, receipt.path("sourceCommit").asText());
        compare(checks, "Qualified APK name", apkName, receipt.path("apkName").asText());
        compare(checks, "Release build is not debuggable", "false",
                receipt.path("debuggable").isBoolean() ? receipt.path("debuggable").asText() : "missing or invalid");

        JsonNode size = receipt.path("apkBytes");
        long expectedBytes = size.isIntegralNumber() && size.canConvertToLong() ? size.asLong() : -1;
        String expectedHash = receipt.path("sha256").asText();
        compare(checks, "Qualification records positive APK bytes", "positive", expectedBytes > 0 ? "positive" : "invalid");
        compare(checks, "Qualification records APK SHA-256", "valid", expectedHash.matches(SHA256) ? "valid" : "invalid");
        compare(checks, "Qualification records signing certificate SHA-256", "valid",
                receipt.path("certificateSha256").asText().matches(SHA256) ? "valid" : "invalid");
        JsonNode qualificationDetails = receipt.path("qualification");
        String scope = receipt.path("validationScope").asText("FULL");
        if (scope.equals("TABLE_GEOMETRY_COMPONENT")) {
            var geometry = qualificationDetails.path("tableGeometry");
            compare(checks, "Component qualification contains three display profiles", "3", Integer.toString(geometry.size()));
            for (String profile : List.of("device", "short", "tall")) {
                var proof = geometry.path(profile);
                compare(checks, profile + " geometry status", "PASS", proof.path("status").asText());
                compare(checks, profile + " geometry used the qualified APK", expectedHash, proof.path("apkSha256").asText());
                compare(checks, profile + " geometry assertions", "18", proof.path("assertions").asText());
            }
        } else {
            compare(checks, "Qualification scope", "FULL", scope);
            JsonNode smoke = qualificationDetails.path("releaseInstallSmoke");
            compare(checks, "Release installation smoke status", "PASS", smoke.path("status").asText());
            compare(checks, "Release installation smoke used the qualified APK", expectedHash, smoke.path("apkSha256").asText());
        }

        Path apk = candidate.resolve(apkName);
        boolean apkPresent = Files.isRegularFile(apk, LinkOption.NOFOLLOW_LINKS);
        compare(checks, "Candidate APK file", "regular APK file", apkPresent ? "regular APK file" : "missing or indirect");
        if (apkPresent) {
            var digest = MessageDigest.getInstance("SHA-256");
            long actualBytes;
            try (var stream = new DigestInputStream(Files.newInputStream(apk, LinkOption.NOFOLLOW_LINKS), digest)) {
                actualBytes = stream.transferTo(OutputStream.nullOutputStream());
            }
            compare(checks, "Candidate APK byte length", Long.toString(expectedBytes), Long.toString(actualBytes));
            compare(checks, "Candidate APK SHA-256", expectedHash, HexFormat.of().formatHex(digest.digest()));
        }
        Path checksum = candidate.resolve(apkName + ".sha256");
        boolean checksumPresent = Files.isRegularFile(checksum, LinkOption.NOFOLLOW_LINKS);
        compare(checks, "Candidate checksum file", "regular checksum file",
                checksumPresent ? "regular checksum file" : "missing or indirect");
        if (checksumPresent) {
            compare(checks, "Candidate checksum identifies the qualified APK", expectedHash + "  " + apkName,
                    Files.size(checksum) <= 256 ? Files.readString(checksum).strip() : "checksum exceeds 256 bytes");
        }
    }

    private static void compare(List<AtfAssertion> checks, String name, String expected, String actual) {
        checks.add(new AtfAssertion(name, expected, actual, expected.equals(actual)));
    }
}
