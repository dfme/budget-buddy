##########
# Prompt #
##########

Analysiere dieses Projekt. Liste JEDE Art von Daten auf, die es aufnimmt, verarbeitet,
speichert oder an Dritte sendet.
Pro Datenart: Quelle · wo gespeichert · wie lange · wer/was sieht sie · geht sie an eine
externe API/ein Modell? Nur was im Code steht — mit Datei-Beleg.


###########
# Antwort #
###########

# Datenarten-Inventar BudgetBuddy

Grundlage: `backend/src/main/**`, `frontend/src/**`, `db/migration/V01–V11`,
`application*.properties`, `render.yaml`, `.github/workflows/`. Alles unten ist im Code belegt;
Punkte, die nur geplant sind, stehen als solche markiert am Ende.

**Ablageorte, die im Folgenden immer gleich gemeint sind:**

| Kürzel | Was | Beleg |
| ------ | --- | ----- |
| **DB** | PostgreSQL bei Neon, Frankfurt/EU. Verbindung ausschliesslich über `SPRING_DATASOURCE_*` aus der Render-Umgebung, `sslmode=require`. | `application-prod.properties`, `render.yaml:36-45` |
| **Logs** | stdout des Render-Containers (Frankfurt/EU), Zugriff über das Render-Dashboard. | `render.yaml:11`, `application-prod.properties` (logging.level.*) |
| **RAM** | nur im Prozessspeicher, nie persistiert. | jeweils am Fundort belegt |
| **Anthropic** | `api.anthropic.com`, Modell `claude-haiku-4-5`. | `application.properties` (`anthropic.api.model`), `config/AnthropicConfig.java` |

---

## 1. Kontodaten

| # | Datenart | Quelle | Gespeichert | Wie lange | Wer/was sieht sie | Extern? |
|---|----------|--------|-------------|-----------|-------------------|---------|
| 1.1 | **E-Mail** | Registrierung/Login, `RegisterRequest`/`LoginRequest` | DB `users.email` (UNIQUE) | bis Kontolöschung | Nutzer selbst (`GET /api/users/me`), DB-Betreiber (Neon), Render-Ops | **nein** |
| 1.2 | **Passwort** | Registrierung, Passwortänderung | DB `users.password_hash` — **BCrypt-Hash**, Klartext nur im Request-Objekt (RAM) | Hash bis Kontolöschung | niemand im Klartext | **nein** |
| 1.3 | **Vorname / Nachname** (optional) | Registrierung | DB `users.first_name`, `users.last_name` | bis Kontolöschung | Nutzer selbst | **nein** |
| 1.4 | **Monatliches Einkommen** | manuell im Onboarding **oder** Vorschlag aus den eigenen Transaktionen | DB `users.monthly_income` `DECIMAL(10,2)` | bis Kontolöschung | Nutzer selbst | **nein** |
| 1.5 | **Onboarding-Status, Token-Version** | System | DB `users.onboarding_completed`, `users.token_version` | bis Kontolöschung | System | **nein** |

**Belege:** `auth/User.java:23-44` · `db/migration/V01__create_users_table.sql` ·
`V07__add_name_to_users.sql` · `V09__add_token_version_to_users.sql` ·
`auth/dto/UserProfileResponse.java:13-19` · `config/SecurityConfig.java:13` (BCryptPasswordEncoder) ·
`transaction/IncomeSuggestionService.java:139` (`suggestMonthlyIncome`)

Zu 1.4: Der Vorschlag entsteht rein lokal aus den bereits importierten Transaktionen
(Median über ≥2 Monate, ±5 %) — kein zusätzlicher Datenfluss.

---

## 2. Session / Authentifizierung

