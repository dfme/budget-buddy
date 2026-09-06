#!/usr/bin/env bash
#
# Verhindert, dass ein PR eine Flyway-Migration antastet, die anderswo bereits angewendet wurde
# (INFRA-29, #207).
#
# DAS PROBLEM. Wird eine gemergte Migration nachträglich geändert, bricht jede Datenbank, die sie
# im alten Stand angewendet hat, beim nächsten Start ab:
#
#     Migration checksum mismatch for migration version 05
#
# CI kann das strukturell nicht bemerken. Der Workflow startet pro Lauf einen frischen Postgres,
# die Integrationstests laufen gegen Testcontainers. Eine leere Datenbank hat keine
# flyway_schema_history, an der eine Prüfsumme scheitern könnte — Flyway wendet immer alles ab V01
# an und ist immer zufrieden. Der Fehler taucht deshalb nur in einer langlebigen lokalen Dev-DB und
# in Produktion auf, also nirgends dort, wo er billig auffiele.
#
# DIE REGEL. Eine einzige, die vier Fehlerbilder abdeckt:
#
#     Keine Migrationsdatei, die auf dem Base-Branch existiert, darf geändert, gelöscht oder
#     umbenannt werden — und jede hinzugefügte muss eine Version tragen, die echt grösser ist als
#     die höchste auf dem Base-Branch.
#
#   geändert   → Checksum-Mismatch
#   gelöscht   → angewandte Migration fehlt im Repo
#   umbenannt  → Description-Mismatch (die Beschreibung steht in flyway_schema_history)
#   neu, Version <= Maximum → Out-of-Order (spring.flyway.out-of-order ist ungesetzt, Default
#                             false) bzw. «Found more than one migration with version NN», wenn die
#                             Version schon vergeben ist. Letzteres bricht den Start sofort ab.
#
# Die Acceptance Criteria von #207 nennen nur «geändert oder gelöscht» und schliessen neue Dateien
# ausdrücklich aus. Das beschreibt den Normalfall richtig, ist als Regel aber zu weit: eine
# hinzugefügte Datei mit zu niedriger Version ist für eine Live-Datenbank genauso tödlich wie eine
# geänderte, und CI ist dafür genauso blind. Die Erweiterung ist bewusst und in
# docs/plans/INFRA-29-migration-guard.md begründet.
#
# NOTFALL-AUSWEG. Muss eine Migration wirklich einmal korrigiert werden, bevor sie irgendwo
# angewendet wurde, setzt ein Mensch das PR-Label `migration-rewrite-ok`. Der Workflow überspringt
# den Aufruf dann und schreibt eine Warnung ins Log. Dieses Skript kennt das Label nicht — es
# urteilt nur über den Diff; wer es aussetzt, entscheidet der Workflow.
#
# Aufruf: scripts/check-migrations.sh [<base-ref>]        (Default: origin/main)
# Exit:   0 = sauber, 1 = Regelverstoss, 2 = Aufruffehler

set -euo pipefail

BASE_REF="${1:-origin/main}"
MIGRATION_DIR="backend/src/main/resources/db/migration"
ESCAPE_LABEL="migration-rewrite-ok"

if ! git rev-parse --verify --quiet "${BASE_REF}^{commit}" >/dev/null; then
    echo "check-migrations: Base-Ref '${BASE_REF}' existiert nicht." >&2
    echo "  In GitHub Actions braucht der Checkout dafür 'fetch-depth: 0' — der Default 1 holt" >&2
    echo "  keine Historie, und dann fehlt origin/main lokal ganz." >&2
    exit 2
fi

# Sammelt die Verstösse, statt beim ersten abzubrechen: Wer drei Migrationen angefasst hat, soll
# alle drei in einem Lauf sehen und nicht dreimal die CI bemühen.
violations=()

# --- Teil 1: bestehende Dateien dürfen nicht angetastet werden ---------------------------------
#
# --find-renames explizit, obwohl git es meist ohnehin tut: ohne Rename-Erkennung erschiene eine
# Umbenennung als A+D und liefe über den D-Zweig — richtig, aber mit irreführender Meldung. Mit
# Erkennung steht sie als R da und wird als das gemeldet, was sie ist.
#
# Die Feldtrennung ist ein echtes Tab: bei R kommen zwei Pfade in derselben Zeile.
while IFS=$'\t' read -r status path newpath; do
    [[ -z "${status}" ]] && continue
    case "${status}" in
        M*)
            violations+=("${path}: geändert — eine bereits angewendete Migration ändert ihre Prüfsumme und bricht jede Datenbank, die sie im alten Stand hat")
            ;;
        D*)
            violations+=("${path}: gelöscht — die Migration ist anderswo bereits angewendet und fehlte danach im Repo")
            ;;
        R*)
            violations+=("${path}: umbenannt nach ${newpath} — die Beschreibung steht in flyway_schema_history und stimmt danach nicht mehr überein")
            ;;
    esac
