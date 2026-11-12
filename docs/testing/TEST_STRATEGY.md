# Strategie de testare

## Pe scurt

J6 separă oracolul de componentă de probele pe Android. Guvernanța documentară verifică separat orientarea, navigarea și traseul de descărcare al ediției curente.

## Revizia documentației

- Compară afirmațiile despre comportament cu implementarea și testul modulului responsabil.
- Rulează `DocumentationGovernanceTest` pentru orientarea README-urilor active și prezența unei singure legături către fiecare ghid responsabil în secțiunea de documentație. Rulează `ReleaseGovernanceTest` pentru blocul **Jocul** și integritatea dovezilor ediției.
- Aplică validatorii locali pentru titluri, proză, linkuri, ancore și legende Mermaid. Verifică editorial dacă prima diagramă răspunde la întrebarea cititorului. [Reguli și execuție](../governance/README.md)

## Niveluri

| Nivel | Alegerea testului și a dovezii |
|---|---|
| `@AtfUnitTest` | Regulă sau calcul determinist, cu intrări mici și valori așteptate explicite. |
| `@AtfComponentTest` | Motor, politică, arenă sau adaptor în limita contractului, cu seed, ceas, fișiere sau transport controlate. |
| `@AtfIntegrationTest` | Contract între module sau între proces și sistemul local, precum Git, PowerShell și catalogul. |
| Android JVM | Componente JUnit 4 din inventarul Gradle. Proiecția, checkpoint-ul și catalogul rulează fără emulator. |
| Android nativ | Componenta randată măsoară viewport-ul, iar smoke-ul verifică navigarea și integrarea cu sistemul pe APK-ul identificat. |

Un caz de componentă pornește de la o regulă și o intrare controlată, cu rezultat așteptat independent. Verifică și cazul negativ cel mai apropiat. Păstrează în parcursul nativ comportamentul care depinde de randare, focus, gest sau reluarea procesului.

### Rulare JVM și Extent

Rulează într-un checkout izolat și folosește un director extern nou pentru rapoarte. Variabila `HOLDEM_DATA_ROOT` indică rădăcina externă de date.

Grupurile Maven selectează atributele ATF. Pentru raportul complet, include toate nivelurile JVM.

```powershell
mvn -o -B -ntp "-Dgroups=unit | component | integration" test
gradle -p holdem-android --offline :app:testSanityUnitTest
.\scripts\Publish-HoldemJvmExtent.ps1 -ReportDirectory "$env:HOLDEM_DATA_ROOT/jvm-audit"
```

Publisherul compară XML-urile cu inventarul versionat și semnalează clase lipsă, duplicate sau rezultate incomplete. Fiecare suită afișează nivelul de test, numărul cazurilor și SHA-256 al XML-ului.

În Extent, verifică întâi scenariul de inventar, apoi suita și comparațiile. Guvernanța produce și rapoarte cu așteptat, observat și rezultat pe document sau clasă. [Inventarul de release](../../config/release-test-suites.json) · [Guvernanță locală](../governance/README.md)

### Contracte de componentă

