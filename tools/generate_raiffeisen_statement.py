"""Generate a Raiffeisen-style bank statement PDF for testing the BudgetBuddy PDF upload."""

from __future__ import annotations

import argparse
from dataclasses import dataclass
from datetime import date, timedelta
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.platypus import (
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)

RAIFFEISEN_RED = colors.HexColor("#C5281C")
RAIFFEISEN_DARK = colors.HexColor("#1F1F1F")
GREY_LIGHT = colors.HexColor("#F2F2F2")
GREY_MED = colors.HexColor("#9C9C9C")


@dataclass(frozen=True)
class Tx:
    booking_date: date
    value_date: date
    text: str
    debit: float | None = None
    credit: float | None = None


def fmt_chf(amount: float | None) -> str:
    if amount is None:
        return ""
    sign = "-" if amount < 0 else ""
    a = abs(amount)
    whole = int(a)
    frac = round((a - whole) * 100)
    s = f"{whole:,}".replace(",", "'")
    return f"{sign}{s}.{frac:02d}"


def demo_transactions(reference: date) -> list[Tx]:
    def d(offset: int) -> date:
        return reference - timedelta(days=offset)

    return [
        Tx(d(28), d(28), "Saldovortrag", credit=2_847.55),
        Tx(d(26), d(26), "Lohn ADCUBUM AG, Bern", credit=4_250.00),
        Tx(d(25), d(25), "MIETE WOHNUNG GERECHTIGKEITSGASSE 12, 3011 BERN", debit=1_200.00),
        Tx(d(24), d(24), "CSS VERSICHERUNG AG, LUZERN MONATSPRAEMIE", debit=312.40),
        Tx(d(23), d(23), "SUNRISE COMMUNICATIONS AG ZUERICH MOBILE-ABO", debit=39.90),
        Tx(d(22), d(22), "MIGROS BERN BAHNHOF", debit=42.55),
        Tx(d(21), d(21), "SBB CFF FFS HALBTAX-ABO", debit=185.00),
        Tx(d(19), d(19), "COOP-2400 BERN MARKTGASSE", debit=67.85),
        Tx(d(18), d(18), "NETFLIX INTERNATIONAL B.V.", debit=19.90),
        Tx(d(17), d(17), "DENNER BERN HAUPTBHF", debit=23.40),
        Tx(d(15), d(15), "MCDONALDS RESTAURANT ZUERICH HB", debit=14.50),
        Tx(d(14), d(14), "SPOTIFY AB STOCKHOLM", debit=12.95),
        Tx(d(13), d(13), "TWINT P2P AN R. MUELLER", debit=50.00),
        Tx(d(11), d(11), "DIGITEC GALAXUS AG WINTERTHUR", debit=129.00),
        Tx(d(10), d(10), "RESTAURANT DELLA CASA, BERN", debit=68.00),
        Tx(d(9), d(9), "APOTHEKE BAHNHOF BERN", debit=28.40),
        Tx(d(7), d(7), "COIFFEUR TOP CUT, BERN", debit=45.00),
        Tx(d(6), d(6), "MIGROS BERN WANKDORF", debit=87.20),
        Tx(d(5), d(5), "SBB MOBILE TICKET BERN-ZUERICH", debit=52.00),
        Tx(d(4), d(4), "STARBUCKS BAHNHOF BERN", debit=8.50),
        Tx(d(2), d(2), "COOP PRONTO BERN", debit=12.30),
        Tx(d(1), d(1), "ZAHLUNG AN SPARKONTO CH64...4521", debit=200.00),
    ]


def compute_running_saldo(initial: float, txs: list[Tx]) -> list[float]:
    saldo = initial
    out: list[float] = []
    for tx in txs[1:]:
        if tx.debit is not None:
            saldo -= tx.debit
        if tx.credit is not None:
            saldo += tx.credit
        out.append(saldo)
    return [initial] + out


