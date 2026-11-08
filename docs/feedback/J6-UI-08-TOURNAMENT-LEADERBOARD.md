# J6-UI-08 · Clasament local după finalizare și redenumire

Închis pe ramura locală `release/holdem-0.2.1`.

Turneul intră o singură dată în registru după salvarea checkpoint-ului terminal. O partidă nouă poate înlocui checkpoint-ul după reconcilierea rezultatului. La afișarea clasamentului, numele curent din profil se aplică doar intrării cu ID-ul stabil al jucătorului. Rangul, punctele, contorul și aliasurile celorlalți participanți se păstrează.

## Verificări

- Sanity-ul `leaderboard-settlement` a confirmat pe API 28 că rezultatul rămâne înregistrat o singură dată după pornirea altui turneu fără deschiderea rezultatului.
- Sanity-ul `leaderboard-profile-restart` a redenumit profilul după o victorie. După `am force-stop`, un PID nou a afișat un singur rând cu numele nou, 100 de puncte și un turneu. ID-ul profilului și digestul registrului au rămas identice. Extent include meniul după salvarea numelui și clasamentul din procesul nou.
- Testul JVM verifică redenumirea după reîncărcarea registrului, inclusiv când profilul și un bot folosesc același nume. Publisherul respinge un receipt care conține doar redenumirea.

[Cerințele clasamentului](../requirements/PRODUCT_REQUIREMENTS.md#clasamentul-turneelor) · [Strategia de testare](../testing/TEST_STRATEGY.md#interfața-android-locală)
