#!/usr/bin/env bash
#
# Legt die beiden Demo-Accounts fuer die Praesentation an (INFRA-38, #258).
#
# Kein Seed-Mechanismus in der App: Es gibt weder data.sql noch einen
# Demo-Endpoint, und das soll so bleiben -- ein Endpoint, der Accounts anlegt,
# waere in Produktion ein offener Hebel. Dieses Skript geht deshalb durch
# dieselbe oeffentliche API wie ein echter Nutzer: Registrierung (US-01),
# Einkommen und Fixkosten (US-03), Onboarding-Abschluss, PDF-Upload (US-04) mit
# Polling auf den Async-Job (ADR-14). Was danach in der Datenbank steht, ist
# damit auf demselben Weg entstanden wie Produktivdaten -- inklusive
# Kategorisierung ueber Lookup und Claude (ADR-6).
#
# PASSWOERTER
#   DEMO_LARA_PASSWORD und DEMO_MARC_PASSWORD sind PFLICHT; fehlt eines, bricht
#   das Skript ab, bevor es irgendetwas anlegt. Bewusst kein Wuerfeln und keine
#   Datei, die das Skript selbst schreibt: ein generiertes Passwort ist auf
#   jedem Rechner ein anderes, und der Account laesst sich dann nur noch von dem
#   Laptop aus benutzen, auf dem er entstanden ist. Das Team vereinbart statt
#   dessen ein Passwort und setzt es hier.
#
#   In den Variablen, nicht im Repo -- #258 verlangt die Login-Daten
#   ausdruecklich ausserhalb des Repos, und CLAUDE.md sagt "Keine Secrets im
#   Git". Wer sie lokal nicht jedes Mal tippen will, legt sie in eine eigene
#   .env.demo (greift ueber .env.* in .gitignore) und laedt sie selbst:
#
#       set -a; . ./.env.demo; set +a
#
# WIEDERHOLBARKEIT
#   Das Skript ist idempotent: eine bestehende E-Mail (409) fuehrt zum Login
#   statt zum Abbruch, vorhandene Fixkosten werden nicht doppelt angelegt, und
#   ein bereits importiertes PDF (409 aus dem Duplikatschutz) wird uebersprungen.
#
# Usage:
#   export DEMO_LARA_PASSWORD='...'   # Pflicht
#   export DEMO_MARC_PASSWORD='...'   # Pflicht
#   backend/tools/seed_demo_accounts.sh
#
#   Gegen eine andere Instanz:
#   BUDGETBUDDY_API=https://<host> backend/tools/seed_demo_accounts.sh

set -euo pipefail

API="${BUDGETBUDDY_API:-http://localhost:8080}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
STATEMENTS="$ROOT/docs/demo/statements"
JAR_DIR="$(mktemp -d)"
trap 'rm -rf "$JAR_DIR"' EXIT

die() { printf '\033[31mFEHLER:\033[0m %s\n' "$*" >&2; exit 1; }
step() { printf '\n\033[1m%s\033[0m\n' "$*"; }
info() { printf '  %s\n' "$*"; }

# --------------------------------------------------------------------------
# HTTP-Helfer. Antwortkoerper nach stdout, Statuscode als Rueckgabe ueber die
# globale HTTP_CODE -- curl kann nicht beides gleichzeitig sauber liefern.
# --------------------------------------------------------------------------
HTTP_CODE=""
HTTP_BODY=""

api() {
  local jar="$1" method="$2" path="$3" body="${4:-}"
  local out; out="$(mktemp)"
  local args=(-sS -o "$out" -w '%{http_code}' -X "$method"
              -b "$jar" -c "$jar" "$API$path")
  if [[ -n "$body" ]]; then
    args+=(-H 'Content-Type: application/json' -d "$body")
  fi
  HTTP_CODE="$(curl "${args[@]}")"
  HTTP_BODY="$(cat "$out")"
  rm -f "$out"
}

upload() {
  local jar="$1" file="$2"
  local out; out="$(mktemp)"
  HTTP_CODE="$(curl -sS -o "$out" -w '%{http_code}' -X POST \
    -b "$jar" -c "$jar" -F "file=@$file;type=application/pdf" \
    "$API/api/import/pdf")"
  HTTP_BODY="$(cat "$out")"
  rm -f "$out"
}

# jq ist keine Voraussetzung fuer dieses Repo -- die Antworten sind flach genug
# fuer eine Extraktion mit sed. Bricht die Form je auf, faellt das hier auf und
# nicht erst still in der Praesentation.
json_field() { printf '%s' "$1" | sed -n "s/.*\"$2\"[[:space:]]*:[[:space:]]*\"\{0,1\}\([^,\"}]*\).*/\1/p"; }

