# Cerințe de produs

## Scop

J6 trebuie să permită dezvoltarea și evaluarea unui AI de No-Limit Texas Hold'em într-un mediu local, controlabil și reproductibil, cu o extensie online numai către arene care acceptă explicit boți.

## Utilizatori

- dezvoltatorul: construiește reguli, politici și adaptoare;
- cercetătorul: compară politici și analizează replay-uri;
- operatorul: pornește/oprește un bot și inspectează starea;
- jucătorul folosește coach-ul la masa locală de antrenament sau în turneu.

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

| Funcție | Contract local |
|---|---|
| Masa | Arată cărțile proprii și comunitare, potul, jetoanele, contribuțiile și rolurile. Eticheta centrală identifică actorul, iar marcajul Fold rămâne până la mâna următoare. |
| Combinația curentă | Se calculează din cărțile proprii și board-ul public. |
| Coach și preluare | Coach-ul recomandă o acțiune legală și explică estimările disponibile. Comanda de preluare joacă mâna curentă prin validatorul motorului. |
| Recapitulare | Dezvăluie cărțile adverse, inclusiv ale locurilor foldate, după decontarea terminală. Politica primește doar observația actorului. |

[Contractul interfeței](../../holdem-android/README.md) · [Coach și recapitulare](../feedback/J6-UI-05-LOCAL-COACH-AND-POSTGAME-REVIEW.md)

Turneul local pornește cu 4–6 jucători. Intervalul blindurilor se alege la creare dintre 1, 2, 3 și 5 minute, cu 1 minut implicit.

