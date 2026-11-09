# Release Android Hold’em Lab

## Stări

| Stare | Criteriu |
|---|---|
| `DRAFT` | Versiune, note și guvernanță inspectabile; fără APK și fără link de descărcare. |
| `PREPARED` | Sursă fixată și curată, teste trecute, APK Release semnat cu identitatea J6, instalare sanity și inventar hash complet. |
| `PUBLISHED` | Tag-ul indică commit-ul calificat, APK-ul și checksum-ul sunt atașate și ambele descărcări anonime sunt verificate. |

APK-urile intermediare, build-urile, cache-urile, logurile și capturile brute rămân în `C:\work\holdem_back`. Dosarul ediției leagă pachetul selectat, checksum-ul, manifestul și rapoartele Extent cu imagini încorporate. APK-ul și checksum-ul rămân în afara Git și devin fișiere atașate release-ului.

## README principal și catalog

| Loc | Contract |
|---|---|
| `## Jocul` în `DRAFT` | O singură legătură, către dosarul ediției. |
| `## Jocul` după calificare | Exact două legături pentru `currentVersion`: pagina Release și APK-ul direct, în această ordine. |
| Dosarul ediției | Identitate, SHA-256, checksum, calificare, rapoarte și receipt-uri de distribuție. |
| Catalog și proces | Navigare tehnică din secțiunea de documentație a README-ului principal. |

- Guvernanța respinge rutele pentru altă versiune și textul suplimentar în blocul `Jocul`. Starea, dimensiunea și cerințele de autentificare se explică în dosarul ediției.
- Pentru `PREPARED`, păstrați dovada descărcării complete autentificate. Pentru `PUBLISHED`, adăugați dovada separată a descărcării anonime.
- Includeți `## Pe scurt` în README-ul ediției și în indexul rapoartelor înainte de calcularea hash-urilor. Păstrați fișierele deja inventariate la octet și documentați clarificările generale în acest ghid.

[Catalogul edițiilor](../../release/README.md)

### Audit editorial și dosare de ediție

| Domeniu | Ce intră | Cum tratăm constatările |
|---|---|---|
| Documentație activă | README-urile proiectului și modulelor, ghidurile, catalogul și documentele edițiilor aflate în pregătire. | Orice constatare J0 rămâne de rezolvat înainte de calificare. |
| Dovezi de ediție | Fișierele din dosarul `PREPARED` sau `PUBLISHED` inventariate individual în `release-validation.json`. Regula se aplică și dosarului ediției curente. | Păstrați fișierele la octet. Separați numai observațiile editoriale existente enumerate în catalog, după verificarea căii, lungimii și SHA-256. |

Rulați verificările J0 pe arborele complet și păstrați rezultatul brut. În rezumat, raportați separat observațiile active și pe cele editoriale deja consemnate în dosarele sigilate.

- O observație nouă sau un link rupt blochează acceptanța documentului afectat. Un fișier lipsă ori o abatere de integritate blochează folosirea dovezii respective și se raportează separat.
- Pentru o ediție nouă, verificați titlul, secțiunea `Pe scurt`, proza și legăturile din README-ul ediției și din indexul rapoartelor înainte de inventariere.
- Clarificările de proces se fac în documentația activă. Starea, hash-urile, rapoartele și legăturile dintre dovezile ediției rămân în dosarul ei.

