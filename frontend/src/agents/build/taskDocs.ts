import type { Task } from '../../types'
import type { TaskDocSpec } from '../../platform/composables/releaseFlowTypes'
import { BUILD_TASK_DOC_CATALOG } from './taskDocCatalog'

function normalize(value: string | null | undefined): string {
  return (value ?? '')
    .toLowerCase()
    .replace(/ibm[\s-]*i/g, 'ibm i')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim()
}

function toTaskDocSpec(entry: (typeof BUILD_TASK_DOC_CATALOG)[number]): TaskDocSpec {
  return {
    primarySkill: entry.primarySkill,
    relatedSkills: entry.relatedSkills,
    inputs: entry.inputs,
    outputs: entry.outputs,
    suggestedInputs: entry.inputs,
    suggestedOutputs: entry.outputs,
    hasOverrides: false,
  }
}

function buildEntryKeys(entry: (typeof BUILD_TASK_DOC_CATALOG)[number]): string[] {
  return [
    entry.primarySkill.key,
    entry.primarySkill.label,
    ...entry.aliases,
  ].map(normalize).filter(Boolean)
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null
}

function parseDocLink(value: unknown) {
  if (!isRecord(value)) return null

  const label = typeof value.label === 'string' ? value.label.trim() : ''
  const url = typeof value.url === 'string' ? value.url.trim() : ''
  const note = typeof value.note === 'string' ? value.note.trim() : undefined
  const required = typeof value.required === 'boolean' ? value.required : false

  if (!label || !url) return null

  return {
    label,
    url,
    note: note || undefined,
    required,
  }
}

function getTaskDocOverrides(task: Task) {
  if (!isRecord(task.customFields)) return null
  const taskDocs = task.customFields.taskDocs
  if (!isRecord(taskDocs)) return null

  const inputs = Array.isArray(taskDocs.inputs)
    ? taskDocs.inputs.map(parseDocLink).filter((doc): doc is NonNullable<typeof doc> => !!doc)
    : []
  const outputs = Array.isArray(taskDocs.outputs)
    ? taskDocs.outputs.map(parseDocLink).filter((doc): doc is NonNullable<typeof doc> => !!doc)
    : []

  return { inputs, outputs }
}

function genericSkillLabel(task: Task): string {
  const script = task.inputParameters?.script?.trim()
  if (script) return script
  const taskName = task.taskName?.trim()
  if (taskName) return taskName
  return 'task-docs'
}

function withOverrides(task: Task, defaults: TaskDocSpec | null): TaskDocSpec | null {
  const overrides = getTaskDocOverrides(task)
  if (!defaults && !overrides) {
    return null
  }

  const primarySkill = defaults?.primarySkill ?? {
    key: genericSkillLabel(task),
    label: genericSkillLabel(task),
    role: 'primary' as const,
  }

  return {
    primarySkill,
    relatedSkills: defaults?.relatedSkills ?? [],
    suggestedInputs: defaults?.suggestedInputs ?? [],
    suggestedOutputs: defaults?.suggestedOutputs ?? [],
    inputs: overrides?.inputs ?? defaults?.inputs ?? [],
    outputs: overrides?.outputs ?? defaults?.outputs ?? [],
    hasOverrides: !!overrides,
  }
}

export function resolveBuildTaskDocs(task: Task): TaskDocSpec | null {
  const scriptCandidate = normalize(task.inputParameters?.script)

  if (scriptCandidate) {
    for (const entry of BUILD_TASK_DOC_CATALOG) {
      const keys = buildEntryKeys(entry)
      const exactMatch = keys.some((key) => scriptCandidate === key)
      if (exactMatch) {
        return withOverrides(task, toTaskDocSpec(entry))
      }
    }

    for (const entry of BUILD_TASK_DOC_CATALOG) {
      const keys = buildEntryKeys(entry)
      const partialMatch = keys.some(
        (key) => scriptCandidate.includes(key) || key.includes(scriptCandidate),
      )
      if (partialMatch) {
        return withOverrides(task, toTaskDocSpec(entry))
      }
    }
  }

  const fallbackCandidates = [
    normalize(task.taskName),
    normalize(task.taskGroupName),
    normalize(task.category),
  ].filter(Boolean)

  if (fallbackCandidates.length === 0) return null

  for (const entry of BUILD_TASK_DOC_CATALOG) {
    const keys = buildEntryKeys(entry)
    const matches = fallbackCandidates.some((candidate) =>
      keys.some((key) => candidate === key || candidate.includes(key) || key.includes(candidate)),
    )
    if (matches) {
      return withOverrides(task, toTaskDocSpec(entry))
    }
  }

  return withOverrides(task, null)
}
