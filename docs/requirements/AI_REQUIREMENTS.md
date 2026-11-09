# Cerințe AI și evaluare

## Principiu

AI-ul nu trebuie să fie doar o funcție care alege o acțiune. Fiecare decizie trebuie să poată răspunde la:

- ce observație a primit;
- ce acțiuni erau legale;
- ce politică și versiune a folosit;
- ce buget de calcul a avut;
- ce utilitate a estimat;
- ce incertitudine a avut;
- ce fallback a aplicat dacă bugetul sau datele au eșuat.

## Niveluri

### L0 — legal fallback

Returnează o acțiune legală în timp bounded. Nu pretinde forță strategică.

### L1 — baseline

Preflop: poziție, perechi, broadway, suitedness și range-uri fixe. Postflop: hand class, pot odds, board texture și bet sizing simplificat.

### L2 — equity cu range condiționat (țintă)

Calculează equity împotriva unui range ponderat, actualizează range-ul din acțiuni și separă made hand, draw, bluff-catcher și air. Acest nivel nu este încă implementat complet.

### L3 — search

Folosește sampling al lumilor ascunse și rollout-uri cu adversari expliciți, dar nu confundă lumi fezabile cu distribuții calibrate.

### L4 — CFR / self-play

Folosește regret minimization, abstractizare de acțiuni și board-uri, evaluare de exploitability proxy și populații diverse de adversari.

## Implementarea curentă: equity Monte Carlo cu context public

`EQUITY-MC-0.4` estimează cota teoretică dintr-un pot comun prin eșantionarea cărților necunoscute. Returnează probabilitatea de victorie, egalitate și eroarea standard pentru bugetul YAML. Cota din potul comun presupune aceeași eligibilitate la jetoane, în timp ce poturile secundare au eligibilitate proprie.

Distribuția cărților adversarilor rămâne uniformă între combinațiile necunoscute. Acțiunile și poziția publice ajustează pragurile; istoricul de shove este un semnal limitat de agresivitate, nu o estimare de range. Prin urmare, aceasta este o **estimare de bază împotriva cărților aleatoare**, nu un solver și nu o estimare completă de range.

```mermaid
flowchart LR
    OBS[Observația actorului] -->|filtrează| DECK[Elimină cărțile proprii și board-ul cunoscut]
    DECK -->|alimentează| MC[Eșantionează adversari activi și board-ul rămas]
    MC -->|estimează| EQ[Equity medie, win/tie și eroare standard]
    OBS -->|descrie| CTX[Pot odds, adversari activi, acțiuni publice, locuri care mai pot acționa]
    EQ -->|se compară cu| CALL[Prag de call]
    EQ -->|se compară cu| RAISE[Prag de raise Hard]
    CTX -->|ajustează| CALL
    CTX -->|ajustează| RAISE
    RAISE -->|echitate și sumă legale| ACT{Acțiune}
    CALL -->|alternativă la raise| ACT
```

<details>
<summary><strong>Legendă — observație și prag</strong></summary>

- **Eșantionarea** estimează equity din cărțile necunoscute.
- **Contextul public** alimentează ambele praguri. Fiecare folosește ponderile acțiunii sale.
- **Acțiunea** este aleasă numai dintre opțiunile legale.

</details>

| Decizie | Calcul și limite |
|---|---|
| Call | Pentru un call pozitiv, `c = min(toCall, heroStack)`. Pragul pornește de la `ceil(100 × c / (pot + c))` și adaugă marjele de mod, adversari, poziție, agresiune, presiune, stivă și incertitudine. Intervalul final este 0–98%. |
| Raise contra unui preț pozitiv | Hard cu `raiseContext` calculează separat pragul de raise. Profilurile fără acest bloc folosesc pragul de call plus `raiseEdgePercent` și marja pentru adversari suplimentari. Motorul furnizează intervalul legal. |
| Bet | Când prețul este zero, pragul de valoare ține cont de adversari, agresiune, poziție și mod. Politica de bază calculează 25–75% din pot înainte de aplicarea limitelor legale. |

