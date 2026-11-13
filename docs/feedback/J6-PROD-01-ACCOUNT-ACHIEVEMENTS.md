# Realizări Hold’em — verificări rămase

## Pe scurt

Catalogul activ are 39 de obiective poker, ordonate în YAML și validate în motor. Rămân două verificări de produs care cer sesiuni native/manuale; ele nu invalidează criteriile deja acoperite de teste de componentă. [Contractul Realizărilor](../requirements/PRODUCT_REQUIREMENTS.md#realizări)

## Închis · Proveniență manuală în motor și checkpoint

**Responsabil · motorul local și checkpoint-ul Android**

Checkpoint-ul păstrează sursa fiecărei mutări, iar motorul condiționează realizările manuale de proveniența completă. Testele de componentă verifică distincția Manual/Auto-play, rezultatele Hard la șase locuri și titlurile la dimensiuni diferite. [Jurnalul local](../../holdem-android/app/src/main/java/ro/atf/holdem/android/LocalHoldemGame.java) · [Testele de realizări](../../holdem-android/app/src/test/java/ro/atf/holdem/android/GameplayAchievementsComponentTest.java) · [Testele de checkpoint](../../holdem-android/app/src/test/java/ro/atf/holdem/android/HoldemSessionRepositoryComponentTest.java)

- Criteriile au cazuri pozitive/negative și deduplicare după rezultat. Simulatorul nu pretinde că validează o carieră manuală persistentă între sesiuni; aceasta rămâne probă nativă separată.

[Catalogul activ](../../config/j6-achievements.yaml) · [Criteriile candidate](../../config/j6-achievement-expansion.yaml)

## P2 · Poziția listei și lizibilitatea pe ecran scund

**Responsabil · acceptanța Android**

Proba nativă verifică nivelurile, căutarea, detaliile, progresul și recrearea ecranului. Rămân poziția rândului filtrat după recreare și măsurarea contrastului cu font mărit pe viewport scund. [Strategia de testare](../testing/TEST_STRATEGY.md#realizări-de-joc)

- Pe o listă filtrată cu mai multe rezultate, derulează înainte să deschizi un detaliu. După recrearea activității și închiderea detaliului, compară primul rând vizibil cu cel anterior. [Activitatea Realizări](../../holdem-android/app/src/main/java/ro/atf/holdem/android/GameplayAchievementsActivity.java)
- Pe profil compact cu font mărit, verifică accesul la textul eligibilității și la butonul de închidere. Măsoară contrastul stărilor obținut și în progres pe ecranul randat. [Proba nativă](../../holdem-android/app/src/androidTest/java/ro/atf/holdem/android/TournamentSanity.java)

## P2 · Ritmul în sesiuni jucate manual

**Responsabil · produs și calibrare**

Simularea folosește Auto-play pentru profil și nu evaluează înțelegerea jucătorului. În sesiuni manuale Normal și Greu, măsoară dacă primul obiectiv al fiecărui nivel este clar și dacă progresul paralel se observă.

- Lotul holdout cuprinde 90 de turnee per mod, câte 30 pentru fiecare dimensiune de masă, cu 64 de eșantioane Monte Carlo per decizie.
- Obiectivul este eligibil numai la Greu și șase locuri: lotul holdout a produs 822 de oportunități și 18 creditări.
- Pragul de patru a fost atins în 3/10 ferestre de trei turnee, 3/5 ferestre de șase și 2/2 ferestre de douăsprezece. Ultima rată are doar două observații și este descriptivă, nu suficientă pentru recalibrarea pragului.
- Măsoară separat claritatea și ritmul în sesiuni manuale înainte să schimbi progresul afișat.

[Metoda de calibrare](../testing/TEST_STRATEGY.md#realizări-de-joc) · [Planul lotului](../../config/j6-live-achievement-calibration.yaml)
