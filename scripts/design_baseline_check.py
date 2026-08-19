#!/usr/bin/env python3
"""Check the Angular frontend against the Variante A «Klarheit» design baseline.

Deterministic counterpart to the `design-baseline-checker` subagent
(.claude/agents/design-baseline-checker.md): same five checks, no LLM, so it can
run in CI and fail on drift instead of relying on someone remembering to look.

Source of truth is design/variant-a/ (Design-Entscheid FE-UI-01 / ADR-11). When
the two sides disagree, the frontend is what gets corrected — never the baseline.

Usage:
    python3 scripts/design_baseline_check.py
    python3 scripts/design_baseline_check.py --scope frontend/src/app/dashboard
    python3 scripts/design_baseline_check.py --changed
    python3 scripts/design_baseline_check.py --json
    python3 scripts/design_baseline_check.py --fail-on never

Exit codes: 0 = sauber (bzw. nichts über der Schwelle), 1 = Befunde, 2 = Setup-Fehler.
Stdlib only — keine Installation, kein venv.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import dataclass, asdict
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

BASELINE_SCSS = ROOT / "design/variant-a/styles.scss"
FE_STYLES = ROOT / "frontend/src/styles.scss"
FE_TOKENS = ROOT / "frontend/src/styles/_tokens.scss"
FE_APP = ROOT / "frontend/src/app"
CATEGORY_JAVA = ROOT / "backend/src/main/java/com/budgetbuddy/categorization/Category.java"

SEVERITIES = ("hoch", "mittel", "niedrig")

# Rohwerte, die keine Token-Entsprechung haben und deshalb erlaubt sind.
ALLOWED_RAW = {"0", "0px", "auto", "100%", "50%", "1px", "2px", "none", "inherit", "initial"}
# Properties, in denen rohe px-Werte legitim sind (Rahmenstärken, Schatten-Offsets).
RAW_OK_PROPS = ("border", "outline", "box-shadow", "text-shadow", "stroke-width", "transform")


@dataclass
class Finding:
    severity: str
    check: str
    file: str
    line: int
    message: str
    expected: str = ""

    def sort_key(self):
        return (SEVERITIES.index(self.severity), self.check, self.file, self.line)


# --------------------------------------------------------------------------- #
# SCSS-Parsing
# --------------------------------------------------------------------------- #

def strip_comments(text: str) -> str:
    """Entfernt // und /* */ Kommentare. Das `(?<!:)` schützt `https://`."""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"(?<!:)//[^\n]*", "", text)


def normalize(value: str) -> str:
    """Vergleichbar machen: Whitespace kollabieren, Quotes vereinheitlichen."""
    value = value.replace('"', "'")
    value = re.sub(r"\s+", " ", value)
    return value.strip().rstrip(";").strip()


def iter_root_blocks(text: str):
    """Liefert (selector, body, startzeile) für jeden :root-Block.

    Nicht über den Selektor-Text greifen: der Hell-Block beginnt in beiden
    Dateien mit `:root,` und die Quote-Schreibweise unterscheidet sich
    (`"dark"` in der Baseline, `'dark'` im Frontend). Deshalb Klammern zählen.
    """
    lines = text.splitlines()
    i = 0
    while i < len(lines):
        if lines[i].lstrip().startswith(":root"):
            selector_parts, j = [], i
            while j < len(lines) and "{" not in lines[j]:
                selector_parts.append(lines[j])
                j += 1
            if j >= len(lines):
                break
            selector_parts.append(lines[j].split("{")[0])
            depth = lines[j].count("{") - lines[j].count("}")
            body_start = j + 1
            k = body_start
            while k < len(lines) and depth > 0:
                depth += lines[k].count("{") - lines[k].count("}")
                k += 1
            yield " ".join(p.strip() for p in selector_parts).strip(), lines[body_start : k - 1], body_start + 1
            i = k
        else:
            i += 1


def theme_blocks(path: Path) -> dict[str, tuple[list[str], int]]:
    """Ordnet die :root-Blöcke den Themes 'light'/'dark' zu."""
    text = strip_comments(path.read_text(encoding="utf-8"))
    out: dict[str, tuple[list[str], int]] = {}
    for selector, body, start in iter_root_blocks(text):
        theme = "dark" if "dark" in selector else "light"
        out.setdefault(theme, (body, start))
    return out


