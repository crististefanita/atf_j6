# Cerințe J6

Acest director este contractul de produs înaintea implementării. Cerințele disting patru lucruri care nu trebuie confundate:

1. corectitudinea regulilor;
2. calitatea deciziei AI;
3. robustețea aplicației și a transportului online;
4. profitabilitatea financiară, care nu poate fi garantată de software.

## Ordinea de livrare

| Etapă | Rezultat obligatoriu | Nu se promovează fără |
|---|---|---|
| E0 | Model de cărți și stare | invariantă de conservare a cărților |
| E1 | Motor legal Hold'em | testare exhaustivă pe stări mici și replay |
| E2 | Evaluator și equity | comparație cu oracol independent |
| E3 | Arena locală | seed, audit, mai multe politici și rezultate repetabile |
| E4 | AI baseline | benchmark pe distribuții nefolosite la reglaj |
| E5 | Adaptor online | protocol, timeout, reconectare și idempotency |
| E6 | AI avansat | benchmark statistic și regresii anti-exploit |

## Termeni de acceptanță

- `legal`: acțiunea este permisă de starea curentă;
- `correct`: motorul produce tranziția și plata regulilor Hold'em;
- `reproducible`: aceeași intrare și același seed produc același replay;
- `stronger`: politica bate baseline-ul pe set de evaluare separat, cu incertitudine raportată;
- `online-ready`: clientul poate relua o sesiune fără dublarea acțiunilor și fără pierderea stării;
- `authorized`: platforma publică sau confirmă că permite exact comportamentul clientului.

Niciunul dintre acești termeni nu înseamnă „profit sigur”.