- Integrarea `HoldemReleaseProvenanceTest` rulează scripturile locale cu un transport GitHub controlat. Verifică taguri simple și adnotate, citirea README-ului la un commit fix și detectarea unui manifest ori raport modificat.
- Fixture-urile Git verifică continuarea pentru dovezi și resping schimbările de produs, inclusiv cele revenite ulterior. [Promovare și descărcare](../release/ATF_Release.md#promovare-și-descărcare)

Testele curente acoperă ordinea actorilor, sizing-ul minim, menținerea stării după acțiuni ilegale, all-in-uri scurte cumulative, evaluarea mâinii și decontarea pe straturi. `config/j6-allin-challenges.yaml` este fixture-ul controlat pentru redeschidere, egalitate și jetoane impare; JUnit îl rulează prin motor, iar `audit-allin-challenge.cjs` reface separat acțiunile, clasificarea și plata.

Un caz redus din turnee verifică plata parțială a ambelor blinduri, locurile eliminate și împărțirea potului. Aserțiunile verifică separat câștigul și restituirea în vederea publică a mesei. Un al doilea caz acoperă jetonul impar acordat după buton peste un loc eliminat, cu pot secundar și restituire distincte. [Testele motorului](../../holdem-core/src/test/java/ro/atf/holdem/core/HoldemRulesTest.java)

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
| Istoric de shove | `--require-public-shove-history` reconstruiește oportunitățile din stivă și acțiunile legale. Compară numai contoarele mâinilor deja încheiate. |

- **Opțiunea din blindul mare.** `HardPressurePolicyTest` joacă limp-ul în motor și verifică perechea premium, mâna slabă, stiva proprie și acoperirea sumei. Loaderul verifică valoarea booleană a opțiunii YAML.
- **Raise cu preț pozitiv.** Testele variază discountul de call, poziția heads-up, agresiunea publică și acoperirea minimului legal. Verifică pragul separat și suma executată. Loaderul validează cheile și limitele ponderilor de raise.
- **Discount preflop.** Verifică pragul inclusiv de două blinduri mari, limita dată de pot odds și riscul de angajare a stivei. Distinge all-in-ul care egalează de cel care mărește prețul.
- **Răspunsul la raise.** Verifică aceeași cheie înainte de acțiune și după executarea ei în motor. Acoperă prețul, poziția, ordinea acțiunilor, abstinența la dovezi puține și plafonul ajustării. Loaderul respinge chei și numărători incompatibile.
- **Izolarea semnalelor.** Compară fold-ul și penalizarea de re-raise cu același model calibrat. Verifică pragul de call și suma propusă pe o observație comună, precum și contribuția zero când frecvența de re-raise este sub reper în modul de penalizare.
- **Dimensionarea prin EV.** Verifică separat ramurile fold, call și re-raise. Reexecută oferta în motor, confruntă suma plătită și acoperă limitele, dovada insuficientă și alegerea pe aceeași decizie de raise.
- **Reproductibilitate.** `MonteCarloEquityEstimatorTest` compară seed-ul implicit cu un reper numeric, la două bugete. Calificarea repetă partidele în procese JVM separate și auditează legalitatea și scorul independent.

- Cohortele `equity` și `legacy-equity` păstrează locurile candidate și seed-urile. Pragurile loose-aggressive se compară prin perechi YAML.
- `run-button-position-tournaments.ps1` rotește butonul inițial, iar `audit-button-rotations.cjs` verifică fluxul de seed-uri și perechile cu același offset. Rotația redistribuie cărțile între locuri. Fixture-ul controlat păstrează cărțile private la aceleași locuri.
- `compare-cash-sessions.cjs` măsoară cota finală a stivei. Estimarea EV cere dovezi suplimentare pe seed-uri independente.

### Online contract

`PlatformLibraryAdoptionTest` verifică transportul mock la nivelul bibliotecii ATF. Pentru adaptorul Open Poker, suita planificată va folosi un server local și va acoperi connected, stale token, duplicate action, disconnect, resync, rejected action, table close și season end. [Cerințele adaptorului](../requirements/ONLINE_REQUIREMENTS.md)

### Interfața Android locală

- Build-ul verifică proveniența, coordonatele și SHA-256 pentru bibliotecile Card Table înainte de împachetare. Card Table UI furnizează antetul comun, lista și notificările Realizărilor, marcajul vizual al cărților și temporizarea de `550 ms` <!-- rag:contract android.turn-pacing --> după primul cadru vizibil. Testele Hold’em verifică selecția pokerului și progresul.
- Componente JVM. Motorul păstrează cărțile observatorului când se schimbă actorul, inclusiv când se modifică numai cărți ascunse. Sesiunea Android joacă mese de 4, 5 și 6 locuri și verifică acțiunile invalide, comenzile repetate, fold, all-in, restaurarea și conservarea jetoanelor.
- Sanity nativ. Modul `tournament` verifică victoria și eliminarea în APK-ul `sanity`, care acceptă seed-uri reproductibile. Modul `release-smoke` verifică navigarea inițială în APK-ul distribuibil exact. Extent leagă API-ul, fereastra, acțiunile, aserțiunile și capturile de hash-ul APK-ului instalat.
- Profil compact. Componentele verifică rândurile pentru 2, 4, 5 și 6 locuri, plus ordinea heads-up în ambele poziții. Pe emulator, profilul de 320 × 569 dp cu font 2,0 verifică șase chenare, board-ul, acțiunile și dialogul de raise cu tastatura deschisă. O a doua trecere pe același profil verifică schimbarea dealerului heads-up. Scriptul restabilește configurația emulatorului.
- Chenare și cărți terminale. Modului `seat-metrics` îi sunt necesare TalkBack, lățimea de `320 dp` <!-- rag:contract android.seat-metrics.viewport-width --> și font 2,0. Pe APK-ul semnat verifică sumele, focalizarea nativă a locului activ cu TalkBack pornit și perechile de cărți sub chenare la șase și la cinci locuri. La cinci locuri confirmă și opțiunile Normal și Hard, cu Normal implicit. Publisherul cere aserțiunile și toate cele trei capturi. Runnerul restaurează afișajul și setările de accesibilitate.
- Reluare și prezentarea turei. `HoldemSessionRepositoryComponentTest` verifică checkpoint-ul durabil după o acțiune umană și o acțiune automată, actorul următor după replay, jurnalul reordonat, păstrarea checkpoint-ului vechi la eșec și recuperarea după întreruperea dintre redenumiri. `LocalHoldemGameTest` verifică acțiunile automate una câte una. `HoldemTablePresentationComponentTest` verifică un singur chenar pentru actorul fizic la mese de 2/4/5/6 locuri și nicio evidențiere la settlement; proiecția de decontare păstrează vizibilitatea cărților și selectează locurile cu premiu din pot. În UI, cadrul terminal rămâne disponibil, iar markerul premiului pornește din pot spre locul beneficiar. Traiectoria măsurată pe Android rămâne în [J6-UI-07](../feedback/J6-UI-07-SHOWDOWN-WIN-CLARITY.md). Aceste teste rulează fără emulator.
- Turneu, componente JVM. `LocalHoldemTournamentTest` verifică stivele exacte, locurile sărite după eliminare, tranziția la heads-up, plata finală, înfrângerea, restaurarea mai multor runde și refuzul comenzilor expirate. Verifică nivelurile de blind la intervale configurate și pauza ceasului în fundal folosind un ceas monoton injectat; timpul suspendat nu avansează nivelul, fără `sleep` sau toleranțe temporale. Rulați această suită la fiecare schimbare a sesiunii.
- Decontare și marker. JVM verifică premiul, restituirea și păstrarea lor după restaurarea checkpoint-ului, inclusiv locul cu restituire fără premiu. Sanity-ul `settlement-award` joacă un singur all-in heads-up. Verifică centrul markerului față de pot și chenar, suma markerului, potul plătit și rândurile lizibile și accesibile. Publisherul respinge un receipt fără măsurarea sosirii sau fără toate cele trei cadre.
- Premii multiple. Fixture-urile comune JVM și Android reproduc seed 33 pentru split (locurile 1 și 3, câte 4.000) și seed 24 pentru side pot (locul 2 primește 7.840, locul 3 primește 160). Componentele confruntă premiile după replay. Sanity-ul animat măsoară câte un marker per loc și păstrează cadrele de plecare și sosire. La scala Android 0, sanity-ul confruntă textul permanent și continuarea fără marker. Publisherul cere dovada numelor și sumelor și refuză receipt-ul incomplet.
- Premii în fereastră scundă. Componenta testează centrul exact al chenarului față de limitele decupajului. `settlement-compact-award` folosește seed 24 și font 2,0. Verifică faptul că locul 3 este complet în afara ferestrei, rezumatul cu ambele sume încape integral, iar singurul marker observat ajunge la locul 2. Extent respinge receipt-ul fără dovada absenței markerului ascuns.
- Turneu, sanity Android. Traseele verifică heads-up, felicitările și ecranul de eliminare. Suma din editor și unicitatea intrării Raise au o probă separată în același traseu. [Confirmarea din editor](#confirmarea-unui-raise-din-editor)
- Guvernanță release. `ReleaseGovernanceTest` verifică offline starea catalogului, identitatea Android, blocul `## Jocul` (dosarul ediției în `DRAFT`; exact două linkuri ordonate în `PREPARED`/`PUBLISHED`), linkurile tehnice către catalog/proces, lipsa checksum-ului din README, politica de semnare, contractul descărcării autentificate și păstrarea octeților în Git. Blocul pentru utilizator nu acceptă mărime, checksum, stare, cerință de autentificare sau linkuri suplimentare; dovada descărcării pentru `PREPARED` rămâne în manifestul ediției. Mutanții negativi resping versiunea veche, ordinea greșită, linkul duplicat, textul zgomotos și rutele nepotrivite stării. Politica API Android minimă respinge metode Java de colecții indisponibile pe `minSdk 26`; testele native validează și calea de recomandare AI pe API 28. Guvernanța din checkout verifică dovezile versionate și receipt-ul cerut de stare, fără APK sau checksum local. Testele publică rapoarte Extent, inspectate cu `ReportArtifactInspector`. `Build-HoldemRelease.ps1` cere un commit curat și verifică separat turneul determinist `sanity` și smoke-ul APK-ului `release` instalat.
- Poarta de promovare. `Test-HoldemPromotion.ps1` verifică copia pentru upload față de calificarea fixată. Extent păstrează rezultatul și cauza blocării pentru APK absent, trunchiat sau alterat, checksum greșit și receipt incoerent. [Comanda și ordinea promovării](../release/ATF_Release.md#promovare-și-descărcare)
- Runnerul publică prin `atf-reporting` câte un Extent pentru Maven și Android JVM. Traseele Android native au rapoarte proprii, cu capturi încorporate. Dosarul fiecărei ediții identifică binarele testate, rezultatele și rapoartele. [Catalogul edițiilor](../../release/README.md)
- Context de impact release. `ReleaseImpactRag` propune suitele și probele native afectate. Testele verifică ordinea surselor, trasabilitatea citatelor, observațiile depășite și referințele locale rupte. [Contractul RAG](../release/ATF_Release.md#contextul-rag-consultativ)
- Decizie și estimare. JVM verifică proiecția permisă, separarea victoriei de egalitate, aceleași intrări între actori și migrarea checkpointurilor. Traseul de turneu verifică bifa în ambele stări, dimensiunea țintelor și accesibilitatea Call imediat după închiderea editorului Raise, urmată de flop și jurnalul acțiunii.
- Lifecycle UI. Componentele verifică timerul determinist; logica Activity invalidează callback-urile, oprește ceasul și checkpoint-uiește la `onPause`, apoi reconstruiește scena la `onResume`. Rezultatul AI este doar o sugestie până când UI-ul revalidează Activity și tokenul curent. O probă de componentă nu înlocuiește verificarea nativă a rotației/background-ului pe emulator.
- Auto-play. Testul de politică livrează o decizie întârziată după debifare și după o nouă bifare pe aceeași tură. Verifică revizia, lipsa mutării întârziate și aplicarea o singură dată. Schimbarea bifei în tura unui adversar păstrează lucrarea acestuia și actualizează delegarea pentru următoarea tură proprie. Testele de checkpoint verifică sursa pe mai multe mâini și redarea datelor anterioare.
- Coach. O pereche de distribuiri păstrează starea publică și cărțile proprii. După comparația recomandărilor, decontarea dovedește diferența dintre cărțile adverse. Apelul păstrează revizia și checkpoint-ul complet.
- Geometria mesei. Componenta măsoară în pixeli titlul, bifa, rândul cu etapa și șansa, jetoanele, cărțile și locurile. Compară schimbarea actorului, o mărire urmată de cărțile publice și redarea cu istoricul deschis. În română și engleză verifică starea bifei, descrierea de accesibilitate și spațiul dintre controale. Proba citește culorile efective ale textului de pe masă și verifică raportul de contrast. [Profiluri de rulare](../../holdem-android/README.md#verificare-locală)
- Separarea semnării. `:app:testSanityUnitTest` rulează fără identitatea privată; `verifyHoldemReleaseSigning` este dependență doar pentru împachetarea APK-urilor `sanity`/`release` și semnarea AAB-urilor. `:app:testDebugUnitTest` nu există deoarece `testBuildType` selectează `sanity`. Dry-run-ul `:app:assembleSanity` trebuie să includă gate-ul de semnare, iar testele JVM nu trebuie să-l includă.
- Capturi native. Arborele de accesibilitate poate anunța un ecran înainte ca imaginea să fie afișată. Runnerul sincronizează captura cu cadrele Android și cu stabilizarea accesibilității. Receipt-ul și PNG-urile sunt transmise prin instrumentare, nu citite din datele private ale aplicației. Publicarea compară SHA-256 al APK-ului instalat cu binarul furnizat din build.
- Reluarea decontării. Rulează sanity-ul cu `-Mode settlement-resume` pentru două tap-uri urmate de recrearea Activity, apoi pentru reluarea rezultatului din checkpoint. Extent cere destinația corectă, un singur turneu în clasament și două capturi. Componenta exercită și scrierea eșuată cu retry.
- Clasamentul la finalizare. `-Mode leaderboard-settlement` încheie un turneu, pornește altul direct din meniu și verifică ID-ul și punctajul inițial în clasament după redeschidere. Componentele verifică recuperarea când eșuează scrierea checkpoint-ului sau a registrului.
- Redenumirea în clasament. `-Mode leaderboard-profile-restart` câștigă un turneu de o mână, redenumește profilul din meniu și verifică rândul înainte și după `am force-stop`. Același APK și aceleași date rămân instalate între cele două faze. Extent cere un PID nou, un singur rând pentru profil, același ID și același digest al registrului. Capturile arată meniul după salvare și clasamentul din procesul nou. Testul JVM verifică și coliziunea cu un alias de bot.
- Oracol Extent nativ. Fiecare parcurs declară observațiile obligatorii și inventarul capturilor. Publisherul cere fiecare observație obligatorie o singură dată și verifică inventarul ordonat. Hash-ul APK are o verificare separată. O listă de aserțiuni absentă sau incompletă produce Extent `FAIL` cu diagnosticul și capturile disponibile dintr-un receipt lizibil. [Testele publisherului](../../holdem-app/src/test/java/ro/atf/j6/reporting/NativeTournamentExtentPublisherTest.java)

[Comenzi de calificare](../../holdem-android/README.md#verificare-locală)

[Procesul de release](../release/ATF_Release.md)

### Sincronizarea jurnalului după Check

- Sanity-ul citește ultimul ordinal din același arbore de accesibilitate în care comanda Check este disponibilă. După tap, așteaptă intrarea următoare pentru jucătorul uman, cu acțiunea, suma și contribuția așteptate.
- La expirare, aserțiunea înregistrează ultimul ordinal citit și descrierea nodului. Un cadru gol ulterior păstrează diagnosticul.
- `BettingActionObservationComponentTest` simulează cadre și timp. Verifică nodul vechi, un adversar care avansează jurnalul, câmpurile incorecte și termenul de așteptare. [Testele JVM](../../holdem-android/app/src/test/java/ro/atf/holdem/android/BettingActionObservationComponentTest.java)

### Confirmarea unui Raise din editor

- `WagerConfirmationComponentTest` conectează confirmarea editorului la motor. Verifică suma adăugată, contribuția cumulată și o singură intrare după o confirmare repetată și după replay. [Testele JVM](../../holdem-android/app/src/test/java/ro/atf/holdem/android/WagerConfirmationComponentTest.java)
- Pentru o verificare scurtă, rulează `scripts/run-holdem-android-sanity.ps1` cu `-Mode betting-ledger`, apoi cu `betting-ledger-compact` și `betting-ledger-talkback`. Ultimele două folosesc fereastra scundă la cel mult 320 dp și font 2,0. Profilul TalkBack pornește cu focus pe comanda Check și urmărește transferul la locul jucătorului până la următoarea decizie, după răspunsul adversarilor.
- Parcursul reia câte un checkpoint înainte de acțiune. Pentru Check verifică zero jetoane adăugate și contribuția de 20. Pentru Raise introduce 60 și verifică o contribuție de 70 cu blindul deja plătit. Proba confruntă sumele cu descrierile accesibile și numără rândurile din jurnalul extins. Măsoară textul badge-ului și cere ca rândul întreg, inclusiv totalul, să intre în fereastră după focus.

### Realizări de joc

- Pentru o familie nouă, păstrați un caz pozitiv mic și un caz aproape identic care nu primește credit. Exemplele de decontare se reduc la stivele inițiale, poziții, acțiuni acceptate și rezultatul verificat independent.
- La mărirea catalogului, verificați că pragul maxim pe metrică provine din YAML și că profilurile cu ID-uri deja salvate se deschid după o reducere a pragului. Pentru obiective manuale, simulați aceeași mână cu proveniență manuală și Auto-play.

- Testele de componentă verifică ordinea, pragul fiecărui obiectiv, poturile secundare sau împărțite, blindul fizic, participanții la dezvăluire și progresul deduplicat. Motorul verifică momentul când se fixează locul într-un turneu, inclusiv locurile împărțite.
- Calibrarea folosește masa locală din cod, fără randare, la 4, 5 și 6 locuri, pe Normal și Greu. Politica Auto-play joacă rolul profilului simulat.
- Loturile de reglaj și confirmare folosesc seed-uri distincte și același buget. Un lot separat folosește bugetul complet al aplicației.
- Raportul arată prima ocazie, deblocarea și ferestrele de 1, 3, 6 și 12 turnee. Turneele cenzurate rămân în numitor. Obiectivele de șase locuri folosesc mesele eligibile.
- Testul notificării simulează eșecul fiecărei scrieri. La succes verifică jocul și progresul salvat înainte de callback, apoi reîncarcă jocul și verifică deduplicarea.
- Proba de instalare deschide Realizările pe APK-ul semnat, parcurge lista în română și engleză, caută un obiectiv după pragul afișat și deschide regula lui. Scenariul de turneu verifică progresul paralel după redeschidere. Testele de componentă verifică identitatea rândurilor și combinațiile de căutare, nivel și stare. Traseul nativ recreează activitatea cu un detaliu deschis, apoi confirmă căutarea, nivelul și starea.
- Măsurarea contrastului randat și proba cu font mărit sunt urmărite în [feedback](../feedback/J6-PROD-01-ACCOUNT-ACHIEVEMENTS.md).

De la rădăcina proiectului, rulați cu JDK 21, Node și PowerShell. Setați `HOLDEM_DATA_ROOT` la directorul extern de date. Dosarul de ieșire trebuie să fie nou.

```powershell
$run = Join-Path $env:HOLDEM_DATA_ROOT 'achievement-calibration'
.\scripts\run-live-achievement-calibration.ps1 -OutputDirectory $run
node scripts/analyze-live-achievement-calibration.cjs $run config/j6-achievements.yaml (Join-Path $run 'objective-results.json')
```

[Planul cohortelor](../../config/j6-live-achievement-calibration.yaml) · [Publicarea Extent](../../holdem-app/src/test/java/ro/atf/j6/release/PublishAchievementCalibrationExtent.java) · [Suita Android](../../holdem-android/app/src/test/java/ro/atf/holdem/android/GameplayAchievementsComponentTest.java)

### Replay pentru catalogul extins

- Catalogul candidat declară familia, ordinea în cele trei file, metrica, pragul și textele RO/EN. Pentru fiecare unitate declară cheia distinctă și proveniența necesară deciziei manuale.
- Auditorul reface mâna și decontarea din jurnal. Expune acțiunile acceptate, câștigătorii fiecărui strat, restituirile și stivele după ce verifică rezultatul.
- Calibrarea folosește jurnalele păstrate. Confirmarea reia configurațiile pereche pe seed-uri disjuncte. Raportul ține separat fiecare profil, fiecare rol și mesele de 4, 5 și 6 locuri.
- Raportul arată evenimentele observate, mediana mâinii printre cei care ating pragul și ferestrele de 1, 3, 6 și 12 turnee. Ferestrele grupează aceeași poziție fizică la aceeași dimensiune de masă. Sunt o simulare de progres, iar activarea cere verificarea profilului și a provenienței manuale în aplicație.

Rulați de la rădăcina proiectului după compilarea Maven. Folosiți un director extern nou pentru lotul independent și pentru raport.

```powershell
$plan = Get-Content -Raw config/j6-achievement-replay.yaml | ConvertFrom-Json
$archive = Join-Path $env:HOLDEM_DATA_ROOT "20261107-controlled-challenges"
$holdout = Join-Path $env:HOLDEM_DATA_ROOT "achievement-holdout"
$report = Join-Path $env:HOLDEM_DATA_ROOT "achievement-replay.json"
.\scripts\run-policy-cohorts.ps1 -OutputRoot $holdout -FirstSeed $plan.holdout.firstSeed `
    -TournamentsPerSeatCount $plan.holdout.tournamentsPerSeatCount -CandidateNames $plan.holdout.style
node --test scripts/test-achievement-replays.cjs
node scripts/analyze-achievement-replays.cjs $archive $report $holdout
```

[Catalogul candidat](../../config/j6-achievement-expansion.yaml) · [Protocolul replay](../../config/j6-achievement-replay.yaml)

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

## Sanity Android pentru Hard

Pentru stilurile Hard, `-IncludeHardSix` pornește din meniu un turneu de șase locuri pe APK-ul semnat. Suita verifică dificultatea salvată, cele cinci răspunsuri automate și acțiunile check/call ale adversarului Normal.
