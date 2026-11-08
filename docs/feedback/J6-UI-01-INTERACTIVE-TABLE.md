# J6-UI-01 · O mână locală interactivă

**Prioritate:** P1

**Stare:** Rezolvat pentru mâna locală de antrenament

Motorul furnizează o vedere stabilă pentru jucătorul uman, iar sesiunea validează mâna și revizia înainte de fiecare comandă. La decontare, cărțile adverse rămân ascunse și se afișează numai board-ul, cărțile proprii și jetoanele primite.

## Criterii de închidere

- Motorul furnizează un snapshot pentru același jucător când se schimbă actorul. Payload-ul păstrează exact cărțile proprii, prefixul public al board-ului și datele publice de masă.
- UI preia lista de acțiuni permise și limitele de la motor. Un raise se exprimă explicit ca jetoane adăugate acum sau ca total pe stradă, cu conversia într-un singur loc. O comandă veche, dublă sau din afara limitelor lasă starea neschimbată.
- Traseul folosește meniul și geometria deja integrate. J6 deține panoul de acțiuni, poturile, blindurile, stiva și istoricul. Contractul comun Card Table rămâne fără reguli Hold’em.
- Testele JVM acoperă legalitatea și vizibilitatea. Un singur sanity Android parcurge o mână cu acțiuni reale, inclusiv decontarea. Se verifică API-ul minim al tuturor apelurilor JVM ajunse în APK.

## Calificare

Componentele au verificat mesele cu 4–6 locuri, inclusiv fold, all-in, restaurare și comenzi expirate. Sanity-ul de 4 locuri a trecut pe Android API 28 cu 78 de comparații. Raise-ul de 39 a fost respins la minimul de 40, raise-ul de 60 a fost acceptat, iar potul de 240 a fost plătit. Mesajul de validare rămâne sub câmp și lasă butoanele accesibile.

[Contractul și build-ul Android](../../holdem-android/README.md) · [Matricea compactă verificată](J6-UI-02-TABLE-COVERAGE.md)
