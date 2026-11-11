# J6-UI-10 · Istoricul pariurilor

Rezolvat pentru Check și Raise 60. Ownerul este masa Android și instrumentarea ei.

## Comportament verificat

- În profilul compact de 320 × 328 dp, cu font 2,0 și TalkBack activ, comanda Check transferă focusul la locul jucătorului. Focusul rămâne acolo până la următoarea decizie, după acțiunile adversarilor.
- Badge-ul mărit afișează acțiunea și suma pe două linii. Istoricul păstrează ordinalul și aduce rândul întreg în fereastră când primește focus.
- Check adaugă 0 și păstrează contribuția la 20. Raise din editor adaugă 60 și duce contribuția la 70 cu blindul deja plătit. Fiecare acțiune are un singur rând. [Probe și criterii](../testing/TEST_STRATEGY.md#confirmarea-unui-raise-din-editor)

## Regresii și calificare

- Traseul de turneu verifică și revenirea la Call după închiderea editorului Raise. Testele de componentă acoperă confirmarea repetată, respingerea, replay-ul și așteptarea ordinalului corect după Check. [Sincronizarea jurnalului](../testing/TEST_STRATEGY.md#sincronizarea-jurnalului-după-check)
- Inventarul RAG propune `-IncludeBettingLedger` pentru căile declarate. Opțiunea rulează profilurile standard, compact și compact cu TalkBack și publică dovezile Extent. [Calificarea de release](../release/ATF_Release.md#calificare-reproductibilă)
- Parcurgerea prin gesturi și anunțurile audio pentru toate locurile și ambele limbi rămân în matricea de accesibilitate. [J6-UI-09](J6-UI-09-SEAT-METRIC-HIERARCHY.md)
