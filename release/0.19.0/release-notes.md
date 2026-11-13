# Note de versiune 0.19.0

## Realizări de poker

- Catalogul are 39 de obiective: 14 Ușoare, 14 Medii și 11 Grele.
- Familiile acoperă poziții și blinduri, mâini la showdown, poturi multiway și secundare, split-uri, eliminări, podiumuri, heads-up, titluri la 4–6 locuri, stive scurte și reveniri.
- Realizarea Hard „carte mare fără pereche” cere câștigul unic al unui pot la showdown în patru mâini distincte din turnee Hard pornite cu șase locuri. Nu recomandă urmărirea unei mâini slabe.
- Progresul rămâne local pe profil și se deduplică după mâna sau turneul confirmat.

## Calificare

- Maven: 222 teste; Android JVM: 114 teste; zero eșecuri, erori sau teste omise.
- APK-ul semnat a trecut smoke-ul nativ de turneu (109 aserțiuni) și smoke-ul de instalare (82 aserțiuni).
- Calibrarea live a parcurs 204 turnee și 15.162 mâini; validarea pe replay a folosit 1.440 de trasee pereche. Rapoartele notează separat raritatea și incertitudinea eșantioanelor mici.

Identitatea APK-ului, commitul calificat, probele și capturile sunt în [manifest](release-validation.json) și [indexul rapoartelor](reports/README.md).
