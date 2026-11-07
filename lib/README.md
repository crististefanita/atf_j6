# Biblioteci ATF

## Pe scurt

Java 21 și Maven; cinci JAR-uri thin, cu tranzitivele din POM. Ghidurile sunt autonome față de sursele producătorului.

| Ghid | Artefact, grup `atf.api` | Utilizare |
|---|---|---|
| [Core](README.Core.md) | `atf-core` | HTTP, configurație, JSON și lifecycle. |
| [Reporting](README.Reporting.md) | `atf-reporting` | Extent/fallback și dovezi auditabile. |
| [Console](README.Console.md) | `atf-console` | Diagnostic Maven și IntelliJ. |
| [Mock](README.Mock.md) | `atf-mock` | Componente și contracte HTTP offline. |
| [Test Kit](README.TestKit.md) | `atf-test-kit` · audit și lifecycle JUnit neutru | Clasificare, comparații și suportul testelor. |

## Integrare

1. Într-o copie TMP a unui consumer ATF, `mvn -B -ntp -N validate` instalează perechile din lib în repository-ul Maven selectat.
2. Declarați dependența în modulul apelant; coordonatele și versiunea sunt în POM-ul alăturat JAR-ului.
3. IntelliJ folosește același JDK și repository Maven; reîncărcați POM-ul rădăcină.

<details>
<summary>Consumer nou, fără reactor ATF</summary>

Copiați perechile JAR/POM în lib; din proiectul nou, în TMP:

```powershell
Get-ChildItem lib\*.pom | ForEach-Object {
    mvn org.apache.maven.plugins:maven-install-plugin:3.2.0:install-file "-Dfile=$([IO.Path]::ChangeExtension($_.FullName,'jar'))" "-DpomFile=$($_.FullName)" -DgeneratePom=false
    if ($LASTEXITCODE) { throw "Instalare eșuată: $($_.Name)" }
}
```

Declarați, de exemplu, `atf.api:atf-reporting:<versiunea din POM>` în dependențe. Folosiți scope `compile` pentru support în src/main și `test` doar în src/test; Maven rezolvă tranzitivele.

</details>

## Identitate și verificare

- O singură pereche JAR/POM per bibliotecă, indexul și cele cinci ghiduri, direct în lib.
- În ATF, pom.xml selectează versiunile; library-provenance.json păstrează hash-urile și originea.
- Guvernanța și acceptanța rulează în TMP; fiecare consumer verifică propriul profil și classpath.

## Mentenanță și feedback

Codul, testele native și feedback-ul bibliotecilor aparțin `atf_j0` (`atf_j0/docs/feedback/README.md`). Remedierea și regresia în J0 preced sincronizarea și acceptanța locală; feedback-ul include JAR/hash, reproducere și așteptat/observat.
