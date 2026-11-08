package ro.atf.j6.release;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AtfComponentTest
class ReleaseImpactRagTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void retrievesVersionedFeedbackAndTestCitationsWithoutTouchingReleaseState(@TempDir Path temp) throws Exception {
        Path root = project(temp, "0.2.0", "holdem-android", "ro.atf.holdem.android.HoldemTablePresentationComponentTest");
        write(root, "docs/feedback/J6-UI-07-SHOWDOWN-WIN-CLARITY.md", """
                # J6-UI-07 — Showdown card layout
                **Stare:** Deschis
                
                Showdown reveal keeps the player's cards, winner and table layout visible after settlement.
                Example input: password=fixture-secret-must-not-leak
                """);
        write(root, "docs/feedback/J6-UI-03-TOURNAMENT-SANITY.md", """
                # J6-UI-03 — Tournament table
                **Stare:** Rezolvat
                
                The table layout and Android display use a stable presentation component.
                """);
        write(root, "docs/architecture/ARCHITECTURE.md", """
                # Card Table architecture
                The shared card table UI is consumed by Hold'em and must preserve its component boundary.
                """);
        write(root, "library-provenance.json", """
                {"coordinate":"ro.cardtable:card-table-ui:0.1.7","producerDirectory":"C:/work/1/atf_j4"}
                """);
        write(root, "holdem-android/ui-library/provenance.json", """
                {"coordinate":"ro.cardtable:card-table-ui:0.1.7","producer":"atf_j4"}
                """);
        write(root, "docs/testing/TEST_STRATEGY.md", "# Testare\nComponenta Android pentru table presentation și showdown.\n");
        write(root, "holdem-android/app/src/main/java/ro/atf/holdem/android/HoldemTablePresentation.java", "package ro.atf.holdem.android;\nclass HoldemTablePresentation {}\n");
        String beforeIndex = Files.readString(root.resolve("release/index.json"));
        String base = commit(root, "base release");
        write(root, "holdem-android/app/src/main/java/ro/atf/holdem/android/HoldemTablePresentation.java", """
                package ro.atf.holdem.android;
                class HoldemTablePresentation {
                    // showdown card table shared UI component provenance remains visible through the terminal review
                }
                """);
        String candidate = commit(root, "candidate layout change");
        Path context = temp.resolve("external-run/release-impact-context.json");

        JsonNode review = ReleaseImpactRag.review(root, "0.1.1", base, candidate, "0.2.0", context);

