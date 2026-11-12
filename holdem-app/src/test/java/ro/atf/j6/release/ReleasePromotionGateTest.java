package ro.atf.j6.release;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AtfComponentTest
class ReleasePromotionGateTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String VERSION = "1.2.3";
    private static final String COMMIT = "a".repeat(40);
    private static final String APK = "HoldemLab-" + VERSION + ".apk";
    private static final byte[] PAYLOAD = {10, 20, 30, 40, 50};
    @TempDir Path tmp;
    private Path candidate;
    private Path qualification;
    private ObjectNode receipt;

    @BeforeEach void prepare() throws Exception {
        candidate = Files.createDirectory(tmp.resolve("candidate"));
        qualification = tmp.resolve("qualification.json");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(PAYLOAD));
        Files.write(candidate.resolve(APK), PAYLOAD);
        Files.writeString(candidate.resolve(APK + ".sha256"), hash + "  " + APK + "\n");
        receipt = JSON.createObjectNode().put("schemaVersion", 1).put("status", "PASS")
                .put("sourceCommit", COMMIT).put("versionName", VERSION).put("apkName", APK)
                .put("apkBytes", PAYLOAD.length).put("sha256", hash).put("certificateSha256", "b".repeat(64))
                .put("debuggable", false);
        receipt.putObject("qualification").putObject("releaseInstallSmoke").put("status", "PASS").put("apkSha256", hash);
    }

    @Test void acceptsTheExactStagedBytesAndChecksum() throws Exception {
        assertTrue(gate("exact"));
    }

    @Test void schemaTwoBindsTheNativePlanToTheQualifiedSource() throws Exception {
        receipt.put("schemaVersion", 2).put("validationScope", "FULL");
        assertFalse(gate("native-plan-missing"));
        Path planPath = tmp.resolve("native-plan.json");
        ObjectNode plan = JSON.createObjectNode().put("schemaVersion", 1).put("sourceCommit", COMMIT)
                .put("versionName", VERSION).put("validationScope", "FULL");
        plan.putArray("journeys").addObject().put("mode", "tournament");
        JSON.writeValue(planPath.toFile(), plan);
        var reference = receipt.putObject("nativePlan").put("path", "native-plan.json");
        reference.put("sha256", HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(planPath))));
        assertTrue(gate("native-plan-exact"));
        plan.put("sourceCommit", "b".repeat(40));
        JSON.writeValue(planPath.toFile(), plan);
        assertFalse(gate("native-plan-tampered"));
        reference.put("sha256", HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(planPath))));
        assertFalse(gate("native-plan-wrong-source"));
    }

    @Test void componentQualificationRequiresEachProfileOnTheExactApk() throws Exception {
        receipt.put("validationScope", "TABLE_GEOMETRY_COMPONENT");
        var geometry = ((ObjectNode) receipt.path("qualification")).putObject("tableGeometry");
        for (String profile : List.of("device", "short", "tall")) {
            geometry.putObject(profile).put("status", "PASS").put("assertions", 18)
                    .put("apkSha256", receipt.path("sha256").asText());
        }
        assertTrue(gate("component-exact"));
        ((ObjectNode) geometry.path("tall")).put("apkSha256", "c".repeat(64));
        assertFalse(gate("component-wrong-apk"));
        geometry.remove("tall");
        assertFalse(gate("component-missing-profile"));
    }

    @Test void refusesMissingAndTruncatedApk() throws Exception {
        Files.delete(candidate.resolve(APK));
        assertFalse(gate("missing-apk"));
        Files.write(candidate.resolve(APK), new byte[]{10, 20});
        assertFalse(gate("truncated-apk"));
    }

    @Test void refusesSameLengthMutationEvenWithAChecksumForTheModifiedBytes() throws Exception {
        var mutated = new byte[]{10, 20, 30, 40, 60};
        Files.write(candidate.resolve(APK), mutated);
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mutated));
        Files.writeString(candidate.resolve(APK + ".sha256"), hash + "  " + APK);
        assertFalse(gate("same-length-mutation"));
    }

    @Test void refusesMissingChecksumOrChecksumForAnotherVersion() throws Exception {
        Files.delete(candidate.resolve(APK + ".sha256"));
        assertFalse(gate("missing-checksum"));
        Files.writeString(candidate.resolve(APK + ".sha256"), receipt.path("sha256").asText() + "  HoldemLab-1.2.2.apk");
        assertFalse(gate("wrong-checksum"));
    }

    @Test void refusesQualificationForAnotherCommitOrSmokeOfAnotherApk() throws Exception {
        receipt.put("sourceCommit", "b".repeat(40));
        assertFalse(gate("wrong-commit"));
        receipt.put("sourceCommit", COMMIT);
        ((ObjectNode) receipt.path("qualification").path("releaseInstallSmoke")).put("apkSha256", "c".repeat(64));
        assertFalse(gate("wrong-smoke"));
    }

    @Test void refusesMissingAndUnreadableQualification() throws Exception {
        assertFalse(run("missing-qualification"));
        Files.writeString(qualification, "{broken");
        assertFalse(run("malformed-qualification"));
    }

    private boolean gate(String name) throws Exception {
        JSON.writeValue(qualification.toFile(), receipt);
        return run(name);
    }

    private boolean run(String name) throws Exception {
        Path report = tmp.resolve(name + ".html");
        boolean passed = ReleasePromotionGate.run(candidate, qualification, VERSION, COMMIT, report);
        assertTrue(Files.isRegularFile(report));
        return passed;
    }
}
