#!/usr/bin/env bash
#
# Selbsttest für scripts/check-migrations.sh (INFRA-29, #207).
#
# Die Definition of Done von #207 verlangt einen automatisierten Nachweis: «ein PR, der eine
# bestehende Migration ändert, wird von CI rot; einer mit einer neuen Migration bleibt grün». Genau
# das lässt sich nicht sinnvoll durch echte PRs belegen — dafür müsste man jedes Mal einen
# kaputten PR aufmachen. Stattdessen baut dieser Test je Szenario ein Wegwerf-Repo mit einem
# main-Branch und einem Feature-Branch und prüft Exit-Code und Meldung des Guards.
#
# Bewusst ohne bats oder shunit2: das Repo hat keine Shell-Test-Infrastruktur, und eine Abhängigkeit
# einzuführen, damit acht Zusicherungen laufen, stünde in keinem Verhältnis. Der Preis ist ein
# handgeschriebenes assert_exit — überschaubar bei dieser Grösse.
#
# Aufruf: scripts/check-migrations.test.sh
# Exit:   0 = alle Szenarien wie erwartet, 1 = mindestens eines nicht

set -euo pipefail

GUARD="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/check-migrations.sh"
MIGRATION_DIR="backend/src/main/resources/db/migration"

passed=0
failed=0

# Legt ein Wegwerf-Repo an: main trägt V01 und V08, ausserdem eine Datei ausserhalb des
# Migrationsverzeichnisses. Danach steht der Aufrufer auf einem Feature-Branch.
#
# 'origin/main' wird als echte Remote-Ref angelegt (refs/remotes/origin/main), damit der Guard
# hier gegen denselben Ref-Namen läuft wie in CI — ein Test gegen 'main' würde den Pfad, der in
# Produktion zählt, gerade nicht abdecken.
new_repo() {
    local dir
    dir="$(mktemp -d)"
    (
        cd "${dir}"
        git init -q -b main
        git config user.email test@example.invalid
        git config user.name "Test"
        mkdir -p "${MIGRATION_DIR}" backend/src/main/java
        echo "CREATE TABLE users (id BIGINT);" > "${MIGRATION_DIR}/V01__create_users_table.sql"
        echo "ALTER TABLE transactions ADD COLUMN x BOOLEAN;" > "${MIGRATION_DIR}/V08__add_x.sql"
        echo "class Foo {}" > backend/src/main/java/Foo.java
        git add -A
        git commit -qm "main"
        git update-ref refs/remotes/origin/main HEAD
        git checkout -q -b feature
    )
    echo "${dir}"
}

# assert_exit <erwarteter-code> <beschreibung> <repo-verzeichnis> [<erwarteter-textbaustein>]
assert_exit() {
    local expected="$1" description="$2" dir="$3" expected_text="${4:-}"
    local actual=0 output

    output="$(cd "${dir}" && "${GUARD}" origin/main 2>&1)" || actual=$?

    if [[ "${actual}" != "${expected}" ]]; then
        printf 'FAIL  %s\n      Exit %s erwartet, %s bekommen. Ausgabe:\n%s\n' \
            "${description}" "${expected}" "${actual}" "${output}"
        failed=$((failed + 1))
        rm -rf "${dir}"
        return
    fi

    if [[ -n "${expected_text}" && "${output}" != *"${expected_text}"* ]]; then
        printf 'FAIL  %s\n      Meldung enthält "%s" nicht. Ausgabe:\n%s\n' \
            "${description}" "${expected_text}" "${output}"
        failed=$((failed + 1))
        rm -rf "${dir}"
        return
    fi

    printf 'ok    %s\n' "${description}"
    passed=$((passed + 1))
    rm -rf "${dir}"
}

# --- grün: der Normalfall ----------------------------------------------------------------------

r="$(new_repo)"
(cd "${r}" && echo "ALTER TABLE users ADD COLUMN y TEXT;" > "${MIGRATION_DIR}/V09__add_y.sql" \
    && git add -A && git commit -qm "neue Migration")
assert_exit 0 "neue Migration mit höherer Version" "${r}"

r="$(new_repo)"
(cd "${r}" && echo "-- a" > "${MIGRATION_DIR}/V09__a.sql" && echo "-- b" > "${MIGRATION_DIR}/V10__b.sql" \
    && git add -A && git commit -qm "zwei neue Migrationen")
