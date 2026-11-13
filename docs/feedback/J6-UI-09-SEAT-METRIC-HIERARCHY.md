# J6-UI-09 · Lizibilitatea sumelor din chenare

P2. Ownerul este acceptanța de accesibilitate Android.

## Acoperire

- `seat-metrics` verifică focalizarea actorului și descrierea accesibilă la `320 dp` <!-- rag:observed android.seat-metrics.viewport-width --> și font 2,0. Afișajul de dezvoltator TalkBack a redat anunțul actorului. [Strategia Android](../testing/TEST_STRATEGY.md#interfața-android-locală)
- Componenta verifică destinația de focus și păstrarea derulării. `betting-ledger-talkback` verifică transferul de la Check la locul propriu și focalizarea rândurilor Check și Raise după deschiderea jurnalului. [Testele stării ferestrei](../../holdem-android/app/src/test/java/ro/atf/holdem/android/TableViewportPositionComponentTest.java) · [Probe compacte](../testing/TEST_STRATEGY.md#dovezi-native-pe-fereastră-compactă)

## Criteriu de închidere

- Parcurge toate locurile prin gesturi TalkBack în română și engleză. Verifică un singur punct de focus pentru fiecare loc, numele, stiva și contribuția în timpul mâinii, apoi premiul și restituirea la decontare.
- La font 200%, verifică separat locul propriu și rândul de jurnal, cu focus pe fiecare. Confirmă că numele locului rămâne integral vizibil când este focalizat și măsoară ce rămâne în fereastră când lista jurnalului este deschisă. [Cerințele produsului](../requirements/PRODUCT_REQUIREMENTS.md#ierarhia-informației-de-la-loc)
