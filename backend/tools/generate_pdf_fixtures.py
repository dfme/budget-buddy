#!/usr/bin/env python3
"""Generate synthetic Swiss bank statement PDFs (Peter Muster) as parser test fixtures.

Regenerates the fixtures in src/test/resources/pdf/ used by
SwissBankStatementParserFixtureTest (BE-PDF-01),
PdfImportLargeStatementIntegrationTest (BE-PDF-09) and
PdfLookupCoverageIntegrationTest (ADR-6). All statements are fully
synthetic: fictional holder "Peter Muster", example IBAN
CH93 0076 2011 6238 5295 7, balance-consistent transaction chains so that
SwissBankStatementParser's saldo-delta direction logic resolves correctly.
Booking texts use recognizable Swiss merchants (Migros, Coop, SBB, Swisscom,
CSS, ...) so the fixtures also exercise categorization (US-05).

Each fixture answers one question the others cannot:

    Post_kontoauszug.pdf                  13 bookings, BE-PDF-01 base cases
    Post_Kontoauszug_2026_Juli_20_Buch..  20 bookings, 3 pages, structural edge
                                          cases; hand-written so every case
                                          stays reviewable
    Post_Kontoauszug_2025_240_Buchungen   240 bookings over a full calendar year:
                                          volume plus the lookup/Claude ratio,
                                          built to 60% (ADR-6/ADR-14)
    UBS_Konto_Bewegungen_2021_Juli.pdf    descending order, 2 pages, 28 bookings
    Kreditkarten Rechnung *.pdf           Viseca layout, foreign currency
    Raiffeisen_..._110_Buchungen.pdf      length: the generic branch at #192 size

NOT generated here: "Kontoauszug 01.06.2026 - 30.06.2026 - CH93... .pdf" is a REAL
Raiffeisen statement, anonymised in place by anonymize_raiffeisen_statement.py. It
covers what a generator cannot -- the bank's own typesetting. The generated
Raiffeisen fixture above prints "01.06.2026", the real one "01.06.26", and the
generic branch used to accept only the first (BE-PDF-12). Keep both.

ALL THREE PostFinance statements use the REAL PostFinance typesetting: the
booking line carries the payment TYPE ("LASTSCHRIFT", "GOOGLE PAY"), and the
merchant sits in the detail lines below it, buried under card number,
counterparty IBAN, postal address and label lines. That is what a real statement
looks like, and it is the only form in which a lookup hit proves anything: it
must have survived DETAIL_NOISE and MAX_DETAIL_LINES to get there. Fixtures that
put the merchant in the booking line measure the generator's choice of words
instead of the parser.

The block builders (_post_card, _post_lsv, _post_giro_in, ...) each declare which
of their printed lines the parser keeps. That split is what makes the 60% figure
honest -- otherwise the generator would count hits in lines DETAIL_NOISE discards.
Whether the claim holds is not checked here but in
PdfLookupCoverageIntegrationTest, against the real parser and the real seeds.

IMPORTANT: The fixture test asserts the exact printed totals (Total/Umsatztotal
lines). If you change any amount here, keep every balance chain consistent and
update the assertions in SwissBankStatementParserFixtureTest accordingly. The
240-booking statement additionally pins its 60% lookup share in
PdfLookupCoverageIntegrationTest -- the generator recomputes that share against
V04__create_category_lookup_table.sql on every run and refuses to write a
fixture that drifted out of the target band.

NOTE: reportlab stamps a creation date into every PDF, so a run rewrites ALL
fixtures byte-wise even when their content is unchanged. Restore the files you
did not mean to touch before committing (git checkout -- <file>), otherwise the
PR carries unreviewable binary diffs.

Usage:
    pip install reportlab
    python3 backend/tools/generate_pdf_fixtures.py
"""

import os
import re
from decimal import Decimal

from reportlab.lib.pagesizes import A4
from reportlab.pdfgen import canvas

W, H = A4
OUT = os.path.normpath(
    os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "test", "resources", "pdf")
)

os.makedirs(OUT, exist_ok=True)

# Fiktiver Kontoinhaber — einzige Quelle für alle drei Layouts.
HOLDER = ["Peter Muster", "Musterweg 14", "8000 Zürich"]
AUTHOR = "BudgetBuddy Test Fixture (synthetic)"


class Page:
    """Simple top-down line writer; every logical statement row is one baseline."""

    def __init__(self, c, top=H - 50):
        self.c = c
        self.y = top

    def text(self, x, s, size=8, font="Helvetica", dy=11):
        self.c.setFont(font, size)
        self.c.drawString(x, self.y, s)
        self.y -= dy

    def row(self, parts, size=8, font="Helvetica", dy=11):
        """parts: list of (x, text, align) drawn on the SAME baseline -> one extracted line."""
        self.c.setFont(font, size)
        for x, s, align in parts:
            if align == "r":
                self.c.drawRightString(x, self.y, s)
            else:
                self.c.drawString(x, self.y, s)
        self.y -= dy

    def gap(self, dy=8):
        self.y -= dy


def footer_marker(c):
    c.setFont("Helvetica", 5)
    c.setFillGray(0.55)
    c.drawString(40, 20, "Synthetische Testdaten - BudgetBuddy Fixture (BE-PDF-01)")
    c.setFillGray(0)



# ================================ PostFinance =================================
# Drei Auszüge, ein Satzbild. Alle Post_*-Fixtures bilden das ECHTE
# PostFinance-Layout nach, nachgezogen an einem realen Auszug:
#
#   * Die Buchungszeile trägt die ZAHLUNGSART, nicht den Händler:
#         "GOOGLE PAY            42.50  03.07.26   1 655.50"
#     Wer bezahlt wurde, steht in den Detailzeilen darunter — verschüttet unter
#     Kartennummer, Gegenpartei-IBAN, Postanschrift und Label-Zeilen.
#   * Gutschrift und Lastschrift sind ZWEI Spalten.
#   * Folgeseiten wiederholen nur Datum, IBAN und Kontonummer — keinen Übertrag.
#   * Der Fuss trägt neben der Seitenzahl eine Drucksteuerzeile.
#
# Warum das zählt: In dieser Form hängt jeder Lookup-Treffer und jeder brauchbare
# Claude-Prompt daran, dass die sprechende Detailzeile den Weg durch das Rauschen
# und durch MAX_DETAIL_LINES überlebt. Fixtures, die den Händler in die
# Buchungszeile schreiben, testen den bequemen Fall und messen am Ende die
# Textwahl des Generators statt das Verhalten des Parsers.
#
# Die drei Auszüge teilen sich Renderer und Blockbauer und unterscheiden sich nur
# in Umfang und Zweck:
#
#   postfinance()        13 Buchungen, 1 Seite   — BE-PDF-01, Grundfälle
#   postfinance_juli()   20 Buchungen, 3 Seiten  — Strukturfälle, handgeschrieben
#   post_year()         240 Buchungen, Jahr      — Menge + Lookup-Quote (ADR-6)

# Spaltenraster. Gutschrift (X_CREDIT) und Lastschrift (X_DEBIT) sind getrennt;
# im extrahierten Text stehen beide zwischen Text und Valuta, die Zeilenform ist
# also identisch — sichtbar wird der Unterschied nur im PDF.
POST_X_DATE, POST_X_TEXT, POST_X_CREDIT, POST_X_DEBIT, POST_X_VAL, POST_X_SALDO = (
    40, 95, 330, 390, 410, 545)
POST_ROW_DY, POST_DETAIL_DY, POST_ROW_GAP, POST_ROW_FLOOR = 13, 10, 6, 62

# Maskierte Kartennummer, wie PostFinance sie druckt — OHNE Leerzeichen vor den
# Klarziffern. Genau diese Schreibweise liess der frühere Filter `\bXXXX\b`
# durch, weil zwischen X und Ziffer keine Wortgrenze steht.
POST_CARD_NUMBER = "KARTEN NR. XXXX4417"

# Spiegelt SwissBankStatementParser.MAX_DETAIL_LINES. Die Blockbauer unten
# behaupten, welche ihrer Zeilen der Parser behält; mehr als diese drei kann er
# gar nicht behalten, und ein Block, der mehr Signal mitbringt, wäre ein
# Denkfehler in der Fixture statt eine Eigenschaft des Parsers.
POST_MAX_DETAIL_LINES = 3


def _post_amount(amount):
    """Decimal -> 4 250.00 — PostFinance trennt Tausender mit Leerzeichen."""
    return _swiss(amount, sep=" ")


def _lookup_patterns():
    """Liest die Seed-Patterns aus V04__create_category_lookup_table.sql.

    Bewusst aus der Migration statt aus einer Kopie hier im Skript: Eine Kopie
    wäre am Tag richtig, an dem sie geschrieben wird, und danach nie wieder
    nachweisbar. Fehlt die Datei, bricht der Lauf ab — eine stillschweigend
    leere Pattern-Liste würde jede Buchung als "unbekannt" zählen und die
    Quotenprüfung wertlos machen.
    """
    sql_path = os.path.normpath(os.path.join(
        os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources",
        "db", "migration", "V04__create_category_lookup_table.sql"))
    with open(sql_path, encoding="utf-8") as f:
        sql = f.read()
    # ('MCDONALD''S', 'Restaurant') — doppelte Apostrophe sind SQL-Escapes. Nur das
    # Pattern zählt hier; welche Kategorie es trägt, ist für die Quote gleichgültig.
    patterns = re.findall(r"\('((?:[^']|'')*)'\s*,\s*'(?:[^']|'')*'\)", sql)
    if not patterns:
        raise SystemExit(f"Keine Seed-Patterns in {sql_path} gefunden")
    return [p.replace("''", "'").upper() for p in patterns]


