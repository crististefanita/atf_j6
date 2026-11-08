# Feedback Hold’em

## Pe scurt

Indexul urmărește lacunele verificabile ale J6. Cerințele stabile apar în documentele owner, iar rezultatele unei ediții rămân în catalogul ei. [Cerințe](../requirements/README.md) · [Strategie](../testing/TEST_STRATEGY.md) · [Ediția 0.2.2](../../release/0.2.2/README.md)

## Priorități active

| Prioritate | Subiect | Dovada care lipsește |
|---|---|---|
| P1 | [J6-REL-01 · Vizibilitate publică](J6-REL-01-PUBLIC-RELEASE-VISIBILITY.md) | Descărcare anonimă completă. Linkul din README a trecut în browserul autentificat. |
| P1 | [J6-REL-05 · RAG](J6-REL-05-RELEASE-RAG-GOVERNANCE.md) | Probe pentru contradicții și feedback depășit, cu dovezi citate. |
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

- [Guvernanța README](J6-REL-02-README-VERSION-GOVERNANCE.md) și [oracolul Extent](J6-REL-04-EXTENT-SUITE-COVERAGE.md)
- [Poziția târzie](J6-AI-01-LATE-POSITION.md) și [argumentul cohortelor](J6-AI-03-COHORT-ARGUMENT.md)
- [Masa interactivă](J6-UI-01-INTERACTIVE-TABLE.md), [matricea compactă](J6-UI-02-TABLE-COVERAGE.md) și [sanity-ul de turneu](J6-UI-03-TOURNAMENT-SANITY.md)
- [Call după închiderea editorului Raise](J6-UI-10-BETTING-ACTION-LEDGER.md#verificat-în-030)
- [Continuarea decontării](J6-UI-06-RESUMABLE-TURNS-AND-SETTLEMENT.md) și [clasamentul local](J6-UI-08-TOURNAMENT-LEADERBOARD.md)

</details>
