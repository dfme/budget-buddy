"""Kategorisierungs-Agent im Aufbau von second_agent.py.

Dieselben vier Schritte wie dort — eigenes Tool, MCP-Server, Optionen, Frage —
nur mit der BudgetBuddy-Kategorisierung als Tool. Die Fachlogik (Lookup-Tabelle,
Kategorienliste, Prompts) kommt aus kategorisierung_tool.py, damit die
Seed-Patterns nur an einer Stelle stehen.

Start:
    python3 kategorisierung_agent.py "MIGROS BERN" "BAECKEREI HUBER LYSS"
"""

import asyncio
import sys

from claude_agent_sdk import (
    query, ClaudeAgentOptions,
    tool, create_sdk_mcp_server,
    AssistantMessage, TextBlock, ToolUseBlock, ResultMessage,
)

# Fachlogik aus dem Tool-Modul — keine zweite Kopie der Lookup-Tabelle.
from kategorisierung_tool import (
    CLAUDE_SYSTEM_PROMPT,
    CLAUDE_USER_PROMPT,
    FALLBACK,
    KATEGORIEN,
    lookup,
)

# Windows-Konsole auf UTF-8 umstellen (siehe first_agent.py)
sys.stdout.reconfigure(encoding="utf-8")


# ── Schritt 1: Eigenes Tool definieren ──────────────────────────
# Stufe 1 der Hybrid-Kategorisierung: deterministischer Lookup, kostenlos.
# Kein Treffer -> das Tool gibt die Entscheidung ans Modell zurück (Stufe 2).

@tool(
    "kategorisiere",
    "Kategorisiert eine Schweizer Bankkonto-Transaktion über die Lookup-Tabelle "
    "bekannter Händler. Liefert die gefundene Kategorie oder die Meldung, dass "
    "der Händler unbekannt ist.",
    # kategorisierung_tool.py nutzt hier Annotated[str, "..."] und beschreibt dem
    # Modell zusätzlich den Parameter selbst.
    {"transaktionstext": str},
)
async def kategorisiere(args):
    # args ist ein dict gemäss Schema: {"transaktionstext": "MIGROS BERN"}
    transaktionstext = (args.get("transaktionstext") or "").strip()

    if not transaktionstext:
        # is_error signalisiert dem Agenten einen Fehlschlag, ohne den Loop zu beenden.
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
        # Stufe 2: derselbe Prompt, den das Backend an die API schicken würde.
        text = "Kein Händler-Pattern getroffen.\n\n" + CLAUDE_USER_PROMPT.format(
            kategorien=", ".join(KATEGORIEN),
            transaktionstext=transaktionstext,
        )

    # Rückgabe IMMER in diesem Format — das Ergebnis geht als Nachricht
    # zurück in den Agent-Loop:
    return {"content": [{"type": "text", "text": text}]}


# ── Schritt 2: Tool in einen In-Process-MCP-Server packen ──────
budget = create_sdk_mcp_server(name="budget", version="1.0.0", tools=[kategorisiere])


# ── Schritt 3: Server anschliessen + Tool erlauben ─────────────
# Der Backend-Prompt steht vorne (er regelt die Kategorie-Entscheidung),
# darunter nur, was der Agent zusätzlich braucht: Tool-Aufruf und Batch-Ausgabe.
SYSTEM_PROMPT = f"""{CLAUDE_SYSTEM_PROMPT}

Rufe für jede Transaktion zuerst das Tool `kategorisiere` auf. Liefert es eine
Kategorie, übernimm sie unverändert. Meldet es 'Kein Händler-Pattern getroffen',
entscheide nach den Regeln oben; im Zweifel '{FALLBACK}'.

Gib pro Transaktion eine Zeile aus, in der Reihenfolge der Eingabe — nur den
Kategorienamen, sonst nichts."""

opts = ClaudeAgentOptions(
    # Klassifikation gegen eine feste Liste — dafür genügt das günstigste Modell.
    model="claude-haiku-4-5",
    system_prompt=SYSTEM_PROMPT,
    mcp_servers={"budget": budget},
    # tools=[] schaltet die eingebauten Claude-Code-Tools ab. Ohne das bleiben
    # Bash, Read & Co. verfügbar — allowed_tools erlaubt nur das Ausführen ohne
    # Rückfrage, es nimmt dem Modell keine Tools weg.
    tools=[],
    # setting_sources steht im SDK per Default auf ["user", "project"]: die CLI
    # lädt dann CLAUDE.md, die Projekt-Skills und alle installierten Plugins in
    # den Kontext — rund 88'000 Tokens pro Request. Leer gesetzt läuft der Agent
    # ausserdem bei allen gleich, unabhängig vom lokalen Setup.
    setting_sources=[],
    #                 └── dieser Schlüssel landet im Tool-Namen:
    allowed_tools=["mcp__budget__kategorisiere"],
    max_turns=3,
    max_budget_usd=0.20,
)


# ── Schritt 4: Aufgabe stellen, die das Tool provoziert ────────
async def main():
    transaktionen = sys.argv[1:] or [
        "MIGROS BERN BAHNHOF",          # Lookup-Treffer
        "DIGITEC GALAXUS AG 044 913 2323",  # Lookup-Treffer, längstes Pattern gewinnt
        "BÄCKEREI HUBER LYSS",          # kein Pattern -> Stufe 2
    ]
    frage = "Kategorisiere diese Transaktionen:\n" + "\n".join(f"- {t}" for t in transaktionen)

    async for msg in query(prompt=frage, options=opts):
        if isinstance(msg, AssistantMessage):
            for block in msg.content:
                if isinstance(block, ToolUseBlock):
                    # Hier SEHT ihr, welche Transaktion durch den Lookup geht:
                    print(f">>> TOOL-CALL: {block.name}  Input: {block.input}")
                elif isinstance(block, TextBlock):
                    print(block.text)
        elif isinstance(msg, ResultMessage):
            # total_cost_usd ist optional: bei Abo-Login meldet die CLI keine Kosten.
            kosten = f"${msg.total_cost_usd:.4f}" if msg.total_cost_usd is not None else "unbekannt"
            print(f"\n--- Turns: {msg.num_turns} · Kosten: {kosten} ---")

asyncio.run(main())