def _matches_lookup(full_text, patterns):
    """Spiegelt CategoryLookupRepository.findMatching: Pattern als Teilstring, case-insensitiv."""
    upper = full_text.upper()
    return any(pattern in upper for pattern in patterns)


# --- Detailblöcke -------------------------------------------------------------
# Jeder Bauer liefert (gedruckt, bleibend): alle Zeilen, die ins PDF gehen, und
# jene, die der Parser davon behält. Die Trennung ist der Grund, warum die
# Lookup-Quote überhaupt ehrlich gerechnet werden kann — sonst zählte der
# Generator Treffer in Zeilen mit, die DETAIL_NOISE längst verwirft.
# Ob die Behauptung stimmt, prüft nicht dieses Skript, sondern
# PdfLookupCoverageIntegrationTest gegen den echten Parser.

def _post_card(merchant, city, card_date):
    """Kartenzahlung, wenn die Buchungszeile schon KAUF/DIENSTLEISTUNG heisst."""
    return ((card_date, POST_CARD_NUMBER, merchant, city), (merchant, city))


def _post_wallet(merchant, city, card_date):
    """Kartenzahlung über TWINT oder Google Pay: Die Buchungszeile trägt das
    Wallet, die Transaktionsart rutscht in die erste Detailzeile."""
    return (("KAUF/DIENSTLEISTUNG VOM", card_date, POST_CARD_NUMBER, merchant, city),
            ("KAUF/DIENSTLEISTUNG VOM", merchant, city))


def _post_online(merchant, card_date, payment_id, order_no):
    """Online-Kauf — der längste Block des Layouts (acht Zeilen). Platzhalter und
    die Referenzen hinter ihren Labels tragen nichts zur Kategorie bei."""
    return ((card_date, POST_CARD_NUMBER, merchant, "N/A",
             "PAYMENT ID", payment_id, "BESTELLNUMMER", order_no),
            (merchant,))


def _post_lsv(iban, company, street, town, purpose=None):
    """Lastschrift mit Firmenanschrift. Strasse und Ort belegten vor dem
    Adressfilter zwei der drei Plätze und verdrängten den Zweck."""
    printed = (iban, company, street, town)
    surviving = (company,)
    if purpose:
        printed += (purpose,)
        surviving += (purpose,)
    return (printed, surviving)


def _post_giro_out(iban, name, purpose):
    """Überweisung nach aussen: Empfänger-IBAN, Empfänger, Zweck."""
    return ((iban, name, purpose), (name, purpose))


def _post_giro_in(iban, name, street, town, purpose):
    """Eingang: Absenderblock mit Privatanschrift, Zweck erst hinter
    MITTEILUNGEN:. Ohne Adress- und Labelfilter kommt beim Prompt ein Klarname
    samt Wohnadresse an — und kein Verwendungszweck (BE-PDF-06)."""
    return ((iban, "ABSENDER:", name, street, town, "MITTEILUNGEN:", purpose), (name, purpose))


def _post_dauerauftrag(order_no, iban, name, purpose):
    """Dauerauftrag. Der Fall, an dem MAX_DETAIL_LINES die einzige sprechende
    Zeile abschnitt: Auftragsnummer, IBAN und das Label belegten die drei
    Plätze, der Zweck darunter fiel weg."""
    return ((f"DAUERAUFTRAG: {order_no}", iban, name, "SENDER REFERENZ:", purpose),
            (name, purpose))


def _post_plain(*lines):
    """Block ohne Rauschen — alles, was gedruckt wird, bleibt auch."""
    return (lines, lines)


def _post_row(date, text, amount, valuta, block, saldo=True, credit=False):
    """Eine Buchungszeile samt Detailblock.

    ``date=None`` druckt kein Buchungsdatum (zweite Buchung desselben Tages),
    ``saldo=False`` keinen Saldo (Tagesblock, den erst die nächste Zeile
    abschliesst).
    """
    printed, surviving = block
    assert len(surviving) <= POST_MAX_DETAIL_LINES, \
        f"{text}: {len(surviving)} bleibende Detailzeilen, Parser behält höchstens " \
        f"{POST_MAX_DETAIL_LINES}"
    return {
        "date": date, "text": text, "betrag": Decimal(amount), "valuta": valuta,
        "print_saldo": saldo, "is_credit": credit,
        "details": printed, "surviving": surviving,
    }


def _post_chain(rows, start_saldo, patterns):
    """Rechnet Saldokette, Totale und Lookup-Treffer über die Zeilen."""
    saldo = start_saldo
    debits = Decimal("0.00")
    credits = Decimal("0.00")
    for r in rows:
        if r["is_credit"]:
            saldo += r["betrag"]
            credits += r["betrag"]
        else:
            saldo -= r["betrag"]
            debits += r["betrag"]
        r["saldo"] = saldo if r["print_saldo"] else None
        # Der Lookup sieht buchungstext + die BLEIBENDEN Detailzeilen, nicht die
        # gedruckten. Alles andere hat DETAIL_NOISE vorher entfernt.
        r["via_lookup"] = _matches_lookup(" ".join([r["text"], *r["surviving"]]), patterns)
    return debits, credits, saldo


def _post_render(c, meta, rows, start_saldo, totals, page_total):
    """Zeichnet einen PostFinance-Auszug und liefert die Anzahl Seiten.

    Wird zweimal aufgerufen: einmal auf ein Wegwerf-Canvas, nur um die Seitenzahl
    zu ermitteln, danach mit dieser Zahl für die echte Datei. Der Umweg ersetzt
    die Höhenarithmetik, mit der raiffeisen() seine Seitenzahl vorab schätzt —
    dort muss jede Layoutänderung von Hand nachgezogen werden, hier zählt das
    Layout sich selbst.
    """
    debits, credits, end_saldo = totals

    def column_header(p):
        p.row([(POST_X_DATE, "Datum", "l"), (POST_X_TEXT, "Text", "l"),
               (POST_X_CREDIT, "Gutschrift", "r"), (POST_X_DEBIT, "Lastschrift", "r"),
               (POST_X_VAL, "Valuta", "l"), (POST_X_SALDO, "Saldo", "r")],
              8, "Helvetica-Bold", 13)

    def full_header(p):
        p.text(40, "PostFinance AG", 10, "Helvetica-Bold")
        p.text(40, "Sie werden betreut von")
        p.text(40, "Kundendienst und Team")
        p.text(40, "Telefon +41 848 888 710")
        p.text(40, "www.postfinance.ch")
        p.gap()
        for line in HOLDER:
            p.text(300, line)
        p.gap(12)
        p.text(40, "Privatkonto", 10, "Helvetica-Bold")
        p.text(40, f"Kontoauszug {meta['period']}")
        p.text(40, f"IBAN {POST_IBAN_SPACED} CHF")
        p.text(40, f"Kontonummer {POST_ACCOUNT_NO}")
        p.text(40, "BIC POFICHBEXXX")
        p.text(40, f"Datum {meta['created']}")
        p.gap(10)
        column_header(p)

    def slim_header(p):
        """Folgeseiten. Der reale Auszug wiederholt nur diese drei Zeilen — und
        druckt insbesondere KEINE Übertragszeile."""
        p.text(40, f"Datum {meta['created']}")
        p.text(40, f"IBAN {POST_IBAN_SPACED}")
        p.text(40, f"Kontonummer {POST_ACCOUNT_NO}")
        p.gap(10)
        column_header(p)

    def page_footer(page_no):
        c.setFont("Helvetica", 7)
        c.drawRightString(W - 40, 34, f"Seite {page_no} / {page_total}")
        # Drucksteuerzeile des realen Auszugs. Sie trägt ein betragsähnliches
        # Token und steht im extrahierten Text hinter der Seitennummer.
        c.setFont("Helvetica", 6)
        c.drawString(40, 24, "00656 DE   000036.00")
        footer_marker(c)

    def draw(p, r):
        amount = _post_amount(r["betrag"])
        column = POST_X_CREDIT if r["is_credit"] else POST_X_DEBIT
        # Reisst der Text in die Betragsspalte, setzt PDFBox beim Extrahieren
        # kein Leerzeichen zwischen Text und Betrag — die Zeile passt dann nicht
        # mehr auf POST_ROW und die Buchung verschwindet lautlos.
        text_right = POST_X_TEXT + c.stringWidth(r["text"], "Helvetica", 8)
        assert column - c.stringWidth(amount, "Helvetica", 8) - text_right >= 6, \
            f"Buchungstext zu lang: {r['text']!r}"
        assert p.y >= POST_ROW_FLOOR, "Buchungszeile zu nah an der Fusszeile"
        parts = []
        if r["date"]:
            parts.append((POST_X_DATE, r["date"], "l"))
        parts.append((POST_X_TEXT, r["text"], "l"))
        parts.append((column, amount, "r"))
        parts.append((POST_X_VAL, r["valuta"], "l"))
        if r["saldo"] is not None:
            parts.append((POST_X_SALDO, _post_amount(r["saldo"]), "r"))
        p.row(parts, dy=POST_ROW_DY)
        for d in r["details"]:
            p.text(POST_X_TEXT, d, 7, dy=POST_DETAIL_DY)
        p.gap(POST_ROW_GAP)

    # Tagesblöcke ohne Zwischensaldo dürfen nicht auf zwei Seiten fallen: Die
    # abschliessende Zeile trägt den Saldo des ganzen Blocks.
    units = []
    for r in rows:
        if units and units[-1][-1]["saldo"] is None:
            units[-1].append(r)
        else:
            units.append([r])

    page_no = 1
    p = Page(c)
    full_header(p)
    p.row([(POST_X_DATE, meta["start_stamp"], "l"), (POST_X_TEXT, "Kontostand", "l"),
           (POST_X_SALDO, _post_amount(start_saldo), "r")], dy=13)
    for unit in units:
        needed = sum(POST_ROW_DY + POST_DETAIL_DY * len(u["details"]) + POST_ROW_GAP
                     for u in unit)
        if p.y - needed < POST_ROW_FLOOR:
            page_footer(page_no)
            c.showPage()
            page_no += 1
            p = Page(c)
            slim_header(p)
        for u in unit:
            draw(p, u)

    if p.y - 80 < POST_ROW_FLOOR:
        page_footer(page_no)
        c.showPage()
        page_no += 1
        p = Page(c)
        slim_header(p)
    p.gap(3)
    p.row([(POST_X_TEXT, "Total", "l"), (POST_X_CREDIT, _post_amount(credits), "r"),
           (POST_X_DEBIT, _post_amount(debits), "r")], 8, "Helvetica-Bold", 13)
    p.row([(POST_X_DATE, meta["end_stamp"], "l"), (POST_X_TEXT, "Kontostand", "l"),
           (POST_X_SALDO, _post_amount(end_saldo), "r")], 8, "Helvetica-Bold", 16)
    p.gap(4)
    p.text(40, "Bitte überprüfen Sie den Kontoauszug. Ohne Ihren Gegenbericht innert 30 Tagen "
               "gilt er als genehmigt.", 7, dy=9)
    p.text(40, "Freundliche Grüsse", 7, dy=9)
    p.text(40, "PostFinance AG", 7, dy=11)
    p.text(40, "Auskunft darüber, wie PostFinance Ihre Personendaten bearbeitet, erhalten Sie "
               "in unserer Allgemeinen", 7, dy=9)
    p.text(40, "Datenschutzerklärung, welche Sie unter  postfinance.ch/dse finden.", 7, dy=9)
    page_footer(page_no)
    c.showPage()
    return page_no


