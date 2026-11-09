# Feedback Hold’em

## Pe scurt

Indexul urmărește lacunele verificabile ale J6. Cerințele stabile apar în documentele owner, iar rezultatele unei ediții rămân în catalogul ei. [Cerințe](../requirements/README.md) · [Strategie](../testing/TEST_STRATEGY.md) · [Catalogul edițiilor](../../release/README.md)

## Priorități active

| Prioritate | Subiect | Dovada care lipsește |
|---|---|---|
| P1 | [J6-UI-07 · Showdown](J6-UI-07-SHOWDOWN-WIN-CLARITY.md#dovezi-rămase) | Identitatea cărților decisive, descrierile accesibile și lizibilitatea marcajelor în profil compact. |
| P1 · extern | [J6-REL-01 · Vizibilitate publică](J6-REL-01-PUBLIC-RELEASE-VISIBILITY.md) | Verificarea accesului anonim și a integrității ambelor fișiere. Starea ediției este în manifest. |
| P1 | [J6-AI-02 · Calibrare](J6-AI-02-STRATEGY-CALIBRATION.md) | Evaluare independentă și model de range înainte de promovarea stilului Hard. |
| P1 | [J6-AI-04 · Ablație shove](J6-AI-04-PUBLIC-SHOVE-ABLATION.md) | Efect repetat cu minimum 20 de seed-uri independente pe configurație. |
| P1 | [J6-UI-05 · Coach](J6-UI-05-LOCAL-COACH-AND-POSTGAME-REVIEW.md) | Oracol pentru combinație, invarianta la cărți ascunse și traseul nativ de la selectorul de blinduri la sesiunea restaurată. |
| P1 | [J6-UI-10 · Istoricul pariurilor](J6-UI-10-BETTING-ACTION-LEDGER.md) | Raise aplicat și corelat cu jurnalul pe traseul curent. În fereastra scundă rămân verificările vizuale și audio. |
| P2 | [J6-UI-09 · Lizibilitate](J6-UI-09-SEAT-METRIC-HIERARCHY.md) | Anunțurile audio și parcurgerea tuturor locurilor prin gesturi. |
| P2 | [J6-REL-03 · API minim](J6-REL-03-ANDROID-MIN-SDK-API-COMPATIBILITY.md) | Instalare și proba de recomandare AI pe API 26. |

## Contracte și dovezi

- [Masa, turneul și reluarea](../../holdem-android/README.md) · [Clasamentul](../requirements/PRODUCT_REQUIREMENTS.md#clasamentul-turneelor)
- [Probe Android și acceptanța Extent](../testing/TEST_STRATEGY.md#interfața-android-locală) · [Catalogul edițiilor](../../release/README.md)
- [Guvernanța release-ului și contextul RAG](../release/ATF_Release.md) · [Poarta de promovare](../release/ATF_Release.md#promovare-și-descărcare) · [Evaluarea AI](../requirements/AI_REQUIREMENTS.md)
