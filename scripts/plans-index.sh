#!/usr/bin/env bash
#
# Erzeugt docs/plans/README.md — den Index über alle Implementierungspläne.
#
# ACHTUNG: Dieses Skript gehört NICHT in den normalen Ablauf. `/implement-issue` hängt die
# Zeile für einen neuen Plan selbst an den Index an — es kennt alle Werte ohnehin, und ein
# Skriptaufruf pro Issue wäre ein Schritt, der nur Gelegenheit zum Vergessen bietet.
#
# Gebraucht wird es für zwei Fälle:
#   1. Erstbefüllung und Reparatur — baut den Index vollständig aus Dateien + Board neu auf,
#      etwa wenn Zeilen fehlen, doppelt sind oder jemand von Hand editiert hat.
#   2. --check — verifiziert, ob der Index zum Dateibestand passt, ohne zu schreiben.
#
# Der Index führt bewusst NUR Spalten, die sich nach dem Schreiben des Plans nicht mehr
# ändern: Task-ID, Titel, Issue, Story, Sprint. Status und Story Points stehen absichtlich
# nicht drin — die leben im Board, ändern sich laufend, und eine Kopie davon wäre ab dem
# Moment ihrer Erzeugung falsch. Wer den aktuellen Stand braucht, klickt auf das Issue.
#
# Aufruf: scripts/plans-index.sh [--check]
#
# Benötigt `gh` (authentifiziert) und `jq`: Story und Sprint der Bestandspläne stehen in
# deren Kopf grösstenteils nicht drin und werden einmalig aus dem Board geholt.

set -euo pipefail

PROJECT_OWNER="dfme"
PROJECT_NUMBER=4
REPO_URL="https://github.com/dfme/budget-buddy"
REPO_ROOT="$(git rev-parse --show-toplevel)"
PLANS_DIR="${REPO_ROOT}/docs/plans"
INDEX_FILE="${PLANS_DIR}/README.md"

check_only=false
[[ "${1:-}" == "--check" ]] && check_only=true

command -v gh >/dev/null || { echo "gh CLI nicht gefunden" >&2; exit 1; }
command -v jq >/dev/null || { echo "jq nicht gefunden" >&2; exit 1; }

