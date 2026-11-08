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

Primul increment reutilizează meniul, selectorul de limbă, editorul numelui, geometria mesei, chenarele și mâna centrată din J4/J5. Limba implicită este engleza; româna se poate selecta din meniu.

Masa locală arată cărțile proprii, cărțile comunitare, potul, jetoanele, contribuțiile și rolurile. În timpul mâinii, cărțile adverse rămân cu fața în jos; după decontare sunt dezvăluite pentru recapitulare, inclusiv cele ale jucătorilor care au dat fold. Un marcaj textual Fold persistă pe locul retras până la următoarea mână. Eticheta centrală identifică explicit actorul; indicatorul combinației folosește doar cărțile proprii și board-ul public. Coach-ul AI oferă o acțiune legală, explică estimările disponibile și nu aplică mutarea. Butonul de preluare joacă mâna locală curentă prin aceeași validare a motorului. Politica nu primește niciodată cărțile adverse; proiecția de recapitulare le expune interfeței numai după decontarea terminală a mâinii. [Contractul interfeței](../../holdem-android/README.md) · [Criteriile mâinii interactive](../feedback/J6-UI-01-INTERACTIVE-TABLE.md) · [Coach și recapitulare](../feedback/J6-UI-05-LOCAL-COACH-AND-POSTGAME-REVIEW.md)

Turneul local pornește la 4–6 locuri. La creare, jucătorul alege blindurile să crească la fiecare 1, 2, 3 sau 5 minute; implicit este 1 minut. Nivelul se aplică de la următoarea mână după expirarea intervalului. Eliminarea unui loc continuă să avanseze nivelul conform regulilor turneului. La fiecare plată, stivele decid cine continuă și ce locuri sunt eliminate. Locul uman rămâne fix când se schimbă dealerul sau masa ajunge heads-up. Ecranul terminal păstrează dezvăluirea ultimei mâini și arată rezultatul turneului. [Criteriile turneului](../feedback/J6-UI-03-TOURNAMENT-SANITY.md)

### Claritatea rezultatului mâinii

- La dezvăluirea terminală, toate cărțile proprii afișate au aceeași dimensiune ca mâna utilizatorului în același viewport; layout-ul se adaptează fără micșorarea selectivă a cărților adverse.
- Când o mână s-a încheiat și este disponibil `New hand`, păstrați cărțile proprii, board-ul, cărțile dezvăluite și evidențierea combinației pe masă până la apăsarea explicită. Nu le ascundeți după un timer și nu avansați automat; resume-ul trebuie să restaureze aceeași recapitulare.
- Pentru showdown, recapitularea identifică cele mai bune cinci cărți ale fiecărui jucător eligibil și evidențiază discret combinația aleasă. O victorie prin fold este etichetată drept uncontested și nu primește o combinație de cărți inventată.
- Semnalul vizual al premiului ajunge la fiecare loc beneficiar conform decontării motorului, inclusiv split și poturi secundare. Mișcarea premiului este separată de colectarea cărților.
- Captionul combinației este scurt și discret; `Hand complete` nu se repetă în centrul unei mese deja închise. Cărțile terminale rămân vizibile până la acțiunea explicită `New hand` sau rezultatul de turneu.
- [Criteriile de showdown și animare](../feedback/J6-UI-07-SHOWDOWN-WIN-CLARITY.md)

### Clasamentul turneelor

- Aplicația oferă un clasament local, cumulativ, al punctelor obținute în turnee. Totalurile se grupează după un ID stabil al participantului, nu după aliasul afișat la masă.
- Fiecare rezultat terminal intră o singură dată în registrul local, după ID-ul stabil al participantului. Topul arată punctele cumulate, turneele jucate și cea mai bună clasare; egalitățile de puncte împart același rang. Tabela este derulabilă și nu pretinde sincronizare online.
- Regula inițială propusă normalizează plasările la o scară de 0–100 și împarte egal locurile ocupate la eliminări simultane. Formula exactă și criteriile complete rămân în [feedback-ul clasamentului](../feedback/J6-UI-08-TOURNAMENT-LEADERBOARD.md) până la calificare.

### Istoricul pariurilor

- În timpul și după o mână, utilizatorul vede sumarul ultimei acțiuni și poate inspecta jurnalul ordonat: cine a făcut `bet`, `raise`, `call`, `check`, `fold` sau `all-in`, suma adăugată și contribuția cumulată după mutare. [Criteriile jurnalului](../feedback/J6-UI-10-BETTING-ACTION-LEDGER.md)

### Ierarhia informației de la loc

- Stiva și sumele decontării folosesc etichete secundare discrete, cu valori lizibile și descrieri accesibile complete. Premiile și restituirile rămân semantic distincte.
- [Criteriile etichetelor de loc](../feedback/J6-UI-09-SEAT-METRIC-HIERARCHY.md)

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
