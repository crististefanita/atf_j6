package ro.atf.j6.release;

import atf.testkit.AtfComponentTest;

import atf.reporting.contract.AtfAssertion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AtfComponentTest
class DocumentationGovernanceTest {
    private static final Pattern SECTION = Pattern.compile("(?m)^## (.+)$");
    private static final Pattern OWNER_SECTION = Pattern.compile(
            "(?ms)^## Cerințe și documentație[ \\t]*\\R(.*?)(?=^## |\\z)");
    private static final Pattern LINK_BULLET = Pattern.compile("- \\[[^\\[\\]]+]\\(([^()\\s]+)\\)");
    private static final Pattern FEEDBACK_ROW = Pattern.compile(
            "(?m)^\\|(?:[^|\\r\\n]*\\|)*[^|\\r\\n]*\\((J6-[^#)]+\\.md)(?:#[^)]*)?\\)[^|\\r\\n]*\\|(?:[^|\\r\\n]*\\|)* *([^|\\r\\n]+) *\\|$");
    private static final Pattern FEEDBACK_SCORE = Pattern.compile("^(\\d{1,3}) \\((\\d+)\\+(\\d+)\\+(\\d+)\\+(\\d+)\\)$");

    @Test
    void activeReadmesLeadWithOrientationAndPointToTheirOwners() throws Exception {
        Path root = projectRoot();
        var catalog = new ObjectMapper().readTree(root.resolve("release/index.json").toFile());
        String version = catalog.required("currentVersion").asText();
        var checks = new ArrayList<AtfAssertion>();
        for (String path : List.of("README.md", "docs/feedback/README.md", "docs/requirements/README.md",
                "holdem-android/README.md", "lib/README.md", "release/README.md",
                "release/" + version + "/README.md", "release/" + version + "/reports/README.md")) {
            Path document = root.resolve(path);
            String content = Files.isRegularFile(document) ? Files.readString(document) : "";
            String first = firstSection(content);
            checks.add(new AtfAssertion(path + " opens with its scope", "Pe scurt", first, "Pe scurt".equals(first)));
        }
        String main = Files.readString(root.resolve("README.md"));
        List<String> links = ownerLinkTargets(main);
        for (String owner : List.of("docs/requirements/README.md", "docs/architecture/ARCHITECTURE.md",
                "docs/testing/TEST_STRATEGY.md", "docs/governance/README.md", "docs/release/ATF_Release.md", "docs/feedback/README.md",
                "release/README.md")) {
            int occurrences = Collections.frequency(links, owner);
            boolean exists = Files.isRegularFile(root.resolve(owner));
            checks.add(new AtfAssertion("README documentation links to " + owner, "one link to an existing guide",
                    occurrences + " link(s), target exists: " + exists, occurrences == 1 && exists));
        }
        var rules = new HoldemMarkdownRules(root);
        for (Path document : activeDocuments(root, version, catalog.required("state").asText().equals("DRAFT"))) {
            var issues = rules.inspect(document);
            checks.add(new AtfAssertion(root.relativize(document).toString(), "no active Markdown violations",
                    issues.isEmpty() ? "no active Markdown violations" : issues.toString(), issues.isEmpty()));
            if (document.startsWith(root.resolve("docs")) && document.getFileName().toString().equals("README.md")) {
                String first = firstSection(Files.readString(document));
                checks.add(new AtfAssertion(root.relativize(document) + " starts with its purpose", "Pe scurt",
                        first, first.equals("Pe scurt")));
            }
        }
        checkFeedback(root, checks);
        checkFeedbackScorecard(root, checks);
        checkActiveDocumentationHasNoExternalProjectReferences(root, version, catalog.required("state").asText().equals("DRAFT"), checks);
        Path reports = Path.of(System.getProperty("atf.test.reportDirectory", "target/atf-reports"));
        Files.createDirectories(reports);
        ReleaseAuditExtent.write(reports.resolve("J6DocumentationGovernance-" + System.currentTimeMillis() + ".html"),
                "J6DocumentationGovernance", "Active documentation, local links and feedback inventory", checks);
        assertTrue(checks.stream().allMatch(AtfAssertion::passed), () -> checks.stream()
                .filter(check -> !check.passed()).toList().toString());
    }

