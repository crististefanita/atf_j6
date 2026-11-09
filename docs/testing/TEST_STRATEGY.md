# Strategie de testare

## Pe scurt

J6 separă oracolul de componentă de probele pe Android. Suita documentară verifică orientarea README și legăturile către documentele responsabile. Regulile editoriale detaliate se verifică separat cu auditul J0.

## Niveluri

### Contracte de componentă

Testele curente acoperă ordinea actorilor, sizing-ul minim, menținerea stării după acțiuni ilegale, all-in-uri scurte cumulative, evaluarea mâinii și decontarea pe straturi. `config/j6-allin-challenges.yaml` este fixture-ul controlat pentru redeschidere, egalitate și jetoane impare; JUnit îl rulează prin motor, iar `audit-allin-challenge.cjs` reface separat acțiunile, clasificarea și plata.

### Politici AI

| Zonă | Acoperire |
|---|---|
| Politici | Replay determinist, acțiuni legale și fallback pentru TAG, calling station, pressure, loose-aggressive, bluff, equity Monte Carlo și `short-stack-shove`. |
| `short-stack-shove` | Declanșare preflop după prag și mână, all-in din meniul legal și revenire la TAG. Cazurile negative acoperă stiva peste prag, mâna slabă și configurația invalidă. |
| Estimator | Egalități, reproducibilitate, cărți duplicate, buget și scăderea equity-ului preflop când crește numărul de adversari activi. |
| Decizie equity | Pot odds, incertitudine, agresiune adversă, ordine postflop după fold și marje diferite în cash și turneu. |
| Istoric public de shove | Limitele `>2..10 BB`, posterior, încălzire, loc fizic, media autorilor măsurați și plafonul în ambele direcții. Contoarele se propagă după mână, iar semnalul rămâne inactiv în cash. |
| Hard | Componenta verifică pragul pentru continuări preflop ieftine și mărimile 1×/2×/3× pot în limitele heads-up, ale stivei efective și ale maximului legal. |
| YAML | Parserul strict încarcă toate profilurile din repository și verifică setul exact de ponderi. |

- Testele de componentă verifică mecanica pragurilor. Estimatorul eșantionează uniform cărțile necunoscute.
- Pentru evaluarea Hard, comparați cu profilul de control la același buget de calcul și pe aceleași distribuții, mese, poziții și tipuri de adversari. Folosiți seed-uri de evaluare diferite de cele de reglaj.

### Aceeași distribuție, buton rotit

- **Distribuție.** `PositionRotationExperimentTest` rotește butonul prin șase locuri pe două distribuții, cu șapte stiluri. Cărțile private rămân atașate locurilor, iar board-ul planificat rămâne același.
- **Observație.** Politica vede numai actorul și informația permisă. JUnit oprește mutarea ilegală, iar dezvăluirea completă apare la final.
- **Oracol.** `audit-position-rotations.cjs` verifică poziția, cărțile actorului, prefixul board-ului și unicitatea cărților. Probele de poziție acoperă butonul, cutoff-ul și heads-up.
- **Control.** Bluff-ul forțat la 100% exercită declanșatorul.

### Arena

| Probă | Contract |
|---|---|
| Turneu, 4–6 locuri | `audit-tournament.cjs` reconstruiește seed-ul, ordinea, legalitatea, clasarea și decontarea independent de motorul Java. |
| Cash, 2–6 locuri | `audit-cash-sessions.cjs` verifică dealerul, blindurile fixe, durata, stivele și conservarea. Candidatul și controlul pornesc cu același seed și primul buton. |
| Politică și lineup | CLI-ul verifică stilul efectiv cu și fără YAML explicit. `TournamentStrategyArenaTest` acoperă rotația rolurilor și jurnalul public, inclusiv lineup-ul adversarial separat. |

`HardPressurePolicyTest` joacă limp-ul în motor și verifică separat perechea premium, mâna slabă, stiva proprie și suma maximă pe care o poate egala adversarul. Testul de configurație verifică încărcarea YAML cu și fără opțiunea din blindul mare și respinge o valoare care nu este booleană.