def _post_write(filename, meta, rows, start_saldo, totals):
    """Trockenlauf für die Seitenzahl, danach die echte Datei."""
    pages = _post_render(canvas.Canvas(os.devnull, pagesize=A4), meta, rows,
                         start_saldo, totals, 0)
    c = canvas.Canvas(os.path.join(OUT, filename), pagesize=A4)
    c.setTitle("Kontoauszug")
    c.setAuthor(AUTHOR)
    assert _post_render(c, meta, rows, start_saldo, totals, pages) == pages
    c.save()
    return pages

# Eigenes Konto und Gegenparteien — frei erfunden wie alle Fixture-Daten.
POST_IBAN_SPACED = "CH11 0900 0000 8500 1234 5"
POST_ACCOUNT_NO = "85-1234-5"
# Gegenpartei-IBANs druckt PostFinance OHNE Leerzeichen.
POST_CP_MIETE = "CH4409000000850012345"
POST_CP_PRIVAT = "CH4409000000850067890"
POST_CP_FIRMA = "CH8830000008500011122"
POST_CP_SPAR = "CH4409000000850099999"


def _post_esr(iban, name):
    """Einzahlungsschein / Übertrag: nur Gegenpartei-IBAN und Empfänger."""
    return ((iban, name), (name,))


# ---------------------- 13 Buchungen, September 2019 -------------------------
# Die älteste PostFinance-Fixture (BE-PDF-01). Beträge und Saldokette sind
# unverändert — sie stehen als gedruckte Totale in den Testassertions; umgestellt
# wurde nur das Satzbild: Zahlungsart in der Buchungszeile, Händler in den
# Detailzeilen.

def postfinance():
    patterns = _lookup_patterns()
    start = Decimal("12345.60")
    rows = [
        _post_row("02.09.19", "GIRO POST", "850.00", "02.09.19",
                  _post_giro_out(POST_CP_MIETE, "Muster Immobilien AG", "MIETE SEPTEMBER 2019")),
        _post_row("04.09.19", "KAUF/DIENSTLEISTUNG", "45.80", "03.09.19",
                  _post_card("MIGROS M BERN", "BERN (CH)", "03.09.2019")),
        _post_row("05.09.19", "GIRO INTERNATIONAL", "230.45", "05.09.19",
                  _post_giro_out(POST_CP_FIRMA, "Amazon EU S.a.r.l.", "Luxembourg")),
        _post_row("06.09.19", "LASTSCHRIFT", "65.00", "06.09.19",
                  _post_lsv(POST_CP_FIRMA, "SWISSCOM (SCHWEIZ) AG", "ALTE TIEFENAUSTRASSE 6",
                            "3050 BERN", "RECHNUNG 08-2019")),
        # Gutschrift mit Leerzeichen-Tausendertrenner (4 589.10).
        _post_row("09.09.19", "GUTSCHRIFT", "4589.10", "09.09.19",
                  _post_giro_in(POST_CP_PRIVAT, "Muster Consulting GmbH", "Bahnhofstrasse 1",
                                "8000 Zürich", "SPESEN AUGUST 2019"), credit=True),
        _post_row("11.09.19", "LASTSCHRIFT", "320.50", "11.09.19",
                  _post_lsv(POST_CP_FIRMA, "CSS VERSICHERUNG AG", "TRIBSCHENSTRASSE 21",
                            "6002 LUZERN", "PRAEMIE SEPTEMBER 2019")),
        _post_row("13.09.19", "KAUF/DIENSTLEISTUNG", "120.00", "12.09.19",
                  _post_card("SBB CFF FFS BERN", "BERN (CH)", "12.09.2019")),
        _post_row("16.09.19", "ESR", "78.50", "16.09.19",
                  _post_esr(POST_CP_FIRMA, "Stadtwerke Bern")),
        _post_row("18.09.19", "TWINT", "28.65", "17.09.19",
                  _post_wallet("COOP-4321 BERN", "BERN (CH)", "17.09.2019")),
        _post_row("20.09.19", "KONTOÜBERTRAG AUF", "500.00", "20.09.19",
                  _post_esr(POST_CP_SPAR, "SPARKONTO RUECKLAGE")),
        _post_row("25.09.19", "GUTSCHRIFT", "2400.00", "25.09.19",
                  _post_giro_in(POST_CP_FIRMA, "Immo Verwaltung AG", "Bahnhofstrasse 1",
                                "8000 Zürich", "RÜCKZAHLUNG KAUTION"), credit=True),
        # Gemischter Tagesblock am 30.09.: Gutschrift ohne Saldo, Gebührenzeile
        # ohne Datum schliesst ihn ab. Richtungen nur über das Saldo-Delta.
        _post_row("30.09.19", "GUTSCHRIFT", "5500.00", "30.09.19",
                  _post_giro_in(POST_CP_PRIVAT, "Muster Consulting GmbH", "Bahnhofstrasse 1",
                                "8000 Zürich", "LOHN SEPTEMBER 2019"), credit=True, saldo=False),
        _post_row(None, "PREIS FÜR", "5.00", "30.09.19", _post_plain("KONTOFÜHRUNG")),
    ]
    totals = _post_chain(rows, start, patterns)
    meta = {"period": "01.09.2019 - 30.09.2019", "created": "30.09.2019",
            "start_stamp": "01.09.19", "end_stamp": "30.09.19"}
    pages = _post_write("Post_kontoauszug.pdf", meta, rows, start, totals)
    _post_report("Post_kontoauszug.pdf", rows, pages, totals)


# ---------------------- 20 Buchungen, Juli 2026 ------------------------------
# Die Strukturfixture: klein, handgeschrieben, jede Zeile mit ihrem Fall im
# Kommentar. Sie misst weder Menge noch Quote — sie hält die Randfälle des
# Layouts fest, die in den beiden anderen Auszügen untergehen würden.

POST_JULI_DAUERAUFTRAG = ("90-11223344", "CH7709000000850055555", "MUSTER, LEA", "SACKGELD LEA")


