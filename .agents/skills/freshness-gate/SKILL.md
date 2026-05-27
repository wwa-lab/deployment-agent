---
name: freshness-gate
description: Use this skill when checking whether requirements, SDD documents, ADRs, code, tests, approvals, or deployment evidence are stale relative to their source references. Applies before implementation, review, approval, release, or when a user asks if docs/code/tests are up to date.
---

# Freshness Gate

Use this skill to detect stale context before work proceeds.

## Purpose

Freshness is a first-class quality signal. A document, approval, test, or implementation can be correct for an old version and wrong for the current one.

## Workflow

1. Identify the artifact under review:
   - requirement source
   - SDD document
   - ADR
   - code change
   - test plan
   - approval
   - deployment or incident evidence
2. Identify upstream sources:
   - Jira / Confluence / PRD / uploaded requirement
   - earlier SDD stage
   - ADRs
   - code paths
   - tests
3. Compare freshness evidence:
   - timestamps
   - commit SHA
   - blob SHA
   - document version
   - approval version
   - changed file list
4. Classify status.
5. Recommend the smallest repair path.

## Status Values

- `Fresh` - artifact is current relative to known upstream sources.
- `Stale` - upstream changed after this artifact was created, approved, or verified.
- `Unknown` - missing version evidence prevents a confident answer.
- `Blocked` - stale or unknown status must be resolved before proceeding.

## Common Checks

| Check | Stale When |
|---|---|
| Requirement to spec | requirement source changed after spec version |
| Spec to design | spec changed after design version |
| Design to code | implementation changed outside design scope or design changed after implementation |
| Test to requirement | tests do not reference latest requirement/spec IDs |
| Approval to document | document blob changed after approval |
| Deployment to code | deployed version does not match reviewed commit |

## Output Format

```markdown
# Freshness Gate Report

## Verdict

Fresh | Stale | Unknown | Blocked

## Evidence

| Artifact | Version / Time | Source | Status |
|---|---|---|---|

## Findings

- [Finding]

## Minimal Repair Path

1. [Step]

## Open Risks

- [Risk]
```

## Rules

- Do not approve work based on unpinned "latest" context.
- If a version is missing, say `Unknown`; do not infer freshness.
- Prefer commit/blob SHAs over timestamps when available.
- If freshness is blocked, stop implementation and ask for source update or approval.