- **Incertitudine.** `uncertaintyZHundredths: 164` înseamnă `z = 1,64`. Componenta de prag este `ceil(z × standardError × 100)`, în puncte procentuale. Agresiunile proprii sunt excluse.
- **Reproductibilitate.** Seed-ul folosește valori stabile pentru rang, suită și stradă, alături de istoricul public. Același model, aceeași observație și același buget păstrează decizia după repornirea JVM. Strategiile mixte din arenă folosesc aceeași reprezentare a cărților pentru selecția de bluff și agresiune.
- **Configurare.** `equityDecision.contextWeights` declară ponderile. Parserul verifică setul exact de chei, iar versiunea deciziei identifică bugetul, modul, pragurile și ponderile. [Profil equity cu buget mărit](../../config/j6-policy-equity.yaml)
- **Limită de model.** Riscul de turneu este o marjă configurată. ICM, valoarea premiilor și equity pe straturile potului rămân în afara estimatorului.

### Profilul Hard al turneului local

- Normal păstrează adversarii check/call. În Hard, adversarii, coach-ul și preluarea folosesc aceeași politică contextuală cu 512 eșantioane pe decizie.
- Profilul reduce cu până la trei puncte procentuale pragul unei continuări preflop de cel mult două blinduri mari. Păstrează pragul obișnuit în fața unui all-in agresiv sau a riscului de eliminare și ține cont de costul apelului și eroarea estimării.

#### Raise cu preț pozitiv

Turneul local Hard folosește aceleași ponderi ca profilul YAML de arenă. Ponderile din `hardPressure.raiseContext` sunt exprimate în puncte procentuale, iar limita de angajare este un procent din stivă.

| Semnal | Aplicare |
|---|---|
| Preț și incertitudine | Pornește de la pot odds rotunjite în sus, eroarea de eșantionare, `baseMargin` și `raiseEdgePercent`. |
| Poziție | Adaugă `positionMargin` devreme și o scade pentru ultima jumătate a locurilor care pot acționa, rotunjită în jos la minimum un loc. Heads-up, ultimul actor beneficiază de reducere. |
| Adversari | Adaugă `multiwayMargin` pentru fiecare adversar suplimentar în mână. Numără până la trei agresiuni adverse pe strada curentă. Adaugă prime distincte când o acțiune ridică prețul cu cel puțin 75% din potul anterior sau când un all-in ridică prețul. |
| Acoperire | Plafonează suma la capacitatea celui mai bine finanțat adversar activ, ținând cont de contribuții. Exclude jucătorii care au dat fold sau și-au consumat stiva. Dacă plafonul cade sub minimul legal, evaluează call-ul sau fold-ul. |
| Angajarea stivei | La `commitmentThresholdPercent` din stiva rămasă, cere `commitmentMargin` în plus. Când echitatea justifică doar o mărire mai mică, plafonează suma imediat sub limită și verifică minimul legal. |

Discountul de call ieftin, reducerea de urgență pentru stivă scurtă și istoricul de shove rămân în pragul de call. Raise-ul folosește acțiunile adverse din mâna curentă. Jurnalul separă pragul de call `required` de `raiseRequired` și afișează plafonul `raiseCap` când motorul oferă raise.

#### Pariu de deschidere postflop

| Deschidere heads-up la preț zero | Condiții suplimentare |
|---|---|
| 1× pot | Turn sau river, cel puțin două perechi și equity conservatoare de minimum 76%. |
| 2× pot | Aceleași condiții, cu minimum 86%. |
| 3× pot | River, cel puțin chintă și minimum 94%. |

Equity conservatoare scade marja de incertitudine din cota estimată. Suma trebuie să intre în intervalul legal și să fie acoperită de stiva adversarului rămas. Politica încearcă mărimile descrescător și revine la mărimea de bază dacă niciuna nu se potrivește. [Configurația comparată în arenă](../../config/policy-tuning/j6-policy-hard-pressure.yaml)

#### Opțiunea de mărire din blindul mare

Arena poate activa `hardPressure.bigBlindValueRaise` pentru o comparație dedicată. Profilul implicit de turneu și configurațiile YAML fără această cheie folosesc valoarea `false`.

