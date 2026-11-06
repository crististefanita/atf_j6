# Arhitectură J6

```text
                 +----------------------+
                 |      holdem-app      |
                 | CLI / operator       |
                 +----------+-----------+
                            |
              +-------------+--------------+
              |                            |
       +------v------+              +------v------+
       | holdem-arena |              | holdem-online|
       | local games  |              | WS adapter   |
       +------+-------+              +------+-------+
              |                             |
              +-------------+---------------+
                            |
                    +-------v--------+
                    |   holdem-ai    |
                    | policies/search|
                    +-------+--------+
                            |
                    +-------v--------+
                    |  holdem-core   |
                    | rules/state    |
                    +----------------+
```

## Limite de dependențe

- `holdem-core` nu depinde de AI, rețea sau UI;
- `holdem-ai` consumă `Observation`, nu `FullState`;
- `holdem-arena` poate folosi starea completă intern pentru simulare, dar filtrează observația înainte de policy;
- `holdem-online` convertește wire messages în `Observation` și `ActionRequest`;
- `holdem-app` orchestrează, dar nu introduce reguli de poker.

## Reducer și replay

Motorul trebuie modelat ca reducer:

```text
FullState + Action -> Transition(FullState, PublicEvent, SettlementDelta)
```

Orice eveniment trebuie să poată fi serializat, verificat și aplicat din nou. Pentru online, reducerul local nu înlocuiește snapshot-ul autoritar al serverului; îl folosește ca verificare și fallback.

## Configurare

Regulile de joc, bugetul AI, profilele de adversari și transportul se configurează declarativ. Configurația nu poate schimba invarianta fundamentală că politica nu primește cărți adverse reale.
