# Feedback Hold’em

## Pe scurt

Indexul urmărește lacunele verificabile ale J6. Cerințele stabile apar în documentele owner, iar rezultatele unei ediții rămân în catalogul ei. [Cerințe](../requirements/README.md) · [Strategie](../testing/TEST_STRATEGY.md) · [Catalogul edițiilor](../../release/README.md)

## Priorități active

| Prioritate | Subiect | Dovada care lipsește |
|---|---|---|
| P1 | [J6-UI-07 · Showdown](J6-UI-07-SHOWDOWN-WIN-CLARITY.md#dovezi-rămase) | Alinierea perechilor la cinci și șase locuri, identitatea cărților decisive și direcția markerului de premiu. |
| Extern · la cerere | [J6-REL-01 · Vizibilitate publică](J6-REL-01-PUBLIC-RELEASE-VISIBILITY.md) | Investigație separată de calificarea autentificată a ediției curente. |
| P1 | [J6-AI-02 · Calibrare](J6-AI-02-STRATEGY-CALIBRATION.md) | Selectorul și reluarea pe Android, evaluarea independentă și modelul de range pentru Hard. |
| P1 | [J6-AI-04 · Ablație shove](J6-AI-04-PUBLIC-SHOVE-ABLATION.md) | Efect repetat cu minimum 20 de seed-uri independente pe configurație. |
| P1 | [J6-UI-05 · Coach](J6-UI-05-LOCAL-COACH-AND-POSTGAME-REVIEW.md) | Oracol pentru combinație, invarianta la cărți ascunse și traseul nativ de la selectorul de blinduri la sesiunea restaurată. |
| P1 | [J6-UI-10 · Istoricul pariurilor](J6-UI-10-BETTING-ACTION-LEDGER.md) | Confirmarea unui Raise din editorul nativ. Proba de componentă verifică deja jurnalul și replay-ul. Rămân verificările vizuale și audio în fereastra scundă. |
| P2 | [J6-UI-09 · Lizibilitate](J6-UI-09-SEAT-METRIC-HIERARCHY.md) | Anunțurile audio și parcurgerea tuturor locurilor prin gesturi. |
| P2 | [J6-REL-03 · API minim](J6-REL-03-ANDROID-MIN-SDK-API-COMPATIBILITY.md) | Instalare și proba de recomandare AI pe API 26. |
| P2 | [J6-PROD-01 · Realizări de cont](J6-PROD-01-ACCOUNT-ACHIEVEMENTS.md) | Calibrarea dificultății și verificarea progresului în sesiuni reale. |

## Contracte și dovezi

- [Masa, turneul și reluarea](../../holdem-android/README.md) · [Clasamentul](../requirements/PRODUCT_REQUIREMENTS.md#clasamentul-turneelor)
- [Probe Android și acceptanța Extent](../testing/TEST_STRATEGY.md#interfața-android-locală) · [Catalogul edițiilor](../../release/README.md)
- [Guvernanța release-ului și contextul RAG](../release/ATF_Release.md) · [Poarta de promovare](../release/ATF_Release.md#promovare-și-descărcare) · [Evaluarea AI](../requirements/AI_REQUIREMENTS.md)
