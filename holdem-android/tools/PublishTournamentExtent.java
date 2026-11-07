import atf.reporting.audit.AssertionAuditReport;
import atf.reporting.artifact.ReportArtifactInspector;
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
import org.w3c.dom.Element;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/** Publishes observed JVM and Android results using the admitted J0 Reporting JAR. */
class PublishTournamentExtent {
    public static void main(String[] args) throws Exception {
        if (args.length < 3) throw new IllegalArgumentException("Expected report, native evidence directory and app APK; JUnit XML(s) are optional");
        Path reportPath = Path.of(args[0]);
        Instant startedAt = Instant.now();
        Path evidence = Path.of(args[1]);
        var receipt = new ObjectMapper().readTree(evidence.resolve("native.json").toFile());
        boolean passed = true;
        try (var report = new ExtentReportSession(reportPath, new ReportingSettings(240, 0))) {
            for (String xml : Arrays.copyOfRange(args, 3, args.length)) {
                passed &= component(report, Path.of(xml));
            }
            passed &= nativeUi(report, evidence, receipt, Path.of(args[2]));
        }
        if (!passed) throw new IllegalStateException("Report contains a failed or incomplete scenario");
        var inspection = ReportArtifactInspector.inspectFresh(reportPath, startedAt);
        if (!inspection.isCompliant()) {
            throw new IllegalStateException("Published Extent HTML is not autonomous and structurally compliant: "
                    + inspection.violations());
        }
    }

    private static boolean component(ExtentReportSession report, Path path) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var xml = factory.newDocumentBuilder().parse(path.toFile());
        var tests = xml.getElementsByTagName("testcase");
        var checks = new ArrayList<AtfAssertion>();
        for (int index = 0; index < tests.getLength(); index++) {
            var result = (Element) tests.item(index);
            String actual = "JUnit passed";
            for (String type : List.of("failure", "error", "skipped")) {
                var details = result.getElementsByTagName(type);
                if (details.getLength() > 0) {
                    actual = "JUnit " + type + " · " + ((Element) details.item(0)).getAttribute("message");
                    break;
                }
            }
            checks.add(new AtfAssertion(result.getAttribute("name"), "JUnit passed", actual, actual.equals("JUnit passed")));
        }
        boolean passed = !checks.isEmpty() && checks.stream().allMatch(AtfAssertion::passed);
        try (var test = report.startScenario(xml.getDocumentElement().getAttribute("name"), "Android JVM component suite",
                List.of("component", "jvm"));
             var phase = test.startPhase("TEST BODY")) {
            AssertionAuditReport.publishStep(phase, "Recorded JUnit outcomes", checks, AtfReportStatus.INFO);
            phase.complete(status(passed));
            test.complete(status(passed));
        }
        return passed;
    }

    private static boolean nativeUi(ExtentReportSession report, Path evidence, JsonNode receipt, Path apk) throws Exception {
        String journey = receipt.path("journey").asText("tournament");
        boolean releaseSmoke = journey.equals("release-smoke");
        if (!releaseSmoke && !journey.equals("tournament")) throw new IllegalArgumentException("Unknown native journey " + journey);
        int expectedCaptures = releaseSmoke ? 2 : 5;
        var checks = new ArrayList<AtfAssertion>();
        for (var entry : receipt.required("assertions")) {
            checks.add(new AtfAssertion(entry.required("name").asText(), entry.required("expected").asText(),
                    entry.required("actual").asText(), entry.required("passed").asBoolean()));
        }
        var actions = new ArrayList<String>();
        for (var entry : receipt.required("actions")) actions.add(entry.asText());
        String apkHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(apk)));
        String error = receipt.required("error").asText();
        String installedApkHash = receipt.required("apkSha256").asText();
        checks.add(new AtfAssertion("Installed APK matches the supplied build", apkHash, installedApkHash, apkHash.equals(installedApkHash)));
        boolean passed = receipt.required("status").asText().equals("PASS") && !checks.isEmpty()
                && checks.stream().allMatch(AtfAssertion::passed) && receipt.required("captures").size() == expectedCaptures;
        String scenario = releaseSmoke ? "ReleaseInstallSmoke" : "TournamentSanity";
        String description = releaseSmoke ? "Installable release APK opens and navigates its entry screens"
                : "Three hands to victory and an opening all-in defeat";
        try (var test = UiExtentReportSession.adaptScenario(
                report.startScenario(scenario, description, List.of("e2e", "sanity", "android", releaseSmoke ? "release" : "tournament")),
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
            for (var entry : receipt.required("captures")) {
                String name = entry.asText();
                if (!name.matches("0[1-5]-[a-z-]+\\.png")) throw new IllegalArgumentException("Unexpected capture name " + name);
                var image = ImageIO.read(evidence.resolve(name).toFile());
                boolean decodes = image != null && image.getWidth() > 0 && image.getHeight() > 0;
                passed &= decodes;
                String dimensions = decodes ? image.getWidth() + " × " + image.getHeight() : "unreadable";
                phase.step(name.startsWith("01") ? AtfGherkinKeyword.GIVEN : AtfGherkinKeyword.AND, name,
                        "Android native UI", Base64.getEncoder().encodeToString(Files.readAllBytes(evidence.resolve(name))),
                        List.of(new AtfAssertion("Native screenshot decodes", "positive raster dimensions", dimensions, decodes)),
                        List.of(), ReportScreenshotPolicy.CapturePoint.CHECKPOINT, status(decodes));
            }
            phase.step(AtfGherkinKeyword.THEN, releaseSmoke ? "The signed release APK opens the expected app flow" : "The device follows the tournament contract",
                    "Android native UI", null, checks, status(passed));
            phase.complete(status(passed));
            test.complete(status(passed));
        }
        return passed;
    }

    private static AtfReportStatus status(boolean passed) {
        return passed ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
    }
}
