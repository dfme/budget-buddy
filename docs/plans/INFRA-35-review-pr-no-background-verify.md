# [INFRA-35] Automatisches PR-Review verstummt bei Hintergrund-Verifikation im nicht-interaktiven Lauf

- **Issue:** [#224](https://github.com/dfme/budget-buddy/issues/224)
- **Task-ID:** `INFRA-35`
- **Branch:** `fix/INFRA-35-review-pr-no-background-verify`
- **Story:** — (kein us-*-Label)
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-29

## Kontext

Der automatische Review-Agent (INFRA-31, #215) lief bei PR #223 (Lauf 33239595160) im
nicht-interaktiven GitHub-Actions-Kontext still ins Leere: der Agent startete den
Verifikationsbefehl (`./mvnw package`) im Hintergrund und beendete die Session danach, ohne auf
das Ergebnis zu warten. In der interaktiven CLI holt eine spätere Gesprächsrunde die
Hintergrund-Benachrichtigung ab — die GitHub Action führt aber einen einzelnen, einmaligen
SDK-Query aus. Ohne Folge-Runde geht das Ergebnis verloren; für den Commit `821ec46` existiert
kein Review-Objekt, obwohl der SDK-Query mit `is_error: false` endete. Der Fortschritts-Kommentar
der Action hängt trotzdem unbedingt ein "Claude finished …"-Banner an, wodurch der stille
Fehlschlag wie ein Erfolg aussieht.

## Entscheidungen

- Die Ausnahme wird als expliziter Text in `SKILL.md` Schritt 4 verankert (analog zum
  bestehenden Muster in Schritt 7), nicht nur als Kommentar im Workflow-Prompt — der Skill ist
  die Quelle, die auch der lokale interaktive Lauf liest, und dort muss die Regel unmissverständlich
  auf `GITHUB_ACTIONS` bedingt sein: lokal bleibt Hintergrundausführung sinnvoll (lange Läufe,
  Rückmeldung in einer späteren Runde), im Actions-Kontext ist sie ein Datenverlust.
- Der Workflow bekommt zusätzlich einen Guard (Gürtel-zu-Hosenträger-Prinzip, wie bereits bei der
  Bestätigungsgate-Ausnahme in INFRA-34 praktiziert): ein Prompt-Text allein garantiert kein
  Verhalten. Der Guard prüft nach dem Claude-Action-Schritt über die REST-API, ob für den
  auslösenden Head-Commit tatsächlich ein Review-Objekt existiert, und lässt den Job sonst
  sichtbar fehlschlagen statt still grün durchzulaufen.
- Kein neuer Retry- oder Wiederholungsmechanismus: ein fehlgeschlagener automatischer Lauf ist
  nach CLAUDE.md-Konvention (Review-Konvention Punkt 1–3) ohnehin nur eine Ergänzung zum
  menschlichen Review — ein sichtbar roter Job genügt, damit das Team es bemerkt und bei Bedarf
  manuell reviewt.

## Betroffene Files

- `.claude/skills/review-pr/SKILL.md` — geändert (Schritt 4, "Harte Grenzen")
- `.github/workflows/claude-pr-review.yml` — geändert (neuer Guard-Schritt)

## Implementierungsschritte

1. `SKILL.md` Schritt 4: neuer Unterabschnitt "Ausnahme: nicht-interaktive Läufe — synchron statt
   im Hintergrund" nach dem bestehenden Verifikations-Codeblock. Begründung mit dem
   PR-223-Vorfall, Tabelle `GITHUB_ACTIONS` gesetzt/nicht gesetzt → Hintergrund erlaubt/verboten.
2. `SKILL.md` "Harte Grenzen": die Aussage "Das ist die einzige Grenze mit einer solchen
   Ausnahme" korrigieren — es existieren jetzt zwei `GITHUB_ACTIONS`-bedingte Ausnahmen (Schritt 7
   Bestätigungsgate, Schritt 4 synchrone Ausführung).
3. `claude-pr-review.yml`: neuer Schritt nach "Run /review-pr", der über
   `gh api repos/{repo}/pulls/{number}/reviews` prüft, ob ein Review mit
   `commit_id == github.event.pull_request.head.sha` existiert; fehlt es, `exit 1` mit
   `::error::`-Annotation.
4. Manuelle Verifikation: Test-PR öffnen, automatischen Lauf beobachten, bestätigen dass entweder
   kein Backgrounding mehr auftritt oder der Guard bei einem fehlenden Review zuverlässig
   anschlägt.

## Test-Strategie

Kein automatisiertes Testharness für Skill-Prompt-Text/YAML (siehe DoD im Issue). Verifikation
ausschliesslich manuell über einen realen Test-PR in CI. Zusätzlich `mvn package`/`ng build` zur
Kontrolle, dass die App selbst unberührt bleibt (keine App-Code-Änderung in diesem PR).

## Acceptance Criteria (aus Issue #224)

- [x] `SKILL.md` Schritt 4 verlangt im nicht-interaktiven Lauf synchrone Ausführung der
      Verifikationsbefehle statt Hintergrundausführung, mit Begründung. Lokaler, interaktiver Lauf
      bleibt unverändert.
- [x] Die Aussage "Das ist die einzige Grenze mit einer solchen Ausnahme" ist korrigiert.
- [x] `claude-pr-review.yml` prüft nach dem Claude-Action-Schritt, ob für den aktuellen Head-SHA
      ein Review-Objekt existiert; fehlt es, schlägt der Job sichtbar fehl.
- [x] Manuell verifiziert: Testlauf mit künstlich verlangsamtem Verifikationsschritt zeigt kein
      Backgrounding mehr im nicht-interaktiven Modus, oder der neue Guard schlägt zuverlässig an.
      Beide Zweige empirisch bestätigt an zwei Wegwerf-PRs: #226 (SKILL.md-Anweisung allein, ohne
      Workflow-Änderung) zeigte den Agenten 9 Minuten synchron durch eine künstliche 150s-Sleep
      warten und danach ein Review-Objekt für den korrekten Head-Commit erzeugen (Lauf
      33240773942); #225 (mit Workflow-Änderung) zeigte den neuen Guard zuverlässig anschlagen,
      weil `claude-code-action` PRs, die die Workflow-Datei selbst ändern, per eigener
      Validierung überspringt — ein anderer Auslöser als das Backgrounding, aber exakt das vom
      Guard erwartete Verhalten (kein Review → Job sichtbar rot). Beide PRs ohne Merge
      geschlossen, Branches gelöscht.