[Catalogul și lista observațiilor păstrate](../../release/README.md#versiuni)

## Contextul RAG consultativ

Runnerul scrie în dosarul extern un context consultativ pentru diferența față de ediția precedentă. `CONTEXT_READY` indică surse și suite sugerate.

- `ReleaseImpactRag` ordonează lexical documentele și suitele afectate de fișierele modificate, adăugate sau șterse.
- În documentele normative, puneți `<!-- rag:contract identificator -->` imediat după valoarea din cod inline.
- În feedbackul activ, folosiți `<!-- rag:observed identificator -->` pentru aceeași regulă și unitate. Comparația citește commitul candidatului.

| Semnal | Ce verifică reviewerul |
|---|---|
| `DOCUMENT_CONTRADICTION` | Documente normative care declară valori diferite pentru aceeași regulă. |
| `STALE_FEEDBACK` | O observație activă care diferă de un contract coerent. |
| `MISSING_FEEDBACK_LINK` și `MISSING_FEEDBACK_ANCHOR` | Un fișier sau o ancoră Markdown locală din feedback care lipsește din sursa candidatului. |

- **Citări.** Fiecare referință identifică fișierul, liniile, fragmentul, commitul și digestul. Manifestul leagă contextul de calificare, iar dosarul ediției poate păstra copia JSON verificabilă.
- **Trasee propuse.** `suggestedNativeJourneys` citează căile din diff, opțiunea de rulare, commitul și hash-ul inventarului. `companionModes` enumeră probele native suplimentare ale aceleiași opțiuni.
- **Dovada execuției.** Reviewerul confirmă selecția. Receipt-ul, hash-ul APK-ului și raportul cu capturi verifică rularea efectivă.

[Teste pentru contextul de impact](../../holdem-app/src/test/java/ro/atf/j6/release/ReleaseImpactRagTest.java)

## Semnare J6

J6 folosește o identitate Android proprie, distinctă de J4/J5. Inițializarea unică este:

```powershell
.\scripts\Initialize-HoldemSigning.ps1
```

Cheia PKCS#12 și parola protejată cu DPAPI stau în `C:\work\holdem_back\infrastructure\signing`, cu ACL limitat la utilizatorul curent și SYSTEM. Nu copiați cheia, certificatul privat sau parola în Git, TMP, raport ori Release. Păstrați o copie de recuperare securizată; scriptul refuză identitate incompletă și nu rotește cheia automat.

## Calificare reproductibilă

Promovați doar dintr-un commit curat. Pregătiți un dosar extern nou și un repository Maven extern:

```powershell
$version = (Get-Content -Raw .\release\index.json | ConvertFrom-Json).currentVersion
$run = "C:\work\holdem_back\release-runs\j6-$version-<id-unic>"
$maven = 'C:\work\holdem_back\infrastructure\cache\maven'
$sourceCommit = (git rev-parse HEAD).Trim()
.\scripts\Build-HoldemRelease.ps1 -RunRoot $run -MavenRepository $maven
```

| Etapă | Ce verifică runnerul |
|---|---|
| Sursă | Fixează commitul și confruntă hash-urile copiei externe. |
| Componente | Rulează `mvn clean test`, Android JVM și lint. Publică rapoarte Extent separate pentru Maven și Android JVM. |
| APK | Construiește `sanity` și `release` cu identitatea J6. Verifică semnătura, certificatul, alinierea, pachetul, versiunea, SDK-ul și flag-ul debug. |
| Emulator rezervat | Rulează turneul reproductibil în `sanity`, care acceptă seed-ul fixture-ului, și smoke-ul de instalare și navigare pe APK-ul `release` exact, cu `debuggable=false`. |
| Raportare | `ReportArtifactInspector` verifică inventarul și structura Extent. Receipt-ul leagă APK-ul instalat de acțiuni, aserțiuni și capturile PNG originale, fiecare încorporată o singură dată. |

- Testele lipsă, duplicate, sărite sau eșuate, diferențele de hash și emulatorul ocupat blochează calificarea.
- Manifestul ediției leagă rapoartele selectate. Dosarul extern păstrează XML-urile și capturile originale.

[Catalogul edițiilor](../../release/README.md)

Pentru o ediție care schimbă lizibilitatea chenarelor, adăugați `-IncludeSeatMetrics` la runner. Acesta cere Android Accessibility Suite pe emulator și include dovada Extent pentru viewport-ul compact și TalkBack în aceeași calificare. [Strategia de testare Android](../testing/TEST_STRATEGY.md#interfața-android-locală)

Pentru o ediție care schimbă decontarea, premiul sau animația lui, includeți `-IncludeSettlementAward`. Pe același APK sanity, runnerul execută cele patru probe:

| Traseu | Contract | Câmp în calificare |
|---|---|---|
| `settlement-award` | Premiu și restituire la același loc | `settlementAward` |
| `settlement-multi-award` | Split și side pot, cu marker la fiecare loc | `settlementMultiAward` |
| `settlement-static-award` | Nume, sume și continuare cu animațiile oprite | `settlementStaticAward` |
| `settlement-compact-award` | Rezumat complet și marker doar la chenarul vizibil | `settlementCompactAward` |

Fiecare câmp leagă hash-ul APK-ului, receipt-ul și raportul `android-<traseu>-extent.html`. Un receipt incomplet sau un hash diferit blochează calificarea. Când există un receipt de eșec, runnerul păstrează Extent cu cauza.

- **Dosarul ediției.** Folosiți `qualification.json` și rapoartele din `$run\reports`. Selectați rapoartele relevante ediției.
- **Guvernanța offline.** `ReleaseGovernanceTest` verifică numele, starea, README-ul principal, legăturile, rutele, inventarul dovezilor, receipt-urile, politica Git și compatibilitatea cu `minSdk`. Rulează din checkout curat, fără APK și checksum local, și publică rapoarte Extent inspectate.
- **Runtime și distribuție.** Sanity-ul verifică separat compatibilitatea care se vede doar la execuție. Descărcarea se verifică prin ruta de distribuție după publicare.

### Compatibilitatea la API-ul Android minim

Guvernanța verifică sursele Java incluse în APK pentru metode cunoscute ca incompatibile cu `minSdk`, inclusiv din colecții, conversii text și formatare hexazecimală. Proba de execuție la nivelul minim verifică separat codul încărcat de Android. La eșec, păstrați diagnosticul de instrumentare și logcat în dosarul extern. [Criteriul runtime restant](../feedback/J6-REL-03-ANDROID-MIN-SDK-API-COMPATIBILITY.md)

### Auditul artefactelor după rulare

Păstrați inventarul `config/release-test-suites.json` sincronizat cu suitele Maven și Android JVM. Publisherul de componentă compară inventarul cu XML-urile și marchează suitele lipsă, duplicate ori nevalide. Publisherul nativ verifică separat turneul pe varianta sanity și instalarea pe varianta release.

```powershell
.\scripts\Test-HoldemReleaseEvidence.ps1 -RunRoot $run
```

Auditul citește manifestul calificării și verifică APK-ul, checksum-ul, integritatea rapoartelor și capturile originale în Extent. Dosarul extern păstrează și XML-urile, receipt-urile native și contextul RAG.

## Promovare și descărcare

`ReleaseGovernanceTest` verifică legăturile locale din dosarul ediției și octeții inventarului de dovezi. Proba anonimă citește URL-ul din secțiunea `Jocul` a README-ului și îl compară cu ruta canonică a versiunii înainte de GET.

1. Copiați APK-ul semnat și `.sha256` din `$run` în `release/<versiune>/`; selectați rapoartele Extent relevante și încorporați capturile, fără copii PNG redundante.
2. Verificați copia care va fi încărcată față de `qualification.json` din rularea calificată și față de versiunea și commit-ul selectate pentru tag. Folosiți raportul Extent din afara checkout-ului. Un fișier lipsă, un checksum necorespunzător sau o identitate diferită opresc promovarea.

   ```powershell
   $candidate = ".\release\$version"
   .\scripts\Test-HoldemPromotion.ps1 -CandidateDirectory $candidate `
       -Qualification "$run\qualification.json" -Version $version -SourceCommit $sourceCommit `
       -Report "$run\promotion-extent.html"
   ```

   `$sourceCommit` este commitul fixat înainte de calificare. Folosiți la upload copia verificată de poartă.

   - Poarta compară octeții, SHA-256 și checksum-ul cu receipt-ul de calificare. Confirmă că smoke-ul instalării a folosit același APK.
   - `-BuildSource` permite o construcție externă explicită la inspectarea unei calificări mai vechi.

3. Completați `release-validation.json` cu identitatea APK-ului, commit-ul sursă, calificarea și hash-urile documentelor. Păstrați raportul Extent al porții în dosarul extern al rulării.
4. Commit-ul sursă și tag-ul `v<versiune>` identifică exact binarul. Commit-ul ulterior al dovezilor poate actualiza catalogul și linkurile, dar nu mută tag-ul.
5. Atașați APK-ul verificat și checksum-ul la GitHub Release, publicați și confruntați asset-urile cu manifestul. După o descărcare autentificată completă, păstrați receipt-ul, marcați ediția `PREPARED` și aliniați linkurile din README.
6. Cu catalogul în `PREPARED`, rulați `scripts/Test-PublishedHoldemDownload.ps1`. Clientul verifică anonim ruta din README, redirect-ul, statusul final, lungimea completă, SHA-256 și octeții de identificare ZIP/APK.
   - Clientul verifică și fișierul checksum de la ruta aceleiași ediții. După PASS, păstrați ambele dovezi, `publicDownload` și `publicChecksumDownload`, apoi marcați `PUBLISHED`.
   - Dacă proba anonimă e blocată, păstrați `PREPARED` cu `authenticatedBrowserDownload` sau `authenticatedAssetDownload`.
   - Consemnați metoda și ruta canonică în receipt, fără credențiale sau URL semnat.

Pagina de Release și endpointul de download sunt distincte de `release/<versiune>/`, care rămâne catalogul tehnic și dovada locală.

### Planul de descărcare

Folosiți opțiunea `-PlanOnly` pentru un checkout care indică o ediție `PREPARED` sau `PUBLISHED`. Scriptul citește manifestul și secțiunea `Jocul` din README și afișează rutele canonice, dimensiunea și SHA-256, fără acces la rețea sau fișiere de ieșire.

```powershell
.\scripts\Test-PublishedHoldemDownload.ps1 -ProjectRoot C:\cale\ediție-publicată -PlanOnly
```

Verificarea online folosește un singur termen de trei minute pentru antete și fluxurile ambelor fișiere. Un plan confirmă identitatea țintelor. Pentru promovare sunt necesare dovezile descărcărilor reale.

Fiecare încercare de descărcare folosește un director nou. Un director existent este respins înainte de cererea HTTP, astfel încât dovada veche să rămână asociată rulării sale. Scriptul emite aceeași dovadă JSON în fișier și la ieșirea standard.

## Diagnostic HTTP 404 pentru GitHub Release

| Probă | Ce comparați |
|---|---|
| Anonimă | Profilul proprietarului, repo-ul, pagina Release, API-ul public și URL-ul direct al APK-ului. Clientul rulează fără cookies, `Authorization` sau credențiale implicite. |
| Autentificată | Repo public și activ, release publicat, asset `uploaded` și mărime. Descărcați integral cu `Accept: application/octet-stream` și confruntați SHA-256. |

Dacă probele anonime primesc `404` la nivel de profil, repo și release, iar proba autentificată confirmă assetul complet, păstrați starea `PREPARED`. Înregistrați cele două probe fără tokenuri sau URL-uri semnate și cereți GitHub să verifice vizibilitatea. Păstrați tagul, binarul și ruta canonică în timpul investigației. [Diagnostic GitHub](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/preventing-unauthorized-access#troubleshooting) · [Revizuirea restricțiilor](https://docs.github.com/en/site-policy/acceptable-use-policies/github-appeal-and-reinstatement)
