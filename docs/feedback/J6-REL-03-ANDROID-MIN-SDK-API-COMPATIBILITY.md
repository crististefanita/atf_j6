# J6-REL-03 — Compatibilitatea API Android minimă

**Prioritate:** P1

**Stare:** Corectat și verificat pe API 28; poartă inclusă în calificarea următoarei ediții

## Constatare

În sanity-ul Android API 28, deschiderea mâinii apela recomandarea coach-ului. Prima rulare s-a oprit în `HoldemHand.observeActor()` deoarece `Stream.toList()` nu există în runtime-ul API 28. După corectarea acelui apel, proba a ajuns mai departe și a găsit `ArrayList.toArray(IntFunction)` în `MonteCarloEquityEstimator`; nici această metodă nu există pe dispozitiv. Ambele terminau procesul aplicației cu `NoSuchMethodError`, iar instrumentarea nu emitea receipt. Testele JVM, compilarea Release și Lint treceau, deci nu acopereau aceste diferențe API runtime.

La integrarea clasamentului, verificarea pe același API a mai descoperit `HexFormat` indisponibil și `ByteArrayOutputStream.toString(Charset)` indisponibil. Defectele au apărut la hash-ul și citirea datelor clasamentului, după ce primele incompatibilități fuseseră corectate; testele JVM singure nu le-au prins.

## Corecție și protecție

- Proiecția folosește `collect(Collectors.toList())`, consecvent cu celelalte colecții din motor.
- Estimatorul convertește pachetul la array cu `deck.toArray(new Card[deck.size()])`, semnătură disponibilă pe API-ul minim.
- `ReleaseGovernanceTest` scanează sursele Java incluse în runtime-ul Android și respinge `Stream.toList()` și `Collection.toArray(IntFunction)`; mutanții negativi verifică ambele porți.
- Testele componente `HoldemRulesTest` și `LocalHoldemGameTest` validează proiecția și recomandarea; sanity-ul nativ API 28 rămâne proba runtime decisivă.
- La adăugarea clasamentului cumulativ J6, aceeași poartă a prins trei utilizări `Stream.toList()` în politica nouă și una în API-ul de clasament al turneului. Au fost schimbate în colectare compatibilă și liste read-only; verificarea Maven curentă trece aceeași scanare peste tot codul Android împachetat.
- Codarea SHA-256 folosește acum encoder hexazecimal compatibil, cu vector cunoscut în test; citirea UTF-8 folosește `new String(bytes, StandardCharsets.UTF_8)`. Scannerul de guvernanță respinge `HexFormat` și `toString(Charset)` în codul Android împachetat, alături de API-urile incompatibile deja urmărite.

## Verificare

Rulările API 28 au identificat incompatibilitățile Java una după alta, înainte de emiterea receipt-ului, iar testul de guvernanță a fost extins pentru regresiile observate. Snapshotul actual a trecut **97/97 teste Maven** și **39/39 teste Android JVM**. Tournament sanity pe API 28 a trecut **58/58 aserțiuni**, cu **8 capturi native**; APK-ul testat are SHA-256 `08f1c4a11ace06059fee2e22d0d7d7e19a2e6a81cea962ff0186f8880dc249bc`, iar certificatul de semnare corespunde identității J6 protejate. Raportul Extent și receipt-ul sunt păstrate în rădăcina externă de dovezi.

## Lecție pentru release

Limbajul sursei Java nu garantează că fiecare metodă Java există pe `minSdk`. Un build semnat, testele JVM și Lint fără avertismente nu înlocuiesc un sanity pe nivelul Android minim suportat. Erorile instrumentării trebuie să păstreze diagnosticul logcat, nu să fie raportate drept lipsă generică de receipt.

Compatibilitatea trebuie verificată și pentru API-uri de utilitate aparent banale (formatare hex și conversie text), nu doar pentru API-uri de colecții. Scannerul static completează testul pe emulator; nu îl înlocuiește.

## Referințe

- [Motorul corectat](../../holdem-core/src/main/java/ro/atf/holdem/core/HoldemHand.java)
- [Guvernanța de release](../../holdem-app/src/test/java/ro/atf/j6/release/ReleaseGovernanceTest.java)
- [Strategia de testare](../testing/TEST_STRATEGY.md)
- [Procesul release Android](../release/ATF_Release.md)
