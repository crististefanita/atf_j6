# Realizări Hold’em — calibrarea dificultății

## Pe scurt

**P2 · De validat cu jucători.** Catalogul grupează obiectivele în funcție de rezultatele de la masă. Dificultatea relativă rămâne o ipoteză până vedem cum avansează profilurile în sesiuni reale.

[Contractul implementat](../requirements/PRODUCT_REQUIREMENTS.md#realizări) · [Metoda de proiectare](../design/achievements/README.md)

## Acoperirea existentă

- Hold’em și Rentz folosesc ecranul comun pentru listă, progres, detalii și notificări. J6 verifică manifestul și checksum-urile bibliotecii înainte de compilare.
- Testele de componentă verifică filtrele, poturile, pragurile și notificarea după salvare.
- Catalogul activ are criterii RO/EN. Calificarea offline compară Normal cu Greu.

[Contractul realizărilor](../requirements/PRODUCT_REQUIREMENTS.md#realizări) · [Testele Android](../../holdem-android/app/src/test/java/ro/atf/holdem/android/GameplayAchievementsComponentTest.java)

## Extinderea catalogului

- Prioritizează familiile de poker din contract. Alege pentru fiecare cel mai mic set de trepte cu criterii diferite și păstrează cheile existente. Verifică deblocările paralele și modificarea unui prag peste valorile inițiale.
- Pentru obiectivele de execuție personală din catalogul candidat, expune proveniența manuală a deciziei. Verifică eligibilitatea la reluare.
- Verifică pe Android filtrarea și revenirea după recrearea activității. Sesiunea trebuie să păstreze căutarea, filtrul, poziția și detaliul selectat.
- Califică lista extinsă la font mare și pe ecran scund. Măsoară contrastul stărilor obținut și în progres pe culorile efectiv randate de ecranul comun.

Catalogul candidat are reguli bilingve și o probă offline. Prioritizează obiectivele confirmate pe mai multe seed-uri. Pentru evenimentele rare, verifică și cazuri controlate, apoi măsoară frecvența în sesiuni.

Înainte de activare, expune proveniența manuală pentru turneele Hard și identitatea persistentă necesară titlurilor la dimensiuni diferite de masă.

[Catalogul candidat](../../config/j6-achievement-expansion.yaml) · [Protocolul de calificare](../testing/TEST_STRATEGY.md#replay-pentru-catalogul-extins)

## Criterii de acceptanță

- Verifică dacă primul obiectiv al fiecărui nivel este înțeles fără explicații și dacă se vede progresul paralel.
- Compară progresul simulat cu sesiuni reale pe Normal și Greu. Pentru seria de trei titluri, urmărește ferestre suficient de lungi pentru obiectivele repetate.
- Adaugă obiective doar pentru situații observabile din joc. Pentru o tactică nouă, precizează evenimentul autoritar, ce o deosebește de un câștig întâmplător și testul care respinge un caz aproape identic.
