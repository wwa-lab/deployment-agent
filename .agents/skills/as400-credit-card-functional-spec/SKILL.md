---
name: as400-credit-card-functional-spec
description: >
  Analyze AS/400, IBM i, RPG, COBOL, CL, DDS, DB2 physical/logical files, job streams,
  screens, and batch programs for credit-card platforms, then reverse-engineer them into
  functional specifications. Use when the user asks to convert legacy credit-card code
  into a functional spec, analyze each function/program/job, extract business rules,
  document data flows, or produce traceable modernization requirements from AS/400 code.
---

# AS/400 Credit Card Functional Spec

Use this skill to turn AS/400 credit-card implementation evidence into business-facing functional specifications.
The goal is not line-by-line translation. The goal is to infer what business capability the code implements,
with evidence, uncertainty labels, and traceability back to source artifacts.

## Inputs

Accept any combination of:
- RPG, COBOL, CL, DDS, SQL, copybooks, display files, printer files, job descriptions, scheduler definitions
- Program names, function names, menu options, screen IDs, batch job names, or transaction codes
- DB2 for i physical files, logical files, field lists, code tables, or sample records
- Existing notes from SMEs, runbooks, production incidents, or migration assessments

If the user provides a whole folder or repository, first identify likely entry points and ask only if the scope is too large to analyze safely in one pass.

## Core Workflow

### 1. Establish Scope

For each requested function, identify:
- Business area: application, account setup, authorization, transaction posting, statementing, payment, collections, card maintenance, fees, interest, rewards, disputes, fraud, reporting, reconciliation
- Execution mode: online screen, API/queue transaction, scheduled batch, ad hoc job, report, file interface
- Entry point: menu option, CL program, RPG/COBOL main program, command, job scheduler, queue, or trigger
- Upstream and downstream systems or files

When the user says "analyze each function", define a function as the smallest externally meaningful business capability, not every subroutine.

### 2. Read Source Evidence

Inspect source before writing conclusions. Prefer these evidence types:
- Program headers, comments, and change history
- CL calls, command parameters, job steps, overrides, file declarations
- RPG `F`, `D`, `C`, free-form procedure blocks, `CHAIN`, `READ`, `READE`, `WRITE`, `UPDATE`, `DELETE`
- COBOL divisions, copybooks, file sections, paragraph names, conditions, moves, computes, calls
- DDS PF/LF definitions, display files, printer files, key fields, indicators
- SQL cursors, joins, stored procedures, embedded SQL
- Hardcoded code values, status flags, reason codes, product/BIN branches, date and amount calculations

Do not treat comments as truth unless the executable logic supports them.

### 3. Build The Function Map

For each function/program, produce:
- Purpose in business language
- Trigger and schedule
- Inputs and outputs
- Programs/procedures called
- Files/tables read
- Files/tables written
- Screens, reports, queues, or extracts produced
- Primary happy path
- Alternate paths and exceptions
- Reconciliation or audit effects

Use `rg`, source search, cross-reference files, or IDE indexes when available to find callers, callees, and file usage.

### 4. Extract Business Rules

Convert implementation details into rules:
- Status transitions
- Eligibility checks
- Limit checks
- Amount calculations
- Interest, fee, penalty, minimum payment, or allocation formulas
- Date logic: billing cycle, due date, grace period, cutoff, effective date, aging bucket
- Authorization decisioning and decline reason logic
- Posting, reversal, adjustment, chargeback, or payment ordering
- Product, BIN, region, customer type, delinquency, or block-code special cases

Write each rule as:
- Rule ID
- Plain-language rule
- Condition
- Outcome
- Evidence
- Confidence: Confirmed, Likely, Needs SME Review, or Possibly Dead Code

Never hide uncertainty. If a field name or code value is ambiguous, say so explicitly.

### 5. Produce Functional Spec

For a single function, use the compact template in `references/function-spec-template.md`.
For multiple functions, repeat the template per function and add a cross-function summary:
- Function inventory
- End-to-end process flow
- Shared data entities
- Shared code tables/status values
- Open questions for SMEs
- Traceability matrix

### 6. Traceability Requirements

Every functional claim must tie back to evidence when source is available.
Use this style:

```text
FR-001 | Calculate minimum payment | Source: PMTCALCR.rpgle, MINPAY copybook, ACCTPF fields CURBAL/MINAMT | Confidence: Confirmed
```

If evidence is missing, label the item:

```text
FR-007 | Apply promotional APR rules | Source: inferred from PROMOAPR fields; no caller found yet | Confidence: Needs SME Review
```

## Output Modes

Choose the mode based on the user's ask:

- **Function Analysis**: One function/program/job, detailed spec.
- **Function Inventory**: Many source files, concise table of candidate functions and recommended analysis order.
- **Business Rule Catalog**: Rules only, grouped by business capability and source evidence.
- **Functional Spec Pack**: Multiple function specs plus traceability and open questions.
- **SME Review Pack**: Short spec with unknowns, assumptions, and questions for business validation.

If the user does not specify a mode, default to Function Analysis for one named item and Function Inventory for a folder or large source set.

## Quality Bar

- Explain AS/400 terms only when needed for business readers.
- Preserve code names in evidence; translate behavior into business language.
- Separate current behavior from target-system recommendations.
- Do not invent business meaning for obscure abbreviations. Mark them as unknown.
- Prefer tables for inventories and traceability.
- Keep output actionable for modernization, requirements, testing, and SME review.

## When To Ask Questions

Ask concise questions only when:
- The source artifact cannot be located.
- Multiple functions share the same name or entry point.
- The user asks for a regulatory, accounting, or customer-facing conclusion that cannot be proven from code.
- The requested scope is too large; propose a first slice by business capability or entry point.

Otherwise proceed with best-effort analysis and mark assumptions.