done < <(git diff --name-status --find-renames "${BASE_REF}...HEAD" -- "${MIGRATION_DIR}")

# --- Teil 2: neue Dateien müssen über dem bisherigen Maximum liegen ----------------------------
#
# Warum nicht bloss «Version noch nicht vergeben»: Flyway lehnt auf einer Datenbank, die schon V08
# angewendet hat, auch ein frisch hinzugekommenes V07 ab (out-of-order steht auf dem Default
# false). Die Prüfung gegen das Maximum deckt beide Fälle mit derselben Zeile ab.

# 10# erzwingt Basis 10. Ohne das läse bash '08' als Oktalzahl und stiege mit «value too great for
# base» aus — und genau 08 und 09 sind der aktuelle Stand dieses Repos.
version_of() {
    local filename="${1##*/}"
    local digits="${filename#V}"
    digits="${digits%%__*}"
    if [[ ! "${digits}" =~ ^[0-9]+$ ]]; then
        echo ""
        return
    fi
    echo $((10#${digits}))
}

max_version=0
while read -r path; do
    [[ -z "${path}" ]] && continue
    v="$(version_of "${path}")"
    [[ -z "${v}" ]] && continue
    (( v > max_version )) && max_version="${v}"
done < <(git ls-tree --name-only "${BASE_REF}" -- "${MIGRATION_DIR}/")

# Zweitverwendung derselben Version innerhalb des PR: zwei neue Dateien mit V09 sind für sich
# genommen beide «grösser als das Maximum» und fielen sonst durch.
#
# Bewusst eine Textliste statt eines assoziativen Arrays: macOS liefert bis heute bash 3.2 aus,
# das `declare -A` nicht kennt. Das Skript soll lokal genauso laufen wie auf dem Ubuntu-Runner —
# ein Guard, den man nur in CI ausprobieren kann, wird vor dem Push nicht ausprobiert.
seen_versions=""   # je Zeile: <version><TAB><pfad>

while IFS=$'\t' read -r status path _; do
    [[ -z "${status}" ]] && continue
    [[ "${status}" == A* ]] || continue

    v="$(version_of "${path}")"
    if [[ -z "${v}" ]]; then
        violations+=("${path}: kein erkennbares Versionspräfix — erwartet wird V<NN>__<beschreibung>.sql (docs/CONVENTIONS.md)")
        continue
    fi

    if (( v <= max_version )); then
        violations+=("${path}: Version ${v} liegt nicht über der höchsten auf ${BASE_REF} (${max_version}) — eine Datenbank, die ${max_version} bereits angewendet hat, nimmt sie nicht mehr an")
    fi

    previous="$(printf '%s' "${seen_versions}" | awk -F'\t' -v want="${v}" '$1 == want { print $2; exit }')"
    if [[ -n "${previous}" ]]; then
        violations+=("${path}: Version ${v} wird in diesem PR bereits von ${previous} belegt — Flyway bricht mit «Found more than one migration with version ${v}» beim Start ab")
    else
        seen_versions="${seen_versions}${v}"$'\t'"${path}"$'\n'
    fi
done < <(git diff --name-status --find-renames "${BASE_REF}...HEAD" -- "${MIGRATION_DIR}")

# --- Ergebnis ----------------------------------------------------------------------------------

if (( ${#violations[@]} == 0 )); then
    echo "check-migrations: keine bestehende Migration angetastet, neue Versionen liegen über ${max_version} (Basis: ${BASE_REF})."
    exit 0
fi

echo "check-migrations: ${#violations[@]} Verstoss/Verstösse gegen die Unveränderlichkeit der Migrationen." >&2
echo >&2
for violation in "${violations[@]}"; do
    echo "  - ${violation}" >&2
done
echo >&2
echo "Eine Migration, die auf ${BASE_REF} liegt, ist anderswo möglicherweise bereits angewendet." >&2
echo "Statt sie zu ändern: eine neue Migration mit der nächsthöheren Version anlegen." >&2
echo >&2
echo "Wurde die Migration nachweislich noch nirgends angewendet, setzt ein Mensch das PR-Label" >&2
echo "'${ESCAPE_LABEL}' — der Workflow überspringt diese Prüfung dann und protokolliert das." >&2
exit 1