`MonteCarloEquityEstimatorTest` compară seed-ul implicit cu un reper numeric, la două bugete de eșantionare. Calificarea unei politici repetă aceleași partide în procese JVM separate și compară deciziile, pe lângă auditul independent al legalității și scorului.
| Istoric de shove | `--require-public-shove-history` reconstruiește oportunitățile din stivă și acțiunile legale. Compară numai contoarele mâinilor deja încheiate. |

- Cohortele `equity` și `legacy-equity` păstrează locurile candidate și seed-urile. Pragurile loose-aggressive se compară prin perechi YAML.
- `run-button-position-tournaments.ps1` rotește butonul inițial, iar `audit-button-rotations.cjs` verifică fluxul de seed-uri și perechile cu același offset. Rotația redistribuie cărțile între locuri. Fixture-ul controlat păstrează cărțile private la aceleași locuri.
- `compare-cash-sessions.cjs` măsoară cota finală a stivei. Estimarea EV cere dovezi suplimentare pe seed-uri independente.

### Online contract

`PlatformLibraryAdoptionTest` verifică transportul mock la nivelul bibliotecii ATF. Pentru adaptorul Open Poker, suita planificată va folosi un server local și va acoperi connected, stale token, duplicate action, disconnect, resync, rejected action, table close și season end. [Cerințele adaptorului](../requirements/ONLINE_REQUIREMENTS.md)

### Interfața Android locală

