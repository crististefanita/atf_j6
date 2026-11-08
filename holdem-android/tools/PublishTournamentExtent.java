import atf.reporting.artifact.ReportArtifactInspector;
import atf.reporting.artifact.ReportArtifactProfile;
import atf.reporting.configuration.ReportingSettings;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfGherkinKeyword;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.evidence.ReportScreenshotPolicy;
import atf.reporting.evidence.profile.ReportEvidenceProfile;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.model.ReportField;
import atf.reporting.model.ReportObservation;
import atf.reporting.ui.UiExtentReportSession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;

/** Publishes observed native Android E2E results using the admitted J0 Reporting JAR. */
class PublishTournamentExtent {
    private static final List<String> TOURNAMENT_ASSERTIONS = List.of("Opening hand", "First hand pot is paid",
            "Raise opens the amount editor", "Call remains accessible after closing Raise",
            "Opt-in shows the human win chance", "Unchecked setup leaves the win chance hidden",
            "Third hand starts heads-up", "Winner sees congratulations",
            "Losing player sees the elimination result");
    private static final List<String> RELEASE_SMOKE_ASSERTIONS = List.of("Release APK opens the branded home menu",
            "Release APK opens hand setup", "Hand setup returns to the home menu", "Release APK opens tournament setup");
    private static final List<String> TOURNAMENT_CAPTURES = List.of(
            "01b-opening-preflop.png", "01-call-ledger-visible.png", "01-betting-ledger-history.png", "01-hand-one-settled.png",
            "01a-settlement-retained.png", "02-two-seats-eliminated.png", "03-heads-up.png",
            "04-tournament-won.png", "05-tournament-lost.png");
    private static final List<String> RELEASE_SMOKE_CAPTURES = List.of(
            "01-release-hand-setup.png", "02-release-tournament-setup.png");
    private static final List<String> SETTLEMENT_ASSERTIONS = List.of(
            "Next hand tap accepted during settlement", "Activity recreation opens exactly hand two", "Next hand persisted once",
            "Result tap accepted during settlement", "Result persisted before reopening",
            "Fresh Activity opens the saved result directly", "Repeated result display records one tournament");
    private static final List<String> SETTLEMENT_CAPTURES = List.of("01-next-hand-restored.png", "02-result-restored.png");
    private static final List<String> LEADERBOARD_ASSERTIONS = List.of(
            "Result screen has not opened", "Settled tournament is already recorded", "New tournament replaced the settled checkpoint",
            "Leaderboard keeps the unopened result once", "Reopened app keeps the original tournament ID once");
    private static final List<String> PROFILE_RESTART_ASSERTIONS = List.of(
            "Original profile has one completed tournament and 100 points", "Tournament belongs to the stable profile",
            "Renamed profile is visible exactly once", "Rename leaves the recorded tournament unchanged",
            "Android process restarted", "Restart shows the renamed profile exactly once",
            "Restart preserves the stable profile ID", "Restart preserves the recorded points and tournament IDs");

