# J6-UI-04 · Separarea potului câștigat de restituiri

**Prioritate:** P2

**Stare:** Implementare parțială — motorul și modelul locului separă premiul de restituire; E2E și acceptanța vizuală sunt restante

În vechea prezentare, `Received` însuma premiul și restituirea mizei neacoperite. Modelul curent expune `potWon` și `returned` separat, iar cadrul locului afișează `Won` și `Returned`. Totalul contabil rămâne disponibil în decontarea motorului; markerul animat folosește numai premiul potului.

## Criterii

- Expuneți din decontarea motorului premiile poturilor și restituirile mizelor neacoperite ca sume distincte; păstrați totalul de audit pentru conservarea jetoanelor.
- Afișați etichete distincte pentru `pot câștigat` și `miză restituită`, în română și engleză. Nu deduceți premiul din schimbarea stivei.
- Componenta verifică all-in neacoperit, side pot și pot împărțit, inclusiv după restaurare. Valorile afișate trebuie să reconcilieze contribuțiile, premiile, restituirile și stiva finală.
- Rapoartele 0.2.2 confirmă decontarea obișnuită și lizibilitatea sumelor. Rămâne o probă Android în care același loc primește un premiu și o restituire nenulă.
- Reutilizați aceeași captură pentru destinația efectului din J6-UI-07. [Dovezile 0.2.2](../../release/0.2.2/reports/README.md) · [Efectul la showdown](J6-UI-07-SHOWDOWN-WIN-CLARITY.md)
