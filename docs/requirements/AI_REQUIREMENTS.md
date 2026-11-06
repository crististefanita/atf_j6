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

### L2 — equity/range

Calculează equity împotriva unui range ponderat, actualizează range-ul din acțiuni și separă made hand, draw, bluff-catcher și air.

### L3 — search

Folosește sampling al lumilor ascunse și rollout-uri cu adversari expliciți, dar nu confundă lumi fezabile cu distribuții calibrate.

### L4 — CFR / self-play

Folosește regret minimization, abstractizare de acțiuni și board-uri, evaluare de exploitability proxy și populații diverse de adversari.

## Acceptanță

O politică nouă se acceptă numai dacă:

- bate baseline-ul pe un corpus ținut separat;
- nu regresează legalitatea sau timeout-ul;
- păstrează aceeași decizie pe replay determinist;
- are rezultate raportate cu număr de mâini, seed, confidence interval și drawdown;
- este testată împotriva mai multor stiluri, nu doar împotriva propriei versiuni;
- nu primește informație ascunsă accidental.

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
