# J6-UI-10 · Istoricul pariurilor

**Prioritate:** P1  
**Stare:** Revenirea la Call verificată pe API 28. Rămân corelarea unui Raise acceptat cu jurnalul curent și probele TalkBack în ferestre scunde.

## Acoperirea existentă

- În preflop, traseul deschide Raise, închide editorul și activează Call prin nodul Android de accesibilitate. Flopul și intrarea `Call +20` în jurnal confirmă acceptarea acțiunii.
- Pe API 28, revenirea dintr-o fereastră modală poate lăsa rădăcina activă indisponibilă chiar dacă fereastra mesei are deja focus. Instrumentarea caută nodul în fereastra de aplicație focalizată când rădăcina activă lipsește. Raportul cere dovada clickului și păstrează diagnosticul dacă aceasta lipsește.
- Proba verifică separat badge-urile vizibile și descrierile accesibile ale locurilor. [Turneu de sanity 0.14.1](../../release/0.14.1/reports/android-tournament-extent.html) · [Ghid Android](../../holdem-android/README.md)

## Acoperirea editorului

Proba de componentă leagă Raise-ul acceptat de motor de intrarea unică, contribuția cumulată și replay. Rămâne verificarea comenzii de confirmare din editorul Android. [Strategia Android](../testing/TEST_STRATEGY.md#interfața-android-locală)

## Acceptanță rămasă

1. Confirmă în Android un Raise aplicat din editor. Verifică suma și intrarea unică din jurnal după apăsarea butonului de confirmare.
2. Într-o fereastră scundă, verifică nume lungi în română și engleză. Badge-ul, stiva, acțiunile și cărțile trebuie să rămână lizibile. Istoricul trebuie să păstreze ordinea motorului.
3. Cu TalkBack activ, parcurge indicatorul fiecărui loc și istoricul. Ascultă numele, acțiunea, suma și contribuția cumulată o singură dată pentru fiecare loc. Leagă proba de matricea [J6-UI-09](J6-UI-09-SEAT-METRIC-HIERARCHY.md).
4. Sincronizează aserțiunea de accesibilitate după Check cu următoarea intrare din jurnal. Așteaptă ca ordinalul acțiunii să avanseze, apoi verifică jucătorul, suma și contribuția. Adaugă o probă care livrează întâi nodul vechi și apoi acțiunea așteptată. La expirare, păstrează ultimul ordinal și ultima descriere observată.
