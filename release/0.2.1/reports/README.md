# Dovezi de calificare

Calificarea verifică proiecția cărților și dimensionarea mesei în componente, apoi fotografiază preflopul înaintea primei mutări și măsoară zonele tactile pe Android.

| Probă | Rezultat |
|---|---|
| [Maven și guvernanță](maven-component-suites-extent.html) | 97 teste trecute |
| [Android JVM](android-component-suites-extent.html) | 45 teste trecute |
| Lint Release | Zero erori și avertismente |
| [Turneu sanity la 392 × 626 dp](android-tournament-extent.html) | 76 aserțiuni și 9 capturi |
| [Turneu sanity suplimentar la 338 × 530 dp](android-tournament-short-extent.html) | 76 aserțiuni și 9 capturi |
| [Instalarea APK-ului Release exact](android-release-smoke-extent.html) | 7 aserțiuni și 2 capturi |
| [Guvernanța ediției după publicare](J6ReleaseGovernance.html) | Catalog, identitatea APK-ului și rutele de descărcare |

La ambele dimensiuni, preflopul și masa decontată au încăput cu istoricul pliat. Încercările inițiale au surprins actualizarea asincronă a indicatorilor AI și a istoricului. Rulările repetate pe același APK au trecut. Dovezile încercărilor rămân în directorul extern. Editorul de raise este verificat separat de traseul care execută call după preflop.

[Manifestul calificării](../qualification.json) leagă APK-ul semnat, rapoartele Extent și contextul consultativ RAG de commitul testat.
