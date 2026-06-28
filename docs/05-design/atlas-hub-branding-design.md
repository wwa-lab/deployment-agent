# Atlas Hub Branding Design

**Date:** 2026-06-29
**Status:** Proposed / Documents-first
**Source architecture:** [atlas-hub-branding-architecture.md](../04-architecture/atlas-hub-branding-architecture.md)

## 1. UI Copy Design

Use these labels:

| Surface | Label |
|---|---|
| Browser title | WWA-Atlas Engineering Delivery Hub |
| Login kicker | WWA-Atlas Engineering Delivery Hub |
| Login headline | Team delivery framework |
| Login access card | WWA-Atlas Hub Access |
| Sidebar logo title | WWA-Atlas Hub |
| Sidebar logo subtitle | Engineering Delivery Hub |
| Primary nav | WWA-Atlas Hub |
| Flyout home | WWA-Atlas Hub Home |
| Topbar kicker | WWA-Atlas Hub |
| Topbar home link | WWA-Atlas Hub Home |
| Shared capability eyebrow | WWA-Atlas Hub Shared Capability |

## 2. Agent Copy Design

Keep these labels:

| Surface | Label |
|---|---|
| Deployment agent card | Deployment Agent |
| Deployment route metadata | Deployment Agent |
| Placeholder examples | Testing Agent or Deployment Agent |

## 3. Documentation Design

README and architecture docs should explain:

- WWA-Atlas Engineering Delivery Hub is the visible product brand.
- Atlas Engineering Delivery Hub remains the framework name.
- Deployment Agent is one agent inside the Hub.
- `WWA` and `deployment-agent` technical identifiers remain temporarily for compatibility.

## 4. Validation Design

Run:

```bash
git diff --check
node scripts/check-markdown-links.mjs
cd frontend && npm run build
```
