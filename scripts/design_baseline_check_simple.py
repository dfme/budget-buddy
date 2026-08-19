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
)
sys.stdout.reconfigure(encoding="utf-8")

reviewer = AgentDefinition(
    description="Check the Angular frontend against the Variante A «Klarheit» design baseline.",
    prompt="Check the Angular frontend against the Variante A «Klarheit» design baseline. Source of truth is design/variant-a/ (Design-Entscheid FE-UI-01 / ADR-11). When the two sides disagree, the frontend is what gets corrected — never the baseline.",
    tools=["Read", "Grep", "Glob", "Bash"],
    model="haiku",   # Billig-Modell für die Routine-Rolle
)

opts = ClaudeAgentOptions(
    model="claude-sonnet-4-6",
    agents={"reviewer": reviewer},
    allowed_tools=["Read", "Grep"],
    max_turns=15,
    max_budget_usd=0.50,
)

async def main():
    async for msg in query(
        prompt="Reviewe bitte die Komponente 'dashboard'",
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