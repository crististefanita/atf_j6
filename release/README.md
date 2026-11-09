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
| 0.10.0 | [Dosarul candidatului](0.10.0/README.md) · `DRAFT` | [Manifestul candidatului](0.10.0/release-validation.json) |
| 0.9.0 | [Dosarul ediției](0.9.0/README.md) · `PREPARED` | [Manifestul ediției](0.9.0/release-validation.json) |
| 0.8.0 | [Dosarul ediției](0.8.0/README.md) · `PREPARED` | [Manifestul ediției](0.8.0/release-validation.json) |
| 0.7.0 | [Dosarul ediției](0.7.0/README.md) · `PREPARED` | [Manifestul ediției](0.7.0/release-validation.json) |
| 0.6.0 | [Dosarul ediției](0.6.0/README.md) · `PREPARED` | [Manifestul ediției](0.6.0/release-validation.json) |
| 0.5.0 | [Dosarul ediției](0.5.0/README.md) · calificat local | [Manifestul ediției](0.5.0/release-validation.json) |
| 0.4.1 | [Dosarul ediției](0.4.1/README.md) · `PREPARED` | [Manifestul ediției](0.4.1/release-validation.json) |
| 0.4.0 | [Dosarul ediției](0.4.0/README.md) · `PREPARED` | [Manifestul ediției](0.4.0/release-validation.json) |
| 0.3.1 | [Dosarul ediției](0.3.1/README.md) · `PREPARED` | [Manifestul ediției](0.3.1/release-validation.json) |
| 0.3.0 | [Dosarul ediției](0.3.0/README.md) · `PREPARED` | [Manifestul ediției](0.3.0/release-validation.json) |

<details>
<summary><strong>Observații editoriale în dosarele sigilate</strong></summary>

J0 continuă să raporteze aceste observații. Atribuirea la istoric cere fișierul, regula și linia din tabel, plus verificarea lungimii și a SHA-256 în manifestul ediției.

| Ediție | `README.md` | `reports/README.md` |
|---|---|---|
| 0.3.0 | `DOC-README-OVERALL` la 1 | `DOC-README-OVERALL` la 1 |
| 0.3.1 | `DOC-README-OVERALL` la 1 | `DOC-README-OVERALL` la 1 |

</details>

### Ediții retrase

Seriile 0.1.x și 0.2.x sunt retrase din distribuția curentă. Tagurile și istoricul Git păstrează proveniența edițiilor.

## Stări de publicare

| Stare | Înțeles |
|---|---|
| `PREPARED` | APK-ul semnat și verificările locale sunt în dosarul ediției; descărcarea anonimă nu este încă acceptată. |
| `PUBLISHED` | APK-ul și checksum-ul pot fi descărcate fără autentificare, iar digestul corespunde manifestului. |

În `DRAFT`, README-ul principal indică doar dosarul ediției. După calificare, păstrează două legături ordonate: pagina Release, apoi APK-ul. Limitările de acces și detaliile de verificare aparțin dosarului versiunii.

## Conținutul fiecărei ediții

- APK și checksum;
- versiune, package name, semnare și commit sursă;
- manifest de verificare și raportare;
- indexul rapoartelor Extent relevante.

[Procesul de build, semnare și publicare](../docs/release/ATF_Release.md) definește poarta comună pentru edițiile următoare.
