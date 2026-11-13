# Note de versiune 0.19.1

## Popup-ul Realizărilor

- Notificarea folosește modalul `AchievementNoticeView.dialog` din biblioteca Card Table UI, ca prezentarea din J4.
- Dialogul rămâne deschis până la închidere explicită; nu dispare după un temporizator.
- Sumarul arată o realizare obținută, primul progres parțial și numărul actualizărilor suplimentare.
- Mesele și ecranul de rezultat nu mai rezervă un rând gol pentru notificarea inline.

## Calificare

Maven a rulat 32 de suite și 223 de teste, iar Android JVM 10 suite și 116 teste; toate au trecut fără eșecuri, erori sau omisiuni. Turneul nativ API 28 a trecut cu 138 de aserțiuni și 20 de capturi, iar smoke-ul APK-ului release semnat a trecut cu 84 de aserțiuni și 13 capturi.

Identitatea APK-ului, commitul calificat, probele și capturile sunt în [manifest](release-validation.json) și [indexul rapoartelor](reports/README.md).
