# Rapoarte de calificare 0.19.1

## Pe scurt

Calificarea este **PASS** pentru APK-ul semnat cu SHA-256 `cfdf3138b1ae67d938af15ed3b013e9db593787c7c532200b5dbf1371aef4d4d`. Rapoartele păstrează expected/actual, proveniența testului și capturile native originale inline.

## Suite și probe

| Raport | Acoperire | Rezultat |
|---|---|---|
| [Poarta de promovare](promotion-extent.html) | Identitatea APK-ului și proveniența sursei | PASS |
| [Componente Maven](maven-component-suites-extent.html) | Motor, reguli, YAML, AI și guvernanță | 32 suite · 223 teste · 0 fail/error/skip |
| [Componente Android JVM](android-component-suites-extent.html) | Realizări, progres și integrarea UI comun | 10 suite · 116 teste · 0 fail/error/skip |
| [Guvernanța release-ului după publicare](J6ReleaseGovernance-1794575551049.html) | Dossier, proveniență, integritatea dovezilor | 8 teste · PASS |
| [Guvernanța linkurilor din README](J6MainReadmeGovernance-1794575554443.html) | Două linkuri curente, ordonate, fără date de checksum | PASS |
| [Guvernanța documentației după publicare](J6DocumentationGovernance-1794575661716.html) | Contractele documentației curente | 4 teste · PASS |
| [Guvernanța documentației](J6DocumentationGovernance-1794570531660.html) | Structura și standardele documentației | 4 teste · PASS |
| [Impactul UI comun](J6SharedUiImpactGovernance-1794570606186.html) | Trasabilitatea dependenței Card Table UI | PASS |
| [Turneu nativ](android-tournament-extent.html) | Flux de joc, Realizări și dialogul comun | 138 aserțiuni · 20 capturi · PASS |
| [Smoke APK semnat](android-release-smoke-extent.html) | Instalare release, niveluri și ecran compact | 84 aserțiuni · 13 capturi · PASS |

## Proveniență

- Commit sursă și build calificat: `001eea1425e7ff00594e23aabcfb9ea230dbd879`.
- APK: `HoldemLab-0.19.1.apk` · 880.802 bytes.
- Certificat de semnare SHA-256: `30213bddf2a1be800d19c588d5ebdfe22ab6b83d9128faee83bf58e260eab1ce`.
- [Manifestul ediției](../release-validation.json), [planul nativ](../native-plan.json) și contextul RAG leagă rapoartele de candidatul calificat.