    private record Journey(String scenario, String description, String conclusion,
                           List<String> assertions, List<String> captures) { }

    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("Expected report, native evidence directory and app APK");
        Path reportPath = Path.of(args[0]);
        Instant startedAt = Instant.now();
        Path evidence = Path.of(args[1]);
        var receipt = new ObjectMapper().readTree(evidence.resolve("native.json").toFile());
        var scenarios = new ArrayList<ReportArtifactProfile.Scenario>();
        var screenshots = new ArrayList<Path>();
        for (var capture : receipt.required("captures")) screenshots.add(evidence.resolve(capture.asText()));
        boolean passed = true;
        try (var report = new ExtentReportSession(reportPath, new ReportingSettings(240, 0))) {
            var nativeScenario = nativeUi(report, evidence, receipt, Path.of(args[2]));
            scenarios.add(nativeScenario);
            passed &= nativeScenario.status() == AtfReportStatus.PASS;
        }
        String html = Files.readString(reportPath);
        for (Path screenshot : screenshots) {
            String payload = Base64.getEncoder().encodeToString(Files.readAllBytes(screenshot));
            if (occurrences(html, payload) != 1) {
                throw new IllegalStateException("Extent must embed the exact native screenshot once: " + screenshot.getFileName());
            }
        }
        var profile = new ReportArtifactProfile(ReportArtifactProfile.Renderer.EXTENT, scenarios);
        var inspection = ReportArtifactInspector.inspectFresh(reportPath, startedAt, profile);
        if (!inspection.isCompliant()) {
            throw new IllegalStateException("Published Extent HTML is not autonomous and structurally compliant: "
                    + inspection.violations());
        }
        if (!passed) throw new IllegalStateException("A failure Extent report was created and structurally audited; native scenario status is FAIL.");
    }

    private static ReportArtifactProfile.Scenario nativeUi(ExtentReportSession report, Path evidence, JsonNode receipt, Path apk) throws Exception {
        String journey = receipt.path("journey").asText("tournament");
        var contract = switch (journey) {
            case "release-smoke" -> new Journey("ReleaseInstallSmoke", "Installable release APK opens and navigates its entry screens",
                    "The signed release APK opens the expected app flow", RELEASE_SMOKE_ASSERTIONS, RELEASE_SMOKE_CAPTURES);
            case "tournament" -> new Journey("TournamentSanity", "Three hands to victory and an opening all-in defeat",
                    "The device follows the tournament contract", TOURNAMENT_ASSERTIONS, TOURNAMENT_CAPTURES);
            case "settlement-resume" -> new Journey("SettlementResumeSanity", "Continue from a settled table during Activity interruption",
                    "The accepted continuation survives once", SETTLEMENT_ASSERTIONS, SETTLEMENT_CAPTURES);
            case "leaderboard-settlement" -> new Journey("LeaderboardSettlementSanity", "Start another tournament from a settled table",
                    "The unopened result stays in the leaderboard once", LEADERBOARD_ASSERTIONS, List.of("01-leaderboard-after-new-game.png"));
            case "leaderboard-profile-restart" -> new Journey("LeaderboardProfileRestartSanity", "Rename the ranked player and restart the Android process",
                    "The current name is visible once with the original ID and points", PROFILE_RESTART_ASSERTIONS,
                    List.of("01-profile-renamed.png", "02-profile-after-restart.png"));
            case "seat-metrics" -> new Journey("CompactSeatMetricsSanity", "Read six player seats at enlarged Android font scale",
                    "Each seat preserves its visible and accessible amounts", List.of("Compact viewport uses enlarged text",
                            "Google TalkBack service is enabled",
                            "Live seat amounts match the engine and fit", "Narrow seat facts use the stacked layout",
                            "Active seat exposes one full accessibility description", "Active seat accepts Android accessibility focus",
                            "Settled seat amounts match the engine and fit"),
                    List.of("01-compact-seat-metrics.png", "02-settled-seat-metrics.png"));
            default -> throw new IllegalArgumentException("Unknown native journey " + journey);
        };
        var checks = nativeChecks(receipt, contract.assertions());
        var captureNames = new ArrayList<String>();
        for (var entry : receipt.required("captures")) captureNames.add(entry.asText());
        var expectedCaptures = contract.captures();
        checks.add(new AtfAssertion("Native capture inventory matches the journey", String.join(", ", expectedCaptures),
                String.join(", ", captureNames), expectedCaptures.equals(captureNames)));
        var actions = new ArrayList<String>();
        for (var entry : receipt.required("actions")) actions.add(entry.asText());
        String apkHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(apk)));
        String error = receipt.required("error").asText();
        String installedApkHash = receipt.required("apkSha256").asText();
        checks.add(new AtfAssertion("Installed APK matches the supplied build", apkHash, installedApkHash, apkHash.equals(installedApkHash)));
        boolean passed = receipt.required("status").asText().equals("PASS")
                && checks.stream().allMatch(AtfAssertion::passed);
        var result = status(passed);
        var checkpointEvidence = new ArrayList<ReportArtifactProfile.Evidence>();
        String scenario = contract.scenario();
        String description = contract.description();
        try (var test = UiExtentReportSession.adaptScenario(
                report.startScenario(scenario, description, List.of("e2e", "sanity", "android", journey.equals("release-smoke") ? "release" : journey)),
                ReportEvidenceProfile.forScreenshotPolicy(ReportScreenshotPolicy.EACH_CHECKPOINT), "Android native PNG");
             var phase = test.startPhase("TEST BODY")) {
            phase.observation(new ReportObservation("Android native evidence", List.of(
                    new ReportField("Android API", receipt.required("api").asText()),
                    new ReportField("Window", receipt.required("window").asText()),
                    new ReportField("Density (dpi)", receipt.required("density").asText()),
                    new ReportField("Font scale", receipt.required("fontScale").asText()),
                    new ReportField("Installed APK SHA-256", installedApkHash),
                    new ReportField("Execution failure", error.isEmpty() ? "none" : error)),
                    List.of(new ReportObservation.Body("Accessibility actions in order", String.join("\n", actions),
                            ReportObservation.Format.TEXT)), List.of()));
            checkpointEvidence.add(new ReportArtifactProfile.Observation("Android native evidence",
                    List.of("Android API", "Window", "Density (dpi)", "Font scale", "Installed APK SHA-256", "Execution failure"),
                    List.of("Accessibility actions in order")));
            int captureIndex = 0;
            var unusableCaptures = new ArrayList<String>();
            for (String name : captureNames) {
                if (!name.matches("0[1-5][a-z]?-[a-z-]+\\.png")) throw new IllegalArgumentException("Unexpected capture name " + name);
                var image = ImageIO.read(evidence.resolve(name).toFile());
                boolean decodes = image != null && image.getWidth() > 0 && image.getHeight() > 0;
                String dimensions = decodes ? image.getWidth() + " × " + image.getHeight() : "unreadable";
                boolean seatMetrics = journey.equals("seat-metrics");
                boolean usable = decodes && (!seatMetrics || hasImageDetail(image));
                passed &= usable;
                if (!usable) unusableCaptures.add(name);
                String checkName = seatMetrics ? "Native screenshot shows a rendered frame" : "Native screenshot decodes";
                String expected = seatMetrics ? "non-uniform raster" : "positive raster dimensions";
                String observed = decodes && !usable ? "uniform raster · " + dimensions : dimensions;
                var keyword = captureIndex++ == 0 ? AtfGherkinKeyword.GIVEN : AtfGherkinKeyword.AND;
                phase.step(keyword, name,
                        "Android native UI", Base64.getEncoder().encodeToString(Files.readAllBytes(evidence.resolve(name))),
                        List.of(new AtfAssertion(checkName, expected, observed, usable)),
                        List.of(), ReportScreenshotPolicy.CapturePoint.CHECKPOINT, status(usable));
                String checkpoint = keyword.name() + " " + name;
                checkpointEvidence.add(nestedValidation(checkpoint,
                        new ReportArtifactProfile.Assertions("UI validation", List.of(
                                new ReportArtifactProfile.Comparison(checkName, usable))), status(usable)));
            }
            if (!unusableCaptures.isEmpty())
                checks.add(new AtfAssertion("Every native screenshot is usable", "no unusable captures",
                        String.join(", ", unusableCaptures), false));
            String conclusion = contract.conclusion();
            phase.step(AtfGherkinKeyword.THEN, conclusion,
                    "Android native UI", null, checks, status(passed));
            var comparisons = checks.stream().map(check -> new ReportArtifactProfile.Comparison(check.name(), check.passed())).toList();
            checkpointEvidence.add(nestedValidation("THEN " + conclusion,
                    new ReportArtifactProfile.Assertions("UI validation", comparisons), status(passed)));
            result = status(passed);
            phase.complete(result);
            test.complete(result);
        }
        return new ReportArtifactProfile.Scenario(scenario, description, result,
                List.of(new ReportArtifactProfile.Phase("TEST BODY", result, checkpointEvidence)));
    }

    private static boolean hasImageDetail(java.awt.image.BufferedImage image) {
        int first = image.getRGB(0, 0);
        for (int y = 1; y < 8; y++) {
            for (int x = 1; x < 8; x++) {
                if (image.getRGB(x * (image.getWidth() - 1) / 8, y * (image.getHeight() - 1) / 8) != first) return true;
            }
        }
        return false;
    }

    private static List<AtfAssertion> nativeChecks(JsonNode receipt, List<String> requiredAssertions) {
        var checks = new ArrayList<AtfAssertion>();
        var assertions = receipt.path("assertions");
        var counts = new HashMap<String, Integer>();
        if (assertions.isArray()) {
            for (var entry : assertions) {
                String name = entry.required("name").asText();
                counts.merge(name, 1, Integer::sum);
                checks.add(new AtfAssertion(name, entry.required("expected").asText(),
                        entry.required("actual").asText(), entry.required("passed").booleanValue()));
            }
        }
        checks.add(new AtfAssertion("Native journey assertions are present", "Non-empty assertions array",
                assertions.isArray() ? assertions.size() + " native assertions" : assertions.getNodeType().name(),
                assertions.isArray() && !assertions.isEmpty()));
        // Repeated tap labels are legitimate. Only the required journey observations must be unique.
        var missing = new ArrayList<String>();
        var repeated = new ArrayList<String>();
        for (String name : requiredAssertions) {
            int occurrences = counts.getOrDefault(name, 0);
            if (occurrences == 0) missing.add(name);
            if (occurrences > 1) repeated.add(name);
        }
        boolean complete = missing.isEmpty() && repeated.isEmpty();
        checks.add(new AtfAssertion("Required native journey assertions occur once", String.join(", ", requiredAssertions),
                complete ? "All required assertions present once" : "Missing: " + missing + "; repeated: " + repeated, complete));
        return checks;
    }

    /**
     * J23 profiles have no generic nested checkpoint type. Optional HTTP fields are deliberately
     * disabled here; this carrier records only the nested assertion table and never invents an exchange.
     */
    private static ReportArtifactProfile.HttpExchange nestedValidation(String title,
            ReportArtifactProfile.Assertions validation, AtfReportStatus status) {
        return new ReportArtifactProfile.HttpExchange(title, false, false, List.of(), List.of(validation), status);
    }

    private static AtfReportStatus status(boolean passed) {
        return passed ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
    }

    private static int occurrences(String text, String value) {
        int matches = 0;
        for (int offset = 0; (offset = text.indexOf(value, offset)) >= 0; offset += value.length()) matches++;
        return matches;
    }
}
