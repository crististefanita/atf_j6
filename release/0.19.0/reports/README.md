# Rapoarte de calificare 0.19.0

## Pe scurt

Calificarea este **PASS** pentru APK-ul semnat cu SHA-256 `16f4c889645b8e026be78989c4e3b3db83b2fc41aadede6bc9e8e024b915645f`. Rapoartele Extent păstrează expected/actual, proveniența testului și, pentru probele native, capturile originale inline.

## Suite și probe

| Raport | Acoperire | Rezultat |
|---|---|---|
| [Poarta de promovare](promotion-extent.html) | Identitatea binarului, checksum și proveniența | 1 scenariu Extent trecut; 8 verificări Maven ale porții, fără eșecuri |
| [Componente Maven](maven-component-suites-extent.html) | Motor, reguli, YAML, progres, persistență și guvernanță | 32 suite · 222 teste · 0 fail/error/skip |
| [Componente Android JVM](android-component-suites-extent.html) | Catalog, eligibilitate, praguri, progres simultan, deduplicare și reluare | 10 suite · 114 teste · 0 fail/error/skip |
| [Guvernanță documentației](documentation-governance-extent.html) | Secțiuni introductive, proprietatea ghidurilor, legături și feedback activ | 4 teste · PASS · 0 eșecuri |
| [Turneu nativ](android-tournament-extent.html) | Turneu la API 28, profil și progresul realizărilor | 109 aserțiuni · 13 capturi inline · trecut |
| [Smoke APK instalat](android-release-smoke-extent.html) | APK-ul calificat, niveluri și limbi | 82 aserțiuni · 13 capturi inline · trecut |
| [Calibrare offline](achievement-calibration-extent.html) | Loturi pereche, holdout, Normal/Greu și verdict pe fiecare obiectiv | 204 turnee · 15.162 mâini · 1.440 replay-uri pereche |

## Interpretarea calibrării

Calibrarea separă frecvența ocaziilor de frecvența creditării obiectivului. Obiectivele rare sau susținute de puține ferestre sunt raportate ca rare ori necalificate, nu prezentate drept confirmate. Loturile holdout nu sunt folosite pentru reglarea pragurilor.

## Proveniență

- Commit sursă și build calificat: `255897f150c2235a78bdfb7fd74ae9f99abc33df`.
- APK: `HoldemLab-0.19.0.apk` · 880.426 bytes.
- Semnare SHA-256: `30213bddf2a1be800d19c588d5ebdfe22ab6b83d9128faee83bf58e260eab1ce`.
- [Manifestul ediției](../release-validation.json) și [planul nativ](../native-plan.json) leagă rapoartele de candidatul calificat.
