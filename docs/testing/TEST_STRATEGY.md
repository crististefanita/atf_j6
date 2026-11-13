# Strategie de testare

## Pe scurt

Verifică regula în componenta care o deține. Folosește probe Android pentru comportamentul care depinde de randare, lifecycle sau accesibilitate. La release, manifestul leagă rezultatele și dovezile de binarul testat.

## Niveluri

| Nivel | Ce verifică | Exemplu |
|---|---|---|
| `@AtfUnitTest` | Calcul determinist, cu rezultat așteptat explicit. | Estimarea equity cu seed fix. |
| `@AtfComponentTest` | Contractul unui motor, al unei politici sau al unui adaptor cu intrări controlate. | Ordinea acțiunilor, poturi secundare și replay. |
| `@AtfIntegrationTest` | Contract între module, fișiere sau procese. | Proveniență Git și inventarul rapoartelor. |
| Android JVM | Logica sesiunii și a prezentării, prin JUnit 4. | Checkpoint, redarea jurnalului și anularea unei mutări automate. |
| Android nativ | Interacțiunea pe APK-ul identificat. | Focus TalkBack, geometrie, animație, instalare și reluare. |

Alege un oracol independent pentru rezultatul verificat. Include o intrare limită sau respinsă, păstrează seed-ul, ceasul ori fișierele sub control și trimite în Extent rezultatul așteptat și rezultatul observat. [Inventarul suitelor](../../config/release-test-suites.json)

