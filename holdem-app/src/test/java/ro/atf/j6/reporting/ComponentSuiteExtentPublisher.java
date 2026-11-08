package ro.atf.j6.reporting;

import atf.reporting.artifact.ReportArtifactInspector;
import atf.reporting.artifact.ReportArtifactProfile;
import atf.reporting.audit.AssertionAuditReport;
import atf.reporting.configuration.ReportingSettings;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.model.ReportField;
import atf.reporting.model.ReportObservation;
import atf.reporting.model.timing.ReportPhaseTiming;
import atf.reporting.security.SensitiveDataRedactor;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Publishes a complete JUnit suite inventory through the pinned ATF Reporting library. */
public final class ComponentSuiteExtentPublisher {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int MAX_XML_BYTES = 16 * 1024 * 1024;
    private static final int MAX_DIAGNOSTIC_CHARS = 65_536;

    private ComponentSuiteExtentPublisher() { }

    public enum Scope {
        MAVEN("maven"), ANDROID("android");
        private final String key;
        Scope(String key) { this.key = key; }
    }

    public record Result(String status, int expectedSuites, int observedSuites, int testCases,
                         int passed, int failed, int skipped, Path report) { }

    public static void main(String[] args) throws Exception {
        if (args.length < 4) {
            throw new IllegalArgumentException("Expected: <maven|android> <contract.json> <source-root> <report.html> [junit.xml...]");
        }
        Scope scope = switch (args[0].toLowerCase()) {
            case "maven" -> Scope.MAVEN;
            case "android" -> Scope.ANDROID;
            default -> throw new IllegalArgumentException("Unsupported suite scope: " + args[0]);
        };
        List<Path> xmlFiles = new ArrayList<>();
        for (int index = 4; index < args.length; index++) xmlFiles.add(Path.of(args[index]));
        Result result = publish(Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), scope, xmlFiles);
        System.out.printf("ATF component Extent: %s suites=%d/%d tests=%d pass=%d fail=%d skip=%d report=%s%n",
                result.status(), result.observedSuites(), result.expectedSuites(), result.testCases(),
                result.passed(), result.failed(), result.skipped(), result.report());
        if (!result.status().equals("PASS")) System.exit(2);
    }

    public static Result publish(Path contractPath, Path sourceRoot, Path reportPath,
                                 Scope scope, List<Path> xmlFiles) throws Exception {
        Path root = sourceRoot.toRealPath();
        Path report = reportPath.toAbsolutePath().normalize();
        Path contract = contractPath.toRealPath();
        if (report.startsWith(root)) throw new IllegalArgumentException("Extent output must stay outside the source snapshot.");
        List<SuiteSpec> expected = expectedSuites(contract, scope);
        var expectedByClass = new HashMap<String, SuiteSpec>();
        for (SuiteSpec suite : expected) {
            if (expectedByClass.put(suite.testClass(), suite) != null) {
                throw new IllegalArgumentException("Duplicate suite in release contract: " + suite.testClass());
            }
        }

        var scenarios = new ArrayList<ReportArtifactProfile.Scenario>();
        var observed = new HashSet<String>();
        var aggregate = new Counts();
        Instant startedAt = Instant.now();
        var redactor = new SensitiveDataRedactor();
        Files.createDirectories(report.getParent());

        try (var session = new ExtentReportSession(report, new ReportingSettings(240, 0), redactor)) {
            for (Path xmlFile : xmlFiles) {
                SuiteResult suite;
                try {
                    suite = readSuite(root, xmlFile, scope, redactor);
                } catch (Exception error) {
                    String label = safeRelative(root, xmlFile);
                    suite = SuiteResult.parseFailure(label, scope, redactor.redactText(error.toString()));
                }
                SuiteSpec expectedSpec = expectedByClass.get(suite.testClass());
                boolean isExpected = expectedSpec != null && expectedSpec.module().equals(suite.module());
                boolean unique = observed.add(suite.testClass());
                suite = suite.withContractStatus(isExpected && unique,
                        !isExpected ? "Suite or module is not declared by config/release-test-suites.json"
                                : !unique ? "Suite XML is duplicated" : "");
                aggregate.add(suite.counts());
                ReportArtifactProfile.Scenario scenario = publishSuite(session, suite, isExpected && unique);
                scenarios.add(scenario);
            }

            for (SuiteSpec missing : expected) {
                if (observed.contains(missing.testClass())) continue;
                SuiteResult absent = SuiteResult.missing(scope, missing);
                aggregate.add(absent.counts());
                scenarios.add(publishSuite(session, absent, false));
            }

            List<AtfAssertion> inventoryChecks = List.of(
                    new AtfAssertion("Expected suite inventory", Integer.toString(expected.size()),
                            Integer.toString(observed.size()), expected.size() == observed.size()),
                    new AtfAssertion("No test cases omitted from JUnit XML", "declared count matches testcase elements",
                            aggregate.countsMatch ? "all suite counts match" : "one or more declared counts differ", aggregate.countsMatch),
                    new AtfAssertion("No unexpected or duplicate suite", "unique allowlisted suite classes",
                            aggregate.unexpectedSuites == 0 && aggregate.duplicateSuites == 0
                                    ? "all suites match the contract" : "unexpected=" + aggregate.unexpectedSuites
                                    + ", duplicate=" + aggregate.duplicateSuites,
                            aggregate.unexpectedSuites == 0 && aggregate.duplicateSuites == 0));
            AtfReportStatus inventoryStatus = inventoryChecks.stream().allMatch(AtfAssertion::passed)
                    ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
            try (var test = session.startScenario(scope.key + " component suite inventory",
                    "JUnit XML outcomes reconciled to the versioned release test contract",
                    List.of("component", "governance", scope.key));
                 var phase = test.startPhase("TEST BODY")) {
                AssertionAuditReport.publishStep(phase, "THEN every declared suite and test is represented", inventoryChecks,
                        inventoryStatus);
                phase.observation(new ReportObservation("Component suite coverage", List.of(
                        new ReportField("Scope", scope.key),
                        new ReportField("Expected suites", Integer.toString(expected.size())),
                        new ReportField("Observed suites", Integer.toString(observed.size())),
                        new ReportField("Test cases", Integer.toString(aggregate.testCases)),
                        new ReportField("Passed", Integer.toString(aggregate.passed)),
                        new ReportField("Failed or errored", Integer.toString(aggregate.failed)),
                        new ReportField("Skipped", Integer.toString(aggregate.skipped))), List.of(), List.of()));
                phase.executionTiming(new ReportPhaseTiming(Duration.ofMillis(1), 1));
                phase.complete(inventoryStatus);
                test.complete(inventoryStatus);
            }
            scenarios.add(new ReportArtifactProfile.Scenario(scope.key + " component suite inventory",
                    "JUnit XML outcomes reconciled to the versioned release test contract", inventoryStatus,
                    List.of(new ReportArtifactProfile.Phase("TEST BODY", inventoryStatus, List.of(
                            new ReportArtifactProfile.Assertions("THEN every declared suite and test is represented",
                                    inventoryChecks.stream().map(check -> new ReportArtifactProfile.Comparison(
                                            check.name(), check.resultStatus())).toList(), inventoryStatus),
                            new ReportArtifactProfile.Observation("Component suite coverage",
                                    List.of("Scope", "Expected suites", "Observed suites", "Test cases", "Passed", "Failed or errored", "Skipped"),
                                    List.of()))))));
        }

        var inspection = ReportArtifactInspector.inspectFresh(report, startedAt,
                new ReportArtifactProfile(ReportArtifactProfile.Renderer.EXTENT, scenarios));
        if (!inspection.isCompliant()) {
            throw new IllegalStateException("Component Extent is incomplete or noncompliant: " + inspection.violations());
        }
        String status = aggregate.failed == 0 && aggregate.skipped == 0 && expected.size() == observed.size()
                && aggregate.unexpectedSuites == 0 && aggregate.duplicateSuites == 0 && aggregate.countsMatch
                ? "PASS" : "FAIL";
        return new Result(status, expected.size(), observed.size(), aggregate.testCases, aggregate.passed,
                aggregate.failed, aggregate.skipped, report);
    }

    private static ReportArtifactProfile.Scenario publishSuite(ExtentReportSession session, SuiteResult suite,
                                                               boolean allowedByContract) throws Exception {
        List<AtfAssertion> assertions = new ArrayList<>(suite.assertions());
        assertions.add(new AtfAssertion("Suite is declared exactly once", "present in release suite contract",
                allowedByContract ? "declared once" : suite.contractProblem(), allowedByContract));
        AtfReportStatus status = suite.status();
        if (!allowedByContract) status = AtfReportStatus.FAIL;
        List<ReportArtifactProfile.Evidence> evidence = new ArrayList<>();
        if (!suite.diagnostics().isEmpty()) {
            evidence.add(new ReportArtifactProfile.Observation("JUnit failure diagnostics",
                    List.of("Suite", "Failure count"), suite.diagnostics().stream()
                    .map(diagnostic -> "Failure diagnostic · " + diagnostic.testName()).toList()));
        }
        evidence.add(new ReportArtifactProfile.Assertions("THEN every JUnit test case outcome is recorded", assertions.stream()
                .map(assertion -> new ReportArtifactProfile.Comparison(assertion.name(), assertion.resultStatus())).toList(), status));
        evidence.add(new ReportArtifactProfile.Observation("JUnit suite identity",
                List.of("Module", "Test class", "JUnit XML", "JUnit XML SHA-256", "Declared test cases", "Recorded test cases"), List.of()));
        try (var test = session.startScenario(suite.testClass(), "Component suite: " + suite.module(),
                List.of("component", suite.module(), suite.scope().key));
             var phase = test.startPhase("TEST BODY")) {
            if (!suite.diagnostics().isEmpty()) {
                phase.observation(new ReportObservation("JUnit failure diagnostics", List.of(
                        new ReportField("Suite", suite.testClass()),
                        new ReportField("Failure count", Integer.toString(suite.diagnostics().size()))),
                        suite.diagnostics().stream().map(diagnostic -> new ReportObservation.Body(
                                "Failure diagnostic · " + diagnostic.testName(), diagnostic.text(), ReportObservation.Format.TEXT)).toList(),
                        List.of()));
            }
            AssertionAuditReport.publishStep(phase, "THEN every JUnit test case outcome is recorded", assertions, status);
            phase.observation(new ReportObservation("JUnit suite identity", List.of(
                    new ReportField("Module", suite.module()),
                    new ReportField("Test class", suite.testClass()),
                    new ReportField("JUnit XML", suite.source()),
                    new ReportField("JUnit XML SHA-256", suite.sha256()),
                    new ReportField("Declared test cases", Integer.toString(suite.declaredTests())),
                    new ReportField("Recorded test cases", Integer.toString(suite.assertions().size()))), List.of(), List.of()));
            phase.executionTiming(new ReportPhaseTiming(Duration.ofMillis(Math.max(1, suite.durationMillis())),
                    Math.max(1, suite.assertions().size())));
            phase.complete(status);
            test.complete(status);
        }
        return new ReportArtifactProfile.Scenario(suite.testClass(), "Component suite: " + suite.module(), status,
                List.of(new ReportArtifactProfile.Phase("TEST BODY", status, evidence)));
    }

    private static List<SuiteSpec> expectedSuites(Path contract, Scope scope) throws Exception {
        JsonNode root = JSON.readTree(contract.toFile());
        if (root.path("schemaVersion").asInt(-1) != 1 || !root.path(scope.key).isArray()) {
            throw new IllegalArgumentException("Invalid release test suite contract: " + contract);
        }
        List<SuiteSpec> suites = new ArrayList<>();
        for (JsonNode item : root.path(scope.key)) {
            String module = item.path("module").asText("");
            String testClass = item.path("testClass").asText("");
            if (module.isBlank() || testClass.isBlank()) throw new IllegalArgumentException("Suite contract entry is incomplete.");
            suites.add(new SuiteSpec(module, testClass));
        }
        if (suites.isEmpty()) throw new IllegalArgumentException("No suites declared for scope " + scope.key);
        return List.copyOf(suites);
    }

    private static SuiteResult readSuite(Path root, Path xmlPath, Scope scope,
                                         SensitiveDataRedactor redactor) throws Exception {
        Path xml = xmlPath.toRealPath();
        if (!xml.startsWith(root) || !xml.getFileName().toString().startsWith("TEST-")
                || !xml.getFileName().toString().endsWith(".xml") || Files.size(xml) > MAX_XML_BYTES) {
            throw new IllegalArgumentException("JUnit XML is outside the source snapshot, has an unexpected name, or exceeds the size limit.");
        }
        String source = root.relativize(xml).toString().replace('\\', '/');
        String module = source.substring(0, source.indexOf('/'));
        var factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        var document = factory.newDocumentBuilder().parse(xml.toFile());
        Element rootNode = document.getDocumentElement();
        if (!rootNode.getTagName().equals("testsuite")) throw new IllegalArgumentException("Expected one JUnit <testsuite> root.");
        String testClass = rootNode.getAttribute("name");
        int declared = integerAttribute(rootNode, "tests");
        long durationMillis = Math.max(0, Math.round(parseSeconds(rootNode.getAttribute("time")) * 1000));
        NodeList nodes = rootNode.getElementsByTagName("testcase");
        List<AtfAssertion> assertions = new ArrayList<>();
        List<Diagnostic> diagnostics = new ArrayList<>();
        Counts counts = new Counts();
        for (int i = 0; i < nodes.getLength(); i++) {
            Element testCase = (Element) nodes.item(i);
            String method = testCase.getAttribute("name");
            String caseClass = testCase.getAttribute("classname");
            if (method.isBlank()) method = "unnamed-testcase-" + (i + 1);
            String name = caseClass.isBlank() ? method : caseClass + "#" + method;
            Outcome outcome = outcome(testCase, redactor, name);
            assertions.add(new AtfAssertion(name, "JUnit passed", outcome.actual(), outcome.status() == AtfReportStatus.PASS,
                    outcome.status()));
            counts.add(outcome.status());
            if (!outcome.diagnostic().isBlank()) diagnostics.add(new Diagnostic(name, outcome.diagnostic()));
        }
        boolean countsMatch = declared == nodes.getLength();
        counts.countsMatch &= countsMatch;
        if (!countsMatch || declared == 0) {
            assertions.add(new AtfAssertion("JUnit XML declares the recorded case count", Integer.toString(declared),
                    Integer.toString(nodes.getLength()), countsMatch && declared > 0));
        }
        if (!scopeModuleAllowed(scope, module)) {
            counts.unexpectedSuites++;
            assertions.add(new AtfAssertion("Suite XML belongs to the requested scope", scope.key, module, false));
        }
        return new SuiteResult(scope, module, testClass, source, sha256(Files.readAllBytes(xml)), declared,
                durationMillis, assertions, diagnostics, "", counts);
    }

    private static Outcome outcome(Element testCase, SensitiveDataRedactor redactor, String testName) {
        for (String kind : List.of("failure", "error", "skipped")) {
            NodeList elements = testCase.getElementsByTagName(kind);
            if (elements.getLength() == 0) continue;
            Element issue = (Element) elements.item(0);
            String message = redactor.redactText(issue.getAttribute("message"));
            String type = redactor.redactText(issue.getAttribute("type"));
            String body = redactor.redactText(issue.getTextContent());
            if (body.length() > MAX_DIAGNOSTIC_CHARS) body = body.substring(0, MAX_DIAGNOSTIC_CHARS) + "\n[diagnostic truncated]";
            String actual = "JUnit " + kind + (type.isBlank() ? "" : " · " + type)
                    + (message.isBlank() ? "" : " · " + message);
            return new Outcome(kind.equals("skipped") ? AtfReportStatus.SKIP : AtfReportStatus.FAIL,
                    actual, body, testName);
        }
        return new Outcome(AtfReportStatus.PASS, "JUnit passed", "", testName);
    }

    private static boolean scopeModuleAllowed(Scope scope, String module) {
        return switch (scope) {
            case MAVEN -> Set.of("holdem-core", "holdem-ai", "holdem-arena", "holdem-app").contains(module);
            case ANDROID -> module.equals("holdem-android");
        };
    }

    private static String safeRelative(Path root, Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        return normalized.startsWith(root) ? root.relativize(normalized).toString().replace('\\', '/')
                : "invalid-source-path";
    }

    private static int integerAttribute(Element element, String name) {
        try { return Integer.parseInt(element.getAttribute(name)); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private static double parseSeconds(String value) {
        try { return Double.parseDouble(value); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }

    private record SuiteSpec(String module, String testClass) { }
    private record Diagnostic(String testName, String text) { }
    private record Outcome(AtfReportStatus status, String actual, String diagnostic, String testName) { }

    private record SuiteResult(Scope scope, String module, String testClass, String source, String sha256,
                               int declaredTests, long durationMillis, List<AtfAssertion> assertions,
                               List<Diagnostic> diagnostics, String contractProblem, Counts counts) {
        SuiteResult withContractStatus(boolean valid, String problem) {
            if (valid) return this;
            Counts copy = counts.copy();
            if (problem.contains("duplicate")) copy.duplicateSuites++;
            else copy.unexpectedSuites++;
            return new SuiteResult(scope, module, testClass, source, sha256, declaredTests, durationMillis,
                    assertions, diagnostics, problem, copy);
        }
        static SuiteResult missing(Scope scope, SuiteSpec spec) {
            var check = new AtfAssertion("Declared suite produced JUnit XML", "one XML suite", "missing", false);
            return new SuiteResult(scope, spec.module(), spec.testClass(), "missing JUnit XML", "", 0, 0,
                    List.of(check), List.of(), "Expected suite was not produced by the test runner.", new Counts());
        }
        static SuiteResult parseFailure(String source, Scope scope, String diagnostic) {
            String name = "unreadable-suite:" + source;
            var check = new AtfAssertion("JUnit XML can be parsed safely", "valid local JUnit XML", diagnostic, false);
            return new SuiteResult(scope, "unknown", name, source, "", 0, 0, List.of(check),
                    List.of(new Diagnostic(name, diagnostic)), "JUnit XML parsing failed.", new Counts());
        }
        AtfReportStatus status() {
            if (!contractProblem.isBlank() || !counts.countsMatch || assertions.stream().anyMatch(check -> !check.passed())) {
                return AtfReportStatus.FAIL;
            }
            if (assertions.stream().anyMatch(check -> check.resultStatus() == AtfReportStatus.SKIP)) return AtfReportStatus.FAIL;
            return AtfReportStatus.PASS;
        }
    }

    private static final class Counts {
        private int testCases;
        private int passed;
        private int failed;
        private int skipped;
        private int unexpectedSuites;
        private int duplicateSuites;
        private boolean countsMatch = true;
        private void add(Counts other) {
            testCases += other.testCases; passed += other.passed; failed += other.failed; skipped += other.skipped;
            unexpectedSuites += other.unexpectedSuites; duplicateSuites += other.duplicateSuites;
            countsMatch &= other.countsMatch;
        }
        private void add(AtfReportStatus status) {
            testCases++;
            if (status == AtfReportStatus.PASS) passed++;
            else if (status == AtfReportStatus.SKIP) skipped++;
            else failed++;
        }
        private Counts copy() {
            var copy = new Counts(); copy.testCases = testCases; copy.passed = passed; copy.failed = failed;
            copy.skipped = skipped; copy.unexpectedSuites = unexpectedSuites; copy.duplicateSuites = duplicateSuites;
            copy.countsMatch = countsMatch; return copy;
        }
    }
}
