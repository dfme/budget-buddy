#!/usr/bin/env python3
"""Anonymise a real Raiffeisen account statement into a parser test fixture.

The other fixtures in src/test/resources/pdf/ are generated from scratch by
generate_pdf_fixtures.py. This one cannot be: its value is that it carries the
REAL Raiffeisen typesetting — multi-line booking blocks, the "(Valuta)" second
date line, "Bezahlt für:" detail lines, the Übertrag/Umsatz page break — which
a reportlab re-render would only approximate. So the original is edited in
place instead: every text run is decoded through the font's ToUnicode CMap,
replaced, and re-encoded with the SAME subsetted glyphs. Layout, fonts and
structure stay bit-for-bit what the bank produced; only the content changes.

Anonymised, in the values the other fixtures use (HOLDER/IBAN from
generate_pdf_fixtures.py):

    holder, family, private counterparties  -> Peter Muster and the Muster family
    addresses                               -> Musterweg 14, 8000 Zürich
    IBAN                                    -> CH93 0076 2011 6238 5295 7
    savings account IBAN                    -> CH66 0076 2011 6238 5295 8
    employer + salary                       -> MUSTER AG, 5'480.00
    bank branch, advisor, phone, UID, mail  -> Raiffeisenbank Musterhausen
    card / reference / depot numbers        -> round dummy numbers
    every amount touching net worth         -> scaled down, chain recomputed

Business counterparties (Helsana, Securitas, TS-Velos, Regio Energie, ...) are
deliberately KEPT: they are not personal data and they are what makes the
fixture exercise the lookup table (US-05).

Amounts: the opening balance, the savings transfer and the salary credit are
replaced; the everyday bookings keep their real values. Every running balance,
both Umsatz lines, the Übertrag and the closing balance are recomputed from
that so the saldo chain stays consistent — SwissBankStatementParser derives
debit/credit direction from the saldo delta, so an inconsistent chain would
silently produce wrong signs.

Das Original liegt bewusst NICHT im Repository und ist nach der Anonymisierung
gelöscht worden — dieses Skript lässt sich daher nicht erneut ausführen. Es
bleibt als nachvollziehbarer Nachweis dessen hier, was ersetzt wurde: Wer der
Fixture ansieht, dass sie aus einem echten Auszug stammt, kann hier Zeile für
Zeile prüfen, dass nichts Personenbezogenes stehen geblieben ist.

Usage:
    pip install pikepdf
    python3 backend/tools/anonymize_raiffeisen_statement.py <original.pdf> [out.pdf]
"""

import os
import re
import sys
from decimal import Decimal

import pikepdf

# ------------------------------------------------------------------ IBAN ----

def iban(country: str, bban: str) -> str:
    """Return the grouped IBAN for a BBAN, with correct mod-97 check digits."""
    body = bban + country + "00"
    digits = "".join(str(ord(c) - 55) if c.isalpha() else c for c in body)
    check = 98 - (int(digits) % 97)
    full = f"{country}{check:02d}{bban}"
    return " ".join(full[i:i + 4] for i in range(0, len(full), 4))


HOLDER_IBAN = iban("CH", "00762011623852957")   # = CH93 ..., the fixture IBAN
SAVINGS_IBAN = iban("CH", "00762011623852958")  # second account, same idiom

assert HOLDER_IBAN == "CH93 0076 2011 6238 5295 7", HOLDER_IBAN

# --------------------------------------------------------------- amounts ----
# Only three real values are replaced; everything else is recomputed from them
# and from the bookings that stay as they are.

OPENING = Decimal("8450.20")     # was 118'032.38
SAVINGS_TRANSFER = Decimal("1500.00")  # was 30'000.00
SALARY = Decimal("5480.00")      # was 15'079.35

