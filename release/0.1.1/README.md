# Hold'em Lab 0.1.1

## Pe scurt

Ediție patch Android pentru jocul local No-Limit Texas Hold’em. APK-ul semnat și calificarea sunt legate de commitul sursă și de probele Extent păstrate aici. Starea `PREPARED` reflectă descărcarea completă autentificată; verificarea anonimă a expirat fără status HTTP și nu este considerată trecută.

## Schimbări

- Reluarea mâinii sau turneului local după închiderea și redeschiderea aplicației, cu istoricul și timpul turneului validate la restaurare.
- Hint AI local explicabil, fără aplicarea automată a mutării; recapitularea cărților apare numai după încheierea mâinii.
- Acțiuni automate afișate în ritm controlat și colectarea mesei după decontare; apăsarea pentru mâna următoare în timpul animației se păstrează până la finalul acesteia.
- Compatibilitate runtime pentru Android minim declarat, verificată pe API 28; matricea de dispozitive și API-uri nu se deduce din această singură probă.

## Identitate

| Detaliu | Valoare |
|---|---|
| Versiune | `0.1.1` · cod Android `4` |
| Pachet | `ro.atf.holdem.android` |
| Android | API 26 minim · target SDK 36 |
| Semnare | Identitate release J6 · `debuggable=false` |
| Sursă calificată | [`ee1003e`](https://github.com/crististefanita/atf_j6/commit/ee1003e4a83fafd38ffcb24b92eb352e406799f6) |
| Stare | `PREPARED` · APK semnat, instalat și descărcat integral prin API autentificat |

## Calificare și descărcare

[Indexul rapoartelor](reports/README.md) enumeră cele șapte rapoarte Extent relevante acestei ediții, cu capturile native încorporate. [Manifestul de validare](release-validation.json) leagă APK-ul, commitul sursă, calificarea, asseturile GitHub și dovada descărcării complete. [Procesul de release](../../docs/release/ATF_Release.md) definește porțile de build, instalare și descărcare.