| # | Datenart | Quelle | Gespeichert | Wie lange | Wer/was sieht sie | Extern? |
|---|----------|--------|-------------|-----------|-------------------|---------|
| 2.1 | **JWT** — Inhalt: `subject` = User-ID, Claim `tokenVersion`, `exp` | Login | **nur im Browser** als `httpOnly`-Cookie (`Secure` in Prod, `SameSite=Strict`); serverseitig keine Session-Tabelle | `app.jwt.expiration=24h`; vorzeitig entwertet durch Passwortänderung (`token_version`) | Browser (kein JS-Zugriff), Server bei jedem Request | **nein** |
| 2.2 | **JWT-Secret** | Umgebungsvariable `JWT_SECRET` | nirgends im Repo, `@NotBlank` → Fail-fast beim Start | — | Render-Umgebung | **nein** |

**Belege:** `auth/JwtService.java:25,49-50` · `auth/JwtCookieFactory.java` ·
`application.properties` (`app.jwt.secret=${JWT_SECRET:}`, `app.cookie.secure`) ·
`application-prod.properties` (`app.cookie.secure=true`) · `render.yaml:33-34`

Im Token steht **keine E-Mail und kein Name** — nur die numerische ID.

---

## 3. PDF-Import (der sensibelste Pfad)

| # | Datenart | Quelle | Gespeichert | Wie lange | Wer/was sieht sie | Extern? |
|---|----------|--------|-------------|-----------|-------------------|---------|
| 3.1 | **PDF-Datei selbst** (kompletter Kontoauszug inkl. Adresse, IBAN, Saldi) | Upload `POST /api/import/pdf`, max. 10 MB | **RAM, nie persistiert** — weder DB noch Disk noch Objektspeicher | Dauer des Requests bzw. des Hintergrundjobs | PDFBox-Parser im Prozess | **nein** |
| 3.2 | **SHA-256 des PDF** | berechnet beim Upload | DB `transactions.pdf_sha256`, `import_jobs.pdf_sha256` | bis Kontolöschung | System (Duplikatcheck) | **nein** |
| 3.3 | **Buchungsdatum** | PDF-Parse | DB `transactions.buchungsdatum` | bis Kontolöschung | Nutzer selbst | **nein** |
| 3.4 | **Buchungstext** (bei PostFinance die Zahlungsart, z. B. `LASTSCHRIFT`) | PDF-Parse | DB `transactions.buchungstext` **unmaskiert** | bis Kontolöschung | Nutzer selbst | **ja, maskiert** → siehe 4.1 |
| 3.5 | **Buchungsdetails** — max. 3 Zeilen à 40 Zeichen: **Gegenpartei + Verwendungszweck** | PDF-Parse, Fortsetzungszeilen | DB `transactions.buchungsdetails` **unmaskiert** | bis Kontolöschung | Nutzer selbst | **ja, maskiert** → siehe 4.1 |
| 3.6 | **Betrag** | PDF-Parse | DB `transactions.betrag` `DECIMAL(10,2)` (positive Magnitude, ADR-9) | bis Kontolöschung | Nutzer selbst | nein (der Sanitizer maskiert Beträge; sie stehen ohnehin nicht im Prompt) |
| 3.7 | **Richtung** (`is_income`) und **Unsicherheitsflag** (`direction_uncertain`) | aus dem Saldo rekonstruiert | DB `transactions.is_income`, `transactions.direction_uncertain` | bis Kontolöschung | Nutzer selbst | **nein** |
| 3.8 | **Kategorie** | Lookup, Claude oder manuelle Korrektur | DB `transactions.category` | bis Kontolöschung | Nutzer selbst | — |
| 3.9 | **Import-Job-Metadaten**: `status`, `total`, `processed`, `degraded`, `created_at`, `finished_at` | System | DB `import_jobs` | bis Kontolöschung | Nutzer selbst (Fortschrittsanzeige) | **nein** |

**Belege:** `transaction/PdfImportService.java:29-31` («Kein PDF in der DB … ausschliesslich der
SHA-256-Hash»), `:104` (`sha256Hex`) · `transaction/ParsedTransaction.java:31-37,69` ·
`db/migration/V02`, `V05`, `V06`, `V08` ·
`application.properties` (`spring.servlet.multipart.max-file-size=10MB`)

