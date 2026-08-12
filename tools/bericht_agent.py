"""Monatsbericht-Agent (US-09) im Aufbau von kategorisierung_agent.py.

Dieselben vier Schritte — eigene Tools, MCP-Server, Optionen, Frage — aber mit
einer anderen Arbeitsteilung: bei der Kategorisierung entscheidet das Modell,
wenn das Tool nichts weiss. Hier entscheidet das Modell **nie** über Zahlen.
`monatszahlen` rechnet alles aus, das Modell formuliert nur, und
`pruefe_bericht` weist jeden Betrag zurück, der nicht aus der Rechnung stammt.

Zwei Tools statt einem heisst: der Agent muss seinen eigenen Entwurf durch die
Prüfung schicken und bei einer Beanstandung nachbessern.

Start:
    python3 bericht_agent.py 2026-07
"""

import asyncio
import sys

from claude_agent_sdk import (
    query, ClaudeAgentOptions,
    tool, create_sdk_mcp_server,
    AssistantMessage, TextBlock, ToolUseBlock, ResultMessage,
)

# Fachlogik aus dem Tool-Modul — die Rechnung steht nur an einer Stelle.
from bericht_tool import (
    MIN_TAGE,
    SYSTEM_PROMPT,
    aggregiere,
    formatiere,
    lade_transaktionen,
    pruefe,
)

# Windows-Konsole auf UTF-8 umstellen (siehe first_agent.py)
sys.stdout.reconfigure(encoding="utf-8")

# Einmal laden, nicht pro Tool-Aufruf — die CSV ändert sich während des Laufs nicht.
TRANSAKTIONEN = lade_transaktionen()


# ── Schritt 1: Zwei Tools definieren ────────────────────────────
# Tool A liefert Fakten, Tool B prüft, was das Modell daraus gemacht hat.

@tool(
    "monatszahlen",
    "Liefert alle Zahlen eines Monats: Einkommen, Gesamtausgaben, Saldo, "
    "Ausgaben je Kategorie mit Anteil und Vormonatsvergleich sowie das "
    "Sparpotenzial. Einzige erlaubte Zahlenquelle für den Bericht.",
    {"monat": str},  # Format YYYY-MM
)
async def monatszahlen(args):
    monat = (args.get("monat") or "").strip()
    zahlen = aggregiere(TRANSAKTIONEN, monat)

    if zahlen.anzahl == 0:
        return {
            "content": [{"type": "text", "text": f"Keine Transaktionen für {monat}."}],
            "is_error": True,
        }

    # US-09, AC 3: zu wenig Daten -> Hinweis statt Bericht. Wie beim leeren
    # Transaktionstext in kategorisierung_tool.py bricht is_error den Loop nicht ab.
    if zahlen.abgedeckte_tage < MIN_TAGE:
        return {
            "content": [{
                "type": "text",
                "text": (
                    f"Für {monat} liegen nur {zahlen.abgedeckte_tage} Tage an Daten vor "
                    f"(nötig: {MIN_TAGE}). Teile der Nutzerin mit, dass noch zu wenig "
                    f"Daten für einen Bericht vorhanden sind, und schreibe keinen Bericht."
                ),
            }],
            "is_error": True,
        }

    return {"content": [{"type": "text", "text": formatiere(zahlen)}]}


@tool(
    "pruefe_bericht",
    "Prüft einen Berichtsentwurf gegen die Zahlen des Monats. Meldet jeden "
    "CHF-Betrag und jeden Prozentwert, der nicht aus der Aggregation stammt.",
    {"monat": str, "entwurf": str},
)
async def pruefe_bericht(args):
    # Bewusst zustandslos: der Monat kommt mit, statt das Ergebnis des letzten
    # monatszahlen-Aufrufs in einer Modulvariable zu halten.
    zahlen = aggregiere(TRANSAKTIONEN, (args.get("monat") or "").strip())
    beanstandet = pruefe(args.get("entwurf") or "", zahlen)

    if beanstandet:
        return {
            "content": [{
                "type": "text",
                "text": (
                    "Diese Werte stehen nicht in den Monatszahlen: "
                    + ", ".join(beanstandet)
                    + ". Ersetze sie durch die Werte aus `monatszahlen` und prüfe erneut."
                ),
            }],
            "is_error": True,
        }

    return {"content": [{"type": "text", "text": "Geprüft: alle Zahlen stammen aus der Aggregation."}]}


