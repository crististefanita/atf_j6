# J6-REL-02 — Formatul comun și guvernanța README-ului de release

**Prioritate:** P2

**Stare:** Rezolvat — README principal minimal, versiunea și rutele au guvernanță negativă

## Constatare

Constatarea inițială: blocul README principal repeta starea `PREPARED`, cerința de autentificare și alte destinații lângă linkurile de download. Informațiile despre starea ediției erau duplicate în dosarul versiunii, iar guvernanța cerea această redundanță. Rezultatul încălca formatul minimalist cerut pentru pagina principală și permitea detalii accidentale în același bloc.

## Criterii

- README-ul principal folosește `## Jocul` cu exact două bullets: documentația paginii Release, apoi APK-ul direct. Numele și URL-urile folosesc `currentVersion`; blocul nu conține stare, notă de autentificare, dimensiune, checksum/hash, dosar local sau alte linkuri. Catalogul și procesul rămân în secțiunea de documentație.
- `DRAFT` nu oferă APK. `PREPARED` expune cele două linkuri numai când dosarul are dovada descărcării autentificate complete; condiția nu se repetă în README-ul principal. `PUBLISHED` cere separat descărcare anonimă completă. Dosarul versiunii și `release-validation.json` trebuie să aibă aceeași identitate.
- Guvernanța respinge versiunea veche, ordinea greșită, al treilea link, o a treia destinație, mărimea fișierului, textul lifecycle/autentificare, destinația greșită, checksum/hash, linkurile documentației lipsă și rutele premature.
- Orice RAG ulterior este consultativ și read-only; doar catalogul și receipt-urile pot susține starea porților. Recuperarea de text nu poate deduce `PASS` sau promova release-ul.
- Controlul rămâne local și deterministic; nu face cereri de rețea. Descărcarea anonimă completă rămâne poarta separată.

## Referințe

- [Testul curent de guvernanță](../../holdem-app/src/test/java/ro/atf/j6/release/ReleaseGovernanceTest.java)
- [Procesul release](../release/ATF_Release.md)

Guvernanța verifică dosarul și formatul versiunii curente, linkurile documentației și existența țintelor lor, ordinea și exactitatea celor două rute, condițiile `PREPARED`/`PUBLISHED`, cazurile negative și lipsa detaliilor tehnice în blocul principal. Teste țintite inițiale: `ReleaseGovernanceTest`, 3/3 trecute în copia TMP; rapoartele Extent au trecut `ReportArtifactInspector`. La auditul de release din 2026-11-08 a fost găsit separat crash-ul API 28 din recomandarea AI; remedierea și noul gate de compatibilitate sunt urmărite în [J6-REL-03](J6-REL-03-ANDROID-MIN-SDK-API-COMPATIBILITY.md).
