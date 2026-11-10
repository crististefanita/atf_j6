# J6-UI-07 · Cărțile decisive și semnalul premiului

**Stare:** Alinierea verticală în fereastra compactă și traseul markerului au dovezi native. Rămân cazuri de poziționare și lizibilitatea marcajelor.

## Acoperirea existentă

Proba compactă măsoară alinierea perechilor imediat sub chenare la cinci și șase locuri. Probele de decontare verifică plecarea din pot și sosirea markerului la beneficiar, inclusiv la split și la potul secundar. În fereastra scundă, rezumatul rămâne complet, iar markerul apare la chenarul vizibil. [Rapoarte 0.14.1](../../release/0.14.1/reports/README.md#rapoarte-extent) · [Contractul produsului](../requirements/PRODUCT_REQUIREMENTS.md#claritatea-rezultatului-mâinii)

## Dovezi rămase

- Măsoară centrarea orizontală a perechilor sub chenare la cinci și șase locuri. Extinde verificarea alinierii verticale la profilul obișnuit și la cazurile cu un rând de restituire sau un loc deja eliminat.
- Verifică identitatea și proveniența celor cinci cărți evidențiate într-un showdown pierdut și în cazul board-play. Probează categoria și descrierile accesibile în română și engleză. Un fold-win păstrează distincția față de un showdown.
- Confirmă lizibilitatea cărților terminale și marcajele în profilul compact. Pentru comportamentul până la comanda de continuare și la reluare, folosește [contractul Android](../../holdem-android/README.md#turneu-și-reluare).
