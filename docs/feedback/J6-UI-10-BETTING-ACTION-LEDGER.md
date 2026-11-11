# J6-UI-10 · Istoricul pariurilor

P1. Ownerul este instrumentarea mesei Android.

## Acoperirea existentă

- În preflop, traseul deschide Raise, închide editorul și activează Call prin nodul Android de accesibilitate. Flopul și intrarea `Call +20` în jurnal confirmă acceptarea acțiunii.
- Pe API 28, revenirea dintr-o fereastră modală poate lăsa rădăcina activă indisponibilă chiar dacă fereastra mesei are deja focus. Instrumentarea caută nodul în fereastra de aplicație focalizată când rădăcina activă lipsește. Raportul cere dovada clickului și păstrează diagnosticul dacă aceasta lipsește.
- Componenta confirmării folosește suma introdusă, aplică o singură comandă și verifică rândul din jurnal după replay. Acoperă sumele invalide și reîncercarea după respingere. [Confirmarea din editor](../testing/TEST_STRATEGY.md#confirmarea-unui-raise-din-editor)
- Sincronizarea după Check are regresii JVM pentru nodul vechi și expirarea așteptării. Proba caută ordinalul următor și verifică jucătorul, suma și contribuția. [Contractul probei](../testing/TEST_STRATEGY.md#sincronizarea-jurnalului-după-check)

## Acceptanță rămasă

1. Rulează nativ proba de confirmare din editor inclusă în `tournament`. Verifică descrierea accesibilă și intrarea unică din jurnalul extins. Implementarea probei este compilată.
2. Într-o fereastră scundă, verifică nume lungi în română și engleză. Badge-ul, stiva, acțiunile și cărțile trebuie să rămână lizibile. Istoricul trebuie să păstreze ordinea motorului.
3. Cu TalkBack activ, parcurge indicatorul fiecărui loc și istoricul. Ascultă numele, acțiunea, suma și contribuția cumulată o singură dată pentru fiecare loc. Leagă proba de matricea [J6-UI-09](J6-UI-09-SEAT-METRIC-HIERARCHY.md).