        assertEquals("CONTEXT_READY", review.path("contextState").asText());
        assertTrue(review.path("advisoryOnly").asBoolean());
        assertEquals("NONE", review.path("gateEffect").asText());
        assertFalse(review.path("canDeclareTestPass").asBoolean());
        assertEquals("NOT_EVALUATED", review.path("testReceiptState").asText());
        assertEquals(candidate, review.path("candidateCommit").asText());
        assertEquals("0.1.1", review.path("baseReleaseVersion").asText());
        assertTrue(review.path("changedFiles").toString().contains("HoldemTablePresentation.java"));
        assertTrue(review.path("retrievedSources").toString().contains("J6-UI-07-SHOWDOWN-WIN-CLARITY.md"));
        assertTrue(review.path("retrievedSources").toString().contains("J6-UI-03-TOURNAMENT-SANITY.md"));
        assertTrue(review.path("retrievedSources").toString().contains("docs/architecture/ARCHITECTURE.md"));
        assertTrue(review.path("retrievedSources").toString().contains("library-provenance.json"));
        assertTrue(review.path("retrievedSources").toString().contains("ui-library/provenance.json"));
        assertTrue(review.path("retrievedSources").toString().contains("startLine"));
        assertTrue(review.path("retrievedSources").toString().contains("sourceSha256"));
        assertTrue(review.path("suggestedTestSuites").toString().contains("HoldemTablePresentationComponentTest"));
        assertFalse(Files.readString(context).contains("fixture-secret-must-not-leak"));
        assertFalse(Files.readString(context).contains("C:/work"));
        assertTrue(Files.readString(context).contains("LOCAL_PATH_REDACTED"));
        assertEquals(beforeIndex, Files.readString(root.resolve("release/index.json")));
        assertTrue(Files.isRegularFile(context));
    }

    @Test
    void ranksAffectedAndroidGuideAndSuiteAboveUnchangedProvenanceAndHistoricalReports(@TempDir Path temp) throws Exception {
        String module = "holdem-android";
        String suite = "ro.atf.holdem.android.LocalHoldemGameTest";
        Path root = project(temp, "0.3.0", module, suite);
        addSuite(root, "android", module, "ro.atf.holdem.android.HoldemTablePresentationComponentTest");
        addSuite(root, "maven", "archive-xylophone", "ro.example.XylophoneArchiveTest");
        write(root, "holdem-android/README.md", "# Android guide\nLocal Holdem Game estimates the win chance.\n\n## Checkpoint\nAndroid local checkpoint.\n");
        write(root, "docs/architecture/ARCHITECTURE.md", "# Checkpoint\nAndroid checkpoint stores the Holdem Game.\n");
        write(root, "library-provenance.json", "{\"feature\":\"Android local Holdem Game win chance checkpoint\"}\n");
        write(root, "holdem-android/ui-library/provenance.json", "{\"feature\":\"Android local Holdem Game win chance checkpoint\"}\n");
        String base = commit(root, "base release");
        write(root, "holdem-android/app/src/main/java/ro/atf/holdem/android/LocalHoldemGame.java", """
                class LocalHoldemGame {
                    // Store the Android win chance with the checkpoint.
                }
                """);
        write(root, "holdem-android/app/src/test/java/ro/atf/holdem/android/LocalHoldemGameTest.java",
                "class LocalHoldemGameTest {}\n");
        write(root, "docs/architecture/ARCHITECTURE.md",
                "# Checkpoint\nAndroid checkpoint persists the local Holdem Game and win chance.\n");
        String archive = "release/0.2.2/reports/xylophone-archive.html";
        write(root, archive, "<p>xylophone archive holdem arena</p>\n".repeat(100));
        String candidate = commit(root, "candidate Android change plus historical evidence");

        JsonNode review = ReleaseImpactRag.review(root, "0.2.2", base, candidate, "0.3.0", temp.resolve("context.json"));

        assertEquals("CONTEXT_READY", review.path("contextState").asText());
        assertTrue(review.path("changedFiles").toString().contains(archive));
        JsonNode sources = review.path("retrievedSources");
        assertEquals("holdem-android/README.md", sources.get(0).path("path").asText());
        assertEquals("docs/architecture/ARCHITECTURE.md", sources.get(1).path("path").asText());
        JsonNode suggestions = review.path("suggestedTestSuites");
        assertEquals(suite, suggestions.get(0).path("testClass").asText());
        assertFalse(suggestions.toString().contains("XylophoneArchiveTest"));
        assertFalse(sources.toString().contains(archive));
    }

    @Test
    void promotesOnlyTheProvenanceForTheChangedLibraryFamily(@TempDir Path temp) throws Exception {
        Path root = project(temp, "0.3.0", "holdem-android", "ro.atf.holdem.android.LocalHoldemGameTest");
        String maven = "library-provenance.json";
        String android = "holdem-android/ui-library/provenance.json";
        String metadata = "{\"libraries\":[\"cardtable engine android\", \"component coordinate\"]}\n";
        write(root, maven, metadata);
        write(root, android, metadata);
        write(root, "docs/architecture/ARCHITECTURE.md", "# Library layout\nCardtable engine Android component coordinate.\n");
        String base = commit(root, "base libraries");

        write(root, "lib/cardtable-engine.pom", "<artifactId>cardtable-engine</artifactId>\n");
        String mavenCandidate = commit(root, "change Maven dependency");
        JsonNode mavenReview = ReleaseImpactRag.review(root, base, mavenCandidate, "0.3.0", temp.resolve("maven.json"));
        assertProvenanceRanking(mavenReview, maven, android);

        write(root, "holdem-android/ui-library/cardtable-engine.pom", "<artifactId>cardtable-engine</artifactId>\n");
        String androidCandidate = commit(root, "change Android dependency");
        JsonNode androidReview = ReleaseImpactRag.review(root, mavenCandidate, androidCandidate, "0.3.0", temp.resolve("android.json"));
        assertProvenanceRanking(androidReview, android, maven);
    }

    @Test
    void citesConflictingAndroidPacingClaimsWithoutChangingTheReleaseGate(@TempDir Path temp) throws Exception {
        Path root = project(temp, "0.3.0", "holdem-android", "ro.atf.holdem.android.LocalHoldemGameTest");
        String guide = "holdem-android/README.md";
        String strategy = "docs/testing/TEST_STRATEGY.md";
        String key = "android.turn-pacing";
        write(root, guide, "# Android\nTurnPacing uses `550 ms` <!-- rag:contract android.turn-pacing -->.\n");
        write(root, strategy, """
                # Strategy
                TurnPacing is checked at `550 MS` <!-- rag:contract android.turn-pacing -->.

                ~~~markdown
                Example only: `400 ms` <!-- rag:contract android.turn-pacing -->.
                ~~~
                """);
        write(root, "release/0.2.2/release-notes.md",
                "# Historical\nTurnPacing used `700 ms` <!-- rag:contract android.turn-pacing -->.\n");
        String base = commit(root, "aligned pacing contract");
        assertTrue(ReleaseImpactRag.review(root, base, base, "0.3.0", temp.resolve("aligned.json"))
                .path("documentFindings").isEmpty());

        write(root, strategy, Files.readString(root.resolve(strategy)).replace("550 MS", "600 ms"));
        String candidate = commit(root, "conflicting pacing documentation");
        String releaseIndex = Files.readString(root.resolve("release/index.json"));
        JsonNode review = ReleaseImpactRag.review(root, base, candidate, "0.3.0", temp.resolve("conflict.json"));

        JsonNode findings = review.path("documentFindings");
        assertEquals(1, findings.size());
        JsonNode finding = findings.get(0);
        assertEquals("DOCUMENT_CONTRADICTION", finding.path("kind").asText());
        assertEquals(key, finding.path("key").asText());
        JsonNode citations = finding.path("citations");
        assertEquals(2, citations.size());
        assertCitation(root, citations.get(0), strategy, 2, "600 ms", candidate);
        assertCitation(root, citations.get(1), guide, 2, "550 ms", candidate);
        assertAdvisory(review);
        assertEquals(releaseIndex, Files.readString(root.resolve("release/index.json")));
        assertTrue(git(root, "status", "--porcelain").isBlank());
    }

    @Test
    void citesStaleViewportFeedbackOnlyWhenCurrentContractsAgree(@TempDir Path temp) throws Exception {
        Path root = project(temp, "0.3.0", "holdem-android", "ro.atf.holdem.android.LocalHoldemGameTest");
        String strategy = "docs/testing/TEST_STRATEGY.md";
        String guide = "holdem-android/README.md";
        String feedback = "docs/feedback/J6-UI-09-SEAT-METRIC-HIERARCHY.md";
        String claim = " <!-- rag:contract android.seat-metrics.viewport-width -->";
        String observation = " <!-- rag:observed android.seat-metrics.viewport-width -->";
        write(root, strategy, "# Strategy\nRun seat-metrics at `360 dp`" + claim + ".\n");
        write(root, guide, "# Android\nThe compact profile uses `360 dp`" + claim + ".\n");
        write(root, feedback, "# Seat metrics\n**Stare:** Deschis\nObserved `360 dp`" + observation + " password=fixture-private\n");
        write(root, "docs/feedback/J6-UI-02-TABLE-COVERAGE.md",
                "# Historical coverage\n**Stare:** Rezolvat\nObserved `360 dp`" + observation + ".\n");
        String base = commit(root, "feedback and contract use the same viewport");
        assertTrue(ReleaseImpactRag.review(root, base, base, "0.3.0", temp.resolve("aligned.json"))
                .path("documentFindings").isEmpty());

        write(root, strategy, "# Strategy\nRun seat-metrics at `320 dp`" + claim + ".\n");
        String disagreement = commit(root, "one document moves to 320 dp");
        JsonNode unresolved = ReleaseImpactRag.review(root, base, disagreement, "0.3.0", temp.resolve("unresolved.json"));
        assertEquals(1, unresolved.path("documentFindings").size());
        assertEquals("DOCUMENT_CONTRADICTION", unresolved.path("documentFindings").get(0).path("kind").asText());

        write(root, guide, "# Android\nThe compact profile uses `320 dp`" + claim + ".\n");
        String candidate = commit(root, "both contracts move to 320 dp");
        Path context = temp.resolve("stale.json");
        JsonNode review = ReleaseImpactRag.review(root, base, candidate, "0.3.0", context);

        JsonNode findings = review.path("documentFindings");
        assertEquals(1, findings.size());
        JsonNode finding = findings.get(0);
        assertEquals("STALE_FEEDBACK", finding.path("kind").asText());
        assertEquals("android.seat-metrics.viewport-width", finding.path("key").asText());
        JsonNode citations = finding.path("citations");
        assertEquals(2, citations.size());
        assertCitation(root, citations.get(0), feedback, 3, "360 dp", candidate);
        assertCitation(root, citations.get(1), strategy, 2, "320 dp", candidate);
        assertEquals("observed", citations.get(0).path("role").asText());
        assertEquals("contract", citations.get(1).path("role").asText());
        assertFalse(Files.readString(context).contains("fixture-private"));
        assertAdvisory(review);
    }

    private static void assertCitation(Path root, JsonNode citation, String path, int line, String value, String commit)
            throws Exception {
        assertEquals(path, citation.path("path").asText());
        assertEquals(line, citation.path("startLine").asInt());
        assertEquals(line, citation.path("endLine").asInt());
        assertEquals(value, citation.path("value").asText());
        assertEquals(commit, citation.path("indexedCommit").asText());
        byte[] bytes = Files.readAllBytes(root.resolve(path));
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                citation.path("sourceSha256").asText());
        assertTrue(citation.path("excerpt").asText().contains(value));
    }

    private static void assertAdvisory(JsonNode review) {
        assertTrue(review.path("advisoryOnly").asBoolean());
        assertEquals("NONE", review.path("gateEffect").asText());
        assertFalse(review.path("canChangeReleaseIndex").asBoolean(true));
        assertFalse(review.path("canDeclareTestPass").asBoolean(true));
        assertEquals("NOT_EVALUATED", review.path("testReceiptState").asText());
    }

    @Test
    void insufficientEvidenceIsExplicitAndOutputCannotBeWrittenIntoTheRepository(@TempDir Path temp) throws Exception {
        Path root = project(temp, "0.2.0", "unrelated-module", "ro.unrelated.UnrelatedComponentTest");
        write(root, "docs/feedback/J6-UI-01-INTERACTIVE-TABLE.md", "# Feedback\nA completely different card table topic.\n");
        String base = commit(root, "base release");
        write(root, "unrelated-module/src/main/java/XylophoneQuartz.java", "class XylophoneQuartz { String payload = \"nebula zircon\"; }\n");
        String candidate = commit(root, "unrelated change");

        JsonNode review = ReleaseImpactRag.review(root, "0.1.1", base, candidate, "0.2.0", temp.resolve("context.json"));

        assertEquals("NEEDS_EVIDENCE", review.path("contextState").asText());
        assertEquals("NONE", review.path("gateEffect").asText());
        assertEquals("NOT_EVALUATED", review.path("testReceiptState").asText());
        assertThrows(IllegalArgumentException.class, () -> ReleaseImpactRag.review(root, "0.1.1", base, candidate,
                "0.2.0", root.resolve("release/impact-context.json")));
    }

    private static void assertProvenanceRanking(JsonNode review, String affected, String unchanged) {
        List<String> paths = new java.util.ArrayList<>();
        review.path("retrievedSources").forEach(source -> paths.add(source.path("path").asText()));
        assertEquals(affected, paths.getFirst());
        int architecture = paths.indexOf("docs/architecture/ARCHITECTURE.md");
        assertTrue(architecture > 0 && paths.indexOf(unchanged) > architecture, paths.toString());
    }

    private static Path project(Path temp, String version, String module, String testClass) throws Exception {
        Path root = temp.resolve("project");
        Files.createDirectories(root);
        write(root, "release/index.json", "{\"schemaVersion\":1,\"currentVersion\":\"" + version + "\",\"state\":\"DRAFT\"}\n");
        write(root, "README.md", "# Fixture\n");
        write(root, "release/README.md", "# Release catalog\n");
        var suiteIndex = JSON.createObjectNode();
        suiteIndex.put("schemaVersion", 1);
        suiteIndex.putArray("maven");
        suiteIndex.putArray("android");
        write(root, "config/release-test-suites.json", JSON.writeValueAsString(suiteIndex));
        addSuite(root, "android", module, testClass);
        git(root, "init", "-b", "main");
        git(root, "config", "user.name", "Release test");
        git(root, "config", "user.email", "release-test@example.invalid");
        return root;
    }

    private static void addSuite(Path root, String scope, String module, String testClass) throws Exception {
        Path inventory = root.resolve("config/release-test-suites.json");
        JsonNode contract = JSON.readTree(inventory.toFile());
        ((ArrayNode) contract.path(scope)).addObject().put("module", module).put("testClass", testClass);
        JSON.writerWithDefaultPrettyPrinter().writeValue(inventory.toFile(), contract);
    }

    private static String commit(Path root, String message) throws Exception {
        git(root, "add", "--all");
        git(root, "commit", "-m", message);
        return git(root, "rev-parse", "HEAD").trim();
    }

    private static void write(Path root, String relative, String content) throws Exception {
        Path path = root.resolve(relative);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    private static String git(Path root, String... args) throws Exception {
        var command = new java.util.ArrayList<>(List.of("git", "-C", root.toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        process.getInputStream().transferTo(output);
        int code = process.waitFor();
        String result = output.toString(StandardCharsets.UTF_8);
        if (code != 0) throw new IllegalStateException("git failed: " + String.join(" ", args) + "\n" + result);
        return result;
    }
}
