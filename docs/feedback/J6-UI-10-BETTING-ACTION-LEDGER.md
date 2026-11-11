# J6-UI-10 · Istoricul pariurilor

P1. Ownerul este instrumentarea mesei Android.

## Acoperirea existentă

- În preflop, traseul deschide Raise, închide editorul și activează Call prin nodul Android de accesibilitate. Flopul și intrarea `Call +20` în jurnal confirmă acceptarea acțiunii.
- Pe API 28, revenirea dintr-o fereastră modală poate lăsa rădăcina activă indisponibilă chiar dacă fereastra mesei are deja focus. Instrumentarea caută nodul în fereastra de aplicație când rădăcina activă lipsește. Raportul păstrează diagnosticul dacă clickul nu a fost observat.
- Componentele acoperă suma exactă din editor, confirmarea repetată, reîncercarea după respingere și replay. După Check, proba așteaptă ordinalul corect și diagnostichează un nod vechi sau expirarea așteptării. [Strategia de testare](../testing/TEST_STRATEGY.md#sincronizarea-jurnalului-după-check)

## Verificat pe Android

Pe API 28, jurnalul a conținut câte un singur rând pentru Check (+0, total 20) și Raise (+60, total 70), atât pe profilul standard, cât și în fereastra de 320 × 328 dp cu font 2,0. Probele au verificat descrierile accesibile și au păstrat capturile originale în Extent. [Comenzi și criterii](../testing/TEST_STRATEGY.md#confirmarea-unui-raise-din-editor)

- Cu TalkBack activ, rândurile Check și Raise au expus aceleași valori, fiecare o singură dată, și au primit focus. Locul Raise a primit focus. La ultima rulare, nodul locului Check nu a mai fost disponibil când proba a cerut focus, iar verdictul a rămas `FAIL`.
- În capturile compacte, badge-ul Check este trunchiat, iar după deschiderea jurnalului contribuția de pe al doilea rând de text rămâne sub marginea ferestrei. Descrierea accesibilă a păstrat contribuția completă.

## Acceptanță rămasă

- După Check la font 2,0, stabilizează focalizarea locului peste schimbarea actorului. La deschiderea jurnalului și la focalizarea unui rând, păstrează ambele linii ale rândului vizibile și textul badge-ului lizibil. Verifică limitele randate după stabilizarea focusului, inclusiv când locul uman se află la baza mesei.
- Pe fereastra scundă, parcurge jurnalul și toate locurile prin gesturi. Verifică nume lungi și ascultă anunțurile în română și engleză. Fiecare loc trebuie să rostească numele, acțiunea, suma și contribuția o singură dată. [Matricea J6-UI-09](J6-UI-09-SEAT-METRIC-HIERARCHY.md)
- Declară profilurile jurnalului în inventarul de release și adaugă în RAG căile exacte pentru editor, proba nativă și proiecția jurnalului. Când se schimbă oricare, contextul trebuie să propună explicit rularea compactă cu TalkBack și să citeze opțiunea de rulare. [Contractul RAG](../release/ATF_Release.md#contextul-rag-consultativ)
