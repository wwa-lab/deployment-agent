# Contributing To Atlas Engineering Delivery Hub

Thank you for contributing to Atlas Engineering Delivery Hub. This repository is packaged as a Framework entry, so contributions should strengthen the reusable delivery framework rather than only optimize one local demo.

## Welcome Contributions

- Framework documentation, diagrams, samples, and templates.
- SDD artifacts for new or changed framework capabilities.
- Agent module patterns that fit the existing Platform Core boundary.
- Validation scripts and lightweight documentation checks.
- Redacted examples that help teams adopt Seven Mountains SDLC and Seven Gates Flow.
- Bug fixes and tests for the existing Spring Boot and Vue implementation.

## Documentation Rules

- Keep `README.md` concise and reviewer-friendly.
- Put deeper details under `docs/` and link them from the docs index.
- Preserve historical or operational detail in reference docs instead of deleting useful context.
- For non-trivial or user-facing changes, update the relevant SDD artifacts before implementation.
- Keep Atlas Phoenix Lens positioned as a Discovery-stage sub-capability example, not the parent project.
- Prefer Mermaid source plus rendered SVG for deterministic diagrams.

## Framework Module And Plugin Rules

- A stage capability must declare its Seven Mountains stage ownership.
- A module must define its I-E-O-V contract: inputs, execution behavior, outputs, and validation evidence.
- Backend agent modules should follow the existing Platform Core and Agent Module boundary in `docs/04-architecture/architecture.md`.
- Do not put persistence logic in controllers.
- Do not add shared platform behavior for one agent unless the need is proven and documented.
- Do not introduce new global state without explicit justification.

## Validation Expectations

Use the smallest validation set that safely matches the change:

- Documentation-only package changes: `git diff --check` and Markdown relative-link check.
- Backend changes: `mvn test`.
- Frontend changes: `cd frontend && npm run build`.
- API changes: update controller or contract tests under `src/test/java/`.
- UI changes: build the frontend and capture before/after screenshots when practical.
- Diagram changes: render SVGs from Mermaid source before submitting.

## Data Safety And Redaction

- Never commit secrets, credentials, passwords, tokens, or private keys.
- Never include customer data, production screenshots, or internal confidential evidence without explicit approval and redaction.
- Use synthetic names, projects, systems, and identifiers in samples.
- If a secret is exposed, stop, rotate it, remove it from history through the approved process, and inspect for similar leaks.
- Use environment variables or a secret manager for runtime secrets.

## Pull Request Checklist

- [ ] Scope is clear and tied to an SDD artifact when required.
- [ ] README and docs index links are updated when discoverability changes.
- [ ] Diagrams include both source and rendered output.
- [ ] Samples use synthetic data only.
- [ ] Validation commands were run and results are included.
- [ ] No unrelated files, lockfiles, `.env` files, or credentials are included.
- [ ] User-facing changes are recorded in `CHANGELOG.md`.

## Commit Style

Use conventional commits:

```text
docs: package atlas engineering delivery hub framework
feat: add new stage capability
fix: correct gate validation
test: add markdown link checker
chore: refresh generated diagram assets
```

Keep commits focused. Stage only files related to the change.

