# Feedback Hold’em

## Pe scurt

<<<<<<< Updated upstream
Indexul urmărește lacunele verificabile ale J6. Cerințele stabile apar în documentele owner, iar rezultatele unei ediții rămân în catalogul ei. [Cerințe](../requirements/README.md) · [Strategie](../testing/TEST_STRATEGY.md) · [Ediția 0.3.0](../../release/0.3.0/README.md)

## Priorități active

| Prioritate | Subiect | Dovada care lipsește |
|---|---|---|
| P1 | [J6-REL-01 · Vizibilitate publică](J6-REL-01-PUBLIC-RELEASE-VISIBILITY.md) | Descărcare anonimă completă. Linkul din README a trecut în browserul autentificat. |
| P1 | [J6-AI-02 · Calibrare](J6-AI-02-STRATEGY-CALIBRATION.md) | Evaluare independentă și model de range înainte de promovarea stilului Hard. |
| P1 | [J6-AI-04 · Ablație shove](J6-AI-04-PUBLIC-SHOVE-ABLATION.md) | Efect repetat cu minimum 20 de seed-uri independente pe configurație. |
| P1 | [J6-UI-05 · Coach](J6-UI-05-LOCAL-COACH-AND-POSTGAME-REVIEW.md) | Oracol pentru cele cinci cărți decisive și legătura dintre selectorul de blinduri și sesiunea salvată. |
| P1 | [J6-UI-07 · Showdown](J6-UI-07-SHOWDOWN-WIN-CLARITY.md) | Dovada nativă a destinației animației, împreună cu cazul de restituire din J6-UI-04. |
| P1 | [J6-UI-10 · Istoricul pariurilor](J6-UI-10-BETTING-ACTION-LEDGER.md) | Raise aplicat și verificarea badge-ului și a istoricului în viewport scund, cu TalkBack. Revenirea la Call a fost verificată. |
| P2 | [J6-UI-04 · Decontare](J6-UI-04-PAYOUT-READABILITY.md) | Același loc afișează separat premiul și o restituire nenulă într-o probă Android. |
| P2 | [J6-UI-09 · Lizibilitate](J6-UI-09-SEAT-METRIC-HIERARCHY.md) | Anunțurile audio și parcurgerea tuturor locurilor prin gesturi. |
| P2 | [J6-REL-03 · API minim](J6-REL-03-ANDROID-MIN-SDK-API-COMPATIBILITY.md) | Instalare și proba de recomandare AI pe API 26. |

<details>
<summary>Constatări închise</summary>

- [Contextul RAG și integritatea referințelor din feedback](J6-REL-05-RELEASE-RAG-GOVERNANCE.md)
- [Guvernanța README](J6-REL-02-README-VERSION-GOVERNANCE.md) și [oracolul Extent](J6-REL-04-EXTENT-SUITE-COVERAGE.md)
- [Poziția târzie](J6-AI-01-LATE-POSITION.md) și [argumentul cohortelor](J6-AI-03-COHORT-ARGUMENT.md)
- [Masa interactivă](J6-UI-01-INTERACTIVE-TABLE.md), [matricea compactă](J6-UI-02-TABLE-COVERAGE.md) și [sanity-ul de turneu](J6-UI-03-TOURNAMENT-SANITY.md)
- [Call după închiderea editorului Raise](J6-UI-10-BETTING-ACTION-LEDGER.md#verificat-în-030)
- [Continuarea decontării](J6-UI-06-RESUMABLE-TURNS-AND-SETTLEMENT.md) și [clasamentul local](J6-UI-08-TOURNAMENT-LEADERBOARD.md)

</details>
=======
Lista urmărește lacunele care mai cer o decizie, o corecție sau o probă. Contractele stabile stau la owner, iar rezultatele de calificare în dosarul ediției.

Catalogul din checkout-ul `main` indică 0.1.1 `PREPARED`. Referința locală `origin/main` indică 0.2.1 `PREPARED`, iar dosarul din worktree-ul de release indică 0.2.2 `PREPARED`. Pentru decizii de publicare, folosiți indexul și receipt-urile checkout-ului vizat.

## Priorități active

| Prioritate | ToDo | Criteriul care lipsește |
|---|---|---|
| P1 | [J6-REL-01 · Descărcare anonimă](J6-REL-01-PUBLIC-RELEASE-VISIBILITY.md) | Descărcare completă pe ruta canonică, cu receipt anonim |
| P1 | [J6-UI-01 · Masa activă 0.2.1](J6-UI-01-INTERACTIVE-TABLE.md) | Politica de afișare aprobată, cerințe și probe de atingere aliniate |
| P1 | [J6-UI-05 · Coach local](J6-UI-05-LOCAL-COACH-AND-POSTGAME-REVIEW.md) | Probe pentru confidențialitate, oprirea preluării și intervalul de blinduri |
| P1 | [J6-UI-06 · Decontare și reluare](J6-UI-06-RESUMABLE-TURNS-AND-SETTLEMENT.md) | Reconcilierea cu `main` a comportamentului calificat pe ramura de release |
| P1 | [J6-UI-07 · Showdown](J6-UI-07-SHOWDOWN-WIN-CLARITY.md) | Cărțile decisive și beneficiarii sunt identificați exact în UI |
| P1 | [J6-UI-10 · Istoricul pariurilor](J6-UI-10-BETTING-ACTION-LEDGER.md) | Traseul nativ validează un raise aplicat și totalul după call |
| P1 | [J6-REL-05 · RAG de release](J6-REL-05-RELEASE-RAG-GOVERNANCE.md) | Impactul ștergerilor, contradicții și context verificabil prin hash |
| P1 | [J6-AI-02 · Calibrare](J6-AI-02-STRATEGY-CALIBRATION.md) | Evaluare independentă înainte de promovarea unei strategii |
| P1 | [J6-AI-04 · Ablație shove](J6-AI-04-PUBLIC-SHOVE-ABLATION.md) | Efect repetat și minimum 20 de seed-uri independente pe configurație |
| P2 | [J6-UI-04 · Decontare](J6-UI-04-PAYOUT-READABILITY.md) | Valorile premiu și restituire rămân distincte, inclusiv după restaurare |
| P2 | [J6-UI-08 · Clasament](J6-UI-08-TOURNAMENT-LEADERBOARD.md) | Reconcilierea înregistrării înainte de schimbarea checkpoint-ului și a numelui după ID stabil cu `main` |
| P2 | [J6-UI-09 · Lizibilitate și accesibilitate](J6-UI-09-SEAT-METRIC-HIERARCHY.md) | Cazul compact verificat. Rămân proba audio și navigarea integrală prin gesturi. |
| P2 | [J6-REL-03 · API minim Android](J6-REL-03-ANDROID-MIN-SDK-API-COMPATIBILITY.md) | Probă nativă la API 26 pentru codul expediat |

[Acceptanța raportului nativ](../testing/TEST_STRATEGY.md#acceptanța-raportului-nativ) deține contractul livrat pentru `J6-REL-04`.

## Documente owner

- [Interfața Android](../../holdem-android/README.md) și [cerințele produsului](../requirements/PRODUCT_REQUIREMENTS.md)
- [Regulile turneului](../requirements/TOURNAMENT_RULES.md) și [evaluarea AI](../requirements/AI_REQUIREMENTS.md)
- [Strategia de testare](../testing/TEST_STRATEGY.md) și [procesul release](../release/ATF_Release.md)
>>>>>>> Stashed changes
