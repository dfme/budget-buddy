"""Fachlogik für den Monatsbericht-Agenten (US-09).

Trennlinie dieses Moduls: **hier wird gerechnet, das Modell rechnet nie.**
ADR-9 verbietet Gleitkomma-Arithmetik für CHF-Beträge — ein Modell, das
Prozentanteile selbst ausrechnet, ist genau das. Deshalb liefert `aggregiere`
jede Zahl fertig, inklusive Anteilen und Sparvorschlag, und `pruefe` weist
jeden Betrag zurück, der nicht aus dieser Rechnung stammt.

Gespiegelt aus dem Backend:
  TransactionSummaryService  Aggregation pro Kategorie, nur Ausgaben, absteigend
                             nach Betrag; Anteile per Largest-Remainder, damit
                             die Summe exakt 100.00 ergibt
  SwissBankStatementParser   Betragsformat 1'234.56 — Apostroph raus vor Decimal
  ADR-9                      Decimal statt float, überall

Datenquelle ist bewusst eine CSV und nicht die Datenbank: der Agent ist ein
Prototyp für den noch leeren report/-Modul, kein zweiter DB-Zugriffspfad.
"""

import csv
import re
from dataclasses import dataclass
from datetime import date
from decimal import ROUND_DOWN, ROUND_HALF_UP, Decimal
from pathlib import Path

CSV_PFAD = Path(__file__).parent / "beispiel_transaktionen.csv"

RAPPEN = Decimal("0.01")
HUNDERT = Decimal("100")

# US-09, AC 3: unter 28 Tagen Datenabdeckung gibt es keinen Bericht.
MIN_TAGE = 28

# Kategorien, an denen sich sinnvoll sparen lässt — Miete und Prämie gehören
# nicht dazu. Reihenfolge = Priorität bei Gleichstand.
DISKRETIONAER = ["Restaurant", "Freizeit", "Shopping"]

# Anteil, den der Sparvorschlag von der grössten diskretionären Kategorie vorschlägt.
SPARQUOTE = Decimal("0.20")


@dataclass(frozen=True)
class Transaktion:
    datum: date
    text: str
    betrag: Decimal
    kategorie: str
    einkommen: bool


@dataclass(frozen=True)
class Posten:
    """Eine Kategorie im Monat: Betrag, Anteil und Veränderung zum Vormonat."""

    kategorie: str
    betrag: Decimal
    anteil: Decimal
    delta: Decimal | None  # None = Kategorie kam im Vormonat nicht vor


@dataclass(frozen=True)
class Monatszahlen:
    monat: str
    einkommen: Decimal
    gesamtausgaben: Decimal
    saldo: Decimal
    anzahl: int
    abgedeckte_tage: int
    posten: list[Posten]
    sparkategorie: str | None
    sparbetrag: Decimal | None


def betrag_aus_text(roh: str) -> Decimal:
    """Schweizer Betragsformat: 1'234.56 -> Decimal('1234.56').

    Der Apostroph muss vor der Konvertierung weg (CLAUDE.md, PDF-Parsing).
    """
    return Decimal(roh.replace("'", "").strip())


def lade_transaktionen(pfad: Path = CSV_PFAD) -> list[Transaktion]:
    with open(pfad, encoding="utf-8", newline="") as f:
        return [
            Transaktion(
                datum=date.fromisoformat(zeile["datum"]),
                text=zeile["text"],
                betrag=betrag_aus_text(zeile["betrag"]),
                kategorie=zeile["kategorie"],
                einkommen=zeile["einkommen"].strip().lower() == "ja",
            )
            for zeile in csv.DictReader(f)
        ]


def _vormonat(monat: str) -> str:
    jahr, mon = (int(t) for t in monat.split("-"))
    return f"{jahr - 1}-12" if mon == 1 else f"{jahr}-{mon - 1:02d}"


def _summen_je_kategorie(transaktionen: list[Transaktion], monat: str) -> dict[str, Decimal]:
    """Ausgaben des Monats pro Kategorie. Einkommen fliesst nicht ein."""
    summen: dict[str, Decimal] = {}
    for tx in transaktionen:
        if tx.einkommen or tx.datum.strftime("%Y-%m") != monat:
            continue
        summen[tx.kategorie] = summen.get(tx.kategorie, Decimal("0")) + tx.betrag
    return summen


