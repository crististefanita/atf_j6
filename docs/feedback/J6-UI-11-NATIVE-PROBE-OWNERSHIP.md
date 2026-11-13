# J6-UI-11 · Responsabilitățile probelor Android

P2. Ownerul este suita de instrumentare Android.

## Lacună

`TournamentSanity` combină scenarii, sincronizare Android, capturi, aserțiuni și intrarea de instrumentare. În scenariul de Realizări, accesarea unui câmp privat a făcut proba dependentă de structura `HoldemActivity`. Check, Raise și reluarea decontării au nevoie de contracte explicite pentru a separa o stare de produs de un nod încă indisponibil. [Suita nativă](../../holdem-android/app/src/androidTest/java/ro/atf/holdem/android/TournamentSanity.java)

La decontare, textul mesei poate deveni accesibil înainte de cadrul terminal al animației. Sincronizarea curentă așteaptă masa stabilă și închide dialogul dacă apare. Viitorul driver trebuie să distingă începutul decontării, sfârșitul animației și disponibilitatea mesei, cu un diagnostic diferit pentru fiecare timeout.

## Criteriu de închidere

- Păstrează punctul de intrare al instrumentării și separă scenariile de jurnal, premii și Realizări. Fiecare declară precondițiile, acțiunea de interfață și rezultatul observabil. Refolosește doar operațiile Android comune.
- Mută parsarea și așteptările deterministe la componente cu ceas și cadre simulate. Rulează în Android focusul real, compunerea cadrului și decontarea animată. Păstrează numele probei, inventarul aserțiunilor și capturile cerute de Extent. [Strategia de testare](../testing/TEST_STRATEGY.md#interfața-android-locală)
- Probează local cazurile cu dialog absent, dialog apărut după primul cadru de decontare și dialog nedemis. Niciun caz reușit nu trebuie să adauge capturi în afara inventarului; un timeout păstrează cadrul și starea observată.
