# J6-AI-04 — Izolarea citirii publice a shove-urilor

**Prioritate:** P1

**Stare:** Deschis — exploratoriu, sub pragul de acceptare

## Obiectiv

Validarea trebuie să distingă contribuția istoricului public de celelalte reguli ale politicii. Fiecare actor primește numai observația legală proprie și acțiunile publice încheiate; auditorul verifică separat desfășurarea completă a mâinii.

## Constatare

Un lot de calibrare a arătat că sensibilitatea 12 se rotunjea la zero în toate confruntările eligibile. Configurația a fost ridicată la 16, cu plafonul păstrat la ±4 puncte. Testul de componentă pentru șapte oportunități fără shove a demonstrat întâi valoarea zero, apoi o ajustare conservatoare de un punct după calibrare.

Compararea globală `equity-stack-aware` cu `equity-baseline` nu izolează această regulă: include simultan și ponderi de presiune, eliminare și angajament. A fost adăugată o pereche dedicată, `equity-history-control` / `equity-history-aware`, care păstrează aceleași ponderi și schimbă numai sensibilitatea istoricului. Rezultatul offline rămâne mixt după 16 clustere independente și nu justifică promovarea ca strategie universală.

## Cerințe pentru continuarea evaluării

- Păstrați controlul și candidatul identice, cu excepția unei singure ponderi YAML.
- Rotiți rolurile prin toate locurile și pozițiile butonului, păstrând același prefix de deal-uri pentru comparația pereche.
- Raportați seed-ul ca unitate independentă; nu numărați rotațiile aceleiași mâini ca eșantioane noi.
- Stratificați după mărimea mesei și profilul stack-ului. Semnalul actual pare favorabil numai la șase locuri; nu activați ponderi diferite pe locuri până când direcția se repetă pe un holdout nou, mai mare.
- Măsurați EV sau share de stack împreună cu calibrarea probabilităților, frecvența fold/call și intervalele de incertitudine. Rata de victorie singură nu este criteriu suficient.
- Extindeți cohorta de short-stack: un rol premium-only verifică răspunsul la shove, dar nu acoperă shove-uri largi ori bluff-uri. Păstrați separat fiecare archetype și nu transformați frecvența de shove în estimare directă de cărți.

## Criterii de acceptare

1. Testele de componentă verifică cold-start, istoric sub și peste prior, limitele de 2–10 BB, jocul cash fără contaminare cu istoricul de turneu și plafonul ajustării.
2. Auditorul reconstruiește independent oportunitățile din acțiunile legale și confirmă că istoricul unei mâini devine vizibil numai după închiderea acelei mâini.
3. Un holdout separat raportează minimum 20 de seed-uri independente per mărime de masă, cu controlul pereche, proveniența codului/YAML, numărul de mâini și intervale de incertitudine.
4. Ponderea se promovează numai dacă îmbunătățește metrica primară declarată fără regresii în calibrare sau legalitate; efectele dependente de mărimea mesei cer dovezi separate pentru fiecare cohortă.

## Proveniență și limite

Run-ul agregat a fost generat cu sursa `ba6d2b02a06726cd75f483a971c480a8a0e9ba55` și marchează `sourceDirty=true`, deoarece runner-ul PowerShell fusese modificat pentru reluarea sigură a urmelor auditate. Proveniența păstrează SHA-256 pentru 24 de fișiere; la sincronizarea curentă 21/24 coincid. Cele trei diferențe sunt adăugarea metodei imutabile `withShortStackShoveSensitivity` și testele asociate din AI/arena; formula istorică evaluată nu s-a schimbat. Acest set nu este o rulare curată a commitului curent și rămâne exploratoriu. Calificarea release separată rulează testele pe sursa curentă; pragul de strategie rămâne minimum 20 seed-uri independente per configurație.
## Dovezi curente

Rapoartele complete, urmele și auditurile sunt în `C:\work\holdem_back\strategy-shove-history-ablation-20261107`: 1.232 turnee, 18.077 mâini, 145.219 decizii și 16 seed-uri independente în profilul stress. Delta candidat minus control este −2,3 pp la 4 locuri (SE 9,0), −5,5 pp la 5 (SE 4,4) și +9,2 pp la 6 (SE 5,4); ponderea medie a stack-ului este −2,52 pp (SE 2,99), −1,12 (SE 1,50) și +3,65 (SE 1,82). Extensia proaspătă de 8 seed-uri (100008–100015) rămâne pozitivă numai la 6 locuri (+6,3 pp victorii, SE 8,5; +2,84 pp stack-share, SE 2,86), dar intervalul de ±1 SE include zero. Încă nu sunt îndeplinite criteriul de 20 seed-uri sau repetarea independentă a efectului. Vezi `strategy-summary.md`, `README.md` și `run-provenance.json`.

Loturile de calibrare pentru prag sunt în `C:\work\holdem_back\strategy-shove-history-final-holdout-20261107` și `C:\work\holdem_back\strategy-shove-history-tuned-holdout-20261107`. Ele explică de ce sensibilitatea 12 a fost înlocuită cu 16; nu se amestecă cu holdout-ul ablației.
