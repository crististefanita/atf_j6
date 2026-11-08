# Rapoarte 0.1.0

## Pe scurt

Acest index descrie rapoartele Extent incluse în pachetul 0.1.0. Capturile UI sunt inline; jurnalele brute, cache-urile și directoarele de build rămân sub `<external-release-run-root>`.

| Raport | Acoperire | Rezultat în pachet |
|---|---|---|
| `android-release-smoke-extent.html` | Instalarea APK-ului Release și navigarea principală | PASS · API 28 · 7 aserțiuni · 2 capturi inline |
| `android-tournament-extent.html` | Turneu determinist de patru locuri până la victorie | PASS · API 28 · 41 aserțiuni · 5 capturi inline |
| `j6-library-component.html` | Contractele componentelor și proveniența bibliotecilor | PASS · 1 test |
| `J6ReleaseGovernance.html` | Versiune, APK, semnare și pachet `PREPARED` | PASS · 1 test |
| `J6ReleaseDownloadRouteGovernance.html` | Politica rutelor și poarta descărcării anonime | PASS · 1 test |

## Calificarea inclusă

- Maven: 13 suite și 76 teste.
- Android JVM: 2 suite și 12 teste.
- Lint: fără avertismente.
- Sanity Android API 28: două scenarii, 48 aserțiuni și 7 capturi.
- Total: 88 teste, fără eșecuri, erori sau teste omise.

Aceste rapoarte verifică pachetul local și flow-urile incluse. Descărcarea publică anonimă este o poartă separată și rămâne blocată la starea `PREPARED`.

[Manifestul de release](../release-validation.json) fixează APK-ul și proveniența; [manifestul calificării](../qualification.json) fixează identitatea rapoartelor și integritatea lor. Capturile nu sunt copiate separat în pachet.

<details>
<summary><strong>Rulări UI anterioare ediției 0.1.0</strong></summary>

Aceste două verificări precedă candidatul Release 0.1.0 și nu intră în totalul celor 88 de teste:

- Prima calificare UI: 33 teste Maven, 5 componente Android JVM și 78 comparații pe API 28; verifică raise, străzi și conservarea jetoanelor.
- Calificarea compactă: 36 teste Maven, 7 componente Android JVM și 88 comparații pe API 28 la font 2,0; verifică șase locuri, tastatura dialogului și ordinea heads-up.

</details>

Evaluarea AI rămâne exploratorie și nu este poartă Android. Limitele strategiei sunt urmărite în [J6-AI-04](../../../docs/feedback/J6-AI-04-PUBLIC-SHOVE-ABLATION.md).