- În componente, verifică rezultatul public al regulii și tratează cadrul întârziat, starea veche sau termenul de așteptare cu date controlate.
- În instrumentare, pornește dintr-o stare reproductibilă și acționează prin controlul vizibil sau accesibil. Verifică schimbarea de focus, cadrul afișat și rezultatul acțiunii. [Probe Android](#interfața-android-locală)

### Rulare JVM și Extent

Rulează într-un checkout izolat și folosește un director extern nou pentru rapoarte. Variabila `HOLDEM_DATA_ROOT` indică rădăcina externă de date.

```powershell
mvn -o -B -ntp "-Dgroups=unit | component | integration" test
gradle -p holdem-android --offline :app:testSanityUnitTest
.\scripts\Publish-HoldemJvmExtent.ps1 -ReportDirectory "$env:HOLDEM_DATA_ROOT/jvm-audit"
```

Publisherul compară XML-urile cu inventarul și raportează suitele lipsă, duplicate sau incomplete. Verifică în Extent nivelul, numărul de cazuri și SHA-256 al fiecărui XML. [Guvernanță locală](../governance/README.md)

## Motor, AI și arenă

| Schimbare | Probă de bază |
|---|---|
| Reguli și decontare | Compară sumele pe loc, restituirile, poturile secundare și starea după acțiuni ilegale. Rulează fixture-ul all-in și auditul independent. |
| Politici și estimator | Fixează seed-ul, adversarii și bugetul. Compară legalitatea, egalitățile, incertitudinea și deciziile pe observații echivalente. |
| Strategie și turnee | Compară candidatul și controlul pe distribuții pereche, cu lot de evaluare distinct de cel de reglaj. Păstrează configurațiile, seed-urile, rezultatele și replay-urile. |
| Online | Verifică parsarea configurației și calea de inspecție `dry-run`. |

[Reguli și oracole](../requirements/DOMAIN_REQUIREMENTS.md) · [Evaluarea politicilor](../requirements/AI_REQUIREMENTS.md) · [Contractul turneelor](../requirements/TOURNAMENT_RULES.md)

## Interfața Android locală

| Contract | Componentă JVM | Probă nativă |
|---|---|---|
| Lifecycle și acțiune automată | Revocarea delegării, rezultat întârziat, aceeași tură, retry și checkpoint. | Control manual, rotație, fundal și reluare. |
| Chenare, showdown și premii | Proiecția pe loc, contribuțiile, premiile, restituirile și limitele ferestrei. | Fereastră compactă, cărți decisive, marker animat și TalkBack. |
| Jurnal și editor | Mutarea unică, contribuția cumulată, păstrarea derulării și destinația de focus după Check. | Tastatură, dialog, focus și vizibilitatea rândului. |
| Clasament și Realizări | Deduplicare, proveniență manuală, filtrare și scriere eșuată. | Progres vizibil, recrearea ecranului și restart. |

Temporizarea comună a turei automate este `550 ms` <!-- rag:contract android.turn-pacing --> după primul cadru vizibil. Proba `seat-metrics` folosește `320 dp` <!-- rag:contract android.seat-metrics.viewport-width -->, font 2,0 și TalkBack. [Comenzi și profiluri Android](../../holdem-android/README.md#verificare-locală)

### Sincronizarea jurnalului după Check

Componenta verifică păstrarea derulării în aceeași mână și mutarea focusului de la buton la locul propriu după Check. Proba nativă confruntă noul rând, actorul și totalul, inclusiv cu TalkBack. [Starea ferestrei mesei](../../holdem-android/app/src/test/java/ro/atf/holdem/android/TableViewportPositionComponentTest.java)

### Dovezi native pe fereastră compactă

| Probă | Rezultat observabil | APK |
|---|---|---|
| `seat-metrics` | Nume, sume, chenare și focusul actorului cu TalkBack activ. | `release` |
| `betting-ledger-talkback` | Focusul pleacă de la Check și ajunge la locul propriu. Rândul unic de jurnal este complet vizibil, cu suma și totalul. | `sanity` |
| `settlement-compact-award` | Rezumatul include și beneficiarul din afara ferestrei. Markerul pornește din pot și ajunge la locul vizibil. | `sanity` |

Verifică fereastra măsurată, scala fontului și SHA-256 din receipt. Raportul Extent păstrează aserțiunile și PNG-urile native, iar calificarea leagă fiecare probă de varianta declarată. [Profiluri și audit](../release/ATF_Release.md#auditul-artefactelor-după-rulare)

La decontare, urmărește mai întâi cadrele premiului și apoi dialogul de progres. În smoke-ul localizat, verifică textul dialogului și progresul salvat. Sumarul poate afișa un alt obiectiv obținut în aceeași mână. [Realizări de joc](#realizări-de-joc)

### Confirmarea unui Raise din editor

Componenta respinge sumele nevalide și verifică o singură intrare la confirmare repetată. Profilurile native standard, compact și compact cu TalkBack verifică editorul, suma, contribuția și rândul vizibil. [Contractul jurnalului](../requirements/PRODUCT_REQUIREMENTS.md#istoricul-pariurilor)

### Realizări de joc

Componenta verifică eligibilitatea, sursa mutării, pragul, deduplicarea și notificarea după salvare. Proba nativă verifică interfața pe ecranul randat. [Criterii și calibrare](../design/achievements/README.md#acceptanță-înainte-de-lansare)

Pentru frecvență și timp până la deblocare, rulează masa locală fără randare cu loturi distincte de reglaj și confirmare. Analizează ocaziile pe dimensiune de masă și dificultate, apoi publică verdictul pe obiectiv. [Planul cohortelor](../../config/j6-live-achievement-calibration.yaml) · [Runner](../../scripts/run-live-achievement-calibration.ps1) · [Analiză](../../scripts/analyze-live-achievement-calibration.cjs) · [Publisher Extent](../../holdem-app/src/test/java/ro/atf/j6/release/PublishAchievementCalibrationExtent.java)

### Replay pentru catalogul extins

Rulează corpusul de replay cu planul versionat. Compară așteptările de creditare și păstrează separat rezultatele calibrării. [Planul corpusului](../../config/j6-achievement-replay.yaml) · [Runner](../../scripts/run-achievement-replay-corpus.ps1) · [Analiză](../../scripts/analyze-achievement-replays.cjs)

## Calificare și guvernanță

RAG propune suitele din diff și inventar. Confirmă selecția, execută testele și verifică rapoartele Extent, receipt-urile și identitatea APK-ului conform procesului de release. [Context RAG](../release/ATF_Release.md#contextul-rag-consultativ) · [Calificare Android](../release/ATF_Release.md#calificare-reproductibilă)

La schimbări documentare, confruntă contractul cu sursa și rulează guvernanța locală. Pune comenzile la owner, rezultatele în dosarul ediției și lacunele în feedback. [Guvernanță](../governance/README.md) · [Feedback activ](../feedback/README.md)