| Pas | Regulă |
|---|---|
| Oportunitate | Preflop, motorul oferă raise legal la preț zero, după ce adversarii au egalat blindul fără să mărească. |
| Prag | Equity conservatoare atinge maximul dintre 45% și cota egală din pot plus 20 de puncte procentuale. Cota egală este 100% împărțit la numărul de jucători încă în mână, inclusiv actorul. |
| Sumă | Ținta este jumătate din pot, în limitele motorului, sub stiva actorului și până la suma pe care o poate egala cel puțin un adversar activ. Când plafonul cade sub minimul legal, politica dă check. |
| Date | Pragul folosește cărțile proprii și informația publică. Probabilitatea de fold a adversarilor rămâne în afara acestui calcul. |

### Riscul public de stack și all-in

- **Oportunitate.** În turneu, locul fizic intră în numitor pentru o decizie preflop cu stiva rămasă peste 2 și cel mult 10 blinduri mari și cu un `ALL_IN` legal peste prețul call-ului. Open-shove și re-shove intră împreună, iar call-ul all-in rămâne în afara numitorului.
- **Moment.** `PublicShoveHistory` publică în arenă și în turneul Hard numai contoarele mâinilor încheiate. Android le reconstruiește la replay-ul acțiunilor acceptate. La raise-ul all-in curent, stiva rămasă este suma acțiunii publice, după blindurile deja plătite.
- **Loc.** `HoldemObservation` separă indexul compact al motorului de `physicalHeroSeat`, folosit în jurnal. Ordinea postflop exclude locurile foldate și fără jetoane, cu fallback la poziția din observație dacă lipsește harta publică.

| Pas | Efect asupra pragului de call |
|---|---|
| Estimare | Pentru `n` oportunități și `r` raise-uri all-in, posteriorul este `p = (r + p₀·k) / (n + k)`, din priorul YAML `p₀` și tăria `k`. |
| Încălzire | Ponderea dovezii crește până la `w = min(1, n / warmup)`. |
| Agregare | Calculează `(p₀ − p) · sensibilitate · w` pentru fiecare autor curent eligibil cu istoric. Media se rotunjește o singură dată și se aplică plafonul simetric YAML. |
| Interpretare | O frecvență peste prior reduce pragul. O frecvență sub prior îl crește. Fără istoric măsurat, ajustarea este zero. |

- **Presiune și eliminare.** Istoricul străzii semnalează un preț mare sau un all-in care l-a ridicat. În turneu, penalizarea de eliminare cere un call care angajează pragul configurat al stivei și un adversar activ care poate acoperi contribuția. La cel mult 10 blinduri mari se aplică discountul de urgență.
- **Domeniu.** Cash ignoră istoricul de shove de turneu. Frecvența publică descrie disponibilitatea de a împinge din stivă scurtă, iar cărțile necunoscute rămân distribuite uniform. Modelarea după poziție, preț, mărimea mesei, adversar, premii sau bounty cere măsurători separate.
- **Ablație.** Lineup-ul păstrează aceleași ponderi și schimbă numai sensibilitatea istoricului între control și varianta activă. Ambele joacă împotriva acelorași roluri și se rotesc prin locuri și poziții pe seed-uri identice. Agregați rotațiile în interiorul seed-ului înainte de a calcula incertitudinea.

## Acceptanță

O schimbare de decizie trebuie să păstreze legalitatea, reproductibilitatea și izolarea cărților ascunse. Evaluarea competitivă măsoară separat diferența față de control.

- **Control.** Compară pe un corpus ținut separat și pe seed-uri independente, cu același buget și mai multe stiluri de adversar.
- **Raport.** Include numărul de turnee și mâini, diferența pereche, intervalul de încredere și drawdown-ul când jurnalul îl măsoară.
- **Promovare competitivă.** Cere un avantaj față de control care depășește incertitudinea. O schimbare structurală poate fi acceptată pentru comportamentul verificat în teste și trebuie evaluată separat înainte de a i se atribui un avantaj la victorii.

