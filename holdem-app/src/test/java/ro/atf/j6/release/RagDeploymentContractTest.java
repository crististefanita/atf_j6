package ro.atf.j6.release;

import atf.reporting.contract.AtfAssertion;
import atf.testkit.AtfComponentTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

@AtfComponentTest
class RagDeploymentContractTest {
    @Test
    void ragPlanIsConsumedByTheMainBranchDownloadGate() throws Exception {
        Path root = projectRoot();
        String rag = Files.readString(root.resolve("holdem-app/src/test/java/ro/atf/j6/release/ReleaseImpactRag.java"));
        String runner = Files.readString(root.resolve("scripts/Build-HoldemRelease.ps1"));
        String gate = Files.readString(root.resolve("scripts/Test-HoldemRagDeployment.ps1"));
        String reader = Files.readString(root.resolve("scripts/Test-PublishedHoldemDownload.ps1"));
        String guide = Files.readString(root.resolve("docs/release/ATF_Release.md"));
        var checks = new ArrayList<AtfAssertion>();

        add(checks, "RAG emits a pending deployment plan", "plan fixes branch, README, index, tag and APK names",
                rag.contains("addDeploymentPlan(result, root, candidateVersion)")
                        && rag.contains("plan.put(\"state\", \"PENDING\")")
                        && rag.contains("plan.put(\"primaryReadme\", \"README.md\")")
                        && rag.contains("plan.put(\"verificationScript\", \"scripts/Test-HoldemRagDeployment.ps1\")"));
        add(checks, "RAG context stays advisory until deployment", "runner validates advisory context and not a test verdict",
                runner.contains("$impactContext.gateEffect -ne 'NONE'")
                        && runner.contains("$impactContext.testReceiptState -ne 'NOT_EVALUATED'"));
        add(checks, "Deployment gate requires the remote default branch", "clean local and remote branch identities match",
                gate.contains("symbolic-ref --quiet --short refs/remotes/origin/HEAD")
                        && gate.contains("ls-remote origin")
                        && gate.contains("$head -cne $mainCommit"));
        add(checks, "Deployed README and tag match the RAG candidate", "current version route and qualified source commit",
                gate.contains("$remoteReadme") && gate.contains("$expectedApk")
                        && gate.contains("$tagCommit -cne $context.candidateCommit"));
        add(checks, "Final download is checked against the qualified APK", "authenticated APK and checksum are verified",
                gate.contains("deployment-download/download-receipt.json")
                        && gate.contains("$apkReceipt.remoteReadmeBlob -cne $mainReadmeBlob")
                        && gate.contains("$apkReceipt.sha256 -cne $qualification.sha256")
                        && gate.contains("$checksumReceipt.apkSha256 -cne $qualification.sha256")
                        && reader.contains("authenticatedChecksumDownload=$checksumReceipt")
                        && guide.contains("Test-PublishedHoldemDownload.ps1"));
        add(checks, "Release guide names the final RAG deploy gate", "same verifier and receipt are documented",
                guide.contains("Test-HoldemRagDeployment.ps1") && guide.contains("rag-deployment-receipt.json"));

        Path reports = Path.of(System.getProperty("atf.test.reportDirectory", "target/atf-reports"));
        Files.createDirectories(reports);
        ReleaseAuditExtent.write(reports.resolve("J6RagDeploymentContract-" + System.currentTimeMillis() + ".html"),
                "J6RagDeploymentContract", "RAG plan, default branch, main README and authenticated APK download", checks);
        assertTrue(checks.stream().allMatch(AtfAssertion::passed), () -> checks.stream()
                .filter(check -> !check.passed()).map(check -> check.name() + ": " + check.actual()).toList().toString());
    }

    private static void add(List<AtfAssertion> checks, String name, String expected, boolean passed) {
        checks.add(new AtfAssertion(name, expected, passed ? expected : "contract not wired", passed));
    }

    private static Path projectRoot() {
        Path root = Path.of("").toAbsolutePath();
        while (root != null && !Files.isRegularFile(root.resolve("release/index.json"))) root = root.getParent();
        if (root == null) throw new IllegalStateException("J6 release catalog not found");
        return root;
    }
}
