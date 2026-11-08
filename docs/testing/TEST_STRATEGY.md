# Strategie de testare

## Niveluri

### Contracte de componentă

Testele curente acoperă ordinea actorilor, sizing-ul minim, menținerea stării după acțiuni ilegale, all-in-uri scurte cumulative, evaluarea mâinii și decontarea pe straturi. `config/j6-allin-challenges.yaml` este fixture-ul controlat pentru redeschidere, egalitate și jetoane impare; JUnit îl rulează prin motor, iar `audit-allin-challenge.cjs` reface separat acțiunile, clasificarea și plata.

### Politici AI

Testele verifică replay-ul determinist, fallback-urile și acțiunile legale pentru TAG, calling station, pressure, loose-aggressive, bluff, equity Monte Carlo și rolul experimental `short-stack-shove`. Ultimul împinge preflop numai sub pragul configurat și cu mâinile specificate; altfel delegă baseline-ului TAG, iar un all-in este ales doar când apare în meniul legal. Testele negative acoperă stiva peste prag, mâna slabă și pragurile de configurare. Estimatorul are verificări pentru pot împărțit, reproducibilitate, duplicate de cărți, bugetul de eșantionare și scăderea equity-ului preflop când cresc adversarii activi. Politica equity testează pot odds, incertitudinea, agresiunile adversarilor fără auto-numărarea acțiunilor proprii, ordinea postflop după fold-uri și diferența de marjă între cash și turneu. Testele de shove public verifică limitele `>2..10 BB`, posteriorul smoothed și încălzirea dovezii, maparea după locul fizic și media doar peste autorii curenți măsurați, reacția în ambele direcții, ambele plafoane, modul cash fără efect și propagarea statisticilor doar după încheierea mâinii. Testul de guvernanță încarcă toate YAML-urile policy din repository prin parserul strict, ca o cheie omisă într-un singur profil să nu scape. YAML-urile verifică exact setul de ponderi admis.

Aceste teste demonstrează contractul și direcția pragurilor; nu demonstrează valoarea EV a ponderilor. Cărțile adversarilor sunt eșantionate uniform, deci politica încă nu are range-uri condiționate din acțiunile individuale.

### Aceeași distribuție, buton rotit

`PositionRotationExperimentTest` joacă două distribuții complete, câte o dată cu fiecare dintre cele șase locuri la buton și șapte stiluri, inclusiv politica equity Monte Carlo; bluff-ul forțat la 100% rămâne doar probă de accesibilitate. Cărțile private rămân atașate locurilor fizice, board-ul planificat este identic, iar politica primește numai observația actorului. JUnit oprește jocul dacă proiecția sau mutarea nu este legală; `audit-position-rotations.cjs` verifică separat din jurnal pozițiile, cărțile private ale actorului, prefixul public al board-ului, unicitatea cărților și că distribuțiile nu s-au schimbat între rotații. Reveal-ul complet apare numai după terminarea mâinii.

### Arena

`TournamentArena` rulează turnee complete pentru mesele de 4–6 locuri; fiecare jurnal trece prin `audit-tournament.cjs`, care reface seed-ul, ordinea, legalitatea, clasificarea și poturile independent de motorul Java. `CashTableArena` rulează mese de 2–6 locuri cu blind-uri fixe; `audit-cash-sessions.cjs` verifică dealerul, structura fixă, durata, stivele și conservarea jetoanelor. Brațele cash candidate/control pornesc cu aceleași seed-uri și primul buton, iar auditorul validează separat orice rotație după eliminări. `compare-cash-sessions.cjs` măsoară cota de stivă a locurilor candidate; nu o interpretează ca EV.

Comparațiile de turneu folosesc aceleași seed-uri și înlocuiesc numai locurile candidate. `equity` și `legacy-equity` aleg politica contextuală; aceasta compară aceeași distribuție la aceleași poziții. Variantele de praguri loose-aggressive sunt evaluate separat prin YAML-uri pereche. `TournamentStrategyArenaTest` verifică lineup-uri strict versionate, rotația fiecărui rol prin fiecare loc și poziție, apoi un jurnal de turneu real fără informație privată. Configurația adversarială `j6-strategy-lineups-shortstack.yaml` pune rolul `short-stack-shove` la toate mărimile de masă și este verificată separat de roster-ul standard. `audit-tournament.cjs` verifică independent contextul public dinaintea acțiunii și îl reconciliază cu starea motorului înainte de a valida acțiunea. `--require-public-shove-history` face obligatorie schema statisticilor, reconstruiește oportunitățile din stiva rămasă și meniu legal, apoi compară contoarele numai din mâinile deja încheiate. Rotațiile fixe izolează comportamentul pozițional; turneele și sesiunile cash verifică interacțiunea în joc complet. Niciun lot mic nu este suficient singur pentru a afirma EV pozitiv.

