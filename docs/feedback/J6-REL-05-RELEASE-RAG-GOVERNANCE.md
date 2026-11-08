# J6-REL-05 — Context de impact release cu proveniență și guvernanță

**Prioritate:** P1  
**Stare:** Verificat pe un candidat curat `DRAFT`. Detectarea conflictelor semantice rămâne deschisă.

## Constatare și rezultat

Catalogul și manifestele dau starea oficială, dar o rundă de schimbări avea nevoie de un mod reproductibil de a găsi feedback, cerințe și suite posibil afectate. Acum runnerul creează în spațiul extern al rulării un context JSON citabil și versionat, iar `ReleaseVersionPolicy` verifică versiunea declarată.

Retrieval-ul este intenționat **determinist, lexical și read-only**; nu folosește embeddinguri, model generativ sau raționament semantic. `CONTEXT_READY` înseamnă numai că a găsit o potrivire suficientă între semnale din diff, cel puțin o sursă citată și o suită din inventar. Nu înseamnă că impactul este complet ori că testele au trecut. Lipsa dovezii produce `NEEDS_EVIDENCE`.

## Corpus și proveniență

Corpusul permis este explicit: Markdown din `docs/feedback`, `docs/requirements`, `docs/testing`, `docs/release` și `docs/architecture`; README-ul principal; catalogul și indexul release; inventarul suitelor; proveniența bibliotecilor J6/Card Table; și README-urile/notițele edițiilor. Rapoartele HTML, capturile, APK-urile, receipt-urile brute, cache-urile, build-urile și directoarele TMP nu sunt surse de retrieval.

Fiecare fragment returnat poartă cale, titlu/heading, linii, hash-ul fișierului și al fragmentului, commit-ul indexat, versiunea candidatului și starea feedback-ului, când există. Hash-urile sunt calculate din sursa originală; fragmentele redactează secrete și căi locale înainte de afișare. Corpusul are digest propriu, iar contextul este scris doar în directorul extern al rulării.

## Porți și versiuni

- Contextul este permis numai pentru HEAD curat, egal cu commit-ul candidat și cu `release/index.json` în `DRAFT`. RAG-ul nu poate modifica indexul, clasa schimbării, receipt-urile sau starea; `gateEffect` este `NONE`, `canDeclareTestPass` este fals, iar `testReceiptState` rămâne `NOT_EVALUATED`.
- Candidatul declară `previousVersion`, `changeClass` și o justificare de cel mult 300 de caractere. `PATCH_FIX` crește patch-ul; `REFACTOR_ONLY` și `COMPATIBLE_FEATURE` cresc minorul; `BREAKING_CHANGE` crește majorul. Runnerul cere ca versiunea precedentă să fie ultima ediție imuabilă și leagă baza de commit-ul din manifestul ei.
- RAG-ul și politica de versiuni au teste componente negative pentru context insuficient, cale de output în repository, versiune inconsistentă, clasă necunoscută și redactarea datelor sensibile. Ele sunt rulate în suita Maven și raportate în Extent-ul componentă.

## Limite rămase

Testele actuale demonstrează citări, proveniență, redactare și abținerea la context slab, dar nu verifică încă un caz cu documente contradictorii, feedback depășit față de cerințe sau link/ancoră eliminată. Clasarea lexicală nu poate decide care sursă are autoritate semantică; reviewer-ul trebuie să verifice citările. Corpusul nu indexează raportul Extent sau receipt-urile, care rămân probe evaluate separat de porțile deterministe.

Runnerul a exercitat integrarea pe candidatul curat 0.2.2. Manifestul păstrează digestul contextului de impact separat de
rezultatele testelor. [Manifestul de calificare](../../release/0.2.2/qualification.json) · [Contextul RAG](../../release/0.2.2/release-impact-context.json)

### Reproducere pe candidatul 0.3.0

În contextul pentru commitul `24e1770`, primele două fragmente provin din `library-provenance.json`, deși fișierul și dependențele nu s-au schimbat. `holdem-android/README.md`, care deține comportamentul nou, lipsește din allowlist. `LocalHoldemGameTest`, care verifică estimarea și vizibilitatea, lipsește dintre cele opt suite sugerate. Porțile independente au rulat suita completă.

Adăugați o probă lexicală cu diff limitat la UI și checkpoint. Ea trebuie să recupereze ghidul Android și suita proprie între primele rezultate. Fragmentele de proveniență trebuie să urce în clasament când diff-ul schimbă o bibliotecă. Păstrați citările și contractul consultativ pentru ambele probe.

## Referințe

- [Retrieval context read-only](../../holdem-app/src/test/java/ro/atf/j6/release/ReleaseImpactRag.java)
- [Teste de retrieval și versionare](../../holdem-app/src/test/java/ro/atf/j6/release/ReleaseImpactRagTest.java)
- [Politica versiunilor](../../holdem-app/src/test/java/ro/atf/j6/release/ReleaseVersionPolicy.java)
- [Runnerul de calificare](../../scripts/Build-HoldemRelease.ps1)
- [Catalogul versiunilor](../../release/README.md)
- [Strategia de testare](../testing/TEST_STRATEGY.md)
- [Referința RAG J5](https://github.com/crististefanita/atf_j5/blob/master/docs/feedback/ATF_Release_RAG.md) — model pentru consultare; corpusul și verdictul rămân proprii J6.
