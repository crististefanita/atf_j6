# Catalog APK Android

## Pe scurt

Acest catalog separă ghidurile de lucru de dosarele care atestă o ediție Android. `index.json` selectează ediția curentă, iar manifestul ediției consemnează starea și identitatea pachetului.

## Documentație activă

- [Procesul de release](../docs/release/ATF_Release.md) deține regulile de build, semnare, promovare și descărcare.
- [Auditul editorial și dosarele de ediție](../docs/release/ATF_Release.md#audit-editorial-și-dosare-de-ediție) definește criteriile de verificare pentru ghiduri, candidați și dovezi sigilate.
- [Indexul ediției curente](index.json) selectează versiunea pe care trebuie să o indice linkurile din README-ul principal.

## Versiuni

Dosarele de mai jos păstrează dovezile edițiilor. Când un fișier este inventariat, SHA-256 și lungimea din manifest îi fixează conținutul, inclusiv pentru ediția selectată în catalog.

| Versiune | Documentație | Identitatea pachetului |
|---|---|---|
| 0.19.2 | [Dosarul ediției](0.19.2/README.md) · `PREPARED` | [Manifestul ediției](0.19.2/release-validation.json) |
| 0.19.1 | [Dosarul ediției](0.19.1/README.md) · `PREPARED` | [Manifestul ediției](0.19.1/release-validation.json) |
| 0.19.0 | [Dosarul ediției](0.19.0/README.md) · `PREPARED` | [Manifestul ediției](0.19.0/release-validation.json) |
| 0.18.0 | [Dosarul ediției](0.18.0/README.md) · `PREPARED` | [Manifestul ediției](0.18.0/release-validation.json) |
| 0.17.2 | [Dosarul ediției](0.17.2/README.md) · `PREPARED` | [Manifestul ediției](0.17.2/release-validation.json) |
| 0.17.1 | [Dosarul ediției](0.17.1/README.md) · `PREPARED` | [Manifestul ediției](0.17.1/release-validation.json) |
| 0.17.0 | [Dosarul ediției](0.17.0/README.md) · `PREPARED` | [Manifestul ediției](0.17.0/release-validation.json) |
| 0.16.0 | [Dosarul ediției](0.16.0/README.md) · `PREPARED` | [Manifestul ediției](0.16.0/release-validation.json) |
| 0.15.0 | [Dosarul ediției](0.15.0/README.md) · `PREPARED` | [Manifestul ediției](0.15.0/release-validation.json) |
| 0.14.1 | [Dosarul ediției](0.14.1/README.md) · `PREPARED` | [Manifestul ediției](0.14.1/release-validation.json) |
| 0.14.0 | [Dosarul ediției](0.14.0/README.md) · `PREPARED` | [Manifestul ediției](0.14.0/release-validation.json) |
| 0.13.0 | [Dosarul ediției](0.13.0/README.md) · retrasă din distribuție | [Manifestul ediției](0.13.0/release-validation.json) |
| 0.12.0 | [Dosarul ediției](0.12.0/README.md) · PREPARED | [Manifestul ediției](0.12.0/release-validation.json) |
| 0.11.1 | [Dosarul ediției](0.11.1/README.md) · `PREPARED` | [Manifestul ediției](0.11.1/release-validation.json) |
| 0.11.0 | [Dosarul ediției](0.11.0/README.md) · `PREPARED` | [Manifestul ediției](0.11.0/release-validation.json) |
| 0.10.0 | [Dosarul ediției](0.10.0/README.md) · `PREPARED` | [Manifestul ediției](0.10.0/release-validation.json) |
| 0.9.0 | [Dosarul ediției](0.9.0/README.md) · `PREPARED` | [Manifestul ediției](0.9.0/release-validation.json) |
| 0.8.0 | [Dosarul ediției](0.8.0/README.md) · `PREPARED` | [Manifestul ediției](0.8.0/release-validation.json) |
| 0.7.0 | [Dosarul ediției](0.7.0/README.md) · `PREPARED` | [Manifestul ediției](0.7.0/release-validation.json) |
| 0.6.0 | [Dosarul ediției](0.6.0/README.md) · `PREPARED` | [Manifestul ediției](0.6.0/release-validation.json) |
| 0.5.0 | [Dosarul ediției](0.5.0/README.md) · calificat local | [Manifestul ediției](0.5.0/release-validation.json) |
| 0.4.1 | [Dosarul ediției](0.4.1/README.md) · `PREPARED` | [Manifestul ediției](0.4.1/release-validation.json) |
| 0.4.0 | [Dosarul ediției](0.4.0/README.md) · `PREPARED` | [Manifestul ediției](0.4.0/release-validation.json) |
| 0.3.1 | [Dosarul ediției](0.3.1/README.md) · `PREPARED` | [Manifestul ediției](0.3.1/release-validation.json) |
| 0.3.0 | [Dosarul ediției](0.3.0/README.md) · `PREPARED` | [Manifestul ediției](0.3.0/release-validation.json) |

### Integritatea edițiilor istorice

Dosarele sigilate păstrează calificarea, manifestele și rapoartele așa cum au fost publicate. Observațiile dintr-o ediție veche nu definesc calitatea documentației active; schimbările de proces se fac în ghidurile curente, iar rezultatele rămân în dosarul versiunii respective.

### Ediții retrase

Indexul selectează ediția curentă, iar pagina GitHub arată edițiile disponibile pentru descărcare. [Release-uri GitHub](https://github.com/crististefanita/atf_j6/releases)

Pentru 0.13.0, tagul și dosarul sigilat păstrează sursa și dovezile calificării. Linkul de descărcare din dosarul sigilat consemnează distribuția de la data publicării. [Tagul v0.13.0](https://github.com/crististefanita/atf_j6/tree/v0.13.0)

## Stări de publicare

| Stare | Înțeles |
|---|---|
| `PREPARED` | Release-ul GitHub conține APK-ul semnat și checksum-ul; identitatea lor și descărcarea autentificată sunt verificate. Aceasta este politica curentă a proiectului. |
| `PUBLISHED` | Stare istorică pentru edițiile care au cerut separat calificarea accesului anonim. Nu se testează implicit și nu se declară pentru edițiile curente. |

După calificare, README-ul principal păstrează pagina Release urmată de linkul direct la APK.

- Edițiile curente se verifică numai cu autentificare.
- Verificarea anonimă se face doar la o cerere explicită separată.
- Dosarul ediției descrie accesul și păstrează dovezile.

## Conținutul fiecărei ediții

- APK și checksum;
- versiune, package name, semnare și commit sursă;
- manifest de verificare și raportare;
- indexul rapoartelor Extent relevante.

[Procesul de build, semnare și publicare](../docs/release/ATF_Release.md) definește poarta comună pentru edițiile următoare.

