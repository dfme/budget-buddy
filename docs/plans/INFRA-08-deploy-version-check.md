# [INFRA-08] Smoke-Test verifiziert nicht die neue Version

- **Issue:** #68
- **Task-ID:** INFRA-08 (INFRA-07 ist durch #60 belegt)
- **Branch:** `fix/INFRA-08-deploy-version-check`
- **Typ:** Bug (Label `bug`) — betrifft die CD-Pipeline aus INFRA-06

## Problem

Der Render Deploy Hook ist fire-and-forget (`cd.yml`): Er stösst den Build an und
kehrt sofort zurück; Render baut danach asynchron. Der Smoke-Test pollt aber sofort
`/actuator/health` — und trifft je nach Zustand des Free-Tier-Service:

- **Service wach:** die noch laufende **alte** Instanz antwortet in Sekunden mit 200
  → Job grün, obwohl die neue Version noch gar nicht gebaut ist (False Green).
- **Service schlafend:** curl läuft in Timeouts (`000`), bis irgendwann 200 kommt
  → zufällig korrekt, aber nicht garantiert.

Beleg aus CD-Run 29341046814 (Commit auf main, 2026-07-14):

```
14:29:48  Render deploy hook ausgelöst.  {"deploy":{"id":"dep-d9b4fn6rnols739fo420"}}
14:29:58  Versuch 1/40: HTTP 000000 — warte 15s...
...
14:32:18  Health-Check grün (HTTP 200) nach Versuch 7.
```

Kernpunkt: Dem Log ist **nicht entnehmbar**, ob die 200 bei Versuch 7 von der neuen
oder der gerade aufgewachten alten Instanz kam. Der Check ist unfalsifizierbar — er
prüft "irgendeine Instanz antwortet", nicht "die neue Version läuft".

## Entscheide

- **Ansatz: Commit-SHA-Abgleich via `/actuator/info`** (Variante A). Die App meldet den
  Commit, den sie ausführt; der Workflow pollt, bis dieser `GITHUB_SHA` entspricht.
  Grundlage ist `RENDER_GIT_COMMIT` — eine von Render dokumentiert gesetzte Default-Env-Var,
  verfügbar zur Build- und Laufzeit (https://render.com/docs/environment-variables).
  Kein neues Secret nötig; prüft die tatsächliche Garantie (neue Version bedient Traffic).
- **Verworfen: Render-API-Polling** (Variante B, `GET /v1/services/{id}/deploys/{deployId}`
  bis `status=live`). Wäre autoritativer bei Build-Fehlern, braucht aber zwei neue Secrets
  (`RENDER_API_KEY`, `RENDER_SERVICE_ID`) und beweist trotzdem nicht, dass der Endpoint
  danach die neue Version ausliefert. Für MVP-Scope nicht gerechtfertigt.
- **Akzeptierte Kröte:** Ein auf Render *fehlgeschlagener* Build wird erst nach dem
  Timeout rot, nicht sofort. Preis für den Verzicht auf die Render-API-Secrets.
- **Fail-safe:** Fehlt `RENDER_GIT_COMMIT`, meldet die App `unknown` — das matcht nie
  → Job rot. Kein stiller Rückfall auf False Green.
- **Präfix- statt Exakt-Vergleich** (Nachtrag aus dem Review): `GITHUB_SHA` ist immer der
  volle 40-Zeichen-SHA; für `RENDER_GIT_COMMIT` ist das Format **nicht dokumentiert**
  (Render-Docs lassen offen, ob voll oder gekürzt). Ein Exakt-Match wäre bei gekürztem SHA
  dauerhaft rot — der Fix hätte den Bug durch das Gegenteil ersetzt. Der Vergleich prüft
  daher, ob der gemeldete Wert ein Präfix von `GITHUB_SHA` ist, mit zwei Guards:
  nicht-leer (leer wäre Präfix von allem) und Länge >= 7 (kürzer ist kein belastbarer
  Commit-Nachweis). Verifiziert für: voller SHA, 7/12-Zeichen-SHA, leer, `unknown`,
  fremder Commit, 1 und 6 Zeichen.
- **Timeout 20 min** (80 × 15s), bisher 10 min. Begründung: Das Timeout ist eine
  Obergrenze, keine Laufzeit — ein erfolgreicher Deploy verlässt die Schleife, sobald der
  SHA matcht (~3–8 min). Die vollen 20 min fallen **nur bei kaputtem Deploy** an. Ein zu
  tiefes Timeout macht dagegen langsame, aber *erfolgreiche* Deploys rot → Fehlalarm auf
  dem Normalpfad → genau der Vertrauensverlust, den dieser Fix behebt. Renders reale
  Build-Dauer ist derzeit unbekannt (Actions baut in 35s, aber mit Dependency-Cache und
  schnellerer Hardware; Render zieht pro Build Node + npm ci + alle Maven-Deps neu).
  **Follow-up:** Nach dem ersten CD-Run zeigt das Log, bei welchem Versuch der neue SHA
  auftaucht → mit dieser Messung auf gemessenes Maximum + Puffer runter.
- **Nebenbefund mitgefixt:** `HTTP 000000` im Log — `-w "%{http_code}"` gibt bei Fehlern
  bereits `000` aus, `|| echo "000"` hängt ein zweites `000` an. Harmlos, aber irreführend.

## Betroffene / neue Files

- **Ändern:** `.github/workflows/cd.yml` — Smoke-Test auf SHA-Abgleich, Timeout, `000`-Fix
- **Ändern:** `backend/src/main/resources/application.properties` — `info` exponieren
- **Ändern:** `backend/src/main/java/com/budgetbuddy/config/SecurityConfig.java` — `/actuator/info` public
- **Ändern:** `backend/src/test/java/com/budgetbuddy/config/SecurityConfigTest.java` — Test `infoEndpointIsPublic`
- **Ändern:** `CLAUDE.md` — Konvention "Git: Bug-Tickets"
- **Neu:** `backend/src/test/java/com/budgetbuddy/config/ActuatorInfoTest.java`

## Implementierung

### 1. `application.properties`

```properties
management.endpoints.web.exposure.include=health,info
management.info.env.enabled=true
info.app.commit=${RENDER_GIT_COMMIT:unknown}
```

`management.info.env.enabled=true` ist nötig, weil der env-Info-Contributor seit
Spring Boot 2.6 standardmässig deaktiviert ist. Exponiert wird ausschliesslich `info.*`
— kein Env-Dump.

### 2. `SecurityConfig`

`/actuator/info` in `PUBLIC_PATHS` aufnehmen (neben `/actuator/health`).

### 3. `cd.yml` — Smoke-Test

```bash
max_attempts=80   # 80 × 15s = 1200s = 20 min
for attempt in $(seq 1 "$max_attempts"); do
  commit=$(curl -s --max-time 10 "$BASE_URL/actuator/info" | jq -r '.app.commit // "?"')
  if [ "$commit" = "$GITHUB_SHA" ]; then
    status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$BASE_URL/actuator/health")
    [ "$status" = "200" ] && exit 0
  fi
  sleep 15
done
exit 1
```

Zusätzlich `timeout-minutes` am Job als Airbag. `jq` ist auf `ubuntu-latest` vorinstalliert.

## Test-Strategie

1. **Integration** (`ActuatorInfoTest`, `@SpringBootTest` + MockMvc, analog zu den
   bestehenden Config-Tests): `/actuator/info` → 200, `$.app.commit` vorhanden;
   ohne gesetztes `RENDER_GIT_COMMIT` → `unknown`
2. **Integration** (`SecurityConfigTest`): `/actuator/info` ohne Auth erreichbar
   (gleiche Form wie das bestehende `healthEndpointIsPublic`)
3. **Lokal:** `./mvnw verify`
4. **Shell-Logik:** Poll-Schleife lokal mit gefaktem `curl` trockenlaufen lassen
   (SHA-Match, Nicht-Match, Timeout-Pfad)
5. **Einschränkung:** Die Workflow-Logik selbst ist lokal nicht verifizierbar — erster
   echter Beweis ist der CD-Run nach dem Merge. Erwartung: Der Job läuft **mehrere
   Minuten** statt in Sekunden grün zu werden, und das Log zeigt den SHA-Abgleich.

## Acceptance Criteria (aus Issue #68)

- [ ] Der Smoke-Test kann nicht mehr gegen ein altes Deployment grün werden
- [ ] Der Job ist nur grün, wenn die Instanz den Commit des CD-Runs (`GITHUB_SHA`) meldet
      und `/actuator/health` 200 liefert
- [ ] Fehlt die Versionsinfo, wird der Job rot (kein stilles Durchwinken)
- [ ] Kein neues Secret nötig