- Selectorul „Nivelul adversarilor” oferă Normal și Hard, cu Normal implicit. În Normal, adversarii dau check sau call. În Hard, adversarii au stiluri distincte, iar la șase locuri unul folosește stilul Normal. Dificultatea și alocarea stilurilor se păstrează la reluare. [Politica Hard](AI_REQUIREMENTS.md#profilul-hard-al-turneului-local)
- Expirarea intervalului și eliminările se reflectă în nivelul mâinii următoare.
- La decontare, stivele decid cine continuă. Locul uman rămâne fix la schimbarea dealerului și în heads-up.
- Ecranul terminal păstrează cărțile ultimei mâini și arată rezultatul turneului.

[Turneu și reluare](../../holdem-android/README.md#turneu-și-reluare)

### Claritatea rezultatului mâinii

- La dezvăluirea terminală, toate cărțile proprii afișate au aceeași dimensiune ca mâna utilizatorului în același viewport; layout-ul se adaptează fără micșorarea selectivă a cărților adverse.
- La cinci și șase locuri, perechea fiecărui jucător este centrată sub propriul chenar. Perechile de pe același rând pornesc de la aceeași bază. Marcajul unei cărți decisive o poate ridica ușor.
- Când o mână s-a încheiat și este disponibil `New hand`, păstrați cărțile proprii, board-ul, cărțile dezvăluite și evidențierea combinației pe masă până la apăsarea explicită. Nu le ascundeți după un timer și nu avansați automat; resume-ul trebuie să restaureze aceeași recapitulare.
- Pentru showdown, recapitularea identifică cele mai bune cinci cărți ale fiecărui jucător eligibil și evidențiază discret combinația aleasă. O victorie prin fold este etichetată drept uncontested și nu primește o combinație de cărți inventată.
- Semnalul vizual al premiului ajunge la fiecare loc beneficiar conform decontării motorului, inclusiv split și poturi secundare. Mișcarea premiului este separată de colectarea cărților.
- Chenarul afișează separat premiul din pot și restituirea pariului neacoperit, inclusiv când același loc primește ambele sume. Sumarul potului include numai premiile. Un loc care primește numai restituire nu primește marker de premiu. Valorile se păstrează după restaurarea mâinii.
- Captionul combinației este scurt și discret; `Hand complete` nu se repetă în centrul unei mese deja închise. Cărțile terminale rămân vizibile până la acțiunea explicită `New hand` sau rezultatul de turneu.
- [Criteriile de showdown și animare](../feedback/J6-UI-07-SHOWDOWN-WIN-CLARITY.md)

### Continuarea în timpul decontării

O comandă de continuare acceptată în timpul animației salvează destinația în checkpoint. După întrerupere, reluarea deschide mâna sau rezultatul salvat. Dacă scrierea eșuează, utilizatorul rămâne la masa terminală și poate reîncerca. [Contractul reluării](../../holdem-android/README.md#turneu-și-reluare)

### Clasamentul turneelor

- Aplicația oferă un clasament local, cumulativ, al punctelor obținute în turnee. Totalurile se grupează după un ID stabil al participantului, nu după aliasul afișat la masă.
- Fiecare rezultat terminal intră o singură dată în registrul local, după ID-ul stabil al participantului. Topul arată punctele cumulate, turneele jucate și cea mai bună clasare; egalitățile de puncte împart același rang. Tabela este derulabilă și nu pretinde sincronizare online.
- Redenumirea profilului actualizează eticheta intrării identificate prin același ID. Rangul, punctele, turneele și intrările celorlalți participanți se păstrează, inclusiv după restartul procesului.
- Punctele se calculează cu `rotunjire(100 × (N − rang_mediu) / (N − 1))`, unde `N` este numărul participanților. La o eliminare simultană, rangul mediu este media locurilor ocupate. O schimbare a curbei cere versiune nouă și migrarea explicită a totalurilor. [Verificările clasamentului](../testing/TEST_STRATEGY.md#interfața-android-locală)

### Istoricul pariurilor

- În timpul și după o mână, utilizatorul vede sumarul ultimei acțiuni și poate inspecta jurnalul ordonat: cine a făcut `bet`, `raise`, `call`, `check`, `fold` sau `all-in`, suma adăugată și contribuția cumulată după mutare. [Criteriile jurnalului](../feedback/J6-UI-10-BETTING-ACTION-LEDGER.md)

### Ierarhia informației de la loc

- Stiva și sumele decontării folosesc etichete secundare discrete, cu valori lizibile și descrieri accesibile complete. Premiile și restituirile rămân semantic distincte.
- Când lățimea disponibilă nu ajunge, eticheta trece deasupra sumei. Locul actorului expune o descriere completă și poate primi focus de accesibilitate.
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

## Realizări

Meniul **Realizări** folosește componentele comune cu Rentz pentru listă, progres, detalii și notificări. Ordinea provine din catalog. O mână sau un turneu poate avansa mai multe obiective simultan.

Căutarea recunoaște titlurile și regulile în română și engleză, inclusiv fără diacritice. Filtrele după nivel și stare se aplică împreună și se păstrează la recrearea activității.

| Nivel | Primul obiectiv | Alte criterii |
|---|---|---|
| Ușor | Câștigă o mână de poker. | Ajungi la prima dezvăluire, câștigi un pot din blindul mare, trei mâini distincte și închei participarea într-un turneu. |
| Mediu | Câștigă un turneu. | Pot împărțit, pot secundar disputat, prima victorie la dezvăluire cu minimum trei participanți, două victorii cu chintă sau mai bine și două podiumuri. |
| Greu | Câștigă trei turnee distincte. | Două victorii cu full sau mai bine și două titluri la mese pornite cu șase jucători. |

- Turneele Normal și Greu acordă progres pe profil. Mâinile de exercițiu din **Antrenament** au propriul parcurs.
- Câștigurile folosesc jetoanele acordate din pot și o singură cheie per mână. Motorul contabilizează separat jetoanele restituite.
- Un pot secundar disputat are minimum doi jucători eligibili la decontare. Un pot împărțit are mai mulți beneficiari pe același strat. La showdown se numără jucătorii cu cărți evaluate.
- Participarea și podiumul se acordă când locul devine definitiv, chiar dacă adversarii continuă. Un loc al treilea împărțit contează. Titlul cere locul întâi, iar obiectivul de șase folosește dimensiunea de la începutul turneului.
- Progresul este local pe profil și se deduplică după ID-ul mâinii sau al turneului. Se poate reconcilia sesiunea salvată. Clasamentul agregat nu reconstruiește partidele vechi.

- Catalogul YAML definește ID-urile, nivelul, ordinea, metrica, pragul și regulile RO/EN. Gradle generează lista Android și rezervă ID-urile retrase.
- Lista, detaliile și panoul de progres folosesc interfața comună. Panoul afișează actualizările după confirmarea salvării, iar repetarea rezultatului nu produce o nouă notificare.
- Progresul acumulează dovezi până la cel mai mare prag declarat pentru metrică. Fiecare obiectiv afișează cel mult propriul prag.
- ID-urile și dovezile salvate se păstrează la reducerea pragului sau retragerea unei realizări. Lista folosește aceeași cheie pentru rând și detaliu când se schimbă ordinea, traducerea sau pragul.

[Regulile executabile](../../config/j6-achievements.yaml) · [Ghid de proiectare](../design/achievements/README.md) · [Verificarea realizărilor](../testing/TEST_STRATEGY.md#realizări-de-joc)

### Extinderea parcursului

Păstrează filele Ușoare, Medii și Grele, cu mai multe trepte în fiecare.

- Grupează treptele după competența de poker. Într-o treaptă, obiectivele avansează în paralel.
- Ordinea duce de la citirea mesei la situații în care poziția, stiva și acțiunile precedente schimbă decizia.

| Familie | Parcurs posibil | Fapt necesar |
|---|---|---|
| Poziție și blinduri | Prima victorie din blind, apoi victorii din poziții distincte la mese de dimensiuni diferite. | Loc fizic și rol în mâna decontată. |
| Showdown și decontare | Prima dezvăluire, o victorie cu o chintă, apoi un pot secundar câștigat distinct de potul principal. | Combinația validată, beneficiarii și eligibilitatea pe fiecare strat al potului. |
| Joc la stivă scurtă | Terminarea unei mâini la stivă mică, câștigarea potului după un all-in acceptat, apoi revenire până la podium. | Stiva efectivă în blinduri mari înainte de decizie, acțiunea acceptată și locul definitiv. |
| Acțiuni după flop | Câștig după pariu și renunțarea adversarilor, apoi astfel de rezultate în poziții și runde distincte. | Actor, rundă, acțiuni acceptate și decontare fără showdown. |
| Turneu | Primul titlu, titluri repetate la dimensiuni diferite, apoi titluri în configurații Hard eligibile. | ID de turneu, loc definitiv, numărul inițial de locuri și dificultatea adversarilor. |

- Poturile și clasările folosesc verdictul motorului. Un pot câștigat după renunțarea adversarilor confirmă rezultatul pariului.
- Criteriile tactice folosesc faptele disponibile la momentul acțiunii. Clasificarea unui bluff cere criterii explicite despre mâna și contextul observabile atunci.

- Un criteriu nou declară modul eligibil, proveniența deciziei, metrica și cheia evenimentului distinct.
- Mâinile de antrenament și replay-urile servesc calibrării. Pentru obiective de execuție personală, evenimentul dovedește decizia manuală.
- Proveniența Auto-play și recuperarea din checkpoint au cazuri de test separate.

- Păstrează lista reciclată și filtrele comune când adaugi grupe noi. În detalii, afișează regula YAML și condițiile de eligibilitate.
- ID-ul din catalog păstrează selecția între traduceri. Afișajul RO/EN arată progresul rămas și situația în care obiectivul poate avansa.

[Criteriile de extindere](../feedback/J6-PROD-01-ACCOUNT-ACHIEVEMENTS.md)
