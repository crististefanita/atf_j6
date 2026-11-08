# Hold’em Lab 0.1.0

## Pe scurt

Prima ediție Android pentru No-Limit Texas Hold’em offline. Manifestul ediției deține identitatea APK-ului și starea verificărilor; `PREPARED` nu înseamnă că descărcarea publică anonimă a trecut.

## Identitate

| Detaliu | Valoare |
|---|---|
| APK | `HoldemLab-0.1.0.apk` |
| Versiune | `0.1.0` · cod Android `3` |
| Pachet | `ro.atf.holdem.android` |
| Android | API 26–36 declarate · target SDK 36 |
| Semnare | Release J6 · `debuggable=false` |
| Sursă calificată | `c4778a45751a5f579820c039e93d5997deb5800a` |
| Stare | `PREPARED` · verificări locale trecute; acces anonim blocat la ultima probă |

## Funcționalitate

- Meniu și masă pe UI-ul comun Whist/Rentz.
- Mână offline și turneu cu 4–6 locuri.
- Acțiuni validate de motor, adversari locali și rezultat la victorie sau eliminare.

## Acces și verificarea publicării

| Probă | Rezultat înregistrat |
|---|---|
| API GitHub autentificat | Repo-ul este public, release-ul nu este draft, iar asset-ul APK răspunde `302` urmat de `200`; dimensiune `722172` bytes. |
| Descărcare autenticată în browser | La `2026-11-08T06:38:01Z`, fișierul primit avea 722.172 bytes și identitatea din manifest. |
| Probe anonime | La `2026-11-07T22:16:42Z`, profilul, repo-ul, pagina Release, API-ul Release și APK-ul au răspuns `404`. |
| Decizie de publicare | Starea rămâne `PREPARED` până trece descărcarea completă fără cookies sau credentiale. |

Checksum-ul și certificatul se verifică în [manifestul ediției](release-validation.json). Receipt-ul autentificat nu substituie poarta de acces anonim.

## Calificare

[Indexul rapoartelor și acoperirea](reports/README.md) prezintă probele locale. [Procesul de release](../../docs/release/ATF_Release.md) definește verificarea semnării și a descărcării publice.

## Blocaj activ

[J6-REL-01](../../docs/feedback/J6-REL-01-PUBLIC-RELEASE-VISIBILITY.md) urmărește vizibilitatea anonimă. Diagnosticul tehnic și identitatea asset-ului sunt păstrate în manifest, nu în README-ul root.