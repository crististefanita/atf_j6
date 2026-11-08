# J6-REL-01 · Vizibilitatea publică a release-ului

P1, blocaj extern. Ownerul este titularul contului GitHub.

## Dovadă curentă

GitHub confirmă publicarea v0.2.2 și cele două fișiere atașate. În browserul autentificat, linkul direct din secțiunea `Jocul` a descărcat APK-ul integral. API-ul autentificat a confirmat separat APK-ul și checksum-ul. Clientul anonim a primit HTTP 404 pentru repo, pagina Release și ambele fișiere. Catalogul rămâne `PREPARED`. [Dovada 0.2.2](../../release/0.2.2/release-validation.json)

[Probele 0.2.1](../../release/0.2.1/release-validation.json) consemnează și diferența față de un repo public de control. Procedura de diagnostic și trimiterea către GitHub apar în documentul owner. [Procesul de release](../release/ATF_Release.md#diagnostic-http-404-pentru-github-release)

## Criteriu de închidere

- După revizuirea vizibilității, `Test-PublishedHoldemDownload.ps1` citește linkul din README și descarcă anonim APK-ul integral, cu dimensiunea și digestul calificate.
- Manifestul ediției curente păstrează receipt-ul anonim și catalogul trece la `PUBLISHED`.
