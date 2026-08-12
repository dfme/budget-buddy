"""Lookup-Pfleger im Aufbau von kategorisierung_agent.py.

Der dritte Agent, und die dritte Arbeitsteilung:

  kategorisierung_agent  Tool weiss es oder das Modell entscheidet
  bericht_agent          Tool rechnet, Modell formuliert nur
  lookup_pfleger         Modell schlägt vor, Tool hat das Veto

Hier hat das Modell die kreative Aufgabe — aus wechselnden Freitexten den
stabilen Händlerstamm erkennen — und das Tool die Kontrolle: es kennt die
tatsächlichen Kategorien der Transaktionen, das Modell nicht.

Ergebnis ist ein Block INSERT-Zeilen für eine neue Flyway-Migration. Jede Zeile
kommt wörtlich aus dem Prüf-Tool; das Modell setzt kein SQL selbst zusammen.

Start:
    python3 lookup_pfleger.py
"""

import asyncio
import sys

from claude_agent_sdk import (
    query, ClaudeAgentOptions,
    tool, create_sdk_mcp_server,
    AssistantMessage, TextBlock, ToolUseBlock, ResultMessage,
)

from bericht_tool import lade_transaktionen
from pfleger_tool import SYSTEM_PROMPT, formatiere_luecken, luecken, pruefe

sys.stdout.reconfigure(encoding="utf-8")

TRANSAKTIONEN = lade_transaktionen()


# ── Schritt 1: Zwei Tools — eines zeigt die Lücken, eines hat das Veto ──

@tool(
    "unbekannte_transaktionen",
    "Listet alle Transaktionstexte, die kein Pattern der Lookup-Tabelle trifft, "
    "häufigste zuerst. Jeder davon kostet aktuell einen Claude-Call.",
    {},  # keine Parameter — die Lücken ergeben sich aus dem Datenbestand
)
async def unbekannte_transaktionen(args):
    return {"content": [{"type": "text", "text": formatiere_luecken(luecken(TRANSAKTIONEN))}]}


@tool(
    "pruefe_pattern",
    "Prüft einen Pattern-Vorschlag auf Länge, Kollision mit bestehenden Patterns "
    "und Übergriffigkeit gegen die echten Transaktionen. Bei Annahme kommt die "
    "fertige Migrationszeile zurück.",
    {"pattern": str, "kategorie": str},
)
async def pruefe_pattern(args):
    angenommen, meldung = pruefe(
        args.get("pattern") or "",
        args.get("kategorie") or "",
        TRANSAKTIONEN,
    )
    # Abgelehnt = is_error: das Modell soll die Begründung lesen und nachbessern,
    # nicht die Ablehnung als Erfolg verbuchen.
    return {
        "content": [{"type": "text", "text": meldung}],
        **({} if angenommen else {"is_error": True}),
    }


# ── Schritt 2: Beide Tools in einen In-Process-MCP-Server ───────
pfleger = create_sdk_mcp_server(
    name="pfleger", version="1.0.0", tools=[unbekannte_transaktionen, pruefe_pattern]
)


# ── Schritt 3: Server anschliessen + Tools erlauben ─────────────
opts = ClaudeAgentOptions(
    # Haiku genügt: die Urteile fällt das Tool, das Modell erkennt nur Namensstämme.
    # Kein effort-Parameter — Haiku 4.5 unterstützt ihn nicht.
    model="claude-haiku-4-5",
    system_prompt=SYSTEM_PROMPT,
    mcp_servers={"pfleger": pfleger},
    tools=[],
    # Ohne setting_sources=[] lädt die CLI CLAUDE.md, Projekt-Skills und alle
    # installierten Plugins in den Kontext — siehe bericht_agent.py.
    setting_sources=[],
    allowed_tools=[
        "mcp__pfleger__unbekannte_transaktionen",
        "mcp__pfleger__pruefe_pattern",
    ],
    # Ein Prüf-Aufruf pro Vorschlag, plus Korrekturrunden.
    max_turns=15,
    max_budget_usd=0.30,
)


# ── Schritt 4: Aufräumen lassen ─────────────────────────────────
async def main():
    frage = (
        "Finde die offenen Transaktionen und schlage Patterns vor, die möglichst "
        "viele davon abdecken."
    )

    try:
        async for msg in query(prompt=frage, options=opts):
            if isinstance(msg, AssistantMessage):
                for block in msg.content:
                    if isinstance(block, ToolUseBlock):
                        print(f">>> TOOL-CALL: {block.name.split('__')[-1]}  {block.input}")
                    elif isinstance(block, TextBlock):
                        print(block.text)
            elif isinstance(msg, ResultMessage):
                kosten = f"${msg.total_cost_usd:.4f}" if msg.total_cost_usd is not None else "unbekannt"
                print(f"\n--- Turns: {msg.num_turns} · Kosten: {kosten} ---")
    except Exception as e:
        # max_budget_usd / max_turns beenden die CLI mit einer Exception —
        # der Guardrail bei der Arbeit, kein Absturz.
        if "budget" not in str(e).lower() and "max turns" not in str(e).lower():
            raise
        print(f"\n--- Abgebrochen: {e} ---")

asyncio.run(main())
