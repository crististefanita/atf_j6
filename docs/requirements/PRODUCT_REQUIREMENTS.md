# Cerințe de produs

## Scop

J6 trebuie să permită dezvoltarea și evaluarea unui AI de No-Limit Texas Hold'em într-un mediu local, controlabil și reproductibil, cu o extensie online numai către arene care acceptă explicit boți.

## Utilizatori

- dezvoltatorul: construiește reguli, politici și adaptoare;
- cercetătorul: compară politici și analizează replay-uri;
- operatorul: pornește/oprește un bot și inspectează starea;
- jucătorul: în faze ulterioare, folosește un coach offline, nu RTA pe site-uri restricționate.

## P0 — obligatoriu

- rulare locală JVM independentă de SDK-ul Android;
- pachet standard de 52 de cărți, 2–6 locuri;
- NLHE cash-style și tournament-style ca profile distincte;
- stări immutable sau protejate la mutație accidentală;
- acțiuni: fold, check, call, bet, raise, all-in;
- blinds, dealer, street, pot, stack și contribuții pe jucător;
- validare independentă a fiecărei acțiuni;
- evaluator de showdown;
- seed și replay JSON versionat;
- politici AI care consumă numai observația actorului;
- baseline random, baseline tight-aggressive și baseline pot-odds;
- arena locală cu bot-vs-bot;
- log fără chei sau date sensibile;
- oprire controlată și limită de timp per decizie.

## P1 — necesar pentru online

- adaptor WebSocket separat de motor;
- handshake și autentificare din variabilă de mediu;
- reducer de evenimente și snapshot autoritar;
- deduplicare prin `client_action_id`;
- `turn_token`, `hand_id` și validare strictă;
- reconectare cu backoff bounded;
- resync după întrerupere;
- rate-limit și timeout vizibile;
- mod `dry-run` care nu trimite acțiuni;
- jurnal de decizie cu hash de observație, nu cu secretul;
- flag explicit pentru `join_lobby` și `auto_rebuy`.

## Interfață locală Android

Primul increment reutilizează meniul, selectorul de limbă, editorul numelui, geometria mesei, chenarele și mâna centrată din J4/J5. J6 furnizează potul, jetoanele, contribuțiile, dealerul, actorul și cele cinci poziții pentru cărțile comunitare. Limba implicită este engleza. Româna se poate selecta din meniu.

Mâna locală heads-up sau la 4–6 locuri folosește o observație fixată pe jucătorul uman și comenzi validate de motor. Adversarii de antrenament dau check sau call, iar decontarea arată distribuția jetoanelor. Cărțile adversarilor rămân ascunse și la rezultat. [Contractul interfeței](../../holdem-android/README.md) · [Criteriile mâinii interactive](../feedback/J6-UI-01-INTERACTIVE-TABLE.md)

Turneul local pornește la 4–6 locuri. La fiecare plată, stivele decid cine continuă și ce locuri sunt eliminate. Locul uman rămâne fix când se schimbă dealerul sau masa ajunge heads-up. Jucătorul vede decontarea ultimei mâini și poate deschide rezultatul parcursului său, fie victorie, fie eliminare. [Criteriile turneului](../feedback/J6-UI-03-TOURNAMENT-SANITY.md)

## P2 — îmbunătățire AI

- range-uri ponderate;
- equity exactă unde este ieftină și Monte Carlo unde este necesar;
- model Bayes/Bayesian-like pentru actualizarea range-ului după acțiuni;
- opponent population model;
- MCCFR/CFR+ pentru heads-up și abstractizare pentru 6-max;
- endgame solving limitat;
- analiză de sensibilitate la sizing și rake;
- dashboard local pentru win-rate, EV, variance, drawdown și regret proxy.

## Non-obiective

- automatizarea unui client comercial fără aprobare;
- screen scraping sau click simulation;
- ascunderea unui bot prin timing artificial;
- folosirea mâinilor adverse reale în politica actorului;
- promisiunea de profit sau recomandări de miză personală.
