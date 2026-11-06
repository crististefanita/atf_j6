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
