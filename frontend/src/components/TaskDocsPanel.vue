<script setup lang="ts">
import type { TaskDocSpec } from '../platform/composables/releaseFlowTypes'

defineProps<{
  taskDocs: TaskDocSpec
}>()
</script>

<template>
  <div class="task-docs-panel">
    <div class="task-docs-header">
      <div class="task-docs-group">
        <div class="task-docs-label">Primary Skill</div>
        <span class="task-doc-skill-chip">
          {{ taskDocs.primarySkill.label }}
        </span>
      </div>

      <div v-if="taskDocs.relatedSkills?.length" class="task-docs-group">
        <div class="task-docs-label">Related Skills</div>
        <div class="task-doc-skill-list">
          <span
            v-for="skill in taskDocs.relatedSkills"
            :key="`${skill.role}:${skill.key}`"
            class="task-doc-skill-chip task-doc-skill-chip-secondary"
          >
            {{ skill.label }}
          </span>
        </div>
      </div>
    </div>

    <div class="task-docs-grid">
      <div class="task-docs-card">
        <div class="task-docs-title">Input Docs</div>
        <div v-if="taskDocs.inputs.length === 0" class="task-docs-empty">No input docs configured.</div>
        <div v-for="doc in taskDocs.inputs" :key="`input:${doc.label}:${doc.url}`" class="task-doc-item">
          <div class="task-doc-item-row">
            <a :href="doc.url" target="_blank" rel="noopener" class="task-doc-link">
              {{ doc.label }}
            </a>
            <span v-if="doc.required" class="task-doc-required">Required</span>
          </div>
          <div v-if="doc.note" class="task-doc-note">{{ doc.note }}</div>
        </div>
      </div>

      <div class="task-docs-card">
        <div class="task-docs-title">Output Docs</div>
        <div v-if="taskDocs.outputs.length === 0" class="task-docs-empty">No output docs configured.</div>
        <div v-for="doc in taskDocs.outputs" :key="`output:${doc.label}:${doc.url}`" class="task-doc-item">
          <div class="task-doc-item-row">
            <a :href="doc.url" target="_blank" rel="noopener" class="task-doc-link">
              {{ doc.label }}
            </a>
            <span v-if="doc.required" class="task-doc-required">Required</span>
          </div>
          <div v-if="doc.note" class="task-doc-note">{{ doc.note }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.task-docs-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.task-docs-header {
  display: flex;
  flex-wrap: wrap;
  gap: 16px 24px;
}

.task-docs-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.task-docs-label {
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-text-muted);
}

.task-doc-skill-list {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.task-doc-skill-chip {
  display: inline-flex;
  align-items: center;
  min-height: 28px;
  padding: 0 10px;
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  font-size: 12px;
  font-weight: 700;
}

.task-doc-skill-chip-secondary {
  background: #eff6ff;
  color: #2563eb;
}

.task-docs-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.task-docs-card {
  padding: 14px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  background: #f8fafc;
}

.task-docs-title {
  margin-bottom: 12px;
  font-size: 13px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.task-doc-item + .task-doc-item {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #e2e8f0;
}

.task-doc-item-row {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.task-doc-link {
  color: #2563eb;
  font-size: 13px;
  font-weight: 600;
  text-decoration: none;
  word-break: break-word;
}

.task-doc-link:hover {
  text-decoration: underline;
}

.task-doc-required {
  display: inline-flex;
  align-items: center;
  min-height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
  font-size: 11px;
  font-weight: 700;
}

.task-doc-note,
.task-docs-empty {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--color-text-muted);
}

@media (max-width: 720px) {
  .task-docs-grid {
    grid-template-columns: 1fr;
  }
}
</style>
