package ro.atf.j6.reporting;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AtfComponentTest
class NativeTournamentExtentPublisherTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String AUDITED_FAILURE = "A failure Extent report was created and structurally audited";

    @Test
    void completeSmokePassesAndEmbedsBothCaptures(@TempDir Path temp) throws Exception {
        var result = publish(temp, smokeReceipt());
        assertEquals(0, result.exitCode(), result.output());
        assertTrue(result.html().contains("All required assertions present once"));
    }

    @Test
    void emptyNativeAssertionsFailEvenWithCorrectApkAndCaptures(@TempDir Path temp) throws Exception {
        var receipt = smokeReceipt();
        receipt.putArray("assertions");

        assertAuditedFailure(publish(temp, receipt), "0 native assertions");
    }

    @Test
    void absentNativeAssertionsStillProduceAnAuditedFailure(@TempDir Path temp) throws Exception {
        var receipt = smokeReceipt();
        receipt.remove("assertions");

        assertAuditedFailure(publish(temp, receipt), "MISSING");
    }

    @Test
    void optionalChecksDoNotReplaceAMissingJourneyAssertion(@TempDir Path temp) throws Exception {
        var receipt = smokeReceipt();
        var assertions = receipt.withArray("assertions");
        assertions.remove(3);
        assertions.add(assertion("Tap Deal cards", true));

        assertAuditedFailure(publish(temp, receipt), "Missing: [Release APK opens tournament setup]");
    }

    @Test
    void duplicateJourneyAssertionFailsWithoutSuppressingEvidence(@TempDir Path temp) throws Exception {
        var receipt = smokeReceipt();
        receipt.withArray("assertions").add(assertion("Release APK opens hand setup", true));

        assertAuditedFailure(publish(temp, receipt), "repeated: [Release APK opens hand setup]");
    }

    @Test
    void validPngWithADifferentCheckpointNameDoesNotSatisfyTheInventory(@TempDir Path temp) throws Exception {
        var receipt = smokeReceipt();
        receipt.putArray("captures").add("01-release-hand-setup.png").add("02-other-checkpoint.png");

        assertAuditedFailure(publish(temp, receipt), "02-other-checkpoint.png");
    }

    @Test
    void tournamentRejectsSmokeAssertionsAsEvidenceOfGameplay(@TempDir Path temp) throws Exception {
        var receipt = smokeReceipt();
        receipt.put("journey", "tournament");
        receipt.putArray("captures");

        assertAuditedFailure(publish(temp, receipt), "Missing: [Opening hand, First hand pot is paid");
    }

    @Test
    void profileRenameAloneCannotPassTheProcessRestartJourney(@TempDir Path temp) throws Exception {
        var receipt = smokeReceipt().put("journey", "leaderboard-profile-restart");
        receipt.putArray("actions").add("Rename the profile");
        receipt.putArray("captures").add("01-profile-renamed.png");
        var assertions = receipt.putArray("assertions");
        for (String name : List.of("Original profile has one completed tournament and 100 points",
                "Tournament belongs to the stable profile", "Renamed profile is visible exactly once",
                "Rename leaves the recorded tournament unchanged")) {
            assertions.add(assertion(name, true));
        }

        assertAuditedFailure(publish(temp, receipt), "Missing: [Android process restarted");
    }

    @Test
    void seatMetricsRejectAUniformScreenshotEvenWithCompleteAssertions(@TempDir Path temp) throws Exception {
        var receipt = smokeReceipt().put("journey", "seat-metrics");
        receipt.putArray("captures").add("01-compact-seat-metrics.png").add("02-settled-seat-metrics.png");
        var assertions = receipt.putArray("assertions");
        for (String name : List.of("Compact viewport uses enlarged text", "Google TalkBack service is enabled",
                "Live seat amounts match the engine and fit", "Narrow seat facts use the stacked layout",
                "Active seat exposes one full accessibility description", "Active seat accepts Android accessibility focus",
                "Settled seat amounts match the engine and fit")) {
            assertions.add(assertion(name, true));
        }
        assertAuditedFailure(publish(temp, receipt, "01-compact-seat-metrics.png"), "uniform raster");
    }

    private static ObjectNode smokeReceipt() {
        var receipt = JSON.createObjectNode().put("journey", "release-smoke").put("status", "PASS")
                .put("error", "").put("api", 28).put("window", "392 × 626 dp").put("density", 320).put("fontScale", 1);
        receipt.putArray("actions").add("Open hand setup").add("Open tournament setup");
        receipt.putArray("captures").add("01-release-hand-setup.png").add("02-release-tournament-setup.png");
        var assertions = receipt.putArray("assertions");
        for (String name : List.of("Release APK opens the branded home menu", "Release APK opens hand setup",
                "Hand setup returns to the home menu", "Release APK opens tournament setup")) {
            assertions.add(assertion(name, true));
        }
        // The same navigation action can legitimately occur more than once.
        assertions.add(assertion("Tap Main menu", true)).add(assertion("Tap Main menu", true));
        return receipt;
    }

    private static ObjectNode assertion(String name, boolean passed) {
        return JSON.createObjectNode().put("name", name).put("expected", "observed").put("actual", "observed").put("passed", passed);
    }

    private static Result publish(Path temp, ObjectNode receipt) throws Exception {
        return publish(temp, receipt, "");
    }

    private static Result publish(Path temp, ObjectNode receipt, String blankCapture) throws Exception {
        Path apk = Files.writeString(temp.resolve("fixture.apk"), "native APK identity fixture");
        receipt.put("apkSha256", HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(apk))));
        Path evidence = Files.createDirectory(temp.resolve("evidence"));
        int color = 0xff123400;
        for (var capture : receipt.withArray("captures")) {
            var image = new BufferedImage(3, 3, BufferedImage.TYPE_INT_ARGB);
            if (!capture.asText().equals(blankCapture)) image.setRGB(1, 1, ++color);
            assertTrue(ImageIO.write(image, "png", evidence.resolve(capture.asText()).toFile()));
        }
        JSON.writeValue(evidence.resolve("native.json").toFile(), receipt);
        Path report = temp.resolve("extent.html");
        Path log = temp.resolve("publisher.log");
        String java = ProcessHandle.current().info().command().orElseThrow();
        var process = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
                publisherSource().toString(), report.toString(), evidence.toString(), apk.toString())
                .redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(30, TimeUnit.SECONDS), "Native Extent publisher timed out");
        } finally {
            if (process.isAlive()) process.destroyForcibly();
        }
        String output = Files.readString(log);
        assertTrue(Files.isRegularFile(report), output);
        String html = Files.readString(report);
        for (var capture : receipt.withArray("captures")) {
            String payload = Base64.getEncoder().encodeToString(Files.readAllBytes(evidence.resolve(capture.asText())));
            assertEquals(1, html.split(Pattern.quote(payload), -1).length - 1, capture.asText());
        }
        return new Result(process.exitValue(), output, html);
    }

    private static Path publisherSource() {
        for (Path path = Path.of("").toAbsolutePath(); path != null; path = path.getParent()) {
            Path source = path.resolve("holdem-android/tools/PublishTournamentExtent.java");
            if (Files.isRegularFile(source)) return source;
        }
        throw new IllegalStateException("Native Extent publisher is missing from the project tree");
    }

    private static void assertAuditedFailure(Result result, String diagnostic) {
        assertEquals(1, result.exitCode(), result.output());
        assertTrue(result.output().contains(AUDITED_FAILURE), result.output());
        assertTrue(result.html().contains(diagnostic), "Missing Extent diagnostic: " + diagnostic);
        assertTrue(result.html().contains("Installed APK matches the supplied build"));
    }

    private record Result(int exitCode, String output, String html) { }
}
