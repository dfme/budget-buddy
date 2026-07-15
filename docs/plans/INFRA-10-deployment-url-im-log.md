# [INFRA-10] Deployment-URL in der Erfolgsmeldung des CD-Jobs anzeigen

- **Issue:** #73
- **Task-ID:** INFRA-10
- **Branch:** `feature/INFRA-10-deployment-url-im-log`
- **Typ:** Enhancement (Label `enhancement`) — reine Log-/Reporting-Verbesserung, kein Defekt
- **Vorgeschichte:** #68 (Versionsprüfung), #71 (Deadline + Logging)

## Problem

Die Erfolgsmeldung nennt Commit, Dauer und Health-Status, aber nicht die URL:

```
Neue Version live nach 204s (Versuch 10): Instanz meldet Commit 02da8255…, health HTTP 200.
```

Inkonsistent: Der **Fehler**fall nennt `$BASE_URL` bereits (`… meldet $BASE_URL nicht Commit …`).
Ausgerechnet im Erfolgsfall — wenn man das frische Deployment öffnen will — fehlt sie.

## Entscheide

- **Log-Zeile + Job-Summary.** Die Log-Zeile beantwortet die Frage beim Lesen des Logs; die
  Summary (`$GITHUB_STEP_SUMMARY`) rendert einen klickbaren Link zuoberst auf der Run-Seite,
  ohne dass der Job-Log aufgeklappt werden muss — dort schaut man nach einem Deploy zuerst hin.
- **URL bleibt einfach definiert.** `BASE_URL` in der Step-Env ist die einzige Quelle; kein
  zweiter Hardcode in der Summary.
- **Kein Eingriff in die Erfolgs-/Fehlerlogik.** Nur zusätzliche Ausgabe. Die Bedingungen
  (SHA-Präfix-Match, health 200, Deadline) bleiben unberührt.
- **Summary nur im Erfolgsfall.** Im Fehlerfall trägt die `::error::`-Annotation die Diagnose
  und wird von GitHub ohnehin prominent angezeigt; eine zweite Darstellung wäre Rauschen.

## Betroffene / neue Files

- **Ändern:** `.github/workflows/cd.yml` — Erfolgsmeldung + Job-Summary
- **Neu:** `docs/plans/INFRA-10-deployment-url-im-log.md` (dieser Plan)

Keine Backend-/Frontend-Änderung.

## Umsetzung

```bash
echo "Neue Version live nach ${dauer}s (Versuch $attempt): Instanz meldet Commit $deployed, health HTTP 200."
echo "Erreichbar unter: $BASE_URL"
{
  echo "### ✅ Deployment erfolgreich"
  echo ""
  echo "| | |"
  echo "|---|---|"
  echo "| **URL** | $BASE_URL |"
  echo "| **Commit** | \`$deployed\` |"
  echo "| **Dauer** | ${dauer}s |"
} >> "$GITHUB_STEP_SUMMARY"
```

## Test-Strategie

1. **Happy Path** gegen Fake-Deploy: Erfolgsmeldung enthält die URL; `GITHUB_STEP_SUMMARY`
   (auf eine temporäre Datei gezeigt) enthält URL, Commit und Dauer.
2. **Markdown der Summary** wird als Tabelle gerendert (Struktur prüfen, nicht nur Inhalt).
3. **Fehlerfall** unverändert: keine Summary geschrieben, `::error::` nennt die URL wie bisher.
4. **Regression:** Erfolgs-/Fehlerlogik unberührt — Deadline-Test und SHA-Match-Test aus
   INFRA-08/09 nochmals durchlaufen.
5. `bash -n` + YAML-Parse; `./mvnw verify` als Regressionsschutz (keine Backend-Änderung).
6. **Einschränkung:** Wie zuvor nur gegen lokale Fakes verifizierbar; die gerenderte Summary
   ist erst im echten Run zu sehen.

## Acceptance Criteria (aus Issue #73)

- [ ] Die Erfolgsmeldung im Log enthält die Deployment-URL
- [ ] Die Run-Seite zeigt eine Zusammenfassung mit klickbarer URL, Commit und Dauer
- [ ] Die URL bleibt an einer Stelle definiert (kein zweiter Hardcode neben `BASE_URL`)
- [ ] Der Fehlerfall verhält sich unverändert
- [ ] Kein Einfluss auf die Erfolgs-/Fehlerlogik des Smoke-Tests
