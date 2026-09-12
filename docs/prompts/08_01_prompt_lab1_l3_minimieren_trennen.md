##########
# Prompt #
##########

@docs/prompts/08_01_prompt_lab1_l1_datenarten_inventar.md
@docs/prompts/08_01_prompt_lab1_l2_datenkarte.md
Wo in diesem Projekt werden personenbezogene oder sensible Daten
gespeichert oder an externe Dienste gesendet? Schlage für jeden Fall
vor: weglassen, kürzer aufbewahren, oder trennen. Zeige die konkrete
Code-Stelle. Ich entscheide, was umgesetzt wird. 
Bitte antwort als Markdown unter antwort im file @docs/prompts/08_01_prompt_lab1_l3_minimieren_trennen hinterlegen.

###########
# Antwort #
###########
# Minimieren und Trennen — 14 Fundstellen mit Vorschlag

Grundlage: das Inventar aus [L1](08_01_prompt_lab1_l1_datenarten_inventar.md) und die Karte aus
[L2](08_01_prompt_lab1_l2_datenkarte.md). Jede Zeile unten ist am Code nachgeprüft, nicht aus den
beiden Dokumenten übernommen — **eine Angabe aus L1/L2 hat der Gegencheck korrigiert** (F2, siehe
dort). Drei Hebel, wie im Prompt vorgegeben:

| Hebel | Bedeutung hier |
| --- | --- |
| **weglassen** | Das Datum wird gar nicht erst erhoben oder nach Gebrauch verworfen |
| **kürzer** | Es bleibt, aber mit einer Frist statt «bis auf Weiteres» |
| **trennen** | Es bleibt, wird aber an den Mandanten gebunden, pseudonymisiert oder vom Kontext gelöst |

## Entscheidungstabelle

