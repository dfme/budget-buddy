# US-14: Passwort, Einkommen und Erscheinungsbild in Einstellungen anpassen

**Persona:** Marc  
**MoSCoW:** Should  
**Story:** Als Marc möchte ich mein Passwort, mein Einkommen und das Erscheinungsbild (hell/dunkel) in den Einstellungen anpassen können, damit ich mein Konto aktuell halte und die App so aussieht, wie ich sie gerne nutze.

---

## Acceptance Criteria

**Given** ich eingeloggt bin, **When** ich unter "Einstellungen > Passwort ändern" das aktuelle und ein neues Passwort (min. 8 Zeichen) eingebe und bestätige, **Then** wird das neue Passwort gespeichert und ich erhalte eine In-App-Bestätigung.

**Given** das eingegebene aktuelle Passwort ist falsch, **When** ich speichere, **Then** wird die Änderung abgelehnt mit "Aktuelles Passwort falsch".

**Given** ich mein Monatseinkommen in den Einstellungen ändere, **When** ich speichere, **Then** wird der Safe-to-Spend-Betrag auf dem Dashboard sofort mit dem neuen Wert neu berechnet.

**Given** kein Monatseinkommen manuell erfasst ist, **When** ich die Einstellungen öffne, **Then** wird das Einkommensfeld als optional gekennzeichnet; wurde ein Einkommen automatisch geschätzt (→ US-06), erscheint der Schätzwert als Vorschlag — das Feld kann leer bleiben, wenn die automatische Schätzung verwendet werden soll.

**Given** ich bin in den Einstellungen, **When** ich unter "Erscheinungsbild" zwischen "Hell", "Dunkel" und "System" wähle, **Then** stellt sich die gesamte App sofort und ohne Reload auf das gewählte Theme um — inklusive Diagrammen, die ihre Farben neu aufbauen.

**Given** ich habe die App noch nie umgestellt, **When** ich sie zum ersten Mal öffne, **Then** ist "System" aktiv: die App startet in der Einstellung meines Betriebssystems (hell oder dunkel) und folgt einem späteren Wechsel dort automatisch, solange ich "System" ausgewählt lasse.

**Given** ich habe explizit "Hell" oder "Dunkel" gewählt, **When** ich die App im selben Browser später erneut öffne, **Then** ist meine Wahl weiterhin aktiv und wird bereits beim ersten Bildaufbau angewendet — das falsche Theme darf nicht kurz aufblitzen.

**Given** ich öffne die App auf einem anderen Gerät oder in einem anderen Browser, **When** ich mich dort einlogge, **Then** gilt dort wieder "System" — die Theme-Wahl wird bewusst lokal im Browser gespeichert und nicht mit dem Konto synchronisiert.

---

## Hinweise

**Scope-Entscheid Theme-Präferenz:** Die Wahl wird *client-only* in `localStorage` gehalten, mit `prefers-color-scheme` als Default. Damit bleibt die Erweiterung ein reines Frontend-Thema — kein Feld an `users`, keine Flyway-Migration, kein zusätzlicher Endpoint. Die geräteübergreifende Variante (Präferenz im Nutzerprofil) ist die bewusst verworfene Alternative; die Herleitung steht in [design/README.md](../../design/README.md) unter „Nutzerseitige Theme-Präferenz".

**Voraussetzung war erfüllt:** Beide Themes liegen vollständig als CSS Custom Properties in [frontend/src/styles.scss](../../frontend/src/styles.scss) und werden über `data-theme` auf `<html>` umgeschaltet (FE-UI-02, ADR-11). Zu den Nutzern gebracht hat die Umschaltung FE-SET-04: [frontend/src/app/core/theme/theme.ts](../../frontend/src/app/core/theme/theme.ts) hält die Wahl in `localStorage` und schreibt sie aufs Attribut, das Inline-Script in [frontend/src/index.html](../../frontend/src/index.html) wendet sie vor dem ersten Bildaufbau an.
