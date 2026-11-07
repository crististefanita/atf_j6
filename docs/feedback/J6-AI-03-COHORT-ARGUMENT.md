# J6-AI-03 — Runner-ul trebuie să respecte stilul din cohorta pereche

**Prioritate:** P1  
**Stare:** Rezolvat

## Constatare

La extinderea CLI-ului cu un YAML explicit pentru reglaj, parsarea selecta stilul numai când existau exact cinci argumente. Apelul valid cu șase argumente cădea înapoi la `mixed`, astfel încât brațele candidat și control puteau rula aceeași strategie și producea o comparație falsă.

## Remediere

Parsarea acceptă acum stilul din poziția argumentului cinci atât cu, cât și fără YAML-ul explicit. Testul de componentă verifică formele CLI cu patru, cinci și șase argumente și verifică politica efectivă din locul candidat.

## Criterii verificate

- Rularea pereche nouă raportează versiunile candidate așteptate, iar controalele folosesc TAG.
- Jurnalele pentru ambele brațe se auditează independent înainte de comparație.
- Comparația verifică seed-urile, mărimile meselor, stivele la fiecare mână, identitatea câștigătorului și diferențele deciziilor candidate.
- Primul lot produs cu parser-ul defect este păstrat, etichetat `INVALID-RUN.md` în directorul extern și exclus din orice concluzie.
