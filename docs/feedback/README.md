# Feedback Hold’em

## Pe scurt

Indexul urmărește lacunele verificabile ale J6. Cerințele stabile apar în documentele owner, iar rezultatele unei ediții rămân în catalogul ei. [Cerințe](../requirements/README.md) · [Strategie](../testing/TEST_STRATEGY.md) · [Catalogul edițiilor](../../release/README.md)

## Priorități active

| Prioritate | Subiect | Dovada care lipsește |
|---|---|---|
| P1 | [J6-AI-02 · Calibrare](J6-AI-02-STRATEGY-CALIBRATION.md) | Reluarea nivelului Hard din interfață, evaluarea independentă și modelul de range. |
| P1 | [J6-AI-04 · Ablație shove](J6-AI-04-PUBLIC-SHOVE-ABLATION.md) | Efect repetat cu minimum 20 de seed-uri independente pe configurație. |
| P1 | [J6-UI-05 · Coach și Auto-play](J6-UI-05-LOCAL-COACH-AND-POSTGAME-REVIEW.md) | Traseul nativ de la delegare la o decizie confirmată și înapoi la control manual. Oracolul pentru combinație și verificarea reluării din interfață. |
| P1 | [J6-UI-07 · Showdown](J6-UI-07-SHOWDOWN-WIN-CLARITY.md#dovezi-rămase) | Centrarea perechilor în cazuri speciale și identitatea celor cinci cărți decisive. |
| P1 la extindere · P2 | [J6-PROD-01 · Realizări](J6-PROD-01-ACCOUNT-ACHIEVEMENTS.md) | Credit manual dovedit pentru catalogul candidat, poziția listei filtrate pe ecran scund și ritmul în sesiuni manuale. |
| P2 | [J6-UI-09 · Lizibilitate](J6-UI-09-SEAT-METRIC-HIERARCHY.md) | Anunțurile audio și parcurgerea tuturor locurilor prin gesturi. |
| P2 | [J6-REL-03 · API minim](J6-REL-03-ANDROID-MIN-SDK-API-COMPATIBILITY.md) | Instalare și proba de recomandare AI pe API 26. |
| Extern · la cerere | [J6-REL-01 · Vizibilitate publică](J6-REL-01-PUBLIC-RELEASE-VISIBILITY.md) | Investigație separată de calificarea autentificată a ediției curente. |

## Contracte și dovezi

- [Masa, turneul și reluarea](../../holdem-android/README.md) · [Clasamentul](../requirements/PRODUCT_REQUIREMENTS.md#clasamentul-turneelor)
- [Probe Android și acceptanța Extent](../testing/TEST_STRATEGY.md#interfața-android-locală) · [Istoricul pariurilor](J6-UI-10-BETTING-ACTION-LEDGER.md) · [Catalogul edițiilor](../../release/README.md)
- [Guvernanța release-ului și contextul RAG](../release/ATF_Release.md) · [Poarta de promovare](../release/ATF_Release.md#promovare-și-descărcare) · [Evaluarea AI](../requirements/AI_REQUIREMENTS.md)
