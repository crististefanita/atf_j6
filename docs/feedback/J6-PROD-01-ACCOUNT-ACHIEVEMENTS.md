# J6-PROD-01 · Realizări pentru contul Hold’em

## Pe scurt

**Stare:** toate cele 20 de familii sunt active. Calibrarea dificultății rămâne de evaluat cu jucători.

Hold’em Android are sesiune locală și clasament de turnee. Propunerea adaugă progres verificabil de profil și o bancă de exerciții. Clasamentul continuă să măsoare rezultatele turneelor.

[Catalogul cu 800 de ID-uri și praguri](J6-PROD-01-ACHIEVEMENT-CATALOG.csv) · [Cerințe de produs](../requirements/PRODUCT_REQUIREMENTS.md) · [Reguli de turneu](../requirements/TOURNAMENT_RULES.md)

## Implementare Android

- Meniul principal deschide Realizări. Taburile urmează ordinea fundamente, reguli de pariere, calcule, strategie și contexte specializate.
- Catalogul cuprinde 500 de realizări ușoare, 200 medii și 100 grele. Filtrul Disponibile pentru exersare arată probele deblocate și încă nefinalizate.
- Progresul se salvează offline după fiecare răspuns, pe ID-ul stabil al profilului. La Ușoare, pragurile din același context sunt cumulative. La Medii și Grele, fiecare lot păstrează numitorul fix.
- Catalogul și exercițiile noi sunt în română. Controalele și exercițiile Pot odds au variante în română și engleză.
- Exercițiile de reguli folosesc poziții, cărți și contribuții concrete. Exercițiile de strategie precizează modelul și ipotezele. Verificarea unei linii într-un model dat nu certifică o decizie universal optimă.

## Meniu și ecran

- Adăugați intrarea **Realizări** în meniul profilului.
- Ecranul afișează progresul total și trei taburi fixe, **Ușoare**, **Medii** și **Grele**, fiecare cu numărul deblocat și totalul. Tabul păstrează selecția și poziția la revenirea din detalii. Filtrele sunt Toate, În progres și Deblocate, cu căutare după titlu sau familie.
- Lista virtualizată folosește rânduri compacte cu titlu, criteriu și progres numeric. În detalii se văd prerechizitul, sursa creditului și următorul pas.
- În profil compact, taburile încap fără derulare orizontală. Folosiți text și icon pentru stare și verificați contrastul și ordinea TalkBack.
- Pentru profil fără progres, fiecare tab arată un prim pas. Testele de componentă acoperă selecția, filtrele, listele goale, 500 de rânduri și păstrarea poziției.
- Smoke-ul verifică navigarea din meniu, schimbarea tabului și revenirea din detalii.

## Praguri

| Nivel | Număr | Durată estimată | Criteriu |
|---|---:|---|---|
| Ușor | 500 | Câteva minute pe prag | Cinci praguri cumulative de 1–5 răspunsuri corecte în situații distincte din același context. |
| Mediu | 200 | În jur de o zi | Două realizări per context, 8/10 și 12/15 în loturi diferite. |
| Greu | 100 | Câteva zile, cu pauze | Trei loturi avansate independente, fiecare cu minimum 8/10. |

Cele 20 de familii au câte cinci contexte. Duratele sunt estimări de pilot. Loturile se reiau fără termen-limită.

Insignele și titlurile sunt cosmetice, fără depuneri, serii zilnice sau stimulente pentru volum.

Google oferă un model pentru progres incremental și offline. Catalogul propriu depășește limita Play Games Services. Misiunile PokerStars ilustrează afișarea criteriilor și a progresului. [Android Developers](https://developer.android.com/games/pgs/achievements) · [PokerStars](https://www.pokerstars.com/poker/promotions/daily-missions/) · [Gambling Commission](https://www.gamblingcommission.gov.uk/public-and-players/page/control-the-time-and-money-spent-gambling)

## Evaluare

- Pentru poziție, blinduri, mărimea legală a pariului, redeschiderea după all-in, poturi, showdown și egalități, oracolul returnează rezultatul exact. [Reguli Poker TDA](https://www.pokertda.com/view-poker-tda-rules/)
- Pentru pot odds compară echitatea stipulată cu prețul call-ului. Pentru outs deduplică și elimină cărțile contaminate.
- Pentru deciziile strategice fixează poziția, acțiunile publice, stackul efectiv, range-urile ipotetice și variantele echivalente. În probele ICM fixează și premiile.
- Decizia primește credit pe baza informației disponibile înainte de acțiune. Autoplay și soluțiile dezvăluite înainte de răspuns rămân antrenament.

## Etape și acceptanță

1. Pilotați un context din fiecare familie. Afișați criteriul înainte de răspuns și explicația după. Măsurați durata efectivă și acordul dintre verificatori.
2. Implementați identitatea locală de profil și progresul atomic pe baza profileId, scenarioId, bankVersion și attemptId. Creditul se acordă o dată per scenariu calificat și profil.
3. Publicați treptat loturile. Probele grele folosesc trei bănci distincte, situații noi pentru credit, numitor fix și checkpoint la pauză.
4. Dacă apare cont de rețea, separați sursele locală și online. Îmbinați evenimentele validate după identificator. Mutați contractele implementate în cerințele de produs.

Verificați unicitatea celor 800 de chei, pragurile, prerechizitele fără cicluri, limitele 7/10 și 8/10, lotul incomplet, evenimentul duplicat, două profile cu același nume, migrarea rubricii și reluarea offline. În evaluarea strategică, variați cărțile adverse ascunse și runout-ul. La side pots verificați beneficiarii fiecărui pot și conservarea jetoanelor.
