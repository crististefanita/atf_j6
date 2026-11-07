# Release Android Hold’em Lab

## Stări

| Stare | Criteriu |
|---|---|
| `DRAFT` | Versiune, note și guvernanță inspectabile; fără APK și fără link de descărcare. |
| `PREPARED` | Sursă fixată și curată, teste trecute, APK Release semnat cu identitatea J6, instalare sanity și inventar hash complet. |
| `PUBLISHED` | Tag-ul indică commit-ul calificat, asset-ul este atașat, iar descărcarea publică are bytes și SHA-256 verificate. |

APK-urile intermediare, build-urile, cache-urile, logurile și capturile brute rămân în `C:\work\holdem_back`. Numai pachetul selectat, checksum-ul, manifestul și rapoartele Extent cu imaginile încorporate intră în `release/<versiune>/`. APK-ul și checksum-ul sunt excluse din Git; la publicare devin assets GitHub.

## Semnare J6

J6 folosește o identitate Android proprie, distinctă de J4/J5. Inițializarea unică este:

```powershell
.\scripts\Initialize-HoldemSigning.ps1
```

Cheia PKCS#12 și parola protejată cu DPAPI stau în `C:\work\holdem_back\infrastructure\signing`, cu ACL limitat la utilizatorul curent și SYSTEM. Nu copiați cheia, certificatul privat sau parola în Git, TMP, raport ori Release. Păstrați o copie de recuperare securizată; scriptul refuză identitate incompletă și nu rotește cheia automat.

## Calificare reproductibilă

Promovați doar dintr-un commit curat. Pregătiți un dosar extern nou și un repository Maven extern:

```powershell
$run = 'C:\work\holdem_back\release-runs\j6-0.1.0-<id-unic>'
$maven = 'C:\work\holdem_back\infrastructure\cache\maven'
.\scripts\Build-HoldemRelease.ps1 -RunRoot $run -MavenRepository $maven
```

Runnerul fixează commit-ul, confruntă hash-urile snapshot-ului, rulează `mvn clean test`, Android JVM, lint și variantele Android `sanity`/`release` în copia externă. Ambele sunt semnate cu identitatea J6; numai `sanity` este debuggable și acceptă seed-ul fixture-ului. APK-ul distribuibil rămâne `debuggable=false`. Build-ul verifică semnătura, certificatul J6, alinierea, package/version/SDK și flag-ul debug. Pe emulatorul rezervat execută turneul reproductibil în `sanity` și navigarea de intrare în APK-ul `release` exact. Receipt-urile includ hash-ul APK-ului instalat și sunt publicate în Extent, împreună cu acțiunile, aserțiunile și capturile PNG transmise de instrumentare. Publicarea verifică raportul HTML cu un profil declarat pentru scenariile reale și compară fiecare captură încorporată cu octeții PNG originali, exact o dată. Orice test lipsă, skip, FAIL, drift, instalare ocupată ori hash diferit oprește calificarea.

Output-ul `qualification.json` și rapoartele din `$run\reports` sunt sursa pentru dosarul ediției. Guvernanța offline din `ReleaseGovernanceTest` verifică numele, starea, linkurile, checksum-ul și politica Git. Guvernanța de descărcare se execută separat numai după publicare; nu se maschează cu un rezultat local.

## Promovare și descărcare

1. Copiați APK-ul semnat și `.sha256` din `$run` în `release/<versiune>/`; selectați rapoartele Extent relevante și încorporați capturile, fără copii PNG redundante.
2. Completați `release-validation.json` cu APK bytes/SHA/certificat, commit-ul sursă, calificarea și hash-urile documentelor. Marcați `PREPARED` numai după reverificarea fișierului copiat.
3. Commit-ul sursă și tag-ul `v<versiune>` identifică exact binarul. Commit-ul ulterior al dovezilor poate actualiza catalogul și linkurile, dar nu mută tag-ul.
4. Atașați APK-ul și checksum-ul la GitHub Release, publicați și comparați asset-urile cu manifestul.
5. Cu catalogul în `PREPARED`, rulați `scripts/Test-PublishedHoldemDownload.ps1`. Clientul HTTP nu trimite cookies, Authorization sau credentiale implicite; verifică redirect-ul GitHub, statusul final, lungimea completă, SHA-256 și semnătura ZIP/APK în copia externă. Copiați numai receipt-ul JSON în dosarul ediției, completați `publicDownload`, marcați `PUBLISHED` și legați în README pagina release-ului și asset-ul direct.

Pagina de Release și endpointul de download sunt distincte de `release/<versiune>/`, care rămâne catalogul tehnic și dovada locală.
## Diagnostic HTTP 404 pentru GitHub Release

Un `404` la asset nu demonstrează singur că APK-ul lipsește. Înainte de orice reîncărcare, rulează cererea anonimă fără cookies, `Authorization` sau credentiale implicite și compară profilul proprietarului, repo-ul, pagina Release, API-ul public al Release-ului și URL-ul direct APK. Apoi verifică separat, autentificat, `private=false`, `disabled=false`, Release `draft=false`, asset `uploaded` și mărimea; descarcă asset-ul prin API cu `Accept: application/octet-stream` și compară toți bytes/SHA-256.

Dacă API-ul autentificat arată repo public și asset complet, dar profilul, repo-ul și Release-ul întorc anonim `404`, oprește promovarea în `PUBLISHED`. Înregistrează ambele probe fără token sau URL-uri semnate și cere GitHub să verifice vizibilitatea proprietarului/conținutului. Nu schimba numele APK-ului, nu îl oglindi pe alt host și nu înlocui Release-ul cu o rută Raw: acestea nu repară vizibilitatea contului. GitHub documentează că o restricție poate ascunde URL-ul profilului și vizibilitatea în căutare ([troubleshooting](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/preventing-unauthorized-access#troubleshooting)); restricțiile de vizibilitate pot fi contestate prin [Appeal and Reinstatement](https://docs.github.com/en/site-policy/acceptable-use-policies/github-appeal-and-reinstatement).