| # | Fundstelle | Was daran heikel ist | Vorschlag | Aufwand | Issue |
| --- | --- | --- | --- | --- | --- |
| **F1** | `category_lookup` — global, unmaskiert, ewig | Mandantengrenze | **trennen** | M | — |
| **F2** | `deleteUser` ohne Aufrufer | keine Frist ist einlösbar | **kürzer** (Löschpfad) | M | [#290](https://github.com/dfme/budget-buddy/issues/290) |
| **F3** | `transactions` ohne Retention | unbegrenzt | **kürzer** | M | — |
| **F4** | `import_jobs` bleiben ewig | unbegrenzt, tragen `pdf_sha256` | **kürzer** | S | — |
| **F5** | `recurring_expenses` nicht im Löschpfad | Waise ab dem ersten Schreibzugriff | **trennen** (jetzt vorbereiten) | S | — |
| **F6** | Restexposition im Prompt (BE-CAT-08) | Vorname, Telefonnummer gehen hinaus | **weglassen** | M | — |
| **F7** | `buchungsdetails` im Prompt | mehr als die Kategorisierung braucht | **weglassen** (messen!) | S | — |
| **F8** | `notifications.message` Freitext | noch kein Produzent — Regel jetzt setzen | **weglassen** | XS | — |
| **F9** | `user_id` im Klartext an jeder Log-Zeile | Verkettbarkeit im Log | **trennen** | S | — |
| **F10** | Log-Retention nirgends gesetzt | die vier `???` aus L2 | **kürzer** | XS | — |
| **F11** | `users.first_name/last_name` | nur für zwei Initialen | **weglassen** | S | — |
| **F12** | `transactions.pdf_sha256` | Abgleich gegen ein bekanntes PDF möglich | **trennen** (HMAC) | S | — |
| **F13** | PR-Review unter Consumer Terms | Repo-Inhalt inkl. echter Fixture-Struktur | **trennen** | XS | — |
| **F14** | JWT-Laufzeit 24 h | langes Fenster für ein gestohlenes Cookie | **kürzer** | XS (Sliding: M) | [#289](https://github.com/dfme/budget-buddy/issues/289) |

Reihenfolge, wenn nur wenig umgesetzt wird: **F2 → F1 → F8 → F13 → F10 → F14**. F2 zuerst, weil
ohne Löschpfad jede andere Frist auf dem Papier bleibt; F8, F13, F10 und F14 sind Einzeiler.

**Ticketiert sind bisher zwei Fundstellen** — F2 als
[#290 `[BE-AUTH-14]` Konto löschen: Endpoint auf /api/users/me](https://github.com/dfme/budget-buddy/issues/290)
und F14 als
[#289 `[BE-AUTH-13]` JWT-Laufzeit von 24 h verkürzen](https://github.com/dfme/budget-buddy/issues/289).
Beide Issues tragen die Optionen aus den Abschnitten unten als Acceptance Criteria; die Wahl
zwischen ihnen ist dort jeweils das erste Kriterium und bleibt beim Team. Die übrigen zwölf
Fundstellen sind bewusst noch nicht ticketiert — was davon umgesetzt wird, ist offen.

---

## F1 — `category_lookup` ist global, unmaskiert und überlebt alles

**Code:** [TransactionCategoryService.java:57](../../backend/src/main/java/com/budgetbuddy/transaction/TransactionCategoryService.java#L57)
→ [CategoryLearningService.java:36-51](../../backend/src/main/java/com/budgetbuddy/categorization/CategoryLearningService.java#L36-L51)
→ [V04__create_category_lookup_table.sql](../../backend/src/main/resources/db/migration/V04__create_category_lookup_table.sql)

Bei jeder manuellen Korrektur wandert `transaction.getBuchungstext()` **roh** als Primärschlüssel in
eine Tabelle, deren `CREATE TABLE` genau zwei Spalten hat: `empfaenger_pattern` und `category`.
Kein `user_id`, keine Fremdschlüssel, kein Zeitstempel. Der Text eines Nutzers wirkt damit auf die
Kategorisierung aller, und der Löschpfad kennt die Tabelle nicht.

**Vorschlag — trennen:** `user_id` als Spalte, PK auf `(user_id, empfaenger_pattern)`, Seeds mit
`user_id IS NULL` als globale Basis. Matching sucht erst eigene, dann globale Patterns; ein
`CategoryLookupCleanupPort` hängt sich in `deleteUser`. Preis: der Lerneffekt wird pro Nutzer neu
aufgebaut statt geteilt — bei ~70–80 % Trefferquote aus den Seeds ein kleiner Preis.

**Alternative (weglassen), falls der geteilte Lerneffekt gewollt bleibt:** nur die **maskierte**
Fassung lernen, also `PromptSanitizer.sanitize(buchungstext)` vor `learn(...)`. Löst das
Mandantenproblem aber nur halb: die Zeile bleibt global und übersteht die Löschung.

## F2 — «bis Kontolöschung» ist derzeit keine Frist

**Code:** [UserService.java:184-190](../../backend/src/main/java/com/budgetbuddy/auth/UserService.java#L184-L190) ·
[UserController.java:30-97](../../backend/src/main/java/com/budgetbuddy/auth/UserController.java#L30-L97)

`deleteUser` räumt sauber und ist getestet (`UserDeletionIntegrationTest`, `UserServiceTest`) —
aber **niemand ruft es auf**. `grep -rn 'DeleteMapping' backend/src/main/java` findet genau einen
Treffer, und der gehört zu `FixedCostController`. Kein Endpoint auf `/api/users/me`, keine
Aktion im Frontend. US-02 ist offen; das zugehörige DB-07 (#142) ist geschlossen.

**Damit ist die Aufbewahrungsangabe in L1 und L2 zu optimistisch:** «bis Kontolöschung» liest sich
wie eine Bedingung, die der Nutzer auslösen kann. Real ist die Aufbewahrung für *jede* Zeile in
`users`, `transactions`, `import_jobs`, `fixed_costs`, `notifications` **unbegrenzt**.

**Vorschlag — kürzer:** `DELETE /api/users/me` mit Passwortbestätigung auf das vorhandene
`deleteUser` legen, plus Aktion in den Einstellungen. Der teure Teil existiert bereits; es fehlt
die Fassade. Vor F3 umzusetzen, sonst regelt man Fristen für Daten, die man ohnehin nicht loswird.

**Umsetzung:** ticketiert als
[#290 — `[BE-AUTH-14]` Konto löschen: Endpoint auf /api/users/me](https://github.com/dfme/budget-buddy/issues/290)
(Label `us-02`). Der Task liefert bewusst nur die API; die Aktion in den Einstellungen wäre
`FE-SET-05`. Zwei Lücken bleiben ausdrücklich ausserhalb und sind im Issue benannt, damit US-02
nicht vorschnell als erfüllt gilt: **F1** (`category_lookup` überlebt die Löschung) und **F5**
(`recurring_expenses` hängt nicht in der Kette). US-02 AC 3 verlangt «alle personenbezogenen
Daten» — mit dem Endpoint allein ist das nicht abhakbar.

## F3 — `transactions` kennt keine Aufbewahrungsfrist

**Code:** [Transaction.java:29-70](../../backend/src/main/java/com/budgetbuddy/transaction/Transaction.java#L29-L70) ·
[V02](../../backend/src/main/resources/db/migration/V02__create_transactions_table.sql),
[V06](../../backend/src/main/resources/db/migration/V06__add_buchungsdetails_to_transactions.sql)

`buchungstext` und `buchungsdetails` liegen unmaskiert in der DB — bei `buchungsdetails` sind das
bis zu drei Zeilen à 40 Zeichen mit Gegenpartei und Verwendungszweck. Richtig so, der Nutzer will
sie in der Liste lesen. Nur: gelesen werden sie für Safe-to-Spend über wenige Monate, liegen bleiben
sie für immer.

**Vorschlag — kürzer:** konfigurierbare Frist (`budgetbuddy.retention.transactions=24m`), ein
Scheduled-Job löscht Ältere. Safe-to-Spend und die Monatsübersicht rechnen ohnehin monatsweise, und der
Einkommensvorschlag blickt selbst nur `LOOKBACK_MONTHS = 12` zurück und verlangt Gutschriften in
`MIN_DISTINCT_MONTHS = 2` verschiedenen Monaten ([IncomeSuggestionService.java:83-91](../../backend/src/main/java/com/budgetbuddy/transaction/IncomeSuggestionService.java#L83-L91)).
24 Monate sind also doppelt so viel, wie irgendeine Berechnung im Projekt anfasst — grosszügig und
trotzdem eine Frist.

**Zusatz (weglassen), falls das zu viel ist:** `buchungsdetails` nach der Kategorisierung leeren und
nur `buchungstext` behalten. Kostet die Nachvollziehbarkeit in der Transaktionsliste — vermutlich
zu teuer, deshalb nur als Notnagel notiert.

## F4 — `import_jobs` bleiben liegen, auch wenn sie fertig sind

**Code:** [V05:12-31](../../backend/src/main/resources/db/migration/V05__create_import_jobs_table.sql#L12-L31) ·
[StaleImportJobCleaner.java:164-186](../../backend/src/main/java/com/budgetbuddy/transaction/StaleImportJobCleaner.java#L164-L186)

Der Cleaner läuft alle 6 h, setzt aber nur verwaiste `RUNNING`-Jobs auf `FAILED` — er löscht keine
Zeile. Ein `DONE`-Job von vor einem Jahr steht noch da, mit `user_id` und `pdf_sha256`.

**Vorschlag — kürzer:** im selben Scheduled-Lauf `DONE`/`FAILED`-Jobs älter als 30 Tage löschen.
Der Duplikatcheck bleibt intakt: er fragt `transactions.pdf_sha256` ab und benutzt `import_jobs` nur
für das Fenster *während* eines laufenden Imports ([PdfImportService.java:184-205](../../backend/src/main/java/com/budgetbuddy/transaction/PdfImportService.java#L184-L205)).
Billigster echter Gewinn in dieser Liste.

## F5 — `recurring_expenses` hängt am User, aber nicht am Löschpfad

**Code:** [V11](../../backend/src/main/resources/db/migration/V11__create_recurring_expenses_table.sql) ·
[TransactionCleanupService.java:26-29](../../backend/src/main/java/com/budgetbuddy/transaction/TransactionCleanupService.java#L26-L29)

Die Tabelle trägt `user_id`, `payee_key` (normalisierter Empfängername) und `amount`, hat aber
weder Entity noch Service — sie ist heute folgenlos. Der Migrationskommentar hält die Verpflichtung
als AC an BE-REC-01 (#253) fest.

**Vorschlag — trennen:** so lassen, aber die AC ernst nehmen: `RecurringExpenseCleanupPort` im
selben PR wie die erste schreibende Zeile, nicht danach. Solange nichts schreibt, ist nichts kaputt;
ab dem ersten Schreibzugriff wäre es eine zweite Waise neben F1.

## F6 — Was der Sanitizer nachweislich stehen lässt

**Code:** [PromptSanitizer.java:25-33](../../backend/src/main/java/com/budgetbuddy/categorization/PromptSanitizer.java#L25-L33) (die Grenzen, dokumentiert),
`sanitize` [:173](../../backend/src/main/java/com/budgetbuddy/categorization/PromptSanitizer.java#L173),
angewendet in [ClaudeCategorizationService.java:406](../../backend/src/main/java/com/budgetbuddy/categorization/ClaudeCategorizationService.java#L406)

Zwei bekannte Reste gehen an Anthropic hinaus: der nachgestellte Vorname in einer frei getippten
Zweckzeile (`LASTSCHRIFT <NAME> SACKGELD LEA`) und die Telefonnummer eines Händlers. Beides ist als
BE-CAT-08 (#233) offen und regelbasiert schlecht lösbar.

**Vorschlag — weglassen:** Telefonnummern sind es nicht: ein Muster für CH-Nummern
(`0XX XXX XX XX`, `+41…`) ist eng genug, um keinen Händlernamen zu verstümmeln. Der Vorname bleibt
offen — dafür ist F7 der wirksamere Hebel.

## F7 — Der Prompt trägt mehr, als die Kategorisierung braucht

**Code:** [ClaudeCategorizationService.java:406-414](../../backend/src/main/java/com/budgetbuddy/categorization/ClaudeCategorizationService.java#L406-L414)
(nummerierte Liste aus `ParsedTransaction.fullText()`)

`fullText()` hängt die Detailzeilen an den Buchungstext. Für die Zuordnung genügt in aller Regel der
Händlertoken — und genau in den Detailzeilen sitzt der frei getippte Zweck, also der Rest aus F6.

**Vorschlag — weglassen, aber gemessen:** eine Variante bauen, die nur den maskierten
`buchungstext` (plus die *erste* Detailzeile) sendet, und die Trefferquote gegen den
Fixture-Korpus vergleichen. Fällt sie nicht messbar, ist das die sauberste Minimierung im ganzen
Dokument: der riskanteste Textteil verlässt das System dann gar nicht mehr. Fällt sie, ist die
Antwort dokumentiert statt vermutet.

## F8 — `notifications.message` ist heute noch leer von Personenbezug

**Code:** [NotificationService.java:42-51](../../backend/src/main/java/com/budgetbuddy/notification/NotificationService.java#L42-L51) ·
[V10:13](../../backend/src/main/resources/db/migration/V10__create_notifications_table.sql#L13)

`grep -rn 'NotificationPort' --include='*.java'` ausserhalb des Moduls liefert nichts: **es gibt
aktuell keinen Produzenten.** L1 nennt «kann künftig Händlernamen tragen» — genau das ist der
Punkt, und es ist noch nicht passiert.

**Vorschlag — weglassen, jetzt:** in `NotificationPort` festschreiben, dass `message` nur generisch
formuliert wird und der Bezug über `reference_id` läuft («Ein wiederkehrender Betrag wurde erkannt»
statt «Abo bei Netflix»). Ein Javadoc-Satz plus ein Test kosten Minuten — nachträglich ist es eine
Datenmigration über gewachsene Freitexte.

## F9 — `user_id` steht im Klartext an jeder Log-Zeile

**Code:** [LogContext.java:52-54](../../backend/src/main/java/com/budgetbuddy/config/LogContext.java#L52-L54) ·
[JwtCookieAuthenticationFilter.java:69](../../backend/src/main/java/com/budgetbuddy/auth/JwtCookieAuthenticationFilter.java#L69) ·
`logging.pattern.level` in [application.properties](../../backend/src/main/resources/application.properties)

Der Transaktionstext in den Logs ist redigiert und gesalzen gehasht ([LogRedaction.java:37-62](../../backend/src/main/java/com/budgetbuddy/categorization/LogRedaction.java#L37-L62))
— vorbildlich. Die User-ID daneben ist es nicht: sie verkettet alle Zeilen einer Person über die
gesamte Log-Retention und ist mit `users.id` direkt verknüpfbar.

**Vorschlag — trennen:** statt der ID ein Pseudonym loggen, `HMAC(prozess-salt, userId)` auf 8 Hex
gekürzt — dieselbe Technik, die `LogRedaction` bereits für den Transaktionstext verwendet. Innerhalb
eines Prozesslebens bleibt jede Support-Frage beantwortbar («welche Zeilen gehören zusammen»),
über den Neustart hinaus ist die Verkettung weg.

## F10 — Die vier `???` der Karte

**Code:** keiner — genau das ist der Befund. Die Log-Retention ist eine Render-Einstellung, keine
Anwendungsentscheidung.

**Vorschlag — kürzer:** im Render-Dashboard eine Frist setzen (z. B. 30 Tage), den Wert in
`docs/TECH-STACK.md` festhalten und die vier `???` in der Karte ersetzen. Nichts zu programmieren,
und die Karte verliert ihre einzige offene Spalte.

## F11 — Vor- und Nachname für zwei Buchstaben

**Code:** [User.java:37-38](../../backend/src/main/java/com/budgetbuddy/auth/User.java#L37-L38) ·
[RegisterRequest.java:24](../../backend/src/main/java/com/budgetbuddy/auth/dto/RegisterRequest.java#L24) ·
[shell.ts:65-82](../../frontend/src/app/core/layout/shell.ts#L65-L82)

Beide Felder sind optional (bewusst, wegen der Onboarding-Hürde) und werden im Produktivcode an
genau einer Stelle verwendet: für den vollen Namen und die Initialen im Avatar.

**Vorschlag — weglassen:** ersatzlos streichen und die Initialen aus der E-Mail ableiten. Preis:
weniger Personalisierung. Das ist eine Produktentscheidung, keine technische — deshalb hier nur als
Option, mit dem Hinweis, dass es die einzige Datenart im Inventar ist, die *nichts* zum Core Value
beiträgt.

## F12 — `pdf_sha256` ist ein ungesalzener Hash

**Code:** [PdfImportService.java:103](../../backend/src/main/java/com/budgetbuddy/transaction/PdfImportService.java#L103),
[:205](../../backend/src/main/java/com/budgetbuddy/transaction/PdfImportService.java#L205)

Wer eine Kopie eines PDFs besitzt, kann per Hash-Vergleich feststellen, ob genau dieser Auszug
importiert wurde — ohne die Transaktionen zu sehen. Ein schmaler Angriff (er setzt DB-Lesezugriff
*und* die Datei voraus), aber gratis zu schliessen.

**Vorschlag — trennen:** `HMAC-SHA256` mit einem Server-Secret statt des rohen Digests. Der
Duplikatcheck arbeitet unverändert, weil er nur Gleichheit prüft. Nebenwirkung: bestehende
Hashes werden ungültig, d. h. eine erneute Migration oder ein Import gilt einmalig als «neu».

## F13 — Das automatische PR-Review läuft unter Consumer Terms

**Code:** [claude-pr-review.yml:51-55](../../.github/workflows/claude-pr-review.yml#L51-L55) ·
Fixtures unter `backend/src/test/resources/pdf/` (7 generiert, 1 echter Auszug in anonymisierter
Fassung, [SwissBankStatementParserFixtureTest.java:22-25](../../backend/src/test/java/com/budgetbuddy/transaction/SwissBankStatementParserFixtureTest.java#L22-L25))

Der Workflow bevorzugt `CLAUDE_CODE_OAUTH_TOKEN`, und `gh secret list` zeigt: genau dieses Secret
ist gesetzt. Jedes Review läuft damit über ein persönliches Pro/Max-Konto — nicht unter den
Commercial Terms des Backends. Details in [L2 → «Was jenseits der Systemgrenze gilt»](08_01_prompt_lab1_l2_datenkarte.md).

**Vorschlag — trennen:** den Workflow auf den `ANTHROPIC_API_KEY` einer Commercial Organization
umstellen. Der Zweig existiert bereits im YAML; es genügt, das OAuth-Secret zu entfernen. Damit
gilt für CI dieselbe Zusicherung wie für die Produktion.

## F14 — 24 Stunden JWT

**Code:** `app.jwt.expiration=24h` in [application.properties](../../backend/src/main/resources/application.properties) ·
[JwtService.java](../../backend/src/main/java/com/budgetbuddy/auth/JwtService.java)

Das Cookie ist `httpOnly`, `Secure` in Prod, `SameSite=Strict`, und `token_version` entwertet es bei
Passwortänderung — sauber gebaut. 24 h sind trotzdem ein langes Fenster.

**Vorschlag — kürzer, Variante A (XS):** `app.jwt.expiration=8h`. Eine Zeile, und zwar wirklich nur
eine: `JwtService` liest den Wert im Konstruktor ([:32](../../backend/src/main/java/com/budgetbuddy/auth/JwtService.java#L32)),
und `JwtCookieFactory` leitet die Cookie-`maxAge` aus derselben Property ab
([:28](../../backend/src/main/java/com/budgetbuddy/auth/JwtCookieFactory.java#L28)) — Token und Cookie
können nicht auseinanderlaufen. Kein Test hängt daran: `JwtServiceTest` konstruiert `JwtProperties`
mit eigenen Dauern. Und der Ablauf ist abgefedert, weil
[auth-error.interceptor.ts:39](../../frontend/src/app/core/interceptors/auth-error.interceptor.ts#L39)
jeden 401 auf einem geschützten Call auf den Login umleitet. Preis: einmal pro Arbeitstag neu
einloggen.

**Variante B (M):** Sliding Expiration — das Token bei Aktivität verlängern, statt es hart ablaufen
zu lassen. Das ist neuer Code (Erneuerungspfad, Entscheidung wann verlängert wird, Tests) und der
Grund, warum diese Zeile ursprünglich mit M stand. Variante A ist davon unabhängig und schon für
sich der ganze Sicherheitsgewinn; B kauft nur die UX zurück.

**Umsetzung:** ticketiert als
[#289 — `[BE-AUTH-13]` JWT-Laufzeit von 24 h verkürzen](https://github.com/dfme/budget-buddy/issues/289)
(Label `us-01`). Beide Varianten stehen gleichwertig im Issue, die Wahl ist das erste Acceptance
Criterion; die vier zusätzlichen Kriterien für Variante B (Erneuerungsschwelle, absolute
Obergrenze, ERROR-Dispatch, Zusammenspiel mit `token_version`) sind als eigener Block markiert.
Scope-Hinweis im Issue: «Automatischer Session-Ablauf nach Inaktivität» steht in US-01 unter
*Post-MVP* — Variante B zieht diesen Punkt vor.

---

## Was ich ausdrücklich **nicht** vorschlage

Diese Stellen sind geprüft und in Ordnung — sie hier zu nennen, verhindert, dass sie in einer
späteren Runde nochmals aufgemacht werden:

- **Das PDF wird nie persistiert**, nur sein Hash ([PdfImportService.java:29-31](../../backend/src/main/java/com/budgetbuddy/transaction/PdfImportService.java#L29-L31)).
  Der sensibelste Gegenstand im ganzen Flow lebt nur im RAM.
- **Der Sanitizer sitzt an der einzigen Serialisierungsstelle**, nicht beim Aufrufer — ein neuer
  Aufrufpfad kann ihn strukturell nicht vergessen.
- **Passwörter** als BCrypt-Hash, beide Secrets ausschliesslich aus der Umgebung, `JWT_SECRET` mit
  Fail-fast beim Start.
- **Kein Tracker im Frontend**, `localStorage` trägt nur die Theme-Wahl.
- **Monatseinkommen und Beträge** sind der Core Value — Minimierung wäre hier Selbstabschaffung.
- **Alle Datenhalter in der EU** (Render und Neon, Frankfurt).

## Zwei Korrekturen an L1/L2

1. **«bis Kontolöschung» ist heute unerreichbar** (F2). Beide Dokumente führen es als
   Aufbewahrungsbedingung; ein Auslöser existiert im Produktivcode nicht.
2. **`/actuator/info` ist öffentlich, nicht «authentifizierte Nutzer»** wie in L1 §7.4:
   [SecurityConfig.java:73](../../backend/src/main/java/com/budgetbuddy/config/SecurityConfig.java#L73)
   führt den Pfad in `PUBLIC_PATHS`. Inhaltlich harmlos — der Endpoint liefert den deployten
   Commit-SHA, keine Nutzerdaten —, aber die Aussage in L1 stimmt so nicht.
