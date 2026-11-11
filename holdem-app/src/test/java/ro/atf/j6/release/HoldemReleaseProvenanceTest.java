package ro.atf.j6.release;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HoldemReleaseProvenanceTest {
    @TempDir Path scratch;

    @Test void authenticatedDownloadPinsRemoteEvidenceBeforeAnyAssetRequest() throws Exception {
        run("PublishedHoldemDownload.Tests.ps1");
    }

    @Test void continuationAllowsOnlyAuditedEvidenceChangesInGitHistory() throws Exception {
        run("HoldemContinuation.Tests.ps1");
    }

    private void run(String suite) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("release/index.json"))) {
            root = root.getParent();
        }
        if (root == null) throw new IllegalStateException("J6 release catalog not found");
        Path script = root.resolve("scripts/tests").resolve(suite);
        Path output = scratch.resolve("component.log");
        Path processTemp = Files.createDirectory(scratch.resolve("process-temp"));
        var command = new ProcessBuilder("pwsh", "-NoProfile", "-NonInteractive", "-File",
                script.toString(), "-Scratch", scratch.toString())
                .redirectErrorStream(true).redirectOutput(output.toFile());
        command.environment().put("TMP", processTemp.toString());
        command.environment().put("TEMP", processTemp.toString());
        command.environment().put("TMPDIR", processTemp.toString());
        Process process = command.start();
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new AssertionError("PowerShell component timed out: " + suite);
        }
        String evidence = Files.readString(output);
        assertEquals(0, process.exitValue(), evidence);
        assertTrue(evidence.contains("PASS "), evidence);
    }
}
