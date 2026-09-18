# [BE-AUTH-12] Obergrenzen für String-Felder in den Auth-DTOs fehlen

- **Issue:** [#231](https://github.com/dfme/budget-buddy/issues/231)
- **Task-ID:** `BE-AUTH-12`
- **Branch:** `feature/BE-AUTH-12-auth-field-limits`
- **Story:** — (Querschnitt: berührt US-01 und US-14, keine der beiden fordert die Begrenzung explizit)
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-16

## Entscheide

- **Grenzwerte:** `email` 254 Zeichen (RFC 5321, wie im Issue vorgeschlagen), `firstName`/`lastName`
  50 Zeichen. Kein bestehender `@Size(max = …)` im Backend, an dem sich der Namenswert hätte
  orientieren können; `FixedCostService.MAX_BEZEICHNUNG_LENGTH` (100, Service-Ebene, kein
  Bean-Validation-Pendant) war zu grosszügig für Personennamen. 50 nach Rücksprache mit dem User.
- **DB-Spalten bleiben `TEXT`.** Postgres' `TEXT` ist storage-identisch mit `VARCHAR` ohne Länge —
  kein Default, keine implizite Grenze, dieselbe Begründung wie in `V02__create_transactions_table.sql`
  und `V11__create_recurring_expenses_table.sql`. Die neue Obergrenze ist damit ausschliesslich eine
  Anwendungsebene-Regel; keine Flyway-Migration nötig. Das erfüllt AC 6 mit einem dokumentierten
  «nein, Validierung genügt».
- **Ein Ort für die Grenzwerte:** neue Klasse `AuthFieldLimits` in `auth.dto`, analog zum Muster von
  `MaxBcryptBytes`/`MaxBcryptBytesValidator` (BE-AUTH-10, #200) — dort aber als Custom-Constraint,
  weil die bcrypt-Grenze Bytes statt Zeichen zählt. Hier genügt das eingebaute `@Size(max = …)`, da
  Zeichenzahl laut AC ausreicht.
- **Kein Scope-Zuwachs:** `LoginRequest.email` bleibt bewusst ohne `@Size` — das Feld hat schon eine
  dokumentierte Design-Entscheidung (keine Format-/Längenprüfung, um 400 vs. 401 nicht als
  User-Enumeration-Kanal zu nutzen), und das Issue nennt nur `RegisterRequest`. Breite Grep-Suche
  (`@Size`, `Obergrenze`) über `backend/src/main/java` ergab keine weiteren unbegrenzten Felder, die
  die ACs nicht schon abdecken.

## Betroffene und neue Dateien

- NEW `backend/src/main/java/com/budgetbuddy/auth/dto/AuthFieldLimits.java`
- MODIFY `backend/src/main/java/com/budgetbuddy/auth/dto/RegisterRequest.java`
- MODIFY `backend/src/test/java/com/budgetbuddy/auth/AuthControllerTest.java`
- MODIFY `backend/src/test/java/com/budgetbuddy/auth/AuthOpenApiTest.java`

## Implementierungsschritte

1. `AuthFieldLimits` anlegen: `public static final int EMAIL_MAX_LENGTH = 254` und
   `NAME_MAX_LENGTH = 50`, mit Javadoc, das die TEXT-vs-VARCHAR-Entscheidung festhält.
2. `RegisterRequest.email` um `@Size(max = AuthFieldLimits.EMAIL_MAX_LENGTH, message = …)` ergänzen,
   `firstName`/`lastName` um `@Size(max = AuthFieldLimits.NAME_MAX_LENGTH, message = …)` — jede
   Meldung nennt die Regel, nie den eingegebenen Wert.
3. `AuthControllerTest`: je ein Test für Ablehnung an der Grenze (255/51 Zeichen) und Annahme knapp
   darunter (254/50 Zeichen) für `email`, `firstName`, `lastName`; Non-Echo-Check für die
   Ablehnungsfälle (Muster aus den bestehenden Bcrypt-Tests).
4. `AuthOpenApiTest`: `maxLength` im `RegisterRequest`-Schema für `email`/`firstName`/`lastName`
   belegen (DoD-Punkt «maxLength erscheint im Schema»).

## Test-Strategie

Ausschliesslich Integrationstests via `MockMvc` gegen den echten `/api/auth/register`-Endpoint
(bestehendes Muster in `AuthControllerTest`) plus eine Erweiterung von `AuthOpenApiTest` für die
Swagger-Schema-Prüfung. Keine dedizierte Unit-Test-Klasse: `@Size` ist eine Standard-Bean-Validation-
Constraint ohne eigene Logik, anders als `MaxBcryptBytesValidator`.

## Acceptance Criteria (aus dem Issue)

- [ ] `RegisterRequest.firstName` und `RegisterRequest.lastName` haben eine Obergrenze; ein
      längerer Wert wird mit `400` abgelehnt, nicht gespeichert
- [ ] `RegisterRequest.email` hat eine Obergrenze (254 Zeichen, RFC 5321)
- [ ] Die Fehlermeldung nennt die Regel, nicht die Eingabe des Users — und niemals das Passwort
- [ ] Die Grenzen sind an einer Stelle definiert, nicht als Magic Number je DTO dupliziert
- [ ] Je ein Test belegt die Ablehnung an der Grenze und die Annahme knapp darunter
- [ ] Geprüft und entschieden, ob die DB-Spalten von `TEXT` auf `VARCHAR(n)` wechseln sollen —
      Entscheid: nein, `TEXT` bleibt, Validierung auf Anwendungsebene genügt (siehe Entscheide oben)
