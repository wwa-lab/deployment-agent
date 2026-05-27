#!/usr/bin/env sh
set -eu

REPO_ROOT="${1:-$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)}"

SKILLS="
agentic-sdlc-doctor
agentic-sdlc-orchestrator
architecture-to-design
context-engineering-adr
cross-ide-skill-router
design-to-tasks
execution-manifest
freshness-gate
req-to-user-story
review-code-against-design
review-doc-quality
review-docs-against-code
sdd-profile-manager
sdd-slice-bootstrap
spec-to-architecture
tasks-to-code
tasks-to-implementation
user-story-to-spec
"

DESTINATIONS="
$HOME/.codex/skills
$HOME/.agents/skills
$HOME/.claude/skills
$HOME/.config/opencode/skills
"

for dest in $DESTINATIONS; do
  mkdir -p "$dest"
  cp -R "$REPO_ROOT/.agents/skills/_shared" "$dest/"
  for skill in $SKILLS; do
    cp -R "$REPO_ROOT/.agents/skills/$skill" "$dest/"
  done
done

mkdir -p "$HOME/.config/opencode/commands"
cp "$REPO_ROOT/.opencode/commands/sdd.md" "$HOME/.config/opencode/commands/sdd.md"

echo "Synced global agent assets from $REPO_ROOT"
