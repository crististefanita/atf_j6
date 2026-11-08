<<<<<<< Updated upstream
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
=======
# J6-UI-10 · Verifică în UI un raise aplicat și istoricul său

**Prioritate:** P1

**Stare:** Proiecția și istoricul de bază sunt calificate, tranzacția raise rămâne de verificat pe UI curent

**Owner:** Masa și jurnalul Android J6

Calificarea 0.2.0 a verificat ultima acțiune pe fiecare loc, extinderea istoricului și revenirea la intrările anterioare. Proiecția are teste de componentă pentru actor, ordine, sumă adăugată și contribuție cumulată. În 0.2.1 (`9cb486a`), sanity-ul verifică apariția editorului `Add now`, îl închide și continuă cu `Call`. Aplicarea unei sume de raise rămâne deschisă. [Testul de prezentare](../../holdem-android/app/src/test/java/ro/atf/holdem/android/HoldemTablePresentationComponentTest.java) · [Sanity](../../holdem-android/app/src/androidTest/java/ro/atf/holdem/android/TournamentSanity.java)

## Criterii de închidere

- Folosiți o mână scurtă cu `Bet → Raise → Call`. Exercitați o valoare sub minim și una legală. Comanda respinsă păstrează starea, iar cea acceptată produce exact o intrare pe locul fizic corect.
- Verificați diferența dintre jetoanele adăugate și contribuția cumulată după call. Istoricul se deschide la cerere și păstrează ordinea când trece la strada următoare.
- Verificați în Extent acțiunea și aserțiunile aferente aceluiași checkpoint. [J6-UI-01](J6-UI-01-INTERACTIVE-TABLE.md) deține țintele de atingere și geometria, iar [J6-UI-09](J6-UI-09-SEAT-METRIC-HIERARCHY.md) deține anunțurile TalkBack.
>>>>>>> Stashed changes