def _anteile(betraege: list[Decimal], total: Decimal) -> list[Decimal]:
    """Prozentanteile per Largest-Remainder, Summe exakt 100.00.

    Naives Runden jedes Anteils kann 99.99 oder 100.01 ergeben — siehe den
    gleichnamigen Kommentar in TransactionSummaryService.
    """
    if total <= 0:
        return [Decimal("0.00") for _ in betraege]

    roh = [b * HUNDERT / total for b in betraege]
    abgerundet = [r.quantize(RAPPEN, rounding=ROUND_DOWN) for r in roh]

    # Fehlende Hundertstel an die grössten Reste verteilen, bei Gleichstand
    # an den grösseren Betrag — damit ist das Ergebnis reproduzierbar.
    fehlend = int(((HUNDERT - sum(abgerundet)) / RAPPEN).to_integral_value())
    reihenfolge = sorted(
        range(len(betraege)),
        key=lambda i: (roh[i] - abgerundet[i], betraege[i]),
        reverse=True,
    )
    for i in reihenfolge[:fehlend]:
        abgerundet[i] += RAPPEN
    return abgerundet


def aggregiere(transaktionen: list[Transaktion], monat: str) -> Monatszahlen:
    """Rechnet den ganzen Monat durch — alles, was der Bericht an Zahlen braucht."""
    im_monat = [tx for tx in transaktionen if tx.datum.strftime("%Y-%m") == monat]
    ausgaben = [tx for tx in im_monat if not tx.einkommen]

    einkommen = sum((tx.betrag for tx in im_monat if tx.einkommen), Decimal("0"))
    gesamt = sum((tx.betrag for tx in ausgaben), Decimal("0"))

    summen = _summen_je_kategorie(transaktionen, monat)
    vormonat_summen = _summen_je_kategorie(transaktionen, _vormonat(monat))

    # Absteigend nach Betrag, bei Gleichstand alphabetisch (wie im Backend).
    sortiert = sorted(summen.items(), key=lambda kv: (-kv[1], kv[0]))
    anteile = _anteile([b for _, b in sortiert], gesamt)

    posten = [
        Posten(
            kategorie=kat,
            betrag=betrag.quantize(RAPPEN),
            anteil=anteil,
            delta=(betrag - vormonat_summen[kat]).quantize(RAPPEN)
            if kat in vormonat_summen
            else None,
        )
        for (kat, betrag), anteil in zip(sortiert, anteile)
    ]

    sparkategorie, sparbetrag = _sparvorschlag(posten)

    return Monatszahlen(
        monat=monat,
        einkommen=einkommen.quantize(RAPPEN),
        gesamtausgaben=gesamt.quantize(RAPPEN),
        saldo=(einkommen - gesamt).quantize(RAPPEN),
        anzahl=len(im_monat),
        abgedeckte_tage=_abgedeckte_tage(im_monat),
        posten=posten,
        sparkategorie=sparkategorie,
        sparbetrag=sparbetrag,
    )


def _abgedeckte_tage(im_monat: list[Transaktion]) -> int:
    if not im_monat:
        return 0
    return (max(tx.datum for tx in im_monat) - min(tx.datum for tx in im_monat)).days + 1


def _sparvorschlag(posten: list[Posten]) -> tuple[str | None, Decimal | None]:
    """Grösste diskretionäre Kategorie, davon SPARQUOTE.

    Auch diese Zahl kommt aus dem Tool — sonst müsste das Modell rechnen,
    und der Prüfschritt könnte den Betrag nicht mehr verifizieren.
    """
    kandidaten = [p for p in posten if p.kategorie in DISKRETIONAER]
    if not kandidaten:
        return None, None
    groesster = max(kandidaten, key=lambda p: (p.betrag, -DISKRETIONAER.index(p.kategorie)))
    return groesster.kategorie, (groesster.betrag * SPARQUOTE).quantize(RAPPEN, ROUND_HALF_UP)


def _chf(betrag: Decimal) -> str:
    """Formatiert mit Apostroph als Tausendertrennzeichen: 1'250.00."""
    ganz, _, rest = f"{betrag:.2f}".partition(".")
    vorzeichen = "-" if ganz.startswith("-") else ""
    ziffern = ganz.lstrip("-")
    gruppen = []
    while len(ziffern) > 3:
        gruppen.insert(0, ziffern[-3:])
        ziffern = ziffern[:-3]
    gruppen.insert(0, ziffern)
    return vorzeichen + "'".join(gruppen) + "." + rest


