# Realizări Hold’em — verificări deschise

## Pe scurt

Rămân trei verificări. Proveniența deciziilor condiționează activarea obiectivelor candidate care cer joc manual. Interfața și ritmul de obținere au criterii separate. [Contractul Realizărilor](../requirements/PRODUCT_REQUIREMENTS.md#realizări)

## P1 · Credit manual dovedit înaintea extinderii

**Responsabil · motorul local și checkpoint-ul Android**

`LocalHoldemGame` salvează actorul și mutarea. Atât comanda din UI, cât și Auto-play pot ajunge la `play`, iar jurnalul le salvează în aceeași formă. [Jurnalul local](../../holdem-android/app/src/main/java/ro/atf/holdem/android/LocalHoldemGame.java) · [Intrările din interfață](../../holdem-android/app/src/main/java/ro/atf/holdem/android/HoldemActivity.java)

- Persistă sursa la acceptarea mutării și păstrează eligibilitatea pe toată durata turneului. Pentru un checkpoint vechi, proveniența necunoscută trebuie să lase jocul reluabil și creditul manual neacordat.
- Verifică aceleași mutări cu surse diferite, inclusiv o mutare Auto-play într-o mână anterioară ultimei. O victorie Hard la șase locuri, integral manuală, avansează exact o dată și după restart. Cazurile Normal, mixt și cu proveniență necunoscută au zero credit la obiectivul manual Hard.
- Pentru titlurile la toate dimensiunile, folosește ID-ul persistent al profilului și dimensiunea inițială a mesei. Două titluri la aceeași dimensiune avansează o singură treaptă. Verifică 4, 5 și 6 locuri, reluarea și două profiluri cu același nume.

[Regulile candidate](../../config/j6-achievement-expansion.yaml) · [Dovezile care lipsesc în replay](../../scripts/analyze-achievement-replays.cjs)

## P2 · Restaurarea listei pe Android

**Responsabil · acceptanța Android**

Proba de instalare acoperă căutarea și deschiderea regulii unui obiectiv. Proba de turneu verifică progresul salvat după redeschiderea listei. [Rapoartele ediției](../../release/0.16.0/reports/README.md#rapoarte-extent)

- Combină căutarea cu nivelul și starea, derulează și deschide un detaliu. După recrearea activității, verifică aceleași filtre, același rând și același detaliu. [Activitatea Realizări](../../holdem-android/app/src/main/java/ro/atf/holdem/android/GameplayAchievementsActivity.java)
- Pe profil compact cu font mărit, verifică accesul la textul eligibilității și la butonul de închidere. Măsoară contrastul stărilor obținut și în progres pe ecranul randat. [Proba nativă](../../holdem-android/app/src/androidTest/java/ro/atf/holdem/android/TournamentSanity.java)

## P2 · Ritmul în sesiuni jucate manual

**Responsabil · produs și calibrare**

Calibrarea existentă folosește Auto-play pentru profil. În sesiuni manuale Normal și Greu, măsoară dacă jucătorul înțelege primul obiectiv din fiecare nivel și observă progresul paralel.

Pentru trei titluri, măsoară deblocarea pe ferestre de 6 și 12 turnee, cu numitorul explicit și sesiunile neterminate evidențiate. Folosește diferența față de simulare ca dovadă pentru o eventuală schimbare de prag. [Metoda de calibrare](../testing/TEST_STRATEGY.md#realizări-de-joc)
