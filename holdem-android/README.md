# Interfața Android Hold’em

## Pe scurt

Aplicația Android oferă mese locale offline pentru No-Limit Texas Hold’em. Folosește componentele comune de card-table pentru shell și aplică regulile prin sursele `holdem-core` și `holdem-ai` compilate de modulul Gradle `engine`.

| Mod | Locuri | Jetoane inițiale | Blinduri |
|---|---:|---:|---|
| `Play a hand` | heads-up sau 4–6 | 2.000 per loc | fixe, 10/20 |
| `Play a tournament` | 4–6 | 2.000 per loc | progresive; interval configurabil 1, 2, 3 sau 5 minute, implicit 1 |

## Ce vede și poate face jucătorul

- În timpul mâinii, jucătorul vede cele două cărți proprii, board-ul public și starea locurilor. Locurile adverse afișează cărți numai la decontare.
- După decontare, recapitularea dezvăluie mâinile distribuite, inclusiv cele ale jucătorilor care au făcut fold. Eticheta `Fold` rămâne până la următoarea distribuire.
- Masa indică actorul curent și combinația proprie calculată din cărțile vizibile. Sub fiecare jucător apar ultima mutare și jetoanele adăugate prin acea mutare.
- `Istoric pariuri` deschide lista cronologică a mâinii, cu strada și contribuția cumulată pentru fiecare mutare. Istoricul poate fi consultat și după decontare, până la următoarea distribuire.
- În configurarea unui turneu, bifa „Arată șansa mea de câștig” activează indicatorul din antet. Alegerea se păstrează cu turneul salvat și pornește debifată pentru un turneu nou. Indicatorul arată șansa estimată de a câștiga singur mâna, pe baza cărților proprii, a board-ului și a numărului de adversari rămași. Estimarea folosește 256 de eșantioane cu mâini adverse necunoscute, pe un worker separat de AI. După fold nu se afișează procent.
- Butoanele de decizie au fețe vizuale reduse și ținte de atingere de cel puțin 48 dp. Închiderea editorului Raise revine la acțiunile mesei. Tastatura se deschide când este selectat câmpul sumei.
- Coach-ul oferă o recomandare locală. **Lasă AI-ul** sau **Let AI play**, după limba aleasă, preia mâna curentă prin acțiuni validate de motor.
- Masa rezervă spațiu pentru mesaj, comenzi și ultima acțiune a fiecărui loc. Schimbarea actorului păstrează poziția potului, a mesajelor și a chenarelor în timpul mâinii.
- Profilul, limba și o sesiune întreruptă pot fi reluate din meniul principal.

## Contractul mâinii

- `engine` compilează sursele `holdem-core` și `holdem-ai`; Java se compilează cu JDK 21 și țintă Java 17.
- Acțiunile afișate provin din acțiunile legale ale motorului. Suma de bet/raise este numărul de jetoane adăugate la acțiunea curentă; motorul respinge valori invalide și comenzi depășite.
- Politica primește numai observația actorului. Dezvăluirea de după decontare ține de prezentare și nu intră în observația AI.
- Selectorul „Nivelul adversarilor” oferă Normal și Hard. Normal folosește check/call. În Hard, stilurile se repartizează pe locuri, iar turneul cu șase jucători include un adversar Normal. Sugestiile și auto-play-ul din Hard folosesc același profil. Sesiunea păstrează dificultatea, iar sesiunile vechi fără această setare se deschid în Normal.

## Turneu și reluare

- Nivelul blindurilor se calculează la început de mână. Un interval expirat și eliminările se aplică mâinii următoare.
- Stivele se păstrează între mâinile turneului; dealerul și blindurile se mută numai printre locurile eligibile.
- Clasamentul local înregistrează turneul după salvarea stării terminale. ID-ul turneului împiedică punctarea repetată. Pornirea unei alte partide verifică întâi că rezultatul anterior a intrat în registru. Rândul jucătorului afișează numele curent al profilului pentru același ID, cu rangul, punctele și numărul de turnee deja înregistrate.
- În chenare, valoarea are mai mult contrast decât eticheta. Când spațiul măsurat nu ajunge pentru o linie, eticheta rămâne întreagă deasupra sumei. Chenarul oferă un singur punct de focus pentru nume, rol, sume și starea jucătorului.
- Fiecare acțiune acceptată se salvează într-un checkpoint privat, cu locul fizic al actorului și ordinea replay-ului.
- La redeschidere, motorul reconstruiește și revalidează istoricul înainte de afișarea mesei. Se reiau mâna, turneul sau rezultatul confirmat.
- O comandă acceptată în timpul decontării salvează direct următoarea mână sau rezultatul. Efectul poate amâna afișarea până la final. După pauză ori recreare se afișează destinația deja salvată, cu aceeași identitate de turneu.
- Suspendarea invalidează callback-urile UI și oprește ceasul turneului; revenirea îl continuă din timpul salvat.

## Prezentarea mesei

