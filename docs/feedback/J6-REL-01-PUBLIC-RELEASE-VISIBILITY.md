# J6-REL-01 — Restabilirea descărcării publice pentru release

**Prioritate:** P1  
**Stare:** Blocat extern — necesită revizuirea GitHub pentru contul proprietarului

## Constatare

Release-ul `v0.1.0` există (ID `437875854`), iar APK-ul și checksum-ul sunt `uploaded`. API-ul autentificat confirmă repo-ul public, activ și nedraft. Descărcarea completă prin API a returnat `302 → 200`, 722.172 bytes și SHA-256 `646a6d5814534986923eff68a0d4172a38462166e4e64ff45ae9cacfed55a03f`.

Clientul fără cookies, `Authorization` sau credentiale implicite a primit `404` pentru profilul `crististefanita`, repo, pagina Release, API-ul public al Release-ului și URL-ul direct APK. Ulterior, pagina Release și APK-ul s-au deschis și s-au descărcat complet în browserul autentificat; dimensiunea și identitatea fișierului coincid cu manifestul. Accesul contului nu confirmă accesul anonim; ediția rămâne `PREPARED`.

### Ediția 0.1.1

Release-ul `v0.1.1` a fost publicat cu APK-ul și checksum-ul încărcate. API-ul autentificat a descărcat assetul APK cu `HTTP 200`; fișierul complet corespunde binarului calificat. În această sesiune nu exista o fereastră de browser autentificată. Cererile anonime directe pentru pagina Release și APK au expirat după 20 de secunde, fără status HTTP; scriptul de acceptanță nu a produs receipt. Rezultatul anonim este **neconcludent**, nu `404`, deci starea rămâne `PREPARED`.

Diagnostic detaliat: [manifestul 0.1.0](../../release/0.1.0/release-validation.json) și [procesul de release](../release/ATF_Release.md#diagnostic-http-404-pentru-github-release). GitHub documentează că unele restricții pot ascunde URL-ul profilului și vizibilitatea în căutare ([troubleshooting](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/preventing-unauthorized-access#troubleshooting)); cererea de revizuire urmează procedura [Appeal and Reinstatement](https://docs.github.com/en/site-policy/acceptable-use-policies/github-appeal-and-reinstatement).

### Ediția 0.2.0

GitHub confirmă release-ul publicat și ambele fișiere încărcate. Clientul autentificat a verificat integral APK-ul și checksum-ul. Scriptul de acceptanță și clientul anonim fără proxy au primit HTTP 404 pentru APK. Clientul fără proxy a primit 404 și pentru profil, repo, API-ul release-ului și checksum, iar repo-ul public folosit drept control a răspuns cu HTTP 200. Aceste observații confirmă efectul asupra accesului anonim. Cauza exactă necesită verificare la GitHub. [Manifestul ediției](../../release/0.2.0/release-validation.json)

### Ediția 0.2.1

GitHub confirmă publicarea și ambele fișiere atașate. Descărcarea autentificată a APK-ului și checksum-ului a trecut verificarea integrală. Clientul anonim a primit HTTP 404 pentru profil, repo, release și ambele fișiere, în timp ce repo-ul public de control a răspuns cu HTTP 200. [Manifestul ediției](../../release/0.2.1/release-validation.json)

## Remediere

Titularul contului GitHub poate cere revizuirea vizibilității publice pentru `crististefanita/atf_j6`. După revizuire, repetați verificarea anonimă pe rutele canonice ale ediției. Dosarul ediției consemnează între timp rezultatele autentificate și anonime.

## Criterii de închidere

- Profilul, repo-ul și pagina Release sunt accesibile anonim.
- `Test-PublishedHoldemDownload.ps1` primește HTTP 200, descarcă APK-ul curent integral și confirmă identitatea din manifest fără cookies, Authorization sau credentiale implicite.
- Receipt-ul anonim fără secrete este păstrat în dosarul versiunii curente; numai atunci indexul trece la `PUBLISHED`. README-ul principal păstrează numai pagina Release și assetul direct.
