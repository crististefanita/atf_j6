# Rapoarte Hold’em Lab 0.19.2

## Pe scurt

Toate probele de mai jos au trecut. Rapoartele native includ rezultatul așteptat, rezultatul observat și capturile PNG originale în Base64. Planul fixează varianta APK pentru fiecare probă.

## Suite JVM

- [Maven](maven-component-suites-extent.html) · 32 suite, 223 teste.
- [Android JVM](android-component-suites-extent.html) · 12 suite, 124 teste.
- [Poarta de promovare](promotion-extent.html) · identitatea APK-ului semnat.

## Probe Android

| Raport | Variantă | Fereastră | Dovezi |
|---|---|---|---|
| [Turneu reproductibil](android-tournament-extent.html) | `sanity` | 392 × 626 dp | 138 aserțiuni · 20 capturi |
| [Instalare și navigare](android-release-smoke-extent.html) | `release` | 392 × 626 dp | 86 aserțiuni · 13 capturi |
| [Turneu Greu la șase locuri](android-hard-six-extent.html) | `release` | 392 × 626 dp | 10 aserțiuni · 3 capturi |
| [Chenare și TalkBack](android-seat-metrics-extent.html) | `release` | 320 × 496 dp | 27 aserțiuni · 3 capturi |
| [Premiu și restituire](android-settlement-award-extent.html) | `sanity` | 392 × 626 dp | 15 aserțiuni · 3 capturi |
| [Split și pot secundar](android-settlement-multi-award-extent.html) | `sanity` | 392 × 626 dp | 29 aserțiuni · 5 capturi |
| [Animații oprite](android-settlement-static-award-extent.html) | `sanity` | 392 × 626 dp | 15 aserțiuni · 1 capturi |
| [Premii pe fereastră scundă](android-settlement-compact-award-extent.html) | `sanity` | 320 × 328 dp | 18 aserțiuni · 3 capturi |
| [Jurnal standard](android-betting-ledger-extent.html) | `sanity` | 392 × 626 dp | 27 aserțiuni · 2 capturi |
| [Jurnal compact](android-betting-ledger-compact-extent.html) | `sanity` | 320 × 328 dp | 28 aserțiuni · 2 capturi |
| [Check și Raise cu TalkBack](android-betting-ledger-talkback-extent.html) | `sanity` | 320 × 328 dp | 38 aserțiuni · 2 capturi |

[Manifestul ediției](../release-validation.json) · [Planul nativ](../native-plan.json) · [Auditul capturilor și rapoartelor](../release-evidence-audit.json) · [Contextul RAG](../release-impact-context.json)