def postfinance_juli():
    patterns = _lookup_patterns()
    start = Decimal("2480.00")
    da = lambda: _post_dauerauftrag(*POST_JULI_DAUERAUFTRAG)
    rows = [
        _post_row("01.07.26", "KONTOÜBERTRAG AUF", "640.00", "01.07.26",
                  _post_esr(POST_CP_MIETE, "HELSANA PRAEMIE JULI 2026")),
        # Zwei Buchungen desselben Tages: die erste ohne Saldo, die zweite ohne
        # Datum. Der Block wird über das Saldo-Delta aufgelöst.
        _post_row("02.07.26", "KONTOÜBERTRAG AUF", "118.00", "02.07.26",
                  _post_esr(POST_CP_PRIVAT, "TURNSCHUHE KIND"), saldo=False),
        _post_row(None, "KONTOÜBERTRAG AUF", "24.00", "02.07.26",
                  _post_esr(POST_CP_PRIVAT, "TRAM BILLETTE")),
        # Kartenzahlung über Wallet: Händler erst auf der vierten Detailzeile,
        # davor die maskierte Kartennummer. Zugleich Valuta ≠ Buchungsdatum.
        _post_row("04.07.26", "GOOGLE PAY", "42.50", "03.07.26",
                  _post_wallet("MIGROS M BERN WANKDORF", "BERN (CH)", "03.07.2026")),
        _post_row("06.07.26", "LASTSCHRIFT", "15.00", "06.07.26", da()),
        _post_row("09.07.26", "LASTSCHRIFT", "289.40", "09.07.26",
                  _post_lsv(POST_CP_FIRMA, "SWISSCOM (SCHWEIZ) AG", "ALTE TIEFENAUSTRASSE 6",
                            "3050 BERN"), saldo=False),
        _post_row(None, "LASTSCHRIFT", "15.00", "09.07.26", da()),
        # Der längste Block des Layouts — acht Zeilen, übrig bleibt der Händler.
        _post_row("11.07.26", "KAUF/ONLINE-SHOPPING VOM", "89.90", "09.07.26",
                  _post_online("ZALANDO SE", "09.07.2026", "250704111222333444AB",
                               "C040725R010A")),
        _post_row("14.07.26", "LASTSCHRIFT", "62.30", "14.07.26",
                  _post_lsv(POST_CP_FIRMA, "SERAFE AG", "SUMATRASTRASSE 10", "8006 ZUERICH")),
        _post_row("16.07.26", "TWINT", "24.60", "15.07.26",
                  _post_wallet("COOP-1234 BERN", "BERN (CH)", "15.07.2026")),
        _post_row("18.07.26", "GUTSCHRIFT", "180.00", "18.07.26",
                  _post_giro_in(POST_CP_PRIVAT, "MUSTER, ANNA", "MUSTERWEG 14", "8000 ZUERICH",
                                "RUECKZAHLUNG FERIENKASSE"), credit=True),
        _post_row("20.07.26", "LASTSCHRIFT", "15.00", "20.07.26", da()),
        _post_row("22.07.26", "KAUF/DIENSTLEISTUNG", "156.75", "21.07.26",
                  _post_card("DIGITEC GALAXUS AG", "ZUERICH (CH)", "21.07.2026")),
        _post_row("24.07.26", "LASTSCHRIFT", "320.50", "24.07.26",
                  _post_lsv(POST_CP_FIRMA, "CSS VERSICHERUNG AG", "TRIBSCHENSTRASSE 21",
                            "6002 LUZERN")),
        _post_row("25.07.26", "ESR", "94.20", "25.07.26",
                  _post_esr(POST_CP_FIRMA, "ENERGIE WASSER BERN")),
        _post_row("27.07.26", "KONTOÜBERTRAG AUF", "200.00", "27.07.26",
                  _post_esr(POST_CP_SPAR, "SPARKONTO RUECKLAGE")),
        # Lohn. Der Zweck bricht mitten im Wort um, wie im echten Mitteilungsfeld.
        _post_row("28.07.26", "GUTSCHRIFT", "4250.00", "28.07.26",
                  (("CH4409000000850067890", "ABSENDER:", "MUSTER CONSULTING GMBH",
                    "BAHNHOFSTRASSE 1", "8000 ZUERICH", "MITTEILUNGEN:",
                    "LOHN JULI 2026 SOWIE SPE", "SENVERGUETUNG"),
                   ("MUSTER CONSULTING GMBH", "LOHN JULI 2026 SOWIE SPE", "SENVERGUETUNG")),
                  credit=True),
        _post_row("30.07.26", "LASTSCHRIFT", "15.00", "30.07.26", da()),
        # Letzter Tagesblock: gewöhnliche Belastung ohne Saldo, abgeschlossen von
        # der kostenlosen Gebührenzeile über 0.00. Der Nullbetrag darf die
        # Richtung der 18.40 nicht mehrdeutig machen (assignDirections).
        _post_row("31.07.26", "KAUF/DIENSTLEISTUNG", "18.40", "30.07.26",
                  _post_card("BECK GLATZ CONFISEUR", "BERN (CH)", "30.07.2026"), saldo=False),
        _post_row(None, "PREIS FÜR", "0.00", "31.07.26",
                  _post_plain("KOSTENLOS FUER MITARBEITENDE")),
    ]
    totals = _post_chain(rows, start, patterns)
    meta = {"period": "01.07.2026 - 31.07.2026", "created": "01.08.2026",
            "start_stamp": "30.06.26", "end_stamp": "31.07.26"}
    name = "Post_Kontoauszug_2026_Juli_20_Buchungen.pdf"
    pages = _post_write(name, meta, rows, start, totals)
    _post_report(name, rows, pages, totals)


def _post_report(name, rows, pages, totals):
    debits, credits, end_saldo = totals
    hits = sum(1 for r in rows if r["via_lookup"])
    print(f"  {name}: {len(rows)} Buchungen auf {pages} Seiten, "
          f"Lookup {hits}/{len(rows)} ({hits / len(rows):.1%}), "
          f"Belastungen {_post_amount(debits)}, Gutschriften {_post_amount(credits)}, "
          f"Schlusssaldo {_post_amount(end_saldo)}")

# --------------------- 240 Buchungen, Kalenderjahr 2025 ----------------------
# Der Mengen- und Quotentest. Er beantwortet eine andere Frage als die beiden
# kleineren Auszüge: Wie viel eines Jahres erledigt die Lookup-Tabelle gratis,
# und wie viel muss an Claude? Gebaut auf 60% Lookup — bewusst unter den 70-80%,
# mit denen ADR-6 rechnet, damit die Claude-Stufe spürbar Last bekommt: 96
# unbekannte Transaktionen sind bei Bündelgrösse 20 zwölf Requests.
#
# Seit der Umstellung aufs echte Satzbild ist diese Quote erst aussagekräftig:
# Die zwölf Treffer pro Monat kommen aus einer DETAILZEILE, nicht aus der
# Buchungszeile. Sie messen damit, ob der Händler das Rauschen überlebt — vorher
# massen sie die Textwahl des Generators.
#
# Ein volles Kalenderjahr bedient zugleich US-08 (wiederkehrende Ausgaben) und
# US-10/US-12 (Monatsvergleich, Monatswechsel): Miete, Krankenkasse, Swisscom,
# Netflix und Spotify stehen in allen zwölf Monaten mit demselben Betrag, alles
# andere schwankt.

POST_YEAR_FILENAME = "Post_Kontoauszug_2025_240_Buchungen.pdf"
POST_YEAR_COUNT = 240
POST_YEAR_START_SALDO = Decimal("8450.00")

# Zielband der Lookup-Quote. Kein exakter Wert: Ein neuer Seed-Eintrag in V04
# darf die Fixture verschieben, ohne den Build zu brechen — aber nicht beliebig
# weit, sonst testet der Auszug nicht mehr das, wofür er gebaut wurde.
POST_YEAR_LOOKUP_TARGET = 0.60
POST_YEAR_LOOKUP_TOLERANCE = 0.05

MONTHS_DE = ["", "JANUAR", "FEBRUAR", "MAERZ", "APRIL", "MAI", "JUNI",
             "JULI", "AUGUST", "SEPTEMBER", "OKTOBER", "NOVEMBER", "DEZEMBER"]

# Rotierende Händler MIT Lookup-Treffer (Slots 15 und 17) — (Händler, Ort).
POST_YEAR_LOOKUP_POOL = [
    ("DIGITEC GALAXUS AG", "ZUERICH (CH)"),
    ("ZALANDO SE", "BERLIN (DE)"),
    ("MCDONALD'S BERN BAHNHOF", "BERN (CH)"),
    ("SALT MOBILE SA", "RENENS (CH)"),
    ("SUNRISE GMBH", "ZUERICH (CH)"),
    ("HELSANA ZUSATZVERSICHERUNG", "DUEBENDORF (CH)"),
    ("SWISS PASS VERLAENGERUNG", "BERN (CH)"),
    ("COOP PRONTO SHOP BERN", "BERN (CH)"),
    ("MIGROS TAKE AWAY", "ZUERICH (CH)"),
    ("GALAXUS OUTLET", "ZUERICH (CH)"),
]

