# J6-REL-01 · Vizibilitatea publică a release-ului

P1, blocaj extern. Ownerul este titularul contului GitHub.

## Dovadă curentă

GitHub a publicat v0.3.0 cu APK și checksum. Browserul autentificat a descărcat integral APK-ul din secțiunea `Jocul`, iar API-ul autentificat a confirmat separat ambele fișiere. Proba anonimă a primit HTTP 404 la APK, deci ediția rămâne `PREPARED` în catalog. La 0.2.2, proba anonimă a primit 404 și pentru repo și pagina Release. [Dovada 0.3.0](../../release/0.3.0/release-validation.json) · [Dovada 0.2.2](../../release/0.2.2/release-validation.json)

[Probele 0.2.1](../../release/0.2.1/release-validation.json) consemnează și diferența față de un repo public de control. Procedura de diagnostic și trimiterea către GitHub apar în documentul owner. [Procesul de release](../release/ATF_Release.md#diagnostic-http-404-pentru-github-release)

## Criteriu de închidere

- După revizuirea vizibilității, `Test-PublishedHoldemDownload.ps1` citește linkul din README și descarcă anonim APK-ul integral, cu dimensiunea și digestul calificate.
- Manifestul ediției curente păstrează receipt-ul anonim și catalogul trece la `PUBLISHED`.
