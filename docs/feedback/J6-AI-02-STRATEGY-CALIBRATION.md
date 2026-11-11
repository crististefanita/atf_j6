# J6-AI-02 · Calificarea unei strategii Hard

**Prioritate:** P1

**Stare:** Pornirea unui turneu Hard este verificată pe Android. Componentele verifică restaurarea nivelului Hard. Rămân rotația și reluarea din interfață, plus evaluarea forței de joc.

**Owner:** Politicile și arena Hold’em J6

Estimatorul Monte Carlo eșantionează uniform mâinile necunoscute. Ponderile contextuale schimbă pragul, iar estimatorul nu modelează range-uri individuale sau cota pe straturile potului. Holdout-urile existente dau rezultate mixte în funcție de mărimea mesei și poziție.

În Android, Normal folosește check/call. Hard alocă stiluri distincte, include un adversar Normal la șase locuri și folosește un profil comun pentru sugestii și auto-play. Statisticile de shove se reconstruiesc din acțiunile publice ale mâinilor încheiate, inclusiv după reluare. [Contractul AI](../requirements/AI_REQUIREMENTS.md) · [Evaluarea în arenă](../requirements/TOURNAMENT_RULES.md)

## Reper de evaluare

Comparația pereche schimbă numai profilul candidatului și păstrează bugetul Monte Carlo. Criteriile pentru continuare și mărimile de pariu sunt documentate lângă modelul AI. [Profilul Hard](../requirements/AI_REQUIREMENTS.md#profilul-hard-al-turneului-local) · [Lineup de test](../../config/j6-strategy-lineups-hard-pressure.yaml)

## Criterii de închidere

- [ ] Confirmă textul localizat al selectorului și al meniului principal în română și engleză. Verifică nivelul Hard după rotație și după reluarea unei sesiuni salvate. Probele native existente acoperă selectorul cu Normal implicit și pornirea unui turneu Hard la șase locuri. [Probele Android](../../holdem-android/app/src/androidTest/java/ro/atf/holdem/android/TournamentSanity.java)
- [ ] Joacă turnee Hard din interfață și confirmă că adversarii pot continua, renunța și mări pariul legal. Urmărește timpul de răspuns și schimbarea ecranului în timpul calculului.

- Definiți înainte de evaluare metrica primară, baseline-urile și pragul de promovare. Separați reglajul de holdout. Comparația păstrează seed-urile, pozițiile, nivelurile și adversarii pereche. Raportați incertitudinea pe seed, chips/hand, bb/100 și drawdown.
- Verificați calibrarea range-urilor din informația publică și deciziile în poturi secundare cu stive inegale. Păstrați legalitatea, confidențialitatea, replay-ul și bugetul de calcul.
- [ ] Calibrați separat discountul de call și marjele de raise. Pentru un discount mai mare, cereți un interval pereche de victorii peste zero și verificați rezultatul pe fiecare clasă de stivă. Comparați call-urile raportate la oportunitățile eligibile.
- [ ] Evaluați citirea adversarilor înainte de a o folosi în decizie. Măsurați separat contribuția succesiunii și a poziției, peste un model cu preț și stivă. Verificați acuratețea pe turnee cu seed-uri separate și păstrați distincte fold-ul, raise-ul și equity.
- [ ] Pentru promovarea unui model de răspuns, cereți atât predicții validate, cât și avantaj la victorii într-un lot competitiv independent. Raportați situațiile acoperite, deciziile schimbate și efectul pe fiecare clasă de stivă.
- [ ] Măsurați separat efectul ponderii de fold și al penalizării pentru re-raise, cu numărătorii calibrați fixați. Comparați măririle în oportunități echivalente și fixați grupa agresivă înainte de calificare. Când evaluați simultan mai mulți candidați, ajustați intervalele pentru comparațiile planificate.
- [ ] Verificați acoperirea în situațiile eligibile înainte de a calibra din nou ponderile. Raportați separat estimările de fold care coboară pragul, cele care îl ridică și cele care se rotunjesc la zero. Urmăriți primele schimbări de acțiune pe stare identică.
- [ ] Verificați dimensionarea prin EV și inițiativa în loturi distincte. Comparați reacția imediată la două sume și la call față de primul raise. Calificați separat rezultatul competitiv și equity pe range-ul care plătește.
- [ ] La calificarea unui re-raise redus, măsurați acoperirea pe stivă efectivă scurtă și pe seed-uri distincte. Verificați separat ofertele care lasă jetoane unui adversar acoperit de control. Raportați atât probabilitățile reacțiilor și valoarea până la decontare, cât și victoriile pe un lot independent. [Contractul de evaluare](../requirements/AI_REQUIREMENTS.md#re-raise-mai-mic-evaluat-până-la-decontare)
- [ ] Pentru fiecare bandă de stivă efectivă, cereți suport pe seed-uri naturale și raportați frecvențele de reacție și equity separat. Calificați independent situațiile acoperite și cele în care adversarul poate mări. Folosiți controlul cu equity uniformă și aceleași probabilități de reacție pentru a izola contribuția range-ului. [Contractul range-urilor](../requirements/AI_REQUIREMENTS.md#adversari-care-decid-după-forța-mâinii)
- [ ] Pentru calibrarea reacției, comparați regimul agregat cu prețul și stilul public. Raportați separat celulele fără istoric și cereți acoperire naturală înainte de a folosi probele de acoperire la alegerea sumei. [Contractul probabilităților](../requirements/AI_REQUIREMENTS.md#calibrarea-reacției-după-preț-și-stil-la-stivă-scurtă)
- [ ] Pentru un cititor de tendințe, separați îmbunătățirea predicției de îmbunătățirea jocului. Cereți suport după schimbarea stilului și verificați că avantajul se păstrează când rolurile își schimbă locul și când stiva inițială crește. [Laboratorul cu distribuții duplicate](../requirements/AI_REQUIREMENTS.md#laboratorul-cu-distribuții-duplicate-și-stiluri-schimbătoare)
- [ ] Măsurați frecvența și efectul mărimii de raise după limp din blindul mare. Separați heads-up de multiway, păstrați pozițiile și bugetul de calcul în perechi și comparați turneele standard cu stivele adânci. [J6-AI-04](J6-AI-04-PUBLIC-SHOVE-ABLATION.md) urmărește separat contribuția istoricului de shove.
