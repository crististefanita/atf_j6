# J6-UI-04 · Separarea potului câștigat de restituiri

În finala sanity-ului, jucătorul are 7.920 de jetoane, iar adversarul 80. După all-in, masa afișează `Pot paid · 8000` și `Received 8000`. Totalul este corect contabil, dar jucătorul a câștigat un pot de 160 și a primit înapoi 7.840 de jetoane din miza neacoperită. Stiva a crescut cu 80.

## Criterii

- Expuneți din decontarea motorului sumele publice pentru premii și restituiri, separat. Păstrați totalul folosit în auditul jetoanelor.
- Folosiți etichete distincte pe masă și în raport. Acoperiți prin componente un all-in neacoperit, un side pot și o împărțire, cu aceleași valori după restaurare.
- În sanity-ul existent, verificați o singură prezentare nativă a cazului neacoperit. Traseul deja ajunge la mâna potrivită. [Sanity de turneu](J6-UI-03-TOURNAMENT-SANITY.md)
