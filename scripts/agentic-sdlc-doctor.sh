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

PROJECT_TARGETS="
$REPO_ROOT/.agents/skills
$REPO_ROOT/.claude/skills
"

GLOBAL_TARGETS="
$HOME/.codex/skills
$HOME/.agents/skills
$HOME/.claude/skills
$HOME/.config/opencode/skills
"

CHECK_GLOBALS=1
if [ "${CI:-false}" = "true" ] && [ "${AGENTIC_SDLC_CHECK_GLOBALS:-0}" != "1" ]; then
  CHECK_GLOBALS=0
  TARGETS="$PROJECT_TARGETS"
  printf "INFO CI mode detected; skipping workstation global skill directories\n"
else
  TARGETS="$PROJECT_TARGETS
$GLOBAL_TARGETS"
fi

failures=0
warnings=0

check_file() {
  if [ -f "$1" ]; then
    printf "PASS file %s\n" "$1"
  else
    printf "FAIL missing file %s\n" "$1"
    failures=$((failures + 1))
  fi
}

check_dir() {
  if [ -d "$1" ]; then
    printf "PASS dir  %s\n" "$1"
  else
    printf "FAIL missing dir  %s\n" "$1"
    failures=$((failures + 1))
  fi
}

check_executable() {
  if [ -x "$1" ]; then
    printf "PASS executable %s\n" "$1"
  else
    printf "FAIL not executable %s\n" "$1"
    failures=$((failures + 1))
  fi
}

check_contains() {
  file="$1"
  pattern="$2"
  label="$3"
  if [ -f "$file" ] && grep -q "$pattern" "$file"; then
    printf "PASS contains %s in %s\n" "$label" "$file"
  else
    printf "FAIL missing %s in %s\n" "$label" "$file"
    failures=$((failures + 1))
  fi
}

check_valid_json() {
  file="$1"
  if command -v node >/dev/null 2>&1; then
    if node -e "JSON.parse(require('fs').readFileSync(process.argv[1], 'utf8'))" "$file" >/dev/null 2>&1; then
      printf "PASS valid json %s\n" "$file"
    else
      printf "FAIL invalid json %s\n" "$file"
      failures=$((failures + 1))
    fi
  else
    printf "WARN node unavailable; skipped JSON parse %s\n" "$file"
    warnings=$((warnings + 1))
  fi
}

check_manifest_shape() {
  file="$1"
  for key in manifest_schema_version manifest_id created_at created_by workspace task inputs constraints outputs verification stop_conditions; do
    if grep -q "^$key:" "$file"; then
      printf "PASS manifest key %s in %s\n" "$key" "$file"
    else
      printf "FAIL manifest missing key %s in %s\n" "$key" "$file"
      failures=$((failures + 1))
    fi
  done
}

for target in $TARGETS; do
  check_dir "$target"
  for skill in $SKILLS; do
    check_file "$target/$skill/SKILL.md"
  done
done

for skill in $SKILLS; do
  if [ -d "$REPO_ROOT/.agents/skills/$skill" ] && [ -d "$REPO_ROOT/.claude/skills/$skill" ]; then
    if diff -qr "$REPO_ROOT/.agents/skills/$skill" "$REPO_ROOT/.claude/skills/$skill" >/dev/null 2>&1; then
      printf "PASS mirror .agents/.claude %s\n" "$skill"
    else
      printf "FAIL mirror mismatch .agents/.claude %s\n" "$skill"
      failures=$((failures + 1))
    fi
  fi
done

check_file "$REPO_ROOT/.opencode/commands/sdd.md"
if [ "$CHECK_GLOBALS" -eq 1 ]; then
  check_file "$HOME/.config/opencode/commands/sdd.md"