def build_pdf(output: Path, reference: date) -> None:
    txs = demo_transactions(reference)
    saldi = compute_running_saldo(2_847.55, txs)

    doc = SimpleDocTemplate(
        str(output),
        pagesize=A4,
        leftMargin=18 * mm,
        rightMargin=18 * mm,
        topMargin=15 * mm,
        bottomMargin=15 * mm,
        title="Kontoauszug Raiffeisen",
        author="Raiffeisen Schweiz Genossenschaft",
    )

    styles = getSampleStyleSheet()
    s_normal = ParagraphStyle(
        "n", parent=styles["Normal"], fontName="Helvetica", fontSize=9, leading=11
    )
    s_small = ParagraphStyle(
        "s", parent=styles["Normal"], fontName="Helvetica", fontSize=7.5, leading=9, textColor=GREY_MED
    )
    s_brand = ParagraphStyle(
        "brand",
        parent=styles["Normal"],
        fontName="Helvetica-Bold",
        fontSize=22,
        leading=24,
        textColor=RAIFFEISEN_RED,
    )
    s_brand_sub = ParagraphStyle(
        "brand_sub",
        parent=styles["Normal"],
        fontName="Helvetica",
        fontSize=8,
        leading=10,
        textColor=RAIFFEISEN_DARK,
    )
    s_h1 = ParagraphStyle(
        "h1",
        parent=styles["Normal"],
        fontName="Helvetica-Bold",
        fontSize=16,
        leading=20,
        textColor=RAIFFEISEN_DARK,
        spaceAfter=4,
    )
    s_h2 = ParagraphStyle(
        "h2",
        parent=styles["Normal"],
        fontName="Helvetica-Bold",
        fontSize=10,
        leading=12,
        textColor=RAIFFEISEN_DARK,
        spaceBefore=8,
        spaceAfter=4,
    )

    story: list = []

    header_data = [
        [
            Paragraph("RAIFFEISEN", s_brand),
            Paragraph(
                "<b>Raiffeisenbank Bern</b><br/>"
                "Bundesplatz 2<br/>"
                "3011 Bern<br/>"
                "Tel. 0848 00 00 00<br/>"
                "www.raiffeisen.ch",
                s_brand_sub,
            ),
        ]
    ]
    header_tbl = Table(header_data, colWidths=[90 * mm, 84 * mm])
    header_tbl.setStyle(
        TableStyle(
            [
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 0),
                ("RIGHTPADDING", (0, 0), (-1, -1), 0),
                ("LINEBELOW", (0, 0), (-1, -1), 1.2, RAIFFEISEN_RED),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
            ]
        )
    )
    story.append(header_tbl)
    story.append(Spacer(1, 6 * mm))

    addr_data = [
        [
            Paragraph(
                "<b>Kontoinhaberin</b><br/>"
                "Lara Müller<br/>"
                "Gerechtigkeitsgasse 12<br/>"
                "3011 Bern",
                s_normal,
            ),
            Paragraph(
                f"<b>Bern, {reference.strftime('%d.%m.%Y')}</b><br/><br/>"
                "Kundennummer: 4'221'886<br/>"
                "Berater: Marc Hofer",
                s_normal,
            ),
        ]
    ]
    addr_tbl = Table(addr_data, colWidths=[90 * mm, 84 * mm])
    addr_tbl.setStyle(
        TableStyle(
            [
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 0),
                ("RIGHTPADDING", (0, 0), (-1, -1), 0),
            ]
        )
    )
    story.append(addr_tbl)
    story.append(Spacer(1, 8 * mm))

    period_start = (reference - timedelta(days=30)).strftime("%d.%m.%Y")
    period_end = reference.strftime("%d.%m.%Y")
    story.append(Paragraph("Kontoauszug Privatkonto", s_h1))
    story.append(
        Paragraph(
            f"IBAN CH64 8080 8004 2218 8600 1 &nbsp;·&nbsp; CHF &nbsp;·&nbsp; "
            f"Periode {period_start} – {period_end}",
            s_normal,
        )
    )
    story.append(Spacer(1, 4 * mm))

    table_data: list[list] = [
        [
            "Buchung",
            "Valuta",
            "Buchungstext",
            "Belastung CHF",
            "Gutschrift CHF",
            "Saldo CHF",
        ]
    ]
    for tx, saldo in zip(txs, saldi, strict=True):
        table_data.append(
            [
                tx.booking_date.strftime("%d.%m.%Y"),
                tx.value_date.strftime("%d.%m.%Y"),
                tx.text,
                fmt_chf(tx.debit) if tx.debit is not None else "",
                fmt_chf(tx.credit) if tx.credit is not None else "",
                fmt_chf(saldo),
            ]
        )

    total_debit = sum(t.debit for t in txs if t.debit is not None)
    total_credit = sum(t.credit for t in txs if t.credit is not None)
    table_data.append(
        [
            "",
            "",
            "Total Bewegungen",
            fmt_chf(total_debit),
            fmt_chf(total_credit),
            fmt_chf(saldi[-1]),
        ]
    )

    col_widths = [22 * mm, 22 * mm, 60 * mm, 24 * mm, 24 * mm, 22 * mm]
    tx_tbl = Table(table_data, colWidths=col_widths, repeatRows=1)
    tx_tbl.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (-1, 0), RAIFFEISEN_RED),
                ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
                ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
                ("FONTSIZE", (0, 0), (-1, 0), 8.5),
                ("ALIGN", (3, 0), (-1, -1), "RIGHT"),
                ("FONTNAME", (0, 1), (-1, -2), "Helvetica"),
                ("FONTSIZE", (0, 1), (-1, -1), 8.5),
                ("LEADING", (0, 0), (-1, -1), 10),
                ("BOTTOMPADDING", (0, 0), (-1, 0), 5),
                ("TOPPADDING", (0, 0), (-1, 0), 5),
                ("BOTTOMPADDING", (0, 1), (-1, -1), 3),
                ("TOPPADDING", (0, 1), (-1, -1), 3),
                ("ROWBACKGROUNDS", (0, 1), (-1, -2), [colors.white, GREY_LIGHT]),
                ("LINEABOVE", (0, -1), (-1, -1), 0.6, RAIFFEISEN_DARK),
                ("FONTNAME", (0, -1), (-1, -1), "Helvetica-Bold"),
                ("BACKGROUND", (0, -1), (-1, -1), GREY_LIGHT),
                ("VALIGN", (0, 0), (-1, -1), "MIDDLE"),
            ]
        )
    )
    story.append(tx_tbl)
    story.append(Spacer(1, 8 * mm))

    story.append(Paragraph("Hinweise", s_h2))
    story.append(
        Paragraph(
            "Bitte prüfen Sie diesen Auszug. Beanstandungen sind innert 30 Tagen schriftlich "
            "zu erheben, andernfalls gilt der Auszug als genehmigt. Bei Fragen steht Ihnen Ihr "
            "Berater jederzeit zur Verfügung.",
            s_small,
        )
    )
    story.append(Spacer(1, 4 * mm))
    story.append(
        Paragraph(
            "Raiffeisenbank Bern · Bundesplatz 2 · 3011 Bern · "
            "Mitglied von Raiffeisen Schweiz Genossenschaft · "
            "BIC RAIFCH22XXX · Clearing 80808",
            s_small,
        )
    )

    doc.build(story)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--output",
        type=Path,
        default=Path(__file__).resolve().parent.parent
        / "tests"
        / "fixtures"
        / "statements"
        / "raiffeisen_kontoauszug.pdf",
    )
    parser.add_argument(
        "--reference-date",
        type=lambda s: date.fromisoformat(s),
        default=date.today(),
        help="Stichdatum für den Auszug (Default: heute)",
    )
    args = parser.parse_args()
    args.output.parent.mkdir(parents=True, exist_ok=True)
    build_pdf(args.output, args.reference_date)
    size_kb = args.output.stat().st_size / 1024
    print(f"Wrote {args.output} ({size_kb:.1f} KB)")


if __name__ == "__main__":
    main()
