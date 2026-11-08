package ro.atf.j6.reporting;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AtfComponentTest
class ComponentSuiteExtentPublisherTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern PACKAGE = Pattern.compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;");
    private static final Pattern TEST_ANNOTATION = Pattern.compile("@(Test|ParameterizedTest|RepeatedTest|TestFactory|TestTemplate)\\b");

    @Test
    void publishesEachJUnitCaseAndRedactsFailureDiagnostics(@TempDir Path temp) throws Exception {
        Path root = temp.resolve("source");
        Files.createDirectories(root);
        Path xml = suitePath(root, "ro.atf.holdem.core.ComponentProbeTest");
        Files.createDirectories(xml.getParent());
        Files.writeString(xml, """
                <?xml version="1.0" encoding="UTF-8"?>
                <testsuite name="ro.atf.holdem.core.ComponentProbeTest" tests="2" failures="1" errors="0" skipped="0" time="0.045">
                  <testcase classname="ro.atf.holdem.core.ComponentProbeTest" name="keepsEvidence" time="0.01"/>
                  <testcase classname="ro.atf.holdem.core.ComponentProbeTest" name="reportsFailure" time="0.02">
                    <failure type="AssertionError" message="password=do-not-leak"><![CDATA[Bearer ghp_abcdefghijklmnopqrstuvwxyz1234567890\n at ComponentProbeTest.reportsFailure]]></failure>
                  </testcase>
                </testsuite>
                """);
        Path contract = writeContract(temp, "ro.atf.holdem.core.ComponentProbeTest");
        Path report = temp.resolve("outside/component-extent.html");

        ComponentSuiteExtentPublisher.Result result = ComponentSuiteExtentPublisher.publish(contract, root, report,
                ComponentSuiteExtentPublisher.Scope.MAVEN, List.of(xml));

        assertEquals("FAIL", result.status(), "a failure must remain visible without making the report invalid");
        assertEquals(1, result.observedSuites());
        assertEquals(2, result.testCases());
        assertEquals(1, result.passed());
        assertEquals(1, result.failed());
        String html = Files.readString(report);
        assertTrue(html.contains("reportsFailure"));
        assertTrue(html.contains("Failure diagnostic"));
        assertFalse(html.contains("do-not-leak"));
        assertFalse(html.contains("ghp_abcdefghijklmnopqrstuvwxyz1234567890"));
    }

    @Test
    void missingOrMalformedXmlCreatesAnAuditableFailureInsteadOfAnEmptyPass(@TempDir Path temp) throws Exception {
        Path root = temp.resolve("source");
        Files.createDirectories(root);
        Path expected = writeContract(temp, "ro.atf.holdem.core.ComponentProbeTest");
        Path missingReport = temp.resolve("outside/missing-suite-extent.html");

        var missing = ComponentSuiteExtentPublisher.publish(expected, root, missingReport,
                ComponentSuiteExtentPublisher.Scope.MAVEN, List.of());

        assertEquals("FAIL", missing.status());
        assertEquals(1, missing.expectedSuites());
        assertEquals(0, missing.observedSuites());
        assertTrue(Files.readString(missingReport).contains("Declared suite produced JUnit XML"));

        Path secret = temp.resolve("private-input.txt");
        Files.writeString(secret, "private-file-marker-must-not-appear");
        Path xml = suitePath(root, "ro.atf.holdem.core.ComponentProbeTest");
        Files.createDirectories(xml.getParent());
        String external = secret.toUri().toASCIIString();
        Files.writeString(xml, """
                <!DOCTYPE testsuite [<!ENTITY private SYSTEM "%s">]>
                <testsuite name="ro.atf.holdem.core.ComponentProbeTest" tests="1"><testcase name="xxe">&private;</testcase></testsuite>
                """.formatted(external));
        Path malformedReport = temp.resolve("outside/malformed-suite-extent.html");

        var malformed = ComponentSuiteExtentPublisher.publish(expected, root, malformedReport,
                ComponentSuiteExtentPublisher.Scope.MAVEN, List.of(xml));

        assertEquals("FAIL", malformed.status());
        String html = Files.readString(malformedReport);
        assertFalse(html.contains("private-file-marker-must-not-appear"));
        assertTrue(html.contains("JUnit failure diagnostics"));
    }

    @Test
    void releaseContractListsEveryJUnitTestClassInItsModule(@TempDir Path ignored) throws Exception {
        Path root = projectRoot();
        JsonNode contract = JSON.readTree(root.resolve("config/release-test-suites.json").toFile());
        assertEquals(1, contract.path("schemaVersion").asInt());
        assertEquals(discover(root, List.of("holdem-core", "holdem-ai", "holdem-arena", "holdem-app")),
                declared(contract.path("maven")), "Maven component Extent would silently omit a JUnit class");
        assertEquals(discover(root, List.of("holdem-android/app")), declared(contract.path("android")),
                "Android component Extent would silently omit a JUnit class");
    }

    private static Set<String> discover(Path root, List<String> modules) throws Exception {
        Set<String> testClasses = new HashSet<>();
        for (String module : modules) {
            Path source = root.resolve(module).resolve("src/test/java");
            if (!Files.isDirectory(source)) continue;
            try (var paths = Files.walk(source)) {
                for (Path file : paths.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String text = Files.readString(file);
                    if (!TEST_ANNOTATION.matcher(text).find()) continue;
                    Matcher packageName = PACKAGE.matcher(text);
                    if (!packageName.find()) throw new AssertionError("JUnit test has no package declaration: " + file);
                    String className = file.getFileName().toString().replaceFirst("\\.java$", "");
                    testClasses.add(module.split("/")[0] + ":" + packageName.group(1) + "." + className);
                }
            }
        }
        return testClasses;
    }

    private static Path projectRoot() throws Exception {
        for (Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath(); current != null;
             current = current.getParent()) {
            if (Files.isRegularFile(current.resolve("config/release-test-suites.json"))
                    && Files.isDirectory(current.resolve("holdem-app"))
                    && Files.isDirectory(current.resolve("holdem-android"))) return current.toRealPath();
        }
        throw new IllegalStateException("J6 release source tree is not available from this test workspace");
    }

    private static Set<String> declared(JsonNode entries) {
        Set<String> suites = new HashSet<>();
        for (JsonNode entry : entries) suites.add(entry.path("module").asText() + ":" + entry.path("testClass").asText());
        return suites;
    }

    private static Path suitePath(Path root, String testClass) {
        return root.resolve("holdem-core/target/surefire-reports/TEST-" + testClass + ".xml");
    }

    private static Path writeContract(Path temp, String testClass) throws Exception {
        Path contract = temp.resolve("release-test-suites.json");
        Files.writeString(contract, """
                {"schemaVersion":1,"maven":[{"module":"holdem-core","testClass":"%s"}],"android":[{"module":"holdem-android","testClass":"ro.atf.holdem.android.ComponentProbeTest"}]}
                """.formatted(testClass));
        return contract;
    }
}
