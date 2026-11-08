# J6-UI-03 · Turneu local până la rezultat

**Prioritate:** P1

**Stare:** Rezolvat pentru turneul Android offline 0.1.0

Mâna de antrenament reconstruia stivele la fiecare deal. Un all-in putea încheia mâna, dar nu exista un parcurs care să păstreze eliminările și să arate câștigătorul.

## Contract

- Folosiți `Tournament` pentru stive, rotație și heads-up. Păstrați aceeași poziție fizică a jucătorului și marcați locurile eliminate.
- Arătați ultima plată înainte de rezultat. Dacă rămâne doar jucătorul, afișați felicitările. Dacă jucătorul a ieșit, afișați eliminarea.
- Reconstruiți rundele din seed și mișcările acceptate la recrearea activității. Verificați că un control dintr-o mână anterioară nu poate schimba runda curentă.

## Dovadă

Matricea de stive și rotație rulează pe JVM. Sanity-ul Android folosește un seed de debug pentru trei mâini până la victorie și o eliminare separată. Extent preia aserțiunile observate și încorporează capturile. [Instrucțiuni de rulare](../../holdem-android/README.md#sanity-de-turneu-pe-android)

Pe Android API 28 au trecut 41 de aserțiuni. Al doilea deal a scos două locuri, al treilea a încheiat heads-up-ul cu 8.000 de jetoane la jucător, iar traseul de eliminare a afișat stiva zero. Cele cinci imagini includ ambele ecrane finale. Runnerul așteaptă cadrele Android și stabilizarea accesibilității înainte de captură.
