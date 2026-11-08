# J6-UI-04 · Separarea potului câștigat de restituiri

**Prioritate:** P2

**Stare:** Implementare parțială — motorul și modelul locului separă premiul de restituire; E2E și acceptanța vizuală sunt restante

În vechea prezentare, `Received` însuma premiul și restituirea mizei neacoperite. Modelul curent expune `potWon` și `returned` separat, iar cadrul locului afișează `Won` și `Returned`. Totalul contabil rămâne disponibil în decontarea motorului; markerul animat folosește numai premiul potului.

## Criterii

- Expuneți din decontarea motorului premiile poturilor și restituirile mizelor neacoperite ca sume distincte; păstrați totalul de audit pentru conservarea jetoanelor.
- Afișați etichete distincte pentru `pot câștigat` și `miză restituită`, în română și engleză. Nu deduceți premiul din schimbarea stivei.
- Componenta verifică all-in neacoperit, side pot și pot împărțit, inclusiv după restaurare. Valorile afișate trebuie să reconcilieze contribuțiile, premiile, restituirile și stiva finală.
- Sanity-ul existent verifică o singură prezentare nativă a cazului neacoperit și păstrează capturile în Extent. Traseul deja ajunge la mâna potrivită. [Sanity de turneu](J6-UI-03-TOURNAMENT-SANITY.md)