- `TurnPacing` programează câte o acțiune automată la `550 ms` <!-- rag:contract android.turn-pacing --> după primul cadru vizibil și verifică din nou tokenul turei înainte de aplicare.
- `PlayerSeatFrame` marchează actorul. După decontare, masa păstrează cărțile vizibile până la următoarea comandă. Cele folosite în combinația câștigătoare sunt evidențiate, iar marcajul premiului se deplasează spre locurile care au câștigat din pot.
- La decontare, chenarul separă `Won` de `Returned`. Sumarul central arată potul plătit, fără restituiri. Fiecare marker arată premiul locului și ajunge în centrul chenarului beneficiar, când centrul lui este vizibil. La mai mulți beneficiari, rezumatul de sub pot păstrează numele și suma fiecăruia, inclusiv cu animațiile Android dezactivate sau dacă un chenar iese din fereastră. Ultima poziție rămâne vizibilă pentru un cadru înainte de curățarea efectului.
- Pe ferestre înguste sau cu font mărit, locurile se rearanjează în jurul potului și al board-ului; conținutul dialogului de raise derulează separat de acțiuni.
- Masa compactă măsoară fereastra cu istoricul pliat și reduce proporțional fețele cărților doar când conținutul depășește spațiul disponibil. Dacă dimensiunea minimă încă depășește fereastra, derularea păstrează comenzile accesibile. Istoricul deschis are propria listă derulabilă.
- Fundalurile butoanelor de pariere ocupă aproximativ 62,5% din înălțimea controlului. Butoanele păstrează ținta tactilă de minimum 48 dp.
- Heads-up: dealerul postează small blind și acționează primul preflop; big blind-ul acționează primul după flop. [Poker TDA, regula 36-C](https://www.pokertda.com/view-poker-tda-rules/)

## Verificare locală

Folosiți JDK 21, Android SDK platform 36 și o copie de lucru care conține `holdem-core` și `holdem-ai`. Din directorul `holdem-android`, configurați `ANDROID_HOME` și păstrați build-ul Gradle sub o rădăcină temporară.

```powershell
$tmp = Join-Path $env:TEMP 'atf-j6-android-components'
$env:ATF_TMP_BUILD_ROOT = Join-Path $tmp 'build'
$init = Join-Path $tmp 'tmp-build.gradle'
New-Item -ItemType Directory -Force $tmp | Out-Null
@"
gradle.beforeProject { project ->
    def root = System.getenv('ATF_TMP_BUILD_ROOT')
    if (root) {
        def part = project.path == ':' ? 'root' : project.path.replace(':', '_')
        project.buildDir = new File(root, project.rootProject.name + '/' + part)
    }
}
"@ | Set-Content -Encoding utf8 $init
.\gradlew.bat --no-daemon -I $init `
    :app:testSanityUnitTest :engine:test :app:lintDebug :app:assembleDebug
```

- `testSanityUnitTest` și `engine:test` verifică componentele JVM fără cheie de release.
- `lintDebug` și `assembleDebug` verifică build-ul local; nu califică APK-ul de distribuție.
- `sanity` și `release` păstrează verificarea cheii private. Acceptanța lor este descrisă în [procesul de release](../docs/release/ATF_Release.md) și în [catalogul versiunilor](../release/README.md).
- Raportarea și APK-urile intermediare rămân în rădăcina temporară configurată.

### Probe native de decontare

| Traseu | Ce verifică |
|---|---|
| `settlement-award` | În heads-up, același loc primește premiul și restituirea. Raportul păstrează valorile separate și cadrele de plecare, sosire și decontare ale markerului. |
| `settlement-multi-award` | Fiecare beneficiar primește propriul marker, atât la split, cât și la pot secundar. |
| `seat-metrics` | La cinci și la șase locuri, perechile întoarse sunt aliniate sub chenarele de pe fiecare rând, inclusiv perechea jucătorului local. |
| `settlement-static-award` | Cu animațiile oprite, masa păstrează numele, sumele și comanda pentru mâna următoare. |
| `settlement-compact-award` | Pe fereastra de 320 × 328 dp, cu font 200%, rezumatul păstrează și premiul locului din afara ferestrei. Raportul verifică markerul locului vizibil și cadrele animației. |

În fereastra compactă, potul și rezumatul premiilor apar înaintea categoriei mâinii și a blindurilor. Runnerul restaurează setările Android modificate înainte de a elibera emulatorul. [Calificarea release-ului](../docs/release/ATF_Release.md#calificare-reproductibilă)

### Spațierea acțiunilor

Perechile de butoane de sub mâna jucătorului au un spațiu orizontal de 1,5 dp, rotunjit la pixel. Fiecare buton păstrează o zonă tactilă de cel puțin 48 dp. Proba nativă măsoară ambele perechi la deschiderea turneului.

## Documente owner

- [Cerințe și reguli](../docs/requirements/README.md)
- [Catalog Android](../release/README.md)
- [Coach și recapitulare](../docs/feedback/J6-UI-05-LOCAL-COACH-AND-POSTGAME-REVIEW.md)
- [Probe pentru reluare și decontare](../docs/testing/TEST_STRATEGY.md#interfața-android-locală)
