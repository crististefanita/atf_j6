# J6-AI-02 — Nu promova stilurile heuristice drept Hard

**Prioritate:** P1  
**Stare:** Deschis

## Constatare

TAG folosește reguli rare și grosiere (As sau pereche preflop și un plafon simplu de call postflop). Loose-aggressive aplică frecvențe deterministe; pressure nu modelează range-uri, fold equity sau valoarea showdown-ului; bluff-ul verifică doar câteva semnale publice. A fost adăugat un candidat Monte Carlo reproductibil pentru equity împotriva mâinilor necunoscute uniforme, cu prețul potului și factori de context publici. Acesta nu actualizează range-uri din acțiuni, nu modelează complet valoarea ICM și nu este încă un jucător Hard.

## Risc

Un procent de victorii pe puține turnee poate proveni din distribuție și varianță. Un buton mai agresiv poate crește uneori stiva și totodată să piardă mai mult contra call-urilor. Compararea doar cu TAG sau doar într-o poziție poate promova o regulă exploatabilă.

## Criterii de închidere

- Pentru fiecare candidat se compară aceleași seed-uri, niveluri, număr de locuri și poziții împotriva mai multor stiluri fixe.
- Se raportează chips/hand și big blinds/100, distribuția pe poziții, rata de showdown, drawdown și intervale de încredere; rata de victorii rămâne indicator secundar.
- Se păstrează separat corpusul de reglaj și cel de evaluare; pragurile YAML se aleg pe primul și se verifică pe seed-uri nefolosite.
- Politicile văd doar observația actorului; auditorul poate primi cărțile ascunse numai după terminarea mâinii.
- Un stil nu este numit „Hard” până când depășește baseline-urile pe evaluare nouă, fără regresii de legalitate, replay sau buget de calcul.

## Progres și rezultat curent

S-au corectat și acoperit următoarele comportamente:

- Prima mărire preflop fără raise anterior folosește frecvența de open; un raise după agresiune pe aceeași stradă folosește frecvența de re-raise.
- Agresiunea de pe strada anterioară nu schimbă clasificarea primei deschideri pe strada nouă.
- Frecvența de open se ajustează cu `latePositionAdjustmentPercent` în cutoff/buton și în sens invers în pozițiile timpurii, cu limite 0–100.
- Versiunea din jurnal include parametrii de frecvență, call și poziție, astfel încât configurațiile YAML diferite să nu fie confundate la comparație.
- Jurnalul deciziei include numai poziția, strada, potul, suma de call și stiva actorului observate înainte de acțiune; nu include cărțile private ale adversarilor sau board viitor.
- Sizing-ul potului este comparat separat de frecvența de open. Dacă sizing-ul ar depăși plafonul legal ori ar consuma toată stiva, politica revine la acțiunea legală de bază.

Testele de componentă verifică primul raise preflop, re-raise-ul, resetarea la stradă nouă, ajustarea pozițională și limitarea sizing-ului. Un test statistic al bluff-ului verifică abaterea de cel mult o decizie la 100 de observații, nu pretinde că un hash trebuie să distribuie exact 18 acțiuni în orice eșantion de 100.

## Calibrare offline

Replay-ul arhivei `C:\work\holdem_back\20261107-tournaments\trace.txt` cu politica legacy a reprodus identic 100/100 turnee pentru fiecare mărime de masă: 2.689 mâini la 4 locuri, 2.538 la 5 și 2.371 la 6; câștigătorii, seed-urile mâinilor și toate deciziile coincid cu jurnalul arhivat. Aceasta validează replay-ul, nu progresul strategiei.

Lotul istoric seed 0–99 a comparat `legacy-loose` și sizing 50/100/150 cu același control legacy în profilurile standard și stress. Sizing-ul de 50% a avut rezultate slabe în profilul standard. Sizing-urile 100% și 150% au fost mai promițătoare, dar niciunul nu a dominat fiecare mărime de masă.

Holdout-ul pe seed-urile 9300–9309 a rotit butonul inițial prin offseturile 0–5, păstrând aceleași deal-uri între controale. Sumarul a comparat direct 100% cu 150% pe poziții distincte:

| Profil / locuri | Diferență medie a traiectoriei de stivă, 100% minus 150% | Fold-win după agresiune / mână agresivă, 100% vs. 150% | Fold-uri ale adversarilor după agresiune / mână agresivă, 100% vs. 150% |
|---|---:|---:|---:|
| Standard / 4 | +136,7 | 0,49 vs. 0,45 | 0,90 vs. 1,03 |
| Standard / 5 | +203,3 | 0,42 vs. 0,31 | 0,94 vs. 0,98 |
| Standard / 6 | −70,1 | 0,43 vs. 0,40 | 1,10 vs. 1,16 |
| Stress / 4 | +6,8 | 0,34 vs. 0,32 | 0,86 vs. 0,99 |
| Stress / 5 | +57,8 | 0,28 vs. 0,26 | 0,90 vs. 0,93 |
| Stress / 6 | +1,4 | 0,30 vs. 0,27 | 1,10 vs. 1,12 |