`run-button-position-tournaments.ps1` rotește butonul inițial pe aceeași secvență de seed-uri pentru fiecare turneu complet; candidat și control păstrează același offset. `audit-button-rotations.cjs` verifică seed-urile fiecărei mâini între offseturi, pozițiile de start și comparația pereche. În acest experiment cărțile se redistribuie între locurile fizice când se mută butonul; rotația exactă a acelorași cărți private la aceleași locuri este acoperită separat de fixture-urile din `PositionRotationExperimentTest`.

### Online contract

`PlatformLibraryAdoptionTest` verifică transportul mock la nivelul bibliotecii ATF. Pentru adaptorul Open Poker, suita planificată va folosi un server local și va acoperi connected, stale token, duplicate action, disconnect, resync, rejected action, table close și season end. [Cerințele adaptorului](../requirements/ONLINE_REQUIREMENTS.md)

### Interfața Android locală

- Admitere bibliotecă. Build-ul verifică cele opt fișiere Card Table după proveniență, coordonate și SHA-256, înainte de împachetare. J6 consumă Card Table UI 0.1.7; producătorul califică pacing-ul comun de `550 ms` <!-- rag:contract android.turn-pacing --> după primul cadru vizibil, iar consumatorul păstrează testul de proveniență exactă.
- Componente JVM. Motorul păstrează cărțile observatorului când se schimbă actorul, inclusiv când se modifică numai cărți ascunse. Sesiunea Android joacă mese de 4, 5 și 6 locuri și verifică acțiunile invalide, comenzile repetate, fold, all-in, restaurarea și conservarea jetoanelor.
- Sanity nativ. Modul `tournament` verifică victoria și eliminarea în APK-ul `sanity`, care acceptă seed-uri reproductibile. Modul `release-smoke` verifică navigarea inițială în APK-ul distribuibil exact. Extent leagă API-ul, fereastra, acțiunile, aserțiunile și capturile de hash-ul APK-ului instalat.
- Profil compact. Componentele verifică rândurile pentru 2, 4, 5 și 6 locuri, plus ordinea heads-up în ambele poziții. Pe emulator, profilul de 320 × 569 dp cu font 2,0 verifică șase chenare, board-ul, acțiunile și dialogul de raise cu tastatura deschisă. O a doua trecere pe același profil verifică schimbarea dealerului heads-up. Scriptul restabilește configurația emulatorului.
- Sumele din chenare. `-Mode seat-metrics` verifică pe Android o masă de șase locuri în română la lățimea de `320 dp` <!-- rag:contract android.seat-metrics.viewport-width --> și font 2,0, înainte și după decontarea unei mâini. Compară fiecare valoare cu motorul, măsoară textul randat și focalizează chenarul actorului cu TalkBack activ. Instalați Android Accessibility Suite înainte de rulare. Pentru inspecția manuală a anunțului, activați `Display speech output` în setările de dezvoltator TalkBack. Runnerul restaurează dimensiunea, fontul și lista serviciilor de accesibilitate. JVM verifică pragul de 4,5:1 și separarea câștigului de restituire.
- Reluare și prezentarea turei. `HoldemSessionRepositoryComponentTest` verifică checkpoint-ul durabil după o acțiune umană și o acțiune automată, actorul următor după replay, jurnalul reordonat, păstrarea checkpoint-ului vechi la eșec și recuperarea după întreruperea dintre redenumiri. `LocalHoldemGameTest` verifică acțiunile automate una câte una. `HoldemTablePresentationComponentTest` verifică un singur chenar pentru actorul fizic la mese de 2/4/5/6 locuri și nicio evidențiere la settlement; setul de cărți colectate respectă vizibilitatea, iar `TrickCollectionMotion` păstrează cele 1.000 ms de expunere și ultimele 500 ms de mișcare spre pot. Aceste teste rulează fără emulator.
- Turneu, componente JVM. `LocalHoldemTournamentTest` verifică stivele exacte, locurile sărite după eliminare, tranziția la heads-up, plata finală, înfrângerea, restaurarea mai multor runde și refuzul comenzilor expirate. Verifică nivelurile de blind la intervale configurate și pauza ceasului în fundal folosind un ceas monoton injectat; timpul suspendat nu avansează nivelul, fără `sleep` sau toleranțe temporale. Rulați această suită la fiecare schimbare a sesiunii.
- Turneu, sanity Android. Traseele verifică heads-up, felicitările și ecranul de eliminare. Android confirmă afișarea plății, iar JVM verifică sumele exacte ale all-in-urilor și decontărilor ulterioare. Editorul de raise are o probă nativă scurtă și izolată.
- Guvernanță release. `ReleaseGovernanceTest` verifică offline starea catalogului, identitatea Android, blocul `## Jocul` (dosarul ediției în `DRAFT`; exact două linkuri ordonate în `PREPARED`/`PUBLISHED`), linkurile tehnice către catalog/proces, lipsa checksum-ului din README, politica de semnare, scriptul credentialless de descărcare și păstrarea octeților în Git. Blocul pentru utilizator nu acceptă mărime, checksum, stare, cerință de autentificare sau linkuri suplimentare; dovada descărcării pentru `PREPARED` rămâne în manifestul ediției. Mutanții negativi resping versiunea veche, ordinea greșită, linkul duplicat, textul zgomotos și rutele nepotrivite stării. Politica API Android minimă respinge metode Java de colecții indisponibile pe `minSdk 26`; testele native validează și calea de recomandare AI pe API 28. Testele publică rapoarte Extent, inspectate cu `ReportArtifactInspector`. `Build-HoldemRelease.ps1` cere un commit curat și verifică separat turneul determinist `sanity` și smoke-ul APK-ului `release` instalat.
- Runnerul publică prin `atf-reporting` câte un Extent pentru Maven și Android JVM. Traseele Android native au rapoarte proprii, cu capturi încorporate. Dosarul fiecărei ediții identifică binarele testate, rezultatele și rapoartele. [Catalogul edițiilor](../../release/README.md)
- Context de impact release. `ReleaseImpactRag` a fost reverificat pe diff-ul calificat pentru 0.3.0. Contextul consultativ prioritizează ghidul Android și suitele afectate, cu linii și digest pentru surse. `ReleaseVersionPolicy` verifică versiunea, iar receipt-urile dau verdictul. Câmpul `documentFindings` citează contractele care diferă, observațiile depășite și linkurile sau ancorele locale rupte din feedback. Convenția de marcare și probele sunt în [J6-REL-05](../feedback/J6-REL-05-RELEASE-RAG-GOVERNANCE.md).
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

### Alegerea nivelului de test

| Contract | Probă principală | Dovadă Android |
|---|---|---|
| Blinduri, all-in, side pots, split, restituiri și replay | Motor și sesiune JVM, cu sume exacte pe loc | O decontare reprezentativă când se schimbă afișarea |
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

Build-urile și fișierele brute de lucru se generează în TMP. Pentru fiecare candidat, `release/<versiune>/` păstrează APK-ul, checksum-ul, validarea și rapoartele Extent relevante; capturile necesare sunt încorporate în raport. `release/index.json` selectează o singură versiune curentă; manifestul acelei versiuni este sursa de adevăr pentru numărul de teste, hash-uri și starea descărcării. În README-ul principal, secțiunea `## Jocul` păstrează numai pagina documentației Release și linkul direct APK, în această ordine, pentru versiunea curentă. Mărimea, checksum-ul, starea și orice limitare de autentificare stau în documentația și manifestul versiunii. `PUBLISHED` cere separat verificarea descărcării complete fără autentificare. Un upload vizibil numai prin API autentificat nu trece poarta publicării; pentru răspunsuri `404` la profil și repo, urmați [diagnosticul GitHub](../release/ATF_Release.md#diagnostic-http-404-pentru-github-release).
