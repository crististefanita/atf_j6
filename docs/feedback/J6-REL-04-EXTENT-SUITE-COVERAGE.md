# J6-REL-04 — Acoperirea Extent a suitelor componentă și E2E

**Prioritate:** P1  
**Stare:** Inventarul și publicarea sunt implementate; snapshotul curent are rezultate componente și Extent E2E nativ verificat. Includerea în dosarul unei ediții cere calificarea unui candidat curat `DRAFT`.

## Constatare și rezultat

Calificarea istorică `v0.1.1` a trecut 13 suite Maven / 80 teste și 4 suite Android JVM / 27 teste. Rapoartele ei rămân neschimbate și nu trebuie confundate cu verificarea sursei curente.

Runnerul folosește acum inventarul versionat [release-test-suites.json](../../config/release-test-suites.json) și publică prin J0 `atf-reporting` două rapoarte componentă distincte de rapoartele Android native E2E. `ComponentSuiteExtentPublisher` reconciliază clasele și cazurile JUnit, publică outcome, durată, hash-ul XML și diagnosticele redacted, apoi inspectează raportul proaspăt cu `ReportArtifactInspector`. Suita lipită, lipsă, duplicată, cu număr diferit, `SKIP`, `FAIL` sau XML invalid nu poate deveni PASS.

Verificarea izolată a snapshotului live din 2026-11-08 a trecut:

- Maven: **16/16 suite, 97/97 cazuri PASS**.
- Android JVM: **5/5 suite, 39/39 cazuri PASS** în snapshotul curent, inclusiv testele de clasament și guvernanță API minim.
- Tournament sanity pe Android API 28: **58/58 aserțiuni PASS**, **8 capturi native**; raportul `android-tournament-extent.html` a trecut `ReportArtifactInspector` și încorporează capturile.
- Rularea, XML-urile și raportul sunt păstrate în rădăcina externă de dovezi, în afara surselor.
- Rapoartele Extent ale suitelor componentă nu fac parte din acest director curent; ele trebuie regenerate și inspectate pentru snapshotul candidat înainte de atașarea la o calificare.
- `PublishTournamentExtent` publică probele Android native E2E separat; rezultatele JVM nu sunt amestecate în raportul UI.

Aceste rezultate sunt verificări de snapshot, nu o nouă calificare `PREPARED`. `release/index.json` indică `v0.1.1 PREPARED` și `published: false`; nu s-a publicat ediție nouă și raportul de lucru nu a fost copiat în istoricul release-ului.

## Contract păstrat

- Unicul renderer HTML Extent este `atf-reporting`; adaptorul J6 doar mapează XML JUnit către contractele și profilul de inspecție ATF.
- Inventarul este verificat față de clasele JUnit reale de testul `ComponentSuiteExtentPublisherTest`; adăugarea unei suite fără actualizarea contractului oprește verificarea.
- Maven și Android au câte un raport componentă. Turneul nativ și smoke-ul APK-ului Release au rapoarte E2E distincte, cu acțiuni, aserțiuni și capturi încorporate.
- XML Surefire/Gradle și logurile brute rămân în spațiul extern al rulării. Numai rapoartele Extent selectate pentru o ediție calificată se copiază în `release/<versiune>/reports/`.
- Parserul XML dezactivează DTD/entități externe și pune limită la dimensiunea inputului. Diagnosticele sunt limitate și trecute prin `SensitiveDataRedactor`.

## Lucru rămas

Următoarea calificare curată trebuie să execute `Build-HoldemRelease.ps1` dintr-un commit cu catalog `DRAFT`, să genereze și să inspecteze ambele rapoarte componentă pentru exact XML-urile candidate, apoi să le atașeze în noul `qualification.json`. Rapoartele E2E native rămân separate. Snapshotul curent a rulat sanity nativ și a construit APK-ul semnat, dar nu este o ediție publicată și nu substituie calificarea reproductibilă dintr-un commit curat.

## Referințe

- [Inventarul suitelor](../../config/release-test-suites.json)
- [Adaptorul Extent pentru suite JUnit](../../holdem-app/src/test/java/ro/atf/j6/reporting/ComponentSuiteExtentPublisher.java)
- [Testele adaptorului și ale inventarului](../../holdem-app/src/test/java/ro/atf/j6/reporting/ComponentSuiteExtentPublisherTest.java)
- [Publicarea E2E Android](../../holdem-android/tools/PublishTournamentExtent.java)
- [Runnerul de calificare](../../scripts/Build-HoldemRelease.ps1)
- [Strategia de testare](../testing/TEST_STRATEGY.md)
- [Procesul de release](../release/ATF_Release.md)