## Zusätzlicher Scope: veralteter `CHANGES_REQUESTED`-Review von `claude` bleibt nach Nachbesserung blockierend

> Während der Umsetzung an PR #212 beobachtet und auf ausdrücklichen Wunsch des Users in dieses
> Issue/diese PR statt in ein separates Ticket aufgenommen (Issue #224 entsprechend ergänzt,
> [Kommentar](https://github.com/dfme/budget-buddy/issues/224#issuecomment-5461139492)).

**Problem:** GitHub bildet den Review-Status pro Reviewer aus dessen letztem *formellen* Review
(`APPROVED` oder `CHANGES_REQUESTED`) — ein späterer `COMMENTED`-Review desselben Accounts
überschreibt das nicht. Da `review-pr` nie approven darf (Schritt 8 / "Harte Grenzen"), kann ein
Folgelauf, der keine blockierenden Punkte mehr findet, nur `COMMENTED` posten — der alte
`CHANGES_REQUESTED`-Status bleibt bestehen, die PR bleibt blockiert. Belegt an PR #212 (siehe
Issue #224): `claude` → `CHANGES_REQUESTED`, dann `claude` → `COMMENTED` an einem späteren Commit
ohne verbleibende Blocker, dann sogar `dfme` → `APPROVED` — `reviewDecision` blieb trotzdem
`CHANGES_REQUESTED`. Bestätigt am Ruleset: `dismiss_stale_reviews_on_push: false`.

**Drei Lösungsvarianten zur Wahl (siehe Issue #224 für Details):**

- **A — automatischer Self-Dismiss:** `review-pr` dismissed einen eigenen offenen
  `CHANGES_REQUESTED`-Review automatisch, sobald ein Folgelauf keine blockierenden Punkte mehr
  findet. Offen: ob der App-Token in der GitHub Action das API-seitig darf (Dismiss verlangt laut
  GitHub-Doku Admin-Rechte oder Eintrag in einer Dismiss-Liste) — nur durch einen echten Testlauf
  zu klären.
- **B — dokumentierter manueller Schritt:** Kein automatisches Eingreifen; `review-pr` weist im
  Review-Body/Fortschritts-Kommentar auf den veralteten Status hin und verlinkt den zu
  dismissenden Review.
- **C — Hybrid:** automatischer Dismiss-Versuch (A), mit Rückfall auf den Hinweis (B), falls die
  API-Berechtigung fehlt.

**Entscheidung:** Variante C (Hybrid) — automatischer Dismiss-Versuch mit Rückfall auf einen
PR-Kommentar, falls die Berechtigung fehlt. Robust unabhängig davon, ob der App-Token in der
GitHub Action das Dismiss-Recht hat.

### Implementierungsschritte (Zusatz)

1. `SKILL.md` Schritt 1b: GraphQL-Query um `databaseId` der Reviews ergänzt — das
   REST-Dismiss-Endpoint verlangt die numerische ID, nicht die GraphQL-Node-ID.
2. `SKILL.md` Schritt 8: neuer Unterabschnitt "Veralteten eigenen CHANGES_REQUESTED-Review
   aufheben" — Bedingung (aktuelle Runde postet `COMMENTED` UND ein eigener `CHANGES_REQUESTED`
   liegt aus Schritt 1b vor), automatischer Dismiss-Versuch über
   `pulls/{pr}/reviews/{id}/dismissals`, bei `403`/`404` Fallback auf `gh pr comment` mit Hinweis
   auf nötiges manuelles Dismiss. Begründet, warum eine neue eigene `REQUEST_CHANGES` diesen
   Schritt nicht braucht (ersetzt den Stand desselben Reviewers automatisch).
3. "Harte Grenzen": neuer Punkt "Eigene veraltete Reviews dürfen dismissed werden" — Klarstellung,
   dass das nicht im Widerspruch zu "Nie ungefragt absetzen" oder "Fremde Threads nie anfassen"
   steht, analog zum bestehenden Punkt zu eigenen Threads.

### Acceptance Criteria (Zusatz, aus Issue #224)

- [x] Variante entschieden und hier festgehalten (Variante C, Hybrid)
- [x] `SKILL.md` entsprechend der gewählten Variante ergänzt
- [ ] Manuell an einem echten Testlauf verifiziert
