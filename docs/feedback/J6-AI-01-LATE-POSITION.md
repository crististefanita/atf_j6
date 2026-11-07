# J6-AI-01 — Poziția târzie trebuie să includă butonul

**Prioritate:** P2  
**Stare:** Rezolvat

## Constatare

`positionFromButton` folosește zero pentru buton. Butonul acționează ultimul după flop, dar vechiul prag `position >= playerCount - window` îl excludea din fereastra târzie și număra locuri dinaintea butonului. Fereastra configurată putea fi, de asemenea, mai mare decât numărul jucătorilor rămași după eliminări.

## Remediere

Politica include poziția zero și cele `window - 1` poziții imediat dinaintea butonului. Fereastra efectivă se limitează la numărul de locuri active. Regula rămâne bazată exclusiv pe poziția primită în observația actorului.

## Criterii verificate

- Testele acoperă butonul, cutoff-ul, poziții mai timpurii, ferestre de unu/trei locuri și o masă scurtată la două locuri.
- Rotațiile cu distribuții identice acoperă toate cele șase poziții, iar jurnalul confirmă acțiunea legală și vizibilitatea cărților la fiecare decizie.
- `mvn -B -o '-Dmaven.repo.local=C:/work/holdem_back/maven-cache' test` trece integral după schimbare.
