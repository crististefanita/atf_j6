package ro.atf.j6.release;

import atf.testkit.AtfComponentTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
        JsonNode contract = JSON.readTree(root.resolve("config/release-test-suites.json").toFile());
        ((com.fasterxml.jackson.databind.node.ArrayNode) contract.path("android")).add(JSON.createObjectNode()
                .put("module", module).put("testClass", testClass));
        JSON.writerWithDefaultPrettyPrinter().writeValue(root.resolve("config/release-test-suites.json").toFile(), contract);
        git(root, "init", "-b", "main");
        git(root, "config", "user.name", "Release test");
        git(root, "config", "user.email", "release-test@example.invalid");
        return root;
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
