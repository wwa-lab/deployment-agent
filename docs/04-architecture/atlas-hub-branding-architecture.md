# Atlas Hub Branding Architecture

**Date:** 2026-06-29
**Status:** Proposed / Documents-first
**Source spec:** [atlas-hub-branding-spec.md](../03-spec/atlas-hub-branding-spec.md)

## 1. Decision

Adopt **WWA-Atlas Engineering Delivery Hub** as the visible root product brand while preserving existing technical identifiers for compatibility.

## 2. Branding Layers

| Layer | Name after this slice | Notes |
|---|---|---|
| Root product | WWA-Atlas Engineering Delivery Hub | Used in browser title, login hero, home hero, README, and submission docs. |
| Short UI shell | WWA-Atlas Hub | Used where compact navigation text is needed. |
| Framework name | Atlas Engineering Delivery Hub | Used for framework docs and competition taxonomy. |
| Deployment workspace | Deployment Agent | Remains the agent name. |
| Lifecycle stage | M6 Deployment | Describes where Deployment Agent sits in the Seven Mountains SDLC. |
| Route/API compatibility | `/wwa/deployment-agent`, `/api/deployment-agent` | Unchanged. |

## 3. Runtime Impact

No backend behavior changes are required. Frontend changes are limited to static copy, title metadata, and shell labels.

## 4. Compatibility

The existing `wwa` route namespace remains as a compatibility namespace. A future route migration could add Atlas aliases, but that is intentionally out of scope for this slice.

## 5. ADR Impact

No new ADR is required because this does not change architecture, platform boundaries, security posture, integrations, data ownership, or shared agent conventions. It documents a presentation-layer naming decision and compatibility boundary.
