# Feedback Hold’em

Acest folder păstrează constatări despre produs, strategie, evaluare, interfață și livrare. Fiecare fișier declară prioritatea, starea și criteriile verificabile; blocajele externe identifică și owner-ul acțiunii. Dovezile mari și jurnalele de joc rămân în afara repository-ului, sub `C:\work\holdem_back`.

## Ordinea curentă

1. **J6-REL-01 · P1 · Blocat extern:** proprietarul contului trebuie să obțină de la GitHub restabilirea vizibilității publice. Asset-ul 0.1.0 este complet în API, dar ultima probă anonimă înregistrată a primit `404`; nu promovați catalogul la `PUBLISHED`.
2. **J6-AI-04 · P1 · Deschis:** ablația pereche a istoricului public are 16 seed-uri independente, sub pragul de 20 pentru fiecare mărime de masă; efectul observat nu justifică activarea unei ponderi generalizate.
3. **J6-AI-02 · P1 · Deschis:** politicile rămân euristice; lipsește un model calibrat de range și o evaluare pe holdout suficientă pentru a numi vreun stil `Hard`.
4. **J6-UI-04 · P2 · Deschis:** separați premiul potului de restituirea mizei neacoperite înainte de următoarea ediție Android.

`J6-REL-01` cere acțiunea proprietarului contului și nu se rezolvă prin redenumirea asset-ului sau schimbarea rutelor. Celelalte priorități sunt lucru de produs și cod.

| ID | Prioritate | Stare | Subiect |
|---|---|---|---|
| [J6-REL-01](J6-REL-01-PUBLIC-RELEASE-VISIBILITY.md) | P1 | Blocat extern | Repo-ul și asset-ul sunt publice în API-ul autentificat, dar profilul, repo-ul și Release-ul întorc `404` anonim. |
| [J6-REL-02](J6-REL-02-README-VERSION-GOVERNANCE.md) | P2 | Rezolvat | README-ul e legat de index; linkurile `PREPARED` cer receipt autentificat și nu expun checksum-uri. |
| [J6-AI-01](J6-AI-01-LATE-POSITION.md) | P2 | Rezolvat | Fereastra de poziție omitea butonul și nu se adapta meselor scurtate. |
| [J6-AI-02](J6-AI-02-STRATEGY-CALIBRATION.md) | P1 | Deschis | Stilurile sunt euristici; comparațiile mici nu justifică promovarea unei strategii Hard. |
| [J6-AI-04](J6-AI-04-PUBLIC-SHOVE-ABLATION.md) | P1 | Deschis | Ablație pereche pentru istoricul public; rezultatele rămân exploratorii și sub pragul de acceptare. |
| [J6-AI-03](J6-AI-03-COHORT-ARGUMENT.md) | P1 | Rezolvat | Argumentul YAML opțional făcea runner-ul să ignore stilul cerut. |
| [J6-UI-01](J6-UI-01-INTERACTIVE-TABLE.md) | P1 | Rezolvat | Mână de antrenament până la decontare, cu acțiuni validate și cărți adverse ascunse. |
| [J6-UI-02](J6-UI-02-TABLE-COVERAGE.md) | P2 | Rezolvat | Masă compactă la font 2,0, dialog de raise cu tastatura deschisă și rotație heads-up. |
| [J6-UI-03](J6-UI-03-TOURNAMENT-SANITY.md) | P1 | Rezolvat | Stive între mâini, eliminări, rezultat de turneu și sanity Android reproductibil. |
| [J6-UI-04](J6-UI-04-PAYOUT-READABILITY.md) | P2 | Deschis | Decontarea afișează la un loc potul câștigat și miza restituită. |
