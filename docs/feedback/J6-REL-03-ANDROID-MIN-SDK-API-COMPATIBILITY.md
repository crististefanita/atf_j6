# J6-REL-03 — Compatibilitatea API Android minimă

**Prioritate:** P1

**Stare:** Corectat în sursa curentă; revalidarea nativă API 28 este în curs

## Constatare

În sanity-ul Android API 28, deschiderea mâinii apela recomandarea coach-ului. Prima rulare s-a oprit în `HoldemHand.observeActor()` deoarece `Stream.toList()` nu există în runtime-ul API 28. După corectarea acelui apel, proba a ajuns mai departe și a găsit `ArrayList.toArray(IntFunction)` în `MonteCarloEquityEstimator`; nici această metodă nu există pe dispozitiv. Ambele terminau procesul aplicației cu `NoSuchMethodError`, iar instrumentarea nu emitea receipt. Testele JVM, compilarea Release și Lint treceau, deci nu acopereau aceste diferențe API runtime.

## Corecție și protecție

- Proiecția folosește `collect(Collectors.toList())`, consecvent cu celelalte colecții din motor.
- Estimatorul convertește pachetul la array cu `deck.toArray(new Card[deck.size()])`, semnătură disponibilă pe API-ul minim.
- `ReleaseGovernanceTest` scanează sursele Java incluse în runtime-ul Android și respinge `Stream.toList()` și `Collection.toArray(IntFunction)`; mutanții negativi verifică ambele porți.
- Testele componente `HoldemRulesTest` și `LocalHoldemGameTest` validează proiecția și recomandarea; sanity-ul nativ API 28 rămâne proba runtime decisivă.

## Verificare

Primele două rulări Android API 28 au identificat incompatibilități consecutive, fiecare înainte de receipt. După corecții trebuie rerulate regresia componentă, guvernanța Extent și ambele sanity-uri native (`tournament`, apoi `release-smoke`). Până la trecerea probelor native nu se marchează ediția nouă `PREPARED`.

## Lecție pentru release

Limbajul sursei Java nu garantează că fiecare metodă Java există pe `minSdk`. Un build semnat, testele JVM și Lint fără avertismente nu înlocuiesc un sanity pe nivelul Android minim suportat. Erorile instrumentării trebuie să păstreze diagnosticul logcat, nu să fie raportate drept lipsă generică de receipt.

## Referințe

- [Motorul corectat](../../holdem-core/src/main/java/ro/atf/holdem/core/HoldemHand.java)
- [Guvernanța de release](../../holdem-app/src/test/java/ro/atf/j6/release/ReleaseGovernanceTest.java)
- [Strategia de testare](../testing/TEST_STRATEGY.md)
- [Procesul release Android](../release/ATF_Release.md)
