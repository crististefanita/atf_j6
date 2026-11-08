# ATF J6 · Hold'em Lab

ATF J6 este laboratorul JVM pentru un sistem local de No-Limit Texas Hold'em: motor de reguli, AI, arena de simulare, aplicație de consolă și adaptor online pentru platforme care acceptă explicit boți.

Nucleul se antrenează și se evaluează local. Adaptorul online vizează numai arene care autorizează explicit boții, precum Open Poker. Android oferă o mână de antrenament sau un turneu offline cu eliminări, pe componentele comune Whist și Rentz. [Interfața Android](holdem-android/README.md)

## Release Android curent

`0.1.0` este `PREPARED`: asset-urile GitHub sunt complete, iar ultima probă anonimă (`2026-11-07T22:16:42Z`) a primit HTTP 404. Descărcarea din browserul conectat la contul GitHub a fost verificată. **Linkurile necesită autentificare GitHub.** [Pagina Release](https://github.com/crististefanita/atf_j6/releases/tag/v0.1.0) · [Descarcă APK-ul](https://github.com/crististefanita/atf_j6/releases/download/v0.1.0/HoldemLab-0.1.0.apk). [Dosarul ediției și rapoartele](release/0.1.0/README.md) păstrează calificarea; [J6-REL-01](docs/feedback/J6-REL-01-PUBLIC-RELEASE-VISIBILITY.md) urmărește accesul anonim.

## Obiectivul primei faze

Construim o buclă completă și repetabilă:

```text
stare Hold'em -> observație publică -> politică AI -> acțiune legală
      ^                                             |
      |------------- motor + replay ----------------|
```

Motorul local implementează distribuirea, acțiunile legale, evaluarea mâinii, decontarea poturilor, turneele de 4–6 locuri și mesele cash cu blind-uri fixe. AI-ul are baseline-uri și un candidat Monte Carlo care estimează cota potului din cărțile vizibile și cărți ascunse eșantionate. Contextul ajustează pragurile, însă range-urile adversarilor nu sunt încă modelate. Testele de componentă și auditorii independenți verifică regulile și sesiunile fără UI; rezultatele offline nu califică singure o strategie de nivel profesionist.

## Module

| Modul | Rol |
|---|---|
| `holdem-core` | Cărți, străzi, acțiuni legale și observația actorului; fără Android, rețea sau AI. |
| `holdem-ai` | Baseline-uri și estimator Monte Carlo reproductibil, cu pot odds și ponderi de context din YAML. |
| `holdem-arena` | Turnee cu blind-uri progresive, mese cash cu blind-uri fixe, seed-uri și jurnale verificabile. |
| `holdem-online` | Transport/adaptor pentru protocoale autorizate; prima țintă este Open Poker WebSocket V2. |
| `holdem-app` | Aplicație de consolă pentru smoke-run local și comenzi viitoare de simulare. |
| [`holdem-android`](holdem-android/README.md) | Meniu, profil, mână de antrenament și turneu la 4–6 locuri până la victorie sau eliminare. Gradle compilează sursele aceluiași motor. |

## Biblioteci comune

J6 consumă Core, Reporting, Console, Mock și Test Kit ca JAR/POM direct în [lib](lib/README.md). POM-ul rădăcină instalează perechile la `validate`; IntelliJ folosește același JDK 21 și repository Maven. Dependențele sunt declarate pentru testele din `holdem-app`, fără a lega motorul Hold’em de Reporting.

[PlatformLibraryAdoptionTest](holdem-app/src/test/java/ro/atf/j6/libraries/PlatformLibraryAdoptionTest.java) verifică originea și hash-ul fiecărui binar, JSON, transportul mock și aserțiunile din HTML-ul Extent. Output-ul rămâne în TMP prin `atf.test.reportDirectory`. Proveniența bibliotecilor este în `library-provenance.json`; mentenanța și feedback-ul comun au owner în J0, iar regulile de joc rămân în J6.

## Cerințe și documentație

- [Cerințe generale](docs/requirements/README.md)
- [Cerințe de produs](docs/requirements/PRODUCT_REQUIREMENTS.md)
- [Cerințe de domeniu Hold'em](docs/requirements/DOMAIN_REQUIREMENTS.md)
- [Reguli și simulare de turneu](docs/requirements/TOURNAMENT_RULES.md)
- [Cerințe AI și evaluare](docs/requirements/AI_REQUIREMENTS.md)
- [Cerințe online și siguranță](docs/requirements/ONLINE_REQUIREMENTS.md)
- [Arhitectură](docs/architecture/ARCHITECTURE.md)
- [Strategie de testare](docs/testing/TEST_STRATEGY.md)
- [Procesul de release Android](docs/release/ATF_Release.md)
- [Feedback](docs/feedback/README.md)

## Construire locală

### Precondiții

- JDK 21 sau mai nou;
- Maven 3.9 sau mai nou;
- PowerShell 7 este recomandat pe Windows;
- acces la internet numai pentru faza online și numai după configurarea explicită a cheii.

Verificare:

```powershell
java -version
mvn -version
```

### Compilare

Dintr-o copie proprie a proiectului în TMP, cu repository Maven tot în TMP:

```powershell
mvn -B -ntp "-Dmaven.repo.local=$env:TEMP\atf-j6-m2" test
```

Pentru prima verificare fără a executa scenariul online:

```powershell
mvn -B -DskipTests package
```

Dacă Maven nu este instalat încă, vertical slice-ul poate fi verificat numai cu JDK 21:

```powershell
.\scripts\compile-and-run-local.ps1
```

### Smoke-run local

```powershell
mvn -B -pl holdem-app -am exec:java -Dexec.mainClass=ro.atf.holdem.app.LocalAppMain
```

Dacă pluginul `exec` nu este disponibil în cache, folosește:

```powershell
mvn -B -pl holdem-app -am package
java -cp holdem-app/target/classes;holdem-ai/target/classes;holdem-core/target/classes ro.atf.holdem.app.LocalAppMain
```

### Online, numai explicit

Open Poker documentează un endpoint WebSocket V2 la `wss://openpoker.ai/ws`, autentificare Bearer, mesaje JSON și limite de reconectare/idempotency. Cheia nu se pune în fișiere sau argumente de comandă.

```powershell
$env:OPEN_POKER_API_KEY = 'cheia-ta-locala'
mvn -B -pl holdem-online -am package
```

Conectarea, `join_lobby` și trimiterea acțiunilor vor fi comenzi separate, cu jurnal și oprire controlată. Rularea locală implicită nu face conexiuni externe.

## Reguli de proiect

1. Motorul primește starea completă a jocului, dar politica AI primește numai observația permisă actorului.
2. Nicio mână adversă reală nu intră în decizie.
3. Orice acțiune este validată de motor înainte de aplicare.
4. Orice rezultat de performanță are seed, versiune de politică, corpus și replay.
5. Un rezultat pozitiv în arena virtuală nu este o garanție de profit financiar.
6. Nu se implementează scraping, click automation, RTA sau ocolirea detecției pe platforme care nu autorizează boți.

## Starea proiectului

Implementat în această bază: structură multi-modul, motor local No-Limit Hold'em, evaluator de 7 cărți, poturi secundare, creșteri all-in și redeschidere cumulativă, turnee reproducibile de 4–6 locuri, baseline-uri AI, explorare de bluff configurabilă și verificări independente pe jurnale.

În lucru: calibrarea equity-ului la range-urile adversarilor, căutarea prin rollout, MCCFR/self-play, replay persistent între sesiuni și client online complet. Equity-ul curent pornește de la cărți adverse aleatoare și ponderi explicite de context; politica de bluff rămâne un amestec determinist de laborator, nu un model exploatativ calibrat.