def custom_props(body: list[str], start_line: int) -> dict[str, tuple[str, int]]:
    props = {}
    for offset, line in enumerate(body):
        m = re.match(r"\s*(--[a-z0-9-]+)\s*:\s*([^;]+);", line)
        if m:
            props[m.group(1)] = (normalize(m.group(2)), start_line + offset)
    return props


def scss_vars(path: Path) -> dict[str, tuple[str, int]]:
    """Top-Level `$name: wert;` — mehrzeilige Werte (prettier) zusammengeführt."""
    raw = path.read_text(encoding="utf-8").splitlines()
    clean = strip_comments(path.read_text(encoding="utf-8")).splitlines()
    out: dict[str, tuple[str, int]] = {}
    i = 0
    while i < len(clean):
        m = re.match(r"\$([a-z0-9-]+)\s*:\s*(.*)$", clean[i])
        if m and not m.group(2).lstrip().startswith("("):
            name, value, lineno = m.group(1), m.group(2), i + 1
            while ";" not in value and i + 1 < len(clean):
                i += 1
                value += " " + clean[i].strip()
            out[name] = (normalize(value), lineno)
        i += 1
    del raw
    return out


def categories_map(path: Path) -> dict[str, str]:
    text = strip_comments(path.read_text(encoding="utf-8"))
    m = re.search(r"\$categories\s*:\s*\((.*?)\)\s*;", text, flags=re.S)
    if not m:
        return {}
    return {
        k: normalize(v)
        for k, v in re.findall(r"['\"]([a-z0-9-]+)['\"]\s*:\s*([^,\n]+)", m.group(1))
    }


def category_slugs(path: Path) -> list[str]:
    text = path.read_text(encoding="utf-8")
    body = text.split("{", 1)[1] if "{" in text else text
    return [c.lower() for c in re.findall(r"^\s*([A-Z][A-Z_]*)\s*\(", body, flags=re.M)]


# --------------------------------------------------------------------------- #
# Prüfungen
# --------------------------------------------------------------------------- #

def check_token_parity() -> list[Finding]:
    findings: list[Finding] = []
    base, fe = theme_blocks(BASELINE_SCSS), theme_blocks(FE_STYLES)
    for theme in ("light", "dark"):
        if theme not in base or theme not in fe:
            findings.append(Finding("hoch", "Token-Parität", rel(FE_STYLES), 1,
                                    f"Theme-Block '{theme}' fehlt auf einer Seite"))
            continue
        b = custom_props(*base[theme])
        f = custom_props(*fe[theme])
        for name, (value, bline) in b.items():
            if name not in f:
                findings.append(Finding("hoch", "Token-Parität", rel(FE_STYLES), fe[theme][1],
                                        f"{name} fehlt im {theme}-Theme",
                                        f"{value} (Baseline Z. {bline})"))
            elif f[name][0] != value:
                findings.append(Finding("hoch", "Token-Parität", rel(FE_STYLES), f[name][1],
                                        f"{name}: {f[name][0]} weicht ab ({theme})",
                                        f"{value} (Baseline Z. {bline})"))
        for name, (value, fline) in f.items():
            if name not in b:
                findings.append(Finding("mittel", "Token-Parität", rel(FE_STYLES), fline,
                                        f"{name} ({theme}) existiert in der Baseline nicht",
                                        "in der Baseline ergänzen oder entfernen"))
    return findings


def check_scale_parity() -> list[Finding]:
    findings: list[Finding] = []
    base, fe = scss_vars(BASELINE_SCSS), scss_vars(FE_TOKENS)
    prefixes = ("fs-", "sp-", "r-", "bp-", "ff-", "shadow-")
    for name, (value, bline) in base.items():
        if not name.startswith(prefixes):
            continue
        if name not in fe:
            findings.append(Finding("hoch", "Skalen-Parität", rel(FE_TOKENS), 1,
                                    f"${name} fehlt", f"{value} (Baseline Z. {bline})"))
        elif fe[name][0] != value:
            findings.append(Finding("hoch", "Skalen-Parität", rel(FE_TOKENS), fe[name][1],
                                    f"${name}: {fe[name][0]} weicht ab",
                                    f"{value} (Baseline Z. {bline})"))

    text = FE_TOKENS.read_text(encoding="utf-8")
    for lineno, line in enumerate(strip_comments(text).splitlines(), 1):
        if re.match(r"\s*(:root|[.#\[a-zA-Z][^:\n]*)\{", line) and "@mixin" not in line:
            findings.append(Finding("hoch", "Skalen-Parität", rel(FE_TOKENS), lineno,
                                    "_tokens.scss gibt CSS aus",
                                    "Partial muss nebeneffektfrei bleiben — wird in jede Komponente dupliziert"))
    return findings


