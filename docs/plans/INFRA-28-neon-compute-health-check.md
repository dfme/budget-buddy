# INFRA-28 — Neon-Compute-Verbrauch: Health-Check und Hikari-Pool halten die DB dauerhaft wach

**Kein GitHub-Issue** (bewusster Entscheid, 23.08.2026): Branch + Plan + PR genügen.
Task-ID `INFRA-28` ist die nächste freie ID im Bereich (höchste vergebene: INFRA-27).

---

## Ausgangslage

Neon-Warnmail vom 23.08.2026: Projekt `budget-buddy` hat **80.3 von 100 CU-Stunden** des
monatlichen Free-Kontingents verbraucht.

### Beweiskette

| Schritt | Befund |
| --- | --- |
| Rechnung | 80.3 CU-h ÷ 0.25 CU (Free-Default-Computegrösse) = **321 Compute-Stunden ≈ 13.4 Tage** |
| Datum | Render-Starter-Plan aktiv seit **09.08.2026** (INFRA-24, `e1468b0`) → bis 23.08. = 14 Tage = 336 h → 84 CU-h bei Dauerbetrieb |
| Abgleich | 80.3 / 84 = **96 %** — der Verbrauch entspricht praktisch exakt durchgehend wachem Compute seit dem Plan-Wechsel |

### Ursache

`render.yaml` setzt `healthCheckPath: /actuator/health`. Spring Boot hängt dort automatisch den
`DataSourceHealthIndicator` ein, sobald ein `DataSource`-Bean existiert; im Repo steht nirgends
`management.health.db.enabled=false`. **Jeder Health-Check macht damit ein `SELECT 1` gegen Neon.**

- **Vor INFRA-24:** Render Free fuhr den Service nach 15 Min ohne Traffic herunter → keine
  Health-Checks → Neon suspendierte nach 5 Min → Verbrauch nahe null.
- **Seit INFRA-24:** Service ist always-on → Health-Checks hören nie auf → **Neons Scale-to-Zero
  greift nie.** Der Wegfall des Render-Spin-Downs hat die Kosten an Neon vererbt.

Andere Verursacher ausgeschlossen: kein `schedule:`-Workflow, kein Keepalive-Job, kein
BetterStack-Monitor auf die App (dort existiert nur einer auf `google.com`).

### Zweiter, nachgelagerter Verursacher

Der Hikari-Pool läuft in Produktion mit reinen Defaults (HikariCP 6.3.0, von Spring Boot 3.5.3
verwaltet): `minimumIdle` = `maximumPoolSize` = 10, `maxLifetime` = 30 Min. Jede gehaltene
Verbindung wird nach `maxLifetime` evakuiert und **sofort nachgezogen** — dieser Neuaufbau weckt
Neon alle 30 Minuten. Solange der Health-Check die DB anfasst, fällt das nicht auf; danach würde
es zum nächsten Kostentreiber.

### Dringlichkeit

Bei 0.25 CU × 24 h = **6 CU-h/Tag** ist das Restkontingent von 19.7 CU-h in **gut 3 Tagen**
aufgebraucht (ca. 26./27.08.2026). Was Neon Free bei 100 % genau tut — drosseln oder bis zum
Periodenende suspendieren — ist im Dashboard zu prüfen; im schlechtesten Fall steht die Prod-DB.

---

## Ziel

Renders Dauerping fasst die Datenbank nicht mehr an, und der Pool ist im Leerlauf still. Neons
Scale-to-Zero greift damit wieder — so, wie CLAUDE.md es ohnehin beschreibt.

---

## Änderungen

### 1. `backend/src/main/resources/application.properties` — Liveness-Probe aktivieren

```properties
management.endpoint.health.probes.enabled=true
```

Erzeugt `/actuator/health/liveness`, das ausschliesslich den `livenessState` enthält — **keinen
DB-Indikator**. `/actuator/health` bleibt unverändert inklusive DB-Status für Menschen und den
CD-Smoke-Test.

Bewusst nicht `management.health.db.enabled=false`: Das würde den DB-Status global abschalten,
statt ihn nur aus dem Plattform-Ping herauszuhalten.

### 2. `backend/src/main/java/com/budgetbuddy/config/SecurityConfig.java` — Pfad freigeben

