# J6-UI-09 · Lizibilitatea sumelor din chenare

P2. Ownerul este acceptanța de accesibilitate Android.

Proba `seat-metrics` a verificat focalizarea actorului și descrierea accesibilă la `320 dp` <!-- rag:observed android.seat-metrics.viewport-width --> și font 2,0. Afișajul de dezvoltator TalkBack a redat anunțul actorului. [Strategia Android](../testing/TEST_STRATEGY.md#interfața-android-locală)

Componenta verifică alegerea destinației de focus după Check și păstrarea poziției jurnalului la redesenare. [Testele stării ferestrei](../../holdem-android/app/src/test/java/ro/atf/holdem/android/TableViewportPositionComponentTest.java)

## Criteriu de închidere

Cu TalkBack activ, parcurge toate locurile prin gesturi și ascultă anunțurile în română și engleză. Fiecare loc trebuie să aibă un singur punct de focus. Verifică numele, stiva și contribuția în timpul mâinii, apoi premiul și restituirea la decontare. [Cerințele produsului](../requirements/PRODUCT_REQUIREMENTS.md#ierarhia-informației-de-la-loc)