# Rotierende Händler OHNE Lookup-Treffer (Slots 12 und 14) — sie gehen an Claude.
POST_YEAR_UNKNOWN_POOL = [
    ("RESTAURANT ROSENGARTEN", "BERN (CH)"),
    ("APOTHEKE AM MARKT", "BERN (CH)"),
    ("FITNESSCENTER AKTIV", "BERN (CH)"),
    ("ORELL FUESSLI BUCHH.", "BERN (CH)"),
    ("SERAFE AG", "ZUERICH (CH)"),
    ("BECK GLATZ CONFISEUR", "BERN (CH)"),
    ("MOBILIAR VERSICHERUNG", "BERN (CH)"),
    ("CAFE ADRIANO", "BERN (CH)"),
    ("EASYPARK SCHWEIZ GMBH", "ZUERICH (CH)"),
    ("KINO REX BERN", "BERN (CH)"),
    ("INTERDISCOUNT BERN", "BERN (CH)"),
    ("DROGERIE MUELLER", "BERN (CH)"),
]

# Deterministische Betragsschwankung statt zufälliger Zahlen: Der Generator muss
# bei jedem Lauf dieselbe Fixture liefern, sonst hängen die gedruckten Totale in
# den Tests an einem Zufallsgenerator.
POST_YEAR_VARIATIONS = [
    "0.00", "3.45", "-2.10", "7.80", "-5.25", "1.95", "-3.60", "9.15", "-1.40", "4.70",
    "-6.05", "2.30", "8.55", "-4.85", "0.60", "5.90", "-7.20", "3.15", "-0.95", "6.40",
]


def _post_year_month(month):
    """Die 20 Buchungen eines Monats.

    Zwölf davon tragen einen Händler mit Lookup-Treffer, acht nicht — daraus
    ergeben sich die 60%. Welche Slots das sind, steht bewusst nicht in einer
    Tabelle daneben: `_assert_lookup_share` rechnet es gegen die echten Seeds
    nach und bricht ab, wenn es nicht mehr stimmt.
    """
    from calendar import monthrange
    from datetime import date, timedelta

    dim = monthrange(2025, month)[1]
    rot = month - 1
    monat = MONTHS_DE[month]

    def day_of(slot):
        # Slots 0-17 gleichmässig über den Monat, aufsteigend; der letzte Tag
        # bleibt der Lohnzeile (Slot 18) vorbehalten.
        return 1 + slot * (dim - 2) // 17 if slot < 18 else dim

    def stamp(slot, back=0, long=False):
        d = date(2025, month, day_of(slot)) - timedelta(days=back)
        return d.strftime("%d.%m.%Y" if long else "%d.%m.%y")

    def amt(slot, base, vary):
        betrag = Decimal(base)
        if vary:
            betrag += Decimal(POST_YEAR_VARIATIONS[(month * 7 + slot) % len(POST_YEAR_VARIATIONS)])
        # Ein Betrag von 0.00 machte die Richtung im Saldo-Delta mehrdeutig.
        assert betrag > Decimal("0.00"), f"Slot {slot}: Betrag {betrag} nicht positiv"
        return str(betrag)

    unknown_a = POST_YEAR_UNKNOWN_POOL[rot % len(POST_YEAR_UNKNOWN_POOL)]
    unknown_b = POST_YEAR_UNKNOWN_POOL[(rot + 6) % len(POST_YEAR_UNKNOWN_POOL)]
    lookup_a = POST_YEAR_LOOKUP_POOL[rot % len(POST_YEAR_LOOKUP_POOL)]
    lookup_b = POST_YEAR_LOOKUP_POOL[(rot + 5) % len(POST_YEAR_LOOKUP_POOL)]

    # Drei Rückerstattungen im Jahr. Ohne sie wäre die Lohnzeile die einzige
    # Gutschrift, und die Richtungserkennung eines EINZELNEN Eingangs (Block der
    # Grösse 1) käme nie vor.
    if month % 4 == 0:
        slot_12 = _post_row(stamp(12), "GUTSCHRIFT", "340.00", stamp(12),
                            _post_giro_in(POST_CP_PRIVAT, "STEUERVERWALTUNG KT. BERN",
                                          "MUENSTERGASSE 3", "3011 BERN",
                                          f"RUECKERSTATTUNG {monat} 2025"), credit=True)
    else:
        slot_12 = _post_row(stamp(12), "KAUF/DIENSTLEISTUNG", amt(12, "46.80", True),
                            stamp(12, back=1), _post_card(*unknown_a, stamp(12, back=1, long=True)))

    return [
        _post_row(stamp(0), "GIRO POST", amt(0, "1250.00", False), stamp(0),
                  _post_giro_out(POST_CP_MIETE, "MUSTER IMMOBILIEN AG", f"MIETE {monat} 2025")),
        _post_row(stamp(1), "KAUF/DIENSTLEISTUNG", amt(1, "62.40", True), stamp(1, back=1),
                  _post_card("MIGROS M BERN WANKDORF", "BERN (CH)", stamp(1, back=1, long=True))),
        _post_row(stamp(2), "LASTSCHRIFT", amt(2, "65.00", False), stamp(2),
                  _post_lsv(POST_CP_FIRMA, "SWISSCOM (SCHWEIZ) AG", "ALTE TIEFENAUSTRASSE 6",
                            "3050 BERN", f"RECHNUNG {month:02d}-2025")),
        _post_row(stamp(3), "TWINT", amt(3, "48.75", True), stamp(3, back=1),
                  _post_wallet("COOP-1234 BERN", "BERN (CH)", stamp(3, back=1, long=True))),
        _post_row(stamp(4), "LASTSCHRIFT", amt(4, "320.50", False), stamp(4),
                  _post_lsv(POST_CP_FIRMA, "CSS VERSICHERUNG AG", "TRIBSCHENSTRASSE 21",
                            "6002 LUZERN", f"PRAEMIE {monat} 2025")),
        _post_row(stamp(5), "ESR", amt(5, "78.50", True), stamp(5),
                  _post_esr(POST_CP_FIRMA, "STADTWERKE BERN")),
        _post_row(stamp(6), "KAUF/DIENSTLEISTUNG", amt(6, "34.60", True), stamp(6, back=1),
                  _post_card("SBB CFF FFS BERN", "BERN (CH)", stamp(6, back=1, long=True))),
        _post_row(stamp(7), "LASTSCHRIFT", amt(7, "20.90", False), stamp(7),
                  _post_lsv(POST_CP_FIRMA, "NETFLIX INTERNATIONAL BV", "KARPERSTRAAT 12",
                            "1017 AMSTERDAM")),
        _post_row(stamp(8), "KAUF/DIENSTLEISTUNG", amt(8, "27.35", True), stamp(8, back=1),
                  _post_card("DENNER SATELLIT BERN", "BERN (CH)", stamp(8, back=1, long=True))),
        _post_row(stamp(9), "TWINT", amt(9, "14.50", True), stamp(9, back=1),
                  _post_wallet("KIOSK BAHNHOF BERN", "BERN (CH)", stamp(9, back=1, long=True))),
        _post_row(stamp(10), "GOOGLE PAY", amt(10, "52.15", True), stamp(10, back=1),
                  _post_wallet("ALDI SUISSE BERN", "BERN (CH)", stamp(10, back=1, long=True))),
        _post_row(stamp(11), "LASTSCHRIFT", amt(11, "12.95", False), stamp(11),
                  _post_lsv(POST_CP_FIRMA, "SPOTIFY AB", "REGERINGSGATAN 19", "1113 STOCKHOLM")),
        slot_12,
        _post_row(stamp(13), "KAUF/DIENSTLEISTUNG", amt(13, "41.80", True), stamp(13, back=1),
                  _post_card("LIDL SCHWEIZ BERN", "BERN (CH)", stamp(13, back=1, long=True))),
        _post_row(stamp(14), "KAUF/DIENSTLEISTUNG", amt(14, "58.90", True), stamp(14, back=1),
                  _post_card(*unknown_b, stamp(14, back=1, long=True))),
        _post_row(stamp(15), "KAUF/ONLINE-SHOPPING VOM", amt(15, "44.90", True), stamp(15, back=1),
                  _post_online(lookup_a[0], stamp(15, back=1, long=True),
                               f"25{month:02d}04111222333444AB", f"C{month:02d}0725R010A")),
        _post_row(stamp(16), "BARBEZUG", amt(16, "200.00", False), stamp(16),
                  _post_plain("POSTOMAT BAHNHOF BERN")),
        _post_row(stamp(17), "KAUF/DIENSTLEISTUNG", amt(17, "33.20", True), stamp(17, back=1),
                  _post_card(*lookup_b, stamp(17, back=1, long=True))),
        # Monatsende: Lohngutschrift ohne Saldo, Gebührenzeile ohne Datum
        # schliesst den gemischten Tagesblock ab.
        _post_row(stamp(18), "GUTSCHRIFT", amt(18, "4250.00", False), stamp(18),
                  _post_giro_in(POST_CP_PRIVAT, "MUSTER CONSULTING GMBH", "BAHNHOFSTRASSE 1",
                                "8000 ZUERICH", f"LOHN {monat} 2025"), credit=True, saldo=False),
        _post_row(None, "PREIS FÜR", amt(19, "5.00", False), stamp(18),
                  _post_plain("KONTOFÜHRUNG")),
    ]


