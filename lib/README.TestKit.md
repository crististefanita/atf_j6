# Test Kit — clasificare și audit JUnit

## Pe scurt

Kit oferă adnotări, comparații evaluate și lifecycle neutru. JAR-ul nu pornește browser, broker ori ADB și nu publică singur Extent.

| Nevoie | API și limită |
|---|---|
| Nivelul testului | @AtfUnitTest, @AtfComponentTest, @AtfIntegrationTest, @AtfE2eTest; clasificare, fără lansare automată. |
| Comparații auditabile | RecordedAssertions; verdictul JUnit precede preview-ul 240, care păstrează perechile Unicode complete. |
| Aserțiuni JUnit existente | AuditedAssertions în scope-ul adaptorului; diagnostic lazy, identitate și cauză originale. Apelurile JUnit directe nu devin automat auditabile. |
| Valori integrale | AssertionValueDisplay poate primi un observer Projection cu rol expected/actual și text redactat, înainte de preview; consumerul deține retenția. |
| Faze și cleanup | ComponentAuditExtension / ComponentAuditAdapter, TestRunInitialization, FailureAwareCleanup. |
| Politici și resurse | Resolverele Atf*Policy; readResourceUtf8 pentru classpath/JAR, requiredPath pentru fișier real. |

## Exemplu minim

Adaptorul deține recorderul și publică rezultatul; o nepotrivire păstrează excepția JUnit originală:

```java
import atf.testkit.assertions.RecordedAssertions;

public static void verifyState(RecordedAssertions audit, String actual) {
    audit.equals("State", "ready", actual);
}
```

## Scope și integrare

- Dependență atf.api:atf-test-kit cu versiunea atf.testkit.version din POM-ul proiectului. Folosiți test scope în produs; compile este justificat numai pentru suportul main al unui modul de test. Nu includeți Kit în APK/aplicație.
- JUnit Jupiter API și Jackson sunt tranzitive. Runnerul declară motorul junit-jupiter-engine și selecția sa; adnotarea de nivel nu înlocuiește @Test ori extensia consumerului.
- Pentru Extent: adaptorul trimite checks către AssertionAuditReport; AssertionAuditRun deține sesiunea comună. Kit nu copiază rendererul. [Integrare Reporting](README.Reporting.md#scope-și-integrare).
- Sanitizer-ul se injectează prin AssertionValueDisplay înainte de preview. Comparația rulează o dată; valorile stocate sunt limitate și nu constituie arhivă integrală. Unitățile pure își păstrează politica proprie.
- Scope-urile și resursele sunt per invocare; închideți-le și păstrați cauza inițială. Politicile globale nu devin izolate între cititori externi printr-un lock local.

---

Consumerul verifică local selecția suitei, lifecycle-ul și raportarea aserțiunilor. Pentru schimbarea unei perechi binare, urmează [procesul de mentenanță și feedback](README.md#mentenanță-și-feedback).
