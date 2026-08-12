"""BudgetBuddy-Kategorisierung als MCP-Tool für das Claude Agent SDK.

Bildet die Hybrid-Kategorisierung aus ADR-6 nach:

  Stufe 1  Lookup-Tabelle (deterministisch, kostenlos)  -> hier im Tool
  Stufe 2  Claude entscheidet bei unbekannten Händlern  -> der Agent selbst
  Fallback "Sonstiges"                                  -> per System-Prompt erzwungen

Die Seed-Patterns entsprechen `V04__create_category_lookup_table.sql`, die
Match-Regel entspricht `CategoryLookupRepository.findMatching`: Pattern ist
case-insensitiv im Transaktionstext enthalten, längstes Pattern gewinnt.

Drei Konventionen des SDK, die hier sichtbar werden:
  1. Die Tool-Funktion ist immer `async`.
  2. Rückgabe immer als {"content": [{"type": "text", ...}]}.
  3. In `allowed_tools` heisst das Tool `mcp__<server>__<tool>` — hier also
     `mcp__budget__kategorisiere`.

Start:
    pip install claude-agent-sdk
    export ANTHROPIC_API_KEY=...
    python3 tools/kategorisierung_agent.py "MIGROS BERN" "DIGITEC GALAXUS AG"
"""

import asyncio
import logging
import sys
from typing import Annotated, Any

from claude_agent_sdk import (
    AssistantMessage,
    ClaudeAgentOptions,
    ClaudeSDKClient,
    ResultMessage,
    TextBlock,
    ToolUseBlock,
    create_sdk_mcp_server,
    tool,
)

# Kennzahlen gehen über logging, nicht über print() — print bleibt der Demo
# in main() vorbehalten.
logger = logging.getLogger(__name__)

# Fixe Kategorienliste (Category.java). Reihenfolge = Enum-Reihenfolge.
KATEGORIEN = [
    "Wohnen",
    "Lebensmittel",
    "Transport",
    "Versicherung",
    "Telekom",
    "Gesundheit",
    "Freizeit",
    "Restaurant",
    "Shopping",
    "Bildung",
    "Einkommen",
    "Sparen",
    "Sonstiges",
]

FALLBACK = "Sonstiges"

# Wortgleich mit ClaudeCategorizationService.SYSTEM_PROMPT — Stufe 2 soll hier
# dieselben Entscheidungen treffen wie im Backend.
CLAUDE_SYSTEM_PROMPT = """Du kategorisierst Schweizer Bankkonto-Transaktionen.
Antworte ausschliesslich mit genau einem Kategorienamen aus der vorgegebenen Liste.
Keine Erklärung, keine Satzzeichen, kein weiterer Text."""

# Wortgleich mit ClaudeCategorizationService.buildUserPrompt. Die Kategorienliste
# wird wie dort aus KATEGORIEN generiert, damit Liste und Prompt nicht auseinanderlaufen.
CLAUDE_USER_PROMPT = """Kategorisiere diese Transaktion in genau eine der folgenden Kategorien:
[{kategorien}]

Transaktion: "{transaktionstext}"
Antwort (nur Kategoriename):"""

# Seed-Daten aus V04__create_category_lookup_table.sql. Patterns sind
# durchgängig gross geschrieben — das Matching normalisiert beide Seiten.
LOOKUP_TABELLE: dict[str, str] = {
    "MIGROS": "Lebensmittel",
    "COOP": "Lebensmittel",
    "DENNER": "Lebensmittel",
    "ALDI": "Lebensmittel",
    "LIDL": "Lebensmittel",
    "SBB": "Transport",
    "SWISS PASS": "Transport",
    "SWISSCOM": "Telekom",
    "SUNRISE": "Telekom",
    "SALT": "Telekom",
    "CSS": "Versicherung",
    "HELSANA": "Versicherung",
    "KPT": "Versicherung",
    "DIGITEC": "Shopping",
    "GALAXUS": "Shopping",
    "ZALANDO": "Shopping",
    "NETFLIX": "Freizeit",
    "SPOTIFY": "Freizeit",
    "MCDONALD'S": "Restaurant",
}


def lookup(transaktionstext: str) -> str | None:
    """Stufe 1: deterministischer Lookup, spezifischster Treffer gewinnt.

    Entspricht `findMatching`: ORDER BY length(pattern) DESC, pattern ASC —
    bei gleich langen Patterns entscheidet die alphabetische Reihenfolge,
    damit das Ergebnis reproduzierbar ist.
    """
    text = transaktionstext.upper()
    treffer = [p for p in LOOKUP_TABELLE if p in text]
    if not treffer:
        return None
    treffer.sort(key=lambda p: (-len(p), p))
    return LOOKUP_TABELLE[treffer[0]]


