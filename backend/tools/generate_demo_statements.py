#!/usr/bin/env python3
"""Generate demo bank statements for the two README personas (INFRA-38).

Not test fixtures. These statements feed the PRESENTATION accounts: they are
uploaded through the normal US-04 flow by seed_demo_accounts.sh, and what the
audience sees on the dashboard is whatever this script wrote.

    Lara Bieri   (22, Studentin, Bern)     PostFinance layout
    Marc Steiner (25, Junior-Verkauf, ZH)  Raiffeisen layout (generic branch)

WHY THE MONTHS ARE RELATIVE, NOT HARDCODED
------------------------------------------
SafeToSpendService rechnet ausschliesslich fuer den LAUFENDEN Monat; ein
vergangener liefert den CLOSED-Marker (SafeToSpendService.java:56). Ein Satz
fest datierter Auszuege ist damit ab dem naechsten Monatswechsel genau fuer das
Feature tot, das die Demo zeigen soll. Erzeugt werden deshalb --months Auszuege
(Default 6), die mit --end-month enden, und der letzte reicht nur bis --as-of. Der Snapshot
in docs/demo/statements/ ist eine Momentaufnahme: verschiebt sich die
Praesentation ueber einen Monatswechsel, dieses Skript einmal neu laufen lassen.

WHY IT IMPORTS THE FIXTURE GENERATOR
------------------------------------
generate_pdf_fixtures.py wird NICHT veraendert (#258 schliesst das aus) -- es
wird als Modul importiert und liefert das Satzbild: Page, die Detailblock-Bauer
(_post_card, _post_lsv, ...), _post_render/_post_write und _swiss. Damit sind
diese Auszuege bitgleich in der Form, die SwissBankStatementParser in den
Fixtures schon parst. Eine eigene Nachbildung waere am Tag ihrer Entstehung
richtig und danach still daneben -- die PostFinance-Detailzeilen muessen durch
DETAIL_NOISE und MAX_DETAIL_LINES kommen, sonst kategorisiert die Demo Miete
als Sonstiges.

Die persona-spezifischen Modul-Globals (Kontoinhaber, IBAN, Kontonummer,
Ausgabeverzeichnis) werden vor jedem Auszug gesetzt; _post_render liest sie zur
Laufzeit. _assert_out_dir() haelt fest, dass OUT dabei nie auf das
Fixture-Verzeichnis zeigt -- ein vergessener Override wuerde sonst die
Parser-Fixtures ueberschreiben.

Der Raiffeisen-Zweig ist hier nachgebaut statt importiert: raiffeisen() im
Fixture-Skript verdrahtet Buchungen, IBAN und Periode fest und ist als Funktion
nicht wiederverwendbar. Nachgezogen sind Spaltenraster, ROW_FLOOR und die
Seitenzahl-Vorausberechnung.

HAENDLERWAHL
------------
Ein Teil der Buchungen trifft absichtlich die Seeds aus
V04__create_category_lookup_table.sql (Migros, Coop, SBB, CSS, Swisscom,
Netflix, Zalando, digitec), ein Teil absichtlich nicht. Ohne unbekannte
Haendler haette die Demo weder fuer die Claude-Stufe (ADR-6, Schritt 2) noch
fuer die manuelle Korrektur (Schritt 3) etwas zu zeigen. Das Skript weist die
Quote am Ende aus.

Usage:
    pip install reportlab
    python3 backend/tools/generate_demo_statements.py
    python3 backend/tools/generate_demo_statements.py --end-month 2026-12 --months 4
"""

import argparse
import os
import random
import sys
from datetime import date, timedelta
from decimal import Decimal

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import generate_pdf_fixtures as fx  # noqa: E402  (needs the sys.path line above)

from reportlab.lib.pagesizes import A4  # noqa: E402
from reportlab.pdfgen import canvas  # noqa: E402

W, H = A4

OUT_DIR = os.path.normpath(os.path.join(
    os.path.dirname(os.path.abspath(__file__)), "..", "..", "docs", "demo", "statements"))

AUTHOR = "BudgetBuddy Demo Data (synthetic)"

MONTHS_DE = ["", "Januar", "Februar", "Maerz", "April", "Mai", "Juni",
             "Juli", "August", "September", "Oktober", "November", "Dezember"]


