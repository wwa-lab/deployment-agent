#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path


DOC_PATTERNS = [
    "README.md",
    "README*.md",
    "docs/**/*.md",
    "tasks/**/*.md",
    "AGENTS.md",
    "CLAUDE.md",
]

SHELL_LANGS = {"", "bash", "console", "shell", "sh", "zsh"}
COMMON_COMMAND_PREFIXES = (
    "$ ",
    "./",
    "cd ",
    "curl ",
    "docker ",
    "export ",
    "git ",
    "java ",
    "make ",
    "mvn ",
    "node ",
    "npm ",
    "npx ",
    "pnpm ",
    "python ",
    "python3 ",
    "uv ",
    "yarn ",
)

LINK_RE = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
CODE_SPAN_RE = re.compile(r"`([^`\n]+)`")
FENCE_RE = re.compile(r"```(?P<lang>[^\n`]*)\n(?P<body>.*?)```", re.DOTALL)
API_RE = re.compile(r"(?<![A-Za-z0-9_.-])(/api/[A-Za-z0-9_./{}:-]*)")
ENV_RE = re.compile(r"\$(?:\{)?([A-Z][A-Z0-9_]{2,})(?:\})?|\b([A-Z][A-Z0-9_]{2,})=")
FILELIKE_RE = re.compile(
    r"^(?:\.{1,2}/)?(?:[\w.-]+/)*[\w.-]+\.(?:"
    r"md|txt|json|ya?ml|xml|properties|sql|java|kt|groovy|scala|js|jsx|ts|tsx|vue|css|scss|html|sh|py"
    r")$"
)
DIRLIKE_RE = re.compile(r"^(?:\.{1,2}/)?(?:[\w.-]+/)+$")
PLACEHOLDER_PATTERNS = {
    "TODO": re.compile(r"\bTODO\b"),
    "TBD": re.compile(r"\bTBD\b"),
    "FIXME": re.compile(r"\bFIXME\b"),
    "XXX": re.compile(r"\bXXX\b"),
    "WIP": re.compile(r"\bWIP\b"),
    "template": re.compile(r"\{\{[^}\n]+\}\}"),
}


def discover_docs(root: Path, explicit_paths: list[str]) -> list[Path]:
    if explicit_paths:
        docs: list[Path] = []
        for raw in explicit_paths:
            path = (root / raw).resolve() if not Path(raw).is_absolute() else Path(raw).resolve()
            if path.is_dir():
                docs.extend(sorted(p for p in path.rglob("*.md") if p.is_file()))
            elif path.is_file():
                docs.append(path)
        return dedupe_paths(docs)

    docs = []
    for pattern in DOC_PATTERNS:
        docs.extend(sorted(p for p in root.glob(pattern) if p.is_file()))
    return dedupe_paths(docs)


def dedupe_paths(paths: list[Path]) -> list[Path]:
    seen: set[Path] = set()
    deduped: list[Path] = []
    for path in paths:
        resolved = path.resolve()
        if resolved in seen:
            continue
        seen.add(resolved)
        deduped.append(resolved)
    return sorted(deduped)


def relative_to_root(path: Path, root: Path) -> str:
    try:
        return str(path.relative_to(root))
    except ValueError:
        return str(path)


