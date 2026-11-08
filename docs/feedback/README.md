# Feedback Hold’em

## Pe scurt

Acest index ordonează lacunele active de produs, AI, interfață și release. Fiecare fișier deține prioritatea, ownerul, starea și criteriile verificabile; dovezile mari rămân în afara repository-ului, sub `<external-evidence-root>`.

Constatările rezolvate rămân marcate explicit până când legătura cu regula stabilă și probele ownerului este reconciliată; nu înlocuiesc documentația de produs.

## Ordinea curentă

1. **J6-REL-01 · P1 · Blocat extern:** proprietarul contului trebuie să obțină de la GitHub restabilirea vizibilității publice. Ultima probă anonimă înregistrată a primit `404`; catalogul rămâne `PREPARED`.
2. **J6-AI-02 · P1 · Deschis:** politicile sunt încă euristice, iar modelul de range și evaluarea independentă nu justifică eticheta `Hard`.
3. **J6-AI-04 · P1 · Deschis, exploratoriu:** ablația pereche are 16 seed-uri independente, sub pragul de 20 pentru fiecare mărime de masă; efectul nu justifică o pondere generală.
4. **J6-UI-05 · P1 · 27/27 JVM, sanity API 28 trecut; acceptanță parțială:** Fold, reveal, coach, preluare locală și selectorul de blinduri există. Rămân evaluatorul kicker/board-play, invariabilitatea față de cărți ascunse, wiring-ul intervalului și verificările dedicate de accesibilitate/layout.
5. **J6-UI-06 · P1 · 27/27 JVM și sanity API 28 trecut:** Reluarea, pacing-ul, colectarea și cele două trasee native au capturi Extent validate. Tapping-ul „New hand” în timpul colectării este acum reținut o singură dată; inspecția cadru-cu-cadru și API 26 rămân neacoperite.
6. **J6-UI-04 · P2 · Deschis:** separați premiul potului de restituirea mizei neacoperite în prezentarea decontării.

`J6-REL-01` cere acțiunea proprietarului contului și nu se rezolvă prin redenumirea asset-ului sau schimbarea rutelor. Celelalte priorități sunt lucru de produs și cod.

| ID | Prioritate | Stare | Subiect |
|---|---|---|---|
| [J6-REL-01](J6-REL-01-PUBLIC-RELEASE-VISIBILITY.md) | P1 | Blocat extern | Repo-ul și asset-ul sunt publice în API-ul autentificat, dar profilul, repo-ul și Release-ul întorc `404` anonim. |
| [J6-REL-02](J6-REL-02-README-VERSION-GOVERNANCE.md) | P2 | Rezolvat | Bloc comun cu două linkuri ordonate, sincronizat cu indexul; catalog/proces legate și stări `PREPARED`/`PUBLISHED` guvernate; fără checksum în README. |
| [J6-REL-03](J6-REL-03-ANDROID-MIN-SDK-API-COMPATIBILITY.md) | P1 | Corectat; tournament și release-smoke trecute pe API 28 | Recomandarea AI folosea două metode Java de colecții indisponibile pe API-ul minim. |
| [J6-AI-01](J6-AI-01-LATE-POSITION.md) | P2 | Rezolvat | Fereastra de poziție omitea butonul și nu se adapta meselor scurtate. |
| [J6-AI-02](J6-AI-02-STRATEGY-CALIBRATION.md) | P1 | Deschis | Stilurile sunt euristici; comparațiile mici nu justifică promovarea unei strategii Hard. |
| [J6-AI-04](J6-AI-04-PUBLIC-SHOVE-ABLATION.md) | P1 | Deschis | Ablație pereche pentru istoricul public; rezultatele rămân exploratorii și sub pragul de acceptare. |
| [J6-AI-03](J6-AI-03-COHORT-ARGUMENT.md) | P1 | Rezolvat | Argumentul YAML opțional făcea runner-ul să ignore stilul cerut. |
| [J6-UI-01](J6-UI-01-INTERACTIVE-TABLE.md) | P1 | Rezolvat | Mână de antrenament până la decontare, cu acțiuni validate și cărți adverse ascunse. |
| [J6-UI-02](J6-UI-02-TABLE-COVERAGE.md) | P2 | Rezolvat | Masă compactă la font 2,0, dialog de raise cu tastatura deschisă și rotație heads-up. |
| [J6-UI-03](J6-UI-03-TOURNAMENT-SANITY.md) | P1 | Rezolvat | Stive între mâini, eliminări, rezultat de turneu și sanity Android reproductibil. |
| [J6-UI-04](J6-UI-04-PAYOUT-READABILITY.md) | P2 | Deschis | Decontarea însumează potul câștigat și restituirea contribuției neacoperite în aceeași valoare `Received`. |
| [J6-UI-05](J6-UI-05-LOCAL-COACH-AND-POSTGAME-REVIEW.md) | P1 | 27/27 JVM; sanity API 28 trecut; acceptanță parțială | Mai lipsesc oracolul kicker/board-play, testul de confidențialitate, testarea spinnerului și verificările dedicate de accesibilitate/layout. |
| [J6-UI-06](J6-UI-06-RESUMABLE-TURNS-AND-SETTLEMENT.md) | P1 | 27/27 JVM; două trasee native API 28 trecute | Persistența, reluarea, acțiunile temporizate și colectarea au probe. Rămân inspecția cadru-cu-cadru și runtime-ul API 26. |