## Politică mixtă de bluff pentru laborator

Politica experimentală `BLUFF-MIXED-0.1` explorează o deschidere minimă după check, cu frecvență și fereastră de poziție configurate. [YAML-ul politicilor](../../config/j6-policy.yaml)

- **Eligibilitate.** Postflop, As pe board, două cărți proprii de ranguri diferite, fără As în mână și fără potrivire cu rangurile de pe board. Actorul este în poziție târzie, istoricul străzii conține cel puțin un check și nicio altă acțiune, iar `BET` este legal.
- **Decizie.** Pe o observație eligibilă, o valoare deterministă decide dacă politica alege bet-ul minim. Restul cazurilor trec la TAG, inclusiv când există un preț de call.
- **Evaluare.** Raportați frecvența bluff-urilor, chips/hand și drawdown pe profil adversar. Triggerul folosește semnale vizibile și nu estimează range, fold equity, textura completă a board-ului sau istoricul de call.

## Coordonata de poziție și limita informației

- `positionFromButton = 0` identifică butonul, ultimul actor după flop în ordinea inițială. Valorile cresc în ordinea locurilor de la masă.
- Fereastra târzie include butonul și cele `n − 1` locuri dinaintea lui, plafonată la numărul de locuri rămase.

Politica primește numai `HoldemObservation`: propriile două cărți, board-ul public până la strada curentă, miza, stivele vizibile, acțiunile publice și setul de mutări legale. Seed-ul, pachetul și cărțile ascunse ale adversarilor rămân în motor și în auditor. În evaluările cu distribuții fixe, auditorul verifică proiecția la fiecare decizie, iar distribuția completă se dezvăluie numai după încheierea mâinii.

- Pentru equity, adversarii nefoldați rămân eligibili în estimare, inclusiv când sunt all-in.
- Ordinea de acțiune exclude locurile all-in. Politica primește istoricul public și vede doar cărțile proprii și board-ul deja deschis.

## Stiluri comparate în laborator

| Stil | Comportament măsurat | Limita interpretării |
|---|---|---|
| TAG | Pereche sau As preflop, call limitat după preț și fold conservator | Baseline-ul este deliberat simplu; nu este un solver. |
| Calling station | Check gratuit, altfel call; nu ridică voluntar | Servește drept model de adversar pasiv, nu candidat de câștig. |
| Pressure | Deschidere minimă legală; răspunde la all-in scurt cu cărți private mari | Nu estimează equity ori range advers. |
| Loose-aggressive | Deschideri și raise-uri minime deterministe, cu plafon configurabil pentru call | Frecvențele sunt de explorare și se compară înainte de a fi promovate. |
| Bluff mixt | Semnal public de As, check-uri și poziție târzie, cu fallback TAG | Nu este încă echilibrat după frecvențe de call sau fold equity. |
| Equity Monte Carlo | Eșantionare reproductibilă, pot odds și ponderi publice de context | Range-ul adversarilor este uniform; eșantioanele mici au eroare vizibilă și nu confirmă EV. |

## Prioritatea acțiunii loose-aggressive

Politica separă deschiderea de continuarea agresiunii în istoricul **străzii curente**:

```mermaid
flowchart LR
    TURN[Observația actorului] -->|verifică| AGG{Există BET/RAISE anterior pe strada curentă?}
    AGG -->|Nu| OPEN[Frecvența open, ajustată pentru poziție]
    AGG -->|Da| RERAISE[Frecvența de raise]
    OPEN -->|propune| LEGAL[Selectează numai o acțiune legală]
    RERAISE -->|propune| LEGAL
    LEGAL -->|verifică| CALL{Preț de call în plafon?}
    CALL -->|Da| MATCH[Call]
    CALL -->|Nu| FOLD[Fold dacă este legal]
```

<details>
<summary><strong>Legendă — deschidere și re-raise</strong></summary>

- **Open** folosește frecvența ajustată pentru poziție.
- **Re-raise** se aplică după agresiune pe strada curentă.
- **Call** rămâne condiționat de preț și opțiunile legale.

</details>

