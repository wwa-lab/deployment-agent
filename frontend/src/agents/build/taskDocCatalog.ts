import type { TaskDocSpec, TaskDocLink, TaskSkillRef } from '../../platform/composables/releaseFlowTypes'

interface BuildTaskDocEntry extends TaskDocSpec {
  aliases: string[]
}

const GITHUB_BASE = 'https://github.com/wwa-lab/build-agent-skill/blob/main'

function githubBlob(path: string): string {
  return `${GITHUB_BASE}/${path.split('/').map(encodeURIComponent).join('/')}`
}

function skill(skillKey: string, role: TaskSkillRef['role'] = 'primary'): TaskSkillRef {
  return {
    key: skillKey,
    label: skillKey,
    role,
  }
}

function doc(label: string, path: string, note?: string, required = false): TaskDocLink {
  return {
    label,
    url: githubBlob(path),
    note,
    required,
  }
}

export const BUILD_TASK_DOC_CATALOG: BuildTaskDocEntry[] = [
  {
    aliases: ['requirement normalizer', 'requirement package', 'normalize requirement', 'ibm i requirement normalizer'],
    primarySkill: skill('ibm-i-requirement-normalizer'),
    inputs: [
      doc('Skill Guide', '.claude/ibm-i-requirement-normalizer/SKILL.md', 'How messy input is normalized into a structured requirement package', true),
    ],
    outputs: [
      doc('Sample Requirement Package', '.claude/ibm-i-requirement-normalizer/examples/sample-normalization.md', 'Example normalized output', true),
    ],
  },
  {
    aliases: ['program analyzer', 'program analysis', 'analyze program', 'ibm i program analyzer'],
    primarySkill: skill('ibm-i-program-analyzer'),
    inputs: [
      doc('Skill Guide', '.claude/ibm-i-program-analyzer/SKILL.md', 'How existing RPGLE/CLLE source is analyzed', true),
    ],
    outputs: [
      doc('README Overview', 'README.md', 'High-level description of the analysis stage'),
    ],
  },
  {
    aliases: ['impact analyzer', 'impact analysis', 'analyze impact', 'ibm i impact analyzer'],
    primarySkill: skill('ibm-i-impact-analyzer'),
    inputs: [
      doc('Skill Guide', '.claude/ibm-i-impact-analyzer/SKILL.md', 'How existing source plus CR is turned into impact analysis', true),
      doc('Program Analyzer Guide', '.claude/ibm-i-program-analyzer/SKILL.md', 'Upstream source comprehension often used as input'),
    ],
    outputs: [
      doc('README Overview', 'README.md', 'High-level description of the impact-analysis stage'),
    ],
  },
  {
    aliases: ['functional spec', 'functional specification', 'ibm i functional spec'],
    primarySkill: skill('ibm-i-functional-spec'),
    relatedSkills: [skill('ibm-i-spec-reviewer', 'review')],
    inputs: [
      doc('Skill Guide', '.claude/ibm-i-functional-spec/SKILL.md', 'How business-functional scope is written', true),
      doc('Section Guide', '.claude/ibm-i-functional-spec/references/section-guide.md', 'Expected structure for the spec'),
    ],
    outputs: [
      doc('Sample Enhancement Spec', '.claude/ibm-i-functional-spec/examples/sample-enhancement.md', 'Example output for enhancement work', true),
      doc('Sample New Function Spec', '.claude/ibm-i-functional-spec/examples/sample-new-function.md', 'Example output for new function work'),
      doc('Tier Guide', '.claude/ibm-i-functional-spec/references/tier-guide.md', 'L1 / L2 / L3 depth guidance'),
    ],
  },
  {
    aliases: ['technical design', 'technical design document', 'ibm i technical design'],
    primarySkill: skill('ibm-i-technical-design'),
    relatedSkills: [skill('ibm-i-spec-reviewer', 'review')],
    inputs: [
      doc('Skill Guide', '.claude/ibm-i-technical-design/SKILL.md', 'How technical design is derived from approved upstream scope', true),
      doc('Functional Spec Example', '.claude/ibm-i-functional-spec/examples/sample-enhancement.md', 'Typical upstream business-functional input'),
      doc('Impact Analyzer Guide', '.claude/ibm-i-impact-analyzer/SKILL.md', 'Typical upstream impact input for enhancement work'),
    ],
    outputs: [
      doc('Sample Enhancement Design', '.claude/ibm-i-technical-design/examples/sample-enhancement-design.md', 'Example design output', true),
      doc('Section Guide', '.claude/ibm-i-technical-design/references/section-guide.md', 'Expected design sections'),
      doc('Tier Guide', '.claude/ibm-i-technical-design/references/tier-guide.md', 'L1 / L2 / L3 depth guidance'),
    ],
  },
  {
    aliases: ['program spec', 'implementation spec', 'ibm i program spec'],
    primarySkill: skill('ibm-i-program-spec'),
    relatedSkills: [skill('ibm-i-spec-reviewer', 'review')],
    inputs: [
      doc('Skill Guide', '.claude/ibm-i-program-spec/SKILL.md', 'How implementation-ready program logic is produced', true),
      doc('Technical Design Example', '.claude/ibm-i-technical-design/examples/sample-rpgle-design.md', 'Typical upstream design input'),
    ],
    outputs: [
      doc('Sample RPGLE Program Spec', '.claude/ibm-i-program-spec/sample-rpgle-spec.md', 'Example output for RPGLE implementation', true),
      doc('Sample CLLE Program Spec', '.claude/ibm-i-program-spec/sample-clle-spec.md', 'Example output for CLLE implementation'),
      doc('Section Guide', '.claude/ibm-i-program-spec/section-guide.md', 'Expected program-spec sections'),
      doc('Tier Guide', '.claude/ibm-i-program-spec/tier-guide.md', 'L1 / L2 / L3 depth guidance'),
    ],
  },
  {
    aliases: ['file spec', 'file specification', 'ibm i file spec'],
    primarySkill: skill('ibm-i-file-spec'),
    relatedSkills: [skill('ibm-i-spec-reviewer', 'review')],
    inputs: [
      doc('Skill Guide', '.claude/ibm-i-file-spec/SKILL.md', 'How DDS-oriented file definition specs are produced', true),
      doc('Interop Model', '.claude/ibm-i-file-spec/references/interop-model.md', 'How File Spec links to Program Spec'),
    ],
    outputs: [
      doc('Sample PF Spec', '.claude/ibm-i-file-spec/examples/sample-pf-spec.md', 'Example physical-file spec', true),
      doc('Sample LF Spec', '.claude/ibm-i-file-spec/examples/sample-lf-spec.md', 'Example logical-file spec'),
      doc('JSON Schema', '.claude/ibm-i-file-spec/references/json-schema.md', 'Machine-readable structure consumed by DDS generation'),
      doc('Validation Rules', '.claude/ibm-i-file-spec/references/validation-rules.md', 'File-spec validation checkpoints'),
    ],
  },
  {
    aliases: ['dds generator', 'dds source', 'generate dds', 'ibm i dds generator'],
    primarySkill: skill('ibm-i-dds-generator'),
    relatedSkills: [skill('ibm-i-dds-reviewer', 'review')],
    inputs: [
      doc('Skill Guide', '.claude/ibm-i-dds-generator/SKILL.md', 'How DDS source is generated from File Spec JSON', true),
      doc('File Spec JSON Schema', '.claude/ibm-i-file-spec/references/json-schema.md', 'Expected upstream machine-readable file-spec structure', true),
    ],
    outputs: [
      doc('Sample PF DDS Output', '.claude/ibm-i-dds-generator/examples/sample-pf-dds-output.md', 'Example generated PF DDS', true),
      doc('Sample LF DDS Output', '.claude/ibm-i-dds-generator/examples/sample-lf-simple-dds-output.md', 'Example generated LF DDS'),
      doc('Sample DSPF DDS Output', '.claude/ibm-i-dds-generator/examples/sample-dspf-dds-output.md', 'Example generated DSPF DDS'),
      doc('Test Harness Notes', '.claude/ibm-i-dds-generator/tests/test-harness.md', 'Validation notes for generator output'),
    ],
  },
  {
    aliases: ['code generator', 'generate code', 'ibm i code generator'],
    primarySkill: skill('ibm-i-code-generator'),
    relatedSkills: [skill('ibm-i-compile-precheck', 'review'), skill('ibm-i-code-reviewer', 'review')],
    inputs: [
      doc('Skill Guide', '.claude/ibm-i-code-generator/SKILL.md', 'How code is generated from a Program Spec', true),
      doc('Program Spec Example', '.claude/ibm-i-program-spec/sample-rpgle-spec.md', 'Typical upstream program-spec input'),
      doc('RPGLE Format Policy', '.claude/ibm-i-code-generator/references/rpgle-format-policy.md', 'Formatting behavior for new vs existing members'),
    ],
    outputs: [
      doc('Sample RPGLE Output', '.claude/ibm-i-code-generator/examples/sample-rpgle-new-free.md', 'Example generated free-format RPGLE', true),
      doc('Sample Change Block Output', '.claude/ibm-i-code-generator/examples/sample-rpgle-existing-fixed-change-block.md', 'Example enhancement-style output'),
      doc('Change Output Modes', '.claude/ibm-i-code-generator/references/change-output-modes.md', 'Skeleton vs full implementation behavior'),
    ],
  },
  {
    aliases: ['ut plan', 'unit test plan', 'ut plan generator', 'ibm i ut plan generator'],
    primarySkill: skill('ibm-i-ut-plan-generator'),
    inputs: [
      doc('Skill Guide', '.claude/ibm-i-ut-plan-generator/SKILL.md', 'How UT plans are produced from specs, CRs, or raw input', true),
      doc('Program Spec Example', '.claude/ibm-i-program-spec/sample-rpgle-spec.md', 'Common upstream input for developer-level test planning'),
    ],
    outputs: [
      doc('README Workflow Notes', 'README.md', 'Recommended use of UT planning in the chain'),
    ],
  },
  {
    aliases: ['test scaffold', 'test scaffolding', 'sql cl scaffold', 'ibm i test scaffold'],
    primarySkill: skill('ibm-i-test-scaffold'),
    inputs: [
      doc('Skill Guide', '.claude/ibm-i-test-scaffold/SKILL.md', 'How executable SQL/CL test scripts are generated from UT plans', true),
      doc('UT Plan Generator Guide', '.claude/ibm-i-ut-plan-generator/SKILL.md', 'Typical upstream input source'),
    ],
    outputs: [
      doc('Sample Batch RPGLE Scaffold', '.claude/ibm-i-test-scaffold/examples/sample-batch-rpgle-test-scaffold.md', 'Example executable test scaffold', true),
      doc('Sample Interactive Scaffold', '.claude/ibm-i-test-scaffold/examples/sample-interactive-rpgle-test-scaffold.md', 'Example interactive program scaffold'),
      doc('Test Harness Notes', '.claude/ibm-i-test-scaffold/tests/test-harness.md', 'Validation notes for scaffold structure'),
    ],
  },
  {
    aliases: ['compile precheck', 'precheck', 'compile safety', 'ibm i compile precheck'],
    primarySkill: skill('ibm-i-compile-precheck'),
    inputs: [
      doc('Skill Guide', '.claude/ibm-i-compile-precheck/SKILL.md', 'How generated or written code is checked before compile', true),
      doc('Fixed Format Checklist', '.claude/ibm-i-compile-precheck/references/fixed-format-checklists.md', 'Common compile-safety checklist'),
    ],
    outputs: [
      doc('README Review Notes', 'README.md', 'High-level review and gate guidance'),
    ],
  },
  {
    aliases: ['spec reviewer', 'review spec', 'ibm i spec reviewer'],
    primarySkill: skill('ibm-i-spec-reviewer'),
    inputs: [
      doc('Skill Guide', '.claude/ibm-i-spec-reviewer/SKILL.md', 'How spec artifacts are reviewed for quality and layer discipline', true),
      doc('Sample Spec Review', '.claude/ibm-i-spec-reviewer/examples/sample-spec-review.md', 'Example reviewer output format'),
    ],
    outputs: [
      doc('Sample Spec Review', '.claude/ibm-i-spec-reviewer/examples/sample-spec-review.md', 'Example findings and readiness decision', true),
    ],
  },
  {
    aliases: ['dds reviewer', 'review dds', 'ibm i dds reviewer'],
    primarySkill: skill('ibm-i-dds-reviewer'),
    inputs: [
      doc('Skill Guide', '.claude/ibm-i-dds-reviewer/SKILL.md', 'How DDS output is reviewed against File Spec', true),
      doc('File Spec Validation Rules', '.claude/ibm-i-file-spec/references/validation-rules.md', 'Common DDS review checkpoints'),
    ],
    outputs: [
      doc('README Review Notes', 'README.md', 'High-level review and gate guidance'),
    ],
  },
  {
    aliases: ['code reviewer', 'review code', 'ibm i code reviewer'],
    primarySkill: skill('ibm-i-code-reviewer'),
    inputs: [
      doc('Skill Guide', '.claude/ibm-i-code-reviewer/SKILL.md', 'How RPGLE/CLLE is reviewed against Program Spec', true),
      doc('Review Checkpoints', '.claude/ibm-i-code-reviewer/references/review-checkpoints.md', 'Common code review checkpoints'),
    ],
    outputs: [
      doc('Sample Review Findings', '.claude/ibm-i-code-reviewer/examples/sample-review-br-coverage-gap.md', 'Example reviewer finding output', true),
    ],
  },
  {
    aliases: ['workflow orchestrator', 'orchestrator', 'ibm i workflow orchestrator'],
    primarySkill: skill('ibm-i-workflow-orchestrator'),
    inputs: [
      doc('Skill Guide', '.claude/ibm-i-workflow-orchestrator/SKILL.md', 'How the safest next step is chosen across the IBM i chain', true),
    ],
    outputs: [
      doc('README Workflow', 'README.md', 'High-level chain and recommended workflow', true),
      doc('AGENTS Overview', 'AGENTS.md', 'Quick reference for which skill to use next'),
    ],
  },
]
