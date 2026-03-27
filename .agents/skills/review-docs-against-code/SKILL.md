---
name: review-docs-against-code
description: >
  Reviews repository documentation against the actual codebase to detect stale docs, broken setup
  steps, invalid file or path references, unsupported architectural claims, API mismatches, and
  other documentation drift. Use this skill whenever the user asks whether README files, docs/,
  runbooks, onboarding guides, AGENTS.md, CLAUDE.md, or in-repo documentation is accurate,
  current, consistent with code, or free of errors. Also trigger when the user says things like
  "check docs against code", "is the README up to date", "find doc drift", "verify repository
  documentation", "文档和代码一致吗", "检查 repo 文档", or "有没有文档错误".
---

# Review Docs Against Code

This skill reviews repository documentation for factual accuracy against the current codebase. It
is for documentation drift and correctness review, not for subjective prose polishing.

## Use This Skill When

- The user wants to know whether repo docs still match implementation
- A refactor may have invalidated commands, file paths, endpoints, config keys, roles, states, or
  architecture notes
- Documentation must be checked before onboarding, release, merge, or handoff

## Do Not Use This Skill For

- General SDLC document quality review with no code in scope: use `review-doc-quality`
- Design-fidelity review against `design.md` or `tasks.md`: use `review-code-against-design`

## Inputs

The user should provide some combination of:

| Input | Required | Notes |
|---|---|---|
| Repo / source tree | Yes | This is the source of truth |
| Documentation files or directories | Recommended | README, `docs/`, runbooks, ADRs, onboarding docs |
| Diff / PR context | Optional | Useful for focusing on recently changed code or docs |
| Specific concern areas | Optional | Setup docs, API docs, architecture docs, config, auth, etc. |

If the user does not specify documents, inspect the highest-risk repo docs first: `README*`,
`docs/**/*.md`, `AGENTS.md`, `CLAUDE.md`, and any task or runbook markdown files.

---

## Review Process

Work through these steps in order.

### 1. Define scope and likely sources of truth

- Identify which documents are in scope
- Identify which code areas those docs describe
- Prefer executable or authoritative evidence in this order:
  1. Running behavior, tests, and contract tests
  2. Build or runtime manifests (`package.json`, `pom.xml`, `Makefile`, config files)
  3. Source files implementing the behavior
  4. Secondary docs and comments

When documentation and code disagree, treat the codebase and executable artifacts as the source of
truth unless the user explicitly says the code is known to be incomplete.

### 2. Run a fast documentation scan

Start with the bundled scanner:

```bash
python3 .agents/skills/review-docs-against-code/scripts/doc_consistency_scan.py
```

Use `--json` when structured output helps:

```bash
python3 .agents/skills/review-docs-against-code/scripts/doc_consistency_scan.py --json
```

The scan is a first pass only. It helps find:

- Broken relative markdown links
- Missing file-like references in code spans
- Placeholder markers such as `TODO`, `TBD`, `FIXME`, `XXX`, `WIP`, `{{...}}`
- Shell commands shown in docs
- API-like paths shown in docs
- Environment variables shown in docs

Do not stop at the scanner output. Verify important findings manually against the repo.

### 3. Extract factual claims from the docs

For each document, identify claims in these categories:

- **Setup / workflow claims**: install, run, test, build, lint, deploy, local-dev commands
- **Path / structure claims**: referenced files, modules, directories, class names, component names
- **API / contract claims**: routes, methods, headers, payloads, DTO names, status codes, auth flow
- **Config claims**: env vars, ports, profiles, feature flags, secret handling, config keys
- **State / behavior claims**: enums, roles, statuses, state transitions, permissions, retries
- **Architecture claims**: layer boundaries, ownership, adapters, integrations, persistence rules
- **Example claims**: code snippets, curl commands, import paths, JSON examples

Focus on claims that would mislead an engineer or operator if wrong.

### 4. Verify each claim against code

Use targeted repo inspection instead of broad reading.

For setup and workflow claims:

- Compare documented commands with `package.json`, workspace manifests, `pom.xml`, `Makefile`,
  Dockerfiles, and CI config
- Confirm the documented package manager matches the repo
- Confirm the documented scripts or Maven goals actually exist

For API and contract claims:

