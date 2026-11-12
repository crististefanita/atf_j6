# J6-REL-08 · Auditul probelor native opționale

## Pe scurt

Auditul independent verifică hash-ul rapoartelor inventariate. Pentru calificarea `FULL`, recitește receipt-urile și capturile originale ale turneului și ale instalării, iar runnerul verifică probele opționale în timpul execuției. Auditul ulterior trebuie să poată reconstrui și verdictul fiecărei probe opționale. [Auditul dovezilor](../../scripts/Test-HoldemReleaseEvidence.ps1) · [Runnerul de release](../../scripts/Build-HoldemRelease.ps1)

## Criteriu de închidere

- Pentru fiecare profil opțional din calificare, confruntă identitatea APK-ului instalat, receipt-ul, verdictul, numărul de aserțiuni și capturile originale din Extent.
- Adaugă un test care respinge un profil lipsă sau asociat altui APK și un test care respinge o captură schimbată. Păstrează verificarea dosarelor sigilate conform versiunii lor de manifest.
