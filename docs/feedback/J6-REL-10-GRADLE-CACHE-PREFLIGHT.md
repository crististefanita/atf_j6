# J6-REL-10 · Verificarea cache-ului de build

P2. Ownerul este procesul de calificare Android.

## Lacună

Runnerul execută întreaga suită Maven înainte ca Gradle să rezolve pluginul Android în mod offline. Un cache cu distribuția Gradle și fără pluginul fixat consumă timpul suitei JVM înainte să raporteze dependența lipsă. [Runnerul de release](../../scripts/Build-HoldemRelease.ps1) · [Configurația pluginului](../../holdem-android/build.gradle)

## Criteriu de închidere

- Verifică rezolvarea offline a pluginului din cache-ul efectiv al calificării înainte de rularea completă Maven. Extrage versiunea din configurația proiectului și menționează cache-ul și dependența în diagnostic.
- Probează separat cache-ul incomplet și cache-ul pregătit. În primul caz verifică oprirea înainte de Maven și rezervarea emulatorului. În al doilea caz verifică faptul că buildul continuă din sursa fixată. [Calificare reproductibilă](../release/ATF_Release.md#calificare-reproductibilă)
