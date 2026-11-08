<<<<<<< Updated upstream
# J6-UI-06 · Continuarea din timpul decontării

**Prioritate:** P1

**Stare:** Închis pentru comanda de continuare

**Owner:** Lifecycle și sesiune Android J6

Checkpoint-ul salvează atomic destinația când utilizatorul apasă `New hand` sau `Tournament result`. Cadrul terminal rămâne până la finalul efectului dacă activitatea rămâne în prim-plan. După întrerupere, aplicația citește destinația deja salvată. Tap-urile duplicate și callback-urile de la scena veche nu pot avansa încă o dată. O scriere eșuată păstrează checkpoint-ul terminal pentru retry.

## Dovezi

- Build-ul în TMP a trecut 48 de teste Android JVM în 5 suite. Testele repository-ului verifică destinația unică, retry după eșecul scrierii, restabilirea cărților de antrenament și redeschiderea rezultatului.
- Sanity-ul `settlement-resume` pe API 28 a trecut 26 de aserțiuni cu două capturi în Extent. A exercitat două tap-uri în decontare, recrearea activității pentru `New hand` și redeschiderea din checkpoint pentru `Tournament result`. Clasamentul a înregistrat turneul o singură dată după două afișări ale rezultatului.
- Proba nativă redeschide Activity în același proces de instrumentare. Componenta verifică și citirea checkpoint-ului printr-o instanță nouă de repository. [Contract Android](../../holdem-android/README.md#turneu-și-reluare) · [Strategia de testare](../testing/TEST_STRATEGY.md)
=======
# J6-UI-06 · Comanda de continuare în timpul decontării

**Prioritate:** P1

**Stare:** Calificat pentru comanda de continuare în `release/holdem-0.2.1`, commit `d8e2b40`. Reconcilierea cu checkout-ul `main` rămâne deschisă.

**Owner:** Lifecycle și sesiune Android J6

În `main`, `HoldemActivity` păstrează continuarea ca acțiune amânată în memorie până la terminarea animației. [Cerința de produs](../requirements/PRODUCT_REQUIREMENTS.md#continuarea-în-timpul-decontării) deține contractul pentru destinația salvată.

- [ ] Reconciliați pe ramura vizată salvarea destinației înainte de afișare și confirmați după integrare reluarea din checkpoint și refuzul la o scriere eșuată.

Build-ul în TMP a trecut 48 de teste Android JVM. Sanity-ul `settlement-resume` pe API 28 a trecut 26 de aserțiuni și a inclus două capturi Extent. Proba a verificat două tap-uri în decontare, recrearea Activity pentru mâna următoare și redeschiderea rezultatului din checkpoint în același proces de instrumentare. Două afișări ale rezultatului au păstrat un singur turneu în clasament.
>>>>>>> Stashed changes