@tool(
    "kategorisiere",
    "Kategorisiert eine Schweizer Bankkonto-Transaktion über die BudgetBuddy-"
    "Lookup-Tabelle bekannter Händler. Liefert entweder die gefundene Kategorie "
    "oder die Meldung, dass der Händler unbekannt ist.",
    {
        "transaktionstext": Annotated[
            str, "Freitext der Transaktion, z. B. 'DIGITEC GALAXUS AG 044 913 2323'"
        ]
    },
)
async def kategorisiere(args: dict[str, Any]) -> dict[str, Any]:
    """Regel 1: immer async. Regel 2: Rückgabe immer als content/text-Block."""
    transaktionstext = (args.get("transaktionstext") or "").strip()

    if not transaktionstext:
        return {
            "content": [{"type": "text", "text": "Leerer Transaktionstext — nichts zu kategorisieren."}],
            "is_error": True,
        }

    kategorie = lookup(transaktionstext)

    if kategorie is not None:
        text = (
            f"Transaktion: {transaktionstext}\n"
            f"Kategorie: {kategorie}\n"
            f"Quelle: Lookup-Tabelle"
        )
    else:
        # Kein Lookup-Treffer -> Stufe 2. Statt eines eigenen API-Calls bekommt der
        # Agent denselben Prompt, den ClaudeCategorizationService an die API schicken würde.
        text = "Kein Händler-Pattern getroffen.\n\n" + CLAUDE_USER_PROMPT.format(
            kategorien=", ".join(KATEGORIEN),
            transaktionstext=transaktionstext,
        )

    return {"content": [{"type": "text", "text": text}]}


# Servername "budget" + Toolname "kategorisiere" ergeben mcp__budget__kategorisiere.
budget_server = create_sdk_mcp_server(
    name="budget",
    version="1.0.0",
    tools=[kategorisiere],
)

# Der Backend-Prompt bleibt unverändert vorne — er regelt die Kategorie-Entscheidung.
# Darunter nur, was der Agent zusätzlich braucht: Tool-Aufruf und Batch-Ausgabe.
SYSTEM_PROMPT = f"""{CLAUDE_SYSTEM_PROMPT}

Rufe für jede Transaktion zuerst das Tool `kategorisiere` auf. Liefert es eine
Kategorie, übernimm sie unverändert. Meldet es 'Kein Händler-Pattern getroffen',
entscheide nach den Regeln oben; im Zweifel '{FALLBACK}'.

Gib pro Transaktion eine Zeile aus, in der Reihenfolge der Eingabe — nur den
Kategorienamen, sonst nichts."""

OPTIONS = ClaudeAgentOptions(
    model="claude-haiku-4-5",
    system_prompt=SYSTEM_PROMPT,
    mcp_servers={"budget": budget_server},
    # Regel 3: mcp__<server>__<tool>
    allowed_tools=["mcp__budget__kategorisiere"],
    max_turns=10,
)


async def kategorisiere_transaktionen(transaktionen: list[str]) -> list[str]:
    """Schickt eine Liste von Transaktionstexten durch den Agenten.

    Der Agent antwortet mit einem Kategorienamen pro Zeile in Eingabereihenfolge
    (so bleibt der Backend-Prompt 'nur Kategoriename' unangetastet). Unbrauchbare
    Zeilen fallen wie im Backend auf 'Sonstiges'.
    """
    frage = "Kategorisiere diese Transaktionen:\n" + "\n".join(f"- {t}" for t in transaktionen)

    antwort: list[str] = []
    tool_aufrufe = 0
    ergebnis: ResultMessage | None = None

    async with ClaudeSDKClient(options=OPTIONS) as client:
        await client.query(frage)
        async for message in client.receive_response():
            if isinstance(message, AssistantMessage):
                for block in message.content:
                    if isinstance(block, TextBlock):
                        antwort.append(block.text)
                    elif isinstance(block, ToolUseBlock):
                        # Erwartung: ein Aufruf pro Transaktion, plus ein ToolSearch
                        # am Anfang (das SDK lädt das MCP-Schema erst bei Bedarf).
                        # Deutlich mehr heisst: der Agent hat mehrfach nachgefragt.
                        tool_aufrufe += 1
                        logger.debug("Tool-Aufruf %s mit %s", block.name, block.input)
            elif isinstance(message, ResultMessage):
                # receive_response() endet mit der ResultMessage — dort stehen
                # Turns und Kosten des gesamten Durchlaufs.
                ergebnis = message

    turns = ergebnis.num_turns if ergebnis is not None else 0
    kosten = ergebnis.total_cost_usd if ergebnis is not None else None
    logger.info(
        "Kategorisierung: %d Transaktionen · %d Tool-Aufrufe · %d Turns · Kosten %s",
        len(transaktionen),
        tool_aufrufe,
        turns,
        # total_cost_usd ist optional: bei Abo-Login meldet die CLI keine Kosten.
        f"${kosten:.4f}" if kosten is not None else "unbekannt",
    )

    zeilen = [z.strip() for z in "".join(antwort).splitlines() if z.strip()]
    zeilen += [FALLBACK] * (len(transaktionen) - len(zeilen))
    return [z if z in KATEGORIEN else FALLBACK for z in zeilen[: len(transaktionen)]]


async def main() -> None:
    # Logging erst hier konfigurieren, nicht beim Import: sonst überschreibt das
    # Modul die Konfiguration einer aufrufenden Anwendung.
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")

    transaktionen = sys.argv[1:] or [
        "MIGROS BERN BAHNHOF",
        "DIGITEC GALAXUS AG 044 913 2323",
        "BÄCKEREI HUBER LYSS",
    ]
    for transaktion, kategorie in zip(transaktionen, await kategorisiere_transaktionen(transaktionen)):
        print(f"{transaktion} -> {kategorie}")


if __name__ == "__main__":
    asyncio.run(main())
