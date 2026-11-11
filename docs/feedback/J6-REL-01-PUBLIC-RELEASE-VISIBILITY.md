# J6-REL-01 · Vizibilitatea publică a release-ului

Investigație externă. Ownerul este titularul contului GitHub. Verificarea vizibilității publice se reia la cerere și este separată de calificarea ediției curente.

## Dovadă curentă

- Verificarea autentificată citește linkul din README-ul remote și descarcă integral APK-ul și checksum-ul prin API-ul GitHub. Manifestul ediției consemnează metoda și verificarea hash-ului. [Catalogul edițiilor](../../release/README.md)
- Răspunsurile HTTP 404 observate în acces anonim sunt consemnate în tichet. [Tichetul GitHub #4973045](https://support.github.com/ticket/4973045)

Procedura de diagnostic și trimiterea către GitHub apar în documentul owner. [Procesul de release](../release/ATF_Release.md#diagnostic-http-404-pentru-github-release)

## Criteriu de închidere

- Când se reia investigația, verifică accesul anonim la profil și la cele șase repo-uri. Verifică integral APK-ul din README și fișierul `.sha256` al aceleiași ediții, cu dovezi separate de runnerul autentificat.
- Manifestul ediției curente păstrează receipt-ul anonim și catalogul trece la `PUBLISHED`.