def check_categories() -> list[Finding]:
    findings: list[Finding] = []
    slugs = category_slugs(CATEGORY_JAVA)
    if not slugs:
        return [Finding("hoch", "Kategorien", rel(CATEGORY_JAVA), 1, "Keine Enum-Konstanten gefunden")]
    fe = theme_blocks(FE_STYLES)
    cmap = categories_map(FE_TOKENS)
    for theme in ("light", "dark"):
        if theme not in fe:
            continue
        props = custom_props(*fe[theme])
        for slug in slugs:
            if f"--cat-{slug}" not in props:
                findings.append(Finding("hoch", "Kategorien", rel(FE_STYLES), fe[theme][1],
                                        f"--cat-{slug} fehlt im {theme}-Theme",
                                        f"Category.{slug.upper()} hat keine Farbe"))
        for name, (_, lineno) in props.items():
            if name.startswith("--cat-") and name[6:] not in slugs:
                findings.append(Finding("mittel", "Kategorien", rel(FE_STYLES), lineno,
                                        f"{name} ({theme}) hat kein Enum-Gegenstück"))
    for slug in slugs:
        if slug not in cmap:
            findings.append(Finding("hoch", "Kategorien", rel(FE_TOKENS), 1,
                                    f"'{slug}' fehlt in der $categories-Map"))
    for key in cmap:
        if key not in slugs:
            findings.append(Finding("mittel", "Kategorien", rel(FE_TOKENS), 1,
                                    f"$categories enthält '{key}' ohne Enum-Gegenstück"))
    return findings


def check_raw_values(files: list[Path]) -> list[Finding]:
    findings: list[Finding] = []
    bp_desktop = scss_vars(FE_TOKENS).get("bp-desktop", ("900px", 0))[0]

    for path in files:
        raw_lines = path.read_text(encoding="utf-8").splitlines()
        clean_lines = strip_comments(path.read_text(encoding="utf-8")).splitlines()

        for lineno, line in enumerate(clean_lines, 1):
            stripped = line.strip()
            if not stripped or stripped.startswith("@use"):
                continue
            # Bewusste Ausnahmen bleiben im Code dokumentiert, nicht in einer Liste hier.
            if is_suppressed(raw_lines, lineno):
                continue

            for hexval in re.findall(r"#[0-9a-fA-F]{3,8}\b", stripped):
                sev = "niedrig" if hexval.lower() in ("#fff", "#ffffff", "#000", "#000000") else "mittel"
                findings.append(Finding(sev, "Rohwert", rel(path), lineno,
                                        f"Hex-Farbe {hexval}", "Token aus styles.scss verwenden"))

            m = re.search(r"\brgba?\(([^)]*)\)", stripped)
            if m:
                channels = re.findall(r"\d+(?:\.\d+)?%?", m.group(1))[:3]
                neutral = all(c in ("0", "255", "0%", "100%") for c in channels) and len(channels) == 3
                findings.append(Finding("niedrig" if neutral else "mittel", "Rohwert", rel(path), lineno,
                                        "neutraler rgb()-Schleier" if neutral else "rgb()/rgba()-Literal",
                                        "ok, wenn bewusst themeneutral — sonst Token"
                                        if neutral else "Token aus styles.scss verwenden"))

            for prop, token in (("font-size", "$fs-*"), ("border-radius", "$r-*")):
                m = re.match(rf"{prop}\s*:\s*([^;]+);", stripped)
                # `var(...)` ist eine bewusste Komponenten-API (Host überschreibt), kein Rohwert.
                if m and "$" not in m.group(1) and "var(" not in m.group(1) \
                        and normalize(m.group(1)) not in ALLOWED_RAW:
                    findings.append(Finding("mittel", "Rohwert", rel(path), lineno,
                                            f"{prop}: {normalize(m.group(1))}", f"{token} verwenden"))

            m = re.match(r"(padding|margin|gap|row-gap|column-gap)(-[a-z]+)?\s*:\s*([^;]+);", stripped)
            if m and "$" not in m.group(3) and "var(" not in m.group(3) \
                    and not stripped.startswith(RAW_OK_PROPS):
                values = normalize(m.group(3)).split()
                if any(re.fullmatch(r"-?\d*\.?\d+(px|rem|em)", v) and v not in ALLOWED_RAW for v in values):
                    findings.append(Finding("mittel", "Rohwert", rel(path), lineno,
                                            f"{m.group(1)}: {normalize(m.group(3))}", "$sp-* verwenden"))

            m = re.search(r"@media\s*\(\s*min-width\s*:\s*([^)]+)\)", stripped)
            if m:
                width = normalize(m.group(1))
                if px(width) == px(bp_desktop):
                    findings.append(Finding("niedrig", "Rohwert", rel(path), lineno,
                                            f"Handgeschriebene Media-Query ({width})",
                                            "@include desktop verwenden"))
                else:
                    findings.append(Finding("mittel", "Breakpoint", rel(path), lineno,
                                            f"Breakpoint {width} ist kein Token",
                                            f"nur $bp-desktop ({bp_desktop}) ist definiert"))
    return findings


