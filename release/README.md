# Catalog APK Android

## Pe scurt

Acest director indexează edițiile Android. Dosarul unei versiuni deține identitatea binarului, starea publicării și dovezile specifice acelei ediții.

## Versiuni

| Versiune | Documentație | Identitatea pachetului |
|---|---|---|
| 0.1.0 | [Dosarul ediției](0.1.0/README.md) | [Manifestul de validare](0.1.0/release-validation.json) |

[`index.json`](index.json) selectează versiunea curentă și starea ei. Nu duplicați în acest index numărul testelor, hash-urile sau rezultatele unei verificări.

## Stări de publicare

| Stare | Înțeles |
|---|---|
| `PREPARED` | APK-ul semnat și verificările locale sunt în dosarul ediției; descărcarea anonimă nu este încă acceptată. |
| `PUBLISHED` | APK-ul poate fi descărcat complet fără autentificare, iar identitatea binarului corespunde manifestului. |

README-ul principal al proiectului păstrează două legături ordonate: pagina Release, apoi APK-ul. Limitările de acces și detaliile de verificare aparțin dosarului versiunii.

## Conținutul fiecărei ediții

- APK și checksum;
- versiune, package name, semnare și commit sursă;
- manifest de verificare și raportare;
- indexul rapoartelor Extent relevante.

[Procesul de build, semnare și publicare](../docs/release/ATF_Release.md) definește poarta comună pentru edițiile următoare.