def _assert_lookup_share(rows):
    """Bricht ab, wenn die Fixture ihr Zielband verlässt — und sagt, was trifft."""
    hits = sum(1 for r in rows if r["via_lookup"])
    share = hits / len(rows)
    low = POST_YEAR_LOOKUP_TARGET - POST_YEAR_LOOKUP_TOLERANCE
    high = POST_YEAR_LOOKUP_TARGET + POST_YEAR_LOOKUP_TOLERANCE
    if not low <= share <= high:
        sample = sorted({" ".join(r["surviving"]) for r in rows if r["via_lookup"]})
        raise SystemExit(
            f"Lookup-Quote {share:.1%} ausserhalb {low:.0%}-{high:.0%}. "
            f"Treffer aktuell: {sample}")


def post_year():
    patterns = _lookup_patterns()
    rows = [r for month in range(1, 13) for r in _post_year_month(month)]
    assert len(rows) == POST_YEAR_COUNT, f"{len(rows)} statt {POST_YEAR_COUNT} Buchungen"
    totals = _post_chain(rows, POST_YEAR_START_SALDO, patterns)
    _assert_lookup_share(rows)
    meta = {"period": "01.01.2025 - 31.12.2025", "created": "31.12.2025",
            "start_stamp": "01.01.25", "end_stamp": "31.12.25"}
    pages = _post_write(POST_YEAR_FILENAME, meta, rows, POST_YEAR_START_SALDO, totals)
    _post_report(POST_YEAR_FILENAME, rows, pages, totals)


# =================================== UBS ======================================
# Privatkonto Peter Muster, 01.01.2021 - 30.06.2021, descending (newest first).
# Anfangssaldo 5'000.00 / Schlusssaldo 18'979.60
# Umsatztotal: Belastung 26'970.40 / Gutschrift 40'950.00

UBS_ROWS_DESC = [
    # (buchungsdatum, text, betrag, valuta, saldo)
    ("28.06.2021", "Dauerauftrag", "1'600.00", "28.06.2021", "18'979.60"),
    ("25.06.2021", "Saläreingang", "6'800.00", "25.06.2021", "20'579.60"),
    ("14.06.2021", "Postüberweisung", "875.25", "14.06.2021", "13'779.60"),
    ("31.05.2021", "Saldo DL-Preisabschluss", "9.40", "31.05.2021", "14'654.85"),
    ("28.05.2021", "Dauerauftrag", "1'600.00", "28.05.2021", "14'664.25"),
    ("25.05.2021", "Saläreingang", "6'800.00", "25.05.2021", "16'264.25"),
    ("10.05.2021", "Ihr Auftrag", "12'500.00", "10.05.2021", "9'464.25"),
    ("28.04.2021", "Dauerauftrag", "1'600.00", "28.04.2021", "21'964.25"),
    ("26.04.2021", "Saläreingang", "6'800.00", "26.04.2021", "23'564.25"),
    ("15.04.2021", "TWINT Kiosk Bahnhof", "12.50", "15.04.2021", "16'764.25"),
    ("09.04.2021", "e-banking-Sammelauftrag", "1'234.55", "09.04.2021", "16'776.75"),
    ("28.03.2021", "Dauerauftrag", "1'600.00", "28.03.2021", "18'011.30"),
    ("25.03.2021", "Saläreingang", "6'800.00", "25.03.2021", "19'611.30"),
    ("15.03.2021", "Vergütung", "150.00", "15.03.2021", "12'811.30"),
    ("12.03.2021", "Kartenzahlung Coop Pronto", "23.45", "12.03.2021", "12'661.30"),
    ("08.03.2021", "Postüberweisung", "432.60", "08.03.2021", "12'684.75"),
    ("28.02.2021", "Saldo DL-Preisabschluss", "10.75", "28.02.2021", "13'117.35"),
    ("28.02.2021", "Dauerauftrag", "1'600.00", "28.02.2021", "13'128.10"),
    ("25.02.2021", "Saläreingang", "6'800.00", "25.02.2021", "14'728.10"),
    ("19.02.2021", "Kartenzahlung SBB Billettautomat", "44.00", "19.02.2021", "7'928.10"),
    ("12.02.2021", "Bezug UBS Bancomat", "200.00", "12.02.2021", "7'972.10"),
    ("08.02.2021", "LSV CSS Kranken-Versicherung", "310.20", "08.02.2021", "8'172.10"),
    ("03.02.2021", "e-banking-Auftrag", "289.90", "03.02.2021", "8'482.30"),
    ("28.01.2021", "Dauerauftrag", "1'600.00", "28.01.2021", "8'772.20"),
    ("25.01.2021", "Saläreingang", "6'800.00", "25.01.2021", "10'372.20"),
    ("18.01.2021", "LSV Swisscom AG", "89.90", "18.01.2021", "3'572.20"),
    ("12.01.2021", "Kartenzahlung Migros Zuerich", "87.60", "12.01.2021", "3'662.10"),
    ("05.01.2021", "Postüberweisung", "1'250.30", "05.01.2021", "3'749.70"),
]


def ubs():
    c = canvas.Canvas(os.path.join(OUT, "UBS_Konto_Bewegungen_2021_Juli.pdf"), pagesize=A4)
    c.setTitle("Kontobewegungen")
    c.setAuthor(AUTHOR)

    X_DATE, X_TEXT, X_AMT, X_VAL, X_SALDO = 40, 110, 400, 415, 550

    def ubs_row(p, r):
        p.row([(X_DATE, r[0], "l"), (X_TEXT, r[1], "l"), (X_AMT, r[2], "r"),
               (X_VAL, r[3], "l"), (X_SALDO, r[4], "r")], dy=13)

    def header(p, page_no):
        for line in HOLDER:
            p.text(40, line, 9)
        p.gap()
        p.text(300, "UBS Switzerland AG")
        p.text(300, "Postfach, CH-8098 Zürich")
        p.text(300, "www.ubs.com")
        p.text(300, "UBS e-banking Support +41 848 848 062")
        p.gap()
        p.text(40, "UBS Privatkonto CHF", 10, "Helvetica-Bold")
        p.text(40, "IBAN CH9300762011623852957", 9)
        p.text(40, "BIC: UBSWCHZH80A", 9)
        p.text(40, "Kontobewegungen Erstellt am 05. Juli 2021", 9, "Helvetica-Bold")
        p.text(40, "01.01.2021 - 30.06.2021", 9)
        p.text(40, "Bewertet in CHF", 8)
        p.gap(10)
        p.row(
            [(X_DATE, "Buchung", "l"), (X_TEXT, "Informationen", "l"),
             (X_AMT, "Belastung Gutschrift", "r"), (X_VAL, "Valuta", "l"), (X_SALDO, "Saldo", "r")],
            8, "Helvetica-Bold", 14)
        c.setFont("Helvetica", 7)
        c.drawString(40, 32, "Angezeigt in UBS e-banking am 05.07.2021, 20:15:33 MESZ")
        c.drawRightString(W - 40, 32, f"Seite {page_no}/2")

    p = Page(c)
    header(p, 1)
    p.row([(X_TEXT, "Schlusssaldo", "l"), (X_SALDO, "18'979.60", "r")], 8, "Helvetica-Bold", 14)
    for r in UBS_ROWS_DESC[:14]:
        ubs_row(p, r)
    footer_marker(c)
    c.showPage()

    p = Page(c)
    header(p, 2)
    for r in UBS_ROWS_DESC[14:]:
        ubs_row(p, r)
    p.gap(4)
    p.row([(X_TEXT, "Anfangssaldo", "l"), (X_SALDO, "5'000.00", "r")], 8, "Helvetica-Bold", 13)
    p.row([(X_TEXT, "Umsatztotal", "l"), (X_AMT, "26'970.40", "r"), (X_SALDO, "40'950.00", "r")],
          8, "Helvetica-Bold", 16)
    p.gap(6)
    p.text(40, "Dieser Ausdruck hat lediglich informativen Charakter und darf nicht für offizielle Zwecke", 7, dy=9)
    p.text(40, "verwendet werden. Im Falle von Abweichungen ist Ihr ordentlicher Kontoauszug massgeblich.", 7, dy=9)
    footer_marker(c)
    c.showPage()
    c.save()


# ============================ Viseca / Raiffeisen =============================