# (old amount, new amount, is_credit) in statement order.
BOOKINGS = [
    ("65.50", Decimal("65.50"), False),        # Einkauf TWINT
    ("1'335.90", Decimal("1335.90"), False),   # Helsana
    ("30'000.00", SAVINGS_TRANSFER, False),    # Übertrag Sparkonto
    ("40.00", Decimal("40.00"), False),        # Securitas
    ("2'745.00", Decimal("2745.00"), False),   # TS-Velos
    ("110.00", Decimal("110.00"), False),      # Privatperson
    ("47.00", Decimal("47.00"), False),        # Geigenbauatelier
    ("170.00", Decimal("170.00"), False),      # Dauerauftrag
    ("41.51", Decimal("41.51"), False),        # Depotgebühr
    ("170.00", Decimal("170.00"), False),      # GA Weissenstein  <- page break
    ("15'079.35", SALARY, True),               # Lohn
    ("654.00", Decimal("654.00"), False),      # Dauerauftrag
    ("300.00", Decimal("300.00"), False),      # Bancomat
    ("50.00", Decimal("50.00"), False),        # TWINT
    ("210.80", Decimal("210.80"), False),      # Gemeinde
    ("79.50", Decimal("79.50"), False),        # Gemeinde
    ("603.00", Decimal("603.00"), False),      # Regio Energie
]
PAGE_BREAK_AFTER = 10  # bookings on page 1


def swiss(value: Decimal) -> str:
    s = f"{value:,.2f}".replace(",", "'")
    return s


def build_amount_map():
    """Old printed number -> new printed number, for saldi and totals."""
    saldo = OPENING
    saldi = []
    debits = credits = Decimal("0.00")
    subtotal = None
    for i, (_, amount, is_credit) in enumerate(BOOKINGS, start=1):
        saldo = saldo + amount if is_credit else saldo - amount
        if is_credit:
            credits += amount
        else:
            debits += amount
        saldi.append(saldo)
        if i == PAGE_BREAK_AFTER:
            subtotal = (debits, credits, saldo)
    return saldi, subtotal, (debits, credits, saldo)


SALDI, SUBTOTAL, TOTAL = build_amount_map()

# The real statement's numbers, in printed order, paired with the new chain.
OLD_SALDI = [
    "117'966.88", "116'630.98", "86'630.98", "86'590.98", "83'845.98",
    "83'735.98", "83'688.98", "83'518.98", "83'477.47", "83'307.47",
    "98'386.82", "97'732.82", "97'432.82", "97'382.82", "97'172.02",
    "97'092.52", "96'489.52",
]
assert len(OLD_SALDI) == len(SALDI)

# ---------------------------------------------------------- replacements ----
# Exact run text -> replacement. A run is one TJ operator, i.e. one logical
# string as the renderer emitted it; `expected` guards against silent drift.

