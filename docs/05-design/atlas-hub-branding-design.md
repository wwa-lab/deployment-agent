# Atlas Hub Branding Design

**Date:** 2026-06-29
**Status:** Proposed / Documents-first
**Source architecture:** [atlas-hub-branding-architecture.md](../04-architecture/atlas-hub-branding-architecture.md)

## 1. UI Copy Design

Use these labels:

| Surface | Label |
|---|---|
| Browser title | Atlas Engineering Delivery Hub |
| Login kicker | Atlas Engineering Delivery Hub |
| Login headline | Team delivery framework |
| Login access card | Atlas Hub Access |
| Sidebar logo title | Atlas Hub |
| Sidebar logo subtitle | Engineering Delivery Hub |
| Primary nav | Atlas Hub |
| Flyout home | Atlas Hub Home |
| Topbar kicker | Atlas Hub |
| Topbar home link | Atlas Hub Home |
| Shared capability eyebrow | Atlas Hub Shared Capability |

## 2. Agent Copy Design

Keep these labels:

| Surface | Label |
|---|---|
| Deployment agent card | Deployment Agent |
| Deployment route metadata | Deployment Agent |
| Placeholder examples | Testing Agent or Deployment Agent |

## 3. Documentation Design

README and architecture docs should explain:

- Atlas Engineering Delivery Hub is the visible product brand.
- Deployment Agent is one agent inside the Hub.
- `WWA` and `deployment-agent` technical identifiers remain temporarily for compatibility.

## 4. Validation Design

Run:

```bash
git diff --check
node scripts/check-markdown-links.mjs
cd frontend && npm run build
```
