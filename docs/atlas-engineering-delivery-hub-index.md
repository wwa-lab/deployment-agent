# Atlas Engineering Delivery Hub Documentation Index

**Category:** Framework
**Status:** Open-collaboration package
**Primary repository entry:** Atlas Engineering Delivery Hub
**Companion function entry:** [Atlas Engineering Delivery Hub - Deployment](atlas-engineering-delivery-hub-deployment-index.md)
**Last updated:** 2026-06-27

This index collects the framework-level packaging materials for reviewers, adopting teams, and contributors. The Hub is the team framework; Deployment is one function inside it and is also packaged as a separate Tool / Function entry.

## Competition Entry Split

| Entry | Category | Scope | Main materials |
|---|---|---|---|
| Atlas Engineering Delivery Hub | Framework | Team operating framework across Seven Mountains SDLC, shared gates, evidence, governance, and contribution patterns. | [README](../README.md), [submission](open-collaboration-submission.md), [pitch](atlas-engineering-delivery-hub-pitch.md) |
| Atlas Engineering Delivery Hub - Deployment | Tool / Function | M6 Deployment function for controlled SIT / UAT / PROD release operations and IBM iSeries one-click release UTL design direction. | [Deployment index](atlas-engineering-delivery-hub-deployment-index.md), [Deployment submission](open-collaboration-submission-deployment.md), [Deployment pitch](atlas-engineering-delivery-hub-deployment-pitch.md) |

## Framework Entry Points

- [English README](../README.md)
- [Chinese README](../README.zh-CN.md)
- [Framework open collaboration submission](open-collaboration-submission.md)
- [Chinese framework submission](open-collaboration-submission.zh-CN.md)
- [Framework pitch](atlas-engineering-delivery-hub-pitch.md)
- [Contribution guide](../CONTRIBUTING.md)

## Framework Visuals

- [Framework lifecycle diagram](assets/atlas-framework-lifecycle.svg)
- [Framework lifecycle Mermaid source](assets/atlas-framework-lifecycle.mmd)
- [Seven Mountains SDLC diagram](assets/seven-mountains-sdlc.svg)
- [Seven Mountains SDLC Mermaid source](assets/seven-mountains-sdlc.mmd)
- [Seven Gates I-E-O-V diagram](assets/seven-gates-ieov.svg)
- [Seven Gates I-E-O-V Mermaid source](assets/seven-gates-ieov.mmd)
- [Mobile vertical artwork](assets/atlas-engineering-delivery-hub-mobile-vertical.png)

## Framework Sample

- [Synthetic framework adoption sample](samples/atlas-framework-adoption-sample.md)

## Framework SDD Traceability

- [Framework traceability index](00-context/atlas-engineering-delivery-hub-traceability.md)
- [Framework requirements](01-requirements/atlas-engineering-delivery-hub-requirement.md)
- [Framework user stories](02-user-stories/atlas-engineering-delivery-hub-user-stories.md)
- [Framework specification](03-spec/atlas-engineering-delivery-hub-spec.md)
- [Framework architecture](04-architecture/atlas-engineering-delivery-hub-architecture.md)
- [Framework design](05-design/atlas-engineering-delivery-hub-design.md)
- [Framework tasks](06-tasks/atlas-engineering-delivery-hub-tasks.md)

## Deployment Function Materials

- [Deployment documentation index](atlas-engineering-delivery-hub-deployment-index.md)
- [Deployment open collaboration submission](open-collaboration-submission-deployment.md)
- [Chinese Deployment submission](open-collaboration-submission-deployment.zh-CN.md)
- [Deployment pitch](atlas-engineering-delivery-hub-deployment-pitch.md)
- [Deployment sample package](samples/atlas-deployment-tool-mini-output/README.md)
- [Deployment SDD traceability](00-context/atlas-engineering-delivery-hub-deployment-traceability.md)

## Runtime References

- [Current implementation baseline](wwa-agent-workspace-hub-current-baseline.md)
- [Platform architecture baseline](04-architecture/architecture.md)
- [Detailed design baseline](05-design/design.md)
- [SDD profile](00-context/sdd-profile.md)
- [Agent Contribute Dashboard requirement](01-requirements/agent-contribute-dashboard-requirement.md)
- [Agent Contribute Dashboard spec](03-spec/agent-contribute-dashboard-spec.md)

## Validation

Recommended package checks:

```bash
git diff --check
node scripts/check-markdown-links.mjs
```