# ── Schritt 2: Beide Tools in einen In-Process-MCP-Server ───────
bericht = create_sdk_mcp_server(
    name="bericht", version="1.0.0", tools=[monatszahlen, pruefe_bericht]
)


# ── Schritt 3: Server anschliessen + Tools erlauben ─────────────
opts = ClaudeAgentOptions(
    # Sonnet statt Haiku: der Bericht läuft 1x pro Nutzer und Monat, und hier
    # zählt die Sprachqualität (CLAUDE.md, Tech-Stack AI/ML).
    model="claude-sonnet-5",
    # Ohne effort denkt der Agent auf Claude-Code-Niveau (xhigh) — der erste Lauf
    # kostete so $0.95. Hier ist nichts zu lösen: die Zahlen stehen fertig da,
    # das Modell formuliert sie nur. Thinking bleibt adaptiv, weil das Modell mit
    # abgeschaltetem Thinking seltener zum Tool greift.
    effort="low",
    system_prompt=SYSTEM_PROMPT,
    mcp_servers={"bericht": bericht},
    # tools=[] schaltet die eingebauten Claude-Code-Tools ab.
    tools=[],
    # Ohne diese Zeile lädt die CLI "user" und "project" — also CLAUDE.md, die
    # Projekt-Skills und alle installierten Plugins. Das waren ~88'000 Tokens
    # Kontext pro Request und der Grund für die ersten Kostenausreisser. Leer
    # gesetzt läuft der Agent ausserdem bei allen gleich, unabhängig davon,
    # was lokal installiert ist.
    setting_sources=[],
    allowed_tools=[
        "mcp__bericht__monatszahlen",
        "mcp__bericht__pruefe_bericht",
    ],
    # Höher als beim Kategorisierer: Entwurf, Prüfung und mindestens eine
    # Korrekturrunde brauchen mehrere Turns.
    max_turns=8,
    max_budget_usd=0.50,
)


# ── Schritt 4: Bericht anfordern ────────────────────────────────
async def main():
    monat = sys.argv[1] if len(sys.argv) > 1 else "2026-07"
    frage = f"Schreibe den Monatsbericht für {monat}."

    try:
        async for msg in query(prompt=frage, options=opts):
            if isinstance(msg, AssistantMessage):
                for block in msg.content:
                    if isinstance(block, ToolUseBlock):
                        # Beim Prüf-Tool nur den Namen zeigen — der Entwurf im Input
                        # wäre der halbe Bericht und macht die Ausgabe unlesbar.
                        if block.name.endswith("pruefe_bericht"):
                            print(">>> TOOL-CALL: pruefe_bericht")
                        else:
                            print(f">>> TOOL-CALL: {block.name}  Input: {block.input}")
                    elif isinstance(block, TextBlock):
                        print(block.text)
            elif isinstance(msg, ResultMessage):
                kosten = f"${msg.total_cost_usd:.4f}" if msg.total_cost_usd is not None else "unbekannt"
                print(f"\n--- Turns: {msg.num_turns} · Kosten: {kosten} ---")
    except Exception as e:
        # max_budget_usd und max_turns beendet die CLI mit einer Exception. Das ist
        # der Guardrail bei der Arbeit, kein Absturz — also sauber melden statt
        # Traceback. Alles andere fliegt weiter.
        if "budget" not in str(e).lower() and "max turns" not in str(e).lower():
            raise
        print(f"\n--- Abgebrochen: {e} ---")

asyncio.run(main())
