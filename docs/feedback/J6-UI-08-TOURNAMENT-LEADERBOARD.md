# J6-UI-08 · Clasament local după finalizare și redenumire

<<<<<<< Updated upstream
Închis. Corecția este inclusă în ediția 0.2.2. [Dosarul ediției](../../release/0.2.2/README.md)

Turneul intră o singură dată în registru după salvarea checkpoint-ului terminal. O partidă nouă poate înlocui checkpoint-ul după reconcilierea rezultatului. La afișarea clasamentului, numele curent din profil se aplică doar intrării cu ID-ul stabil al jucătorului. Rangul, punctele, contorul și aliasurile celorlalți participanți se păstrează.

## Verificări

- Sanity-ul `leaderboard-settlement` a confirmat pe API 28 că rezultatul rămâne înregistrat o singură dată după pornirea altui turneu fără deschiderea rezultatului.
- Sanity-ul `leaderboard-profile-restart` a redenumit profilul după o victorie. După `am force-stop`, un PID nou a afișat un singur rând cu numele nou, 100 de puncte și un turneu. ID-ul profilului și digestul registrului au rămas identice. Extent include meniul după salvarea numelui și clasamentul din procesul nou.
- Testul JVM verifică redenumirea după reîncărcarea registrului, inclusiv când profilul și un bot folosesc același nume. Publisherul respinge un receipt care conține doar redenumirea.

[Cerințele clasamentului](../requirements/PRODUCT_REQUIREMENTS.md#clasamentul-turneelor) · [Strategia de testare](../testing/TEST_STRATEGY.md#interfața-android-locală)
=======
**Prioritate:** P2

Calificat pe `release/holdem-0.2.1` prin `875a796` și `7d289a9`. [Cerința de produs](../requirements/PRODUCT_REQUIREMENTS.md#clasamentul-turneelor) deține calculul punctelor și identitatea participantului.

**Owner:** Persistența turneelor Android J6

- [ ] Reconciliați cu `main` înregistrarea rezultatului înaintea înlocuirii checkpoint-ului și afișarea numelui curent după ID. În checkout-ul curent, `showLeaderboard()` afișează încă `entry.displayName()` din registru.

<details>
<summary>Dovada ramurii de release</summary>

Turneul se înregistrează după checkpoint-ul terminal. O partidă nouă poate înlocui checkpoint-ul numai după reconcilierea rezultatului. Build-ul în TMP a trecut 51 de teste de componentă. Sanity-ul `leaderboard-settlement` a trecut 16 aserțiuni pe API 28, cu o captură în Extent. După pornirea altui turneu și redeschiderea Activity, registrul a păstrat o singură intrare pentru ID-ul inițial.

Clasamentul preia numele curent al profilului după ID-ul stabil. Proba nativă `leaderboard-profile-restart` a trecut 26 de aserțiuni pe API 28. După `am force-stop`, procesul nou a afișat numele nou o singură dată, cu 100 de puncte și un turneu. ID-ul și digestul registrului au rămas identice. Extent încorporează meniul după redenumire și clasamentul după restart. [Cerința de produs](../requirements/PRODUCT_REQUIREMENTS.md#clasamentul-turneelor)

Testul de componentă verifică și cazul în care profilul folosește același nume ca un bot. Publisherul cere dovada restartului pentru un rezultat PASS.

</details>
>>>>>>> Stashed changes
