# J6-UI-05 · Calificarea coach-ului și a recapitulării locale

P1. Ownerul este interfața Android J6.

## Acoperirea existentă

Componentele verifică sugestia legală, finalizarea mâinii curente prin AI și restaurarea intervalului de blinduri din sesiune. Contractul produsului descrie vizibilitatea cărților, limita coach-ului și controlul asupra mâinii curente. [Contract Android](../../holdem-android/README.md#contractul-mâinii) · [Teste ale mâinii](../../holdem-android/app/src/test/java/ro/atf/holdem/android/LocalHoldemGameTest.java) · [Teste ale turneului](../../holdem-android/app/src/test/java/ro/atf/holdem/android/LocalHoldemTournamentTest.java)

Proba nativă verifică ascunderea cărților adversarilor la începutul mâinii și afișarea lor după decontare. [Rapoarte 0.14.1](../../release/0.14.1/reports/README.md#rapoarte-extent)

## Criterii rămase

- Compară combinația vizibilă, kicker-ul și cazul în care board-ul joacă singur cu un oracol independent. Criteriile pentru marcajele terminale sunt urmărite separat. [Showdown](J6-UI-07-SHOWDOWN-WIN-CLARITY.md#dovezi-rămase)
- Modifică numai cărțile ascunse într-o probă pereche și confirmă că rezultatul coach-ului și decizia rămân identice. Confirmă că cererea hint-ului păstrează revizia, stivele și jurnalul.
- Verifică trecerea dintre două mâini. Recapitularea terminală trebuie să arate ultima mână, iar masa nouă să ascundă din nou cărțile adverse. Acoperă eticheta actorului și recapitularea în română și engleză, cu font mărit.
- Verifică nativ traseul local de la selectorul de blinduri la sesiunea salvată și restaurată. Într-o mână locală, urmărește hint-ul, acțiunea manuală, întreruperea preluării și recapitularea terminală. Păstrează variațiile în probele de componentă.

## P2 · Contrastul textului mic la masă

**Responsabil · adaptorul Android Hold’em**

Recomandarea coach-ului, nivelul blindurilor și butonul istoricului folosesc griul `#69756A` pe fundalul `#174F3C`. Raportul calculat din culori este 1,96:1.

- Folosește un stil pentru textul secundar pe masă și verifică minimum 4,5:1 în stările active ale celor trei elemente.
- Confirmă că schimbarea culorii păstrează coordonatele pe profilul scund cu font mărit. [Randarea mesei](../../holdem-android/app/src/main/java/ro/atf/holdem/android/HoldemTableView.java)
