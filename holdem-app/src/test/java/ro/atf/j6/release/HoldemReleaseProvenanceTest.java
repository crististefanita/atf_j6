package ro.atf.j6.release;

import atf.testkit.AtfIntegrationTest;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@AtfIntegrationTest
class HoldemReleaseProvenanceTest {
    @TempDir Path scratch;

    @Test void authenticatedDownloadPinsRemoteEvidenceBeforeAnyAssetRequest() throws Exception {
        run("PublishedHoldemDownload.Tests.ps1");
    }

    @Test void continuationAllowsOnlyAuditedEvidenceChangesInGitHistory() throws Exception {
        run("HoldemContinuation.Tests.ps1");
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"all-optionals", "legacy", "missing-profile", "orphan-report", "wrong-apk",
            "changed-capture", "duplicate-capture", "planned", "missing-family", "wrong-required-journey",
            "changed-plan", "missing-plan", "omitted-companion", "duplicate-switch", "wrong-plan-variant",
            "reordered-plan", "omitted-selection", "missing-catalog"})
    void auditsOptionalNativeEvidenceOffline(String scenario) throws Exception {
        run("HoldemNativeEvidenceAudit.Tests.ps1", "-Scenario", scenario);
    }

    private void run(String suite, String... arguments) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("release/index.json"))) {
            root = root.getParent();
        }
        if (root == null) throw new IllegalStateException("J6 release catalog not found");
        Path script = root.resolve("scripts/tests").resolve(suite);
        Path output = scratch.resolve("component.log");
        Path processTemp = Files.createDirectory(scratch.resolve("process-temp"));
        var invocation = new ArrayList<>(List.of("pwsh", "-NoProfile", "-NonInteractive", "-File",
                script.toString(), "-Scratch", scratch.toString()));
        invocation.addAll(List.of(arguments));
        var command = new ProcessBuilder(invocation)
                .redirectErrorStream(true).redirectOutput(output.toFile());
        command.environment().put("TMP", processTemp.toString());
        command.environment().put("TEMP", processTemp.toString());
        command.environment().put("TMPDIR", processTemp.toString());
        Process process = command.start();
        long timeoutSeconds = suite.equals("HoldemContinuation.Tests.ps1") ? 180 : 60;
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("PowerShell component timed out after " + timeoutSeconds + "s: " + suite);
        }
        String evidence = Files.readString(output);
        assertEquals(0, process.exitValue(), evidence);
        assertTrue(evidence.contains("PASS "), evidence);
    }
}
