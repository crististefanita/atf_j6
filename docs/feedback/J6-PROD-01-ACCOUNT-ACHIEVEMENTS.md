# Realizări Hold’em — verificări deschise

## Pe scurt

Rămân trei verificări. Proveniența deciziilor condiționează activarea obiectivelor candidate care cer joc manual. Interfața și ritmul de obținere au criterii separate. [Contractul Realizărilor](../requirements/PRODUCT_REQUIREMENTS.md#realizări)

## P1 · Credit manual dovedit înaintea extinderii

**Responsabil · motorul local și checkpoint-ul Android**

Checkpoint-ul păstrează sursa fiecărei mutări. Componentele verifică proveniența delegată dintr-o mână anterioară, migrarea cu sursă necunoscută și respingerea unei surse incompatibile cu actorul. [Jurnalul local](../../holdem-android/app/src/main/java/ro/atf/holdem/android/LocalHoldemGame.java) · [Testele de checkpoint](../../holdem-android/app/src/test/java/ro/atf/holdem/android/HoldemSessionRepositoryComponentTest.java)

- Leagă eligibilitatea obiectivelor candidate de sursa salvată pe toate mâinile. O victorie Hard la șase locuri, integral manuală, avansează exact o dată și după restart. Cazurile Normal, mixt și cu proveniență necunoscută au zero credit la obiectivul manual Hard.
- Pentru titlurile la toate dimensiunile, folosește ID-ul persistent al profilului și dimensiunea inițială a mesei. Două titluri la aceeași dimensiune avansează o singură treaptă. Verifică 4, 5 și 6 locuri, reluarea și două profiluri cu același nume.

[Regulile candidate](../../config/j6-achievement-expansion.yaml) · [Dovezile care lipsesc în replay](../../scripts/analyze-achievement-replays.cjs)

## P2 · Lista filtrată pe ecran scund

**Responsabil · acceptanța Android**

Proba de instalare verifică restaurarea detaliului, căutării și filtrelor după recrearea activității. Proba de turneu verifică progresul salvat la redeschiderea listei. Rămân poziția listei filtrate și lizibilitatea pe ecran scund. [Strategia de testare](../testing/TEST_STRATEGY.md#realizări-de-joc)

- Pe o listă filtrată cu mai multe rezultate, derulează înainte să deschizi un detaliu. După recrearea activității și închiderea detaliului, compară primul rând vizibil cu cel anterior. [Activitatea Realizări](../../holdem-android/app/src/main/java/ro/atf/holdem/android/GameplayAchievementsActivity.java)
- Pe profil compact cu font mărit, verifică accesul la textul eligibilității și la butonul de închidere. Măsoară contrastul stărilor obținut și în progres pe ecranul randat. [Proba nativă](../../holdem-android/app/src/androidTest/java/ro/atf/holdem/android/TournamentSanity.java)

## P2 · Ritmul în sesiuni jucate manual

**Responsabil · produs și calibrare**

Calibrarea existentă folosește Auto-play pentru profil. În sesiuni manuale Normal și Greu, măsoară dacă jucătorul înțelege primul obiectiv din fiecare nivel și observă progresul paralel.

Măsoară separat ritmul victoriei la showdown multiplu, al seriei de trei titluri și al titlurilor la șase locuri. Folosește ferestre de 6 și 12 turnee, cu numitorul explicit și sesiunile neterminate evidențiate. Folosește diferența față de simulare ca dovadă pentru o eventuală schimbare de prag. [Metoda de calibrare](../testing/TEST_STRATEGY.md#realizări-de-joc)
