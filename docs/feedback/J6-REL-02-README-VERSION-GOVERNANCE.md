# J6-REL-02 — Legarea README-ului principal de versiunea curentă

**Prioritate:** P2

**Stare:** Rezolvat — versiunea, dosarul și politica README sunt verificate în guvernanță

## Constatare

Constatarea inițială: `ReleaseGovernanceTest` folosea `release/index.json` pentru URL-urile publice, dar nu lega versiunea afișată și dosarul README-ului principal de index în starea `PREPARED`. Astfel, un număr sau un dosar vechi putea rămâne în pagina principală. Guvernanța leagă acum README-ul de candidatul curent și condiționează linkurile autentificate de receipt-ul browserului și de mențiunea explicită de autentificare.

## Criterii

- Pentru `DRAFT`, README-ul principal identifică `currentVersion` și trimite la dosarul versiunii, fără URL de download. Pentru `PREPARED`, permite linkuri exact versionate numai dacă manifestul confirmă o descărcare completă din browser autentificat și pagina spune clar că autentificarea GitHub este necesară.
- Pentru `PUBLISHED`, README-ul principal identifică aceeași versiune și leagă pagina Release și asset-ul exact; dosarul versiunii și `release-validation.json` trebuie să aibă aceeași identitate.
- Cazurile negative schimbă doar versiunea sau destinația README-ului și trebuie să eșueze pentru versiune veche, dosar greșit, APK greșit și linkuri publice premature.
- Controlul rămâne local și deterministic; nu face cereri de rețea. Descărcarea anonimă completă rămâne poarta separată.

## Referințe

- [Testul curent de guvernanță](../../holdem-app/src/test/java/ro/atf/j6/release/ReleaseGovernanceTest.java)
- [Procesul release](../release/ATF_Release.md)

Guvernanța verifică dosarul versiunii curente, respinge dosarul și numărul de versiune vechi, condiționează linkurile `PREPARED` de receipt-ul browserului și verifică lipsa etichetelor SHA/checksum și a digesturilor. Cele trei teste `ReleaseGovernanceTest` au trecut din copia TMP.