- Check controllers, routers, API client modules, DTOs, and contract tests
- Verify route paths, methods, auth requirements, request fields, and response shapes
- If docs claim a behavior that is only partially implemented, mark it as partial rather than aligned

For config and runtime claims:

- Check config modules, `application*.properties`, `*.env.example`, Vite config, ports, and profile
  names
- Verify env var names exactly; one renamed key is a real mismatch

For state and behavior claims:

- Check enums, state machines, permission resolvers, review flows, retry behavior, and audit hooks
- Verify documented status names and transitions exactly

For architecture and boundary claims:

- Check folder structure and actual ownership boundaries
- Flag docs that place logic in the wrong layer or describe modules that no longer exist

Use the review checklist in `references/review-checklist.md` when deciding which code artifacts to
inspect.

### 5. Classify findings

Use these severities:

| Severity | Criteria |
|---|---|
| **Critical** | Documentation would cause a user to run the wrong commands, call the wrong API, break setup, misunderstand auth/security, or trust behavior that the code does not provide |
| **Major** | Meaningful drift, stale naming, invalid paths, incorrect states or config, or architecture claims that would mislead engineering work |
| **Minor** | Small inaccuracies, stale examples, wording that should be corrected but is unlikely to block work |

If evidence is ambiguous, say so. Do not invent certainty.

### 6. Render the report

Follow the output format below. Cite both the documentation evidence and the code evidence for every
meaningful finding.

---

## Output Format

Produce the following report. Do not omit sections. If a section is empty, write `None identified`.

```markdown
# Documentation vs Code Review Report

## Review Scope
- **Documentation reviewed:** [list of files or directories]
- **Code / artifacts inspected:** [list of files or repo areas]
- **Review objective:** [one sentence]

## Overall Assessment
- **Accuracy rating:** [0-100%]
- **Verdict:** Accurate | Mostly accurate | Partially outdated | Misleading
- **Rationale:** [2-4 sentences]

## Confirmed Accurate Areas
- [Specific areas that were checked and aligned]

## Findings

### Critical
[Finding title]
- **Document claim:** ...
- **Documentation evidence:** ...
- **Code / repo evidence:** ...
- **Why it matters:** ...
- **Recommended fix:** ...

### Major
[Same structure]

### Minor
[Same structure]

## Coverage Summary
| Area | Status | Notes |
|---|---|---|
| Setup / install / run / test | Aligned / Partial / Drift / Not reviewed | ... |
| Paths / module names | Aligned / Partial / Drift / Not reviewed | ... |
| API / contracts | Aligned / Partial / Drift / Not reviewed | ... |
| Config / runtime | Aligned / Partial / Drift / Not reviewed | ... |
| States / roles / workflow | Aligned / Partial / Drift / Not reviewed | ... |
| Architecture / boundaries | Aligned / Partial / Drift / Not reviewed | ... |
| Examples / snippets | Aligned / Partial / Drift / Not reviewed | ... |

## Automated Scan Highlights
- **Broken markdown links:** [list or "None identified"]
- **Missing file-like references:** [list or "None identified"]
- **Placeholder markers:** [list or "None identified"]
- **Commands observed:** [short list or "None identified"]
- **API-like paths observed:** [short list or "None identified"]
- **Environment variables observed:** [short list or "None identified"]

## Minimal Fix Path
1. [Highest-value documentation correction]
2. ...

## Open Risks / Ambiguities
- [Any area where code was ambiguous, partial, or hard to verify]

**Final verdict:** [Accurate / Mostly accurate / Partially outdated / Misleading]
```

---

## Reviewer Conduct Rules

- Treat the codebase as the default source of truth unless the user explicitly says otherwise
- Do not approve documentation based on tone or structure alone; verify factual claims
- Distinguish between:
  - broken documentation
  - partially outdated documentation
  - undocumented behavior
  - ambiguous evidence
- Cite exact files and sections where possible
- Prefer targeted `rg` searches over broad file dumping
- Do not execute arbitrary commands copied from docs unless the user asks
- When no issues are found, still state what was checked and what was not checked

---

## Reference Files

- `references/review-checklist.md` — high-risk doc drift areas and evidence to inspect
- `scripts/doc_consistency_scan.py` — first-pass scanner for broken links, missing references, and
  suspicious markers
