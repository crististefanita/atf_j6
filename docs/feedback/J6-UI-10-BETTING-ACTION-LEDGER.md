# J6-UI-10 · Istoricul pariurilor

**Prioritate:** P1  
**Stare:** Revenirea la Call verificată pe API 28. Rămân probele pentru Raise aplicat, TalkBack și ferestre scunde.

## Verificat în 0.3.0

- În preflop, traseul deschide Raise, închide editorul și activează Call prin nodul Android de accesibilitate. Flopul și intrarea `Call +20` în jurnal confirmă acceptarea acțiunii.
- Pe API 28, revenirea dintr-o fereastră modală poate lăsa rădăcina activă indisponibilă chiar dacă fereastra mesei are deja focus. Instrumentarea caută nodul în fereastra de aplicație focalizată când rădăcina activă lipsește. Raportul cere dovada clickului și păstrează diagnosticul dacă aceasta lipsește.
- Contractele de interacțiune, stilul compact și indicatorul opțional de șansă sunt documentate la owner. [Ghid Android](../../holdem-android/README.md) · [Strategie de testare](../testing/TEST_STRATEGY.md) · [Dosar 0.3.0](../../release/0.3.0/README.md)

## Acceptanță rămasă

1. Folosește o probă de componentă pentru fiecare tip de acțiune, contribuție cumulată și rotație de locuri. Android trebuie să confirme doar un Raise aplicat din editor, cu suma reală și o singură intrare acceptată.
2. Într-o fereastră scundă, verifică nume lungi în română și engleză. Badge-ul, stiva, acțiunile și cărțile trebuie să rămână lizibile. Istoricul trebuie să păstreze ordinea motorului.
3. Cu TalkBack activ, parcurge indicatorul fiecărui loc și istoricul. Ascultă numele, acțiunea, suma și contribuția cumulată o singură dată pentru fiecare loc. Leagă proba de matricea [J6-UI-09](J6-UI-09-SEAT-METRIC-HIERARCHY.md).
