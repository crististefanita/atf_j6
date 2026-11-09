# J6-REL-03 · Verificarea pe Android API 26

P2. Ownerul este calificarea Android J6.

`minSdk` este 26. Guvernanța de release scanează codul Java inclus în APK, iar calificarea disponibilă acoperă execuția pe API 28. [Configurație Android](../../holdem-android/app/build.gradle) · [Guvernanță](../../holdem-app/src/test/java/ro/atf/j6/release/ReleaseGovernanceTest.java)

## Criteriu de închidere

Instalează varianta Release semnată pe API 26 și verifică pornirea și navigarea. Exercită recomandarea AI și restaurarea pe același nivel API. Păstrează receipt-ul cu hash-ul APK-ului și diagnosticul de runtime pentru orice eșec. [Procesul de calificare](../release/ATF_Release.md#calificare-reproductibilă)
