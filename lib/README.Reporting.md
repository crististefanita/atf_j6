# Reporting — dovezi și rapoarte auditabile

## Pe scurt

Reporting publică datele, comparațiile și verdictul callerului în Extent/fallback, fără Core ori Kit.

| Nevoie | API |
|---|---|
| Rulare individuală/clasă/suită | `AssertionAuditRun` |
| Invocare | `run.open(Identity)` → `AssertionAuditReport` |
| Ports / fallback | `AtfReportPorts` / `HtmlReportSession` |
| HTTP observat | `request.execution(ReportExecutionEvidence)`, cu `ReportResponse` |
| Capturi | `UiExtentReportSession`; PNG integral |

## Exemplu minim

Individual: `try (var run = open(output)) { publish(run, actual); }`. Suita păstrează run-ul și deschide un scenariu per invocare:

```java
import atf.reporting.audit.AssertionAuditRun;
import atf.reporting.audit.AssertionAuditReport;
import atf.reporting.configuration.ReportingSettings;
import atf.reporting.contract.AtfAssertion;
import atf.reporting.contract.AtfReportStatus;
import atf.reporting.extent.session.ExtentReportSession;
import atf.reporting.security.SensitiveDataRedactor;
import java.nio.file.Path;
import java.util.List;

public static AssertionAuditRun open(Path output) {
    var settings = ReportingSettings.defaults();
    var redactor = new SensitiveDataRedactor();
    return new AssertionAuditRun(new ExtentReportSession(output, settings, redactor), redactor, settings);
}

public static void publish(AssertionAuditRun run, String actual) {
    boolean passed = "ready".equals(actual);
    var status = passed ? AtfReportStatus.PASS : AtfReportStatus.FAIL;
    var identity = new AssertionAuditReport.Identity("HealthContract", "example", "state is ready", "state", List.of("component"));
    try (var report = run.open(identity)) {
        report.phase("TEST BODY", List.of(new AtfAssertion("State", "ready", String.valueOf(actual), passed)), status, "");
        report.complete(status);
    }
    if (!passed) throw new AssertionError("State differs; inspect the report");
}
```

## Scope și integrare

| Owner | Închidere și limită |
|---|---|
| AssertionAuditRun | Deține sesiunea; close publică HTML-ul final. |
| run.open(identity) | Împrumută sesiunea; close închide scenariul invocării. |
| Constructorul AssertionAuditReport(session, ...) | Deține și închide sesiunea proprie. |

- Aceleași settings/redactor în run și renderer; preview implicit **240**, citit prin assertionMaxDisplayCharacters(). Valorile complete preced preview-ul.
- Exemplul urmează thread-ul sesiunii; AssertionAuditRun.extent(...) serializează publicarea pentru apelanți concurenți.
- Kit colectează comparația JUnit evaluată o dată; Reporting publică. Callerul păstrează verdictul, cauza și timing-ul prin phase(..., timing).
- Fails indexează cauza terminală la rândul ACTION sau OBSERVE care a eșuat. Tests păstrează și diagnosticul colapsabil, iar comparațiile independente rămân distincte.
- PNG-ul păstrează întregul viewport/display; captura și zoom-ul aparțin adaptorului. Capturile identice din același pas păstrează o singură dovadă și primul caption.
- După close, ReportArtifactInspector.inspect(path, profile) califică inventarul și resursele embedded; diagnosticul localizează dependențele externe. Căile text rămân valide. Inspectorul este static; randarea și interacțiunile se verifică separat în browser.
- Browserul aparține callerului: separați diagnosticul de discovery JUnit de execuție, închideți contextul înaintea browserului și păstrați cauza inițială la cleanup.

Durata HTTP provine din `ReportResponse.duration()`; `detail(...)` păstrează textul callerului, fără să îl transforme într-o măsurătoare de transport.

---

Consumerul verifică local artefactele Extent, capturile încorporate și contractul raportului. Pentru schimbarea unei perechi binare, urmează [procesul de mentenanță și feedback](README.md#mentenanță-și-feedback).