assert_exit 0 "zwei neue Migrationen in Folge (V09, V10)" "${r}"

r="$(new_repo)"
(cd "${r}" && echo "class Foo { int x; }" > backend/src/main/java/Foo.java \
    && git add -A && git commit -qm "Code ausserhalb der Migrationen")
assert_exit 0 "Änderung ausserhalb des Migrationsverzeichnisses" "${r}"

r="$(new_repo)"
assert_exit 0 "PR ohne jede Änderung" "${r}"

# --- rot: die vier Fehlerbilder ----------------------------------------------------------------

r="$(new_repo)"
(cd "${r}" && echo "CREATE TABLE users (id BIGINT, email TEXT);" > "${MIGRATION_DIR}/V01__create_users_table.sql" \
    && git add -A && git commit -qm "bestehende Migration geändert")
assert_exit 1 "bestehende Migration geändert" "${r}" "V01__create_users_table.sql: geändert"

r="$(new_repo)"
(cd "${r}" && git rm -q "${MIGRATION_DIR}/V01__create_users_table.sql" && git commit -qm "gelöscht")
assert_exit 1 "bestehende Migration gelöscht" "${r}" "V01__create_users_table.sql: gelöscht"

r="$(new_repo)"
(cd "${r}" && git mv "${MIGRATION_DIR}/V01__create_users_table.sql" "${MIGRATION_DIR}/V01__create_user_table.sql" \
    && git commit -qm "umbenannt")
assert_exit 1 "bestehende Migration umbenannt" "${r}" "umbenannt nach"

# Der reale Fall aus #272 und #263: main trägt V08, der Branch legt eine zweite V08 an.
r="$(new_repo)"
(cd "${r}" && echo "-- kollidiert mit V08 auf main" > "${MIGRATION_DIR}/V08__create_notifications_table.sql" \
    && git add -A && git commit -qm "zweite V08")
assert_exit 1 "neue Migration mit bereits vergebener Version" "${r}" "liegt nicht über der höchsten"

r="$(new_repo)"
(cd "${r}" && echo "-- zu niedrig" > "${MIGRATION_DIR}/V07__too_low.sql" \
    && git add -A && git commit -qm "Out-of-Order")
assert_exit 1 "neue Migration mit niedrigerer Version" "${r}" "V07__too_low.sql: Version 7"

r="$(new_repo)"
(cd "${r}" && echo "-- a" > "${MIGRATION_DIR}/V09__a.sql" && echo "-- b" > "${MIGRATION_DIR}/V09__b.sql" \
    && git add -A && git commit -qm "zwei Dateien mit V09")
assert_exit 1 "zwei neue Migrationen mit derselben Version" "${r}" "wird in diesem PR bereits von"

r="$(new_repo)"
(cd "${r}" && echo "-- ohne Versionspräfix" > "${MIGRATION_DIR}/create_something.sql" \
    && git add -A && git commit -qm "kein Präfix")
assert_exit 1 "neue Datei ohne Versionspräfix" "${r}" "kein erkennbares Versionspräfix"

# --- Aufruffehler ------------------------------------------------------------------------------

# assert_exit ruft den Guard fest gegen origin/main auf; für diesen Fall braucht es einen Ref, den
# es nicht gibt, deshalb hier von Hand statt über den Helfer.
r="$(new_repo)"
actual=0
output="$(cd "${r}" && "${GUARD}" origin/gibt-es-nicht 2>&1)" || actual=$?
rm -rf "${r}"
if [[ "${actual}" == 2 && "${output}" == *"fetch-depth"* ]]; then
    printf 'ok    unbekannter Base-Ref meldet Aufruffehler (Exit 2) mit fetch-depth-Hinweis\n'
    passed=$((passed + 1))
else
    printf 'FAIL  unbekannter Base-Ref: Exit 2 erwartet, %s bekommen. Ausgabe:\n%s\n' "${actual}" "${output}"
    failed=$((failed + 1))
fi

# --- Ergebnis ----------------------------------------------------------------------------------

printf '\n%s Szenarien bestanden, %s fehlgeschlagen.\n' "${passed}" "${failed}"
(( failed == 0 ))
