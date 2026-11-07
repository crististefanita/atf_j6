# Interfața Android Hold’em

## Pe scurt

Android folosește aspectul Whist și Rentz pentru meniu, limbă, profil și masă. Jucătorul își poate edita numele și alege unul dintre modurile offline.

- **Play a hand** oferă heads-up sau o masă cu 4–6 locuri. Fiecare mână pornește cu stive noi.
- **Play a tournament** pornește la 4–6 locuri, cu 2.000 de jetoane per loc și blinduri fixe de 10/20. Stivele trec dintr-o mână în alta, locurile eliminate rămân marcate pe masă, iar motorul mută blindurile printre locurile cu jetoane. După plata ultimului pot, **Tournament result** arată rezultatul, numărul de mâini și stiva finală.

Adversarii de antrenament dau check sau call în ambele moduri.

| Comun J4/J5 | Personalizare J6 |
|---|---|
| Tema, widgeturile, selectorul de limbă, editorul de nume și numele adversarilor | Rutele, textele EN/RO și persistența profilului Hold’em |
| Geometria mesei și chenarul activ | Jucătorul propriu jos, rotația locurilor, dealerul, stiva și contribuția fiecăruia |
| Fețele și mâna centrată | Două cărți proprii și cinci poziții comunitare în centru, lângă pot |

## Frontiera motorului

`engine` compilează sursele `holdem-core` cu JDK 21 și țintă Java 17. Android afișează acțiunile permise de motor. La bet sau raise, suma introdusă este numărul de jetoane adăugate în acel moment. Motorul respinge valorile din afara intervalului, iar sesiunea respinge comenzile unui alt tur sau ale altei mâini.

Interfața primește numai cele două cărți proprii, board-ul public și faptele de masă. La decontare arată jetoanele primite de fiecare loc, inclusiv eventualele restituiri. Cărțile adversarilor rămân în motor. La recrearea activității, starea privată salvată reconstruiește mâinile din seed și comenzile umane acceptate, în ordinea rundelor. Dacă ecranul final era deschis, este afișat din nou.

În heads-up, dealerul pune blindul mic și acționează primul preflop. Blindul mare primește prima carte și acționează primul după flop. Butonul dealerului se mută la următoarea mână heads-up, iar locul jucătorului uman rămâne jos, inclusiv când un turneu se reduce la doi participanți. [Poker TDA, regula 36-C](https://www.pokertda.com/view-poker-tda-rules/)

Pe ferestre înguste, joase sau cu font mărit, J6 așază chenarele pe două rânduri în jurul potului și al cărților comunitare. Antetul arată strada, iar mâna proprie folosește două cărți mai mici, fără suprapunere. La heads-up sunt două chenare opuse. Fereastra poate derula vertical dacă înălțimea disponibilă o cere. În dialogul de raise, conținutul derulează separat, iar butoanele rămân în zona de acțiuni.

## Proveniență

`ui-library` păstrează copia binară admisă în J5 pentru `ro.cardtable:card-table-ui:0.1.6` și `ro.cardtable:card-table-names:0.1.0`. Producătorul este J4. Build-ul verifică manifestul și SHA-256 înainte de împachetare. Modificările comune se califică la producător, apoi se sincronizează în consumatori. [Manifest](ui-library/provenance.json)

## Verificare

Folosiți JDK 21 și Android SDK cu platforma 36, dintr-o copie externă care conține și `holdem-core`. Setați `ANDROID_HOME` către SDK și rulați din `holdem-android`.

```powershell
.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

APK-ul de dezvoltare se generează în `app/build/outputs/apk/debug/`. Componentele Android JVM acoperă stivele, eliminarea, rotația și restaurarea la 4–6 locuri. Rapoartele Extent și build-urile rămân în dosarul extern al execuției. [Strategia J6](../docs/testing/TEST_STRATEGY.md)

### Calificarea release-ului Android

Rulați `scripts/Build-HoldemRelease.ps1` dintr-un worktree curat. Scriptul creează o copie unică sub `C:\work\holdem_back\release-runs`, apoi construiește un APK `sanity` pentru turneul determinist și APK-ul `release` pentru distribuție. Ambele folosesc cheia privată J6; numai `sanity` este debuggable și primește seed-ul de fixture. `release` rămâne `debuggable=false` și nu primește seed-uri de test.

Runnerul rezervă emulatorul `127.0.0.1:5555`, refuză să înlocuiască o instalare existentă și rulează două călătorii: un turneu complet prin controalele accesibile și navigarea pe APK-ul `release` exact. Receipt-ul cu hash-ul binar, aserțiunile și capturile PNG vine prin instrumentare; nu depinde de `run-as` ori de fișiere private de pe dispozitiv. `PublishTournamentExtent.java` încorporează capturile și raportul JVM în două rapoarte Extent, iar rapoartele componente Maven sunt păstrate în același pachet de ediție.

Pentru dezvoltare locală, build-ul Debug simplu rămâne util; el nu califică și nu înlocuiește APK-ul Release. [Procesul complet](../docs/release/ATF_Release.md) include semnarea, integritatea, upload-ul și testul anonim al descărcării.

Prima calificare a trecut cu 33 de teste Maven, 5 componente Android JVM și 78 de comparații pe emulatorul API 28. Traseul nativ a verificat un raise respins, un raise acceptat, străzile și conservarea jetoanelor după decontare.

Calificarea compactă a trecut cu 36 de teste Maven, 7 componente Android JVM și 88 de comparații pe API 28 la font 2,0. A verificat încadrarea la șase locuri, dialogul cu tastatura deschisă și ordinea heads-up înainte și după mutarea dealerului.

## APK local 0.1.0

Starea și cerințele APK-ului sunt în [catalogul release](../release/0.1.0/README.md). Cât timp ediția este `DRAFT`, build-urile Debug rămân doar în TMP; nu există încă un APK de distribuție sau un link public.

Build-urile, APK-urile intermediare și capturile brute rămân în TMP. Pentru un candidat, copiați doar APK-ul Release, checksum-ul, validarea și rapoartele Extent relevante în `release/<versiune>/`; capturile UI rămân încorporate în raport.

## Continuare

- [Prima mână interactivă](../docs/feedback/J6-UI-01-INTERACTIVE-TABLE.md) păstrează criteriile pentru observație și comenzi.
- [Matricea compactă și heads-up](../docs/feedback/J6-UI-02-TABLE-COVERAGE.md) descrie calificarea geometriei și ordinea pozițiilor.
- [Turneu și sanity reproductibil](../docs/feedback/J6-UI-03-TOURNAMENT-SANITY.md) fixează criteriile de eliminare, rezultat și audit.
