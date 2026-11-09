# J6-REL-01 · Vizibilitatea publică a release-ului

P1, blocaj extern. Ownerul este titularul contului GitHub.

## Dovadă curentă

- API-ul autentificat a descărcat integral APK-ul și checksum-ul ediției 0.4.0.
- Calificarea distribuției a înregistrat HTTP 504. Reverificarea directă, fără autentificare și fără proxy, a primit HTTP 404 pentru profil, toate cele șase repo-uri publice, APK și checksum. Controlul public a răspuns HTTP 200. Ediția rămâne PREPARED. [Dovada ediției](../../release/0.4.0/release-validation.json) · [Tichetul GitHub #4973045](https://support.github.com/ticket/4973045)

Procedura de diagnostic și trimiterea către GitHub apar în documentul owner. [Procesul de release](../release/ATF_Release.md#diagnostic-http-404-pentru-github-release)

## Criteriu de închidere

- După revizuirea vizibilității, verifică accesul anonim la profil și la cele șase repo-uri, apoi descarcă integral APK-ul din README și fișierul `.sha256` de lângă el. Automatizarea dovezii pentru ambele fișiere este urmărită separat. [J6-REL-06](J6-REL-06-ANONYMOUS-CHECKSUM-GATE.md)
- Manifestul ediției curente păstrează receipt-ul anonim și catalogul trece la `PUBLISHED`.