În acest holdout, 150% a fost urmat de mai multe fold-uri ale adversarilor în toate cele șase perechi, dar rata fold-win-urilor a fost mai mică decât la 100%. Traiectoria favorizează 100% în cinci dintre cele șase perechi și favorizează 150% la standard/6 locuri. Acesta este un semnal pentru sizing-ul de 100% ca variantă echilibrată de laborator; nu este suficient pentru promovarea la Hard.

Ajustarea pozițională de 10 puncte a fost comparată direct cu zero pe 50 seed-uri noi, seed 9500–9549, pentru fiecare profil și mărime de masă. Efectul a fost mixt: ușor pozitiv la stress și la standard/4 și /6, negativ la standard/5. Păstrați ambele setări ca variante de evaluare; nu considerați încă 10 puncte o regulă universală.

Jurnalele complete, auditul butonului, sizing-ul pe poziții și sumarul ablației se află în:

- `C:\work\holdem_back\current-code-replay-20261107-1757`
- `C:\work\holdem_back\current-ai-calibration-20261107-1758`
- `C:\work\holdem_back\current-ai-positions-holdout-9300-20261107`
- `C:\work\holdem_back\position-adjustment-ablation-9500-20261107`

Comparațiile de fold sunt temporale, nu cauzale; un fold poate urma unei acțiuni fără să fi fost provocat de mărimea ei. Traiectoria stivei și victoriile sunt zgomotoase și nu substituie EV, chips/hand, intervale de încredere sau drawdown. Câmpul adversarilor este încă restrâns la TAG și calling station. Candidatul Monte Carlo calculează equity împotriva distribuției uniforme de mâini necunoscute, dar nu estimează range-ul condiționat al fiecărui adversar din acțiunile sale.

Aceste rezultate califică numai `TournamentArena`. Jocul local Android din `holdem-android/app/src/main/java/ro/atf/holdem/android/LocalHoldemGame.java` răspunde momentan cu check/call și nu folosește politicile din arena. Nu atribuiți progresul de aici comportamentului din aplicație; integrarea AI în jocul local este o sarcină distinctă și nu a fost modificată în această etapă offline.

## Următorul prag de închidere

1. Introduceți model de range adversar pe fiecare jucător, actualizat din acțiuni publice și poziție; validați probabilitățile pe holdout cu reliability/calibration error, nu doar cu rezultate de turneu.
2. Separați equity-ul brut de equity-ul efectiv al poturilor secundare și costul call-ului/all-in-ului. Adăugați situații multiway cu stive inegale și all-in-uri înainte de a folosi estimatorul pentru o decizie Hard.
3. Extindeți cohortele cu mai multe seed-uri independente, control de poziții și răspunsuri adaptive la sizing; țineți separat reglajul de holdout.
4. Raportați EV chips/hand, bb/100, showdown calibration, fold/call/raise calibration și drawdown cu intervale de încredere. Traiectoria stivei și rata de victorie rămân descriptive.

Până când aceste praguri sunt îndeplinite, politicile rămân candidați de laborator; nu se prezintă drept Hard și nu se declară câștigătoare pe baza ratelor de fold sau a unui eșantion mic.

## Starea implementării curente

- Componenta de equity estimează reproductibil cota din pot, win/tie și eroarea standard, eșantionând mâini necunoscute uniforme. Observația politicii conține doar mâna actorului, board-ul și istoricul public.
- Politica ajustează pragurile de call și value bet după prețul accesibil, numărul adversarilor activi, agresiunea adversă de pe strada curentă, poziția de acțiune, incertitudinea eșantionului și modul cash/turneu. Ponderile sunt configurabile în YAML și intră în versiunea jurnalizată.
- Cash arena păstrează stack-urile între mâini și folosește blind-uri fixe; Tournament arena păstrează eliminările și nivelurile de blind. Acestea sunt scenarii diferite și folosesc marje diferite, nu sunt amestecate în același scor.
- Rulările exploratorii sunt păstrate în `C:\work\holdem_back\contextual-equity-20261107-2005`. Auditul confirmă distribuții/deal-uri pereche și legalitatea fiecărei sesiuni; rezultatele nu promovează algoritmul. Cash-ul are 16 perechi pentru fiecare mărime de masă, iar turneele au 8 perechi pentru fiecare mărime și profil standard/stress. Aceste volume sunt mici pentru inferență de forță.
- Rezultatul agregat este mixt: diferențele de stack share cash nu sunt consecvent pozitive pentru 5 și 6 locuri, iar turneele alternează în funcție de profil și numărul de locuri. Eroarea statistică și lipsa modelului de range împiedică o concluzie de EV. Raportul extern păstrează tabelele și limitele metodologice.
