# J6-REL-04 · Extent cere aserțiuni native pentru fiecare parcurs

**Prioritate:** P1

**Stare:** Închis. Protecția este inclusă în 0.2.2.

**Owner:** Adaptorul de raportare Android J6

## Rezultat

Publisherul cere o listă de aserțiuni native și verifică prezența unică a observațiilor obligatorii pentru turneu și smoke. Inventarul capturilor și identitatea APK-ului au verificări proprii. [Contractul și probele](../testing/TEST_STRATEGY.md)

Calificarea 0.2.2 include proba compactă cu TalkBack. Un receipt complet însoțit de o captură uniformă produce Extent `FAIL`, cu verdict și diagnostic în concluzie. [Rapoartele 0.2.2](../../release/0.2.2/reports/README.md)

- Maven în TMP a trecut 15/15 probe, inclusiv 5 de guvernanță și 7 pentru publisher. [Testele publisherului](../../holdem-app/src/test/java/ro/atf/j6/reporting/NativeTournamentExtentPublisherTest.java)
- Replay API 28 din calificarea sursei `828b461`. Turneul normal și profilul scund au produs `PASS` cu 76 de aserțiuni și 9 capturi fiecare. Smoke-ul Release a produs `PASS` cu 7 aserțiuni și 2 capturi.
- Receipt-ul de turneu fără aserțiuni a produs Extent `FAIL` și cod 1, cu toate cele 9 capturi. Receipt-ul real întrerupt a produs `FAIL` cu diagnosticul și cele 2 capturi. Inspectorul a validat structura celor cinci rapoarte de replay.

Rapoartele de replay sunt dovezi externe ale corecției. [Dosarul calificării 0.2.1](../../release/0.2.1/reports/README.md)
