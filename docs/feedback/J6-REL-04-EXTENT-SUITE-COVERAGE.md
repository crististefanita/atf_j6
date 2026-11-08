<<<<<<< Updated upstream
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
=======
# J6-REL-04 · Respinge Extent nativ fără aserțiuni de joc

Contractul livrat este în [strategia de testare](../testing/TEST_STRATEGY.md#acceptanța-raportului-nativ). Referința de mai jos păstrează proveniența verificărilor încheiate pentru `b136638` pe `release/holdem-0.2.1`.

<details>
<summary>Dovada istorică a închiderii</summary>

## Rezultat verificat

- Același receipt de turneu cu `assertions: []` și cu APK-ul și capturile originale producea `PASS` înainte de corecție. Acum raportul afișează `FAIL` și `0 native assertions`, cu capturile păstrate și cod de ieșire 1 după inspecție.
- Cele 7 probe ale publisherului și cele 8 probe de guvernanță și integrare componente au trecut în TMP. Cazurile negative acoperă lista goală sau absentă, aserțiunea obligatorie omisă sau duplicată, captura înlocuită și folosirea aserțiunilor smoke pentru turneu. [Testele](../../holdem-app/src/test/java/ro/atf/j6/reporting/NativeTournamentExtentPublisherTest.java)
- Replay-ul receipt-urilor API 28 a păstrat `PASS` pentru 0.2.0 (63 de aserțiuni și 8 capturi pentru turneu) și 0.2.1 la `2811a0b` (76 de aserțiuni și 9 capturi). Smoke-ul are 7 aserțiuni și 2 capturi pentru fiecare. Replay-ul receipt-ului 0.2.1 întrerupt la `Call` a păstrat `FAIL`, eroarea și captura parțială.

## Integrare pe 0.2.1

- Build-ul Maven în TMP, pe baza `453288f`, a trecut 15/15 probe. Replay-ul calificării `828b461` a produs trei rapoarte `PASS` pentru turneul normal, profilul scund și smoke. Receipt-ul fără aserțiuni și receipt-ul nativ întrerupt au produs Extent `FAIL` cu capturile păstrate. [Strategia de testare](../testing/TEST_STRATEGY.md)

</details>
>>>>>>> Stashed changes
