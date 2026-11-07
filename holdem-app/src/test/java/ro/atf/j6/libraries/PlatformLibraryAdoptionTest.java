package ro.atf.j6.libraries;

import atf.console.AtfConsoleReporter;
import atf.console.ConsoleSettings;
import atf.core.http.request.ApiRequest;
import atf.core.json.JsonDocuments;
import atf.mock.AtfMockTransport;
import atf.reporting.artifact.ReportArtifactInspector;
import atf.reporting.artifact.ReportArtifactProfile;
import atf.reporting.audit.AssertionAuditReport;
import atf.reporting.audit.AssertionAuditRun;
import atf.reporting.configuration.ReportingSettings;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.security.SensitiveDataRedactor;
import atf.reporting.model.timing.ReportPhaseTiming;
import atf.testkit.AtfComponentTest;
import atf.testkit.assertions.RecordedAssertions;
import atf.testkit.lifecycle.FailureAwareCleanup;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Consumer adoption only: exact local binaries plus a small offline composition, without producer sources. */
@AtfComponentTest
class PlatformLibraryAdoptionTest {
    @TempDir Path directory;

    @Test void distributedLibrariesComposeAnAuditableOfflineComponent() throws Exception {
        var checks = new RecordedAssertions();
        Path output = Path.of(System.getProperty("atf.test.reportDirectory", "target/atf-reports"));
        Files.createDirectories(output); Path report = output.resolve("j6-library-component-" + System.currentTimeMillis() + ".html");
        var settings = ReportingSettings.defaults(); var redactor = new SensitiveDataRedactor();
        try (var run = new AssertionAuditRun(new ExtentReportSession(report, settings, redactor), redactor, settings);
             var invocation = run.open(new AssertionAuditReport.Identity("J6LibraryAdoption", "ro.atf.j6.libraries", "offline library composition", "libraries", List.of("component", "offline")))) {
            long started = System.nanoTime();
            Throwable failure = FailureAwareCleanup.runAll(null, () -> verifyLibraries(checks));
            var timing = new ReportPhaseTiming(java.time.Duration.ofNanos(System.nanoTime() - started), 1);
            var status = failure == null ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
            String detail = failure == null ? "" : failure.toString();
            failure = FailureAwareCleanup.runAll(failure,
                    () -> invocation.phase("TEST BODY", checks.checks().stream()
                            .map(value -> new AtfAssertion(value.name(), value.expected(), value.actual(), value.passed())).toList(), status, detail, timing),
                    () -> invocation.complete(status));
            if (failure instanceof Exception original) throw original;
            if (failure instanceof Error original) throw original;
        }
        List<String> names = List.of("Flat JAR count", "Flat POM count", "Nested directories", "Shared source shadows", "JAR JsonDocuments", "SHA JsonDocuments", "JAR AssertionAuditRun", "SHA AssertionAuditRun",
                "JAR AtfConsoleReporter", "SHA AtfConsoleReporter", "JAR AtfMockTransport", "SHA AtfMockTransport",
                "JAR RecordedAssertions", "SHA RecordedAssertions", "Console message", "Core JSON", "Mock status", "Mock body");
        var profile = new ReportArtifactProfile(ReportArtifactProfile.Renderer.EXTENT, List.of(new ReportArtifactProfile.Scenario(
                "J6LibraryAdoption", "offline library composition", AtfReportStatus.PASS, List.of(new ReportArtifactProfile.Phase(
                "TEST BODY", AtfReportStatus.PASS, List.of(new ReportArtifactProfile.Assertions("Component assertions",
                names.stream().map(name -> new ReportArtifactProfile.Comparison(name, true)).toList())))))));
        var inspection = ReportArtifactInspector.inspect(report, profile);
        assertTrue(inspection.isCompliant(), () -> inspection.violations().toString());
    }

    private void verifyLibraries(RecordedAssertions checks) throws Exception {
        List<Path> catalog;
        try (var files = Files.list(root().resolve("lib"))) { catalog = files.toList(); }
        checks.equals("Flat JAR count", 5L, catalog.stream().filter(file -> file.toString().endsWith(".jar")).count());
        checks.equals("Flat POM count", 5L, catalog.stream().filter(file -> file.toString().endsWith(".pom")).count());
        checks.equals("Nested directories", 0L, catalog.stream().filter(Files::isDirectory).count());
        checks.equals("Shared source shadows", List.of(), List.of("atf-core", "atf-reporting", "atf-reporting-tests", "atf-console", "atf-mock", "atf-test-kit")
                .stream().filter(name -> Files.exists(root().resolve(name))).toList());
        List<String> artifacts = List.of("atf-core-", "atf-reporting-", "atf-console-", "atf-mock-", "atf-test-kit-");
        List<Class<?>> anchors = List.of(JsonDocuments.class, AssertionAuditRun.class, AtfConsoleReporter.class,
                AtfMockTransport.class, RecordedAssertions.class);
        for (int index = 0; index < anchors.size(); index++) {
            Class<?> anchor = anchors.get(index);
            String prefix = artifacts.get(index);
            Path expected = catalog.stream().filter(file -> file.getFileName().toString().startsWith(prefix) && file.toString().endsWith(".jar"))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Missing flat JAR: " + prefix));
            Path actual = Path.of(anchor.getProtectionDomain().getCodeSource().getLocation().toURI());
            checks.equals("JAR " + anchor.getSimpleName(), expected.getFileName().toString(), actual.getFileName().toString());
            checks.equals("SHA " + anchor.getSimpleName(), digest(expected), digest(actual));
        }
        var consoleOutput = new java.io.ByteArrayOutputStream();
        try (var stream = new java.io.PrintStream(consoleOutput, true, java.nio.charset.StandardCharsets.UTF_8)) {
            new AtfConsoleReporter(stream, ConsoleSettings.defaults()).info("J6 offline library adoption");
        }
        checks.isTrue("Console message", consoleOutput.toString(java.nio.charset.StandardCharsets.UTF_8).contains("J6 offline library adoption"));
        checks.equals("Core JSON", true, JsonDocuments.read("{\"accepted\":true}").path("accepted").asBoolean());
        Path yaml = directory.resolve("mock.yaml");
        Files.writeString(yaml, "mock:\n  routes:\n    - {method: GET, path: /holdem/health, status: 200, body: '{\"state\":\"ready\"}'}\n  fallback: {status: 500, body: '{}'}\n");
        var response = AtfMockTransport.fromConfiguration(yaml).send(ApiRequest.of("GET", "/holdem/health"), "https://offline.test/holdem/health", "none").join();
        checks.equals("Mock status", 200, response.status());
        checks.equals("Mock body", "ready", JsonDocuments.read(response.body()).path("state").asText());
    }

    private static Path root() {
        for (Path path = Path.of(System.getProperty("user.dir")).toAbsolutePath(); path != null; path = path.getParent())
            if (Files.isDirectory(path.resolve("lib")) && Files.isDirectory(path.resolve("holdem-app"))) return path;
        throw new IllegalStateException("J6 binary library root is unavailable");
    }
    private static String digest(Path path) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
}
