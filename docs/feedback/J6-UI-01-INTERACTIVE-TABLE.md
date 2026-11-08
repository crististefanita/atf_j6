# J6-UI-01 · Aliniază masa activă și cerințele 0.2.1

**Prioritate:** P1

**Stare:** Divergență între cerință și implementarea în lucru

**Owner:** Produs și interfață Android J6

Cerința de produs descrie cărțile adverse cu fața în jos în timpul mâinii. În candidatul 0.2.1 (`870e4f0`), `showOpponentCards` afișează numai fețele terminale, iar sanity-ul cere zero noduri pentru spatele cărților și zero fețe adverse la preflop. README-ul Android din același candidat precizează că locurile adverse afișează cărți numai la decontare. [Cerințe de produs](../requirements/PRODUCT_REQUIREMENTS.md)

Sanity-ul candidatului măsoară la deschidere fundalul comenzilor la 75% și o zonă de atingere de minimum 48 dp. Verifică și absența derulării cu istoricul pliat la preflop și după decontare. `HoldemViewport` păstrează derularea când masa depășește fereastra și la dimensiunea minimă a cărților.

## Criterii de închidere

<<<<<<< Updated upstream
- Motorul furnizează un snapshot pentru același jucător când se schimbă actorul. Payload-ul păstrează exact cărțile proprii, prefixul public al board-ului și datele publice de masă.
- UI preia lista de acțiuni permise și limitele de la motor. Un raise se exprimă explicit ca jetoane adăugate acum sau ca total pe stradă, cu conversia într-un singur loc. O comandă veche, dublă sau din afara limitelor lasă starea neschimbată.
- Traseul folosește meniul și geometria deja integrate. J6 deține panoul de acțiuni, poturile, blindurile, stiva și istoricul. Contractul comun Card Table rămâne fără reguli Hold’em.
- Testele JVM acoperă legalitatea și vizibilitatea. Un singur sanity Android parcurge o mână cu acțiuni reale, inclusiv decontarea. Se verifică API-ul minim al tuturor apelurilor JVM ajunse în APK.

## Calificare

Componentele au verificat mesele cu 4–6 locuri, inclusiv fold, all-in, restaurare și comenzi expirate. Sanity-ul de 4 locuri a trecut pe Android API 28 cu 78 de comparații. Raise-ul de 39 a fost respins la minimul de 40, raise-ul de 60 a fost acceptat, iar potul de 240 a fost plătit. Mesajul de validare rămâne sub câmp și lasă butoanele accesibile.

[Contractul și build-ul Android](../../holdem-android/README.md) · [Matricea compactă verificată](J6-UI-02-TABLE-COVERAGE.md)
=======
- Confirmați politica pentru locurile adverse active și actualizați formularea explicită în documentul ownerului odată cu acceptarea. Proba separă absența spatelui și a fețelor în timpul mâinii de dezvăluirea după decontare pentru locurile care au primit cărți, inclusiv fold.
- Păstrați ținta de atingere și textul lizibil în română și la font mărit. Verificați o fereastră scundă care încape după scalare și una care cere derulare la dimensiunea minimă. În ambele cazuri, ultimele comenzi și dialogul de raise cu tastatura deschisă trebuie să rămână accesibile. Validarea unui raise aplicat rămâne la [J6-UI-10](J6-UI-10-BETTING-ACTION-LEDGER.md).
>>>>>>> Stashed changes
