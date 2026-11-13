# J6-REL-09 · Integritatea planului nativ

P2. Ownerul este procesul de calificare Android.

## Lacună

Validatorul verifică digestul planului, identitatea candidatului, probele obligatorii și unicitatea destinațiilor. Reproducerea offline a păstrat `-IncludeBettingLedger`, a scos `betting-ledger-talkback` și a recalculat SHA-256. Validatorul a acceptat planul, iar auditorul caută dovezi doar pentru intrările prezente în el. [Validatorul](../../scripts/HoldemNativeQualification.ps1) · [Auditorul](../../scripts/Test-HoldemReleaseEvidence.ps1)

## Criteriu de închidere

- Fixează inventarul din commitul candidatului și reconstruiește selecția din scop și opțiuni. Confruntă modurile, profilele, variantele APK, cheile și rapoartele în ordinea canonică.
- Probează un plan cu SHA-256 recalculat în care lipsește un profil companion, unul cu opțiune duplicată și unul cu variantă APK modificată. Toate trebuie respinse înainte de promovare. Auditează dosarele istorice cu inventarul ediției lor. [Testele offline](../../scripts/tests/HoldemNativeEvidenceAudit.Tests.ps1)