**Was der Parser bereits verwirft, bevor irgendetwas gespeichert wird:** Gegenpartei-IBAN,
Postanschrift, maskierte Kartennummern, opake Referenzen, Dauerauftragsnummern, Seitenzahlen,
Kartenlimite — `SwissBankStatementParser.DETAIL_NOISE` (`:272-285`, Bausteine `:177-269`),
zusätzlich hart begrenzt auf `MAX_DETAIL_LINES` / `MAX_DETAIL_LENGTH`.

---

## 4. Kategorisierung — der einzige Weg zu einem externen Modell

| # | Datenart | Quelle | Gespeichert | Wie lange | Wer/was sieht sie | Extern? |
|---|----------|--------|-------------|-----------|-------------------|---------|
| 4.1 | **Maskierter Transaktionstext** = Buchungstext + Detailzeilen, durch `PromptSanitizer` gefiltert | `ParsedTransaction.fullText()` | **nirgends** — nur im Request-Body | Dauer des Calls (Timeout 10 s, 1 Retry) | Anthropic | **JA — `api.anthropic.com`, Modell `claude-haiku-4-5`** |
| 4.2 | **Antwort des Modells**: Nummer + Kategorie-Enum | Anthropic | landet als `transactions.category` | bis Kontolöschung | Nutzer selbst | — |
| 4.3 | **Gelerntes Händler-Pattern** = **roher, unmaskierter `buchungstext`**, upper-case | manuelle Kategorie-Korrektur des Nutzers | DB `category_lookup.empfaenger_pattern` (PK) — **ohne `user_id`, global** | **unbegrenzt — überlebt die Kontolöschung** | alle Nutzer implizit (Matching), DB-Betreiber | **nein** |
| 4.4 | **Token-/Kostenzeile**: Modell-ID, Anzahl Transaktionen, `input_tokens`, `output_tokens`, `cost_usd` | Anthropic-Response | Logs | Log-Retention Render | Render-Ops | **nein** |
| 4.5 | **API-Key** | Umgebungsvariable `ANTHROPIC_API_KEY` | nirgends im Repo | — | Render-Umgebung | geht als Header an Anthropic |
| 4.6 | **Startup-Healthcheck** `GET /v1/models` | System, einmalig nach `ApplicationReadyEvent` | — | — | Anthropic | **ja — enthält aber keinerlei Nutzerdaten** |

**Belege:** `categorization/ClaudeCategorizationService.java:406-414` (`buildUserPrompt`, einzige
Serialisierungsstelle, ruft dort `PromptSanitizer.sanitize`) · `:71-110` (System-Prompt,
Bündelgrösse 20) · `categorization/PromptSanitizer.java:194-207` (`sanitize`) ·
`config/AnthropicConfig.java:25,37` (Timeout 10 s, `MAX_RETRIES = 1`) ·
`categorization/AnthropicStartupHealthCheck.java:78` (`client.models().list()`) ·
`transaction/TransactionCategoryService.java:54-57` (lernt `transaction.getBuchungstext()`) ·
`categorization/CategoryLearningService.java:47-48` · `db/migration/V04__create_category_lookup_table.sql`

### Was der Sanitizer vor dem Versand entfernt

IBAN · Karten- und Kontonummern (inline, in Vierergruppen, unmaskierte Ziffernläufe 12–19) ·
Geldbeträge im CH-Format · opake Referenzen · Name einer natürlichen Gegenpartei
(`MUSTER, LEA` → `<NAME>`) · E-Mail-Adressen.
Belegt in `PromptSanitizer.java:44-190`, jede Regel gegen den Fixture-Korpus gegengeprüft.

### Was nachweislich **nicht** maskiert wird