    @Test
    void ownerLinksMustBeStandaloneAndBelongToOneDocumentationSection() {
        String guide = "docs/testing/TEST_STRATEGY.md";
        String link = "- [Strategie](" + guide + ")";
        String section = "## Cerințe și documentație\n\n";
        assertEquals(List.of(guide), ownerLinkTargets(section + link + "\n## Jocul\n" + link));
        assertEquals(List.of(), ownerLinkTargets("## Pe scurt\n" + link + "\n" + section + "Consultați " + link));
        assertEquals(List.of(), ownerLinkTargets(section + "- [Alt ghid](other.md) și " + link.substring(2)));
        assertEquals(List.of(guide, guide), ownerLinkTargets(section + link + "\n" + link));
        assertEquals(List.of(), ownerLinkTargets(section + link + "\n" + section + link));
    }

    @Test
    void markdownRulesDetectBrokenAnchorsEscapesDenseProseAndMissingLegends(@TempDir Path root) throws Exception {
        Path guide = Files.writeString(root.resolve("guide.md"), """
                # Ghid
                ## Acțiuni și scor
                ## Acțiuni și scor
                """);
        Path document = Files.writeString(root.resolve("README.md"), """
                # Owner

                [Secțiune](guide.md#ac%C8%9Biuni-%C8%99i-scor-1)
                [Fișier](guide.md#acțiuni-lipsă)
                [Extern pe disc](../private.md)

                Prima propoziție. A doua. A treia. A patra.

                ```mermaid
                flowchart LR
                    A --> B
                ```

                <!-- [nepublicat](missing.md) -->
                ```text
                [exemplu literal](missing.md)
                ```
                """);
        var rules = new HoldemMarkdownRules(root);
        assertEquals(List.of(), rules.inspect(guide));
        assertEquals(List.of("guide.md#acțiuni-lipsă", "../private.md", "4 propoziții în proza de orientare",
                        "diagramă Mermaid"),
                rules.inspect(document).stream().map(HoldemMarkdownRules.Issue::actual).toList());
    }

    @Test
    void feedbackRowsNeedAResolvableCaseAndACriterion() {
        String file = "J6-UI-07-SHOWDOWN-WIN-CLARITY.md";
        String link = "[Showdown](" + file + "#dovezi-rămase)";
        String row = "| 1 | P1 | 86 (36+26+16+8) | " + link + " | Verifică marcajele. |";
        assertEquals(List.of(file, file), feedbackTargets(row + "\n" + row));
        assertEquals(List.of(), feedbackTargets("- " + link + "\n| P1 | " + link + " |   |"));
    }

    private static void checkFeedbackScorecard(Path root, List<AtfAssertion> checks) throws Exception {
        String markdown = Files.readString(root.resolve("docs/feedback/README.md"));
        var section = Pattern.compile("(?ms)^## Restanțe active, ordonate după impact\\R(.*?)(?=^## |\\z)").matcher(markdown);
        boolean hasSection = section.find();
        checks.add(new AtfAssertion("Feedback priorities have an active scorecard", "one ordered scorecard section",
                hasSection ? "section present" : "section missing", hasSection));
        if (!hasSection) return;

        var scores = new ArrayList<Integer>();
        var ids = new java.util.HashSet<String>();
        for (String line : section.group(1).lines().filter(value -> value.startsWith("|")).toList()) {
            String[] cells = line.split("\\|", -1);
            if (cells.length < 7 || cells[1].isBlank() || cells[1].contains("---") || cells[3].contains("Scor")) continue;
            String priority = cells[3].strip();
            Matcher score = FEEDBACK_SCORE.matcher(priority);
            String row = cells[2].strip();
            Matcher feedback = FEEDBACK_ROW.matcher(line);
            boolean uniqueLink = feedback.find() && ids.add(feedback.group(1));
            boolean valid = score.matches();
            int value = valid ? Integer.parseInt(score.group(1)) : 0;
            int sum = valid ? Integer.parseInt(score.group(2)) + Integer.parseInt(score.group(3))
                    + Integer.parseInt(score.group(4)) + Integer.parseInt(score.group(5)) : -1;
            boolean criterion = !cells[cells.length - 2].isBlank();
            boolean passed = valid && value >= 1 && value <= 100 && value == sum && uniqueLink && criterion;
            checks.add(new AtfAssertion("Feedback row " + row, "score is 1..100, sums its four factors, and has one target and criterion",
                    "score=" + priority + ", unique target=" + uniqueLink + ", criterion=" + criterion, passed));
            if (valid) scores.add(value);
        }
        boolean descending = !scores.isEmpty();
        for (int i = 1; i < scores.size(); i++) descending &= scores.get(i - 1) >= scores.get(i);
        checks.add(new AtfAssertion("Feedback scorecard ordering", "scores descend by priority", scores.toString(), descending));
    }

