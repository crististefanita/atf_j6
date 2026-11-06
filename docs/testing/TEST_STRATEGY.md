# Strategie de testare

## Niveluri

### Contracte pure

Teste pentru Card, deck, rank, street, legal actions, pot arithmetic și evaluator.

### Property tests

- numărul de cărți rămâne constant;
- nicio carte nu apare de două ori;
- chips conservate după fiecare tranziție;
- acțiunea ilegală nu mută starea;
- replay-ul produce același digest;
- aceeași observație + aceeași versiune produce aceeași decizie în modul determinist.

### Exemple exhaustive

Enumerăm cazurile mici pentru straight, flush, ties, all-in incomplet, side pots și heads-up.

### Arena

Comparăm random, calling station, tight-aggressive, loose-aggressive și politici adversar-aware pe seed-uri separate.

### Online contract

Folosim server mock local pentru connected, stale token, duplicate action, disconnect, resync, rejected action, table close și season end. Nu se folosesc conexiuni reale în testele automate.

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