def line_number_for_offset(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def strip_fragment(target: str) -> str:
    target = target.strip()
    if target.startswith("<") and target.endswith(">"):
        target = target[1:-1]
    target = target.split("#", 1)[0]
    target = target.split("?", 1)[0]
    return target.strip()


def is_local_relative_target(target: str) -> bool:
    if not target:
        return False
    lowered = target.lower()
    if lowered.startswith(("http://", "https://", "mailto:", "tel:", "javascript:")):
        return False
    if target.startswith(("#", "/")):
        return False
    return True


def looks_like_path(token: str) -> bool:
    if "://" in token or "@" in token or "*" in token or "|" in token:
        return False
    if "/" not in token and not token.startswith("."):
        return False
    return bool(FILELIKE_RE.match(token) or DIRLIKE_RE.match(token))


def candidate_exists(doc_path: Path, root: Path, candidate: str) -> bool:
    normalized = candidate.rstrip("/")
    doc_relative = (doc_path.parent / normalized).resolve()
    if doc_relative.exists():
        return True
    root_relative = (root / normalized).resolve()
    return root_relative.exists()


def is_shell_command(line: str, lang: str) -> bool:
    stripped = line.strip()
    if not stripped or stripped.startswith("#"):
        return False
    if lang and lang in SHELL_LANGS:
        return True
    return stripped.startswith(COMMON_COMMAND_PREFIXES)


def append_unique(bucket: list[dict], item: dict, dedupe_key: tuple) -> None:
    if dedupe_key not in append_unique.seen:
        append_unique.seen.add(dedupe_key)
        bucket.append(item)


append_unique.seen = set()


def analyze_doc(doc_path: Path, root: Path, report: dict) -> None:
    text = doc_path.read_text(encoding="utf-8", errors="ignore")
    rel_doc = relative_to_root(doc_path, root)

    for match in LINK_RE.finditer(text):
        raw_target = match.group(1).strip()
        target = strip_fragment(raw_target)
        if not is_local_relative_target(target):
            continue
        if candidate_exists(doc_path, root, target):
            continue
        append_unique(
            report["broken_links"],
            {
                "doc": rel_doc,
                "line": line_number_for_offset(text, match.start(1)),
                "target": raw_target,
            },
            (rel_doc, raw_target),
        )

    for match in CODE_SPAN_RE.finditer(text):
        token = match.group(1).strip()
        if not looks_like_path(token):
            continue
        if candidate_exists(doc_path, root, token):
            continue
        append_unique(
            report["missing_path_references"],
            {
                "doc": rel_doc,
                "line": line_number_for_offset(text, match.start(1)),
                "reference": token,
            },
            (rel_doc, token),
        )

    for label, pattern in PLACEHOLDER_PATTERNS.items():
        for line_no, line in enumerate(text.splitlines(), start=1):
            if not pattern.search(line):
                continue
            append_unique(
                report["placeholders"],
                {
                    "doc": rel_doc,
                    "line": line_no,
                    "marker": label,
                    "text": line.strip(),
                },
                (rel_doc, line_no, label),
            )

    for match in FENCE_RE.finditer(text):
        lang = match.group("lang").strip().lower()
        if lang not in SHELL_LANGS:
            continue
        body = match.group("body")
        fence_line = line_number_for_offset(text, match.start("body"))
        for offset, line in enumerate(body.splitlines()):
            if not is_shell_command(line, lang):
                continue
            command = line.strip()
            if command.startswith("$ "):
                command = command[2:].strip()
            append_unique(
                report["commands"],
                {
                    "doc": rel_doc,
                    "line": fence_line + offset,
                    "command": command,
                },
                (rel_doc, command),
            )

    for match in API_RE.finditer(text):
        api_path = match.group(1)
        append_unique(
            report["api_paths"],
            {
                "doc": rel_doc,
                "line": line_number_for_offset(text, match.start(1)),
                "path": api_path,
            },
            (rel_doc, api_path),
        )

    for match in ENV_RE.finditer(text):
        env_name = match.group(1) or match.group(2)
        if not env_name:
            continue
        append_unique(
            report["env_vars"],
            {
                "doc": rel_doc,
                "line": line_number_for_offset(text, match.start()),
                "name": env_name,
            },
            (rel_doc, env_name),
        )


def format_report(report: dict) -> str:
    lines = []
    lines.append("# Doc Consistency Scan")
    lines.append("")
    lines.append(f"Root: {report['root']}")
    lines.append(f"Docs scanned: {len(report['docs'])}")
    lines.append("")

    lines.append("## Docs")
    if report["docs"]:
        lines.extend(f"- {doc}" for doc in report["docs"])
    else:
        lines.append("- None identified")
    lines.append("")

    sections = [
        ("Broken Relative Links", "broken_links", "target"),
        ("Missing File-Like References", "missing_path_references", "reference"),
        ("Placeholder Markers", "placeholders", "marker"),
        ("Commands Observed", "commands", "command"),
        ("API-Like Paths Observed", "api_paths", "path"),
        ("Environment Variables Observed", "env_vars", "name"),
    ]

    for title, key, value_key in sections:
        lines.append(f"## {title}")
        entries = report[key]
        if not entries:
            lines.append("- None identified")
            lines.append("")
            continue
        for entry in entries:
            value = entry[value_key]
            suffix = ""
            if key == "placeholders":
                suffix = f" :: {entry['text']}"
            lines.append(f"- {entry['doc']}:{entry['line']} :: {value}{suffix}")
        lines.append("")

    return "\n".join(lines).rstrip() + "\n"


def build_report(root: Path, docs: list[Path]) -> dict:
    report = {
        "root": str(root),
        "docs": [relative_to_root(doc, root) for doc in docs],
        "broken_links": [],
        "missing_path_references": [],
        "placeholders": [],
        "commands": [],
        "api_paths": [],
        "env_vars": [],
    }
    append_unique.seen = set()
    for doc in docs:
        analyze_doc(doc, root, report)
    return report


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Scan repo docs for broken links, missing path references, and suspicious markers."
    )
    parser.add_argument(
        "paths",
        nargs="*",
        help="Optional doc files or directories to scan. Defaults to common repo documentation paths.",
    )
    parser.add_argument(
        "--root",
        default=".",
        help="Repository root. Defaults to the current directory.",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Emit machine-readable JSON instead of markdown text.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = Path(args.root).resolve()
    docs = discover_docs(root, args.paths)
    report = build_report(root, docs)

    if args.json:
        print(json.dumps(report, indent=2, sort_keys=True))
        return 0

    print(format_report(report), end="")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