REPLACEMENTS = [
    # --- Kontoinhaber, Adresse, IBAN -----------------------------------
    ("Daniel Wagner", "Peter Muster", 3),
    ("Dr. Rudolf Probst-Weg 15", "Musterweg 14", 1),
    ("4513 Langendorf", "8000 Zürich", 1),
    ("CH22 8080 8002 7354 5247 6", HOLDER_IBAN, 2),
    ("Übertrag auf Mitglieder Sparkonto CH14 8080 8001 4215 5648 3",
     f"Übertrag auf Mitglieder Sparkonto {SAVINGS_IBAN}", 1),

    # --- Angehörige und private Gegenparteien --------------------------
    ("Bezahlt für: Scherrer Wagner Eliane", "Bezahlt für: Muster Anna", 1),
    ("Bezahlt für: Eliane Antoinette Scherrer Wagner",
     "Bezahlt für: Anna Maria Muster", 1),
    ("Bezahlt für: Eliane Scherrer Wagner", "Bezahlt für: Anna Muster", 2),
    ("Bezahlt für: Manon Wagner", "Bezahlt für: Lena Muster", 1),
    ("Bezahlt für: Wagner Daniel", "Bezahlt für: Muster Peter", 3),
    ("Bezahlt für: Daniel Wagner Scherer", "Bezahlt für: Peter Muster", 1),
    ("Dauerauftrag Manon Wagner", "Dauerauftrag Lena Muster", 1),
    ("Dauerauftrag Daniel Wagner", "Dauerauftrag Peter Muster", 1),
    ("Dr. Rudolf-Probstweg 15, 4513 Langendorf", "Musterweg 14, 8000 Zürich", 2),
    ("Endbegünstigter: Manon", "Endbegünstigter: Lena", 1),
    ("Zahlung TWINT WAGNER, MANON", "Zahlung TWINT MUSTER, LENA", 1),
    ("Zahlung Ingrid Eggimann-Vögtlin", "Zahlung Rita Beispiel-Muster", 1),
    ("Loretostrasse 33, 4500 Solothurn", "Beispielweg 7, 4500 Solothurn", 1),
    ("Gutschein für Christine", "Gutschein für Sofia", 1),
    # Vorname eines Marktstands in der Buchungszeile.
    ("Einkauf TWINT ALPAHIRT UNTERWEGS SILVAN",
     "Einkauf TWINT ALPAHIRT UNTERWEGS", 1),

    # --- Wohngemeinde des Inhabers -------------------------------------
    ("Zahlung Einwohnergemeinde Langendorf",
     "Zahlung Einwohnergemeinde Musterhausen", 2),
    ("Schulhausstrasse 2,, 4513 Langendorf", "Schulhausstrasse 2,, 8000 Zürich", 2),
    ("Bancomat Bezug RB Bellach", "Bancomat Bezug RB Musterhausen", 1),

    # --- Arbeitgeber ----------------------------------------------------
    ("Gutschrift ADCUBUM AG", "Gutschrift MUSTER AG", 1),
    ("ZUERCHER STRASSE 464 CH 9015 ST. GALLEN",
     "BAHNHOFSTRASSE 1 CH 8000 ZUERICH", 1),

    # --- Karten-, Referenz- und Depotnummern ----------------------------
    ("Reg. Nr 30267040", "Reg. Nr 10000001", 1),
    ("Rechnungsnummer: R-2021055250 Ihre Kundennummer: 32190 ",
     "Rechnungsnummer: R-2026000001 Ihre Kundennummer: 10000 ", 1),
    ("Depotnummer 112.449.851.2", "Depotnummer 100.000.000.0", 1),
    ("28.06.2026, 08:35, Kontokarten-Nr. 21466883",
     "28.06.2026, 08:35, Kontokarten-Nr. 10000002", 1),
    ("AN03029000000000000553462215", "AN00000000000000000000000001", 2),
    ("03029", "00000", 2),

    # --- Bankstelle und Kundenberater -----------------------------------
    ("Wasseramt-Buchsi", "Musterhausen", 2),
    ("Raiffeisenbank Wasseramt-Buchsi Genossenschaft",
     "Raiffeisenbank Musterhausen Genossenschaft", 2),
    ("Geschäftsstelle Zuchwil", "Geschäftsstelle Musterhausen", 1),
    ("Hauptstrasse 75", "Bahnhofstrasse 1", 1),
    ("4528", "8000", 1),
    ("Zuchwil", "Zürich", 1),
    ("+41 32 681 45 00", "+41 44 000 00 00", 1),
    ("CHE-106.920.980", "CHE-123.456.789", 1),
    ("www.raiffeisen.ch/wasseramt-buchsi", "www.raiffeisen.ch/musterhausen", 1),
    ("wasseramt-buchsi@raiffeisen.ch", "musterhausen@raiffeisen.ch", 1),
    ("Denis Di Donato", "Thomas Beispiel", 1),
    ("+41 32 681 45 24", "+41 44 000 00 24", 1),
    ("denis.didonato@raiffeisen.ch", "thomas.beispiel@raiffeisen.ch", 1),
    ("Zuchwil, 1. Juli 2026", "Musterhausen, 1. Juli 2026", 1),

    # --- Beträge ---------------------------------------------------------
    ("118'032.38", swiss(OPENING), 1),
    ("30'000.00", swiss(SAVINGS_TRANSFER), 1),
    ("15'079.35", swiss(SALARY), 2),          # Buchung + Umsatz Gutschriften
    ("34'724.91", swiss(SUBTOTAL[0]), 2),     # Umsatz Seite 1 + Übertrag Seite 2
    ("36'622.21", swiss(TOTAL[0]), 1),        # Umsatz Belastungen
]
REPLACEMENTS += [(old, swiss(new), 2 if old == "96'489.52" else 1)
                 for old, new in zip(OLD_SALDI, SALDI)]

