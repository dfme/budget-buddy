##########
# Prompt #
##########

Wir sind ein Team von 3 Entwicklern, 3 Monate Zeit.
Tech-Stack: C2 Container Diagram aus @CLAUDE.md

Hilf uns eine Definition of Done zu erstellen.
Sie soll:
- Realistisch für ein kleines Team sein
- Qualität sichern ohne uns zu lähmen
- Konkret und überprüfbar sein

Schlage 6-8 Kriterien vor. Für jedes: warum ist es wichtig?


############
# Antwort  #
############

## Definition of Done — BudgetBuddy

Kontext: 3 Devs, 3 Monate, Angular + Spring Boot + SQLite + Claude API, MVP-Fokus.

---

### 1. Code ist reviewed (1 Approval genügt)

**Warum:** Mit 3 Personen sind Vier-Augen-Reviews realistisch. Fehler im Kategorisierungs- oder Safe-to-Spend-Code sind direkt nutzerseitig sichtbar — ein kurzer Review-Loop verhindert die teuersten Bugs ohne Overhead.

**Check:** GitHub PR hat mindestens 1 Approval vor dem Merge.

---

### 2. Keine kompilier- oder build-Fehler (Backend + Frontend)

**Warum:** `mvn package` und `ng build` müssen grün sein. Gebrochene Builds blockieren das gesamte Team sofort — in einem 3-Personen-Team ist das tagelanger Stillstand.

**Check:** CI-Pipeline (oder lokal) läuft durch ohne Error.

---

### 3. Neue API-Endpoints sind in OpenAPI dokumentiert

**Warum:** Springdoc generiert die Doku automatisch aus Annotations — Aufwand nahe null. Ohne Doku wissen die Frontend-Entwickler nicht, was der Endpoint erwartet und zurückgibt. Stoppt Rückfragen.

**Check:** Swagger UI zeigt den Endpoint mit Request/Response-Schema.

---

### 4. Happy Path ist durch automatisierten E2E-Test abgedeckt

**Warum:** Unit-Tests prüfen Logik, aber nicht ob der Feature-Flow für Lara oder Marc tatsächlich funktioniert. Ein automatisierter E2E-Test (Playwright) schützt dauerhaft vor Regressionen — gerade der kritische Pfad PDF-Upload → Kategorisierung → Safe-to-Spend darf nach keinem Merge stillschweigend brechen.

**Check:** Playwright-Test für den Happy Path des Features läuft grün in der CI-Pipeline (`npx playwright test`).

---

### 5. Acceptance Criteria der zugehörigen User Story sind erfüllt

**Warum:** Verhindert Scope Creep in beide Richtungen — kein Over-Engineering, aber auch kein "fast fertig". Die ACs in `docs/requirements/US-XX-*.md` sind der einzige gemeinsame Massstab, was "fertig" bedeutet.

**Check:** Jedes AC-Kriterium im zugehörigen US-Dokument ist abhängbar (ja/nein).

---

### Bewusst weggelassen

| Was | Warum nicht |
|-----|-------------|
| 80% Test Coverage | Zu starr; SQLite-Integration-Tests sind wertvoller als Unit-Test-Zahlen |
| Performance-Budget | Kein Lasttest-Kontext für MVP; wird erst relevant wenn > 50 simultane User |

