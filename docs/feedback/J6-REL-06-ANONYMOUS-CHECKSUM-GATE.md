# J6-REL-06 · Dovada checksum-ului anonim

**Prioritate** P1

**Owner** Guvernanța de release J6

## Diferența observată

În 0.4.0, scriptul de verificare anonimă descarcă și validează doar APK-ul.

Guvernanța din 0.4.0 acceptă starea `PUBLISHED` pe baza acelei singure dovezi, deși criteriul de distribuție cere și disponibilitatea checksum-ului.

În acea ediție, un răspuns valid pentru APK poate trece poarta chiar dacă fișierul `.sha256` lipsește. Blocajul extern de vizibilitate este urmărit separat. [Scriptul de descărcare](../../scripts/Test-PublishedHoldemDownload.ps1) · [Guvernanța stării](../../holdem-app/src/test/java/ro/atf/j6/release/ReleaseGovernanceTest.java) · [J6-REL-01](J6-REL-01-PUBLIC-RELEASE-VISIBILITY.md)

## Criterii de acceptare

- Descarcă anonim ambele fișiere ale aceleiași ediții. Validează dimensiunea și SHA-256 pentru APK, apoi digestul și numele APK-ului din checksum.
- Emite o dovadă de succes după validarea ambelor fișiere. Guvernanța respinge o promovare nouă dacă lipsește dovada pentru checksum sau dacă ruta, versiunea ori digestul diferă.
- Acoperă prin teste de componentă APK valid cu checksum absent, digest greșit și răspuns trunchiat. Lasă dosarele istorice în forma inventariată și aplică noul contract la promovările ulterioare.
