"""Fachlogik für den Lookup-Pfleger.

Schliesst den Kreis zum Kategorisierer: dort fällt alles, was kein Pattern
trifft, an die Claude-API. Jede Transaktion, die stattdessen die Lookup-Tabelle
trifft, spart dauerhaft einen API-Call (ADR-6). Dieser Agent sucht also die
Lücken und schlägt neue Patterns vor.

Die Arbeitsteilung ist wieder eine andere:

  Modell   sieht nur die unbekannten Texte und schlägt ein Pattern vor —
           es muss den stabilen Stamm über wechselnde Freitexte erkennen
           ("LOHN JUNI ARBEITGEBER AG" / "LOHN JULI ARBEITGEBER AG" -> ?)
  Tool     kennt die Wahrheit (die Kategorie-Spalte der Transaktionen) und
           prüft jeden Vorschlag dagegen. Das Modell sieht diese Spalte nie.

Gespiegelt aus dem Backend:
  CategoryLearningService    Normalisierung trim + upper(Locale.ROOT)
  CategoryLookupRepository   längstes Pattern gewinnt — daher die Kollisionsprüfung
  V04__create_category_lookup_table.sql   Format der INSERT-Zeile
"""

from dataclasses import dataclass

from bericht_tool import Transaktion, lade_transaktionen
from kategorisierung_tool import KATEGORIEN, LOOKUP_TABELLE, lookup

# Unter drei Zeichen ist ein Pattern zu übergriffig: "AG" träfe jede Schweizer
# Firma. Die kürzesten Seeds (SBB, CSS) haben genau drei.
MIN_LAENGE = 3


@dataclass(frozen=True)
class Luecke:
    """Ein Transaktionstext, den die Lookup-Tabelle nicht kennt."""

    text: str
    anzahl: int


def normalisiere(pattern: str) -> str:
    """trim + upper — wie CategoryLearningService.learn.

    Python kennt kein Locale-abhängiges upper(), der Locale.ROOT-Fallstrick
    aus dem Java-Kommentar entfällt hier also.
    """
    return pattern.strip().upper()


def luecken(transaktionen: list[Transaktion]) -> list[Luecke]:
    """Transaktionstexte ohne Lookup-Treffer, häufigste zuerst.

    Die Häufigkeit ist die Priorität: ein Pattern, das drei Transaktionen
    abdeckt, spart dreimal so viele API-Calls wie eines für einen Einzelfall.
    """
    zaehler: dict[str, int] = {}
    for tx in transaktionen:
        if lookup(tx.text) is None:
            zaehler[tx.text] = zaehler.get(tx.text, 0) + 1
    return [
        Luecke(text=text, anzahl=anzahl)
        for text, anzahl in sorted(zaehler.items(), key=lambda kv: (-kv[1], kv[0]))
    ]


def formatiere_luecken(offen: list[Luecke]) -> str:
    if not offen:
        return "Keine offenen Transaktionen — die Lookup-Tabelle deckt alles ab."
    zeilen = [f"{len(offen)} Transaktionstexte ohne Lookup-Treffer (häufigste zuerst):"]
    zeilen += [f"  {l.anzahl}x  {l.text}" for l in offen]
    return "\n".join(zeilen)


def _insert_zeile(pattern: str, kategorie: str) -> str:
    """INSERT im Format von V04 — Apostroph im Pattern wird verdoppelt."""
    return f"    ('{pattern.replace(chr(39), chr(39) * 2)}', '{kategorie}'),"


