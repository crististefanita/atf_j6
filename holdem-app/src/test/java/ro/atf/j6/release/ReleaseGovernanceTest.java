package ro.atf.j6.release;

import atf.reporting.audit.AssertionAuditRun;
import atf.reporting.audit.AssertionAuditReport;
import atf.reporting.configuration.ReportingSettings;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.artifact.ReportArtifactInspector;
import atf.reporting.artifact.ReportArtifactProfile;
import atf.reporting.model.timing.ReportPhaseTiming;
import atf.reporting.security.SensitiveDataRedactor;
import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Offline release governance: version identity, state transitions, and byte-preserving evidence. */
@AtfComponentTest
class ReleaseGovernanceTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test void currentReleaseStateMatchesSourceBinaryPolicyAndMainNavigation() throws Exception {
        Path root = projectRoot();
        var checks = new ArrayList<AtfAssertion>();
        Throwable failure = null;
        try {
            verify(root, checks);
        } catch (Throwable problem) {
            failure = problem;
            checks.add(new AtfAssertion("Release metadata can be read", "valid source and release metadata",
                    problem.getClass().getSimpleName() + ": " + problem.getMessage(), false));
        }
        publish(root, checks);
        assertTrue(failure == null && !checks.isEmpty() && checks.stream().allMatch(AtfAssertion::passed),
                () -> checks.stream().filter(check -> !check.passed()).map(check -> check.name() + " expected "
                        + check.expected() + " actual " + check.actual()).toList().toString());
    }

    @Test void publicDownloadIsGatedByPublicationAndMustMatchTheCurrentVersion() {
        String page = "https://github.com/crististefanita/atf_j6/releases/tag/v0.1.0";
        String apk = "https://github.com/crististefanita/atf_j6/releases/download/v0.1.0/HoldemLab-0.1.0.apk";
        String publishedGuide = "## Android\n- [Release](" + page + ")\n- [APK](" + apk + ")\n";
        assertTrue(routePolicy("DRAFT", "## Android", "0.1.0"), "DRAFT has no public download route");
        assertTrue(routePolicy("PREPARED", "## Android\n- [Candidate](release/0.1.0/README.md)", "0.1.0"),
                "PREPARED remains local");
        assertTrue(routePolicy("PUBLISHED", publishedGuide, "0.1.0"), "Exact current release and APK are accepted");
        assertFalse(routePolicy("DRAFT", publishedGuide, "0.1.0"), "A DRAFT cannot expose an APK");
        assertFalse(routePolicy("PUBLISHED", publishedGuide.replace("0.1.0", "0.0.9"), "0.1.0"),
                "An older version is rejected");
        assertFalse(routePolicy("PUBLISHED", publishedGuide + "- [Again](" + apk + ")", "0.1.0"),
                "A duplicate direct asset link is rejected");
        assertFalse(routePolicy("PUBLISHED", publishedGuide.replace(apk, "https://raw.githubusercontent.com/crististefanita/atf_j6/main/release/0.1.0/HoldemLab-0.1.0.apk"), "0.1.0"),
                "Raw source links are not release assets");
        assertFalse(routePolicy("PUBLISHED", publishedGuide.replace(apk, "release/0.1.0/HoldemLab-0.1.0.apk"), "0.1.0"),
                "A folder path is not a public asset");
    }

    private void verify(Path root, List<AtfAssertion> checks) throws Exception {
        JsonNode index = JSON.readTree(root.resolve("release/index.json").toFile());
        String version = index.path("currentVersion").asText();
        String state = index.path("state").asText();
        String packageName = "HoldemLab-" + version + ".apk";
        Path folder = root.resolve("release").resolve(version);
        JsonNode validation = JSON.readTree(folder.resolve("release-validation.json").toFile());
        JsonNode identity = validation.path("release");
        JsonNode publication = validation.path("publication");
        String mainReadme = Files.readString(root.resolve("README.md"));
        String apkUrl = "https://github.com/crististefanita/atf_j6/releases/download/v" + version + "/" + packageName;
        String releaseUrl = "https://github.com/crististefanita/atf_j6/releases/tag/v" + version;
        boolean published = state.equals("PUBLISHED");

        boolean oneCurrentVersion = index.path("versions").size() == 1 && version.equals(index.path("versions").get(0).asText());
        add(checks, "One current version", "exactly one " + version,
                oneCurrentVersion ? "exactly one " + version : "duplicate or mismatched version");
        boolean allowedState = List.of("DRAFT", "PREPARED", "PUBLISHED").contains(state);
        add(checks, "Allowed release lifecycle state", allowedState ? "allowed " + state : "unknown",
                allowedState ? "allowed " + state : "unknown");
        add(checks, "Index and validation state", state, publication.path("state").asText());
        add(checks, "APK filename follows the product version", packageName, identity.path("apkName").asText());
        add(checks, "Android Gradle version matches the release", version,
                gradleVersion(Files.readString(root.resolve("holdem-android/app/build.gradle"))));
        String androidBuild = Files.readString(root.resolve("holdem-android/app/build.gradle"));
        add(checks, "Runtime language resources cannot split from the APK", "bundle language split disabled",
                androidBuild.contains("enableSplit = false") ? "bundle language split disabled" : "language split may be enabled");
        add(checks, "Release root avoids Raw and folder downloads", "no raw or source-folder URL",
                mainReadme.contains("raw.githubusercontent.com") || mainReadme.contains("release/" + version + "/" + packageName)
                        ? "unqualified route present" : "no raw or source-folder URL");
        add(checks, "README has current release page", published ? releaseUrl : "no public route",
                published && mainReadme.contains(releaseUrl) ? releaseUrl
                        : !published && !mainReadme.contains("github.com/crististefanita/atf_j6/releases/") ? "no public route" : "wrong route");
        add(checks, "README has direct APK only after publication", published ? apkUrl : "no public APK link",
                published && mainReadme.contains(apkUrl) ? apkUrl
                        : !published && !mainReadme.contains("releases/download/") ? "no public APK link" : "wrong route");
        add(checks, "Git preserves release bytes", "release/** -text",
                Files.readString(root.resolve(".gitattributes")).contains("/release/** -text") ? "release/** -text" : "missing");
        String ignore = Files.readString(root.resolve(".gitignore"));
        add(checks, "APK and checksum stay out of Git history", "release APK and checksum ignored",
                ignore.contains("/release/**/*.apk") && ignore.contains("/release/**/*.apk.sha256")
                        ? "release APK and checksum ignored" : "ignore rule missing");
        for (String required : List.of("README.md", "release-notes.md", "release-validation.json", "reports/README.md")) {
            add(checks, "Version folder contains " + required, "file exists",
                    Files.isRegularFile(folder.resolve(required)) ? "file exists" : "file missing");
        }

        Path apk = folder.resolve(packageName);
        if (state.equals("DRAFT")) {
            add(checks, "DRAFT contains no APK", "absent", Files.exists(apk) ? "present" : "absent");
            add(checks, "DRAFT claims no APK bytes", "null", identity.path("apkBytes").isNull()
                    && identity.path("sha256").isNull() ? "null" : "binary identity claimed");
            add(checks, "DRAFT has no download pointer", "no direct APK link",
                    Files.readString(folder.resolve("README.md")).contains(".apk)") ? "direct link present" : "no direct APK link");
        } else {
            byte[] bytes = Files.readAllBytes(apk);
            String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            add(checks, "Prepared APK has bytes", "positive size", bytes.length > 0 ? Integer.toString(bytes.length) : "empty");
            add(checks, "Manifest APK size matches", Integer.toString(bytes.length), Long.toString(identity.path("apkBytes").asLong(-1)));
            add(checks, "Manifest APK SHA-256 matches", digest, identity.path("sha256").asText());
            add(checks, "Release APK is not debuggable", "false", identity.path("debuggable").asText());
            String sum = Files.readString(folder.resolve(packageName + ".sha256")).trim();
            add(checks, "Checksum file identifies this APK", digest + "  " + packageName, sum);
        }
        if (published) {
            JsonNode download = validation.path("publicDownload");
            add(checks, "Public download was checked successfully", "200 and exact APK identity",
                    download.path("httpStatus").asInt() == 200
                            && download.path("sha256").asText().equals(identity.path("sha256").asText())
                            && download.path("bytes").asLong() == identity.path("apkBytes").asLong()
                            ? "200 and exact APK identity" : "unverified or mismatched download");
        }
    }

    private void publish(Path root, List<AtfAssertion> checks) throws Exception {
        Path output = Path.of(System.getProperty("atf.test.reportDirectory", "target/atf-reports"));
        Files.createDirectories(output);
        Path reportPath = output.resolve("j6-release-governance-" + System.currentTimeMillis() + ".html");
        AtfReportStatus status = checks.stream().allMatch(AtfAssertion::passed) ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
        var settings = ReportingSettings.defaults();
        var redactor = new SensitiveDataRedactor();
        try (var run = new AssertionAuditRun(new ExtentReportSession(reportPath, settings, redactor), redactor, settings);
             var invocation = run.open(new AssertionAuditReport.Identity("J6ReleaseGovernance", "ro.atf.j6.release",
                     "Current version, APK, and download route", "release", List.of("component", "governance", "offline")))) {
            invocation.phase("TEST BODY", checks, status, "", new ReportPhaseTiming(java.time.Duration.ofMillis(1), 1));
            invocation.complete(status);
        }
        var profile = new ReportArtifactProfile(ReportArtifactProfile.Renderer.EXTENT, List.of(
                new ReportArtifactProfile.Scenario("J6ReleaseGovernance", "Current version, APK, and download route",
                        status, List.of(new ReportArtifactProfile.Phase("TEST BODY", status,
                        List.of(new ReportArtifactProfile.Assertions("Component assertions",
                                checks.stream().map(check -> new ReportArtifactProfile.Comparison(check.name(), check.passed())).toList())))))));
        var inspection = ReportArtifactInspector.inspect(reportPath, profile);
        assertTrue(inspection.isCompliant(), () -> inspection.violations().toString());
    }

    private static void add(List<AtfAssertion> checks, String name, String expected, String actual) {
        checks.add(new AtfAssertion(name, expected, actual, expected.equals(actual)));
    }

    private static String gradleVersion(String build) {
        var matcher = java.util.regex.Pattern.compile("versionName\\s+['\"]([^'\"]+)['\"]").matcher(build);
        return matcher.find() ? matcher.group(1) : "missing";
    }

    private static boolean routePolicy(String state, String readme, String version) {
        String prefix = "https://github.com/crististefanita/atf_j6/releases/";
        String page = prefix + "tag/v" + version;
        String apk = prefix + "download/v" + version + "/HoldemLab-" + version + ".apk";
        long releaseLinks = readme.lines().filter(line -> line.contains(prefix)).count();
        if (!state.equals("PUBLISHED")) return releaseLinks == 0;
        return releaseLinks == 2 && readme.lines().filter(line -> line.contains(page)).count() == 1
                && readme.lines().filter(line -> line.contains(apk)).count() == 1
                && !readme.contains("raw.githubusercontent.com") && !readme.contains("/releases/latest");
    }

    private static Path projectRoot() {
        for (Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath(); current != null; current = current.getParent()) {
            if (Files.isRegularFile(current.resolve("release/index.json"))
                    && Files.isDirectory(current.resolve("holdem-android"))
                    && Files.isDirectory(current.resolve("holdem-app"))) return current;
        }
        throw new IllegalStateException("J6 release catalog is not available from this test workspace");
    }
}
