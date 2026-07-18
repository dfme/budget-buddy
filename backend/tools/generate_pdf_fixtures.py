#!/usr/bin/env python3
"""Generate synthetic Swiss bank statement PDFs (Peter Muster) as parser test fixtures.

Regenerates the fixtures in src/test/resources/pdf/ used by
SwissBankStatementParserFixtureTest (BE-PDF-01). All statements are fully
synthetic: fictional holder "Peter Muster", example IBAN
CH93 0076 2011 6238 5295 7, balance-consistent transaction chains so that
SwissBankStatementParser's saldo-delta direction logic resolves correctly.
Booking texts use recognizable Swiss merchants (Migros, Coop, SBB, Swisscom,
CSS, ...) so the fixtures also exercise categorization (US-05).

IMPORTANT: The fixture test asserts the exact printed totals (Total/Umsatztotal
lines). If you change any amount here, keep every balance chain consistent and
update the assertions in SwissBankStatementParserFixtureTest accordingly.

Usage:
    pip install reportlab
    python3 backend/tools/generate_pdf_fixtures.py
"""

import os

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
# Privatkonto Peter Muster, 01.09.2019 - 30.09.2019, Start 12 345.60
# credits: 4 589.10 + 2 400.00 + 5 500.00 = 12 489.10
# debits:  850.00 + 45.80 + 230.45 + 65.00 + 320.50 + 120.00 + 78.50 + 28.65
#          + 500.00 + 5.00 = 2 243.90
# end: 12 345.60 + 12 489.10 - 2 243.90 = 22 590.80

