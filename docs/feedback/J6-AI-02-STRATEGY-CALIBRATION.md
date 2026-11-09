# J6-AI-02 · Calificarea unei strategii Hard

**Prioritate:** P1

**Stare:** Candidat de laborator, evaluare independentă restantă

**Owner:** Politicile și arena Hold’em J6

Estimatorul Monte Carlo eșantionează uniform mâinile necunoscute. Ponderile contextuale schimbă pragul, iar estimatorul nu modelează range-uri individuale sau cota pe straturile potului. Holdout-urile existente dau rezultate mixte în funcție de mărimea mesei și poziție.

În Android, adversarii rămân pe check/call, iar politica contextuală servește coach-ul și preluarea. [Contractul AI](../requirements/AI_REQUIREMENTS.md) · [Evaluarea în arenă](../requirements/TOURNAMENT_RULES.md)

## Candidat Hard pentru arenă

- Pentru o continuare preflop de cel mult două blinduri mari, profilul reduce marja cu cel mult trei puncte procentuale. Pragul rămâne legat de prețul apelului și de incertitudinea estimării. Un all-in advers sau riscul de eliminare păstrează pragul de control.
- La deschiderea pariului heads-up, turnul poate folosi 1× sau 2× pot, iar riverul poate folosi și 3×. Pragurile folosesc limita inferioară a cotei estimate și categoria mâinii. Mărimea trebuie să intre în intervalul legal și să fie acoperită de adversarul rămas. Politica coboară la treapta inferioară care trece aceleași condiții.
- Comparația schimbă doar profilul candidatului și păstrează bugetul Monte Carlo. Configurația definește parametrii de evaluat, iar replay-ul și loturile de control stabilesc efectul lor. [Ponderi Hard](../../config/policy-tuning/j6-policy-hard-pressure.yaml) · [Linia de test](../../config/j6-strategy-lineups-hard-pressure.yaml)

Mărimile respectă limitele No-Limit. Analizele de solver motivează testarea overbetului în situații cu avantaj de valoare. Pragurile locale rămân ipoteze de evaluat în turneu. [Reguli No-Limit](https://www.pokerstars.com/poker/games/texas-holdem/) · [Analiză a overbetului](https://blog.gtowizard.com/overbetting-the-flop-in-cash-games/)

## Criterii de închidere

- Definiți înainte de evaluare metrica primară, baseline-urile și pragul de promovare. Separați reglajul de holdout. Comparația păstrează seed-urile, pozițiile, nivelurile și adversarii pereche. Raportați incertitudinea pe seed, chips/hand, bb/100 și drawdown.
- Verificați calibrarea range-urilor din informația publică și deciziile în poturi secundare cu stive inegale. Păstrați legalitatea, confidențialitatea, replay-ul și bugetul de calcul.
- Promovați un nivel Android doar cu dovezi pentru politica instalată efectiv ca adversar local. [J6-AI-04](J6-AI-04-PUBLIC-SHOVE-ABLATION.md) urmărește separat contribuția istoricului de shove.
