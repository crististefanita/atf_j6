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

- Admitere bibliotecă. Build-ul verifică cele opt fișiere Card Table după proveniență, coordonate și SHA-256, înainte de împachetare. J6 consumă Card Table UI 0.1.7; producătorul califică pacing-ul comun de 550 ms după primul cadru vizibil, iar consumatorul păstrează testul de proveniență exactă.
- Componente JVM. Motorul păstrează cărțile observatorului când se schimbă actorul, inclusiv când se modifică numai cărți ascunse. Sesiunea Android joacă mese de 4, 5 și 6 locuri și verifică acțiunile invalide, comenzile repetate, fold, all-in, restaurarea și conservarea jetoanelor.
- Sanity nativ. `TournamentSanity` folosește controalele accesibile în două trasee scurte: trei mâini până la victorie și o mână separată până la eliminare. Varianta `sanity` semnată, dar debuggable, acceptă seed-ul reproductibil; varianta distribuibilă `release` nu acceptă seed-uri și rămâne `debuggable=false`. Al doilea traseu pornește și navighează în chiar APK-ul Release. Extent leagă API-ul și geometria ferestrei de hash-ul APK-ului instalat, ordinea acțiunilor, aserțiuni și capturi încorporate, transmise prin rezultatul instrumentării.
- Profil compact. Componentele verifică rândurile pentru 2, 4, 5 și 6 locuri, plus ordinea heads-up în ambele poziții. Pe emulator, profilul de 320 × 569 dp cu font 2,0 verifică șase chenare, board-ul, acțiunile și dialogul de raise cu tastatura deschisă. O a doua trecere pe același profil verifică schimbarea dealerului heads-up. Scriptul restabilește configurația emulatorului.
- Reluare și prezentarea turei. `HoldemSessionRepositoryComponentTest` verifică checkpoint-ul durabil după o acțiune umană și o acțiune automată, actorul următor după replay, jurnalul reordonat, păstrarea checkpoint-ului vechi la eșec și recuperarea după întreruperea dintre redenumiri. `LocalHoldemGameTest` verifică acțiunile automate una câte una. `HoldemTablePresentationComponentTest` verifică un singur chenar pentru actorul fizic la mese de 2/4/5/6 locuri și nicio evidențiere la settlement; setul de cărți colectate respectă vizibilitatea, iar `TrickCollectionMotion` păstrează cele 1.000 ms de expunere și ultimele 500 ms de mișcare spre pot. Aceste teste rulează fără emulator.
- Turneu, componente JVM. `LocalHoldemTournamentTest` verifică stivele exacte, locurile sărite după eliminare, tranziția la heads-up, plata finală, înfrângerea, restaurarea mai multor runde și refuzul comenzilor expirate. Verifică nivelurile de blind la intervale configurate și pauza ceasului în fundal folosind un ceas monoton injectat; timpul suspendat nu avansează nivelul, fără `sleep` sau toleranțe temporale. Rulați această suită la fiecare schimbare a sesiunii.
- Turneu, sanity Android. `TournamentSanity` păstrează două trasee scurte prin controalele afișate. Primul acoperă străzile, două all-in-uri, eliminările și felicitările. Al doilea verifică rezultatul când jucătorul este eliminat. Matricea de 4–6 locuri rămâne pe JVM. Extent include rezultatele JUnit, aserțiunile native, ordinea acțiunilor și capturile încorporate.
- Guvernanță release. `ReleaseGovernanceTest` verifică offline starea catalogului, identitatea Android, blocul `## Jocul` (dosarul ediției în `DRAFT`; exact două linkuri ordonate în `PREPARED`/`PUBLISHED`), linkurile tehnice către catalog/proces, lipsa checksum-ului din README, politica de semnare, scriptul credentialless de descărcare și păstrarea octeților în Git. Blocul pentru utilizator nu acceptă mărime, checksum, stare, cerință de autentificare sau linkuri suplimentare; dovada descărcării pentru `PREPARED` rămâne în manifestul ediției. Mutanții negativi resping versiunea veche, ordinea greșită, linkul duplicat, textul zgomotos și rutele nepotrivite stării. Politica API Android minimă respinge metode Java de colecții indisponibile pe `minSdk 26`; testele native validează și calea de recomandare AI pe API 28. Testele publică rapoarte Extent, inspectate cu `ReportArtifactInspector`. `Build-HoldemRelease.ps1` cere un commit curat și verifică separat turneul determinist `sanity` și smoke-ul APK-ului `release` instalat.
- Acoperire Extent. `v0.1.1` rămâne calificarea istorică: 13 suite Maven / 80 teste și 4 suite Android JVM / 27 teste; rapoartele ei nu se rescriu. Runnerul curent publică separat, prin `atf-reporting`, un Extent pentru fiecare domeniu componentă și păstrează E2E-ul Android nativ în rapoarte independente. Snapshotul sincronizat din 2026-11-08 a reconciliat 16/16 suite Maven / 97 teste, 5/5 suite Android JVM / 43 teste și tournament sanity API 28 / 64 aserțiuni / 8 capturi. Rapoartele componente și E2E au trecut inspecția publisherelor și se află în `C:\Users\Cristi\AppData\Local\Temp\j6-live-final6-20261108\reports\`; nu sunt calificarea unei ediții și nu au fost mutate în `release/0.1.1`. Vezi [J6-REL-04](../feedback/J6-REL-04-EXTENT-SUITE-COVERAGE.md).
- Context de impact release. `ReleaseImpactRag` construiește offline un pachet lexical cu surse, linii, hash-uri și suite recomandate; contextul are rol consultativ și nu e verdict, raport de test sau dovadă de PASS. `ReleaseVersionPolicy` validează clasa și versiunea. Probele de retrieval insuficient, redactare și izolare sunt teste componente; rularea completă pe un candidat curat și probele pentru contradicții sunt încă restante în [J6-REL-05](../feedback/J6-REL-05-RELEASE-RAG-GOVERNANCE.md).
- Lifecycle UI. Componentele verifică timerul determinist; logica Activity invalidează callback-urile, oprește ceasul și checkpoint-uiește la `onPause`, apoi reconstruiește scena la `onResume`. Rezultatul AI este doar o sugestie până când UI-ul revalidează Activity și tokenul curent. O probă de componentă nu înlocuiește verificarea nativă a rotației/background-ului pe emulator.
- Separarea semnării. `:app:testSanityUnitTest` rulează fără identitatea privată; `verifyHoldemReleaseSigning` este dependență doar pentru împachetarea APK-urilor `sanity`/`release` și semnarea AAB-urilor. `:app:testDebugUnitTest` nu există deoarece `testBuildType` selectează `sanity`. Dry-run-ul `:app:assembleSanity` trebuie să includă gate-ul de semnare, iar testele JVM nu trebuie să-l includă.
- Capturi native. Arborele de accesibilitate poate anunța un ecran înainte ca imaginea să fie afișată. Runnerul sincronizează captura cu cadrele Android și cu stabilizarea accesibilității. Receipt-ul și PNG-urile sunt transmise prin instrumentare, nu citite din datele private ale aplicației. Publicarea compară SHA-256 al APK-ului instalat cu binarul furnizat din build.

[Comenzi de calificare](../../holdem-android/README.md#verificare)

[Procesul de release](../release/ATF_Release.md)

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