- Admitere bibliotecă. Build-ul verifică cele opt fișiere Card Table după proveniență, coordonate și SHA-256, înainte de împachetare. J6 consumă Card Table UI 0.1.7; producătorul califică pacing-ul comun de `550 ms` <!-- rag:contract android.turn-pacing --> după primul cadru vizibil, iar consumatorul păstrează testul de proveniență exactă.
- Componente JVM. Motorul păstrează cărțile observatorului când se schimbă actorul, inclusiv când se modifică numai cărți ascunse. Sesiunea Android joacă mese de 4, 5 și 6 locuri și verifică acțiunile invalide, comenzile repetate, fold, all-in, restaurarea și conservarea jetoanelor.
- Sanity nativ. Modul `tournament` verifică victoria și eliminarea în APK-ul `sanity`, care acceptă seed-uri reproductibile. Modul `release-smoke` verifică navigarea inițială în APK-ul distribuibil exact. Extent leagă API-ul, fereastra, acțiunile, aserțiunile și capturile de hash-ul APK-ului instalat.
- Profil compact. Componentele verifică rândurile pentru 2, 4, 5 și 6 locuri, plus ordinea heads-up în ambele poziții. Pe emulator, profilul de 320 × 569 dp cu font 2,0 verifică șase chenare, board-ul, acțiunile și dialogul de raise cu tastatura deschisă. O a doua trecere pe același profil verifică schimbarea dealerului heads-up. Scriptul restabilește configurația emulatorului.
- Chenare și cărți terminale. Modului `seat-metrics` îi sunt necesare TalkBack, lățimea de `320 dp` <!-- rag:contract android.seat-metrics.viewport-width --> și font 2,0. Verifică sumele și accesibilitatea la șase locuri, apoi măsoară perechile sub chenare la șase și la cinci locuri. La cinci locuri confirmă și opțiunile Normal și Hard, cu Normal implicit. Publisherul cere aserțiunile și toate cele trei capturi. Runnerul restaurează afișajul și setările de accesibilitate.
- Reluare și prezentarea turei. `HoldemSessionRepositoryComponentTest` verifică checkpoint-ul durabil după o acțiune umană și o acțiune automată, actorul următor după replay, jurnalul reordonat, păstrarea checkpoint-ului vechi la eșec și recuperarea după întreruperea dintre redenumiri. `LocalHoldemGameTest` verifică acțiunile automate una câte una. `HoldemTablePresentationComponentTest` verifică un singur chenar pentru actorul fizic la mese de 2/4/5/6 locuri și nicio evidențiere la settlement; proiecția de decontare păstrează vizibilitatea cărților și selectează locurile cu premiu din pot. În UI, cadrul terminal rămâne disponibil, iar markerul premiului pornește din pot spre locul beneficiar. Traiectoria măsurată pe Android rămâne în [J6-UI-07](../feedback/J6-UI-07-SHOWDOWN-WIN-CLARITY.md). Aceste teste rulează fără emulator.
- Turneu, componente JVM. `LocalHoldemTournamentTest` verifică stivele exacte, locurile sărite după eliminare, tranziția la heads-up, plata finală, înfrângerea, restaurarea mai multor runde și refuzul comenzilor expirate. Verifică nivelurile de blind la intervale configurate și pauza ceasului în fundal folosind un ceas monoton injectat; timpul suspendat nu avansează nivelul, fără `sleep` sau toleranțe temporale. Rulați această suită la fiecare schimbare a sesiunii.
- Decontare și marker. JVM verifică premiul, restituirea și păstrarea lor după restaurarea checkpoint-ului, inclusiv locul cu restituire fără premiu. Sanity-ul `settlement-award` joacă un singur all-in heads-up. Verifică centrul markerului față de pot și chenar, suma markerului, potul plătit și rândurile lizibile și accesibile. Publisherul respinge un receipt fără măsurarea sosirii sau fără toate cele trei cadre.
- Premii multiple. Fixture-urile comune JVM și Android reproduc seed 33 pentru split (locurile 1 și 3, câte 4.000) și seed 24 pentru side pot (locul 2 primește 7.840, locul 3 primește 160). Componentele confruntă premiile după replay. Sanity-ul animat măsoară câte un marker per loc și păstrează cadrele de plecare și sosire. La scala Android 0, sanity-ul confruntă textul permanent și continuarea fără marker. Publisherul cere dovada numelor și sumelor și refuză receipt-ul incomplet.
- Premii în fereastră scundă. Componenta testează centrul exact al chenarului față de limitele decupajului. `settlement-compact-award` folosește seed 24 și font 2,0. Verifică faptul că locul 3 este complet în afara ferestrei, rezumatul cu ambele sume încape integral, iar singurul marker observat ajunge la locul 2. Extent respinge receipt-ul fără dovada absenței markerului ascuns.
- Turneu, sanity Android. Traseele verifică heads-up, felicitările și ecranul de eliminare. Android confirmă afișarea plății, iar JVM verifică sumele exacte ale all-in-urilor și decontărilor ulterioare. Verifică limitele legale ale unui Raise în testele de componentă. Traseul nativ confirmă deschiderea editorului, revenirea la Call și înregistrarea lui în jurnal. Corelarea unui Raise acceptat cu jurnalul curent rămâne în [J6-UI-10](../feedback/J6-UI-10-BETTING-ACTION-LEDGER.md).
- Guvernanță release. `ReleaseGovernanceTest` verifică offline starea catalogului, identitatea Android, blocul `## Jocul` (dosarul ediției în `DRAFT`; exact două linkuri ordonate în `PREPARED`/`PUBLISHED`), linkurile tehnice către catalog/proces, lipsa checksum-ului din README, politica de semnare, contractul descărcării autentificate și păstrarea octeților în Git. Blocul pentru utilizator nu acceptă mărime, checksum, stare, cerință de autentificare sau linkuri suplimentare; dovada descărcării pentru `PREPARED` rămâne în manifestul ediției. Mutanții negativi resping versiunea veche, ordinea greșită, linkul duplicat, textul zgomotos și rutele nepotrivite stării. Politica API Android minimă respinge metode Java de colecții indisponibile pe `minSdk 26`; testele native validează și calea de recomandare AI pe API 28. Guvernanța din checkout verifică dovezile versionate și receipt-ul cerut de stare, fără APK sau checksum local. Testele publică rapoarte Extent, inspectate cu `ReportArtifactInspector`. `Build-HoldemRelease.ps1` cere un commit curat și verifică separat turneul determinist `sanity` și smoke-ul APK-ului `release` instalat.
- Poarta de promovare. `Test-HoldemPromotion.ps1` verifică copia pentru upload față de calificarea fixată. Extent păstrează rezultatul și cauza blocării pentru APK absent, trunchiat sau alterat, checksum greșit și receipt incoerent. [Comanda și ordinea promovării](../release/ATF_Release.md#promovare-și-descărcare)
- Runnerul publică prin `atf-reporting` câte un Extent pentru Maven și Android JVM. Traseele Android native au rapoarte proprii, cu capturi încorporate. Dosarul fiecărei ediții identifică binarele testate, rezultatele și rapoartele. [Catalogul edițiilor](../../release/README.md)
- Context de impact release. `ReleaseImpactRag` propune suitele și probele native afectate. Testele verifică ordinea surselor, trasabilitatea citatelor, observațiile depășite și referințele locale rupte. [Contractul RAG](../release/ATF_Release.md#contextul-rag-consultativ)
- Decizie și estimare. JVM verifică proiecția permisă, separarea victoriei de egalitate, aceleași intrări între actori și migrarea checkpointurilor. Traseul de turneu verifică bifa în ambele stări, dimensiunea țintelor și accesibilitatea Call imediat după închiderea editorului Raise, urmată de flop și jurnalul acțiunii.
- Lifecycle UI. Componentele verifică timerul determinist; logica Activity invalidează callback-urile, oprește ceasul și checkpoint-uiește la `onPause`, apoi reconstruiește scena la `onResume`. Rezultatul AI este doar o sugestie până când UI-ul revalidează Activity și tokenul curent. O probă de componentă nu înlocuiește verificarea nativă a rotației/background-ului pe emulator.
- Separarea semnării. `:app:testSanityUnitTest` rulează fără identitatea privată; `verifyHoldemReleaseSigning` este dependență doar pentru împachetarea APK-urilor `sanity`/`release` și semnarea AAB-urilor. `:app:testDebugUnitTest` nu există deoarece `testBuildType` selectează `sanity`. Dry-run-ul `:app:assembleSanity` trebuie să includă gate-ul de semnare, iar testele JVM nu trebuie să-l includă.
- Capturi native. Arborele de accesibilitate poate anunța un ecran înainte ca imaginea să fie afișată. Runnerul sincronizează captura cu cadrele Android și cu stabilizarea accesibilității. Receipt-ul și PNG-urile sunt transmise prin instrumentare, nu citite din datele private ale aplicației. Publicarea compară SHA-256 al APK-ului instalat cu binarul furnizat din build.
- Reluarea decontării. Rulează sanity-ul cu `-Mode settlement-resume` pentru două tap-uri urmate de recrearea Activity, apoi pentru reluarea rezultatului din checkpoint. Extent cere destinația corectă, un singur turneu în clasament și două capturi. Componenta exercită și scrierea eșuată cu retry.
- Clasamentul la finalizare. `-Mode leaderboard-settlement` încheie un turneu, pornește altul direct din meniu și verifică ID-ul și punctajul inițial în clasament după redeschidere. Componentele verifică recuperarea când eșuează scrierea checkpoint-ului sau a registrului.
- Redenumirea în clasament. `-Mode leaderboard-profile-restart` câștigă un turneu de o mână, redenumește profilul din meniu și verifică rândul înainte și după `am force-stop`. Același APK și aceleași date rămân instalate între cele două faze. Extent cere un PID nou, un singur rând pentru profil, același ID și același digest al registrului. Capturile arată meniul după salvare și clasamentul din procesul nou. Testul JVM verifică și coliziunea cu un alias de bot.
- Oracol Extent nativ. Fiecare parcurs declară observațiile obligatorii și inventarul capturilor. Publisherul cere fiecare observație obligatorie o singură dată și verifică inventarul ordonat. Hash-ul APK are o verificare separată. O listă de aserțiuni absentă sau incompletă produce Extent `FAIL` cu diagnosticul și capturile disponibile dintr-un receipt lizibil. [Testele publisherului](../../holdem-app/src/test/java/ro/atf/j6/reporting/NativeTournamentExtentPublisherTest.java)

[Comenzi de calificare](../../holdem-android/README.md#verificare-locală)

[Procesul de release](../release/ATF_Release.md)

### Realizări de joc

- Testele de componentă verifică ordinea, pragul fiecărui obiectiv, poturile secundare sau împărțite, blindul fizic, participanții la dezvăluire și progresul deduplicat. Motorul verifică momentul când se fixează locul într-un turneu, inclusiv locurile împărțite.
- Calibrarea realizărilor rulează la 4, 5 și 6 locuri cu coach-ul și adversarii Normal. Loturile de reglaj și verificare folosesc seed-uri distincte.
- Raportul arată rata de obținere după 1, 3, 6 și 12 turnee și mediana mâinii între sesiunile care au obținut obiectivul. Turneele care ating limita de observare rămân în numitor. Pentru cele de șase locuri se numără doar sesiunile eligibile.
- Proba vizuală urmărește deschiderea meniului, nivelurile, progresul și legătura către Antrenament.

De la rădăcina proiectului, rulați cu JDK 21, Node și PowerShell. Setați `HOLDEM_DATA_ROOT` la directorul extern de date. Dosarul de ieșire trebuie să fie nou.

```powershell
$run = Join-Path $env:HOLDEM_DATA_ROOT 'achievement-calibration'
.\scripts\run-achievement-calibration.ps1 -OutputDirectory $run
```

[Planul cohortelor](../../config/j6-achievement-calibration.yaml) · [Suita Android](../../holdem-android/app/src/test/java/ro/atf/holdem/android/GameplayAchievementsComponentTest.java)

### Alegerea nivelului de test

| Contract | Probă principală | Dovadă Android |
|---|---|---|
| Blinduri, all-in, side pots, split, restituiri și replay | Motor și sesiune JVM, cu sume exacte pe loc | Probele de decontare din [procesul de release](../release/ATF_Release.md#calificare-reproductibilă) |
| Actor, rol, acțiune și total pe loc | Proiecția JVM pentru 2/4/5/6 locuri | Bounds reale, focus TalkBack și jurnalul derulabil |
| Redenumire, deduplicare și recuperare după scriere eșuată | Componente de registru și checkpoint | Un restart real când se schimbă persistența |
| Editorul de raise | Limite și acțiuni respinse în JVM | Dialogul și tastatura în proba nativă izolată |
| Semnare și lansare | Identitatea verificată offline | Smoke pe APK-ul distribuibil exact |

## Gate de promovare

`mvn test` trebuie să fie verde. Orice schimbare strategică trebuie să atașeze:

- policy version;
- corpus de reglaj și corpus de evaluare;
- seed list;
- număr de mâini;
- baseline comparison;
- failure inventory;
- replay pentru cazurile de frontieră.

Un test verde al motorului nu certifică un AI profitabil.

## Dovezi de distribuție

- **Dosar extern.** Păstrați build-urile și fișierele brute ale calificării sub rădăcina externă a proiectului.
- **Dosar de ediție.** `release/<versiune>/` leagă pachetul, checksum-ul, manifestul și rapoartele Extent cu capturi încorporate. `release/index.json` selectează versiunea curentă, iar manifestul consemnează testele, hash-urile și verificarea distribuției.
- **README principal.** În `DRAFT`, secțiunea `## Jocul` indică dosarul ediției. După calificare, indică pagina Release și linkul direct la APK, în această ordine.
- **Acces public.** `PUBLISHED` cere descărcarea completă fără autentificare. Pentru `404` la profil și repo, aplicați [diagnosticul GitHub](../release/ATF_Release.md#diagnostic-http-404-pentru-github-release).
