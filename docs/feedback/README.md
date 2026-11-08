# Feedback Hold’em

## Pe scurt

Acest index ordonează lacunele active de produs, AI, interfață și release. Fiecare fișier deține prioritatea, ownerul, starea și criteriile verificabile; dovezile mari rămân în afara repository-ului, sub `<external-evidence-root>`.

Constatările rezolvate rămân marcate explicit până când legătura cu regula stabilă și probele ownerului este reconciliată; nu înlocuiesc documentația de produs.

## Ordinea curentă

1. **J6-REL-01 · P1 · Blocat extern.** Pentru `v0.2.1`, ambele fișiere au trecut verificarea prin API autentificat. APK-ul și checksum-ul răspund anonim cu HTTP 404, iar catalogul rămâne `PREPARED`.
2. **J6-REL-05 · P1 · Verificat în calificare:** retrieval-ul lexical și politica de versiuni au rulat pe candidatul curat 0.2.0. Conflictele semantice și feedback-ul învechit rămân de detectat.
3. **J6-AI-02 · P1 · Deschis:** politicile sunt încă euristice, iar modelul de range și evaluarea independentă nu justifică eticheta `Hard`.
4. **J6-AI-04 · P1 · Deschis, exploratoriu:** ablația pereche are 16 seed-uri independente, sub pragul de 20 pentru fiecare mărime de masă; efectul nu justifică o pondere generală.
5. **J6-UI-05 · P1 · 27/27 JVM, sanity API 28 trecut; acceptanță parțială:** Fold, reveal, coach, preluare locală și selectorul de blinduri există. Rămân evaluatorul kicker/board-play, invariabilitatea față de cărți ascunse, wiring-ul intervalului și verificările dedicate de accesibilitate/layout.
6. **J6-UI-07 · P1 · Implementare parțială:** evaluatorul, cărțile terminale egale, separarea premiu/rest și animația către beneficiari sunt conectate; lipsesc acceptanța Android și unele cazuri de componentă.
7. **J6-UI-04 · P2 · Implementare parțială:** motorul și locurile separă `Won` de `Returned`; mai lipsesc verificarea E2E a cazurilor de decontare și acceptanța vizuală.
8. **J6-UI-09 · P2 · Implementare parțială:** etichetele `Stack`, `Committed`, `Won` și `Returned` sunt secundare față de valori; mai lipsesc verificarea de viewport și TalkBack.
9. **J6-UI-10 · P1 · Verificat pe API 28:** fiecare loc arată ultima sa acțiune sub nume, iar istoricul complet este derulabil și ascuns inițial. Componentele trec 11/11; sanity-ul nativ trece 64/64 cu 8 capturi. Viewport-urile joase, TalkBack și traseul UI real `Bet → Raise → Call` rămân de calificat.

`J6-REL-01` cere acțiunea proprietarului contului și nu se rezolvă prin redenumirea asset-ului sau schimbarea rutelor. Celelalte priorități sunt lucru de produs și cod.