def _assert_out_dir():
    """Der Fixture-Modul-Global OUT zeigt per Default auf src/test/resources/pdf.

    Ein vergessener Override wuerde die Parser-Fixtures ueberschreiben, und weil
    reportlab ohnehin jede Datei neu schreibt, faellt das erst im Binaer-Diff
    auf. Deshalb hart pruefen statt darauf vertrauen, dass der Aufrufer setzt.
    """
    if os.path.normpath(fx.OUT) != OUT_DIR:
        raise SystemExit(f"OUT zeigt auf {fx.OUT}, erwartet {OUT_DIR}")


def _months_back(end_month, count):
    """[(jahr, monat)] aufsteigend, endend auf end_month."""
    y, m = end_month
    out = []
    for _ in range(count):
        out.append((y, m))
        m -= 1
        if m == 0:
            y, m = y - 1, 12
    return list(reversed(out))


def _last_day(y, m):
    return (date(y + (m == 12), (m % 12) + 1, 1) - timedelta(days=1)).day


def _jitter(rng, base, pct=0.25):
    """Betrag mit +/- pct Streuung, auf 5 Rappen gerundet.

    Ohne Streuung traegt jede Migros-Buchung denselben Betrag, und der
    FixedCostDebitMatcher (BE-STS-01) zieht eine betragsgleiche Belastung je
    Fixkosten-Position ab -- eine Demo aus lauter identischen Betraegen wuerde
    dort zufaellig treffen und die Safe-to-Spend-Zahl unerklaerlich machen.
    """
    base = Decimal(base)
    factor = Decimal(str(round(rng.uniform(1 - pct, 1 + pct), 4)))
    cents = (base * factor * 20).to_integral_value() / 20
    return Decimal(cents).quantize(Decimal("0.01"))


# ============================== Lara (PostFinance) ============================
# Eigenes Konto und Gegenparteien -- frei erfunden wie alle Demo-Daten.
LARA_HOLDER = ["Lara Bieri", "Laenggassstrasse 42", "3012 Bern"]
LARA_IBAN_SPACED = "CH21 0900 0000 3012 4488 1"
LARA_ACCOUNT_NO = "30-124488-1"
LARA_CP_MIETE = "CH8009000000301299887"
LARA_CP_ELTERN = "CH4409000000301255661"
LARA_CP_BAR = "CH1200762011600330012"
LARA_CP_FIRMA = "CH8830000008500011122"

LARA_START_SALDO = Decimal("3180.45")

# (Tag, Buchungstext, Betrag, Blockbauer) -- der Buchungstext ist die
# ZAHLUNGSART, der Haendler steckt im Detailblock (echtes PostFinance-Satzbild).
LARA_VARIABLE = [
    ("KAUF/DIENSTLEISTUNG", "38.60", "card", "MIGROS M BERN", "BERN (CH)"),
    ("KAUF/DIENSTLEISTUNG", "24.15", "card", "COOP CITY BERN", "BERN (CH)"),
    ("KAUF/DIENSTLEISTUNG", "17.90", "card", "DENNER SATELLIT LAENGGASSE", "BERN (CH)"),
    ("KAUF/DIENSTLEISTUNG", "29.40", "card", "ALDI SUISSE BERN BREITENRAIN", "BERN (CH)"),
    ("TWINT", "12.50", "wallet", "BAECKEREI GLATZ", "BERN (CH)"),
    ("KAUF/DIENSTLEISTUNG", "8.80", "card", "SBB CFF FFS BERN", "BERN (CH)"),
    ("TWINT", "6.40", "wallet", "KIOSK UNITOBLER", "BERN (CH)"),
    ("KAUF/DIENSTLEISTUNG", "22.00", "card", "MENSA UNI BERN", "BERN (CH)"),
    ("TWINT", "16.80", "wallet", "CAFE KAIROS", "BERN (CH)"),
    ("KAUF/DIENSTLEISTUNG", "43.20", "card", "APOTHEKE ZYTGLOGGE", "BERN (CH)"),
    ("KAUF/DIENSTLEISTUNG", "31.50", "card", "COOP PRONTO BERN BAHNHOF", "BERN (CH)"),
    ("BARGELDBEZUG", "60.00", "plain", "BANCOMAT BERN BAHNHOF", None),
]

# Nicht jeden Monat: sonst waeren es Fixkosten und keine Ausreisser.
LARA_OCCASIONAL = [
    ("KAUF/DIENSTLEISTUNG", "78.90", "online", "ZALANDO SE"),
    ("KAUF/DIENSTLEISTUNG", "54.00", "online", "EXLIBRIS AG"),
    ("KAUF/DIENSTLEISTUNG", "39.00", "card", "KINO REX BERN"),
    ("KAUF/DIENSTLEISTUNG", "112.00", "card", "VELOWERKSTATT BERN"),
]


