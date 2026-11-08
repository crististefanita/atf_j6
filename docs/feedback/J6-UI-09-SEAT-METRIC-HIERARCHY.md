<<<<<<< Updated upstream
# J6-UI-09 · Lizibilitatea sumelor din chenare

Cazul compact cu șase locuri a fost verificat pe Android API 28, la `320 dp` <!-- rag:observed android.seat-metrics.viewport-width --> și font 2,0, în română.

- Eticheta și suma rămân pe aceeași linie când măsurătorile o permit. La lățime insuficientă, eticheta rămâne întreagă deasupra sumei. Contrastul etichetelor mici este de cel puțin 4,5:1 pe fundalurile active și inactive. Starea `Fold` / `Elim.` rămâne explicită, iar textul păstrează opacitatea.
- Testele JVM separă stiva, contribuția, premiul și restituirea. Proba nativă compară cele șase chenare cu motorul înainte și după decontare, măsoară textul și verifică focalizarea actorului cu TalkBack activ.
- Afișajul de dezvoltator TalkBack a redat anunțul complet al actorului, inclusiv cele două sume și starea de tură. Validarea audio și parcurgerea tuturor locurilor prin gesturi rămân probe manuale separate.

Pentru revalidare, folosiți `-Mode seat-metrics`. [Strategia Android](../testing/TEST_STRATEGY.md#interfața-android-locală) · [Cerințele produsului](../requirements/PRODUCT_REQUIREMENTS.md#ierarhia-informației-de-la-loc)
=======
# J6-UI-09 · Lizibilitate și anunțuri la locul jucătorului

**Prioritate:** P2

**Stare:** Cazul compact verificat în dosarul 0.2.2 pe API 28. Probele audio și navigarea integrală prin gesturi rămân deschise.

**Owner:** Prezentarea și accesibilitatea Android J6

Raportul `android-seat-metrics-extent.html` din dosarul 0.2.2 păstrează 15 aserțiuni și două capturi. Receipt-ul confirmă layout-ul compact și sumele înainte și după decontare, descrierea completă a actorului și focusul de accesibilitate cu TalkBack activ. [Cerința](../requirements/PRODUCT_REQUIREMENTS.md#ierarhia-informației-de-la-loc)

## Probe manuale rămase

- Ascultați anunțurile și parcurgeți toate locurile prin gesturi în română și engleză. Includeți un loc cu rol de blind și unul cu o restituire. [J6-UI-04](J6-UI-04-PAYOUT-READABILITY.md) deține exactitatea sumelor.
>>>>>>> Stashed changes
