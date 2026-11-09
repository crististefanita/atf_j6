# Realizări Hold’em — calibrarea dificultății

## Pe scurt

**P2 · De validat cu jucători.** Catalogul grupează obiectivele în funcție de rezultatele de la masă. Dificultatea relativă rămâne o ipoteză până vedem cum avansează profilurile în sesiuni reale.

[Contractul implementat](../requirements/PRODUCT_REQUIREMENTS.md#realizări-și-antrenament) · [Metoda de proiectare](../design/achievements/README.md)

## Criterii de acceptanță

- Verifică dacă primul obiectiv din fiecare tab este înțeles fără explicații și dacă se vede că alte obiective pot progresa în paralel.
- Compară distribuția numărului de mâini și turnee necesare per nivel. Urmărește separat victoria cu o carte mare, unde oportunitatea depinde de distribuție și de adversari.
- Adaugă obiective doar pentru situații observabile din joc. Pentru o tactică nouă, precizează evenimentul autoritar, ce o deosebește de un câștig întâmplător și testul care respinge un caz aproape identic.