- **Die Lookup-Stufe davor sieht den unmaskierten Text.** Das ist so gewollt und dokumentiert:
  sie ist lokal, ihr Input verlässt den Prozess nicht (`PromptSanitizer.java:8-12`).
- **Ein Vorname in einer frei getippten Zweckzeile.** `LASTSCHRIFT MUSTER, LEA SACKGELD LEA`
  wird zu `LASTSCHRIFT <NAME> SACKGELD LEA` — das nachgestellte `LEA` geht mit hinaus.
- **Die Telefonnummer eines Händlers** (`DIGITEC GALAXUS AG 044 913 2323`).

Beides ist als bekannte Restexposition in `PromptSanitizer.java:25-33` festgehalten und als
BE-CAT-08 (#233) offen.

### Fallbacks, bei denen gar nichts hinausgeht

Kein API-Key gesetzt → `CLAUDE_SKIPPED`, kein Request (`ClaudeCategorizationService.java:180-186`).
Circuit Breaker offen (≥3 Fehler in Folge, 60 s Cooldown) → kein Request (`:205-211`).
Jeder Fehler endet auf `Sonstiges`, nie in einem Abbruch.

---

## 5. Budget

| # | Datenart | Quelle | Gespeichert | Wie lange | Wer/was sieht sie | Extern? |
|---|----------|--------|-------------|-----------|-------------------|---------|
| 5.1 | **Fixkosten**: `bezeichnung` (Freitext des Nutzers), `betrag`, `intervall` | manuelle Eingabe | DB `fixed_costs` | bis Kontolöschung | Nutzer selbst | **nein** |
| 5.2 | **Safe-to-Spend-Betrag** | berechnet aus 1.4 + 3.6 + 5.1 | **nirgends** — bei jedem Request neu berechnet | — | Nutzer selbst | **nein** |

**Belege:** `db/migration/V03__create_fixed_costs_table.sql` · `budget/FixedCost.java` ·
`budget/SafeToSpendService.java` (kein Repository, kein `save`)

---

## 6. Benachrichtigungen

| # | Datenart | Quelle | Gespeichert | Wie lange | Wer/was sieht sie | Extern? |
|---|----------|--------|-------------|-----------|-------------------|---------|
| 6.1 | **Notification**: `type`, `reference_id`, `message` (Freitext, kann künftig Händlernamen tragen), `read_at`, `created_at` | System | DB `notifications` | bis Kontolöschung | Nutzer selbst (`GET /api/notifications`) | **nein** |

**Belege:** `db/migration/V10__create_notifications_table.sql` ·
`notification/NotificationService.java:42-50` · `notification/NotificationController.java:31`

---

## 7. Logs und Betriebsdaten

| # | Datenart | Quelle | Gespeichert | Wie lange | Wer/was sieht sie | Extern? |
|---|----------|--------|-------------|-----------|-------------------|---------|
| 7.1 | **Request-ID + User-ID an jeder Log-Zeile** | MDC, gesetzt in `LoggingContextFilter` bzw. nach JWT-Validierung | Logs | Render-Log-Retention | Render-Ops | **nein** |
| 7.2 | **Redigierter Transaktionstext** `<len=34 sha256=ab12cd34>` | Fehlerpfade der Kategorisierung | Logs | Render-Log-Retention | Render-Ops | **nein** |
| 7.3 | **Import-Kennzahlen**: Anzahl Transaktionen, Parse-Dauer in ms | `PdfImportService` | Logs | Render-Log-Retention | Render-Ops | **nein** |
| 7.4 | **Deployter Commit-SHA** | `RENDER_GIT_COMMIT` | `/actuator/info` | — | authentifizierte Nutzer, CD-Smoke-Test | **nein** |

**Belege:** `config/LoggingContextFilter.java:44-50` · `application.properties`
(`logging.pattern.level=%5p [req:%X{requestId:-none} user:%X{userId:-none}]`) ·
`categorization/LogRedaction.java:55-62`

Der Hash in 7.2 ist **gesalzen** — Salt einmalig pro Prozess, `SecureRandom`, nie geloggt
(`LogRedaction.java:37-48`). Damit ist ein Volltext auch nicht *bestätigbar*, wenn jemand ihn
errät. Einen `TRACE`-Pfad mit Klartext gibt es bewusst nicht (`:28-30`). Exception-Meldungen
fremder SDKs werden nie übernommen, nur der Typ (`LogRedaction.describe`, `:78-85`).

---

## 8. Frontend

| # | Datenart | Quelle | Gespeichert | Wie lange | Wer/was sieht sie | Extern? |
|---|----------|--------|-------------|-----------|-------------------|---------|
| 8.1 | **Theme-Wahl** (`light` / `dark` / `system`) | Einstellungen | `localStorage` im Browser | bis der Nutzer den Storage leert | nur der Browser | **nein** |

**Beleg:** `frontend/src/app/core/theme/theme.ts:11,29,108-113`

Vollständige Gegenprobe: `localStorage` / `sessionStorage` / `indexedDB` kommen im Produktivcode
**ausschliesslich** hier vor. Kein Google Analytics, kein Sentry, kein Matomo, kein CDN-Script —
`frontend/package.json` führt nur Angular, RxJS, `chart.js` / `ng2-charts` und `tslib`.
**Es gibt keinen Third-Party-Tracker im Frontend.**

---

## 9. Daten im Repository selbst

| # | Datenart | Quelle | Gespeichert | Extern? |
|---|----------|--------|-------------|---------|
| 9.1 | **8 Bank-PDF-Fixtures** (PostFinance, UBS, Viseca, Raiffeisen) | `backend/src/test/resources/pdf/` | Git — einsehbar für alle mit Repo-Zugriff | in jedem CI-Lauf im Runner |
| 9.2 | **Synthetisches E2E-PDF** | `e2e/fixtures/pdf/kontoauszug-synthetisch.pdf` | Git | dito |

Sieben der acht Fixtures sind generiert (`generate_pdf_fixtures.py`), Kontoinhaber «Peter Muster»,
IBAN `CH9300762011623852957` (PostFinance-Demo-IBAN). Die achte ist ein **echter Auszug in
anonymisierter Fassung** — Personen, Adressen, IBANs, Referenznummern und die Beträge, die
Vermögen verraten würden, sind ersetzt; Satzbild und Struktur stammen unverändert von der Bank.
**Beleg:** `transaction/SwissBankStatementParserFixtureTest.java:16-27`

---

## 10. Datenempfänger (Dritte) auf einen Blick

| Empfänger | Was er sieht | Standort | Beleg |
|-----------|--------------|----------|-------|
| **Neon** (PostgreSQL) | **alles** aus den Abschnitten 1, 3, 4.3, 5, 6 im Klartext (Passwörter nur als BCrypt-Hash) | Frankfurt / EU, TLS erzwungen | `application-prod.properties`, ADR-12 |
| **Render** (Hosting) | Prozessspeicher inkl. hochgeladener PDFs, alle Logs aus Abschnitt 7 | Frankfurt / EU | `render.yaml:7-11` |
| **Anthropic** | ausschliesslich **maskierte** Transaktionstexte (4.1) und den Healthcheck (4.6) | API-Region nicht im Code festgelegt | `AnthropicConfig.java`, `ClaudeCategorizationService.java:406` |
| **GitHub + Anthropic (CI)** | Quellcode und Diffs beim automatischen PR-Review — **inkl. der Fixtures aus 9.1**, keine Produktivdaten | GitHub-Runner | `.github/workflows/claude-pr-review.yml` |

Kein weiterer ausgehender Netzwerkaufruf im Produktivcode: OpenBanking ist laut `CLAUDE.md`
Nice-to-Have und **nicht implementiert**, es gibt keinen Mail-Versand, keinen Payment-Provider
und keinen Push-Dienst.

---

## 11. Löschung — was `deleteUser` tatsächlich räumt

```
UserService.deleteUser(userId)                       // UserService.java:184-189
  ├─ transactionCleanupPort.deleteAllForUser()       // transactions + import_jobs
  ├─ fixedCostCleanupPort.deleteAllForUser()         // fixed_costs
  ├─ notificationCleanupPort.deleteAllForUser()      // notifications
  └─ userRepository.delete(user)                     // users
```

Kein `ON DELETE CASCADE` an irgendeiner Tabelle — die Löschung ist bewusst eine explizite,
testbare Operation (`V05`, `V10`, `V11`, jeweils «nDSG-Hinweis»).

---

## 12. Befunde

**B1 — `category_lookup` ist global, unmaskiert und überlebt die Kontolöschung.**
Bei jeder manuellen Kategorie-Korrektur wandert der **rohe** `buchungstext` als Pattern in eine
Tabelle **ohne `user_id`** (`TransactionCategoryService.java:57` → `CategoryLearningService.learn`
→ `V04`). Damit gilt:

- Der Text eines Nutzers wirkt auf die Kategorisierung *aller* Nutzer.
- `deleteUser` räumt diese Tabelle nicht — sie steht nicht in der Kette oben.
- Bei PostFinance ist `buchungstext` zwar meist nur die Zahlungsart (`LASTSCHRIFT`, `TWINT`);
  bei anderen Layouts steht dort der Händler, und ein frei getippter Zweck kann einen Namen tragen.

Nicht über eine API auslesbar — es gibt keinen Endpoint auf `category_lookup` —, aber in der DB
zeitlich unbegrenzt vorhanden. Das ist die einzige Stelle, an der Nutzerdaten die Mandantengrenze
verlassen.

**B2 — `recurring_expenses` ist in `deleteUser` nicht vorgesehen.**
Die Tabelle existiert seit `V11` mit `payee_key` (normalisierter Empfängername) und `amount`.
Sie ist **aktuell folgenlos**: es gibt weder Entity noch Service — `grep -r RecurringExpense
backend/src/main` liefert nichts. Die Verpflichtung ist in `V11` als AC an BE-REC-01 (#253)
verankert. Wichtig ist nur, dass sie *vor* dem ersten Schreibzugriff eingelöst wird.

**B3 — Kein Auskunfts- oder Exportpfad.**
`GET /api/users/me`, `/api/transactions`, `/api/fixed-costs` und `/api/notifications` geben
jeweils ihren Ausschnitt zurück; einen gebündelten Datenexport (nDSG Art. 25) gibt es nicht.

**B4 — Keine Aufbewahrungsfrist ausser der Kontolöschung.**
Transaktionen und Import-Jobs bleiben unbegrenzt liegen. Automatisch aufgeräumt wird nur der
*Status* verwaister Jobs (`StaleImportJobCleaner:199-200` setzt `FAILED`, löscht keine Zeile).

**B5 — BE-CAT-08 (#233) bleibt offen** — Vorname in freier Zweckzeile, Händler-Telefonnummer.
Beides ist dokumentiert und regelbasiert nicht sauber lösbar.

### Was ausdrücklich sauber ist

- Das PDF selbst wird **nie** persistiert, nur sein SHA-256.
- Der Sanitizer sitzt an der **einzigen** Serialisierungsstelle, nicht beim Aufrufer — ein neuer
  Aufrufpfad kann ihn nicht umgehen (`ClaudeCategorizationService.java:401-405`).
- Logs tragen keinen Klartext, der Korrelationshash ist gesalzen, es gibt keinen `TRACE`-Ausweg.
- Beide Secrets kommen ausschliesslich aus der Umgebung; `JWT_SECRET` erzwingt Fail-fast.
- Kein Tracker im Frontend, `localStorage` trägt nur die Theme-Wahl.
- Alle Datenhalter liegen in der EU (Frankfurt).