def check_token_usage(files: list[Path]) -> list[Finding]:
    findings: list[Finding] = []
    defined = {n for n in scss_vars(FE_TOKENS)}
    known_prefixes = ("c-", "fs-", "sp-", "r-", "bp-", "ff-", "shadow-")

    for path in files:
        for lineno, line in enumerate(strip_comments(path.read_text(encoding="utf-8")).splitlines(), 1):
            if re.match(r"\s*\$[a-z0-9-]+\s*:", line):
                continue  # lokale Variable, keine Token-Nutzung
            for name in re.findall(r"\$([a-z0-9-]+)", line):
                if name.startswith(known_prefixes) and name not in defined:
                    findings.append(Finding("hoch", "Token-Nutzung", rel(path), lineno,
                                            f"${name} ist in _tokens.scss nicht definiert",
                                            "Build bricht beim nächsten Compile"))

    # Tote Tokens: Definition und Alias-Zeile aus der Suche ausklammern.
    haystack = []
    for path in FE_APP.rglob("*"):
        if path.suffix in (".scss", ".html", ".ts") and path.is_file():
            haystack.append(path.read_text(encoding="utf-8"))
    haystack.append(re.sub(r"^\s*--[a-z0-9-]+\s*:.*$", "", FE_STYLES.read_text(encoding="utf-8"), flags=re.M))
    blob = "\n".join(haystack)

    fe_light = theme_blocks(FE_STYLES).get("light")
    if fe_light:
        for name, (_, lineno) in custom_props(*fe_light).items():
            alias = "$" + name[2:]
            if alias not in blob and f"var({name})" not in blob and name[6:] not in categories_map(FE_TOKENS):
                findings.append(Finding("niedrig", "Token-Nutzung", rel(FE_STYLES), lineno,
                                        f"{name} wird nirgends verwendet"))
    return findings


# --------------------------------------------------------------------------- #
# Rahmen
# --------------------------------------------------------------------------- #

def is_suppressed(raw_lines: list[str], lineno: int) -> bool:
    """`// baseline-check: ignore` auf der Zeile selbst oder direkt darüber."""
    pragma = "baseline-check: ignore"
    if lineno <= len(raw_lines) and pragma in raw_lines[lineno - 1]:
        return True
    return lineno >= 2 and pragma in raw_lines[lineno - 2]


def px(value: str) -> float | None:
    """rem/px auf px normalisieren (16px Basis), damit 40rem und 640px vergleichbar sind."""
    m = re.fullmatch(r"(-?\d*\.?\d+)(px|rem|em)?", value.strip())
    if not m:
        return None
    n = float(m.group(1))
    return n * 16 if m.group(2) in ("rem", "em") else n


def rel(path: Path) -> str:
    try:
        return str(path.relative_to(ROOT))
    except ValueError:
        return str(path)


