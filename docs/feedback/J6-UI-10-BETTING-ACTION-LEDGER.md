# J6-UI-10 · Acțiunea recentă și istoricul pariurilor

**Prioritate:** P1  
**Stare:** Verificat pe Android API 28: 11/11 probe de componentă și 64/64 aserțiuni E2E trecute; matricea extinsă de viewport și TalkBack rămân de calificat
**Domeniu:** vizibilitatea mutărilor publice din mâna curentă

## Problema

Jucătorul trebuie să vadă imediat cine a făcut `bet`, `raise`, `call`, `check`, `fold` sau `all-in`, cât a adăugat și cum evoluează contribuțiile. Un indicator unic lângă masă nu leagă suficient de clar mutarea de locul care a făcut-o și ocupă spațiu util pe ecrane joase.

## Soluția din codul live

Fiecare loc păstrează sub nume și valorile sale un indicator compact pentru cea mai recentă acțiune a acelui jucător. Indicatorul arată numărul mutării, tipul acțiunii și jetoanele adăugate la acea mutare. Accesibilitatea anunță și jucătorul, strada și contribuția cumulată. Cea mai recentă acțiune a mâinii primește un accent discret.

Istoricul complet rămâne disponibil într-o secțiune derulabilă, ascunsă inițial și deschisă la cerere. Fiecare rând păstrează ordinea motorului, locul fizic, strada, acțiunea, diferența adăugată și contribuția cumulată. Astfel, interfața arată rapid starea fiecărui loc, iar istoricul păstrează traseul complet fără să ocupe permanent zona jocului.

Chenarele folosesc text mai mic, spațiere verticală redusă și etichete scurte pentru blinduri. În timpul deciziei jucătorului uman, coach-ul rămâne locul pentru recomandare, equity estimată, pot odds, prag, numărul de eșantioane și incertitudine. Interfața nu afișează probabilități individuale inventate pentru adversari: acestea cer range-uri condiționate de acțiuni publice și calibrare, care sunt încă limitări cunoscute ale modelului din [J6-AI-02](J6-AI-02-STRATEGY-CALIBRATION.md).

Această alegere de interacțiune este o sinteză, nu o regulă impusă de un singur produs: unele interfețe leagă ultima acțiune de jucător, iar istoricul complet este oferit separat. Referințe: [DD Poker — game board](https://static.ddpoker.com/gamehelp/help/gameboard.html), [PokerStars — table appearance](https://www.pokerstars.com/help/articles/table-appearance-feature/232136/) și [ACR — navigarea istoricului la masă](https://faq.acrpoker.eu/gameplay/tables-features/how-to-navigate-the-player-table/).

## Contractul comportamental

- Jurnalul este o listă imuabilă și ordonată de acțiuni acceptate de motor; fiecare intrare conține ordinea, locul fizic, strada, tipul, suma adăugată și contribuția cumulată.
- La fiecare loc este vizibilă ultima acțiune proprie, chiar dacă alt jucător a acționat între timp. Un jucător fără acțiune în mâna curentă nu primește un indicator gol sau înșelător.
- Diferența de jetoane exprimă schimbarea produsă de acea acțiune. `Check` și `Fold` adaugă zero; blindurile sunt mutări distincte; `Call`, `Bet`, `Raise` și `All-in` nu confundă suma țintă cu diferența efectivă.
- Istoricul extins este localizat, derulabil, ordonat și disponibil până la pornirea mâinii următoare. Deschiderea lui aduce cele mai noi intrări la vedere.
- Identitatea acțiunii este accesibilă independent de culoare și poziție. Eticheta vizuală rămâne scurtă; descrierea accesibilă include jucătorul, strada și contribuția cumulată.
- Jurnalul nu dezvăluie cărți adverse, seed-ul sau informații despre rezultate viitoare. După decontare, istoricul poate fi consultat lângă cărțile dezvăluite.

## Probe existente

- Proiecția mapează acțiunile după locul fizic, inclusiv după rotația locurilor afișate.
- Componenta verifică `small blind`, `big blind`, `call`, `raise`, `fold`, `check`, `bet` și `all-in`, ordinea istorică și suma cumulată.
- Componenta verifică faptul că fiecare loc păstrează ultima sa mutare și că ultima mutare globală primește stilul accentuat.
- Componenta verifică ierarhia redusă a textului și valorilor pentru chenarele compacte și obișnuite.
- Verificarea locală curentă: `HoldemTablePresentationComponentTest` **11/11 PASS**; `compileSanityAndroidTestJavaWithJavac` **PASS**.

## Rezultatul verificării UI

- Android API 28, fereastră **392 × 626 dp**, font scale 1.0: **64/64 aserțiuni PASS**, **8 capturi native**.
- E2E-ul confirmă patru indicatoare vizibile, patru descrieri accesibile per loc, acțiunea `Call +20` la locul uman, ultima mutare `Check +0`, extinderea istoricului și derularea până la mutările inițiale.
- Același traseu continuă până la decontare și parcurge fluxul de turneu; raportul autonom Extent încorporează capturile native inline și a trecut inspecția publisherului.
- APK sanity: `39353f18940e6d407ffadd4242fbf598fa483b0d892454851c8ca22bf3ddaa7f`.
- Extent, receipt și PNG-urile sunt în `C:\Users\Cristi\AppData\Local\Temp\j6-live-final6-20261108\`; raportul E2E este `reports\android-tournament-extent.html`. Rularea nu a fost promovată în `release/0.1.1`.
- Componenta curentă: **11/11 PASS**; compilarea `:app:compileSanityAndroidTestJavaWithJavac` și asamblarea sanity trec.

Raportul anterior cu 58 de aserțiuni valida un sumar central și nu califică aranjamentul per loc. El nu este folosit pentru această acceptanță.

## Acceptanță UI rămasă

În proba API 28, după închiderea editorului de raise, controlul Call a rămas prezent în arborele view-urilor, dar activarea prin arborele accesibil a eșuat de două ori. Proba dialogului rămâne izolată. Verificați recuperarea focusului și efectuarea unui Call prin UI după închiderea dialogului înainte de a combina traseele.

1. Verifică viewport-uri joase și nume lungi în română și engleză; textul vizibil nu trebuie să se suprapună cu stiva, acțiunile sau cărțile.
2. Verifică TalkBack: fiecare indicator este anunțat o singură dată cu locul, mutarea și totalul cumulativ; deschiderea istoricului este anunțată ca acțiune disponibilă.
3. Păstrează separat testul unui `Bet → Raise → Call` UI real; verificările curente de componentă acoperă tipurile și proiecția, nu înlocuiesc traseul UI.
