# J6-UI-05 · Calificarea coach-ului și a recapitulării locale

P1. Ownerul este interfața Android J6.

## Acoperirea existentă

Componenta verifică legalitatea recomandării și faptul că ea rămâne aceeași dacă se schimbă numai cărțile ascunse. Traseul nativ verifică ascunderea cărților adverse înainte de decontare și afișarea lor la final. [Componenta coach](../../holdem-android/app/src/test/java/ro/atf/holdem/android/LocalHoldemGameTest.java) · [Traseul nativ](../../holdem-android/app/src/androidTest/java/ro/atf/holdem/android/TournamentSanity.java)

## Criterii rămase

- Compară combinația vizibilă, kicker-ul și cazul în care board-ul joacă singur cu un oracol independent. Criteriile pentru marcajele terminale sunt urmărite separat. [Showdown](J6-UI-07-SHOWDOWN-WIN-CLARITY.md#dovezi-rămase)
- Verifică trecerea dintre două mâini. Recapitularea terminală trebuie să arate ultima mână, iar masa nouă să ascundă din nou cărțile adverse. Acoperă eticheta actorului și recapitularea în română și engleză, cu font mărit.
- Verifică nativ traseul local de la selectorul de blinduri la sesiunea salvată și restaurată. Într-o mână locală, urmărește hint-ul, acțiunea manuală, întreruperea preluării și recapitularea terminală. Păstrează variațiile în probele de componentă.
