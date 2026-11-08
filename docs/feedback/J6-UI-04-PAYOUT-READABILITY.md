# J6-UI-04 · Separarea potului câștigat de restituiri

**Prioritate:** P2

**Stare:** Deschis — afișarea actuală confundă premiul cu returnarea mizei

În finala sanity-ului, jucătorul are 7.920 de jetoane, iar adversarul 80. După all-in, masa afișează `Pot paid · 8000` și `Received 8000`. Totalul este corect contabil, dar jucătorul a câștigat un pot de 160 și a primit înapoi 7.840 de jetoane din miza neacoperită. Stiva a crescut cu 80.

## Criterii

- Expuneți din decontarea motorului premiile poturilor și restituirile mizelor neacoperite ca sume distincte; păstrați totalul de audit pentru conservarea jetoanelor.
- Afișați etichete distincte pentru `pot câștigat` și `miză restituită`, în română și engleză. Nu deduceți premiul din schimbarea stivei.
- Componenta verifică all-in neacoperit, side pot și pot împărțit, inclusiv după restaurare. Valorile afișate trebuie să reconcilieze contribuțiile, premiile, restituirile și stiva finală.
- Sanity-ul existent verifică o singură prezentare nativă a cazului neacoperit și păstrează capturile în Extent. Traseul deja ajunge la mâna potrivită. [Sanity de turneu](J6-UI-03-TOURNAMENT-SANITY.md)
