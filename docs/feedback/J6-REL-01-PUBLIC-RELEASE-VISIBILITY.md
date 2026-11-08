# J6-REL-01 — Restabilirea descărcării publice pentru release

**Prioritate:** P1  
**Stare:** Blocat extern — necesită revizuirea GitHub pentru contul proprietarului

## Constatare

Release-ul `v0.1.0` există (ID `437875854`), iar APK-ul și checksum-ul sunt `uploaded`. API-ul autentificat confirmă repo-ul public, activ și nedraft. Descărcarea completă prin API a returnat `302 → 200`, 722.172 bytes și SHA-256 `646a6d5814534986923eff68a0d4172a38462166e4e64ff45ae9cacfed55a03f`.

Clientul fără cookies, `Authorization` sau credentiale implicite a primit `404` pentru profilul `crististefanita`, repo, pagina Release, API-ul public al Release-ului și URL-ul direct APK. Ulterior, pagina Release și APK-ul s-au deschis și s-au descărcat complet în browserul autentificat; dimensiunea și identitatea fișierului coincid cu manifestul. Accesul contului nu confirmă accesul anonim; ediția rămâne `PREPARED`.

Diagnostic detaliat: [manifestul 0.1.0](../../release/0.1.0/release-validation.json) și [procesul de release](../release/ATF_Release.md#diagnostic-http-404-pentru-github-release). GitHub documentează că unele restricții pot ascunde URL-ul profilului și vizibilitatea în căutare ([troubleshooting](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/preventing-unauthorized-access#troubleshooting)); cererea de revizuire urmează procedura [Appeal and Reinstatement](https://docs.github.com/en/site-policy/acceptable-use-policies/github-appeal-and-reinstatement).

## Owner și pas următor

Owner: titularul contului GitHub. Cereți revizuirea vizibilității publice pentru contul și repo-ul `crististefanita/atf_j6`, apoi repetați verificarea anonimă. README-ul oferă între timp linkurile Release și APK cu autentificarea explicită. Nu creați un release cu alt nume și nu mutați APK-ul pe o rută Raw: acestea nu repară cauza și ar ocoli poarta de publicare.

## Criterii de închidere

- Profilul, repo-ul și pagina Release sunt accesibile anonim.
- `Test-PublishedHoldemDownload.ps1` primește HTTP 200, descarcă exact 722.172 bytes și confirmă SHA-256-ul calificat fără cookies, Authorization sau credentiale implicite.
- Receipt-ul anonim fără secrete este păstrat în `release/0.1.0/`; indexul trece la `PUBLISHED` și README-ul principal păstrează pagina Release și asset-ul exact fără mențiunea că autentificarea este necesară.
