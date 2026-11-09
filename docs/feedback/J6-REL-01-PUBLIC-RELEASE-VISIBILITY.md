# J6-REL-01 · Vizibilitatea publică a release-ului

P1, blocaj extern. Ownerul este titularul contului GitHub.

## Dovadă curentă

- API-ul autentificat a descărcat integral APK-ul și checksum-ul ediției 0.4.0.
- Ruta anonimă a răspuns HTTP 504 pentru APK și HTTP 504 pentru checksum. Catalogul rămâne PREPARED. [Dovada ediției](../../release/0.4.0/release-validation.json)

Procedura de diagnostic și trimiterea către GitHub apar în documentul owner. [Procesul de release](../release/ATF_Release.md#diagnostic-http-404-pentru-github-release)

## Criteriu de închidere

- După revizuirea vizibilității, `Test-PublishedHoldemDownload.ps1` citește linkul din README și descarcă anonim APK-ul integral, cu dimensiunea și digestul calificate. Descărcarea anonimă a fișierului `.sha256` trebuie să confirme același digest.
- Manifestul ediției curente păstrează receipt-ul anonim și catalogul trece la `PUBLISHED`.
