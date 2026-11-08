# J6-REL-04 — Acoperirea Extent a suitelor componentă și E2E

**Prioritate:** P1  
**Stare:** Verificat la calificarea ediției 0.2.0. Rapoartele componentă și E2E sunt în dosarul ediției.

## Constatare și rezultat

Calificarea istorică `v0.1.1` a trecut 13 suite Maven / 80 teste și 4 suite Android JVM / 27 teste. Rapoartele ei rămân neschimbate și nu trebuie confundate cu verificarea sursei curente.

Runnerul folosește acum inventarul versionat [release-test-suites.json](../../config/release-test-suites.json) și publică prin J0 `atf-reporting` două rapoarte componentă distincte de rapoartele Android native E2E. `ComponentSuiteExtentPublisher` reconciliază clasele și cazurile JUnit, publică outcome, durată, hash-ul XML și diagnosticele redacted, apoi inspectează raportul proaspăt cu `ReportArtifactInspector`. Suita lipită, lipsă, duplicată, cu număr diferit, `SKIP`, `FAIL` sau XML invalid nu poate deveni PASS.

Ediția 0.2.0 a exercitat contractul complet pe un candidat curat. Dosarul leagă rapoartele de hash-urile din manifest și separă turneul pe varianta sanity de instalarea APK-ului Release. [Rapoartele ediției](../../release/0.2.0/reports/README.md)

## Contract păstrat

- Unicul renderer HTML Extent este `atf-reporting`; adaptorul J6 doar mapează XML JUnit către contractele și profilul de inspecție ATF.
- Inventarul este verificat față de clasele JUnit reale de testul `ComponentSuiteExtentPublisherTest`; adăugarea unei suite fără actualizarea contractului oprește verificarea.
- Maven și Android au câte un raport componentă. Turneul nativ și smoke-ul APK-ului Release au rapoarte E2E distincte, cu acțiuni, aserțiuni și capturi încorporate.
- XML Surefire/Gradle și logurile brute rămân în spațiul extern al rulării. Numai rapoartele Extent selectate pentru o ediție calificată se copiază în `release/<versiune>/reports/`.
- Parserul XML dezactivează DTD/entități externe și pune limită la dimensiunea inputului. Diagnosticele sunt limitate și trecute prin `SensitiveDataRedactor`.

## Referințe

- [Inventarul suitelor](../../config/release-test-suites.json)
- [Adaptorul Extent pentru suite JUnit](../../holdem-app/src/test/java/ro/atf/j6/reporting/ComponentSuiteExtentPublisher.java)
- [Testele adaptorului și ale inventarului](../../holdem-app/src/test/java/ro/atf/j6/reporting/ComponentSuiteExtentPublisherTest.java)
- [Publicarea E2E Android](../../holdem-android/tools/PublishTournamentExtent.java)
- [Runnerul de calificare](../../scripts/Build-HoldemRelease.ps1)
- [Strategia de testare](../testing/TEST_STRATEGY.md)
- [Procesul de release](../release/ATF_Release.md)
