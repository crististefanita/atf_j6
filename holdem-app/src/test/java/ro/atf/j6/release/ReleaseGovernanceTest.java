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
        publish(root, "J6ReleaseGovernance", "Current version, APK, and download route", checks);
        assertTrue(failure == null && !checks.isEmpty() && checks.stream().allMatch(AtfAssertion::passed),
                () -> checks.stream().filter(check -> !check.passed()).map(check -> check.name() + " expected "
                        + check.expected() + " actual " + check.actual()).toList().toString());
    }

    @Test void downloadRoutesAreStateGatedAndMatchTheCurrentVersion() throws Exception {
        Path root = projectRoot();
        var checks = new ArrayList<AtfAssertion>();
        String page = "https://github.com/crististefanita/atf_j6/releases/tag/v0.1.0";
        String apk = "https://github.com/crististefanita/atf_j6/releases/download/v0.1.0/HoldemLab-0.1.0.apk";
        String publishedGuide = "## Android\n- [Release](" + page + ")\n- [APK](" + apk + ")\n";
        String authenticatedGuide = "## Android\nAutentificare GitHub necesară.\n- [Release](" + page + ")\n- [APK](" + apk + ")\n";
        recordRoute(checks, "DRAFT has no public download route", "DRAFT", "## Android", "0.1.0", false);
        recordRoute(checks, "PREPARED without browser proof remains local", "PREPARED",
                "## Android\n- [Candidate](release/0.1.0/README.md)", "0.1.0", false);
        recordRoute(checks, "PREPARED allows verified account download", "PREPARED", authenticatedGuide, "0.1.0", true);
        recordRoute(checks, "PREPARED direct links require an account notice", "PREPARED", publishedGuide, "0.1.0", false);
        recordRoute(checks, "Exact current release and APK are accepted", "PUBLISHED", publishedGuide, "0.1.0", true);
        recordRoute(checks, "DRAFT cannot expose an APK", "DRAFT", publishedGuide, "0.1.0", false);
        recordRoute(checks, "An older version is rejected", "PUBLISHED",
                publishedGuide.replace("0.1.0", "0.0.9"), "0.1.0", false);
        recordRoute(checks, "Duplicate direct asset links are rejected", "PUBLISHED",
                publishedGuide + "- [Again](" + apk + ")", "0.1.0", false);
        String rawAsset = "https://raw.githubusercontent.com/crististefanita/atf_j6/main/release/0.1.0/HoldemLab-0.1.0.apk";
        recordRoute(checks, "Raw source links are rejected", "PUBLISHED", publishedGuide.replace(apk, rawAsset), "0.1.0", false);
        String folderAsset = "release/0.1.0/HoldemLab-0.1.0.apk";
        recordRoute(checks, "Catalog folder is not a public asset", "PUBLISHED", publishedGuide.replace(apk, folderAsset), "0.1.0", false);
        String downloader = Files.readString(root.resolve("scripts/Test-PublishedHoldemDownload.ps1"));
        add(checks, "Anonymous download runner disables credentials and cookies", "credentialless HTTP client",
                downloader.contains("UseCookies = $false") && downloader.contains("$handler.Credentials = $null")
                        && downloader.contains("$handler.DefaultProxyCredentials = $null")
                        ? "credentialless HTTP client" : "credentials may be sent");
        add(checks, "Download runner verifies byte length, digest, and APK signature", "complete APK integrity gate",
                downloader.contains("$count -ne $expectedBytes") && downloader.contains("$hash -ne $expectedHash")
                        && downloader.contains("$signature[0] -ne 0x50") ? "complete APK integrity gate" : "integrity gate incomplete");
        add(checks, "Download runner rejects DRAFT", "PREPARED/PUBLISHED only",
                downloader.contains("@('PREPARED','PUBLISHED')") ? "PREPARED/PUBLISHED only" : "unqualified state accepted");
        publish(root, "J6ReleaseDownloadRouteGovernance", "Current version and authenticated/anonymous download contract", checks);
        assertTrue(!checks.isEmpty() && checks.stream().allMatch(AtfAssertion::passed),
                () -> checks.stream().filter(check -> !check.passed()).map(check -> check.name() + " expected "
                        + check.expected() + " actual " + check.actual()).toList().toString());
    }

    @Test void mainReadmeOmitsChecksumAndPinsTheCurrentReleaseFolder() throws Exception {
        Path root = projectRoot();
        var checks = new ArrayList<AtfAssertion>();
        JsonNode index = JSON.readTree(root.resolve("release/index.json").toFile());
        String version = index.path("currentVersion").asText();
        String mainReadme = Files.readString(root.resolve("README.md"));
        add(checks, "Main README omits checksum labels and digests", "no checksum disclosure",
                containsChecksumDisclosure(mainReadme) ? "checksum disclosure present" : "no checksum disclosure");
        add(checks, "Main README links the current version dossier", "current version and exact dossier",
                referencesVersionedDossier(mainReadme, version) ? "current version and exact dossier" : "version/dossier drift");
        String staleVersion = version.equals("0.0.1") ? "0.0.2" : "0.0.1";
        add(checks, "Governance rejects a stale dossier path", "blocked",
                !referencesVersionedDossier("`" + version + "` [Ediție](release/" + staleVersion + "/README.md)", version)
                        ? "blocked" : "accepted");
        add(checks, "Governance rejects a stale README version", "blocked",
                !referencesVersionedDossier("`" + staleVersion + "` [Ediție](release/" + version + "/README.md)", version)
                        ? "blocked" : "accepted");

        String digest = "a".repeat(64);
        add(checks, "Governance detects a SHA label", "blocked",
                containsChecksumDisclosure("SHA-256: " + digest) ? "blocked" : "not blocked");
        add(checks, "Governance detects a checksum label", "blocked",
                containsChecksumDisclosure("Checksum: " + digest) ? "blocked" : "not blocked");
        add(checks, "Governance detects a bare digest", "blocked",
                containsChecksumDisclosure(digest) ? "blocked" : "not blocked");
        add(checks, "Governance accepts ordinary release navigation", "allowed",
                !containsChecksumDisclosure("Release 0.1.0 · dosarul ediției și rapoarte") ? "allowed" : "rejected");

        publish(root, "J6MainReadmeGovernance", "No checksum in the project README; current release dossier is linked", checks);
        assertTrue(!checks.isEmpty() && checks.stream().allMatch(AtfAssertion::passed),
                () -> checks.stream().filter(check -> !check.passed()).map(check -> check.name() + " expected "
                        + check.expected() + " actual " + check.actual()).toList().toString());
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
        add(checks, "Main README identifies the indexed version and evidence", version,
                referencesVersionedDossier(mainReadme, version) ? version : "missing or stale version dossier");
        add(checks, "Main README contains no checksum material", "no checksum disclosure",
                containsChecksumDisclosure(mainReadme) ? "checksum disclosure present" : "no checksum disclosure");
        add(checks, "APK filename follows the product version", packageName, identity.path("apkName").asText());
        add(checks, "Android Gradle version matches the release", version,
                gradleVersion(Files.readString(root.resolve("holdem-android/app/build.gradle"))));
        String androidBuild = Files.readString(root.resolve("holdem-android/app/build.gradle"));
        add(checks, "Runtime language resources cannot split from the APK", "bundle language split disabled",
                androidBuild.contains("enableSplit = false") ? "bundle language split disabled" : "language split may be enabled");
        add(checks, "Release APK build type disables debugging", "debuggable false",
                androidBuild.contains("debuggable false") ? "debuggable false" : "debuggable is not disabled");
        boolean deterministicSanityOnly = androidBuild.contains("testBuildType 'sanity'")
                && java.util.regex.Pattern.compile("sanity\\s*\\{[^}]*debuggable true", java.util.regex.Pattern.DOTALL).matcher(androidBuild).find()
                && androidBuild.contains("initWith release");
        add(checks, "Deterministic deals stay in the non-distributable sanity variant", "sanity-only seeded build",
                deterministicSanityOnly ? "sanity-only seeded build" : "test seed can reach the distributable build");
        String androidSanity = Files.readString(root.resolve("holdem-android/app/src/androidTest/java/ro/atf/holdem/android/TournamentSanity.java"));
        add(checks, "Native evidence is returned by instrumentation", "receipt and screenshots stream from the test run",
                androidSanity.contains("result.putString(\"receipt\"") && androidSanity.contains("status.putString(\"pngBase64\"")
                        ? "receipt and screenshots stream from the test run" : "evidence depends on private device storage");
        String extentPublisher = Files.readString(root.resolve("holdem-android/tools/PublishTournamentExtent.java"));
        add(checks, "Extent is checked against its declared evidence profile", "fresh Extent profile inspection",
                extentPublisher.contains("inspectFresh(reportPath, startedAt, profile)")
                        ? "fresh Extent profile inspection" : "generic API-only inspection");
        add(checks, "Exact native screenshots are embedded once in Extent", "receipt image bytes embedded exactly once",
                extentPublisher.contains("occurrences(html, payload) != 1")
                        && extentPublisher.contains("Base64.getEncoder().encodeToString(Files.readAllBytes(screenshot))")
                        ? "receipt image bytes embedded exactly once" : "image attachment identity is not gated");
        add(checks, "Release root avoids Raw and folder downloads", "no raw or source-folder URL",
                mainReadme.contains("raw.githubusercontent.com") || mainReadme.contains("release/" + version + "/" + packageName)
                        ? "unqualified route present" : "no raw or source-folder URL");
        boolean browserReceiptMatches = validation.path("authenticatedBrowserDownload").path("verified").asBoolean()
                && validation.path("authenticatedBrowserDownload").path("bytes").asLong() == identity.path("apkBytes").asLong()
                && validation.path("authenticatedBrowserDownload").path("sha256").asText().equals(identity.path("sha256").asText());
        boolean authenticatedPreparedDownload = state.equals("PREPARED") && browserReceiptMatches;
        boolean accountNotice = mainReadme.toLowerCase(java.util.Locale.ROOT).contains("autentificare github");
        if (state.equals("PREPARED") && (mainReadme.contains(releaseUrl) || mainReadme.contains(apkUrl))) {
            add(checks, "PREPARED links have a complete authenticated browser receipt", "verified bytes and digest",
                    browserReceiptMatches ? "verified bytes and digest" : "missing or mismatched browser receipt");
            add(checks, "PREPARED links disclose the authentication requirement", "GitHub account required",
                    accountNotice ? "GitHub account required" : "account requirement missing");
        }
        add(checks, "README has the current release page", published || authenticatedPreparedDownload ? releaseUrl : "no release route",
                (published || authenticatedPreparedDownload) && mainReadme.contains(releaseUrl) ? releaseUrl
                        : !published && !authenticatedPreparedDownload && !mainReadme.contains("github.com/crististefanita/atf_j6/releases/")
                                ? "no release route" : "wrong route");
        add(checks, "README has the current direct APK route", published || authenticatedPreparedDownload ? apkUrl : "no APK route",
                (published || authenticatedPreparedDownload) && mainReadme.contains(apkUrl) ? apkUrl
                        : !published && !authenticatedPreparedDownload && !mainReadme.contains("releases/download/")
                                ? "no APK route" : "wrong route");
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
            checks.add(new AtfAssertion("Prepared APK has bytes", "positive size", Integer.toString(bytes.length),
                    bytes.length > 0));
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

    private void publish(Path root, String testClass, String scenario, List<AtfAssertion> checks) throws Exception {
        Path output = Path.of(System.getProperty("atf.test.reportDirectory", "target/atf-reports"));
        Files.createDirectories(output);
        Path reportPath = output.resolve(testClass + "-" + System.currentTimeMillis() + ".html");
        AtfReportStatus status = checks.stream().allMatch(AtfAssertion::passed) ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
        var settings = ReportingSettings.defaults();
        var redactor = new SensitiveDataRedactor();
        try (var run = new AssertionAuditRun(new ExtentReportSession(reportPath, settings, redactor), redactor, settings);
             var invocation = run.open(new AssertionAuditReport.Identity(testClass, "ro.atf.j6.release",
                     scenario, "release", List.of("component", "governance", "offline")))) {
            invocation.phase("TEST BODY", checks, status, "", new ReportPhaseTiming(java.time.Duration.ofMillis(1), 1));
            invocation.complete(status);
        }
        var profile = new ReportArtifactProfile(ReportArtifactProfile.Renderer.EXTENT, List.of(
                new ReportArtifactProfile.Scenario(testClass, scenario,
                        status, List.of(new ReportArtifactProfile.Phase("TEST BODY", status,
                        List.of(new ReportArtifactProfile.Assertions("Component assertions",
                                checks.stream().map(check -> new ReportArtifactProfile.Comparison(check.name(), check.passed())).toList())))))));
        var inspection = ReportArtifactInspector.inspect(reportPath, profile);
        assertTrue(inspection.isCompliant(), () -> inspection.violations().toString());
    }

    private static boolean referencesVersionedDossier(String readme, String version) {
        return readme.contains("`" + version + "`")
                && readme.contains("(release/" + version + "/README.md)");
    }

    private static boolean containsChecksumDisclosure(String text) {
        return java.util.regex.Pattern.compile("(?i)(?:\\bsha\\s*-?\\s*256\\b|\\bchecksum\\b|\\b[0-9a-f]{64}\\b)")
                .matcher(text).find();
    }

    private static void add(List<AtfAssertion> checks, String name, String expected, String actual) {
        checks.add(new AtfAssertion(name, expected, actual, expected.equals(actual)));
    }

    private static void recordRoute(List<AtfAssertion> checks, String name, String state, String readme,
                                    String version, boolean expected) {
        boolean accepted = routePolicy(state, readme, version);
        add(checks, name, expected ? "accepted" : "blocked", accepted == expected
                ? (expected ? "accepted" : "blocked") : (accepted ? "exposed" : "rejected"));
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
        boolean exactCurrentLinks = releaseLinks == 2 && readme.lines().filter(line -> line.contains(page)).count() == 1
                && readme.lines().filter(line -> line.contains(apk)).count() == 1
                && !readme.contains("raw.githubusercontent.com") && !readme.contains("/releases/latest");
        if (state.equals("PUBLISHED")) return exactCurrentLinks;
        return state.equals("PREPARED") && exactCurrentLinks
                && readme.toLowerCase(java.util.Locale.ROOT).contains("autentificare github");
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
