# Atlas Engineering Delivery Hub Packaging Architecture

## Current revision — 2026-09-07, platform clarification
This revision changes information architecture only; runtime boundaries and ownership are unchanged. It supersedes earlier default-language and dual-entry packaging descriptions below.

The entry layer introduces the Agentic SDLC platform, its current IBM iSeries practice setting and language-independent method in Chinese and English. It then focuses on Deployment Agent: atomization, automation and intelligence as a staged capability path. The narrative layer uses one primary pitch with submission summaries referring to it. The evidence layer owns case provenance, maturity and verification results. The presentation and two diagrams are views of those same claims. Historical Deployment entry points resolve to a module-level view, not an asserted independent solution.

Shared intake, task services, execution adapters and human decisions remain the implemented system boundary. External work is performed by people or configured tools; the Hub records and governs its workflow. Generic task metadata, scoped configuration and execution-adapter boundaries are the reuse basis across delivery languages. New platform adapters and intelligence remain subject to explicit contracts and validation. Repository Agent Skills guide development and documentation; they are not the running release engine. No other project's implementation or evidence is imported.

Historical evidence stays immutable; new evidence references its source version. Known limits are simulated external calls, unverified deployment environments, and absent field-benefit measurements. This information model prevents a sample or framework aspiration from being treated as an operational result.

## Historical packaging baseline (superseded where inconsistent)

**Date:** 2026-06-27
**Status:** Proposed / Documents-first
**Source spec:** [atlas-engineering-delivery-hub-spec.md](../03-spec/atlas-engineering-delivery-hub-spec.md)

## 1. Overview

This document describes the documentation and information architecture for packaging the repository as Atlas Engineering Delivery Hub. It does not redefine the runtime architecture captured in [architecture.md](architecture.md); instead, it explains how the framework story, implementation baseline, diagrams, samples, and contribution rules are organized.

## 2. Documentation Layers

```text
README.md
  -> concise English framework entry

README.zh-CN.md
  -> Chinese companion entry

docs/atlas-engineering-delivery-hub-index.md
  -> navigable package index

docs/open-collaboration-submission*.md
  -> competition submission materials

docs/atlas-engineering-delivery-hub-pitch.md
  -> short presentation narrative

docs/assets/
  -> Mermaid source and rendered SVG diagrams

docs/samples/
  -> synthetic adoption templates and examples

docs/wwa-agent-workspace-hub-current-baseline.md
  -> preserved detailed implementation README reference
```

## 3. Framework Model

Atlas Engineering Delivery Hub is described as the parent framework above the current WWA Agent Workspace Hub implementation. The model has four layers:

| Layer | Responsibility | Current repository evidence |
|---|---|---|
| Lifecycle model | Seven Mountains SDLC stages and stage gates. | Agent Contribute Dashboard stage baseline. |
| Governance model | Seven Gates Flow and I-E-O-V controls. | Human decision gates, audit logs, access governance, SDD profile. |
| Platform core | Shared shell, auth, access, audit, configuration, upload, task, and release-flow services. | `docs/04-architecture/architecture.md` and platform code. |
| Agent modules | Stage-specific workspaces and future sub-capabilities. | Build, Testing, Deployment agents; target Discovery and Maintenance stages. |

## 4. Sub-Capability Plug-In Rule

Sub-capabilities are presented as stage-level extensions, not separate parent frameworks. A sub-capability must declare:

- owning stage or stages;
- I-E-O-V inputs, execution behavior, outputs, and validation evidence;
- required owners and co-build partners;
- integration boundary with Platform Core or an agent module;
- safety and redaction expectations.

Atlas Phoenix Lens is mentioned only as a Discovery-stage example for requirements or discovery intelligence. It is not the main project identity for this repository.

## 5. Current Implementation Boundary

The current operational baseline is still WWA Agent Workspace Hub with:

- Build Agent for DEV-stage work;
- Testing Agent for UAT testing work in progress;
- Deployment Agent for SIT / UAT / PROD release orchestration;
- shared Platform Core capabilities for authentication, access governance, configuration, audit, uploads, and task progression;
- Agent Contribute Dashboard for seven-stage SDLC visibility.

The packaging work does not rename package namespaces, route slugs, APIs, database tables, or product runtime labels.

## 6. Data Flow And Data Model

No runtime data flow or persistence model changes are introduced. Documentation data flow is:

1. SDD slice defines intent and scope.
2. README and submission docs summarize the framework.
3. Diagrams and samples illustrate adoption.
4. Contribution guide constrains future changes.
5. Validation checks ensure links and whitespace remain healthy.

## 7. Security And Safety Architecture

The package follows repository safety rails:

- no secrets or credentials;
- no customer names, screenshots, or internal-only evidence;
- synthetic sample data only;
- contribution rules require redaction before submission;
- existing access/security runtime posture is referenced, not changed.

## 8. Related Architecture References

- [System Architecture: Deployment Agent + Platform Core](architecture.md)
- [Agent Contribute Dashboard Architecture](agent-contribute-dashboard-architecture.md)
- [WWA Product Positioning](../00-context/wwa-product-positioning.md)
- [SDD Profile](../00-context/sdd-profile.md)
