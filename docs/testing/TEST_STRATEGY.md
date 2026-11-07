# Strategie de testare

## Niveluri

### Contracte de componentă

Testele curente acoperă ordinea actorilor, sizing-ul minim, menținerea stării după acțiuni ilegale, all-in-uri scurte cumulative, evaluarea mâinii și decontarea pe straturi. `config/j6-allin-challenges.yaml` este fixture-ul controlat pentru redeschidere, egalitate și jetoane impare; JUnit îl rulează prin motor, iar `audit-allin-challenge.cjs` reface separat acțiunile, clasificarea și plata.

### Politici AI

Testele verifică replay-ul determinist, fallback-urile și acțiunile legale pentru TAG, calling station, pressure, loose-aggressive, bluff și equity Monte Carlo. Estimatorul are verificări pentru pot împărțit, reproducibilitate, duplicate de cărți, bugetul de eșantionare și scăderea equity-ului preflop când cresc adversarii activi. Politica equity testează pot odds, incertitudinea, agresiunile adversarilor fără auto-numărarea acțiunilor proprii, ordinea postflop după fold-uri și diferența de marjă între cash și turneu. YAML-urile verifică exact setul de ponderi admis.

Aceste teste demonstrează contractul și direcția pragurilor; nu demonstrează valoarea EV a ponderilor. Cărțile adversarilor sunt eșantionate uniform, deci politica încă nu are range-uri condiționate din acțiunile individuale.

### Aceeași distribuție, buton rotit

`PositionRotationExperimentTest` joacă două distribuții complete, câte o dată cu fiecare dintre cele șase locuri la buton și șapte stiluri, inclusiv politica equity Monte Carlo; bluff-ul forțat la 100% rămâne doar probă de accesibilitate. Cărțile private rămân atașate locurilor fizice, board-ul planificat este identic, iar politica primește numai observația actorului. JUnit oprește jocul dacă proiecția sau mutarea nu este legală; `audit-position-rotations.cjs` verifică separat din jurnal pozițiile, cărțile private ale actorului, prefixul public al board-ului, unicitatea cărților și că distribuțiile nu s-au schimbat între rotații. Reveal-ul complet apare numai după terminarea mâinii.

### Arena

`TournamentArena` rulează turnee complete pentru mesele de 4–6 locuri; fiecare jurnal trece prin `audit-tournament.cjs`, care reface seed-ul, ordinea, legalitatea, clasificarea și poturile independent de motorul Java. `CashTableArena` rulează mese de 2–6 locuri cu blind-uri fixe; `audit-cash-sessions.cjs` verifică dealerul, structura fixă, durata, stivele și conservarea jetoanelor. Brațele cash candidate/control pornesc cu aceleași seed-uri și primul buton, iar auditorul validează separat orice rotație după eliminări. `compare-cash-sessions.cjs` măsoară cota de stivă a locurilor candidate; nu o interpretează ca EV.

Comparațiile de turneu folosesc aceleași seed-uri și înlocuiesc numai locurile candidate. `equity` și `legacy-equity` aleg politica contextuală; aceasta compară aceeași distribuție la aceleași poziții. Variantele de praguri loose-aggressive sunt evaluate separat prin YAML-uri pereche. Rotațiile fixe izolează comportamentul pozițional; turneele și sesiunile cash verifică interacțiunea în joc complet. Niciun lot mic nu este suficient singur pentru a afirma EV pozitiv.

`run-button-position-tournaments.ps1` rotește butonul inițial pe aceeași secvență de seed-uri pentru fiecare turneu complet; candidat și control păstrează același offset. `audit-button-rotations.cjs` verifică seed-urile fiecărei mâini între offseturi, pozițiile de start și comparația pereche. În acest experiment cărțile se redistribuie între locurile fizice când se mută butonul; rotația exactă a acelorași cărți private la aceleași locuri este acoperită separat de fixture-urile din `PositionRotationExperimentTest`.

### Online contract

`PlatformLibraryAdoptionTest` verifică transportul mock la nivelul bibliotecii ATF. Pentru adaptorul Open Poker, suita planificată va folosi un server local și va acoperi connected, stale token, duplicate action, disconnect, resync, rejected action, table close și season end. [Cerințele adaptorului](../requirements/ONLINE_REQUIREMENTS.md)

### Interfața Android locală

- Admitere bibliotecă. Build-ul verifică cele opt fișiere Card Table după proveniență, coordonate și SHA-256, înainte de împachetare.
- Componente JVM. Motorul păstrează cărțile observatorului când se schimbă actorul, inclusiv când se modifică numai cărți ascunse. Sesiunea Android joacă mese de 4, 5 și 6 locuri și verifică acțiunile invalide, comenzile repetate, fold, all-in, restaurarea și conservarea jetoanelor.
- Sanity nativ. `TournamentSanity` folosește controalele accesibile în două trasee scurte: trei mâini până la victorie și o mână separată până la eliminare. Verifică străzile, plata potului, eliminarea, tranziția heads-up și rezumatele finale. Extent leagă API-ul și geometria ferestrei de hash-ul APK-ului instalat, ordinea acțiunilor, aserțiuni și capturi încorporate.
- Profil compact. Componentele verifică rândurile pentru 2, 4, 5 și 6 locuri, plus ordinea heads-up în ambele poziții. Pe emulator, profilul de 320 × 569 dp cu font 2,0 verifică șase chenare, board-ul, acțiunile și dialogul de raise cu tastatura deschisă. O a doua trecere pe același profil verifică schimbarea dealerului heads-up. Scriptul restabilește configurația emulatorului.
- Turneu, componente JVM. `LocalHoldemTournamentTest` verifică stivele exacte, locurile sărite după eliminare, tranziția la heads-up, plata finală, înfrângerea, restaurarea mai multor runde și refuzul comenzilor expirate. Rulați această suită la fiecare schimbare a sesiunii.
- Turneu, sanity Android. `TournamentSanity` păstrează două trasee scurte prin controalele afișate. Primul acoperă străzile, două all-in-uri, eliminările și felicitările. Al doilea verifică rezultatul când jucătorul este eliminat. Matricea de 4–6 locuri rămâne pe JVM. Extent include rezultatele JUnit, aserțiunile native, ordinea acțiunilor și capturile încorporate.
- Guvernanță release. `ReleaseGovernanceTest` verifică offline starea catalogului, identitatea Android, navigarea din README, linkul APK permis de stare și păstrarea octeților în Git. `Build-HoldemRelease.ps1` cere un commit curat și înregistrează separat sanity-ul Debug și smoke-ul APK-ului Release.
- Capturi native. Arborele de accesibilitate poate anunța un ecran înainte ca imaginea să fie afișată. Runnerul sincronizează captura cu cadrele Android și cu stabilizarea accesibilității. Publicarea compară SHA-256 al APK-ului instalat cu cel furnizat din build.

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

Build-urile și fișierele brute de lucru se generează în TMP. Pentru fiecare candidat, `release/<versiune>/` păstrează APK-ul, checksum-ul, validarea și rapoartele Extent relevante; capturile necesare sunt încorporate în raport. `DRAFT` rămâne local, `PREPARED` cere cheia de distribuție și gate-urile proiectului, iar `PUBLISHED` cere asset-ul public și verificarea descărcării complete.