| Semnal | Comportament |
|---|---|
| Open | Prima mărire preflop fără raise anterior, sau un `BET` fără preț, folosește frecvența de deschidere. |
| Re-raise | După agresiune pe strada curentă, politica folosește `raiseFrequencyPercent`. Clasificarea pornește din nou pe strada următoare. |
| Poziție | `latePositionAdjustmentPercent` crește frecvența de open pentru buton și cutoff, o scade în rest și plafonează rezultatul la 0–100%. |
| Call | `maxCallPotPercent` compară suma de plătit cu potul înainte de call. |
| Mărime | Politica de bază deschide sau mărește cu suma minimă legală. |

Frecvențele de open și re-raise folosesc o valoare deterministă calculată din observația actorului. Ajustarea pozițională se aplică numai la open. [Politica loose-aggressive](../../holdem-ai/src/main/java/ro/atf/holdem/ai/LooseAggressivePolicy.java)

## Sizing relativ la pot

| Caz | Ce calculează `PotSizedPolicy` |
|---|---|
| `BET` | Mărimea pleacă de la fracția configurată din potul curent și respectă minimul legal. |
| `RAISE` | Calculează incrementul din potul după call și adaugă suma de call la acțiune. |
| Limită | Păstrează mărimea de bază dacă rezultatul o micșorează, depășește maximul legal sau consumă întreaga stivă. |

- **Configurații.** `betPotPercent: 0` dezactivează învelișul. Valorile 50, 100 și 150 compară sizing-ul în cohortele `sized` și `legacy-pot`. În `config/policy-tuning/` sunt și variante conservatoare, neutre pozițional și agresive.
- **Control.** Mențineți aceleași seed-uri, mărimi de masă, niveluri și locuri. Înlocuiți candidatul cu TAG sau cu varianta de control declarată. `bluff-always` servește doar proba declanșatorului.
- **Interpretare.** Raportați separat mărimea relativă la pot, câștigul prin fold și evoluția stivei. Stratificați după poziție, mărimea mesei și profilul blindurilor. Pentru estimarea EV sunt necesare date suplimentare față de fold-uri și victorii observate.
- **Trasabilitate.** Versiunea din jurnal include parametrii YAML care influențează decizia.

## Lecții pentru evaluarea unei politici

| Întrebare | Metodă |
|---|---|
| Din ce a rezultat decizia? | Păstrează observația publică, acțiunile legale, versiunea și hash-ul YAML. Auditorul verifică separat distribuția și decontarea, inclusiv eligibilitatea în fiecare pot. |
| Ce aduce o regulă nouă? | Compară candidatul cu un control care schimbă o singură pondere. Rulează ambele variante cu aceleași distribuții, buget și adversari. Numără deciziile unde ponderea schimbă efectiv pragul sau acțiunea. |
| Cât contează poziția și sizing-ul? | Rotește actorul și butonul pe aceeași distribuție. Compară separat deschiderea și re-raise-ul, precum și pariurile raportate la pot și la stiva efectivă. |
| Când merită promovată? | Separă reglajul de holdout, agregă rotațiile per seed și raportează incertitudinea pe 4, 5 și 6 locuri. Verifică direcția efectului pe adversari diferiți și separat în cash și turneu. |

Păstrează pentru fiecare lecție un jurnal reprezentativ, verificarea independentă și proveniența necesară reluării. Rezumatele loturilor și seed-urile concrete apar în directorul extern de lucru. O greșeală stabilă intră ca test de componentă cu o distribuție minimă. [Reguli și replay](TOURNAMENT_RULES.md) · [Strategia de testare](../testing/TEST_STRATEGY.md)

## Metrici

- chips/hand și big blinds/100;
- EV brut și EV după rake simulată;
- showdown equity error;
- fold/call/raise calibration;
- variance și maximum drawdown;
- decizii timeout/fallback;
- regret proxy și exploatare de către adversari;
- distribuția sizing-urilor și frecvențele pe street.

Win-rate-ul singur nu este suficient: pokerul are varianță mare și un eșantion pozitiv poate fi noroc.