`PUBLIC_PATHS` listet `/actuator/health` als **exakten** Pfad; `/actuator/**` steht darunter
explizit auf `authenticated()`. Ohne Ergänzung liefert `/actuator/health/liveness` **401**, Render
markiert den Service als unhealthy und der Deploy rollt zurück. Neuer Eintrag:

```java
"/actuator/health/liveness",
```

Exakter Pfad, kein `/actuator/health/**` — die Deny-by-Default-Absicht des bestehenden Kommentars
(Review zu PR #187) bleibt erhalten.

### 3. `render.yaml` — Health-Check umhängen

```yaml
healthCheckPath: /actuator/health/liveness
```

### 4. `backend/src/main/resources/application-prod.properties` — Pool im Leerlauf stilllegen

```properties
spring.datasource.hikari.minimum-idle=0
spring.datasource.hikari.idle-timeout=60000
```

`minimum-idle=0` statt `1`: Jede gehaltene Verbindung wird nach `maxLifetime` ersetzt und weckt
Neon dabei. Der Fail-Fast beim Start bleibt erhalten — HikariCP öffnet auch bei `minimumIdle=0`
eine Prüfverbindung und schliesst sie sofort wieder (verifiziert im Bytecode von
`HikariPool.checkFailFast`: `"(initialization check complete and minimumIdle is zero)"`).

`maximum-pool-size` bleibt beim Default 10 — es beeinflusst den Compute-Verbrauch nicht, und
Neons Verbindungslimit bei 0.25 CU liegt weit darüber.

### 5. Doku

- **CLAUDE.md**, Abschnitt *PostgreSQL + Neon Gotchas*: Der Fallstrick „ein DB-behafteter
  Health-Check hebelt Scale-to-Zero aus" gehört dorthin, wo die Scale-to-Zero-Aussage steht.
- **ADR-12** prüfen und bei Bedarf um das Compute-Kontingent ergänzen.

---

## Tests

| Test | Prüft |
| --- | --- |
| `SecurityConfigTest` (Ergänzung) | `/actuator/health/liveness` ist unauthentifiziert 200 — die Bedingung, an der der Deploy sonst scheitert. Gegenprobe: `/actuator/health/readiness` bleibt 401, die Freigabe gilt also exakt einem Pfad |
| `ActuatorLivenessProbeTest` (neu) | Die Liveness-Gruppe enthält `livenessState`, aber **nicht** `db`; `/actuator/health` meldet den DB-Status weiterhin |
| `ProdPoolPropertiesTest` (neu) | Die Hikari-Werte aus `application-prod.properties` binden tatsächlich (`minimumIdle` = 0 statt Default -1) |
| Bestehende Suite | Vollständiger `./mvnw verify`-Lauf |

**Korrektur am Testentwurf während der Umsetzung:** Geplant war, die Abwesenheit des
DB-Indikators am Response-Body von `/actuator/health/liveness` zu prüfen. Das wäre ein wertloser
Test gewesen: Springs `AvailabilityProbesHealthEndpointGroup` gibt in `showComponents()` und
`showDetails()` hart `false` zurück (im Bytecode von `spring-boot-actuator-autoconfigure` 3.5.3
verifiziert) — der Body enthält also nie Komponenten, unabhängig von `show-details`, und der
Assert wäre auch dann grün, wenn der DB-Indikator in der Gruppe steckte. Geprüft wird deshalb die
Gruppenzugehörigkeit über den `HealthEndpointGroups`-Bean.

## Nicht im Scope

- Kein Upgrade auf den Neon-Launch-Plan
- Keine Änderung am CD-Smoke-Test — er nutzt weiterhin `/actuator/health` samt DB-Signal
- Kein GitHub-Issue

## Risiko und Rollback

Einziges echtes Risiko: `/actuator/health/liveness` antwortet in Produktion mit 401 oder 404 →
Render stuft den Service als unhealthy ein. Abgesichert durch den Security-Test; nach dem Deploy
zusätzlich manuell prüfen:

```bash
curl -s -o /dev/null -w "%{http_code}\n" https://budgetbuddy-0myo.onrender.com/actuator/health/liveness
```

Rollback: `healthCheckPath` in `render.yaml` zurück auf `/actuator/health`.

## Verifikation nach dem Deploy

Neon-Dashboard, Compute-Stunden über 24 h. Erwartung: Abfall von ~6 CU-h/Tag auf nahe null im
Leerlauf, mit kurzen Aufwachphasen bei echtem Nutzer-Traffic.