def viseca(filename, abrechnung_datum, last_total, payment_dates, rows, total):
    c = canvas.Canvas(os.path.join(OUT, filename), pagesize=A4)
    c.setTitle("Kartenabrechnung")
    c.setAuthor(AUTHOR)
    p = Page(c)

    p.text(40, "P.P. CH-8050 Zürich Post CH AG", 7)
    p.gap()
    p.text(300, "Herausgegeben von Ihrer Raiffeisenbank")
    p.text(300, "Viseca Payment Services SA")
    p.text(300, "Hagenholzstrasse 56")
    p.text(300, "8050 Zürich")
    p.text(300, "Kundenservice Telefon: +41 (0)58 958 69 11")
    p.gap()
    p.text(40, "Herr")
    for line in HOLDER:
        p.text(40, line)
    p.gap(12)
    p.row([(40, f"Abrechnung vom {abrechnung_datum}", "l"), (250, "Globallimite CHF 5'000", "l"),
           (420, "Kontoinhaber Peter Muster", "l")], 8, "Helvetica-Bold", 12)
    p.text(40, "Kartenkontonummer 1107 5680 0232 6623", 8, "Helvetica-Bold")
    p.gap(6)
    p.row([(40, "Datum", "l"), (85, "Valuta", "l"), (130, "Details", "l"),
           (390, "Währung", "l"), (460, "Betrag", "r"), (545, "Betrag in CHF", "r")],
          8, "Helvetica-Bold", 13)

    X_D1, X_D2, X_TXT, X_FX, X_CHF, X_CR = 40, 85, 130, 460, 530, 545

    p.row([(X_D1, payment_dates[0], "l"), (X_TXT, "Totalbetrag letzte Abrechnung", "l"),
           (X_CHF, last_total, "r")], dy=11)
    p.row([(X_D1, payment_dates[0], "l"), (X_D2, payment_dates[1], "l"),
           (X_TXT, "Ihre Zahlung - Danke", "l"), (X_CHF, last_total, "r"), (X_CR, "-", "l")], dy=12)
    p.text(X_TXT, "5500 20XX XXXX 5446 Mastercard Silber,Peter Muster", 7, dy=8)
    p.text(X_TXT, "Kartenlimite CHF 5'000", 7, dy=10)

    for r in rows:
        parts = [(X_D1, r["d1"], "l"), (X_D2, r["d2"], "l"), (X_TXT, r["text"], "l")]
        if "fx" in r:
            parts.append((X_FX, r["fx"], "r"))
        parts.append((X_CHF, r["chf"], "r"))
        p.row(parts, dy=11)
        p.text(X_TXT, r["cat"], 7, dy=8)
        for extra in r.get("extra", ()):
            p.text(X_TXT, extra, 7, dy=8)
        p.gap(2)

    p.gap(5)
    p.row([(X_TXT, "Total Karte Mastercard Silber 5500 20XX XXXX 5446", "l"), (X_CHF, total, "r")],
          8, "Helvetica-Bold", 13)
    p.row([(X_TXT, "Total Rechnungsbetrag zu unseren Gunsten", "l"), (X_CHF, total, "r")],
          8, "Helvetica-Bold", 13)
    p.row([(X_TXT, "Der fällige Betrag wird Ihrem Konto CH93 0076 2011 6238 5295 7 belastet", "l"),
           (X_CHF, total, "r")], 8, "Helvetica", 16)
    p.text(40, "Einzug der Forderung erfolgt aufgrund Abtretung durch Viseca Payment Services SA (neue Gläubigerin)", 6)
    c.setFont("Helvetica", 7)
    c.drawRightString(W - 40, 32, "Seite 1/1")
    footer_marker(c)
    c.showPage()
    c.save()


APRIL_ROWS = [
    {"d1": "28.03.25", "d2": "29.03.25", "text": "Coop-2345, Zürich CH", "chf": "87.45", "cat": "Lebensmittel"},
    {"d1": "01.04.25", "d2": "02.04.25", "text": "SBB CFF FFS, Bern CH", "chf": "45.60", "cat": "Öffentlicher Verkehr"},
    {"d1": "03.04.25", "d2": "04.04.25", "text": "Spotify P123456789, Stockholm SE", "chf": "12.95", "cat": "Digitalprodukte, Filme, Musik"},
    {"d1": "06.04.25", "d2": "07.04.25", "text": "BKG*HOTEL BELLEVUE, Amsterdam NL", "fx": "EUR 250.00", "chf": "238.55",
     "cat": "Hotels",
     "extra": ["Umrechnungskurs 0.9400 vom 07.04.25 CHF 235.00", "Bearbeitungsgebühr 1.5% CHF 3.55"]},
    {"d1": "08.04.25", "d2": "09.04.25", "text": "Orell Fuessli Buchhandlung, Bern CH", "chf": "54.90", "cat": "Buchhandlungen"},
    {"d1": "10.04.25", "d2": "11.04.25", "text": "digitec Galaxus (Onlin, Zürich CH", "chf": "349.00", "cat": "Warenhäuser"},
    {"d1": "14.04.25", "d2": "15.04.25", "text": "Migros M Zuerich HB, Zürich CH", "chf": "23.80", "cat": "Lebensmittel"},
    {"d1": "16.04.25", "d2": "17.04.25", "text": "Fitnesscenter Aktiv, Zürich CH", "chf": "89.00", "cat": "Sportanlagen, Fitness"},
    {"d1": "18.04.25", "d2": "19.04.25", "text": "Netflix.com, Los Gatos NL", "chf": "20.90", "cat": "Fernsehen und Radio"},
    {"d1": "21.04.25", "d2": "22.04.25", "text": "Restaurant Rosengarten, Bern CH", "chf": "68.50", "cat": "Restaurants"},
    {"d1": "23.04.25", "d2": "24.04.25", "text": "Apotheke am Markt, Zürich CH", "chf": "35.20", "cat": "Apotheken"},
]
# April debits: 87.45+45.60+12.95+238.55+54.90+349.00+23.80+89.00+20.90+68.50+35.20 = 1'025.85

JUNI_ROWS = [
    {"d1": "26.05.25", "d2": "27.05.25", "text": "Coop-1122, Bern CH", "chf": "54.30", "cat": "Lebensmittel"},
    {"d1": "28.05.25", "d2": "29.05.25", "text": "EasyPark Schweiz GmbH, easypark.ch CH", "chf": "4.50", "cat": "Parkhäuser, Parkplätze"},
    {"d1": "01.06.25", "d2": "02.06.25", "text": "Spotify P123456789, Stockholm SE", "chf": "12.95", "cat": "Digitalprodukte, Filme, Musik"},
    {"d1": "04.06.25", "d2": "05.06.25", "text": "Zalando SE, Berlin DE", "chf": "129.90", "cat": "Bekleidung"},
    {"d1": "06.06.25", "d2": "07.06.25", "text": "Interdiscount, Bern CH", "chf": "199.90", "cat": "Elektronikgeschäfte"},
    {"d1": "08.06.25", "d2": "10.06.25", "text": "RYANAIR ABC123, Dublin IE", "fx": "EUR 89.99", "chf": "85.90",
     "cat": "Fluggesellschaften",
     "extra": ["Umrechnungskurs 0.9400 vom 10.06.25 CHF 84.60", "Bearbeitungsgebühr 1.5% CHF 1.30"]},
    {"d1": "13.06.25", "d2": "13.06.25", "text": "Netflix.com, Los Gatos NL", "chf": "20.90", "cat": "Fernsehen und Radio"},
    {"d1": "16.06.25", "d2": "17.06.25", "text": "digitec Galaxus (Onlin, Zürich CH", "chf": "78.60", "cat": "Warenhäuser"},
    {"d1": "18.06.25", "d2": "19.06.25", "text": "Cafe Adriano, Bern CH", "chf": "14.80", "cat": "Cafes, Tearooms"},
    {"d1": "20.06.25", "d2": "21.06.25", "text": "Restaurant Bahnhoefli, Zürich CH", "chf": "92.40", "cat": "Restaurants"},
    {"d1": "22.06.25", "d2": "23.06.25", "text": "Migros M Bern, Bern CH", "chf": "31.15", "cat": "Lebensmittel"},
    {"d1": "24.06.25", "d2": "25.06.25", "text": "SBB CFF FFS, Bern CH", "chf": "89.00", "cat": "Öffentlicher Verkehr"},
]
# Juni debits: 54.30+4.50+12.95+129.90+199.90+85.90+20.90+78.60+14.80+92.40+31.15+89.00 = 814.30


# =============================== Raiffeisen ===================================
# Generisches Layout (Datum Valuta Text Betrag Saldo) — der Fallback-Zweig von
# SwissBankStatementParser, für den es bisher keine Fixture gab.
#
# Dieser Auszug ist bewusst GROSS: 110 Buchungen. Er bildet den Auszug aus
# Issue #192 nach, an dem der synchrone Import zweimal ins 30-Sekunden-Budget
# lief und dabei den gesamten Import verwarf. Die kleinen Fixtures (28 bzw. 12
# Buchungen) zeigen das nicht — bei ihnen greift weder die Bündelung sichtbar
# noch ein Zeitbudget.
#
# Die Zeilen werden aus einem Muster erzeugt statt einzeln getippt: 110
# handgeschriebene Zeilen wären im Review unlesbar, und die Saldokette müsste
# von Hand konsistent gehalten werden. Erzeugt rechnet sie sich selbst aus.

RAIFFEISEN_FILENAME = "Raiffeisen_Kontoauszug_110_Buchungen.pdf"
RAIFFEISEN_COUNT = 110
RAIFFEISEN_START_SALDO = Decimal("12000.00")

