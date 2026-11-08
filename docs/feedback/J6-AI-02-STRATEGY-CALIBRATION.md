# J6-AI-02 · Calificarea unei strategii Hard

**Prioritate:** P1

**Stare:** Candidat de laborator, evaluare independentă restantă

**Owner:** Politicile și arena Hold’em J6

Estimatorul Monte Carlo eșantionează uniform mâinile necunoscute. Ponderile contextuale schimbă pragul, iar estimatorul nu modelează range-uri individuale sau cota pe straturile potului. Holdout-urile existente dau rezultate mixte în funcție de mărimea mesei și poziție. În Android, adversarii rămân pe check/call, iar politica contextuală servește coach-ul și preluarea. [Contractul AI](../requirements/AI_REQUIREMENTS.md) · [Evaluarea în arenă](../requirements/TOURNAMENT_RULES.md)

## Criterii de închidere

- Definiți înainte de evaluare metrica primară, baseline-urile și pragul de promovare. Separați reglajul de holdout. Comparația păstrează seed-urile, pozițiile, nivelurile și adversarii pereche. Raportați incertitudinea pe seed, chips/hand, bb/100 și drawdown.
- Verificați calibrarea range-urilor din informația publică și deciziile în poturi secundare cu stive inegale. Păstrați legalitatea, confidențialitatea, replay-ul și bugetul de calcul.
- Promovați un nivel Android doar cu dovezi pentru politica instalată efectiv ca adversar local. [J6-AI-04](J6-AI-04-PUBLIC-SHOVE-ABLATION.md) urmărește separat contribuția istoricului de shove.
