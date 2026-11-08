# J6-UI-02 · Masa pe ferestre compacte și heads-up

**Prioritate:** P2

**Stare:** Rezolvat

J6 folosește ancorele comune pentru mesele cu 4–6 locuri și așază adversarul heads-up deasupra potului. Adaptorul compact scurtează antetul, afișează separat cărțile proprii și ține butoanele aliniate la marginea superioară a rândului. Dialogul de raise păstrează intervalul, suma, eroarea și cele două acțiuni vizibile cu tastatura deschisă în profilul calificat.

## Criterii de închidere

- Matricea compactă măsoară fereastra efectivă, fontul, cele cinci poziții comunitare, mâna și chenarele. La 6 locuri, fiecare chenar și potul rămân distincte și accesibile. Cazul heads-up are oracol propriu înainte de expunerea în meniu.
- J6 testează dealerul, blindurile, distribuirea cărților și actorul în ambele poziții heads-up. Ancorele pentru două locuri sunt în adaptorul local. [Poker TDA, regula 36-C](https://www.pokertda.com/view-poker-tda-rules/)
- Un sanity mic verifică meniul și un caz compact. Extent include pașii Android, comparațiile colapsabile, capturile și proveniența APK-ului. Matricea restului rulează offline.

## Calificare

Au trecut 36 de teste Maven, 7 componente Android JVM și 88 de comparații native pe Android API 28. Profilul nativ a folosit 720 × 1280 px, 360 dpi și font 2,0. Proba la șase locuri a verificat chenarele, mâna, cele patru comenzi preflop și cele cinci cărți de pe river. Dialogul a respins 39 și a acceptat 60. În heads-up, mâna următoare a inversat dealerul și blindurile, iar blindul mare a deschis acțiunea pe flop. Scriptul a restabilit configurația emulatorului.

[Contractul și build-ul Android](../../holdem-android/README.md)
