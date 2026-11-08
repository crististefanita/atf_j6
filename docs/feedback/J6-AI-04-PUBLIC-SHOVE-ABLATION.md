# J6-AI-04 · Izolează efectul istoricului de shove

**Prioritate:** P1

**Stare:** Efect exploratoriu, sub pragul de acceptare

**Owner:** Evaluarea strategiilor în arena J6

Perechea `equity-history-control` / `equity-history-aware` izolează sensibilitatea istoricului. Lotul de stres din `<external-evidence-root>/strategy-shove-history-ablation-20261107` are 16 seed-uri independente. Diferențele de victorii sunt −2,3 pp la 4 locuri, −5,5 pp la 5 și +9,2 pp la 6. Extensia de opt seed-uri la 6 locuri are +6,3 pp cu eroare standard de 8,5 pp. Proveniența lotului marchează `sourceDirty=true`. [Contractul semnalului public](../requirements/AI_REQUIREMENTS.md)

## Criterii de închidere

- Păstrați o singură diferență YAML și aceleași distribuții, locuri și poziții pereche. Cereți minimum 20 de seed-uri independente pentru fiecare mărime de masă într-un holdout separat. Rotațiile aceluiași seed rămân măsurători repetate.
- Includeți adversari care împing și mâini largi, pe lângă profilul premium. Raportați metrica primară, intervalele de incertitudine și ratele de fold/call pe cohortă.
- Auditorul verifică oportunitățile reconstruite din acțiunile legale și folosirea exclusivă a istoricului mâinilor deja încheiate. Atașați commitul și hash-urile configurațiilor. Promovați ponderea numai unde efectul se repetă fără regresie de legalitate sau calibrare.