def component_files(scope: str | None, changed: bool) -> list[Path]:
    if changed:
        try:
            base = subprocess.run(["git", "merge-base", "HEAD", "main"], cwd=ROOT,
                                  capture_output=True, text=True, check=True).stdout.strip()
            out = subprocess.run(["git", "diff", "--name-only", base, "--", "frontend/src/app"],
                                 cwd=ROOT, capture_output=True, text=True, check=True).stdout
        except (subprocess.CalledProcessError, FileNotFoundError) as exc:
            print(f"warn: --changed nicht auswertbar ({exc}); prüfe alles", file=sys.stderr)
            return sorted(FE_APP.rglob("*.scss"))
        return [ROOT / p for p in out.split() if p.endswith(".scss") and (ROOT / p).exists()]
    root = ROOT / scope if scope else FE_APP
    if not root.exists():
        sys.exit(f"error: Scope existiert nicht: {root}")
    return sorted(root.rglob("*.scss")) if root.is_dir() else [root]


def dedupe(findings: list[Finding]) -> list[Finding]:
    """Token-Parität und Kategorien überlappen bei --cat-*: der speziellere Befund gewinnt."""
    cat_hits = {(f.file, f.line, f.message.split()[0]) for f in findings if f.check == "Kategorien"}
    return [f for f in findings
            if not (f.check == "Token-Parität" and (f.file, f.line, f.message.split()[0]) in cat_hits)]


def render(findings: list[Finding], scope_label: str, checked: list[str]) -> str:
    counts = {s: sum(1 for f in findings if f.severity == s) for s in SEVERITIES}
    lines = [f"## Design-Baseline-Check — {scope_label}", ""]
    if not findings:
        lines += ["**Verdikt:** GRÜN", ""] + [f"- {c}" for c in checked]
        return "\n".join(lines)
    noun = "Befund" if len(findings) == 1 else "Befunde"
    lines += [f"**Verdikt:** ABWEICHUNGEN ({len(findings)} {noun}, "
              f"davon {counts['hoch']} hoch / {counts['mittel']} mittel / {counts['niedrig']} niedrig)", ""]
    lines += ["| # | Sev | Prüfung | Ort | Befund | Soll |", "|---|-----|---------|-----|--------|------|"]
    for i, f in enumerate(sorted(findings, key=Finding.sort_key), 1):
        lines.append(f"| {i} | {f.severity} | {f.check} | {f.file}:{f.line} | {f.message} | {f.expected} |")
    return "\n".join(lines)


def main() -> int:
    ap = argparse.ArgumentParser(description="Frontend gegen die Design-Baseline Variante A prüfen.")
    ap.add_argument("--scope", help="Nur diesen Pfad auf Rohwerte prüfen (relativ zum Repo-Root)")
    ap.add_argument("--changed", action="store_true", help="Nur gegenüber main geänderte Komponenten-SCSS")
    ap.add_argument("--json", action="store_true", help="Befunde als JSON ausgeben")
    ap.add_argument("--fail-on", choices=[*SEVERITIES, "never"], default="mittel",
                    help="Ab welcher Severity Exit 1 (Default: mittel)")
    args = ap.parse_args()

    for required in (BASELINE_SCSS, FE_STYLES, FE_TOKENS, CATEGORY_JAVA):
        if not required.exists():
            sys.exit(f"error: fehlt: {rel(required)}")

    files = component_files(args.scope, args.changed)

    findings = check_token_parity() + check_scale_parity() + check_categories()
    findings += check_raw_values(files) + check_token_usage(files)
    findings = dedupe(findings)

    checked = [
        f"Token-Parität: {len(custom_props(*theme_blocks(FE_STYLES)['light']))} Tokens je Theme, identisch",
        f"Skalen-Parität: {sum(1 for n in scss_vars(BASELINE_SCSS) if n.startswith(('fs-','sp-','r-','bp-','ff-','shadow-')))} Variablen, identisch",
        f"Kategorien: {len(category_slugs(CATEGORY_JAVA))} Enum-Konstanten vollständig abgedeckt",
        f"Rohwerte: {len(files)} Komponenten-SCSS geprüft",
        "Token-Nutzung: keine undefinierten oder toten Tokens",
    ]

    scope_label = args.scope or ("geänderte Dateien" if args.changed else "gesamtes Frontend")
    if args.json:
        print(json.dumps([asdict(f) for f in sorted(findings, key=Finding.sort_key)],
                         indent=2, ensure_ascii=False))
    else:
        print(render(findings, scope_label, checked))

    if args.fail_on == "never":
        return 0
    threshold = SEVERITIES.index(args.fail_on)
    return 1 if any(SEVERITIES.index(f.severity) <= threshold for f in findings) else 0


if __name__ == "__main__":
    sys.exit(main())
