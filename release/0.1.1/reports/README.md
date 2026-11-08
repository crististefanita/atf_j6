# Rapoarte 0.1.1

## Pe scurt

Acest index este dedicat exclusiv calificării ediției 0.1.1. Rapoartele HTML Extent includ capturile native inline și aserțiunile auditabile; nu duplică PNG-uri, loguri brute sau directoare de build.

| Raport | Acoperire | Rezultat |
|---|---|---|
| [android-tournament-extent.html](android-tournament-extent.html) | Turneu prin controalele UI, până la victorie și eliminare | PASS · API 28 · 42 aserțiuni · 5 capturi inline |
| [android-release-smoke-extent.html](android-release-smoke-extent.html) | Instalarea APK-ului Release și navigarea de bază | PASS · API 28 · 7 aserțiuni · 2 capturi inline |
| [j6-library-component.html](j6-library-component.html) | Contractele componentelor și proveniența bibliotecilor | PASS · 1 test |
| [J6AndroidRuntimeApiGovernance.html](J6AndroidRuntimeApiGovernance.html) | Compatibilitatea API-urilor Java cu `minSdk 26` | PASS · 1 test · Extent inspectat |
| [J6MainReadmeGovernance.html](J6MainReadmeGovernance.html) | Exact două linkuri în README-ul principal, fără zgomot | PASS · 1 test · Extent inspectat |
| [J6ReleaseDownloadRouteGovernance.html](J6ReleaseDownloadRouteGovernance.html) | Contractul rutelor de download pe fiecare stare | PASS · 1 test · Extent inspectat |
| [J6ReleaseGovernance.html](J6ReleaseGovernance.html) | Identitatea APK-ului, semnarea și receipt-ul autenticat | PASS · 1 test · Extent inspectat |

## Calificarea inclusă

- Maven: 13 suite și 80 teste; fără eșecuri, erori sau teste omise.
- Android JVM: 4 suite și 27 teste; fără eșecuri, erori sau teste omise.
- Lint Release: fără avertismente.
- Sanity Android API 28: două trasee, 49 de aserțiuni și 7 capturi încorporate în rapoartele Extent.
- Descărcarea assetului Release autentificat: răspuns complet `200`, identitate exactă cu APK-ul calificat.
- Cererile anonime directe către GitHub au expirat fără status HTTP; nu există receipt public, iar ediția păstrează starea `PREPARED`.

Build-urile, cache-urile și logurile rămân în run-ul extern de release. [Calificarea JSON](../qualification.json) și manifestul identifică numai rapoartele păstrate în dosarul ediției.