fi
check_file "$REPO_ROOT/.github/copilot-instructions.md"
check_file "$REPO_ROOT/.github/instructions/agentic-sdlc.instructions.md"
check_file "$REPO_ROOT/docs/00-context/sdd-profile.md"
check_file "$REPO_ROOT/docs/00-context/agent-discipline-profile.md"
check_file "$REPO_ROOT/docs/00-context/agentic-sdlc-registry.md"
check_file "$REPO_ROOT/docs/00-context/execution-manifest.schema.json"
check_file "$REPO_ROOT/.github/workflows/agentic-sdlc.yml"
check_file "$REPO_ROOT/scripts/sync-global-agent-assets.sh"
check_executable "$REPO_ROOT/scripts/sync-global-agent-assets.sh"
check_executable "$REPO_ROOT/scripts/agentic-sdlc-doctor.sh"
check_valid_json "$REPO_ROOT/docs/00-context/execution-manifest.schema.json"

manifest_template="$REPO_ROOT/.agents/skills/execution-manifest/references/execution-manifest-template.yaml"
check_file "$manifest_template"
check_contains "$manifest_template" "execution-manifest.schema.json" "manifest schema reference"
check_manifest_shape "$manifest_template"

for skill in $SKILLS; do
  if ! grep -q "$skill" "$REPO_ROOT/scripts/sync-global-agent-assets.sh"; then
    printf "FAIL sync script missing skill %s\n" "$skill"
    failures=$((failures + 1))
  fi
  if ! grep -q "$skill" "$REPO_ROOT/.opencode/commands/sdd.md"; then
    printf "WARN opencode route may not mention %s\n" "$skill"
    warnings=$((warnings + 1))
  fi
done

registry="$REPO_ROOT/docs/00-context/agentic-sdlc-registry.md"
for skill in $SKILLS; do
  check_contains "$registry" "$skill" "registry entry $skill"
done

workflow="$REPO_ROOT/.github/workflows/agentic-sdlc.yml"
check_contains "$workflow" "scripts/agentic-sdlc-doctor.sh" "doctor CI step"
check_contains "$workflow" "gitleaks/gitleaks-action" "secret scan CI step"
check_contains "$workflow" "mvn test" "backend test CI step"
check_contains "$workflow" "npm run build" "frontend build CI step"

copilot_instructions="$REPO_ROOT/.github/copilot-instructions.md"
check_contains "$copilot_instructions" "AGENTS.md" "Copilot primary contract"
check_contains "$copilot_instructions" ".agents/skills" "Copilot skill route"
check_contains "$copilot_instructions" "docs/00-context/sdd-profile.md" "Copilot SDD profile route"
check_contains "$copilot_instructions" "scripts/agentic-sdlc-doctor.sh" "Copilot doctor route"

copilot_agentic_sdlc="$REPO_ROOT/.github/instructions/agentic-sdlc.instructions.md"
check_contains "$copilot_agentic_sdlc" "applyTo" "Copilot path instruction frontmatter"
check_contains "$copilot_agentic_sdlc" "agentic-sdlc-orchestrator" "Copilot orchestrator route"
check_contains "$copilot_agentic_sdlc" "context-engineering-adr" "Copilot ADR route"
check_contains "$copilot_agentic_sdlc" "freshness-gate" "Copilot freshness route"
check_contains "$registry" ".github/copilot-instructions.md" "registry Copilot repository instructions"
check_contains "$registry" ".github/instructions/agentic-sdlc.instructions.md" "registry Copilot Agentic SDLC instructions"

adr_index="$REPO_ROOT/docs/00-context/decisions/README.md"
check_file "$adr_index"
for adr in "$REPO_ROOT"/docs/00-context/decisions/ADR-*.md; do
  [ -e "$adr" ] || continue
  adr_name="$(basename "$adr")"
  if grep -q "$adr_name" "$adr_index"; then
    printf "PASS ADR index %s\n" "$adr_name"
  else
    printf "FAIL ADR index missing %s\n" "$adr_name"
    failures=$((failures + 1))
  fi
done

if [ "$failures" -gt 0 ]; then
  printf "FAIL Agentic SDLC doctor found %s failure(s), %s warning(s)\n" "$failures" "$warnings"
  exit 1
fi

if [ "$warnings" -gt 0 ]; then
  printf "WARN Agentic SDLC doctor found 0 failures, %s warning(s)\n" "$warnings"
  exit 0
fi

printf "PASS Agentic SDLC doctor found 0 failures, 0 warnings\n"