def pruefe(pattern: str, kategorie: str, transaktionen: list[Transaktion]) -> tuple[bool, str]:
    """Prüft einen Vorschlag. Gibt (angenommen, Begründung) zurück.

    Reihenfolge ist Absicht: erst die billigen Formprüfungen, dann die
    Kollisionsprüfung gegen die Tabelle, zuletzt der Abgleich mit echten Daten.
    """
    p = normalisiere(pattern)

    if kategorie not in KATEGORIEN:
        return False, (
            f"'{kategorie}' ist keine gültige Kategorie. Erlaubt: {', '.join(KATEGORIEN)}."
        )

    if len(p) < MIN_LAENGE:
        return False, f"'{p}' ist zu kurz (min. {MIN_LAENGE} Zeichen) und würde zu viel treffen."

    # Der PK ist das Pattern, save() ist ein Upsert — ein vorhandenes Pattern
    # würde also überschrieben, nicht abgelehnt. Bei abweichender Kategorie
    # ändert das stillschweigend das Verhalten in Produktion.
    if p in LOOKUP_TABELLE:
        bestehend = LOOKUP_TABELLE[p]
        if bestehend == kategorie:
            return False, f"'{p}' steht bereits als '{bestehend}' in der Tabelle — bringt nichts."
        return False, (
            f"'{p}' existiert bereits als '{bestehend}'. Ein Insert wäre ein Upsert und würde "
            f"die bestehende Zuordnung auf '{kategorie}' umschreiben."
        )

    # Längstes Pattern gewinnt (findMatching). Ein Pattern, das ein bestehendes
    # enthält oder in einem enthalten ist, verschiebt darum bestehende Treffer.
    for bestehend, bestehende_kategorie in LOOKUP_TABELLE.items():
        if (bestehend in p or p in bestehend) and bestehende_kategorie != kategorie:
            laenger = p if len(p) > len(bestehend) else bestehend
            return False, (
                f"Kollision mit '{bestehend}' ('{bestehende_kategorie}'): die Patterns überlappen, "
                f"und da das längere gewinnt ('{laenger}'), würden bestehende Treffer umgeleitet."
            )

    # Abgleich mit echten Daten — hier sieht das Tool die Kategorie-Spalte,
    # die das Modell nicht kennt.
    treffer = [tx for tx in transaktionen if p in tx.text.upper()]
    if not treffer:
        return False, f"'{p}' trifft keine der vorliegenden Transaktionen."

    kategorien = sorted({tx.kategorie for tx in treffer})
    if len(kategorien) > 1:
        beispiele = "; ".join(f"{tx.text} ({tx.kategorie})" for tx in treffer[:3])
        return False, (
            f"'{p}' trifft {len(treffer)} Transaktionen aus mehreren Kategorien "
            f"({', '.join(kategorien)}) — zu übergriffig. Beispiele: {beispiele}"
        )

    if kategorien[0] != kategorie:
        return False, (
            f"'{p}' trifft {len(treffer)} Transaktionen, die alle '{kategorien[0]}' sind, "
            f"nicht '{kategorie}'."
        )

    return True, (
        f"Angenommen: '{p}' trifft {len(treffer)} Transaktionen, alle '{kategorie}'.\n"
        f"Migrationszeile:\n{_insert_zeile(p, kategorie)}"
    )


SYSTEM_PROMPT = f"""Du pflegst die Lookup-Tabelle von BudgetBuddy.

Jede Transaktion, die ein Pattern in dieser Tabelle trifft, wird ohne API-Call
kategorisiert. Alles andere kostet einen Claude-Call. Dein Ziel: möglichst
viele der offenen Transaktionen mit möglichst wenigen Patterns abdecken.

Ablauf:
1. `unbekannte_transaktionen` aufrufen.
2. Für jeden erkennbaren Händler ein Pattern vorschlagen und mit
   `pruefe_pattern` prüfen lassen.
3. Wird ein Vorschlag abgelehnt, lies die Begründung und versuche es einmal
   anders. Geht es nicht, lass den Händler weg.
4. Am Schluss alle angenommenen Migrationszeilen ausgeben, sonst nichts.

Regeln für gute Patterns:
- Nimm den stabilen Teil des Textes, nicht den ganzen Text. Filialen, Orte,
  Monate und Telefonnummern wechseln — der Händlername bleibt.
- So kurz wie möglich, aber lang genug, dass er nur diesen Händler trifft.
- Gross geschrieben, wie die bestehenden Einträge.
- Kategorie exakt aus dieser Liste: {', '.join(KATEGORIEN)}

Du siehst die Kategorien der Transaktionen nicht — schliesse sie aus dem Text.
Die Prüfung kennt sie und widerspricht dir, wenn du falsch liegst."""