# "96'489.52" is printed twice (letzter Saldo + "Saldo zu Ihren Gunsten").

# ------------------------------------------------------------- PDF logic ----

RUN_RE = re.compile(
    r"BT\s*/(?P<font>F\d+)\s+(?P<size>[\d.]+)\s+Tf\s*"
    r"(?P<a>[-\d.]+) (?P<b>[-\d.]+) (?P<c>[-\d.]+) (?P<d>[-\d.]+) "
    r"(?P<x>[-\d.]+) (?P<y>[-\d.]+) Tm\s*\[<(?P<hex>[0-9A-Fa-f]+)>\]\s*TJ\s*ET"
)


def parse_tounicode(data: bytes) -> dict:
    txt = data.decode("latin-1")
    out = {}
    for blk in re.findall(r"beginbfchar(.*?)endbfchar", txt, re.S):
        for src, dst in re.findall(r"<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>", blk):
            out[int(src, 16)] = "".join(
                chr(int(dst[i:i + 4], 16)) for i in range(0, len(dst), 4))
    for blk in re.findall(r"beginbfrange(.*?)endbfrange", txt, re.S):
        for lo, hi, dst in re.findall(
                r"<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>\s*<([0-9A-Fa-f]+)>", blk):
            base = int(dst, 16)
            for i, cid in enumerate(range(int(lo, 16), int(hi, 16) + 1)):
                out[cid] = chr(base + i)
    return out


def parse_widths(font) -> tuple:
    """Return (widths_by_cid, default_width) from the descendant CIDFont."""
    desc = font.DescendantFonts[0]
    default = float(desc.get("/DW", 1000))
    widths, arr = {}, desc.get("/W")
    if arr is None:
        return widths, default
    i = 0
    while i < len(arr):
        first = int(arr[i])
        nxt = arr[i + 1]
        if isinstance(nxt, pikepdf.Array):
            for j, w in enumerate(nxt):
                widths[first + j] = float(w)
            i += 2
        else:
            last, w = int(nxt), float(arr[i + 2])
            for cid in range(first, last + 1):
                widths[cid] = float(w)
            i += 3
    return widths, default


class Font:
    def __init__(self, obj):
        self.to_unicode = parse_tounicode(obj.ToUnicode.read_bytes())
        self.from_unicode = {}
        for cid, s in self.to_unicode.items():
            self.from_unicode.setdefault(s, cid)
        self.widths, self.default_width = parse_widths(obj)

    def decode(self, hx: str) -> str:
        return "".join(self.to_unicode.get(int(hx[i:i + 4], 16), "�")
                       for i in range(0, len(hx), 4))

    def encode(self, s: str) -> str:
        cids = []
        for ch in s:
            cid = self.from_unicode.get(ch)
            if cid is None:
                raise SystemExit(
                    f"Glyph '{ch}' (U+{ord(ch):04X}) fehlt im Subset — "
                    f"Ersatztext umformulieren, ein Subset lässt sich hier "
                    f"nicht erweitern.")
            cids.append(cid)
        return "".join(f"{c:04X}" for c in cids)

    def width(self, s: str, size: float) -> float:
        total = sum(self.widths.get(self.from_unicode.get(ch, -1),
                                    self.default_width) for ch in s)
        return total * size / 1000.0