def _lara_month(rng, y, m, max_day):
    """Buchungen eines Monats fuer Lara, aufsteigend nach Tag."""
    monat = MONTHS_DE[m].upper()
    yy = f"{y % 100:02d}"
    rows = []

    def stamp(day):
        return f"{day:02d}.{m:02d}.{yy}"

    def add(day, text, amount, block, credit=False):
        if day > max_day:
            return
        rows.append((day, text, Decimal(amount), block, credit))

    # -- Fixkosten (die drei monatlichen Positionen aus dem Onboarding) --------
    add(1, "DAUERAUFTRAG", "650.00",
        fx._post_dauerauftrag("90-77665544", LARA_CP_MIETE, "WG LAENGGASSE 42",
                              f"ZIMMERMIETE {monat} {y}"))
    add(3, "LASTSCHRIFT", "180.00",
        fx._post_lsv(LARA_CP_FIRMA, "CSS VERSICHERUNG AG", "TRIBSCHENSTRASSE 21",
                     "6002 LUZERN", f"PRAEMIE {monat} {y}"))
    add(5, "LASTSCHRIFT", "25.00",
        fx._post_lsv(LARA_CP_FIRMA, "SALT MOBILE SA", "RUE DU CAUDRAY 4",
                     "1020 RENENS", f"MOBILE ABO {monat}"))

    # -- Einkommen ------------------------------------------------------------
    # Elternbeitrag fix, Barjob schwankend: genau die Unregelmaessigkeit, die
    # Lara laut README ihren Ueberblick kostet.
    # Der Absender ist eine natuerliche Person und steht deshalb in der Form, die
    # ein realer Auszug druckt -- NACHNAME, VORNAME in Versalien. Das ist die
    # einzige Form, die PromptSanitizer.PERSON_NAME maskiert
    # (PromptSanitizer.java:140); ein frei geschriebenes "Bieri Martin und Ruth"
    # faellt durch die Regel und ginge im Klartext an die Claude-API. Die
    # Demo-Daten sind zwar erfunden, sollen die Maskierung aber vorfuehren statt
    # an ihr vorbeizulaufen.
    add(10, "GUTSCHRIFT", "1000.00",
        fx._post_giro_in(LARA_CP_ELTERN, "BIERI, MARTIN", "Dorfstrasse 8",
                         "3123 Belp", "AUSBILDUNGSBEITRAG"), credit=True)
    add(25, "GUTSCHRIFT", str(_jitter(rng, "800.00", 0.22)),
        fx._post_giro_in(LARA_CP_BAR, "Bar Roessli GmbH", "Rathausgasse 72",
                         "3011 Bern", f"LOHN {monat} {y}"), credit=True)

    # -- Variable Ausgaben ----------------------------------------------------
    for day in sorted(rng.sample(range(2, 29), 18)):
        text, base, kind, merchant, city = rng.choice(LARA_VARIABLE)
        amount = _jitter(rng, base)
        if kind == "card":
            block = fx._post_card(merchant, city, stamp(max(1, day - 1)))
        elif kind == "wallet":
            block = fx._post_wallet(merchant, city, stamp(max(1, day - 1)))
        else:
            block = fx._post_plain(merchant)
        add(day, text, str(amount), block)

    if rng.random() < 0.75:
        text, base, kind, merchant = rng.choice(LARA_OCCASIONAL)
        day = rng.randint(6, 26)
        block = (fx._post_online(merchant, stamp(max(1, day - 1)), "PMT-4471-2205",
                                 "BST-99231")
                 if kind == "online" else fx._post_card(merchant, "BERN (CH)", stamp(day - 1)))
        add(day, text, str(_jitter(rng, base)), block)

    rows.sort(key=lambda r: r[0])
    return [fx._post_row(stamp(day), text, str(amount), stamp(day), block, credit=credit)
            for day, text, amount, block, credit in rows]


# ============================== Marc (Raiffeisen) =============================
MARC_HOLDER = ["Marc Steiner", "Birmensdorferstrasse 118", "8003 Zuerich"]
MARC_IBAN = "CH8981464000073912644"
MARC_START_SALDO = Decimal("2480.00")

