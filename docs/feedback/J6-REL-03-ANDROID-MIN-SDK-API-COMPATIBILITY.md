# J6-REL-03 · Verifică runtime-ul Android minim

**Prioritate:** P2

**Stare:** Regresiile Java cunoscute sunt corectate, proba API 26 rămâne deschisă

**Owner:** Calificare Android J6

`minSdk` este 26, iar calificarea 0.2.0 din `origin/main:release/0.2.0/qualification.json` a rulat pe API 28. Guvernanța acoperă metodele incompatibile descoperite anterior, inclusiv în coach și stocarea clasamentului. [Configurația Android](../../holdem-android/app/build.gradle) · [Guvernanța](../../holdem-app/src/test/java/ro/atf/j6/release/ReleaseGovernanceTest.java)

## Criteriu de închidere

<<<<<<< Updated upstream
## Corecție și protecție

- Proiecția folosește `collect(Collectors.toList())`, consecvent cu celelalte colecții din motor.
- Estimatorul convertește pachetul la array cu `deck.toArray(new Card[deck.size()])`, semnătură disponibilă pe API-ul minim.
- `ReleaseGovernanceTest` scanează sursele Java incluse în runtime-ul Android și respinge `Stream.toList()` și `Collection.toArray(IntFunction)`; mutanții negativi verifică ambele porți.
- Testele componente `HoldemRulesTest` și `LocalHoldemGameTest` validează proiecția și recomandarea; sanity-ul nativ API 28 rămâne proba runtime decisivă.
- La adăugarea clasamentului cumulativ J6, aceeași poartă a prins trei utilizări `Stream.toList()` în politica nouă și una în API-ul de clasament al turneului. Au fost schimbate în colectare compatibilă și liste read-only; verificarea Maven curentă trece aceeași scanare peste tot codul Android împachetat.
- Codarea SHA-256 folosește acum encoder hexazecimal compatibil, cu vector cunoscut în test; citirea UTF-8 folosește `new String(bytes, StandardCharsets.UTF_8)`. Scannerul de guvernanță respinge `HexFormat` și `toString(Charset)` în codul Android împachetat, alături de API-urile incompatibile deja urmărite.

## Verificare

Rulările API 28 au identificat incompatibilitățile Java una după alta, înainte de emiterea receipt-ului, iar testul de guvernanță a fost extins pentru regresiile observate. Snapshotul verificat acum a trecut **97/97 teste Maven** și **43/43 teste Android JVM**. Tournament sanity pe API 28 a trecut **64/64 aserțiuni**, cu **8 capturi native**; APK-ul sanity testat are SHA-256 `39353f18940e6d407ffadd4242fbf598fa483b0d892454851c8ca22bf3ddaa7f`, semnat cu identitatea J6 protejată. Receipt-ul și PNG-urile sunt în `C:\Users\Cristi\AppData\Local\Temp\j6-live-final6-20261108\android-tournament-sanity\`, iar Extent-ul verificat este `C:\Users\Cristi\AppData\Local\Temp\j6-live-final6-20261108\reports\android-tournament-extent.html`. Acestea sunt dovezi de snapshot, nu o calificare nouă de release.

## Lecție pentru release

Limbajul sursei Java nu garantează că fiecare metodă Java există pe `minSdk`. Un build semnat, testele JVM și Lint fără avertismente nu înlocuiesc un sanity pe nivelul Android minim suportat. Erorile instrumentării trebuie să păstreze diagnosticul logcat, nu să fie raportate drept lipsă generică de receipt.

Compatibilitatea trebuie verificată și pentru API-uri de utilitate aparent banale (formatare hex și conversie text), nu doar pentru API-uri de colecții. Scannerul static completează testul pe emulator; nu îl înlocuiește.

## Referințe

- [Motorul corectat](../../holdem-core/src/main/java/ro/atf/holdem/core/HoldemHand.java)
- [Guvernanța de release](../../holdem-app/src/test/java/ro/atf/j6/release/ReleaseGovernanceTest.java)
- [Strategia de testare](../testing/TEST_STRATEGY.md)
- [Procesul release Android](../release/ATF_Release.md)
=======
- Folosiți API 26 pentru un sanity scurt care exercită coach-ul, reluarea și citirea/scrierea clasamentului, plus intrarea în APK-ul semnat. Receipt-ul fixează API-ul și identitatea fiecărui binar. [Procesul](../release/ATF_Release.md)
>>>>>>> Stashed changes
