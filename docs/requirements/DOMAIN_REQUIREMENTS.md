# Cerințe de domeniu Hold'em

## Modelul cărții

- Ranguri: 2–A;
- culori: club, diamond, heart, spade;
- fiecare combinație rang/culoare apare o singură dată;
- deck-ul este immutable și verificabil;
- toate operațiile de card trebuie să respingă duplicatele și cărțile inexistente.

## Starea mâinii

Starea trebuie să reprezinte explicit:

- numărul mâinii și seed-ul;
- dealer, small blind, big blind;
- street: preflop, flop, turn, river, showdown, complete;
- board-ul;
- cărțile private numai pentru actorul autorizat sau pentru motorul intern al arenei;
- stack-ul și contribuția fiecărui loc;
- fold/all-in/active status;
- pot principal și poturi secundare;
- actorul curent și acțiunile legale;
- istoricul public al acțiunilor;
- starea de settlement.

## Legalitate

Motorul trebuie să verifice:

- ordinea locurilor și a blind-urilor;
- actorul curent;
- existența unei acțiuni legale;
- minimul de raise și efectul unui all-in care nu este raise complet;
- check numai când nu există sumă de call;
- call numai până la contribuția maximă curentă;
- bet/raise numai în limitele stack-ului;
- street advancement numai după închiderea acțiunii;
- showdown numai pentru jucătorii eligibili;
- împărțirea potului fără pierdere sau creare de chips.

## Evaluator

Evaluatorul trebuie să fie independent de AI și să testeze:

- high card până la straight flush;
- tie exact;
- board-only hand;
- wheel straight A-2-3-4-5;
- duplicate impossible;
- cele mai bune 5 cărți din 7, nu primele 5;
- side-pot eligibility.

## Observația actorului

Politica primește doar informația pe care un jucător real o poate vedea:

- propria mână;
- board;
- pot și contribuții publice;
- stack-uri publice;
- poziție și street;
- acțiunile publice din mâna curentă;
- acțiunile legale.

Mâinile private ale adversarilor, deck-ul complet și seed-ul dealerului sunt interzise în API-ul deciziei.

## Sesiune locală și reluare

- După fiecare acțiune acceptată, aplicația persistă checkpoint-ul privat cu seed, profil de masă, mâinile turneului, timpul de start, actorul fizic și mutarea fiecărei acțiuni.
- La redeschidere, fiecare acțiune se replay-uiește prin motor; un actor neașteptat, o mutare ilegală sau date trunchiate nu devin stare de joc.
- Înlocuirea checkpoint-ului păstrează o copie anterioară recuperabilă peste întreruperea procesului. Eroarea de restaurare este vizibilă, iar octeții invalizi rămân disponibili pentru diagnostic.
- Reluarea nu pornește automat preluarea AI. Un checkpoint terminal reia ecranul rezultatului; o mână terminată, dar încă în recapitulare, își reia animația de colectare.
- Suspendarea aplicației oprește ceasul turneului, invalidează acțiunile UI programate și salvează starea confirmată. Revenirea reia ceasul din durata checkpoint-ului; timpul petrecut în fundal nu avansează blindurile.
- Sugestia AI calculată în worker este read-only. Aplicarea ei se face pe firul UI numai dacă Activity este activă și tokenul de tură corespunde; un rezultat întârziat după suspendare nu poate avansa jocul.
- Testele de componentă întrerup replay-ul între acțiuni automate și verifică atât reluarea actorului corect, cât și respingerea unui jurnal malformat sau reordonat.

[Profilul de turneu și verificarea offline](TOURNAMENT_RULES.md) descriu nivelurile, eliminările și convenția sumelor folosite de motor.
