package ro.atf.j6.reporting;

import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AtfComponentTest
class ComponentSuiteExtentPublisherTest {
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
        Path contract = writeContract(temp, "ro.atf.holdem.core.ComponentProbeTest", "unit");
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
        assertTrue(html.contains("TEST LEVEL"));
        assertTrue(html.contains("unit JVM suite: holdem-core"));
        assertFalse(html.contains("do-not-leak"));
        assertFalse(html.contains("ghp_abcdefghijklmnopqrstuvwxyz1234567890"));
    }

    @Test
    void missingOrMalformedXmlCreatesAnAuditableFailureInsteadOfAnEmptyPass(@TempDir Path temp) throws Exception {
        Path root = temp.resolve("source");
        Files.createDirectories(root);
        Path expected = writeContract(temp, "ro.atf.holdem.core.ComponentProbeTest", "component");
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
    void rejectsANativeLevelInTheJvmInventory(@TempDir Path temp) throws Exception {
        Path root = Files.createDirectory(temp.resolve("source"));
        Path contract = writeContract(temp, "ro.atf.holdem.core.ComponentProbeTest", "e2e");
        var failure = assertThrows(IllegalArgumentException.class, () -> ComponentSuiteExtentPublisher.publish(
                contract, root, temp.resolve("extent.html"), ComponentSuiteExtentPublisher.Scope.MAVEN, List.of()));
        assertTrue(failure.getMessage().contains("Invalid JVM test level"));
    }

    private static Path suitePath(Path root, String testClass) {
        return root.resolve("holdem-core/target/surefire-reports/TEST-" + testClass + ".xml");
    }

    private static Path writeContract(Path temp, String testClass, String level) throws Exception {
        Path contract = temp.resolve("release-test-suites.json");
        Files.writeString(contract, """
                {"schemaVersion":1,"maven":[{"module":"holdem-core","testClass":"%s","level":"%s"}],"android":[{"module":"holdem-android","testClass":"ro.atf.holdem.android.ComponentProbeTest"}]}
                """.formatted(testClass, level));
        return contract;
    }
}
