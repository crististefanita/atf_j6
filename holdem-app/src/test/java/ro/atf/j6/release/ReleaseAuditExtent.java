package ro.atf.j6.release;

import atf.reporting.audit.AssertionAuditRun;
import atf.reporting.audit.AssertionAuditReport;
import atf.reporting.configuration.ReportingSettings;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.artifact.ReportArtifactInspector;
import atf.reporting.artifact.ReportArtifactProfile;
import atf.reporting.model.timing.ReportPhaseTiming;
import atf.reporting.security.SensitiveDataRedactor;
import atf.reporting.contract.AtfAssertion;
import java.nio.file.Path;
import java.util.List;

final class ReleaseAuditExtent {
    private ReleaseAuditExtent() { }

    static void write(Path reportPath, String testClass, String scenario, List<AtfAssertion> checks) throws Exception {
        AtfReportStatus status = !checks.isEmpty() && checks.stream().allMatch(AtfAssertion::passed) ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
        var settings = ReportingSettings.defaults();
        var redactor = new SensitiveDataRedactor();
        try (var run = new AssertionAuditRun(new ExtentReportSession(reportPath, settings, redactor), redactor, settings);
             var invocation = run.open(new AssertionAuditReport.Identity(testClass, "ro.atf.j6.release",
                     scenario, "release", List.of("component", "governance", "offline")))) {
            invocation.phase("TEST BODY", checks, status, "", new ReportPhaseTiming(java.time.Duration.ofMillis(1), 1));
            invocation.complete(status);
        }
        var profile = new ReportArtifactProfile(ReportArtifactProfile.Renderer.EXTENT, List.of(
                new ReportArtifactProfile.Scenario(testClass, scenario,
                        status, List.of(new ReportArtifactProfile.Phase("TEST BODY", status,
                        List.of(new ReportArtifactProfile.Assertions("Component assertions",
                                checks.stream().map(check -> new ReportArtifactProfile.Comparison(check.name(), check.passed())).toList())))))));
        var inspection = ReportArtifactInspector.inspect(reportPath, profile);
        if (!inspection.isCompliant()) throw new IllegalStateException(inspection.violations().toString());
    }
}