# --------------------------------------------------------------------------
# Passwoerter
# --------------------------------------------------------------------------
# Holt ein Pflicht-Passwort aus der Umgebung und prueft es gegen die Grenzen aus
# RegisterRequest, bevor die API es mit einem nackten 400 zurueckweist.
require_password() {
  local var="$1" pw="${!1:-}"
  if [[ -z "$pw" ]]; then
    die "$var ist nicht gesetzt.

  Die Demo-Passwoerter sind Pflicht und stehen bewusst nicht im Repo:

      export DEMO_LARA_PASSWORD='...'
      export DEMO_MARC_PASSWORD='...'
      $(basename "${BASH_SOURCE[1]:-$0}")

  Im Team dasselbe Passwort verwenden -- sonst gehoert der Demo-Account dem
  Rechner, auf dem er angelegt wurde. Details: docs/demo/README.md"
  fi
  if [[ ${#pw} -lt 8 ]]; then
    die "$var ist kuerzer als 8 Zeichen; die Registrierung lehnt das ab (RegisterRequest)."
  fi
  # bcrypt kappt bei 72 BYTES, nicht Zeichen (BE-AUTH-10, #200) -- deshalb ueber
  # die Byte-Laenge pruefen und nicht ueber ${#pw}.
  local bytes
  bytes="$(printf '%s' "$pw" | wc -c | tr -d ' ')"
  if [[ "$bytes" -gt 72 ]]; then
    die "$var ist $bytes Bytes lang; bcrypt nimmt hoechstens 72 (BE-AUTH-10)."
  fi
  PASSWORD_OUT="$pw"
}

PASSWORD_OUT=""

# --------------------------------------------------------------------------
# Ein Account
# --------------------------------------------------------------------------
seed_persona() {
  local slug="$1" email="$2" password="$3" first="$4" last="$5" income="$6"
  local pattern="$7"; shift 7
  local fixed_costs=("$@")
  local jar="$JAR_DIR/$slug.cookies"
  # bash 3.2 (macOS /bin/bash) kennt ${var^^} nicht -- deshalb ueber tr.
  local pw_var="DEMO_$(printf '%s' "$slug" | tr '[:lower:]' '[:upper:]')_PASSWORD"

  step "$first $last <$email>"

  api "$jar" POST /api/auth/register \
    "{\"email\":\"$email\",\"password\":\"$password\",\"firstName\":\"$first\",\"lastName\":\"$last\"}"
  case "$HTTP_CODE" in
    200|201) info "registriert" ;;
    409)
      info "Account existiert bereits -- Login"
      api "$jar" POST /api/auth/login "{\"email\":\"$email\",\"password\":\"$password\"}"
      [[ "$HTTP_CODE" == "200" ]] \
        || die "Login fehlgeschlagen ($HTTP_CODE). Der Account existiert, aber $pw_var passt
  nicht -- vermutlich wurde er mit einem anderen Passwort angelegt. Entweder das
  urspruengliche setzen oder die Demo-Accounts zuruecksetzen (docs/demo/README.md)."
      ;;
    *) die "Registrierung fehlgeschlagen ($HTTP_CODE): $HTTP_BODY" ;;
  esac

  api "$jar" PUT /api/users/me/income "{\"betrag\":$income}"
  [[ "$HTTP_CODE" == "200" ]] || die "Einkommen setzen fehlgeschlagen ($HTTP_CODE): $HTTP_BODY"
  info "Monatseinkommen $income CHF"

  # GET /api/fixed-costs liefert FixedCostSummaryResponse -- ein Objekt mit der
  # Liste unter "fixedCosts", kein nacktes Array. Ein Vergleich gegen "[]" ist
  # deshalb immer falsch und legt die Positionen still nie an.
  api "$jar" GET /api/fixed-costs
  if [[ "$HTTP_BODY" == *'"fixedCosts":[]'* ]]; then
    for entry in "${fixed_costs[@]}"; do
      IFS='|' read -r bez betrag intervall <<< "$entry"
      api "$jar" POST /api/fixed-costs \
        "{\"bezeichnung\":\"$bez\",\"betrag\":$betrag,\"intervall\":\"$intervall\"}"
      [[ "$HTTP_CODE" == "201" || "$HTTP_CODE" == "200" ]] \
        || die "Fixkosten '$bez' fehlgeschlagen ($HTTP_CODE): $HTTP_BODY"
      info "Fixkosten: $bez $betrag CHF ($intervall)"
    done
  else
    info "Fixkosten bereits vorhanden -- uebersprungen"
  fi

  api "$jar" POST /api/users/me/onboarding-complete
  [[ "$HTTP_CODE" == "200" ]] || die "Onboarding-Abschluss fehlgeschlagen ($HTTP_CODE)"
  info "Onboarding abgeschlossen"

  local imported=0 skipped=0 total=0
  for pdf in "$STATEMENTS"/$pattern; do
    [[ -f "$pdf" ]] || die "Keine Auszuege unter $STATEMENTS/$pattern -- zuerst generate_demo_statements.py laufen lassen"
    local name; name="$(basename "$pdf")"
    upload "$jar" "$pdf"
    case "$HTTP_CODE" in
      202)
        local job_id parsed
        job_id="$(json_field "$HTTP_BODY" jobId)"
        parsed="$(json_field "$HTTP_BODY" total)"
        [[ -n "$job_id" ]] || die "Keine jobId in der Antwort: $HTTP_BODY"
        # Die Kategorisierung laeuft asynchron weiter (ADR-14). Ohne Polling
        # waere der naechste Upload schneller als der Claude-Call davor, und
        # der Abschlussbericht zaehlte halbfertige Monate.
        local waited=0
        while :; do
          api "$jar" GET "/api/import/$job_id/status"
          local status; status="$(json_field "$HTTP_BODY" status)"
          [[ "$status" == "RUNNING" ]] || break
          sleep 2
          waited=$((waited + 2))
          if [[ $waited -gt 300 ]]; then
            die "Job $job_id haengt seit ${waited}s: $HTTP_BODY"
          fi
        done
        [[ "$status" == "DONE" ]] || die "Import von $name endete mit $status: $HTTP_BODY"
        case "$HTTP_BODY" in
          *'"degraded":true'*) info "$name: $parsed Buchungen (degraded -- Rest als Sonstiges)" ;;
          *) info "$name: $parsed Buchungen" ;;
        esac
        imported=$((imported + 1)); total=$((total + parsed))
        ;;
      409) info "$name: bereits importiert -- uebersprungen"; skipped=$((skipped + 1)) ;;
      *) die "Upload von $name fehlgeschlagen ($HTTP_CODE): $HTTP_BODY" ;;
    esac
  done
  info "$imported Auszuege importiert, $skipped uebersprungen, $total Buchungen"

  local month; month="$(date +%Y-%m)"
  api "$jar" GET "/api/transactions/summary?month=$month"
  info "Kategorien $month: $HTTP_BODY"
  api "$jar" GET /api/budget/safe-to-spend
  info "Safe-to-Spend: $HTTP_BODY"
}