def postfinance():
    c = canvas.Canvas(os.path.join(OUT, "Post_kontoauszug.pdf"), pagesize=A4)
    c.setTitle("Kontoauszug")
    c.setAuthor(AUTHOR)
    p = Page(c)

    p.text(40, "PostFinance AG", 10, "Helvetica-Bold")
    p.text(40, "Sie werden betreut von")
    p.text(40, "Kundendienst und Team")
    p.text(40, "Telefon +41 848 888 710")
    p.text(40, "www.postfinance.ch")
    p.gap()
    p.text(40, "Post CH AG")
    p.text(40, "P.P. CH-4808 Zofingen A-PRIORITY")
    p.gap()
    p.text(300, "Herr")
    for line in HOLDER:
        p.text(300, line)
    p.gap(12)
    p.text(40, "Privatkonto", 10, "Helvetica-Bold")
    p.row([(40, "Kontoauszug 01.09.2019 - 30.09.2019", "l"), (450, "Seite: 1 / 1", "l")], 9, "Helvetica-Bold")
    p.text(40, "Datum: 30.09.2019")
    p.text(40, "CHF")
    p.text(40, "IBAN CH11 0900 0000 8500 1234 5 Kontonummer 85-1234-5")
    p.text(40, "BIC POFICHBEXXX")
    p.gap(10)

    # column layout: Datum | Text | Betrag | Valuta | Saldo — one baseline per booking row
    X_DATE, X_TEXT, X_AMT, X_VAL, X_SALDO = 40, 95, 380, 400, 545
    p.row(
        [(X_DATE, "Datum", "l"), (X_TEXT, "Text", "l"), (X_AMT, "Gutschrift Lastschrift", "r"),
         (X_VAL, "Valuta", "l"), (X_SALDO, "Saldo", "r")],
        8, "Helvetica-Bold", 13)

    def booking(date, text, amount, valuta, saldo=None, details=()):
        parts = []
        if date:
            parts.append((X_DATE, date, "l"))
        parts.append((X_TEXT, text, "l"))
        parts.append((X_AMT, amount, "r"))
        parts.append((X_VAL, valuta, "l"))
        if saldo:
            parts.append((X_SALDO, saldo, "r"))
        p.row(parts, dy=10)
        for d in details:
            p.text(X_TEXT, d, 7, dy=8)
        p.gap(3)

    p.row([(X_DATE, "01.09.19", "l"), (X_TEXT, "Kontostand", "l"), (X_SALDO, "12 345.60", "r")], dy=13)

    booking("02.09.19", "GIRO POST", "850.00", "02.09.19", "11 495.60",
            ["CH63 0900 0000 2500 9779 8", "Muster Immobilien AG", "MIETE SEPTEMBER 2019"])
    booking("04.09.19", "KAUF/DIENSTLEISTUNG MIGROS M BERN", "45.80", "04.09.19", "11 449.80")
    booking("05.09.19", "GIRO INTERNATIONAL", "230.45", "05.09.19", "11 219.35",
            ["Amazon EU S.a.r.l.", "Luxembourg"])
    booking("06.09.19", "LASTSCHRIFT SWISSCOM (SCHWEIZ) AG", "65.00", "06.09.19", "11 154.35",
            ["RECHNUNG 08-2019"])
    booking("09.09.19", "GIRO AUS KONTO 25-9034-2", "4 589.10", "09.09.19", "15 743.45",
            ["ABSENDER:", "Muster Consulting GmbH", "Bahnhofstrasse 1", "8000 Zürich"])
    booking("11.09.19", "LASTSCHRIFT CSS VERSICHERUNG AG", "320.50", "11.09.19", "15 422.95",
            ["PRAEMIE SEPTEMBER 2019"])
    booking("13.09.19", "KAUF/DIENSTLEISTUNG SBB CFF FFS BERN", "120.00", "13.09.19", "15 302.95")
    booking("16.09.19", "ESR", "78.50", "16.09.19", "15 224.45",
            ["Stadtwerke Bern"])
    booking("18.09.19", "TWINT KAUF/DIENSTLEISTUNG COOP-4321", "28.65", "18.09.19", "15 195.80")
    booking("20.09.19", "E-FINANCE UEBERTRAG SPARKONTO", "500.00", "20.09.19", "14 695.80")
    booking("25.09.19", "GIRO POST", "2 400.00", "25.09.19", "17 095.80",
            ["ABSENDER:", "Immo Verwaltung AG", "RÜCKZAHLUNG KAUTION"])
    # mixed block on 30.09.: credit + debit resolved via saldo delta (+5 495.00)
    booking("30.09.19", "GUTSCHRIFT LOHN SEPTEMBER", "5 500.00", "30.09.19", None,
            ["ABSENDER:", "Muster Consulting GmbH"])
    booking(None, "PREIS FÜR KONTOFÜHRUNG", "5.00", "30.09.19", "22 590.80")

    p.gap(3)
    p.row([(X_TEXT, "Total", "l"), (X_AMT, "12 489.10", "r"), (X_SALDO, "2 243.90", "r")],
          8, "Helvetica-Bold", 13)
    p.row([(X_DATE, "30.09.19", "l"), (X_TEXT, "Kontostand", "l"), (X_SALDO, "22 590.80", "r")],
          8, "Helvetica-Bold", 16)

    p.gap(6)
    p.text(40, "Bitte überprüfen Sie den Kontoauszug. Ohne Ihren Gegenbericht innert 30 Tagen gilt er als genehmigt.", 7)
    p.text(40, "Freundliche Grüsse", 7)
    p.text(40, "PostFinance AG", 7)

    footer_marker(c)
    c.showPage()
    c.save()


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


if __name__ == "__main__":
    postfinance()
    ubs()
    viseca("Kreditkarten Rechnung April 2025 - CH9300762011623852957 - 2025-04-25.pdf",
           "25.04.2025", "950.20", ("25.03.25", "26.03.25"), APRIL_ROWS, "1'025.85")
    viseca("Kreditkarten Rechnung Juni 2025 - CH9300762011623852957 - 2025-06-25.pdf",
           "25.06.2025", "1'025.85", ("23.05.25", "24.05.25"), JUNI_ROWS, "814.30")
    for f in sorted(os.listdir(OUT)):
        print(f, os.path.getsize(os.path.join(OUT, f)))
