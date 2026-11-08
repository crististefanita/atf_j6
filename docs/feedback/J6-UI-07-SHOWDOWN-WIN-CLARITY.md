# J6-UI-07 · Cărți decisive și semnalul câștigătorului

**Prioritate:** P1  
**Stare:** Implementare parțială — evaluatorul exact, separarea premiu/rest, evidențierea și păstrarea mesei sunt conectate; verificarea completă UI/E2E este restantă  
**Domeniu:** recapitularea mâinii locale și decontarea Hold’em

## Problema observată în cod

Prezentarea terminală arată cărțile proprii mai mari decât pe cele adverse. `HoldemTableView` folosește înălțimi de 56 dp pentru mâna proprie, 24/32 dp pentru cărțile de pe locurile adverse și 38 dp în recapitularea turneului. La șase locuri, diferența reduce lizibilitatea exact când utilizatorul compară mâinile.

`bestHandLabel` arată numai categoria calculată din cărțile proprii și board. `HandRank.bestOf` returnează scorul ordonat, nu identitățile celor cinci cărți selectate din cele 5–7 disponibile. Prin urmare, interfața nu poate ridica în mod corect exact cărțile care compun mâna.

Animația curentă colectează cărțile proprii, board-ul și cărțile dezvăluite către eticheta potului. Contractul din `J6-UI-06` confirmă că această mișcare nu indică locul care primește premiul. `HoldemHand` are suma acordată fiecărui loc, iar `PotSettlement` păstrează câștigătorii pe fiecare strat de pot; prezentarea trebuie să folosească această sursă autoritară.

## Contractul cerut

1. După decontare, toate cărțile proprii dezvăluite au aceeași lățime și înălțime ca fața cărților jucătorului uman din aceeași prezentare. La 4–6 locuri, interfața rearanjează conținutul sau îl face derulabil; nu micșorează numai cărțile adverse. Dacă viewport-ul cere o dimensiune mai mică, aplicarea ei este uniformă pentru toți.
2. Evaluarea mâinii expune un rezultat imuabil cu scorul, categoria și exact cinci identități de carte selectate din cele două cărți proprii plus board-ul. Rezultatul păstrează proveniența fiecărei cărți, nu folosește culoarea suit-ului la departajare și respectă regulile existente pentru kicker și board-play.
3. Recapitularea ridică discret cărțile selectate pentru fiecare mână evaluabilă și arată categoria. Cărțile comunitare sunt marcate ca parte din board, iar cărțile proprii rămân ușor de deosebit. Mâinile se pot compara fără a confunda o carte comună cu una privată.
4. O victorie prin fold este prezentată drept pot acordat fără showdown. Nu se inventează o combinație câștigătoare și nu se pretinde că mâna retrasă a câștigat la comparația de cărți.
5. Semnalul premiului pornește din zona potului și ajunge la fiecare loc care primește jetoane. Sumele și destinațiile derivă din decontarea motorului, inclusiv pot principal, poturi secundare, split și restituirea contribuției neacoperite. Cărțile pot rămâne afișate pentru explicație; animația jetoanelor este separată de colectarea cărților.
6. La split pot, toți beneficiarii sunt marcați, fără alegerea arbitrară a unui singur câștigător. Pentru mize returnate, interfața nu folosește eticheta de victorie. Mișcarea dezactivată la nivelul sistemului oferă aceeași destinație și rezultat prin text și stare statică.
7. După închiderea unei mâini care permite continuarea, tabla terminală rămâne vizibilă până când utilizatorul apasă `New hand`. Cărțile proprii, board-ul, cărțile dezvăluite, combinațiile evidențiate și rezumatul premiului nu dispar și nu se colectează automat în timpul așteptării. Nu pornește o mână nouă pe timer.
8. Apăsarea lui `New hand` este tranziția explicită care închide recapitularea și pornește următoarea mână o singură dată. O restaurare a activității cât timp recapitularea este deschisă reafișează aceeași stare terminală și păstrează butonul; nu consumă acțiunea înainte de apăsare.
9. La finalul turneului, butonul de rezultat este acțiunea explicită echivalentă: recapitularea ultimei mâini rămâne disponibilă pe ecranul rezultatului până la `Play again` sau revenirea la meniu.
10. Indicatorul combinației este un caption scurt, discret și localizat: numele categoriei este accentul principal, fără propoziția lungă „Cea mai bună combinație a ta”. În starea terminală, eticheta generică `Hand complete`/`Mână încheiată` dispare; titlul mâinii și butonul explicit descriu deja starea.

