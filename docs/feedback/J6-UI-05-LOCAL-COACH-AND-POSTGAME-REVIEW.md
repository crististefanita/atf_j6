# J6-UI-05 · Antrenor local și recapitulare după joc

**Prioritate:** P1  
**Stare:** Implementat parțial; calificarea JVM este raportată, acceptanța UI rămâne  
**Domeniu:** Android local, modurile offline de antrenament Hold’em

## De ce este necesar

J6 afișează actorul și primește cărțile proprii și board-ul public. Adversarii locali check/call. Cerința extinde masa cu recapitulare după mână, coach offline și o preluare limitată a mâinii curente. `J6-UI-01` descrie fluxul de bază; acest contract permite cărțile adverse numai în proiecția de prezentare după decontare, niciodată în observația AI.

## Situația implementării în codul live

- **Implementat:** cărți adverse ca spate în timpul mâinii; cărți distribuite afișate după `Street.COMPLETE`, inclusiv pentru locurile foldate; recapitulare cu nume, Fold/Out și cărțile ultimei mâini pe rezultatul turneului.
- **Implementat:** etichetă centrală cu numele actorului, categorie de mână formată din cele două cărți proprii și board-ul vizibil, marcaj Fold pe locul retras și selector de blinduri 1/2/3/5 minute, implicit 1. Nivelul se schimbă la începutul mâinii următoare după expirare.
- **Implementat:** hint-ul rulează în fundal, nu aplică acțiunea și afișează acțiunea, equity, pot odds, pragul, versiunea politicii, eșantioanele și eroarea standard disponibile.
- **Implementat limitat:** butonul lasă AI-ul să termine mâna locală curentă și blochează comenzile manuale cât timp rulează; nu pornește automat următoarea mână sau întregul turneu.
- **Rămâne pentru calificare:** expunerea și testarea celor cinci cărți decisive, kicker-ului și cazului în care board-ul joacă singur; un test explicit că schimbarea exclusivă a cărților ascunse nu schimbă hint-ul/decizia; testarea legăturii dintre selecția din meniu (1/2/3/5 minute, implicit 1), setarea salvată și regulile turneului; verificări dedicate de localizare, accesibilitate și layout la font mărit. Sanity-ul nativ de turneu și cel de instalare Release au trecut, însă nu înlocuiesc aceste verificări de componentă și nu declară acceptanță UI completă.

### Testare curentă

- Calificarea curentă: 80/80 teste Maven, inclusiv patru teste de guvernanță Extent; componentele Android JVM au trecut 27/27, iar lint Release nu are avertismente.
- Sanity Android API 28: turneu complet și eliminare 42/42 aserțiuni, cinci capturi; instalarea APK-ului Release 7/7, două capturi. Rapoartele native Extent încorporează capturile și sunt inspectate cu profilul de raportare. Aceste probe nu închid lacunele dedicate de evaluator, confidențialitate și configurare enumerate mai sus.

Ținta este o masă care explică ce se întâmplă, ce combinație are jucătorul acum și ce decizie sugerează AI-ul, fără a-i arăta informații pe care politica nu le-ar avea în mod legitim.

## Cerințe de produs

### 1. Indică fără ambiguitate cine acționează

- Locul activ are o evidențiere vizuală consecventă cu masa comună J4/J5 și o etichetă textuală lângă nume; culoarea singură nu transmite tura.
- Antetul mesei sau zona de acțiuni spune pe scurt cine trebuie să acționeze și ce așteaptă interfața: acțiunea jucătorului, răspunsul AI-ului, următoarea stradă ori decontarea.
- Eticheta se mută după fiecare acțiune validă și dispare la o stare terminală. În heads-up, rotația dealerului, blindurilor și actorului nu trebuie să mute eticheta pe locul greșit.
- Textul, ordinea de focus și descrierea accesibilă identifică actorul chiar și la font mărit și în layout compact. Testele nu se bazează numai pe culoarea chenarului.

### 2. Arată cea mai bună combinație făcută din informația cunoscută

- În timpul unei mâini locale, un indicator dedicat prezintă categoria celei mai bune mâini Hold’em pe care jucătorul o poate forma **acum** din cele două cărți proprii și cărțile comune deja deschise.
- Categoria și cărțile decisive se recalculează după flop, turn și river. Se aplică regulile reale de departajare și kicker; când board-ul comun este cea mai bună mână, indicatorul spune explicit că mâna este pe board.
- Înainte de flop, interfața nu inventează o combinație de cinci cărți: afișează separat profilul celor două cărți proprii sau mesajul localizat că încă nu există o mână făcută. Draw-urile și șansele rămân câmpuri separate de categoria mâinii făcute.
- Indicatorul folosește numai cărțile proprii și board-ul public curent. Nu evaluează și nu sugerează rangul cărților adversarilor ascunse. Se localizează în română și engleză și are o descriere accesibilă completă.

### 3. Oferă un hint AI explicabil, numai în antrenamentul local

- Când jucătorul uman este actor, un hint poate arăta acțiunea legală recomandată de politica selectată și o explicație scurtă bazată pe semnalele pe care le-a folosit: prețul call-ului, pot odds/equity estimată, poziție sau context public relevant. Dacă modelul nu estimează un semnal, interfața nu îl inventează.
- Hint-ul este consultativ: nu execută mutarea, nu schimbă revizia mâinii, stack-urile ori istoricul. Jucătorul poate alege orice acțiune legală disponibilă.
- Hint-ul indică nivelul și versiunea politicii, precum și incertitudinea disponibilă; nu pretinde că acțiunea este perfectă sau că garantează câștigul.
- Hint-ul nu este afișat la tura adversarului, când observația este incompletă ori când nu există decizie umană de luat. Nu dezvăluie seed, pachet, cărți ascunse sau rezultate viitoare.
- Coaching-ul și autoplay-ul sunt disponibile **numai în modurile locale offline**. Sunt dezactivate în adaptorul online, inclusiv când platforma permite bot-uri; astfel UI-ul nu devine asistență în timp real pentru un jucător de pe o platformă externă.