    private static void checkActiveDocumentationHasNoExternalProjectReferences(Path root, String version,
                                                                                boolean draft,
                                                                                List<AtfAssertion> checks) throws Exception {
        var external = Pattern.compile("(?i)atf[_-]j0|\\bJ0\\b");
        var references = new ArrayList<String>();
        for (Path document : activeDocuments(root, version, draft)) {
            var matcher = external.matcher(Files.readString(document));
            if (matcher.find()) references.add(root.relativize(document).toString());
        }
        checks.add(new AtfAssertion("Active documentation is self-contained", "no external project names in active Markdown",
                references.isEmpty() ? "self-contained" : references.toString(), references.isEmpty()));
    }

    static Path projectRoot() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("release/index.json"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("J6 release catalog not found");
        return root;
    }

    private static List<Path> activeDocuments(Path root, String version, boolean draft) throws Exception {
        var documents = new ArrayList<Path>();
        documents.add(root.resolve("README.md"));
        try (var children = Files.list(root)) {
            documents.addAll(children.map(path -> path.resolve("README.md")).filter(Files::isRegularFile).toList());
        }
        for (Path directory : List.of(root.resolve("docs"), root.resolve("lib"), root.resolve("release").resolve(version))) {
            if (directory.startsWith(root.resolve("release")) && !draft) continue;
            try (var tree = Files.walk(directory)) {
                documents.addAll(tree.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".md")).toList());
            }
        }
        return documents.stream().distinct().sorted().toList();
    }

    private static void checkFeedback(Path root, List<AtfAssertion> checks) throws Exception {
        Path feedback = root.resolve("docs/feedback");
        var active = feedbackTargets(Files.readString(feedback.resolve("README.md")));
        var editionNotes = new ArrayList<String>();
        try (var editions = Files.list(root.resolve("release"))) {
            for (Path notes : editions.map(edition -> edition.resolve("release-notes.md"))
                    .filter(Files::isRegularFile).toList()) {
                editionNotes.add(Files.readString(notes));
            }
        }
        try (var documents = Files.newDirectoryStream(feedback, "J6-*.md")) {
            for (Path document : documents) {
                String file = document.getFileName().toString();
                int count = Collections.frequency(active, file);
                boolean historical = editionNotes.stream()
                        .anyMatch(notes -> notes.contains("(../../docs/feedback/" + file + ")"));
                checks.add(new AtfAssertion("Feedback inventory: " + file, "one active row or an archived link",
                        count + " active row(s), archived link: " + historical,
                        count == 1 || (count == 0 && historical)));
            }
        }
        for (String file : active.stream().distinct().toList()) {
            boolean exists = Files.isRegularFile(feedback.resolve(file));
            checks.add(new AtfAssertion("Active feedback resolves: " + file, "existing document",
                    exists ? "existing document" : "missing document", exists));
        }
    }

    private static List<String> feedbackTargets(String markdown) {
        return FEEDBACK_ROW.matcher(markdown).results().filter(row -> !row.group(2).isBlank())
                .map(row -> row.group(1)).toList();
    }

    private static List<String> ownerLinkTargets(String markdown) {
        var section = OWNER_SECTION.matcher(markdown);
        if (!section.find()) return List.of();
        String body = section.group(1);
        if (section.find()) return List.of();
        return body.lines().map(String::strip).map(LINK_BULLET::matcher).filter(Matcher::matches)
                .map(link -> link.group(1)).toList();
    }

    private static String firstSection(String markdown) {
        var sections = SECTION.matcher(markdown);
        return sections.find() ? sections.group(1).strip() : "missing section";
    }
}