# Raiffeisen druckt den Haendler in die Buchungszeile -- kein Detailblock.
MARC_FIXED = [
    (1, "Dauerauftrag Miete Immobilien Kreis 3 AG", "1450.00"),
    (3, "LSV Helsana Versicherungen AG", "380.00"),
    (5, "LSV Swisscom (Schweiz) AG", "79.00"),
    (6, "LSV Fitnesspark Sihlcity", "89.00"),
    (8, "LSV Netflix International B.V.", "20.90"),
    (8, "LSV Spotify AB", "13.90"),
    (12, "LSV Serafe AG Radio und TV", "27.90"),
]

# Das "Kleinvieh" aus README.md: viele kleine Betraege, die einzeln harmlos
# aussehen. Ein Teil trifft die Lookup-Seeds, ein Teil bewusst nicht.
MARC_VARIABLE = [
    ("Kartenzahlung Coop Pronto Shop Zuerich", "8.40"),
    ("Kartenzahlung Migros M Zuerich Wiedikon", "34.20"),
    ("Kartenzahlung Coop-2001 Zuerich Sihlcity", "27.60"),
    ("Kartenzahlung Denner Satellit Zuerich", "19.80"),
    ("TWINT Sushi Take Away Loewenstrasse", "16.50"),
    ("TWINT Cafe Bar Zentral", "7.20"),
    ("Kartenzahlung Doener Palast Langstrasse", "13.50"),
    ("Kartenzahlung SBB CFF FFS Zuerich HB", "9.60"),
    ("Kartenzahlung Kiosk Stauffacher", "5.80"),
    ("TWINT Feierabendbier Bar Rothaus", "22.00"),
    ("Kartenzahlung Aldi Suisse Zuerich", "24.90"),
    ("Kartenzahlung Lidl Schweiz Zuerich", "21.40"),
    ("Kartenzahlung Burger Lab Europaallee", "18.90"),
    ("Bezug Bancomat Zuerich Stauffacher", "100.00"),
    ("Kartenzahlung Coiffeur Studio 8", "45.00"),
    ("Onlinekauf digitec Galaxus AG", "68.00"),
    ("Onlinekauf Zalando SE", "89.90"),
    ("Kartenzahlung Gamestop Zuerich", "59.90"),
]

MARC_SALARY = ("Gutschrift Lohn Detailhandel Zuerich AG", "4200.00")


def _marc_month(rng, y, m, max_day):
    """(tag, text, betrag, is_credit) eines Monats fuer Marc."""
    monat = MONTHS_DE[m]
    rows = []

    def add(day, text, amount, credit=False):
        if day <= max_day:
            rows.append((day, text, Decimal(amount), credit))

    for day, text, amount in MARC_FIXED:
        add(day, text, amount)
    add(25, f"{MARC_SALARY[0]} {monat} {y}", MARC_SALARY[1], credit=True)

    # 52 Kleinbuchungen pro Monat: die Menge IST die Aussage. Mit fuenf
    # Buchungen sieht Marcs Auszug aus wie Laras, und das Problem aus README.md
    # ("0 CHF uebrig trotz Vollzeitjob") ist auf dem Dashboard nicht sichtbar.
    # Die Zahl ist so gewaehlt, dass Fixkosten plus Kleinvieh den Lohn fast
    # genau aufbrauchen -- ein Auszug, der Monat fuer Monat 1'100 CHF liegen
    # laesst, widerspricht der Persona.
    for _ in range(52):
        day = rng.randint(1, 28)
        text, base = rng.choice(MARC_VARIABLE)
        add(day, text, str(_jitter(rng, base)))

    rows.sort(key=lambda r: r[0])
    return rows


def _raiffeisen_page_count(row_count, header_height, row_floor):
    """Zaehlt die Seiten vorab, damit "Seite x/y" stimmt.

    Spiegelt die Hoehenarithmetik unten -- Kopf, Saldovortrag und dy=11 je
    Buchung -- plus die Schlussseite mit den Totalen. Uebernommen aus
    generate_pdf_fixtures._raiffeisen_page_count; das Layout dort wird nicht
    importiert, weil raiffeisen() seine Buchungen fest verdrahtet.
    """
    y = H - 50 - header_height - 13
    pages = 1
    for _ in range(row_count):
        if y < row_floor:
            pages += 1
            y = H - 50 - header_height
        y -= 11
    return pages + 1


