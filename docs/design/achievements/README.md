# Proiectarea realizărilor într-un joc

## Pe scurt

Realizările marchează ce a reușit jucătorul într-o partidă. Ghidul definește cum alegem obiectivele, cum le legăm de rezultatele motorului și cum verificăm progresul înainte de lansare. [Aplicarea în Hold’em](../../requirements/PRODUCT_REQUIREMENTS.md#realizări-și-antrenament) arată ce este specific jocului.

```mermaid
flowchart LR
    Game["Rezultat validat"] --> Facts["Fapte măsurabile"]
    Facts --> Rules["Obiective evaluate în paralel"]
    Rules --> Progress["Progres pe profil"]
    Progress --> UI["Listă pe niveluri"]
```

## Pornește de la joc

1. Enumeră situațiile în care jucătorul ia decizii și rezultatele care arată că a înțeles mecanica. Separă rezultatul unei runde, al unei partide și al unei competiții.
2. Alege pentru fiecare nivel un prim obiectiv ușor de explicat. La Ușoare, jucătorul obține primul rezultat de bază. La Medii, duce la capăt un obiectiv complet. La Grele, repetă o performanță în partide distincte sau reușește într-o situație rară și precis definită.
3. Grupează câte 5–6 obiective în jurul unei competențe. Ordinea recomandă ce să încerce jucătorul. În grup, fiecare obiectiv poate progresa în orice ordine. Folosește prerechizite doar când un mod de joc sau un eveniment chiar nu poate exista înaintea altuia.
4. Scrie criteriul înainte să alegi un prag: ce rezultat contează, în ce mod, pentru cine, când se confirmă și ce pare asemănător dar nu se califică.
5. Crește catalogul numai când apare o mecanică, o alegere sau o performanță distinctă. Mai multe praguri pe același contor sunt utile când marchează etape reale; răspunsurile la exerciții apar în Antrenament.

| Nivel | Întrebarea de proiectare | Verificare |
|---|---|---|
| Ușor | Ce poate reuși jucătorul în primele sesiuni? | Motorul confirmă un rezultat accesibil într-un mod obișnuit. |
| Mediu | Ce rezultat cere coordonarea mai multor decizii? | O rundă sau o competiție se încheie cu rezultatul cerut. |
| Greu | Ce performanță este rară sau cere consecvență? | Se verifică situația exactă sau mai multe rezultate distincte. |

## Contractul progresului

| Componentă | Responsabilitate |
|---|---|
| Motorul | Confirmă rezultatul și emite identitatea participantului, a rundei sau a partidei. |
| Adaptorul jocului | Extrage fapte din rezultatul confirmat: câștig, clasare, regulă de departajare ori contextul unei acțiuni. |
| Catalogul | Asociază faptele cu un ID stabil, nivel, grup, ordine, prag și text localizat. |
| Stocarea | Separă profilurile și deduplică în fiecare obiectiv după cheia evenimentului. Salvează împreună actualizările produse de același eveniment. |
| Interfața | Arată criteriul și progresul, inclusiv pentru obiectivele încă neobținute. |

- Un rezultat poate satisface mai multe obiective. O victorie poate avansa simultan primul câștig și obiectivul cu trei câștiguri.
- Redeschiderea aceluiași rezultat nu avansează din nou niciun obiectiv. Separă identitatea contului de numele afișat.
- La schimbarea criteriului, păstrează sensul ID-ului existent sau introdu un ID nou cu o regulă de migrare explicită.

Pentru reguli tactice, verifică acțiunea în contextul disponibil înainte de decizie. Un rezultat norocos nu dovedește că decizia a fost bună. Pentru un obiectiv bazat pe rezultatul partidei, folosește verdictul final al motorului.

<details>
<summary>Model de catalog YAML pentru un proiect nou</summary>

Un catalog mic poate rămâne un tabel tipizat în cod. Dacă regulile sunt editate separat, validează la încărcare câmpurile și leagă fiecare metrică de un evaluator înregistrat. Exemplul descrie schema, nu este un fișier încărcat de Hold’em.

```yaml
schemaVersion: 1
groups:
  - id: first-wins
    achievements:
      - id: first-round-win
        tier: easy
        order: 10
        metric: round.won
        distinctBy: roundId
        target: 1
        labelKey: achievement.firstRoundWin
      - id: three-round-wins
        tier: easy
        order: 20
        metric: round.won
        distinctBy: roundId
        target: 3
        labelKey: achievement.threeRoundWins
```

Respinge ID-urile duplicate, metricile necunoscute, pragurile invalide și textele lipsă. Catalogul descrie criteriile; evaluatorii rămân acoperiți de teste de domeniu.

</details>

## Acceptanță înainte de lansare

- Pentru fiecare familie, testează un rezultat pozitiv și o situație asemănătoare fără credit. La praguri repetate, verifică valorile imediat sub și la prag.
- Reia același rezultat după restart. Verifică un profil diferit cu același nume și un rezultat identic ca scor, dar cu alt ID.
- Livrează două rezultate valide în ordine inversă. Confirmă că ambele realizări pot avansa și că ordinea din listă rămâne aceeași.
- Verifică o întrerupere între închiderea partidei și scrierea progresului. La reluare, rezultatul valid trebuie procesat o singură dată.
- În UI, verifică un profil nou, progres parțial și realizare obținută. Pentru calibrare, urmărește cât durează obiectivele, cât de des apar și ce comportament încurajează. Nu crește volumul doar pentru a umple un nivel.

[Strategia de testare](../../testing/TEST_STRATEGY.md) · [Cerințe de produs](../../requirements/PRODUCT_REQUIREMENTS.md)
