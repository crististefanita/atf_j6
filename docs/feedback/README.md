# Feedback Hold’em

## Pe scurt

Indexul urmărește lacunele verificabile ale J6. Cerințele stabile apar în documentele owner, iar rezultatele unei ediții rămân în catalogul ei. [Cerințe](../requirements/README.md) · [Strategie](../testing/TEST_STRATEGY.md) · [Catalogul edițiilor](../../release/README.md)

## Restanțe active, ordonate după impact

Scorul este **prioritatea de mentenanță**, nu nota de calitate, procentul de acoperire sau probabilitatea de eșec. Se calculează din impact (0–40), lipsa dovezilor (0–30), fezabilitate (0–20) și datorie documentară (0–10). Componentele înscrise între paranteze fac suma verificabilă; recalibrați scorul când se schimbă dovada sau costul.

| Ordine | Nivel | Scor /100 | Owner și criteriu de închidere | Restanță concretă |
|---:|---|---:|---|---|
| 1 | P1 | 91 (38+27+17+9) | [J6-AI-02 · Calibrare](J6-AI-02-STRATEGY-CALIBRATION.md) · politici și arena | Verificare nativă a rotației și reluării Hard; apoi evaluare competitivă independentă și calificarea modelelor de range. |
| 2 | P1 | 89 (36+27+18+8) | [J6-UI-05 · Coach și Auto-play](J6-UI-05-LOCAL-COACH-AND-POSTGAME-REVIEW.md) · aplicația Android | Oracol independent pentru combinație, recapitulare între mâini și flux nativ complet cu delegare, control manual și restaurare. |
| 3 | P1 | 86 (36+26+16+8) | [J6-UI-07 · Showdown](J6-UI-07-SHOWDOWN-WIN-CLARITY.md) · prezentarea mesei | Centrare orizontală la cinci și șase locuri, aliniere verticală în cazurile rămase, oracol pentru cele cinci cărți decisive și lizibilitate pe profil compact. |
| 4 | P1 | 84 (34+26+16+8) | [J6-AI-04 · Ablație shove](J6-AI-04-PUBLIC-SHOVE-ABLATION.md) · evaluarea strategiilor | Confirmarea efectului pe minimum 20 de seed-uri independente pentru fiecare mărime de masă, cu intervale și oportunități eligibile. |
| 5 | P2 | 75 (31+20+17+7) | [J6-PROD-01 · Realizări](J6-PROD-01-ACCOUNT-ACHIEVEMENTS.md) · produs și acceptanță | Restaurarea poziției filtrate, contrastul cu font mărit și claritatea progresului în sesiuni manuale. |
| 6 | P2 | 72 (27+25+12+8) | [J6-REL-03 · API minim](J6-REL-03-ANDROID-MIN-SDK-API-COMPATIBILITY.md) · calificarea Android | Instalare pe API 26, pornire, navigare, recomandare AI și restaurarea unei sesiuni pe versiunea minimă. |
| 7 | P2 | 68 (24+22+14+8) | [J6-UI-09 · Lizibilitate](J6-UI-09-SEAT-METRIC-HIERARCHY.md) · accesibilitate Android | Parcurgerea tuturor locurilor cu TalkBack, anunțurile RO/EN și informațiile corecte în timpul mâinii și la decontare. |
| 8 | P2 | 60 (22+14+17+7) | [J6-UI-11 · Structura probelor Android](J6-UI-11-NATIVE-PROBE-OWNERSHIP.md) · verificare Android | Separă scenariile de driverul comun și verifică dialogurile prin contractul UI. |
| — | Extern, la cerere | 43 (25+12+2+4) | [J6-REL-01 · Vizibilitate publică](J6-REL-01-PUBLIC-RELEASE-VISIBILITY.md) · titularul contului | Reia separat proba anonimă; nu blochează distribuția autentificată verificată în RAG. |

Scorurile sunt revizuite odată cu fiecare calificare. O restanță se scoate din tabel numai după ce criteriul ei a fost demonstrat și contractul stabil a fost mutat în documentația owner; dosarul ediției păstrează rezultatul istoric.

## Contracte și dovezi

- [Masa, turneul și reluarea](../../holdem-android/README.md) · [Clasamentul](../requirements/PRODUCT_REQUIREMENTS.md#clasamentul-turneelor)
- [Probe Android și acceptanța Extent](../testing/TEST_STRATEGY.md#interfața-android-locală) · [Istoricul pariurilor](../requirements/PRODUCT_REQUIREMENTS.md#istoricul-pariurilor) · [Catalogul edițiilor](../../release/README.md)
- [Guvernanța release-ului și contextul RAG](../release/ATF_Release.md) · [Planul și auditul probelor native](../release/ATF_Release.md#auditul-artefactelor-după-rulare) · [Poarta de promovare](../release/ATF_Release.md#promovare-și-descărcare) · [Evaluarea AI](../requirements/AI_REQUIREMENTS.md)
