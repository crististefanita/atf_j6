# J6-UI-08 · Clasament cumulativ al turneelor

**Prioritate:** P2  
**Stare:** Implementat; componentele și un turneu nativ pe API 28 verifică sumarul, clasamentul și reîncărcarea activității. Restartul real al procesului și acceptanța vizuală pe alte viewport-uri rămân de calificat.  
**Domeniu:** statistici locale persistente pentru turnee Hold’em

## Problema observată în cod

Clasamentul local păstrează acum plasările și punctele cumulate după ID-ul stabil al participantului, separat de numele afișat. Înregistrarea unui turneu are un ID idempotent și folosește un fișier privat aplicației, înlocuit atomic. Turneul calculează plasările reale, inclusiv egalitățile la eliminare simultană.

Numele AI-urilor sunt generate pentru fiecare meci. Numele vizibil nu poate servi drept cheie pentru a aduna rezultatele aceluiași participant; schimbarea numelui profilului nu trebuie nici ea să creeze un jucător statistic nou.

## Contractul cerut

- Meniul oferă un clasament local al punctelor din turnee. El agregă rezultatele în timp pentru același participant și nu necesită cont, rețea sau serviciu extern.
- Fiecare turneu finalizat se înregistrează o singură dată: ID-ul unic al turneului, ID-ul stabil al participantului, numele afișat, plasarea și punctele acordate sunt reconciliate înainte de acumulare.
- ID-ul utilizatorului supraviețuiește schimbării numelui. Fiecare profil de bot are un ID stabil separat de aliasul aleator afișat la masă; două persoane cu același nume nu se unesc în aceeași intrare.
- Înregistrarea se face o singură dată când turneul devine terminal. Reluarea activității, redeschiderea aplicației sau replay-ul checkpoint-ului nu dublează rezultatul. Salvarea folosește versiune de schemă și înlocuire atomică, independentă de checkpoint-ul mâinii în curs.
- Clasamentul afișează primele 10 intrări după punctaj total, cu totalul de puncte, turnee jucate și cea mai bună clasare. Egalitățile la puncte au același rang; departajarea vizuală nu schimbă punctajul.
- Profilul poate fi numit din nou fără pierderea totalurilor. Identitatea și punctele rămân locale dispozitivului; interfața nu sugerează că tabela este globală sau sincronizată.

## Regulă de punctaj propusă

Punctajul curent normalizează turneele cu 4–6 participanți la aceeași scară și are versiunea `normalized-placement-v1` în antetul depozitului:

```text
puncte = rotunjire(100 × (N − rang_mediu) / (N − 1))
```

`N` este numărul participanților, iar rangul este de la 1 pentru primul loc. O eliminare simultană împarte locurile ocupate: rangul mediu este media pozițiilor respective și toți jucătorii egalați primesc același punctaj. Astfel, câștigătorul primește 100, ultimul loc 0, iar numărul brut de jetoane nu este confundat cu punctele. O schimbare a curbei cere versiune nouă și migrare explicită a datelor cumulative.

## Probe existente și limitele lor

- `TournamentLeaderboardTest` acoperă formula pentru 4–6 participanți, egalități, agregare pe ID stabil, clasament top 10, codec/checksum, deduplicarea ID-ului turneului, respingerea plasărilor invalide și reîncărcarea repository-ului dintr-un storage partajat între instanțe.
- `TournamentSanity` verifică sumarul, clasamentul din meniu, contorul de turnee și reîncărcarea după deschiderea unei instanțe `Activity` noi, pe storage-ul Android app-private. Rularea API 28 a trecut; raportul nativ include verificarea clasamentului după întoarcerea la meniul principal.
- Captura API 28 a confirmat că aliasurile AI din clasamentul turneului corespund numelor din recapitularea mesei; aliasurile sunt stabile pe seed-ul partidei și sunt mapate din ordinea de afișare la identitatea scaunului fizic înainte de salvare. Testul nativ verifică nume ne-goale pentru toate cele patru plasări după eliminare.
- Identitatea umană este persistentă prin preferințele aplicației; ID-urile botului sunt independente de aliasul vizual. Redenumirea și revenirea după restart de proces trebuie verificate integrat, nu deduse doar din testele pure.
- Testele JVM folosesc `MemoryStorage`; ele verifică protocolul și comportamentul repository-ului, nu API-ul Android `AtomicFile`, recuperarea după întreruperea scrierii sau supraviețuirea unui proces omorât.
- Codec-ul verifică checksum și round-trip, dar eșantionul de fault injection pentru fișier trunchiat, schema necunoscută și date corupte trebuie extins înainte de a considera persistența complet calificată.

## Acceptanță rămasă

Un restart real al procesului trebuie să confirme că rezultatul terminal nu se dublează și că redenumirea profilului păstrează totalul. Testul curent nu pretinde că a omorât și repornit procesul și nu califică toate viewport-urile. Nu se adaugă test online: registrul este local.
