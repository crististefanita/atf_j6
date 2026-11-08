# Cerințe J6

## Pe scurt

Acest director definește contractele de produs și condițiile de promovare. Folosiți documentul potrivit domeniului, iar `README.md` rămâne harta comună.

| Întrebare | Document owner |
|---|---|
| Ce trebuie să ofere produsul și cui? | [Cerințe de produs](PRODUCT_REQUIREMENTS.md) |
| Ce stări și reguli are mâna? | [Cerințe de domeniu](DOMAIN_REQUIREMENTS.md) |
| Cum funcționează masa, blindurile și turneul? | [Reguli de turneu](TOURNAMENT_RULES.md) |
| Cum se măsoară decizia AI? | [Cerințe AI și evaluare](AI_REQUIREMENTS.md) |
| Ce limitează o eventuală conexiune externă? | [Cerințe online și siguranță](ONLINE_REQUIREMENTS.md) |

## Ordinea de maturizare

| Etapă | Contract | Poarta de promovare |
|---|---|---|
| E0 | Model de cărți și stare | Invariantă de conservare a cărților. |
| E1 | Motor legal Hold’em | Stări-limită și replay independent. |
| E2 | Evaluator și equity | Comparație cu un oracol independent. |
| E3 | Arena locală | Seed, audit, politici comparabile și rezultate reproductibile. |
| E4 | AI baseline | Set de evaluare separat de reglaj. |
| E5 | Adaptor online | Autorizare, protocol, timeout, reconectare și idempotency. |
| E6 | AI avansat | Benchmark statistic și regresii anti-exploit. |

Etapele online și AI avansat nu sunt declarate livrate doar fiindcă au cerințe scrise. Starea reală se verifică în [codul și testele ownerului](../../README.md).

## Termeni de acceptanță

- `legal`: acțiunea este permisă de starea curentă;
- `correct`: motorul produce tranziția și plata regulilor Hold’em;
- `reproducible`: aceeași intrare și același seed produc același replay;
- `stronger`: politica depășește baseline-ul pe set de evaluare separat, cu incertitudine raportată;
- `online-ready`: clientul reia sesiunea fără acțiuni duplicate sau pierderea stării și are autorizare explicită;
- `authorized`: platforma publică sau confirmă că permite exact comportamentul clientului.

Niciunul dintre acești termeni nu înseamnă profit financiar garantat.