# Design: Atlas Engineering Delivery Hub - Deployment Package

**Slice key:** `atlas-engineering-delivery-hub-deployment`
**Status:** Backfilled

## Design Objective

Create a reviewer-ready documentation package that accurately presents the existing Deployment Agent as the Atlas Engineering Delivery Hub - Deployment Tool / Function inside the parent Atlas Engineering Delivery Hub framework.

## Public Entry Design

- `README.md` is the default English framework entry point and links Deployment as one function.
- `README.zh-CN.md` mirrors the Hub-first positioning in Chinese.
- Deployment-specific entry points emphasize: function positioning, lifecycle stage, current scope, capabilities, inputs/outputs, workflow, quick start, sample release flow, docs, roadmap, safety notes, and IBM iSeries one-click release UTL design direction.

## Competition Material Design

- `docs/open-collaboration-submission-deployment.md` is the English Tool / Function-category submission.
- `docs/open-collaboration-submission-deployment.zh-CN.md` is the Chinese Tool / Function submission.
- `docs/atlas-engineering-delivery-hub-deployment-pitch.md` is a short presentation aid.
- `docs/atlas-engineering-delivery-hub-deployment-index.md` is the reviewer navigation hub.

## Visual Design

The package includes three Mermaid diagrams with rendered SVGs:

- Lifecycle positioning with M6 highlighted.
- Internal deployment workflow from evidence/upload through review/audit.
- Upstream/downstream relationship from M4/M5 to M6 to M7.

## Sample Package Design

The sample package is intentionally small:

- one release input JSON;
- one representative task output JSON;
- one audit trail JSON;
- one rollback handoff checklist.

The sample demonstrates the shape of evidence without exposing operational data.

## Validation Design

The minimum validation path for this documentation-only slice is:

```bash
git diff --check
node scripts/check-markdown-links.mjs
```

Additional validation:

- Render Mermaid diagrams when tooling is available.
- Run cheap existing tests when discoverable.
- Inspect staged diff for secrets, credentials, kubeconfigs, and confidential screenshots.

## Edge Cases

| Edge case | Expected design response |
|---|---|
| Framework and Deployment entries become confused | Keep separate framework and Deployment indexes, submissions, and pitch docs. |
| Diagram renderer unavailable | Keep Mermaid source files committed and report rendering status. |
| Sample needs environment-like labels | Use synthetic labels and avoid real environment names. |
| A capability is only planned | Mark it as planned/TBD and do not place it in implemented scope. |