def _raiffeisen_write(filename, holder, iban, period, rows, start_saldo):
    """Ein Raiffeisen-Auszug. rows: [(datum, text, betrag, saldo, is_credit)]."""
    path = os.path.join(OUT_DIR, filename)
    c = canvas.Canvas(path, pagesize=A4)
    c.setTitle("Kontoauszug")
    c.setAuthor(AUTHOR)

    X_DATE, X_VAL, X_TEXT, X_AMT, X_SALDO = 40, 95, 150, 450, 550
    ROW_FLOOR = 60
    header_height = 6 * 11 + 8 + 10 + 3 * 9 + 8 + 13

    debits = sum((r[2] for r in rows if not r[4]), Decimal("0.00"))
    credits = sum((r[2] for r in rows if r[4]), Decimal("0.00"))
    end_saldo = rows[-1][3] if rows else start_saldo
    page_total = _raiffeisen_page_count(len(rows), header_height, ROW_FLOOR)

    def header(p, page_no):
        p.text(40, "Raiffeisenbank Zuerich", 10, "Helvetica-Bold")
        p.text(40, "Kalkbreitestrasse 10, 8003 Zuerich")
        p.gap()
        p.text(300, "Herr")
        for line in holder:
            p.text(300, line)
        p.gap(10)
        p.text(40, "Kontoauszug Privatkonto CHF", 10, "Helvetica-Bold")
        p.text(40, f"IBAN {iban}", 9)
        p.text(40, period, 9)
        p.gap(8)
        p.row([(X_DATE, "Buchung", "l"), (X_VAL, "Valuta", "l"), (X_TEXT, "Text", "l"),
               (X_AMT, "Belastung Gutschrift", "r"), (X_SALDO, "Saldo", "r")],
              8, "Helvetica-Bold", 13)
        c.setFont("Helvetica", 7)
        c.drawRightString(W - 40, 32, f"Seite {page_no}/{page_total}")

    page_no = 1
    p = fx.Page(c)
    header(p, page_no)
    p.row([(X_TEXT, "Saldovortrag", "l"), (X_SALDO, fx._swiss(start_saldo), "r")],
          8, "Helvetica-Bold", 13)

    for stamp, text, betrag, saldo, _credit in rows:
        if p.y < ROW_FLOOR:
            fx.footer_marker(c)
            c.showPage()
            page_no += 1
            p = fx.Page(c)
            header(p, page_no)
        # Faellt eine Buchung unter ROW_FLOOR, verschmilzt sie beim Extrahieren
        # mit dem Fusszeilen-Marker, beginnt nicht mehr mit einem Datum und
        # verschwindet still aus dem Import (BE-PDF-12).
        assert p.y >= ROW_FLOOR, "Buchungszeile zu nah an der Fusszeile"
        p.row([(X_DATE, stamp, "l"), (X_VAL, stamp, "l"), (X_TEXT, text, "l"),
               (X_AMT, fx._swiss(betrag), "r"), (X_SALDO, fx._swiss(saldo), "r")], dy=11)

    fx.footer_marker(c)
    c.showPage()

    p = fx.Page(c)
    header(p, page_no + 1)
    p.gap(6)
    p.row([(X_TEXT, "Total Belastungen", "l"), (X_AMT, fx._swiss(debits), "r")],
          8, "Helvetica-Bold", 13)
    p.row([(X_TEXT, "Total Gutschriften", "l"), (X_AMT, fx._swiss(credits), "r")],
          8, "Helvetica-Bold", 13)
    p.row([(X_TEXT, "Schlusssaldo", "l"), (X_SALDO, fx._swiss(end_saldo), "r")],
          8, "Helvetica-Bold", 16)
    fx.footer_marker(c)
    c.showPage()
    c.save()
    return page_total, debits, credits, end_saldo


# ================================== Treiber ===================================

def _report(filename, count, lookup_hits, debits, credits, saldo, pages):
    share = (lookup_hits / count * 100) if count else 0
    print(f"  {filename}")
    print(f"    {count:>3} Buchungen, {pages} Seiten, "
          f"Lookup-Treffer {lookup_hits} ({share:.0f}%)")
    print(f"    Belastungen {fx._swiss(debits)}, Gutschriften {fx._swiss(credits)}, "
          f"Schlusssaldo {fx._swiss(saldo)}")


