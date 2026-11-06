# Cerințe online și limite de siguranță

## Primul adaptor

Prima țintă este Open Poker, o arenă pentru boți AI cu protocol WebSocket + JSON. Documentația publică descrie endpointul `wss://openpoker.ai/ws`, mesajele `connected`, `join_lobby`, `table_joined`, `your_turn`, `action_ack`, `resync_response` și `season_ended`.

Implementarea trebuie să trateze protocolul extern ca un adaptor, nu ca motorul jocului. Motorul local trebuie să poată rula fără internet și să poată reproduce o mână primită de la adaptor.

## Cerințe de transport

- cheia din `OPEN_POKER_API_KEY` sau credential store;
- niciodată în sursă, log, URL, screenshot sau commit;
- Bearer header preferat;
- o singură conexiune logică per bot;
- reconnect bounded și resync după pierdere;
- mesajele aplicate numai în ordinea lor;
- snapshot-ul autoritar instalat atomic;
- action acknowledgement urmărit prin ID stabil;
- aceeași acțiune retrimisă numai dacă idempotency contract o permite;
- timeout-ul de decizie și expirarea turn-ului observabile;
- limită de 20 mesaje/secundă și 10 conexiuni/minut conform documentației curente a platformei;
- dry-run și spectator mode separate de player mode.

## Politica de autorizare

Este permisă conectarea numai la:

- propria aplicație/server;
- un benchmark sau o arenă care declară explicit că acceptă boți;
- o platformă pentru care există aprobare scrisă sau API oficial pentru automatizare.

Nu se implementează:

- screen scraping pentru site-uri comerciale;
- auto-click sau injectare de input;
- real-time assistance pe cont uman unde este interzis;
- timing artificial pentru evitarea detectării;
- coluziune, multi-account, chip dumping sau manipularea clasamentului;
- orice logică de ocolire a verificărilor platformei.

## Secrete și operare

- `.env` este local și ignorat de Git;
- `OPEN_POKER_API_KEY` se rotește dacă a fost expusă;
- `--online` trebuie cerut explicit;
- `--dry-run` este implicit când nu există confirmare;
- `--auto-rebuy` este dezactivat implicit;
- oprirea procesului trebuie să lase replay-ul și ultima stare închisă coerent.
