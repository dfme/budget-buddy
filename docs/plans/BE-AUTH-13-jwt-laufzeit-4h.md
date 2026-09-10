# [BE-AUTH-13] JWT-Laufzeit von 24 h verkürzen

- **Issue:** [#289](https://github.com/dfme/budget-buddy/issues/289)
- **Task-ID:** `BE-AUTH-13`
- **Branch:** `feature/BE-AUTH-13-jwt-laufzeit-4h`
- **Story:** US-01 — Konto und Login
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-09

## Entscheid: Option A mit 4 h

Das Issue stellte Option A (feste, kürzere Laufzeit) und Option B (Sliding Expiration mit
absoluter Obergrenze) zur Wahl und legte die Entscheidung ausdrücklich in diesen Task.

Gewählt ist **Option A mit `app.jwt.expiration=4h`**.

Begründung:

- Option A liefert den Sicherheitsgewinn vollständig und für sich allein. Das Cookie wird nach
  dem Login von nichts mehr erneuert — die Laufzeit *ist* damit exakt das Fenster, in dem ein
  abhandengekommenes Cookie nutzbar bleibt. Von 24 h auf 4 h ist das eine Sechstelung dieses
  Fensters.
- Option B holt „automatischer Session-Ablauf nach Inaktivität" vor, das in
  [US-01](../requirements/US-01-konto-login.md) unter *Post-MVP (nicht im Scope dieses Semesters)*
  steht. Sie kauft ausserdem nur UX zurück, keinen zusätzlichen Sicherheitsgewinn — naiv gebaut
  (ohne absolute Obergrenze) wäre sie gegen genau das Szenario, um das es hier geht, sogar
  schwächer als ein festes Fenster.
- 4 h statt der im Issue vorgeschlagenen 8 h: ein halber Arbeitstag deckt einen
  zusammenhängenden Nutzungsblock ab (Kontoauszug hochladen, kategorisieren, Budget prüfen),
  ohne die Sitzung über eine Mittagspause oder einen Feierabend hinaus offen zu halten.

Option B bleibt jederzeit als eigener Task nachrüstbar; sie ist von A unabhängig.

## Delta gegenüber den Acceptance Criteria

Die ACs nennen als Fundstelle allein `application.properties`. Die breite Gegensuche
(`grep -rn "24h\|24 Stunden\|Duration.ofHours"`) fand drei weitere relevante Stellen. Das Delta
wurde dem Team vorgelegt; entschieden ist, alle drei im selben PR zu erledigen:

1. **`JwtProperties.java:29`** trägt `Duration.ofHours(24)` als hartkodierten Fallback. Die
   Aussage des Issues, es gebe „genau eine Stelle", trifft deshalb nicht zu: fehlt die Property,
   fällt die Anwendung still auf genau den Wert zurück, den dieser Task abschafft.
2. **`docs/adr/ADR-7-jwt-authentication.md:21`** nennt „24 Stunden Expiry" und wird durch den
   Diff dieses Tasks falsch.
3. **Kein Test bindet den konfigurierten Wert.** `JwtServiceTest.rejectsExpiredToken` deckt den
   Ablauf-*Mechanismus* ab (eigene Duration von −1 s), nicht die *Konfiguration*. Ein stilles
   Zurückdrehen der Property auf `24h` liefe heute grün durch. Das ist die Antwort auf die
   Prüffrage in AC 3.

Nicht betroffen: `JwtCookieFactoryTest.java:25` nutzt `Duration.ofHours(24)` als lokale
Konstante und prüft damit die *Spiegelung* von Token-Laufzeit auf Cookie-`maxAge`, nicht den
Produktionswert. Der Test bleibt korrekt; ihn auf den Prod-Wert festzunageln würde ihn ohne
Gewinn brüchig machen.

## Betroffene Dateien

### Geändert

| Datei | Änderung |
| ----- | -------- |
| `backend/src/main/resources/application.properties:173-174` | `24h` → `4h`; Kommentar nennt die Begründung, nicht nur das Duration-Format |
| `backend/src/main/java/com/budgetbuddy/auth/JwtProperties.java:26-30` | Fallback `ofHours(24)` → `ofHours(4)`, Javadoc dazu, warum der Fallback dem Property folgen muss |
| `docs/adr/ADR-7-jwt-authentication.md:21` | „24 Stunden Expiry" → 4 h, mit Verweis auf BE-AUTH-13 |

### Neu

| Datei | Zweck |
| ----- | ----- |
| `backend/src/test/java/com/budgetbuddy/auth/JwtExpirationConfigTest.java` | Bindet den ausgelieferten Konfigurationswert und den Record-Fallback |

## Implementierungsschritte

1. `app.jwt.expiration=4h` in `application.properties` setzen. Der Kommentar begründet den Wert
   (Laufzeit = Missbrauchsfenster, weil nichts den Token erneuert) und verweist darauf, dass
   `JwtProperties` denselben Wert als Fallback trägt.
2. Fallback in `JwtProperties` auf `Duration.ofHours(4)` nachziehen.
3. ADR-7 korrigieren.
4. `JwtExpirationConfigTest` schreiben — `ApplicationContextRunner` mit
   `ConfigDataApplicationContextInitializer` (Muster vorhanden in `MdcTaskDecoratorTest.java:68`).
   `JWT_SECRET` liefert Surefire bereits (`pom.xml:159`), die `@Validated`-Bindung von
   `JwtProperties` läuft damit durch.
5. `mvn test` und `ng build` ausführen.

## Test-Strategie

| Test | Art | Deckt ab |
| ---- | --- | -------- |
| `JwtExpirationConfigTest.configuredExpirationIsFourHours` (neu) | Unit | Der ausgelieferte Wert ist 4 h — schliesst die Lücke aus AC 3 |
| `JwtExpirationConfigTest.fallbackMatchesConfiguredExpiration` (neu) | Unit | `new JwtProperties(secret, null).expiration()` ist 4 h — der Fallback kann nicht vom Property abdriften |
| `JwtServiceTest.rejectsExpiredToken` (bestehend, unverändert) | Unit | Ablauf-Mechanismus; konfigurationsunabhängig |
| `JwtCookieFactoryTest` (bestehend, unverändert) | Unit | Cookie-`maxAge` spiegelt die Token-Laufzeit |
| `auth-error.interceptor.spec.ts:33` (bestehend, unverändert) | Unit (FE) | 401 auf geschütztem Call → Redirect auf `/login` |

Kein Frontend-Code ändert sich: AC 4 wird durch Nachweis erfüllt, nicht durch neuen Code.

## Acceptance Criteria aus dem Issue

- [ ] Entscheid zwischen Option A und B ist im Issue festgehalten (Kommentar genügt)
- [ ] `app.jwt.expiration` steht auf dem beschlossenen Wert; der Kommentar in
      `application.properties` nennt die Begründung, nicht nur die Zahl
- [ ] Ein Test belegt, dass ein abgelaufenes Token abgewiesen wird (vorhanden:
      `JwtServiceTest.rejectsExpiredToken` — prüfen, ob er die neue Konfiguration mit abdeckt)
- [ ] Frontend-Verhalten beim Ablauf ist verifiziert: 401 auf einem geschützten Call führt zum
      Redirect auf den Login, kein toter Screen

Die vier Zusatz-ACs des Issues gelten nur für Option B und entfallen mit dem Entscheid für A.