def generate_lara(months, as_of, seed):
    print("Lara Bieri -- PostFinance")
    fx.OUT = OUT_DIR
    fx.AUTHOR = AUTHOR
    fx.HOLDER = LARA_HOLDER
    fx.POST_IBAN_SPACED = LARA_IBAN_SPACED
    fx.POST_ACCOUNT_NO = LARA_ACCOUNT_NO
    _assert_out_dir()

    patterns = fx._lookup_patterns()
    saldo = LARA_START_SALDO
    written = []
    for y, m in months:
        rng = random.Random(f"{seed}-lara-{y}-{m}")
        last = _last_day(y, m)
        max_day = as_of.day if (y, m) == (as_of.year, as_of.month) else last
        end_day = min(max_day, last)
        rows = _lara_month(rng, y, m, max_day)
        totals = fx._post_chain(rows, saldo, patterns)
        yy = f"{y % 100:02d}"
        meta = {"period": f"01.{m:02d}.{y} - {end_day:02d}.{m:02d}.{y}",
                "created": f"{end_day:02d}.{m:02d}.{y}",
                "start_stamp": f"01.{m:02d}.{yy}",
                "end_stamp": f"{end_day:02d}.{m:02d}.{yy}"}
        filename = f"PostFinance_Kontoauszug_Lara_{y}-{m:02d}.pdf"
        pages = fx._post_write(filename, meta, rows, saldo, totals)
        debits, credits, saldo = totals
        assert saldo > 0, f"{filename}: Saldo ins Minus ({saldo})"
        _report(filename, len(rows), sum(1 for r in rows if r["via_lookup"]),
                debits, credits, saldo, pages)
        written.append(filename)
    return written


def generate_marc(months, as_of, seed):
    print("Marc Steiner -- Raiffeisen")
    patterns = fx._lookup_patterns()
    saldo = MARC_START_SALDO
    written = []
    for y, m in months:
        rng = random.Random(f"{seed}-marc-{y}-{m}")
        last = _last_day(y, m)
        max_day = as_of.day if (y, m) == (as_of.year, as_of.month) else last
        end_day = min(max_day, last)
        raw = _marc_month(rng, y, m, max_day)

        start = saldo
        rows = []
        hits = 0
        for day, text, betrag, credit in raw:
            saldo = saldo + betrag if credit else saldo - betrag
            rows.append((f"{day:02d}.{m:02d}.{y}", text, betrag, saldo, credit))
            if fx._matches_lookup(text, patterns):
                hits += 1

        filename = f"Raiffeisen_Kontoauszug_Marc_{y}-{m:02d}.pdf"
        period = f"01.{m:02d}.{y} - {end_day:02d}.{m:02d}.{y}"
        pages, debits, credits, saldo = _raiffeisen_write(
            filename, MARC_HOLDER, MARC_IBAN, period, rows, start)
        assert saldo > 0, f"{filename}: Saldo ins Minus ({saldo})"
        _report(filename, len(rows), hits, debits, credits, saldo, pages)
        written.append(filename)
    return written


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    parser.add_argument("--end-month", metavar="YYYY-MM",
                        help="Letzter erzeugter Monat (Default: laufender Monat)")
    parser.add_argument("--as-of", metavar="YYYY-MM-DD",
                        help="Stichtag, bis zu dem der letzte Auszug reicht "
                             "(Default: heute)")
    parser.add_argument("--months", type=int, default=6,
                        help="Anzahl Auszuege je Persona (Default: 6, Issue verlangt >= 3)")
    args = parser.parse_args()

    as_of = date.fromisoformat(args.as_of) if args.as_of else date.today()
    if args.end_month:
        y, m = (int(p) for p in args.end_month.split("-"))
    else:
        y, m = as_of.year, as_of.month
    if (y, m) != (as_of.year, as_of.month):
        # Ein vollstaendig vergangener Endmonat: der letzte Auszug reicht dann
        # bis zum Monatsende, nicht bis heute.
        as_of = date(y, m, _last_day(y, m))
    if args.months < 3:
        raise SystemExit("--months darf nicht unter 3 liegen (#258 verlangt mind. 3)")

    months = _months_back((y, m), args.months)
    os.makedirs(OUT_DIR, exist_ok=True)

    print(f"Ausgabe: {OUT_DIR}")
    print(f"Monate:  {months[0][0]}-{months[0][1]:02d} bis {months[-1][0]}-{months[-1][1]:02d} "
          f"(letzter Auszug bis {as_of.isoformat()})\n")

    written = generate_lara(months, as_of, "INFRA-38")
    print()
    written += generate_marc(months, as_of, "INFRA-38")

    print(f"\n{len(written)} Auszuege geschrieben.")
    print("Login-Daten und Import: backend/tools/seed_demo_accounts.sh")


if __name__ == "__main__":
    main()
