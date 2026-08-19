"""Check the Angular frontend against the Variante A «Klarheit» design baseline.

Deterministic counterpart to the `design-baseline-checker` subagent
(.claude/agents/design-baseline-checker.md): same five checks, no LLM, so it can
run in CI and fail on drift instead of relying on someone remembering to look.

Source of truth is design/variant-a/ (Design-Entscheid FE-UI-01 / ADR-11). When
the two sides disagree, the frontend is what gets corrected — never the baseline.
"""
import asyncio, sys
from claude_agent_sdk import (
    query, ClaudeAgentOptions, AgentDefinition,
    AssistantMessage, TextBlock, ToolUseBlock, ResultMessage,
    PermissionResultAllow, PermissionResultDeny,   # ohne die: NameError im Gate
)
sys.stdout.reconfigure(encoding="utf-8")

reviewer = AgentDefinition(
    description="Check the Angular frontend against the Variante A «Klarheit» design baseline.",
    prompt="Check the Angular frontend against the Variante A «Klarheit» design baseline. Source of truth is design/variant-a/ (Design-Entscheid FE-UI-01 / ADR-11). When the two sides disagree, the frontend is what gets corrected — never the baseline.",
    tools=["Read", "Grep", "Glob"],
    model="haiku",   # Billig-Modell für die Routine-Rolle
)

audit_log = []
async def gate(tool_name, input_data, context):
    audit_log.append((tool_name, str(input_data)[:60]))
    print(f">>> CALL: {tool_name}  Input: {str(input_data)[:80]}")

    if tool_name == "Git" and "rm -rf" in str(input_data):
       return PermissionResultDeny(message="Blockiert!")
    return PermissionResultAllow()

opts = ClaudeAgentOptions(
    can_use_tool=gate,
    model="claude-sonnet-4-6",
    agents={"reviewer": reviewer},
    # allowed_tools bewusst NICHT gesetzt: ein Eintrag, der ein ganzes Tool
    # freigibt, genehmigt es, BEVOR das Gate gefragt wird. Mit ["Read","Grep"]
    # sähe `gate` ausgerechnet die Tools nicht, die der reviewer benutzt.
    # Das SDK warnt darüber (CanUseToolShadowedWarning).
    #
    # setting_sources=[] aus demselben Grund: Default None = "alle Quellen
    # laden", womit die 191 Allow-Regeln aus .claude/settings.local.json
    # (`Bash(git diff *)`, `Bash(git checkout *)`, …) am Gate vorbeizögen.
    # Preis: ohne "project" wird CLAUDE.md nicht geladen — hier egal, der
    # reviewer bringt seine Anweisung selbst mit.
    setting_sources=[],
    max_turns=30,
    max_budget_usd=0.30,
)

PROMPT = (
    "Führe ein Design Baseline Review für die Komponente "
    "'frontend/src/app/dashboard' durch. Verwende das gegebene Tool. "
    "Mache keine Git Calls. Schaue dir nur die gegebene Komponente an, keine Referenzen."
)


async def single_prompt(text: str):
    """`can_use_tool` erzwingt Streaming-Modus: der Prompt muss ein AsyncIterable
    von Dicts sein, kein String. Diese Dict-Form gibt der query()-Docstring vor."""
    yield {
        "type": "user",
        "message": {"role": "user", "content": text},
        "parent_tool_use_id": None,
        "session_id": "design-baseline-check",
    }


async def main():
    async for msg in query(
        prompt=single_prompt(PROMPT),
        options=opts,
    ):
        if isinstance(msg, AssistantMessage):
            for b in msg.content:
                if isinstance(b, ToolUseBlock):
                    print(f">>> TOOL: {b.name}  Input: {str(b.input)[:80]}")
                elif isinstance(b, TextBlock):
                    print(b.text)
        elif isinstance(msg, ResultMessage):
            print(f"\n--- Turns: {msg.num_turns} · Kosten: ${msg.total_cost_usd:.4f} ---")

asyncio.run(main())