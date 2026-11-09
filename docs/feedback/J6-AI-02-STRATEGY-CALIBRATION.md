# J6-AI-02 · Calificarea unei strategii Hard

**Prioritate:** P1

**Stare:** Profil selectabil în cod. Forța de joc și acceptanța nativă rămân deschise.

**Owner:** Politicile și arena Hold’em J6

Estimatorul Monte Carlo eșantionează uniform mâinile necunoscute. Ponderile contextuale schimbă pragul, iar estimatorul nu modelează range-uri individuale sau cota pe straturile potului. Holdout-urile existente dau rezultate mixte în funcție de mărimea mesei și poziție.

În Android, Normal păstrează adversarii check/call. Hard este o alegere explicită pentru turneele locale și folosește profilul de presiune și pentru coach și preluare. Statisticile de shove se reconstruiesc din acțiunile publice ale mâinilor încheiate, inclusiv după reluare. [Contractul AI](../requirements/AI_REQUIREMENTS.md) · [Evaluarea în arenă](../requirements/TOURNAMENT_RULES.md)

## Reper de evaluare

Comparația pereche schimbă numai profilul candidatului și păstrează bugetul Monte Carlo. Criteriile pentru continuare și mărimile de pariu sunt documentate lângă modelul AI. [Profilul Hard](../requirements/AI_REQUIREMENTS.md#profilul-hard-al-turneului-local) · [Lineup de test](../../config/j6-strategy-lineups-hard-pressure.yaml)

## Criterii de închidere

- [ ] Verifică nativ selectorul „Nivelul adversarilor” în română și engleză, opțiunile Normal și Hard, Normal la prima deschidere, Hard după rotație și Hard după salvare și reluare. Verifică și textul de pe ecranul principal, care prezintă jocul offline și alegerea nivelului.
- [ ] Joacă turnee Hard din interfață și confirmă că adversarii pot continua, renunța și mări pariul legal. Urmărește timpul de răspuns și schimbarea ecranului în timpul calculului.

- Definiți înainte de evaluare metrica primară, baseline-urile și pragul de promovare. Separați reglajul de holdout. Comparația păstrează seed-urile, pozițiile, nivelurile și adversarii pereche. Raportați incertitudinea pe seed, chips/hand, bb/100 și drawdown.
- Verificați calibrarea range-urilor din informația publică și deciziile în poturi secundare cu stive inegale. Păstrați legalitatea, confidențialitatea, replay-ul și bugetul de calcul.
- [ ] Calibrați separat discountul de call și marjele de raise. Pentru un discount mai mare, cereți un interval pereche de victorii peste zero și verificați rezultatul pe fiecare clasă de stivă. Comparați call-urile raportate la oportunitățile eligibile.
- [ ] Evaluați citirea adversarilor înainte de a o folosi în decizie. Măsurați separat contribuția succesiunii și a poziției, peste un model cu preț și stivă. Verificați acuratețea pe turnee cu seed-uri separate și păstrați distincte fold-ul, raise-ul și equity.
- [ ] Pentru promovarea unui model de răspuns, cereți atât predicții validate, cât și avantaj la victorii într-un lot competitiv independent. Raportați situațiile acoperite, deciziile schimbate și efectul pe fiecare clasă de stivă.
- [ ] Măsurați separat efectul ponderii de fold și al penalizării pentru re-raise, cu numărătorii calibrați fixați. Comparați măririle în oportunități echivalente și fixați grupa agresivă înainte de calificare. Când evaluați simultan mai mulți candidați, ajustați intervalele pentru comparațiile planificate.
- [ ] Verificați acoperirea în situațiile eligibile înainte de a calibra din nou ponderile. Raportați separat estimările de fold care coboară pragul, cele care îl ridică și cele care se rotunjesc la zero. Urmăriți primele schimbări de acțiune pe stare identică.
- [ ] Verificați dimensionarea prin EV și inițiativa în loturi distincte. Comparați reacția imediată la două sume și la call față de primul raise. Calificați separat rezultatul competitiv și equity pe range-ul care plătește.
- [ ] Izolați expunerea stivelor scurte când valoarea imediată favorizează suma mare. Comparați oferta de control cu sume mai apropiate și măsurați atât efectul asupra fold-urilor, cât și rezultatul pe turnee noi.
- [ ] Măsurați frecvența și efectul mărimii de raise după limp din blindul mare. Separați heads-up de multiway, păstrați pozițiile și bugetul de calcul în perechi și comparați turneele standard cu stivele adânci. [J6-AI-04](J6-AI-04-PUBLIC-SHOVE-ABLATION.md) urmărește separat contribuția istoricului de shove.
