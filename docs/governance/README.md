# Guvernanța Hold’em Lab

## Pe scurt

J6 verifică documentația și organizarea testelor din propriul checkout, prin Maven și bibliotecile fixate în proiect. Extent arată comparațiile pentru inventarul suitelor și constatările pe document, inclusiv linia și remedierea.

## Contractul documentației

| Element | Regulă verificabilă |
|---|---|
| Intrare | Un titlu H1 și orientare `Pe scurt` în README-urile de intrare. Secțiunea principală de documentație trimite o singură dată la fiecare ghid responsabil. |
| Navigare | Legăturile locale ajung la un fișier sau director din repo. Pentru Markdown, ancora corespunde unui titlu sau identificator existent. |
| Lectură | Proza de orientare are cel mult trei propoziții pe paragraf. Listele descriu pași, iar tabelele pun în relație responsabilități și dovezi. |
| Diagramă | Un flux Mermaid are o legendă imediat sub diagramă. Legenda explică ce relație trebuie înțeleasă și unde se verifică. |
| Feedback | Un caz deschis apare o singură dată în index, cu un criteriu de închidere. Un fișier retras din index poate rămâne ca destinație citată de note de release arhivate. |

Documentația activă include ghidurile, README-urile de intrare, catalogul și dosarul unei ediții `DRAFT`. Dosarele sigilate sunt verificate prin manifestul ediției și poarta de integritate. [Politica dosarelor de ediție](../release/ATF_Release.md#audit-editorial-și-dosare-de-ediție)

La revizie, confruntă diagrama cu granița reală dintre module și mută criteriile stabilizate în ghidul responsabil. Păstrează rezultate de rulare și capturi în dosarul dovezilor. [Contractele produsului](../requirements/README.md) · [Feedback activ](../feedback/README.md)

## Inventarul testelor

`TestInventoryGovernanceTest` descoperă testele executabile Maven și Android JVM din sursă. Compară clasele cu inventarul de release și cere câte un singur atribut ATF canonic pe fiecare suită Maven. Atributul trebuie să corespundă nivelului publicat în Extent.

Pentru unitare și integrare, declară nivelul în câmpul `level`. Nivelul implicit este `component`, inclusiv pentru JUnit 4 Android. Instrumentarea nativă are selecții și rapoarte proprii. [Niveluri și aserțiuni](../testing/TEST_STRATEGY.md#niveluri)

## Verificare offline

Rulează din rădăcina proiectului cu JDK 21 și un cache Maven pregătit. Pentru guvernanța locală sunt suficiente proiectul, bibliotecile declarate și JDK-ul.

```powershell
mvn -o -B -ntp -pl holdem-app -am `
    '-Dtest=DocumentationGovernanceTest,TestInventoryGovernanceTest' `
    '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Rapoartele Extent ale celor două verificări apar implicit în `holdem-app/target/atf-reports`. Opțiunea `atf.test.reportDirectory` le trimite într-un dosar extern. Pentru auditul complet, rulați ambele suite JVM și publicați inventarul din XML-urile acelei rulări. [Comenzi și dovezi](../testing/TEST_STRATEGY.md#rulare-jvm-și-extent)
