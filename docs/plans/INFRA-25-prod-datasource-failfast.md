# [INFRA-25] Fail-fast im prod-Profil bei fehlender oder ungültiger Datasource-URL

- **Issue:** [#150](https://github.com/dfme/budget-buddy/issues/150)
- **Task-ID:** `INFRA-25`
- **Branch:** `feature/INFRA-25-prod-datasource-failfast`
- **Story:** — (kein us-*-Label, Infrastruktur, querschnittlich)
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-29

## Ausgangslage

Beim ersten Deploy gegen Neon (DB-05, #89) traten zwei Konfigurationsfehler auf, die beide erst
spät und mit irreführender Meldung sichtbar wurden: falsch benannte Variablen (App fällt auf
`localhost` zurück, scheitert auf Render mit einer Meldung, die nach Netzwerkproblem statt
Tippfehler aussieht — lokal dagegen ist es ein stiller Fallback gegen die falsche DB) und ein
vergessenes `jdbc:`-Präfix (Hikari-Stacktrace erst nach vollständigem Docker-Build im Render-Log).
Für `JWT_SECRET` ist Fail-fast bereits über `@NotBlank` in `JwtProperties` gelöst; für die
Datasource-URL fehlt die analoge Behandlung.

## Entscheid

Bean-Validation (`@NotBlank`/`@Validated`, das Muster aus `JwtProperties`) passt hier nicht direkt:
sie deckt nur "leer", nicht die drei weiteren Regeln (localhost, `jdbc:`-Präfix, eingebettete
Zugangsdaten), und ihre Prüfung läuft bei der Bean-Erzeugung — die Reihenfolge relativ zu
Hikari/pgjdbc ist dabei nicht garantiert. AC #3 verlangt aber, dass die Prüfung **vor** pgjdbc
greift, damit ein Passwort nie geloggt wird.

Gewählt: ein `EnvironmentPostProcessor` (Spring-Boot-SPI, registriert über
`META-INF/spring.factories`). Er läuft garantiert vor jeglicher Bean-Erstellung — also bevor
Hikari/pgjdbc die URL je zu Gesicht bekommen — und ist nur aktiv, wenn `prod` im aktiven Profil
steht. Damit ist AC #4 (Default-Profil unverändert) strukturell gelöst, nicht nur durch einen Test
abgesichert.

## Betroffene Files

- **Neu:** `backend/src/main/java/com/budgetbuddy/config/DataSourceUrlEnvironmentPostProcessor.java`
- **Neu:** `backend/src/main/resources/META-INF/spring.factories`
- **Neu:** `backend/src/test/java/com/budgetbuddy/config/DataSourceUrlEnvironmentPostProcessorTest.java`
- **Geändert:** `backend/src/main/resources/application-prod.properties` (Kommentar Zeile 16–18)

## Implementierungsschritte

1. `DataSourceUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered` mit
   `getOrder() == Ordered.LOWEST_PRECEDENCE` (läuft nach der Profil-/Property-Auflösung durch
   `ConfigDataEnvironmentPostProcessor`).
2. Nur aktiv, wenn `"prod"` in `environment.getActiveProfiles()` enthalten ist.
3. Vier Prüfungen auf `spring.datasource.url`, jede mit eigener Fehlermeldung, die die Variable
   beim Namen nennt und das erwartete Format zeigt — die rohe URL wird **nie** in eine Meldung
   eingebettet, das vermeidet jede Klartext-Passwort-Exposition strukturell, nicht nur im
   Credentials-Fall:
   - leer/nicht gesetzt
   - enthält `localhost` oder `127.0.0.1`
   - beginnt nicht mit `jdbc:`
   - `@` in der Authority (zwischen `//` und dem nächsten `/`) — eingebettete Zugangsdaten
4. Bei Verstoss: `IllegalStateException` mit Variable + erwartetem Format
   (`jdbc:postgresql://<host>.eu-central-1.aws.neon.tech/<db>?sslmode=require`, wie im
   bestehenden Kommentar in `application-prod.properties`).
5. Registrierung in `backend/src/main/resources/META-INF/spring.factories` (Datei existiert noch
   nicht, wird neu angelegt):
   ```
   org.springframework.boot.env.EnvironmentPostProcessor=\
   com.budgetbuddy.config.DataSourceUrlEnvironmentPostProcessor
   ```
6. Kommentar in `application-prod.properties` (Zeile 16–18) korrigieren: die alte Aussage
   ("ein lauter Fehler, kein stiller Fallback") stimmt nur auf Render, nicht lokal mit laufendem
   Postgres auf 5432. Neuer Kommentar verweist auf den Validator und benennt INFRA-25.

## Test-Strategie

Reine Unit-Tests gegen die Klasse direkt mit `org.springframework.mock.env.MockEnvironment`
(Vorbild: `AnthropicStartupHealthCheckTest` — bewusst kein `@SpringBootTest`, kein echter Kontext,
kein Netz-/DB-Zugriff nötig, weil die Prüfung rein auf dem Property-String arbeitet):

- leer + Profil `prod` → Exception, Meldung nennt `SPRING_DATASOURCE_URL` + erwartetes Format
- `localhost`/`127.0.0.1` + Profil `prod` → Exception
- ohne `jdbc:`-Präfix + Profil `prod` → Exception
- Zugangsdaten in der URL (`jdbc:postgresql://user:pass@host/db`) + Profil `prod` → Exception,
  **Assertion, dass die Meldung das Passwort nicht enthält**
- gültige Neon-URL + Profil `prod` → kein Wurf (Happy Path)
- ungültige/leere URL, aber Profil ≠ `prod` → kein Wurf (belegt AC #4 auf Unit-Ebene)

Zusätzlich manuell vor dem PR (nicht automatisiert, da echte DB/Docker nötig):
`SPRING_PROFILES_ACTIVE=prod` ohne `SPRING_DATASOURCE_URL` starten lassen und die
Fail-fast-Meldung live prüfen, sowie `./mvnw spring-boot:run` gegen Compose-Postgres ohne gesetzte
Variablen (Default-Profil unverändert, AC #4).

## Acceptance Criteria (aus Issue #150)

- [ ] Start mit prod-Profil und fehlender bzw. auf `localhost` zeigender `SPRING_DATASOURCE_URL`
      bricht mit einer Meldung ab, die die Variable und das erwartete Format nennt
- [ ] Start mit prod-Profil und einer URL ohne `jdbc:`-Präfix bricht mit derselben Klarheit ab
- [ ] Start mit prod-Profil und Zugangsdaten in der URL bricht mit einer Meldung ab, die das
      Passwort nicht enthält
- [ ] Default-Profil ist unverändert: `./mvnw spring-boot:run` gegen den Compose-Postgres
      funktioniert ohne gesetzte Variablen
- [ ] Der irreführende Kommentar in `application-prod.properties` ist korrigiert
- [ ] Tests decken die drei Fehlerfälle und den Happy Path ab
