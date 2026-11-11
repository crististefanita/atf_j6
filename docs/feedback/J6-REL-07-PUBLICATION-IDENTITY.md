# J6-REL-07 · Proveniența descărcării

P1. Ownerul este guvernanța release-ului Android.

## Ce lipsește

Proba autentificată validează linkul din README, APK-ul și checksum-ul. Dovada reține ref-ul și blobul README. Lipsește legătura cu commitul README, commitul indicat de tag și hash-ul manifestului de calificare. [Verificatorul descărcării](../../scripts/Test-PublishedHoldemDownload.ps1)

## Criterii de închidere

- În proba de componentă, schimbă separat commitul indicat de tag și raportul inventariat, păstrând URL-ul și octeții APK-ului. Verificatorul respinge ambele cazuri și leagă dovada acceptată de commitul README, commitul tagului și hash-ul calificării.
- Când commitul binarului diferă de cel indicat de tag, poarta cere dovada de continuare. O republicare exclusivă a dovezilor păstrează calificarea dacă diferența este verificată. O schimbare de produs între cele două commituri cere recalificare. [Poarta de promovare](../../holdem-app/src/test/java/ro/atf/j6/release/ReleasePromotionGate.java)
