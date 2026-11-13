# Realizări Hold’em — verificări rămase

## Pe scurt

Rămân verificările de restaurare, lizibilitate și ritm în sesiuni manuale. Proveniența Manual/Auto-play și creditarea o singură dată sunt acoperite la nivel de componentă. [Contractul Realizărilor](../requirements/PRODUCT_REQUIREMENTS.md#realizări) · [Testele de realizări](../../holdem-android/app/src/test/java/ro/atf/holdem/android/GameplayAchievementsComponentTest.java)

## P2 · Poziția listei și lizibilitatea pe ecran scund

**Responsabil · acceptanța Android**

Proba nativă verifică nivelurile, căutarea, detaliile, progresul și recrearea ecranului. Rămân poziția rândului filtrat după recreare și măsurarea contrastului cu font mărit pe viewport scund. [Strategia de testare](../testing/TEST_STRATEGY.md#realizări-de-joc)

- Pe o listă filtrată cu mai multe rezultate, derulează înainte să deschizi un detaliu. După recrearea activității și închiderea detaliului, compară primul rând vizibil cu cel anterior. [Activitatea Realizări](../../holdem-android/app/src/main/java/ro/atf/holdem/android/GameplayAchievementsActivity.java)
- Pe profil compact cu font mărit, verifică accesul la textul eligibilității și la butonul de închidere. Măsoară contrastul stărilor obținut și în progres pe ecranul randat. [Proba nativă](../../holdem-android/app/src/androidTest/java/ro/atf/holdem/android/TournamentSanity.java)

## P2 · Ritmul în sesiuni jucate manual

**Responsabil · produs și calibrare**

În sesiuni manuale Normal și Greu, verifică dacă jucătorii pot identifica primul obiectiv din fiecare nivel și dacă observă progresul paralel. Înregistrează separat claritatea criteriului, timpul până la prima creditare și rata de atingere înainte de a recalibra pragurile.

[Metoda de calibrare](../testing/TEST_STRATEGY.md#realizări-de-joc) · [Planul lotului](../../config/j6-live-achievement-calibration.yaml)
