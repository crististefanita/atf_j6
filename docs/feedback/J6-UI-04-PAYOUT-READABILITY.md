# J6-UI-04 · Reconcilierea premiului și a restituirii

**Prioritate:** P2

**Stare:** Motorul și proiecția au probe separate, traseul restaurat rămâne deschis

**Owner:** Proiecția sumelor Android J6

Motorul testează potul principal, poturile secundare și restituirea contribuției neacoperite. Testul de prezentare exclude un loc cu numai restituire dintre destinațiile premiului. Sanity-ul de turneu calificat verifică plăți reprezentative și păstrarea cărților. El nu asertează valorile distincte după restaurarea unui caz cu poturi secundare. [Teste Core](../../holdem-core/src/test/java/ro/atf/holdem/core/HoldemRulesTest.java) · [Sanity](../../holdem-android/app/src/androidTest/java/ro/atf/holdem/android/TournamentSanity.java)

<<<<<<< Updated upstream
- Expuneți din decontarea motorului premiile poturilor și restituirile mizelor neacoperite ca sume distincte; păstrați totalul de audit pentru conservarea jetoanelor.
- Afișați etichete distincte pentru `pot câștigat` și `miză restituită`, în română și engleză. Nu deduceți premiul din schimbarea stivei.
- Componenta verifică all-in neacoperit, side pot și pot împărțit, inclusiv după restaurare. Valorile afișate trebuie să reconcilieze contribuțiile, premiile, restituirile și stiva finală.
- Rapoartele 0.2.2 confirmă decontarea obișnuită și lizibilitatea sumelor. Rămâne o probă Android în care același loc primește un premiu și o restituire nenulă.
- Reutilizați aceeași captură pentru destinația efectului din J6-UI-07. [Dovezile 0.2.2](../../release/0.2.2/reports/README.md) · [Efectul la showdown](J6-UI-07-SHOWDOWN-WIN-CLARITY.md)
=======
## Criterii de închidere

- Folosiți un fixture determinist cu split sau side-pot și miză neacoperită. Comparați suma câștigată și suma restituită per loc cu decontarea motorului, inclusiv după checkpoint. Reconciliați contribuțiile și stiva finală.
- Verificați etichetele `Won` și `Returned` în română și engleză. Locul cu numai restituire păstrează suma și nu primește marcaj de câștig.
- Reutilizați același fixture pentru [J6-UI-07](J6-UI-07-SHOWDOWN-WIN-CLARITY.md), care deține selecția cărților și destinația semnalului vizual. [J6-UI-09](J6-UI-09-SEAT-METRIC-HIERARCHY.md) deține încadrarea și anunțurile de accesibilitate.
>>>>>>> Stashed changes
