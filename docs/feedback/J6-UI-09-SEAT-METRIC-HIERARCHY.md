# J6-UI-09 · Ierarhia etichetelor de stivă și plată

**Prioritate:** P2  
**Stare:** Implementare parțială — semantica premiului/restituirii și stilul local cu etichetă mică și valoare lizibilă sunt conectate; acceptanța vizuală și TalkBack rămân restante  
**Domeniu:** faptele afișate în chenarele jucătorilor

## Problema observată în cod

Fiecare loc primește faptele `Stack` și `Committed`, iar la final `Stack`, `Won` și, dacă este cazul, `Returned`. Proiecția distinge premiul potului de restituirea mizei neacoperite. Eticheta este mai mică și mai estompată decât valoarea, iar cadrul păstrează descrierea accesibilă completă.

Reducerea vizibilității etichetelor nu trebuie să șteargă sensul sau să transforme restituirea în câștig afișat. `Won` și `Returned` folosesc valori separate din motor.

## Contractul cerut

- Folosiți o ierarhie compactă și discretă: etichetă secundară mai mică și cu contrast moderat, valoare numerică clară și aliniată. Evitați chenare suplimentare, pictograme fără text sau decor care concurează cu actorul și cărțile.
- Localizați etichetele și păstrați sensurile distincte: stiva curentă, contribuția în mâna activă, premiul potului și miza neacoperită returnată. Numele final al valorii de decontare depinde de rezolvarea `J6-UI-04`.
- Descrierea accesibilă a locului include numele, rolul, valoarea completă și eticheta semantică; stilul discret nu reduce informația pentru TalkBack și nu se bazează numai pe culoare.
- Inspirația vizuală poate folosi prezentarea compactă a faptelor din componentele comune J4/J5, dar adaptarea păstrează vocabularul și regulile de decontare Hold’em.

## Teste de componentă necesare

- Proiecția fiecărui loc asociază valoarea corectă cu faptul corect înainte și după decontare; `Won` și `Returned` nu se contopesc.
- Layout-ul rămâne lizibil la 2/4/5/6 locuri, mod compact și normal, română și engleză și font mărit. Testele detectează text trunchiat, etichete suprapuse, valori mutate la alt jucător și contrast insuficient.
- Testul de componentă verifică ierarhia de dimensiune/culoare a valorii față de etichetă pentru locurile active și inactive în ambele moduri.
- Descrierea accesibilă conține toate valorile și etichetele complete, chiar dacă vizual textul secundar este mai mic. Actorul curent rămâne identificabil fără culoare.
- Inspecția de snapshot componentă compară densitatea și ierarhia cu stilul existent al mesei; nu verifică pixel-perfect un model de dispozitiv anume.

## Dependențe

- Păstrați actorul și marcajele `Fold`/`Out` definite de `J6-UI-01` și `J6-UI-05`.