def rewrite_page(page, fonts, pending):
    """Replace runs on one page; returns the number of substitutions made."""
    data = page.Contents.read_bytes().decode("latin-1")
    runs = list(RUN_RE.finditer(data))

    # Runs printed back-to-back (only whitespace between them) form one
    # left-to-right flow: widening one shifts every later run in that flow.
    flows, cur = [], []
    for i, m in enumerate(runs):
        if cur and data[runs[i - 1].end():m.start()].strip() == "":
            cur.append(i)
        else:
            if cur:
                flows.append(cur)
            cur = [i]
    if cur:
        flows.append(cur)
    flow_of = {i: f for f in flows for i in f}

    new_text = {}
    shift = {i: 0.0 for i in range(len(runs))}
    count = 0

    for i, m in enumerate(runs):
        font = fonts[m.group("font")]
        old = font.decode(m.group("hex"))
        new = pending.get(old)
        if new is None or pending_used[old] >= pending_count[old]:
            continue
        pending_used[old] += 1
        count += 1
        new_text[i] = new

        size = float(m.group("size"))
        delta = font.width(new, size) - font.width(old, size)
        if abs(delta) < 1e-9:
            continue
        flow = flow_of[i]
        if len(flow) == 1 and abs(float(m.group("x"))) > 1e-9:
            shift[i] -= delta          # rechtsbündige Spalte: linke Kante zieht nach
        else:
            for j in flow[flow.index(i) + 1:]:
                shift[j] += delta      # Fliesstext: alles danach rückt nach

    out, last = [], 0
    for i, m in enumerate(runs):
        if i not in new_text and abs(shift[i]) < 1e-9:
            continue
        font = fonts[m.group("font")]
        hx = font.encode(new_text[i]) if i in new_text else m.group("hex")
        x = float(m.group("x")) + shift[i]
        rebuilt = (f"BT\n/{m.group('font')} {m.group('size')} Tf\n"
                   f"{m.group('a')} {m.group('b')} {m.group('c')} {m.group('d')} "
                   f"{x:.8g} {m.group('y')} Tm [<{hx}>] TJ\nET")
        out.append(data[last:m.start()])
        out.append(rebuilt)
        last = m.end()
    out.append(data[last:])
    page.Contents.write("".join(out).encode("latin-1"))
    return count


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__.strip().splitlines()[-1])
    src = sys.argv[1]
    dst = sys.argv[2] if len(sys.argv) > 2 else os.path.join(
        os.path.dirname(src),
        f"Kontoauszug 01.06.2026 - 30.06.2026 - "
        f"{HOLDER_IBAN.replace(' ', '')} - 2026-06-30.pdf")

    global pending_count, pending_used
    pending = {old: new for old, new, _ in REPLACEMENTS}
    pending_count = {old: n for old, _, n in REPLACEMENTS}
    pending_used = {old: 0 for old, _, _ in REPLACEMENTS}
    if len(pending) != len(REPLACEMENTS):
        raise SystemExit("Doppelter Suchtext in REPLACEMENTS")

    pdf = pikepdf.open(src)
    total = 0
    for page in pdf.pages:
        fonts = {str(name).lstrip("/"): Font(obj)
                 for name, obj in page.Resources.Font.items()}
        total += rewrite_page(page, fonts, pending)

    missed = {old: (pending_count[old], pending_used[old])
              for old in pending if pending_used[old] != pending_count[old]}
    if missed:
        raise SystemExit(f"Erwartete Treffer nicht erreicht: {missed}")

    with pdf.open_metadata() as meta:
        meta.clear()
        meta["dc:title"] = "Kontoauszug 01.06.2026 - 30.06.2026"
        meta["dc:creator"] = ["BudgetBuddy Test Fixture (anonymised)"]
        meta["dc:description"] = (
            "Anonymisierter Raiffeisen-Kontoauszug. Alle Personen, Adressen, "
            "IBANs, Referenznummern und Beträge sind erfunden.")
    pdf.docinfo = pdf.make_indirect(pikepdf.Dictionary({
        "/Title": "Kontoauszug 01.06.2026 - 30.06.2026",
        "/Author": "BudgetBuddy Test Fixture (anonymised)",
        "/Subject": "Synthetische Testdaten - BudgetBuddy Fixture",
        "/Producer": "pikepdf",
    }))
    pdf.save(dst, linearize=False)
    print(f"{total} Ersetzungen -> {dst}")
    print(f"  Saldovortrag {swiss(OPENING)}  "
          f"Umsatz S.1 {swiss(SUBTOTAL[0])}  "
          f"Belastungen {swiss(TOTAL[0])}  Gutschriften {swiss(TOTAL[1])}  "
          f"Schlusssaldo {swiss(TOTAL[2])}")


if __name__ == "__main__":
    main()
