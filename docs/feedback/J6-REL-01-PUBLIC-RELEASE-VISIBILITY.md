# J6-REL-01 · Vizibilitatea publică a release-ului

P1, blocaj extern. Ownerul este titularul contului GitHub.

## Dovadă curentă

- API-ul autentificat a descărcat integral APK-ul și checksum-ul ediției 0.6.0.
- Ambele rute anonime ale ediției au răspuns HTTP 404. Linkul direct din README a răspuns HTTP 404 și la proba cu token REST. Descărcarea ambelor fișiere prin endpointurile REST ale asset-urilor a trecut. [Manifestul 0.6.0](../../release/0.6.0/release-validation.json) · [Tichetul GitHub #4973045](https://support.github.com/ticket/4973045)

Procedura de diagnostic și trimiterea către GitHub apar în documentul owner. [Procesul de release](../release/ATF_Release.md#diagnostic-http-404-pentru-github-release)

## Criteriu de închidere

- După revizuirea vizibilității, verifică accesul anonim la profil și la cele șase repo-uri, apoi descarcă integral APK-ul din README și fișierul `.sha256` de lângă el. Runnerul verifică ambele fișiere și emite dovezile împreună.
- Manifestul ediției curente păstrează receipt-ul anonim și catalogul trece la `PUBLISHED`.