# --- Board einmal abfragen: Issue-Nr -> Sprint, Stories -------------------------------------
# Die Pagination-Variable MUSS `$endCursor` heissen: `gh api graphql --paginate` injiziert den
# Cursor unter genau diesem festen Namen. Bei jedem anderen Namen kommt er nie an, `after` bleibt
# null, und dieselbe erste Seite wird endlos wiederholt — das Skript terminiert dann nicht.
board_data="$(gh api graphql -f query='
query($org:String!, $num:Int!, $endCursor:String) {
  user(login:$org) { projectV2(number:$num) {
    items(first:100, after:$endCursor) {
      pageInfo { hasNextPage endCursor }
      nodes {
        sprint: fieldValueByName(name:"Sprint") { ... on ProjectV2ItemFieldIterationValue { title } }
        content { ... on Issue { number labels(first:20) { nodes { name } } } }
      }
    }
  } }
}' -f org="$PROJECT_OWNER" -F num="$PROJECT_NUMBER" --paginate --jq '
  .data.user.projectV2.items.nodes[]
  | select(.content.number != null)
  | [
      (.content.number | tostring),
      (.sprint.title // "—"),
      ([.content.labels.nodes[].name | select(startswith("us-")) | ascii_upcase] | sort
       | if length == 0 then "—" else join(", ") end)
    ] | @tsv')"

lookup() { # lookup <issue-nr> <spalte 2=Sprint|3=Story>
  awk -F'\t' -v n="$1" -v c="$2" '$1 == n { print $c; found=1 } END { if (!found) print "—" }' <<<"$board_data"
}

# --- Index aufbauen -------------------------------------------------------------------------
tmp="$(mktemp)"
trap 'rm -f "$tmp"' EXIT

{
  cat <<HEADER
# Implementierungspläne

Pro umgesetztem Issue ein Plan — abgelegt von [\`/implement-issue\`](../../.claude/skills/implement-issue/SKILL.md)
nach der Bestätigung durch den User, bevor der Branch erstellt wird.

Die Ablage ist bewusst **flach**. Ein Verzeichnis pro Sprint wäre die naheliegende Gliederung,
schreibt aber eine Dimension fest: Sprint-Zugehörigkeit ist eine Eigenschaft des Boards und
ändert sich bei Carryover — #13 und #16 wurden in Sprint 2 geplant und erst in Sprint 3 fertig.
Ordner hätten diese Dateien umziehen lassen und die Historie gebrochen. Der Index bildet
Bereich, Story und Sprint stattdessen gleichzeitig als Spalten ab.

**Status und Story Points stehen hier nicht.** Die ändern sich laufend und gehören ins
[Sprint Board](https://github.com/users/${PROJECT_OWNER}/projects/${PROJECT_NUMBER}) — eine Kopie
davon wäre ab ihrer Erzeugung veraltet. Die Spalte *Sprint* meint den Sprint, in dem der Plan
**geschrieben** wurde, nicht den, in dem das Issue fertig wurde.

Neue Zeilen hängt \`/implement-issue\` beim Ablegen des Plans selbst an. Bei Lücken oder
Handarbeit im Index baut \`scripts/plans-index.sh\` ihn vollständig neu auf.

HEADER

  printf '| Task-ID | Plan | Issue | Story | Sprint |\n'
  printf '| ------- | ---- | ----- | ----- | ------ |\n'

  for file in "$PLANS_DIR"/*.md; do
    base="$(basename "$file")"
    [[ "$base" == "README.md" ]] && continue

    # BE-AUTH-01-jwt-filter.md -> Task-ID "BE-AUTH-01"
    task_id="$(sed -E 's/^([A-Z0-9]+(-[A-Z0-9]+)*-[0-9]+)-.*\.md$/\1/' <<<"$base")"

    # Titel aus der ersten Überschrift, ohne das führende "[TASK-ID] "
    title="$(sed -n '1s/^# //p' "$file" | sed -E 's/^\[[^]]+\] *//')"
    [[ -z "$title" ]] && title="$base"

    # Issue-Nummer aus dem Plan-Kopf. Im Bestand sind drei Formate gewachsen — Bullet-Liste,
    # Tabelle und freie Prosa. Gesucht wird deshalb in den ersten 15 Zeilen die erste Zeile
    # mit dem Feldnamen "Issue"; so greift die Suche nicht auf Zeilen wie "| Depends on | #15 |".
    issue="$(awk 'NR<=15 && /Issue/ { if (match($0, /#[0-9]+/)) { print substr($0, RSTART+1, RLENGTH-1); exit } }' "$file")"

    if [[ -n "$issue" ]]; then
      issue_cell="[#${issue}](${REPO_URL}/issues/${issue})"
      story="$(lookup "$issue" 3)"
      sprint="$(lookup "$issue" 2)"
    else
      issue_cell="—"; story="—"; sprint="—"
    fi

    printf '| `%s` | [%s](%s) | %s | %s | %s |\n' \
      "$task_id" "$title" "$base" "$issue_cell" "$story" "$sprint"
  done
} >"$tmp"

if $check_only; then
  if diff -u "$INDEX_FILE" "$tmp"; then
    echo "docs/plans/README.md ist aktuell."
  else
    echo "docs/plans/README.md weicht ab — scripts/plans-index.sh ausführen." >&2
    exit 1
  fi
else
  mv "$tmp" "$INDEX_FILE"
  trap - EXIT
  echo "docs/plans/README.md geschrieben ($(grep -c '^| `' "$INDEX_FILE") Pläne)."
fi
