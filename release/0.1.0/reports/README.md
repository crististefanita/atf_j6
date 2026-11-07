# Rapoarte 0.1.0

Pachetul păstrează cele cinci rapoarte Extent care justifică APK-ul și gate-urile de release. Intermediarii, cache-urile și logurile brute rămân în `C:\work\holdem_back\release-runs`.

| Raport Extent | Acoperire | Rezultat |
|---|---|---|
| `android-release-smoke-extent.html` | Instalarea APK-ului Release și navigarea principală | PASS · API 28 · 7 aserțiuni · 2 capturi încorporate |
| `android-tournament-extent.html` | Turneu determinist cu 4 locuri până la victorie | PASS · API 28 · 41 aserțiuni · 5 capturi încorporate |
| `j6-library-component.html` | Contracte componente și proveniența bibliotecilor comune | PASS · 1 test |
| `J6ReleaseGovernance.html` | Versiune, APK, semnare, README și pachet PREPARED | PASS · 1 test |
| `J6ReleaseDownloadRouteGovernance.html` | Politica rutelor și verificarea descărcării anonime | PASS · 1 test |

Calificare curată: Maven 13 suite / 76 teste; Android JVM 2 suite / 12 teste; Lint fără avertismente; Android API 28: două sanity-uri, 48 aserțiuni și 7 capturi. Total: 88 teste, zero eșecuri, erori sau teste omise. Gate-ul Maven rerulat pe candidatul PREPARED a trecut la `2026-11-08 00:05:27 +02:00`.

Capturile Android sunt încorporate în Extent, fără copii PNG redundante. Mărimile și SHA-256-urile celor cinci rapoarte sunt în `../qualification.json`, iar integritatea fișierelor de release este în `../release-validation.json`. Evaluarea strategiei AI rămâne exploratorie și nu este gate Android; limitele și proveniența ei sunt în [J6-AI-04](../../../docs/feedback/J6-AI-04-PUBLIC-SHOVE-ABLATION.md).