| ID | Prioritate | Stare | Subiect |
|---|---|---|---|
| [J6-REL-01](J6-REL-01-PUBLIC-RELEASE-VISIBILITY.md) | P1 | Blocat extern, `v0.2.1` anonim HTTP 404 | API-ul autentificat descarcă integral APK-ul și checksum-ul. |
| [J6-REL-02](J6-REL-02-README-VERSION-GOVERNANCE.md) | P2 | Rezolvat | Bloc comun cu două linkuri ordonate, sincronizat cu indexul; catalog/proces legate și stări `PREPARED`/`PUBLISHED` guvernate; fără checksum în README. |
| [J6-REL-03](J6-REL-03-ANDROID-MIN-SDK-API-COMPATIBILITY.md) | P1 | Corectat; tournament și release-smoke trecute pe API 28 | Recomandarea AI folosea două metode Java de colecții indisponibile pe API-ul minim. |
| [J6-REL-04](J6-REL-04-EXTENT-SUITE-COVERAGE.md) | P1 | Închis pe 0.2.1 | Extent cere aserțiuni native obligatorii și verifică inventarul capturilor. |
| [J6-REL-05](J6-REL-05-RELEASE-RAG-GOVERNANCE.md) | P1 | Verificat în calificare, contradicții restante | Retrieval-ul lexical consultativ păstrează citările și digestul în manifestul calificării. |
| [J6-AI-01](J6-AI-01-LATE-POSITION.md) | P2 | Rezolvat | Fereastra de poziție omitea butonul și nu se adapta meselor scurtate. |
| [J6-AI-02](J6-AI-02-STRATEGY-CALIBRATION.md) | P1 | Deschis | Stilurile sunt euristici; comparațiile mici nu justifică promovarea unei strategii Hard. |
| [J6-AI-04](J6-AI-04-PUBLIC-SHOVE-ABLATION.md) | P1 | Deschis | Ablație pereche pentru istoricul public; rezultatele rămân exploratorii și sub pragul de acceptare. |
| [J6-AI-03](J6-AI-03-COHORT-ARGUMENT.md) | P1 | Rezolvat | Argumentul YAML opțional făcea runner-ul să ignore stilul cerut. |
| [J6-UI-01](J6-UI-01-INTERACTIVE-TABLE.md) | P1 | Rezolvat | Mână de antrenament până la decontare, cu acțiuni validate și cărți adverse ascunse. |
| [J6-UI-02](J6-UI-02-TABLE-COVERAGE.md) | P2 | Rezolvat | Masă compactă la font 2,0, dialog de raise cu tastatura deschisă și rotație heads-up. |
| [J6-UI-03](J6-UI-03-TOURNAMENT-SANITY.md) | P1 | Rezolvat | Stive între mâini, eliminări, rezultat de turneu și sanity Android reproductibil. |
| [J6-UI-04](J6-UI-04-PAYOUT-READABILITY.md) | P2 | Implementare parțială; E2E restant | Motorul distinge premiul potului de restituirea contribuției neacoperite; trebuie calificată prezentarea cazurilor reale. |
| [J6-UI-05](J6-UI-05-LOCAL-COACH-AND-POSTGAME-REVIEW.md) | P1 | 27/27 JVM; sanity API 28 trecut; acceptanță parțială | Mai lipsesc oracolul kicker/board-play, testul de confidențialitate, testarea spinnerului și verificările dedicate de accesibilitate/layout. |
| [J6-UI-06](J6-UI-06-RESUMABLE-TURNS-AND-SETTLEMENT.md) | P1 | Închis pentru continuare | Destinația se salvează la tap și se redeschide o singură dată după întrerupere. |
| [J6-UI-07](J6-UI-07-SHOWDOWN-WIN-CLARITY.md) | P1 | Implementare parțială; acceptanță restantă | Evaluatorul expune cele cinci cărți; dimensiunile sunt comune, iar premiul și restituirea au destinații distincte. |
| [J6-UI-08](J6-UI-08-TOURNAMENT-LEADERBOARD.md) | P2 | Închis pe ramura locală | Pe API 28, numele nou apare o singură dată după restartul procesului, cu același ID și punctaj. |
| [J6-UI-09](J6-UI-09-SEAT-METRIC-HIERARCHY.md) | P2 | Implementare parțială; verificare viewport/A11y restantă | Valoarea are accent mai puternic decât eticheta; semantică și descriere completă păstrate. |
| [J6-UI-10](J6-UI-10-BETTING-ACTION-LEDGER.md) | P1 | API 28 verificat; 11/11 componente și 63/63 E2E, 8 capturi | Ultima acțiune este afișată sub fiecare loc; istoricul cronologic complet se deschide la cerere. Rămân viewport-urile joase, TalkBack și traseul UI real `Bet → Raise → Call`. |