### 4. Dezvăluie cărțile după terminarea jocului, nu în timpul lui

- Pe durata unei mâini active, inclusiv după fold-ul unui loc, cărțile adverse rămân ascunse în masa, observația actorului, hint și jurnalul de decizie.
- După decontarea terminală a unei mâini de antrenament, sau după victoria/eliminarea care încheie un turneu local, ecranul de rezultat oferă o recapitulare cu cele două cărți ale fiecărui loc din **ultima mână încheiată**, inclusiv cărțile locurilor care au dat fold.
- Recapitularea este marcată drept dezvăluire pentru învățare după joc. Ea nu schimbă showdown-ul oficial, câștigătorii, poturile, stack-urile ori istoricul acțiunilor și nu este accesibilă până când rezultatul este terminal.
- Dacă recapitularea persistă după recrearea activității, sursa ei este snapshot-ul terminal salvat, nu o nouă citire privilegiată din politica AI. La „Play again” sau la o nouă mână, cărțile adverse sunt din nou ascunse.

### 5. Permite preluarea și oprirea controlului AI

- În jocul local există o comandă explicită **„AI termină mâna”**, cu stare vizibilă; la apăsare, AI-ul joacă deciziile locului uman până la decontarea mâinii curente. Rularea se oprește la terminal, iar o mână nouă cere o acțiune nouă a utilizatorului.
- Pe durata preluării, eticheta arată că AI-ul joacă, iar acțiunile manuale se dezactivează. Politica verifică întreruperea și o limită de pași; după completare, controlul manual este disponibil din nou pe mâna următoare.
- Fiecare mutare trece prin aceeași validare de acțiuni legale, identificator de tură și revizie ca mutarea manuală. Re-render, tap repetat, recrearea activității sau pauza nu dublează o acțiune deja acceptată.
- Politica primește aceeași observație limitată a actorului ca în evaluarea offline: cărțile proprii, board-ul public, acțiunile publice, stack-urile și meniul legal. Oracolul de test poate vedea starea completă numai separat și nu este conectat la politica jucătorului.
- Preluarea nu pornește automat la intrarea în joc și nu este disponibilă în modul online.

## Verificare cerută

### Teste de componentă, fără emulator

- Evaluarea mâinii: high card, pereche, două perechi, trei de-un fel, chintă, culoare, full house, careu și chintă de culoare; kicker-uri; board care joacă singur; board-uri pereche; tranzițiile flop–turn–river. Compară categoria și cele cinci cărți alese cu un oracol independent.
- Vizibilitate: politica și hint-ul nu se schimbă când se modifică numai cărțile ascunse ale adversarilor; proiecția conține numai cărțile actorului și board-ul public.
- Hint: acțiunea recomandată aparține meniului legal; cererea hint-ului nu mută mâna; același snapshot și aceeași versiune de politică produc același rezultat; lipsa estimării produce formularea de fallback, nu o valoare inventată.
- Starea turei: actorul vizual corespunde exact actorului din motor după call, raise, fold, schimbarea străzii, heads-up, eliminare și restaurare.
- Dezvăluirea: înainte de terminal, nicio carte ascunsă nu ajunge în view-state sau în arborele de accesibilitate; după terminal, recapitularea conține câte două cărți per loc și nu modifică rezultatul; la joc nou, dezvăluirea veche dispare.
- Autoplay: fiecare acțiune este legală, se acceptă cel mult o dată, oprirea păstrează următoarea decizie pentru om, iar politica nu poate accesa pachetul/seed-ul sau mâinile adverse.
- Interfață: componente JVM verifică layout compact și normal, mese heads-up și 4–6 locuri, română/engleză, font mărit și etichete accesibile fără suprapuneri.

### E2E minim, doar după ce componentele trec

Un traseu local de mână verifică hint-ul fără execuție, alegerea manuală, autoplay pornit/oprit și recapitularea terminală. Un sanity scurt de turneu verifică faptul că adversarii continuă automat și că dezvăluirea apare numai după victorie/eliminare. Păstrați E2E la aceste două trasee; restul matricei rămâne simulată și testată pe componente.

## Limite și dependențe

- `J6-UI-01` rămâne descrierea fluxului existent de mână și a contractului de vizibilitate. Cerința nouă extinde contractul doar pentru ecranul local terminal de review; nu schimbă regulile de showdown.
- Egalitatea dimensiunii cărților, selecția vizuală a celor cinci cărți alese și semnalul premiului spre beneficiari sunt extensii separate, urmărite în [J6-UI-07](J6-UI-07-SHOWDOWN-WIN-CLARITY.md). Acest feedback păstrează owner-ul pentru coach și confidențialitatea dezvăluirii.
- Eticheta combinației reutilizează evaluatorul de reguli Hold’em, iar hint-ul reutilizează politica AI și observația actorului. UI-ul nu implementează logică paralelă de poker și nu deduce cărți din adversari.
- Prioritatea nu justifică prezentarea actualului estimator Monte Carlo drept solver: documentația AI continuă să declare limitele de calibrare și estimare din [AI și evaluare](../requirements/AI_REQUIREMENTS.md).
