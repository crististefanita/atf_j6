# Console — diagnostic pentru execuție

## Pe scurt

`atf-console` proiectează evenimentele ATF în stdout/stderr: lifecycle, durate și detalii configurabile. Este util pentru urmărirea unei rulări Maven sau IntelliJ; artifactul HTML este produs de [Reporting](README.Reporting.md).

## Exemplu minim

Fragment într-o metodă; politica este explicită, iar stream-ul rămâne în adaptor:

```java
import atf.console.AtfConsoleReporter;
import atf.console.ConsoleSettings;

var console = AtfConsoleReporter.configuredStdout(
        new ConsoleSettings(true, false));
console.info("Catalogul de scenarii a fost încărcat");
```

`configuredStdout()` citește `atf.report.config` → `ATF_REPORT_CONFIG` → `atf.yaml/yml`; opțiunile sunt:

```yaml
reporting:
  console:
    enabled: true
    detailsEnabled: false
```

## Integrare și opțiuni

| Intenție | Alegere |
|---|---|
| Mesaj independent de detalii | `info(...)` |
| Evenimente typed, cu lifecycle și durată | `publish(AtfConsoleEvent)` prin portul Core `AtfConsoleSink` |
| Consolă complet oprită | `enabled: false` sau `ConsoleSettings.disabled()` |
| Calea raportului final în consolă | Constructorul cu `reportPathEnabled: true` și evenimentul `REPORT_PUBLISHED` |
| Politică JUnit per clasă | Adaptorul JUnit Console și adnotările Test Kit; declarați Kit explicit |

Calea cere opt-in, consolă activă și eveniment cu artifact confirmat; `detailsEnabled` controlează doar detaliile.

Console depinde de Core; Kit este opțional pentru legătura JUnit. Proiecția rămâne diagnostică: comparația și verdictul aparțin testului.

---

Mentenanța codului și testelor Console aparține proiectului separat `atf_j0/atf-console`; [feedback-ul și sincronizarea](README.md#mentenanță-și-feedback) pornesc de la J0.