# Wiederkehrende Schweizer Händler — die Fixture prüft damit zugleich die
# Lookup-Tabelle (US-05): ein Teil dieser Namen steht in den Seeds von V04.
RAIFFEISEN_MERCHANTS = [
    ("Kartenzahlung Migros M Bern", "45.60"),
    ("Kartenzahlung Coop-2001 Bern", "38.90"),
    ("Kartenzahlung SBB CFF FFS Bern", "12.40"),
    ("LSV Swisscom (Schweiz) AG", "89.90"),
    ("Kartenzahlung Denner Satellit", "27.35"),
    ("TWINT Kiosk Bahnhof", "8.50"),
    ("LSV CSS Kranken-Versicherung", "310.20"),
    ("Kartenzahlung Aldi Suisse Bern", "52.15"),
    ("Onlinekauf digitec Galaxus AG", "189.00"),
    ("Kartenzahlung Restaurant Rosengarten", "68.50"),
    ("Kartenzahlung Coop Pronto Shop", "23.45"),
    ("LSV Serafe AG Radio und TV", "27.90"),
    ("Kartenzahlung Lidl Schweiz Bern", "41.80"),
    ("Onlinekauf Zalando SE", "129.90"),
    ("Kartenzahlung Apotheke am Markt", "35.20"),
    ("Bezug Bancomat Bahnhof Bern", "200.00"),
    ("Kartenzahlung Migros M Zuerich HB", "23.80"),
    ("LSV Netflix International", "20.90"),
    ("Kartenzahlung Cafe Adriano", "14.80"),
    ("Kartenzahlung Fitnesscenter Aktiv", "89.00"),
    ("ESR Stadtwerke Bern", "78.50"),
    ("Dauerauftrag Miete Muster Immobilien AG", "1250.00"),
]

# Ohne Gutschriften liefe der Saldo ins Minus, und der Parser bekäme keinen
# einzigen Einnahmefall zu sehen. Alle 30 Buchungen entspricht bei einer Buchung
# pro Tag grob einem Monatslohn.
RAIFFEISEN_SALARY = ("Gutschrift Lohn Muster Consulting GmbH", "6800.00")
RAIFFEISEN_SALARY_EVERY = 30


def _swiss(amount, sep="'"):
    """Decimal -> 9'876.50 (Apostroph-Tausendertrenner, CLAUDE.md).

    ``sep`` ist konfigurierbar, weil PostFinance Tausender mit einem Leerzeichen
    trennt (``12 345.60``) und nicht mit einem Apostroph. Beide Zeichen stehen in
    SwissBankStatementParser.THOUSANDS_SEPARATORS.
    """
    sign = "-" if amount < 0 else ""
    digits, _, frac = f"{abs(amount):.2f}".partition(".")
    grouped = ""
    for i, ch in enumerate(digits):
        if i > 0 and (len(digits) - i) % 3 == 0:
            grouped += sep
        grouped += ch
    return f"{sign}{grouped}.{frac}"


def _raiffeisen_rows():
    """Baut die Buchungen samt selbst gerechneter Saldokette.

    Returns (rows, totals) mit rows = [(datum, text, betrag, saldo, is_credit)]
    und totals = (belastungen, gutschriften, schlusssaldo).
    """
    from datetime import date, timedelta

    rows = []
    saldo = RAIFFEISEN_START_SALDO
    debits = Decimal("0.00")
    credits = Decimal("0.00")
    day = date(2026, 1, 2)
    # Eigener Zähler für die Händlerliste: Liefe sie über den Schleifenindex,
    # fiele genau der Eintrag aus, dessen Index auf einen Lohn-Slot trifft.
    merchant = 0

    for i in range(RAIFFEISEN_COUNT):
        if i % RAIFFEISEN_SALARY_EVERY == RAIFFEISEN_SALARY_EVERY - 1:
            text, amount = RAIFFEISEN_SALARY
            betrag = Decimal(amount)
            saldo += betrag
            credits += betrag
            is_credit = True
        else:
            text, amount = RAIFFEISEN_MERCHANTS[merchant % len(RAIFFEISEN_MERCHANTS)]
            merchant += 1
            betrag = Decimal(amount)
            saldo -= betrag
            debits += betrag
            is_credit = False
        stamp = (day + timedelta(days=i)).strftime("%d.%m.%Y")
        rows.append((stamp, text, betrag, saldo, is_credit))

    return rows, (debits, credits, saldo)


def _raiffeisen_page_count(rows, row_floor):
    """Zählt die Seiten vorab, damit «Seite x/y» stimmt.

    Spiegelt die Höhenarithmetik des Layouts unten — Kopf, Saldovortrag-Zeile und
    dy=11 pro Buchung — plus die Schlussseite mit den Totalen. Eine Simulation
    statt einer Konstante, weil eine zusätzliche Kopfzeile die Zahl sonst still
    falsch machen würde.
    """
    header_height = 6 * 11 + 8 + 10 + 3 * 9 + 8 + 13  # Absender, Empfänger, Konto, Spaltenkopf
    y = H - 50 - header_height - 13  # -13 für die Saldovortrag-Zeile auf Seite 1
    pages = 1
    for _ in rows:
        if y < row_floor:
            pages += 1
            y = H - 50 - header_height
        y -= 11
    return pages + 1  # Schlussseite mit den Totalen


def raiffeisen():
    c = canvas.Canvas(os.path.join(OUT, RAIFFEISEN_FILENAME), pagesize=A4)
    c.setTitle("Kontoauszug")
    c.setAuthor(AUTHOR)

    X_DATE, X_VAL, X_TEXT, X_AMT, X_SALDO = 40, 95, 150, 450, 550
    rows, (debits, credits, end_saldo) = _raiffeisen_rows()

    def header(p, page_no, page_total):
        p.text(40, "Raiffeisenbank Musterhausen", 10, "Helvetica-Bold")
        p.text(40, "Bahnhofstrasse 1, 8000 Zürich")
        p.gap()
        p.text(300, "Herr")
        for line in HOLDER:
            p.text(300, line)
        p.gap(10)
        p.text(40, "Kontoauszug Privatkonto CHF", 10, "Helvetica-Bold")
        p.text(40, "IBAN CH9300762011623852957", 9)
        p.text(40, "01.01.2026 - 30.04.2026", 9)
        p.gap(8)
        p.row(
            [(X_DATE, "Buchung", "l"), (X_VAL, "Valuta", "l"), (X_TEXT, "Text", "l"),
             (X_AMT, "Belastung Gutschrift", "r"), (X_SALDO, "Saldo", "r")],
            8, "Helvetica-Bold", 13)
        c.setFont("Helvetica", 7)
        c.drawRightString(W - 40, 32, f"Seite {page_no}/{page_total}")

    # Seitenumbruch nach POSITION, nicht nach Zeilenzahl.
    #
    # Erst gelernt, dann so gebaut: Mit einer festen Zeilenzahl pro Seite landete
    # die 107. Buchung auf Höhe y≈13 und verschmolz beim Extrahieren mit dem
    # Fusszeilen-Marker bei y=20 zu einer Zeile. Die Zeile begann damit nicht mehr
    # mit einem Datum, GENERIC_ROW griff nicht — und der Auszug lieferte still 109
    # statt 110 Buchungen. Ein Auszug mit fünf Zeilen hätte das nie gezeigt.
    #
    # ROW_FLOOR hält jede Buchung klar über dem Marker; der assert unten macht
    # einen künftigen Layout-Fehler laut statt still.
    ROW_FLOOR = 60

    page_total = _raiffeisen_page_count(rows, ROW_FLOOR)

    page_no = 1
    p = Page(c)
    header(p, page_no, page_total)
    p.row([(X_TEXT, "Saldovortrag", "l"), (X_SALDO, _swiss(RAIFFEISEN_START_SALDO), "r")],
          8, "Helvetica-Bold", 13)

    for stamp, text, betrag, saldo, _is_credit in rows:
        if p.y < ROW_FLOOR:
            footer_marker(c)
            c.showPage()
            page_no += 1
            p = Page(c)
            header(p, page_no, page_total)
        assert p.y >= ROW_FLOOR, "Buchungszeile zu nah an der Fusszeile"
        p.row([(X_DATE, stamp, "l"), (X_VAL, stamp, "l"), (X_TEXT, text, "l"),
               (X_AMT, _swiss(betrag), "r"), (X_SALDO, _swiss(saldo), "r")], dy=11)

    footer_marker(c)
    c.showPage()

    p = Page(c)
    header(p, page_no + 1, page_total)
    p.gap(6)
    p.row([(X_TEXT, "Total Belastungen", "l"), (X_AMT, _swiss(debits), "r")],
          8, "Helvetica-Bold", 13)
    p.row([(X_TEXT, "Total Gutschriften", "l"), (X_AMT, _swiss(credits), "r")],
          8, "Helvetica-Bold", 13)
    p.row([(X_TEXT, "Schlusssaldo", "l"), (X_SALDO, _swiss(end_saldo), "r")],
          8, "Helvetica-Bold", 16)
    footer_marker(c)
    c.showPage()
    c.save()

    print(f"  {RAIFFEISEN_COUNT} Buchungen, "
          f"Belastungen {_swiss(debits)}, Gutschriften {_swiss(credits)}, "
          f"Schlusssaldo {_swiss(end_saldo)}")


if __name__ == "__main__":
    postfinance()
    postfinance_juli()
    post_year()
    ubs()
    raiffeisen()
    viseca("Kreditkarten Rechnung April 2025 - CH9300762011623852957 - 2025-04-25.pdf",
           "25.04.2025", "950.20", ("25.03.25", "26.03.25"), APRIL_ROWS, "1'025.85")
    viseca("Kreditkarten Rechnung Juni 2025 - CH9300762011623852957 - 2025-06-25.pdf",
           "25.06.2025", "1'025.85", ("23.05.25", "24.05.25"), JUNI_ROWS, "814.30")
    for f in sorted(os.listdir(OUT)):
        print(f, os.path.getsize(os.path.join(OUT, f)))
