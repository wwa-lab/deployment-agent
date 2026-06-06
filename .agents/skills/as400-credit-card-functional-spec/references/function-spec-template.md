# Function Spec Template

Use this template when analyzing one AS/400 credit-card function, program, job, screen, or batch capability.

## Function: <name>

**Business Capability:** <credit-card domain capability>

**Current Source Artifacts:**
| Artifact | Type | Role |
|---|---|---|
| <program/file/job> | <RPG/COBOL/CL/DDS/PF/LF/etc.> | <entry/callee/data/report/etc.> |

**Purpose:**
<One or two paragraphs in business language.>

**Trigger:**
| Trigger | Details | Evidence |
|---|---|---|
| <online/batch/API/file/scheduler> | <how it starts> | <source reference> |

**Inputs:**
| Input | Source | Required? | Notes |
|---|---|---|---|
| <field/file/parameter> | <source> | <yes/no/conditional> | <meaning or uncertainty> |

**Outputs:**
| Output | Destination | Notes |
|---|---|---|
| <record/report/message/status> | <file/screen/queue/report> | <meaning> |

**Data Access:**
| Entity/File | Access | Key Fields | Business Meaning |
|---|---|---|---|
| <PF/LF/table> | <read/write/update/delete> | <keys> | <entity> |

**Functional Requirements:**
| ID | Requirement | Evidence | Confidence |
|---|---|---|---|
| FR-001 | <system shall/current system does...> | <program/file/line/logic> | <Confirmed/Likely/Needs SME Review/Possibly Dead Code> |

**Business Rules:**
| ID | Rule | Condition | Outcome | Evidence | Confidence |
|---|---|---|---|---|---|
| BR-001 | <plain-language rule> | <when> | <then> | <source> | <confidence> |

**Process Flow:**
1. <step>
2. <step>
3. <step>

**Alternate Paths And Exceptions:**
| Scenario | Behavior | Evidence | Confidence |
|---|---|---|---|
| <condition/error/status> | <what happens> | <source> | <confidence> |

**Audit, Reconciliation, And Controls:**
| Control | Behavior | Evidence | Confidence |
|---|---|---|---|
| <audit/recon/log/report> | <what is recorded or checked> | <source> | <confidence> |

**Traceability Matrix:**
| Functional Item | Source Artifact | Data Elements | Test Ideas |
|---|---|---|---|
| <FR/BR ID> | <program/file/logic> | <fields> | <scenario> |

**Open Questions For SME Review:**
| ID | Question | Why It Matters | Suggested Owner |
|---|---|---|---|
| Q-001 | <question> | <impact> | <business/tech/ops> |

**Modernization Notes:**
<Optional. Only include when the user asks for migration guidance. Separate target recommendations from current-state behavior.>