## Teste de componentă necesare

### Progres în cod

Motorul expune `HandEvaluation` pentru fiecare jucător eligibil la showdown și separă `potWon` de `returned`. Proiecția marchează doar cărțile evaluate ale beneficiarilor reali; o victorie prin fold nu primește combinație inventată. Cărțile terminale folosesc o singură înălțime derivată pentru mâna proprie, locuri adverse și recapitulare. Efectul de settlement mută un marker cu suma premiului de la pot la locurile din decontare, fără să animeze cărțile. Verificarea Android și E2E rămân restante.

- Evaluatorul întoarce exact cele cinci cărți pentru high card, pereche, două perechi, trei de-un fel, chintă, culoare, full house, careu și chintă de culoare; verifică kicker-uri, wheel, board-play și egalitate fără departajare pe suit.
- Pentru fiecare rezultat, un oracol independent confirmă scorul și mulțimea cărților. Variantele cu 5, 6 și 7 cărți verifică faptul că nu sunt ridicate cărți care nu intră în cea mai bună combinație.
- O mână închisă prin fold nu produce o selecție de showdown. Un showdown și o victorie uncontested au etichete și marcaje diferite.
- În viewport-uri compacte și normale, la 2/4/5/6 locuri, dimensiunile fețelor cărților dezvăluite sunt egale; testele detectează tăierea, suprapunerea și cărțile decisive care nu se pot identifica.
- Câștigătorul potului principal, câștigători diferiți ai poturilor secundare, split egal/inegal și returnarea unei mize neacoperite produc exact destinațiile din decontarea motorului. Niciun loc fără premiu nu primește semnal de câștig.
- Colectarea cărților și distribuirea premiului sunt două tranziții independente. Reluarea checkpoint-ului terminal nu acordă și nu animă premiul a doua oară.
- După decontare, timpul avansat fără input păstrează aceeași tablă terminală și același buton. O singură apăsare `New hand` trece la un deal nou; tap-uri repetate, recrearea activității și resume-ul nu sar peste recapitulare și nu dublează tranziția.
- Proiecția terminală nu conține captionul redundant `Hand complete`; indicatorul mâinii rămâne scurt și nu este tăiat în română sau engleză.
- Descrierile de accesibilitate numesc categoria, cărțile selectate, sursa lor și beneficiarul/suma, în română și engleză. Culoarea sau mișcarea singură nu transmite rezultatul.

## E2E minim după trecerea componentelor

Un singur traseu sanity vizual pornește o mână locală, verifică un showdown și compară dimensiunile, combinația evidențiată și destinația premiului. Lasă recapitularea deschisă fără input, apoi apasă `New hand` și verifică deal-ul nou. Un al doilea rezultat determinist verifică un fold-win sau split pot. Rapoartele trebuie să păstreze capturi native înainte și după acțiune; E2E nu înlocuiește verificările de logică și geometrie.

## Delimitări

- `J6-UI-05` rămâne ownerul pentru confidențialitatea cărților, coach și recapitularea după joc. Acest feedback completează recapitularea cu selecția exactă a mâinii și egalitatea de dimensiune.
- `J6-UI-06` rămâne ownerul pentru pacing și reluare. Comportamentul curent de colectare după 1,5 secunde nu mai este acceptabil pentru recapitularea unei mâini care așteaptă `New hand`; păstrarea până la input este contractul nou de aici. Premiul se animă spre beneficiarii returnați de `PotSettlement`; nu se reutilizează semantica de levată sau destinația potului pentru această mișcare.
- `J6-UI-04` deține semantica separării `potWon` și `returned`. Interfața expune acum `Won` și `Returned` ca valori distincte; testele UI trebuie să confirme că markerul de premiu folosește numai `potWon`.
