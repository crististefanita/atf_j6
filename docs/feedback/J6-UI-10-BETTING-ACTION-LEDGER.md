# J6-UI-10 · Jurnal lizibil al pariurilor

**Prioritate:** P1  
**Stare:** Implementat și verificat pe API 28 pentru `Call`/`Check`; `Bet`/`Raise` au probe de componentă, iar traseul UI dedicat și matricea de viewport rămân de extins  
**Domeniu:** istoric public al acțiunilor într-o mână locală

## Problema și progresul în cod

Înaintea schimbării, chenarele arătau stiva și contribuția totală, iar motorul păstra acțiunile doar ca șiruri de istoric. Nu se putea urmări cine a făcut `bet`, `raise`, `call`, `check`, `fold` sau `all-in` la fiecare pas.

Motorul expune acum un jurnal public structurat și validat față de contribuțiile fiecărui loc. Masa îl păstrează după decontare și accentuează separat ultima acțiune lângă board, ca să fie ușor de observat după fiecare actualizare. Rămân de verificat lizibilitatea pe viewport-uri compacte și cu TalkBack.

Rularea nativă API 28 verifică `Call` și acțiunile ulterioare `Check`: banda evidențiază actorul, tipul mutării, diferența (`+20` sau `+0`) și totalul mâinii, iar jurnalul păstrează ordinea. Probele de componentă acoperă și tipurile `Bet`/`Raise`, dar acestea nu au încă un traseu UI dedicat în sanity.

## Contractul cerut

- Fiecare mutare acceptată este o înregistrare structurată și imuabilă cu numărul ordinii, strada (`Preflop`, `Flop`, `Turn`, `River`), locul fizic, tipul acțiunii, jetoanele adăugate prin acea acțiune și contribuția cumulată a acelui loc după mutare.
- `Small blind` și `Big blind` apar ca postări inițiale. `Check` și `Fold` arată `0` adăugat; `Call`, `Bet`, `Raise` și `All-in` arată exact schimbarea stivei produsă de acțiunea validată, nu suma-țintă interpretată ca diferență.
- Ordinea este aceeași cu ordinea acceptării de către motor. Acțiunile respinse, rezultatele AI expirate și tap-urile duplicate nu intră în jurnal. Schimbarea străzii nu șterge rândurile mâinii curente.
- Masa arată un jurnal discret în timpul mâinii și după decontare. Lista completă este derulabilă în interior; cea mai nouă acțiune este adusă la vedere după fiecare mutare.
- Fiecare rând arată numărul mutării, jucătorul mapat la locul fizic, strada, acțiunea, suma adăugată și totalul cumulativ după acea acțiune. Nu folosiți numai culoare, simbol sau numărul total al potului.
- Ultima mutare apare și într-un sumar fix lângă masă, cu accent vizual și anunțarea accesibilă a actualizării; lista derulabilă rămâne registrul complet.
- Jurnalul păstrează ordinea motorului, distinge Fold/Out, este localizat și accesibil. În recapitularea terminală poate fi consultat până la `New hand`.
- Starea poate fi derivată sau salvată în checkpoint astfel încât replay-ul determinist reconstruiește exact același jurnal. Jurnalul nu expune pachetul, seed-ul ori cărți ascunse în timpul mâinii.

## Teste de componentă necesare

- Pentru fiecare tip de acțiune, suma din jurnal este egală cu scăderea stivei produsă de motor. `Check`/`Fold` adaugă zero; blindurile sunt rânduri distincte.
- După fiecare acțiune, `cumulativeContribution` este egală cu contribuția reală a acelui loc și este monotonă pe parcursul mâinii. Suma contribuțiilor reconciliază potul înainte de settlement.
- Secvențele `bet → raise → call`, raise scurt, all-in, fold și acțiuni pe toate cele patru străzi verifică actorul, ordinea și textul de prezentare. Acțiunile ilegale nu schimbă jurnalul.
- Replay-ul unei mâini și restaurarea checkpoint-ului produc jurnal byte-for-byte identic. Un rezultat asincron învechit sau o comandă repetată nu adaugă al doilea rând.
- Numărul ordinii este consecutiv și stabil, iar fiecare rând este imuabil; contribuția per jucător nu scade între acțiuni.
- Sumarul ultimei mutări selectează exact ultimul eveniment ordonat și arată jucătorul, tipul, suma adăugată, strada și totalul cumulativ; sunt verificate toate tipurile, inclusiv `fold`, `check` și `all-in`.
- Proiecția leagă fiecare ID fizic de numele vizual corect la 2/4/5/6 locuri. Layout-ul derulabil trece română/engleză, nume lungi, font mărit și TalkBack fără trunchiere ori intersectare.
- În timpul jocului, rândurile nu conțin cărți private adverse sau informații de rezultat viitor. După settlement, jurnalul rămâne disponibil alături de cărțile dezvăluite până la acțiunea explicită.

## E2E minim după trecerea componentelor

Traseul UI calificat execută `call` și verifică iterațiile `check` produse pe străzile următoare; aserțiunile accesibile confirmă jucătorul, acțiunea, diferența de jetoane și totalul mâinii. Capturile surprind sumarul ultimei mutări și istoricul derulabil. `Bet`/`Raise` și acțiunile fără contribuție sunt acoperite de componente; următoarea extindere UI trebuie să inițieze un `Bet` sau `Raise` real și să confirme `Call`-ul următor.

## Rezultatul verificării native

- Android API 28, 392 × 626 dp, font scale 1.0: **58/58 aserțiuni PASS**, **8 capturi native**.
- Traseul a confirmat `Call +20`, `Check +0`, actorul afișat în banda ultimei mutări și păstrarea istoricului. Hash-ul APK-ului testat: `08f1c4a11ace06059fee2e22d0d7d7e19a2e6a81cea962ff0186f8880dc249bc`.
- Rularea nativă, raportul Extent și capturile sunt păstrate în rădăcina externă de dovezi; sursele și artefactele nu sunt amestecate.
- Limită: nu reprezintă acceptanță pentru toate dimensiunile ecranului, TalkBack, fonturi mărite sau un traseu UI complet `Bet → Raise → Call`.
