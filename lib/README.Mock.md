# Mock — transport HTTP determinist

## Pe scurt

`atf-mock` înlocuiește portul Core `ApiTransport` cu un catalog local de răspunsuri. Folosiți-l pentru componente și contracte offline, cu intrări controlate și un fallback explicit.

## Exemplu minim

`mock.yaml` în directorul de lucru:

```yaml
mock:
  routes:
    - {method: GET, path: /health, status: 200, body: '{"status":"ready"}'}
  fallback: {status: 500, body: '{}'}
```

Fragment într-o metodă; transportul rămâne offline:

```java
import atf.core.http.request.ApiRequest;
import atf.mock.AtfMockTransport;
import java.nio.file.Path;

var mock = AtfMockTransport.fromConfiguration(Path.of("mock.yaml"));
var response = mock.send(ApiRequest.of("GET", "/health"),
        "https://offline.test/health", "none").join();
```

`response.status()` este `200`; o cerere fără rută potrivită primește fallback-ul `500`. Testul compară independent răspunsul cu rezultatul așteptat.

## Catalog și potrivire

| Necesitate | API sau contract |
|---|---|
| Catalog YAML explicit | `AtfMockTransport.fromConfiguration(Path)` |
| Configurația proiectului | `fromProjectConfiguration()`: `atf.mock.config` > `ATF_MOCK_CONFIG` > `atf-mock.yaml/yml` |
| Condiții de request | Metodă, cale, profil auth, query, headere cerute și corp așteptat în `MockRoute` |
| Corp de request | Mod implicit `json`, probă MIME negativă `wrong-content-type`; potrivire numerică fără conversie prin `double` |
| Query repetat | `ApiQueryParameters`; include valorile cerute, cu multiplicitate |

- Fără configurație, `fromProjectConfiguration()` folosește fixture-ul `defaults()`; catalogul aplicației rămâne local.
- Durata din rută este metadată a răspunsului, nu o întârziere reală simulată.
- O probă mock verifică integrarea; acceptanța serviciului, brokerului sau dispozitivului real rămâne separată.

---

Consumerul verifică local rutele, condițiile de request și integrarea Mock în teste offline. Pentru schimbarea unei perechi binare, urmează [procesul de mentenanță și feedback](README.md#mentenanță-și-feedback).
