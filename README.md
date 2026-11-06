# ATF J6 · Hold'em Lab

ATF J6 este laboratorul JVM pentru un sistem local de No-Limit Texas Hold'em: motor de reguli, AI, arena de simulare, aplicație de consolă și adaptor online pentru platforme care acceptă explicit boți.

Prima versiune nu este un bot pentru site-uri comerciale cu bani reali. Este un nucleu verificabil care poate fi antrenat și evaluat local, apoi conectat explicit la o arenă autorizată precum Open Poker. Nu există Android în prima fază.

## Obiectivul primei faze

Construim o buclă completă și repetabilă:

```text
stare Hold'em -> observație publică -> politică AI -> acțiune legală
      ^                                             |
      |------------- motor + replay ----------------|
```

Vertical slice-ul actual demonstrează contractul dintre aplicație și AI. Motorul complet de reguli, evaluatorul de mâini și simularea multi-jucător sunt cerințe de implementare următoare și sunt descrise în documentație; nu sunt declarate finalizate doar pentru că proiectul se compilează.

## Module

| Modul | Rol |
|---|---|
| `holdem-core` | Cărți, străzi, acțiuni legale și observația actorului; fără Android, rețea sau AI. |
| `holdem-ai` | Porturi de politică și baseline deterministic; ulterior equity, range-uri și MCCFR. |
| `holdem-arena` | Contractul pentru jocuri locale, seed-uri, replay și evaluare. |
| `holdem-online` | Transport/adaptor pentru protocoale autorizate; prima țintă este Open Poker WebSocket V2. |
| `holdem-app` | Aplicație de consolă pentru smoke-run local și comenzi viitoare de simulare. |

## Cerințe și documentație

- [Cerințe generale](docs/requirements/README.md)
- [Cerințe de produs](docs/requirements/PRODUCT_REQUIREMENTS.md)
- [Cerințe de domeniu Hold'em](docs/requirements/DOMAIN_REQUIREMENTS.md)
- [Cerințe AI și evaluare](docs/requirements/AI_REQUIREMENTS.md)
- [Cerințe online și siguranță](docs/requirements/ONLINE_REQUIREMENTS.md)
- [Arhitectură](docs/architecture/ARCHITECTURE.md)
- [Strategie de testare](docs/testing/TEST_STRATEGY.md)

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

Din `C:\work\auto\atf_j6`:

```powershell
mvn -B test
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

Implementat în această bază: structură multi-modul, modele de bază, port de politică, baseline local și documentație de cerințe.

Neimplementat încă: evaluator complet 7-card, toate tranzițiile Hold'em, poturi secundare, simulare multi-seat, range engine, MCCFR, persistent replay și client online complet.
