# ATF J6 · Hold’em Lab

## Pe scurt

ATF J6 este un laborator pentru dezvoltarea și evaluarea locală a unui joc No-Limit Texas Hold’em. Motorul, politicile AI și arena de simulare funcționează fără serviciu extern.

- `holdem-core` deține regulile, starea și acțiunile legale.
- `holdem-ai` decide numai din observația permisă jucătorului curent.
- `holdem-arena` rulează mese și turnee reproductibile, cu replay.
- `holdem-android` oferă joc offline și revizuirea mâinii.
- `holdem-online` este numai un scaffold `dry-run`: nu deschide WebSocket, nu intră în lobby și nu trimite acțiuni. Cerințele online sunt plan de lucru, nu funcție livrată.

## Jocul

- **[Hold'em Lab 0.2.1 — dosarul ediției](release/0.2.1/README.md)**

## Bucla locală

```text
stare de joc → observația actorului → politică AI → acțiune legală
      ↑                                           │
      └──────────── motor și replay ──────────────┘
```

- Motorul aplică tranziția și verifică acțiunea față de starea curentă.
- Politica primește cărțile proprii, board-ul vizibil și informațiile publice.
- Arena păstrează seed-ul și istoricul necesare reluării și comparației.
- Rezultatele offline verifică implementarea și compară politici; nu dovedesc singure o strategie profesionistă sau profit financiar.

## Module

| Modul | Contract |
|---|---|
| `holdem-core` | Cărți, străzi, observația actorului, acțiuni legale și decontarea poturilor; fără Android sau rețea. |
| `holdem-ai` | Politici baseline și estimator Monte Carlo reproductibil; ponderi de context configurate în YAML. |
| `holdem-arena` | Turnee cu blinduri progresive, mese cash cu blinduri fixe, seed-uri și replay. |
| `holdem-online` | Configurație și raportare `dry-run` doar; nu implementează transportul protocolului. Poate fi scos din produs până când online devine o etapă aprobată. |
| `holdem-app` | Vertical slice de consolă pentru o decizie locală și inspecția scaffold-ului online. |
| `holdem-android` | Interfață locală pentru mâini de antrenament și turnee de 4–6 locuri; Gradle folosește sursele comune ale motorului și AI-ului. |

## Biblioteci comune

J6 consumă Core, Reporting, Console, Mock și Test Kit din perechile JAR/POM din [lib](lib/README.md). POM-ul rădăcină fixează versiunile și instalarea; testele verifică proveniența și integrarea.

[PlatformLibraryAdoptionTest](holdem-app/src/test/java/ro/atf/j6/libraries/PlatformLibraryAdoptionTest.java) verifică hash-urile binare, proveniența JSON, transportul mock și raportul Extent. Biblioteca comună este întreținută în J0; regulile Hold’em rămân în J6.

## Cerințe și documentație

- [Cerințe generale](docs/requirements/README.md)
- [Cerințe de produs](docs/requirements/PRODUCT_REQUIREMENTS.md)
- [Cerințe de domeniu Hold’em](docs/requirements/DOMAIN_REQUIREMENTS.md)
- [Reguli și simulare de turneu](docs/requirements/TOURNAMENT_RULES.md)
- [Cerințe AI și evaluare](docs/requirements/AI_REQUIREMENTS.md)
- [Cerințe online și siguranță](docs/requirements/ONLINE_REQUIREMENTS.md)
- [Arhitectură](docs/architecture/ARCHITECTURE.md)
- [Strategie de testare](docs/testing/TEST_STRATEGY.md)
- [Catalogul release-urilor](release/README.md)
- [Procesul de release Android](docs/release/ATF_Release.md)
- [Feedback activ](docs/feedback/README.md)

## Construire locală

### Cerințe

- JDK 21 sau mai nou;
- Maven 3.9 sau mai nou;
- Android SDK platform 36 pentru aplicația Android;
- PowerShell 7 recomandat pe Windows.

### Maven

Dintr-o copie proprie a proiectului, cu repository-ul Maven în directorul temporar:

```powershell
mvn -B -ntp "-Dmaven.repo.local=$env:TEMP\atf-j6-m2" test
```

Pentru compilare fără rularea testelor:

```powershell
mvn -B -DskipTests package
```

Vertical slice-ul de consolă se poate porni astfel:

```powershell
mvn -B -pl holdem-app -am exec:java -Dexec.mainClass=ro.atf.holdem.app.LocalAppMain
```

Dacă pluginul Maven `exec` nu este disponibil, împachetați modulele și porniți `LocalAppMain` cu classpath-ul local. Execuția implicită nu trimite acțiuni către o platformă externă.

## Reguli și limite

- Motorul gestionează starea completă; politica vede numai observația actorului.
- Motorul validează fiecare acțiune înainte de aplicare.
- Evaluările păstrează seed-ul, versiunea politicii și replay-ul.
- Politicile curente rămân euristice, iar estimatorul Monte Carlo nu modelează încă range-uri adversare.
- Un client WebSocket real nu este implementat; orice integrare viitoare cere autorizarea explicită a platformei.
- Nu se adaugă scraping, auto-click, real-time assistance interzisă sau ocolirea detecției.

Pentru contractele și criteriile de acceptare, începeți din [indexul cerințelor](docs/requirements/README.md) și [strategia de testare](docs/testing/TEST_STRATEGY.md).