def formatiere(zahlen: Monatszahlen) -> str:
    """Der Zahlen-Block, den das Modell als einzige Faktenquelle bekommt."""
    zeilen = [
        f"Monat: {zahlen.monat}",
        f"Einkommen: CHF {_chf(zahlen.einkommen)}",
        f"Gesamtausgaben: CHF {_chf(zahlen.gesamtausgaben)}",
        f"Saldo: CHF {_chf(zahlen.saldo)}",
        f"Transaktionen: {zahlen.anzahl} · abgedeckte Tage: {zahlen.abgedeckte_tage}",
        "",
        "Ausgaben nach Kategorie (absteigend):",
    ]
    for p in zahlen.posten:
        delta = (
            "neu gegenüber Vormonat"
            if p.delta is None
            else f"Vormonat {'+' if p.delta >= 0 else '−'}CHF {_chf(abs(p.delta))}"
        )
        zeilen.append(f"  {p.kategorie}: CHF {_chf(p.betrag)} · {p.anteil:.2f}% · {delta}")

    if zahlen.sparbetrag is not None:
        zeilen += [
            "",
            f"Sparpotenzial: CHF {_chf(zahlen.sparbetrag)} pro Monat "
            f"(20% der Kategorie {zahlen.sparkategorie})",
        ]
    return "\n".join(zeilen)


# ── Prüfung ────────────────────────────────────────────────────────
# Jede Zahl im Bericht muss aus formatiere() stammen. Erkannt werden nur
# Zahlen, die an CHF oder % hängen — Datumsangaben wie 2026-07 lösen damit
# keinen Fehlalarm aus. Der System-Prompt verlangt deshalb, dass jeder Betrag
# mit CHF und jeder Anteil mit % geschrieben wird.

_BETRAG = re.compile(r"CHF\s*(-?[\d']+(?:\.\d{1,2})?)|(-?[\d']+(?:\.\d{1,2})?)\s*CHF")
_ANTEIL = re.compile(r"([\d']+(?:\.\d{1,2})?)\s*%")


def _erlaubte_werte(zahlen: Monatszahlen) -> set[Decimal]:
    werte = {
        zahlen.einkommen,
        zahlen.gesamtausgaben,
        zahlen.saldo,
        abs(zahlen.saldo),
    }
    for p in zahlen.posten:
        werte |= {p.betrag, p.anteil}
        if p.delta is not None:
            werte |= {p.delta, abs(p.delta)}
    if zahlen.sparbetrag is not None:
        werte.add(zahlen.sparbetrag)
    return werte


def pruefe(entwurf: str, zahlen: Monatszahlen) -> list[str]:
    """Gibt die Zahlen zurück, die nicht aus der Aggregation stammen."""
    erlaubt = _erlaubte_werte(zahlen)
    beanstandet: list[str] = []

    for treffer in _BETRAG.finditer(entwurf):
        roh = treffer.group(1) or treffer.group(2)
        if betrag_aus_text(roh) not in erlaubt:
            beanstandet.append(f"CHF {roh}")

    for treffer in _ANTEIL.finditer(entwurf):
        roh = treffer.group(1)
        if betrag_aus_text(roh) not in erlaubt:
            beanstandet.append(f"{roh}%")

    # Reihenfolge stabil halten, Duplikate raus.
    return list(dict.fromkeys(beanstandet))


# ── Prompts ────────────────────────────────────────────────────────

SYSTEM_PROMPT = """Du schreibst den monatlichen Finanzbericht für BudgetBuddy.
Die Leserin ist Studentin oder Berufseinsteigerin ohne Finanzhintergrund.

Du rechnest nicht. Jede Zahl im Bericht stammt aus dem Tool `monatszahlen` —
übernimm sie unverändert, leite nichts ab, schätze nichts, runde nichts.

Ablauf:
1. `monatszahlen` für den gefragten Monat aufrufen.
2. Bericht schreiben.
3. Den Bericht durch `pruefe_bericht` schicken.
4. Beanstandet die Prüfung eine Zahl, korrigiere sie aus den Tool-Zahlen und
   prüfe erneut. Gib den Bericht erst aus, wenn die Prüfung sauber ist.

Form:
- Beträge immer als «CHF 1'234.56», Anteile immer mit «%» — sonst kann die
  Prüfung sie nicht erkennen.
- Vier kurze Abschnitte: Überblick, grösste Ausgaben, Veränderung zum Vormonat,
  Sparvorschlag.
- Keine Fachbegriffe ohne Erklärung, keine Anrede, keine Emojis.
- Höchstens 200 Wörter."""
