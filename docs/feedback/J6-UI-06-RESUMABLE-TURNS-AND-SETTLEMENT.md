# J6-UI-06 · Reluare, ritmul turelor și colectarea mesei

**Prioritate:** P1

**Stare:** Pacing-ul și reluarea anterioară rămân calificate; recapitularea nouă este implementată în cod, dar testele și acceptanța acestei versiuni sunt restante

J4 este reperul pentru interfața comună, iar J5 confirmă că pacing-ul poate fi folosit fără a muta regulile în bibliotecă. J6 păstrează motorul Hold’em, checkpoint-ul și proiecția potului în aplicația proprie. Contractele de cadru activ și mișcare sunt reutilizate din Card Table UI.

## Contract

- Redeschiderea oferă **Continue saved game** și reconstruiește sesiunea offline din acțiunile acceptate, inclusiv actorii AI, stivele, dealerul, seed-ul și rundele anterioare.
- La `onPause`, sesiunea este salvată, timerul turneului este suspendat și acțiunile UI programate sunt invalidate. `onResume` reia ceasul din timpul confirmat și redesenează scena curentă; timpul de fundal nu avansează blindurile.
- Calculul AI din worker este read-only. Aplicarea sugestiei cere Activity activă și același token de tură, astfel încât un rezultat întârziat nu poate muta jocul ascuns în fundal.
- Replay-ul verifică fiecare actor și fiecare mutare prin motor. Nu se fabrică o tură din date invalide, iar o scriere eșuată nu șterge checkpoint-ul deja comis.
- Fiecare check/call automat este vizibil ca o acțiune separată, după primul cadru și intervalul comun de 550 ms. Tokenul turei invalidează lucrul rămas în coadă dacă scena sau actorul s-a schimbat.
- Cadrul colorat urmează actorul fizic al motorului după fiecare mutare; la settlement nu rămâne marcat un actor activ.
- **Comportamentul anterior, calificat la commit-ul de referință:** la finalul mâinii, cărțile vizibile se colectau spre pot. Această animație nu mai este potrivită pentru recapitularea cerută.
- **Implementarea curentă, încă necalificată:** mâna terminală păstrează tabla și cărțile pe ecran; efectul temporar mută valoarea premiului de la pot la locurile beneficiare, fără să colecteze cărțile și fără avansare automată. Butonul explicit execută tranziția. Detaliile pentru combinația evidențiată și beneficiari sunt în [J6-UI-07](J6-UI-07-SHOWDOWN-WIN-CLARITY.md).
- **Comportamentul codului calificat:** restartul în timpul colectării reia prezentarea din checkpoint-ul terminal al mâinii; rezultatul turneului se deschide după aceea.
- **Comportamentul codului calificat:** butonul `New hand` rămâne vizibil în timpul colectării. O apăsare în acea fereastră se reține o singură dată și se execută după încheierea animației; înainte, handlerul o ignora deși controlul apărea activ.
- **Delta de verificat:** când recapitularea așteaptă `New hand`, starea terminală și cărțile trebuie să rămână vizibile fără timer de colectare. Detaliul și cazurile de componentă sunt owner-ate de [J6-UI-07](J6-UI-07-SHOWDOWN-WIN-CLARITY.md).

## Delimitarea responsabilităților

| Comun J4 | Adaptor J6 |
|---|---|
| `PlayerSeatFrame` și starea chenarului activ | Locul fizic activ, nume, contribuție, stivă și roluri |
| `TurnPacing` și întârzierea de 550 ms | Check/call, acțiune legală AI, token de tură și programarea următoarei scene |
| `TrickCollectionMotion` pentru alte interacțiuni comune | Fețele vizibile, animația premiului către locurile calculate și restaurarea ecranului terminal |
| Teste pure de cadru și temporizare | Persistența, replay-ul prin motor și setul de cărți permis la showdown |

Nu mutați jurnalul de acțiuni, persistența, potul sau regulile Hold’em în AAR-ul comun. În special, colecția spre pot reutilizează matematica mișcării, nu `CollectionTarget` ori câștigătorul unei levate.

## Calificare

- J4 `TurnPacingComponentTest`: 550 ms după cadru, o singură acțiune, invalidare pentru actor uman, blocaj sau scenă expirată.
- J6 `HoldemSessionRepositoryComponentTest`: checkpoint între două acțiuni automate, restaurarea actorului exact, victoria terminală după recrearea repository-ului, respingerea jurnalului inversat, eșec de scriere și recuperare după oprire între redenumiri.
- J6 `LocalHoldemGameTest`: mutare pasivă individuală cu actorul motorului salvat în jurnal.
- J6 `LocalHoldemTournamentTest`: ceas monoton injectat, suspendare și reluare fără `sleep`; timpul de fundal nu schimbă timpul salvat sau nivelul blindurilor.
- J6 `HoldemTablePresentationComponentTest`: actorul corect în ordinea fizică la 2/4/5/6 locuri, cărțile permise la colectare și convergența la pot în fereastra comună. Extensia cerută în [J6-UI-07](J6-UI-07-SHOWDOWN-WIN-CLARITY.md) trebuie să testeze separat persistența vizuală a stării `Street.COMPLETE` fără input, după restore și până la o singură comandă `New hand`; testul actual acoperă numai comportamentul anterior.
- Build-urile producătorului și consumatorului se execută cu directoarele Gradle și depozitul Maven în TMP; integrarea verifică manifestul binar și SHA-256 înainte de compilare.
- Calificarea J6 actuală: `:app:testSanityUnitTest` a trecut 27/27; `:app:lintRelease` nu a raportat avertismente, iar variantele semnate `sanity` și `release` s-au construit. Maven a trecut 80/80, inclusiv patru teste de guvernanță Extent. Pe API 28, turneul a trecut 42 de aserțiuni și cinci capturi, iar smoke-ul APK-ului `release` a trecut șapte aserțiuni și două capturi. Rapoartele Extent au capturile încorporate și au trecut inspecția profilului.
- Capturile confirmă actorul, pozițiile heads-up, settlement-ul și traseul până la rezultat; această rulare nu a măsurat fiecare cadru al animației și nu a folosit runtime-ul minim API 26. Probe anterioare de la alt commit nu sunt rezultat pentru această ediție.

## Limite rămase

Testele componente verifică regulile, serializarea și geometria temporală; capturile native confirmă stări reprezentative, nu fiecare cadru al animației sau toate dimensiunile de ecran. Separarea Gradle păstrează pachetele de distribuție protejate. Matricea completă de rezoluții, reluarea animației cadru-cu-cadru și API 26 rămân în afara sanity-ului scurt.

[Interfața Android](../../holdem-android/README.md) · [Arhitectura](../architecture/ARCHITECTURE.md) · [Strategia de testare](../testing/TEST_STRATEGY.md)