# --------------------------------------------------------------------------
main() {
  command -v curl >/dev/null || die "curl nicht gefunden"
  [[ -d "$STATEMENTS" ]] || die "$STATEMENTS fehlt -- zuerst generate_demo_statements.py laufen lassen"
  curl -sS -o /dev/null "$API/api/auth/login" -X POST -H 'Content-Type: application/json' -d '{}' \
    || die "Backend unter $API nicht erreichbar. Siehe README.md -> 'Lokal starten (Dev)'."

  if [[ -z "${ANTHROPIC_API_KEY:-}" ]]; then
    printf '\033[33mHINWEIS:\033[0m ANTHROPIC_API_KEY ist nicht gesetzt. Unbekannte Haendler\n'
    printf '         landen dann als "Sonstiges" (BE-CAT-02) -- die Demo zeigt nur die\n'
    printf '         Lookup-Stufe. Fuer die Praesentation den Key setzen.\n'
  fi

  local lara_pw marc_pw
  # Beide Passwoerter VOR dem ersten Schreibzugriff pruefen: ein Abbruch erst
  # bei Marc liesse Lara halb angelegt zurueck.
  require_password DEMO_LARA_PASSWORD; lara_pw="$PASSWORD_OUT"
  require_password DEMO_MARC_PASSWORD; marc_pw="$PASSWORD_OUT"

  seed_persona lara "lara@demo.bb" "$lara_pw" "Lara" "Bieri" 1900.00 \
    "PostFinance_Kontoauszug_Lara_*.pdf" \
    "WG-Zimmer Laenggasse|650.00|monatlich" \
    "Krankenkasse CSS|180.00|monatlich" \
    "Handy Salt|25.00|monatlich" \
    "Semestergebuehr Uni Bern|1500.00|jaehrlich"

  seed_persona marc "marc@demo.bb" "$marc_pw" "Marc" "Steiner" 4200.00 \
    "Raiffeisen_Kontoauszug_Marc_*.pdf" \
    "Miete Wohnung Kreis 3|1450.00|monatlich" \
    "Krankenkasse Helsana|380.00|monatlich" \
    "Swisscom Abo|79.00|monatlich" \
    "Fitnesspark Sihlcity|89.00|monatlich" \
    "Hausratversicherung|240.00|jaehrlich"

  step "Fertig."
  info "Logins: lara@demo.bb / marc@demo.bb"
  info "Passwoerter: aus DEMO_LARA_PASSWORD / DEMO_MARC_PASSWORD (hier nicht ausgegeben)."
}

main "$@"
