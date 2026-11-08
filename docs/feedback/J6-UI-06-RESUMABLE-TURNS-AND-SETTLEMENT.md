# J6-UI-06 · Reluare, ritmul turelor și colectarea mesei

**Prioritate:** P1

**Stare:** 27/27 teste JVM calificate; sanity-ul Android vizual rămâne

J4 este reperul pentru interfața comună, iar J5 confirmă că pacing-ul poate fi folosit fără a muta regulile în bibliotecă. J6 păstrează motorul Hold’em, checkpoint-ul și proiecția potului în aplicația proprie. Contractele de cadru activ și mișcare sunt reutilizate din Card Table UI.

## Contract

- Redeschiderea oferă **Continue saved game** și reconstruiește sesiunea offline din acțiunile acceptate, inclusiv actorii AI, stivele, dealerul, seed-ul și rundele anterioare.
- La `onPause`, sesiunea este salvată, timerul turneului este suspendat și acțiunile UI programate sunt invalidate. `onResume` reia ceasul din timpul confirmat și redesenează scena curentă; timpul de fundal nu avansează blindurile.
- Calculul AI din worker este read-only. Aplicarea sugestiei cere Activity activă și același token de tură, astfel încât un rezultat întârziat nu poate muta jocul ascuns în fundal.
- Replay-ul verifică fiecare actor și fiecare mutare prin motor. Nu se fabrică o tură din date invalide, iar o scriere eșuată nu șterge checkpoint-ul deja comis.
- Fiecare check/call automat este vizibil ca o acțiune separată, după primul cadru și intervalul comun de 550 ms. Tokenul turei invalidează lucrul rămas în coadă dacă scena sau actorul s-a schimbat.
- Cadrul colorat urmează actorul fizic al motorului după fiecare mutare; la settlement nu rămâne marcat un actor activ.
- La finalul mâinii, cărțile vizibile rămân pe masă o secundă, apoi se colectează spre pot în următoarele 500 ms. J6 nu folosește semantica de câștigător a unei levate.
- Restartul în timpul colectării reia prezentarea din checkpoint-ul terminal al mâinii; rezultatul turneului se deschide după aceea.

## Delimitarea responsabilităților

| Comun J4 | Adaptor J6 |
|---|---|
| `PlayerSeatFrame` și starea chenarului activ | Locul fizic activ, nume, contribuție, stivă și roluri |
| `TurnPacing` și întârzierea de 550 ms | Check/call, acțiune legală AI, token de tură și programarea următoarei scene |
| `TrickCollectionMotion` și fereastra 1.500/500 ms | Fețele vizibile, măsurarea potului ca țintă și restaurarea ecranului terminal |
| Teste pure de cadru și temporizare | Persistența, replay-ul prin motor și setul de cărți permis la showdown |

Nu mutați jurnalul de acțiuni, persistența, potul sau regulile Hold’em în AAR-ul comun. În special, colecția spre pot reutilizează matematica mișcării, nu `CollectionTarget` ori câștigătorul unei levate.

## Calificare

- J4 `TurnPacingComponentTest`: 550 ms după cadru, o singură acțiune, invalidare pentru actor uman, blocaj sau scenă expirată.
- J6 `HoldemSessionRepositoryComponentTest`: checkpoint între două acțiuni automate, restaurarea actorului exact, victoria terminală după recrearea repository-ului, respingerea jurnalului inversat, eșec de scriere și recuperare după oprire între redenumiri.
- J6 `LocalHoldemGameTest`: mutare pasivă individuală cu actorul motorului salvat în jurnal.
- J6 `LocalHoldemTournamentTest`: ceas monoton injectat, suspendare și reluare fără `sleep`; timpul de fundal nu schimbă timpul salvat sau nivelul blindurilor.
- J6 `HoldemTablePresentationComponentTest`: actorul corect în ordinea fizică la 2/4/5/6 locuri, cărțile permise la colectare și convergența la pot în fereastra comună.
- Build-urile producătorului și consumatorului se execută cu directoarele Gradle și depozitul Maven în TMP; integrarea verifică manifestul binar și SHA-256 înainte de compilare.
- Calificarea din 2026-11-08: J4 Card Table UI a trecut 16/16; J5 pacing și adoptarea bibliotecii au trecut 3/3; J6 `testSanityUnitTest --rerun-tasks` a trecut 27/27 fără cheie release, inclusiv suspendarea deterministică a ceasului. `lintDebug` a raportat 0 erori și un avertisment despre versiunea Gradle din wrapper; `assembleDebug` a trecut. Dry-run-ul `assembleSanity` a păstrat `verifyHoldemReleaseSigning` înainte de împachetare.
- [Receipt-ul auditabil](C:/work/holdem_back/ui-06-resumable-turns-20261108/README.md) păstrează XML-urile JUnit, logul J6, manifestul Card Table UI 0.1.7, hash-urile sursei/artefactelor și sumarul calificării. Build-ul Gradle pentru componente produce JUnit XML, nu Extent; nu s-a rulat E2E sau emulatorul.

## Limite rămase

Testele JVM verifică regulile, serializarea și geometria temporală, dar nu confirmă efectiv culoarea cadrului sau animația Android în dispozitiv. Separarea Gradle este rezolvată: componentele rulează fără identitate privată, iar pachetele de distribuție rămân protejate. Un sanity Android scurt poate confirma un check/call cu schimbarea cadrului și o mână terminată cu colectarea spre pot; nu cere un turneu complet. E2E nativ, capturi și Extent nu au fost rulate în această sarcină.

[Interfața Android](../../holdem-android/README.md) · [Arhitectura](../architecture/ARCHITECTURE.md) · [Strategia de testare](../testing/TEST_STRATEGY.md)
