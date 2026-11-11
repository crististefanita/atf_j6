# J6-REL-01 · Vizibilitatea publică a release-ului

Investigație externă. Ownerul este titularul contului GitHub. Verificarea vizibilității publice se reia la cerere și este separată de calificarea ediției curente.

## Dovadă curentă

- Verificarea autentificată citește linkul din README-ul remote și descarcă integral APK-ul și checksum-ul prin API-ul GitHub. Manifestul ediției selectate în index consemnează metoda și verificarea hash-ului. [Indexul ediției](../../release/index.json) · [Catalogul edițiilor](../../release/README.md)
- Răspunsurile HTTP 404 observate în acces anonim sunt consemnate în tichet. [Tichetul GitHub #4973045](https://support.github.com/ticket/4973045)

Procedura de diagnostic și trimiterea către GitHub apar în documentul owner. [Procesul de release](../release/ATF_Release.md#diagnostic-http-404-pentru-github-release)

## Criteriu de închidere

- Când se reia investigația, verifică accesul anonim la profil și la repo-urile afectate. Fixează ediția și commitul README-ului remote. Descarcă integral APK-ul și checksum-ul fără autentificare. Confruntă lungimea și SHA-256 cu dosarul aceleiași ediții și păstrează rezultatul separat de descărcarea autentificată.
- Promovează catalogul la `PUBLISHED` după verificarea anonimă a ambelor asset-uri. Păstrează diagnosticul în dosarul ediției și rutele pentru jucători în README.
