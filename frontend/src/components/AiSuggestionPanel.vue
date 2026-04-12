<script setup lang="ts">
/**
 * AI Assist — visual-only placeholder.
 *
 * Phase-A AI advisor hook. This component renders a static, disabled preview
 * of where AI-generated suggestions will appear. It makes NO API calls and
 * produces NO real recommendations. The goal is to reserve the UX slot and
 * wiring so that when the real AiAdvisorService is added later, only this
 * file changes — the dialogs that host it are already correct.
 *
 * Rendering is gated by AI_ASSIST_PREVIEW_ENABLED in platformConfig.
 */
import { computed } from 'vue'

type PanelContext = 'decision' | 'task-edit'

const props = withDefaults(defineProps<{
  context: PanelContext
  taskName?: string
}>(), {
  taskName: '',
})

const headline = computed(() =>
  props.context === 'decision'
    ? 'Suggested decision will appear here'
    : 'Suggested inputs will appear here',
)

const mockLine = computed(() =>
  props.context === 'decision'
    ? 'Based on the task history and prior similar flows, the advisor will recommend Approve / Reject / Rerun / Skip with a short rationale.'
    : 'Based on the task type, previous successful runs, and related templates, the advisor will suggest script snippets and parameter values.',
)
</script>

<template>
  <section class="ai-panel" aria-label="AI Assist preview">
    <header class="ai-panel-header">
      <span class="ai-icon" aria-hidden="true">✨</span>
      <span class="ai-title">AI Assist</span>
      <span class="ai-badge">Preview · Coming soon</span>
    </header>

    <div class="ai-body">
      <div class="ai-headline">{{ headline }}</div>
      <p class="ai-mock">{{ mockLine }}</p>
      <div class="ai-hint">
        <span class="ai-hint-dot">•</span>
        This panel is a placeholder. No real model calls are made yet.
        Human decisions remain authoritative.
      </div>
    </div>

    <footer class="ai-footer">
      <button type="button" class="btn-ghost" disabled>Apply suggestion</button>
      <button type="button" class="btn-ghost" disabled>Explain</button>
    </footer>
  </section>
</template>

<style scoped>
.ai-panel {
  position: relative;
  margin-bottom: 16px;
  padding: 14px 16px;
  border: 1px dashed #c7d2fe;
  border-radius: 10px;
  background:
    linear-gradient(135deg, rgba(238, 242, 255, 0.8) 0%, rgba(245, 243, 255, 0.8) 100%);
}

.ai-panel::before {
  content: '';
  position: absolute;
  inset: 0 auto 0 0;
  width: 3px;
  border-radius: 10px 0 0 10px;
  background: linear-gradient(180deg, #818cf8, #a78bfa);
  opacity: 0.6;
}

.ai-panel-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.ai-icon {
  font-size: 15px;
}

.ai-title {
  font-size: 13px;
  font-weight: 700;
  color: #4338ca;
  letter-spacing: 0.02em;
}

.ai-badge {
  margin-left: auto;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: #6366f1;
  background: rgba(99, 102, 241, 0.12);
  padding: 3px 8px;
  border-radius: 999px;
}

.ai-body {
  font-size: 12.5px;
  color: var(--color-text-secondary);
}

.ai-headline {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 4px;
}

.ai-mock {
  margin: 0 0 8px;
  line-height: 1.5;
  color: var(--color-text-muted);
  font-style: italic;
}

.ai-hint {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  font-size: 11px;
  color: #6366f1;
  line-height: 1.45;
}

.ai-hint-dot {
  font-weight: 700;
}

.ai-footer {
  display: flex;
  gap: 8px;
  margin-top: 10px;
}

.btn-ghost {
  font-size: 12px;
  font-weight: 500;
  padding: 5px 10px;
  border: 1px solid #c7d2fe;
  border-radius: 6px;
  background: rgba(255, 255, 255, 0.6);
  color: #6366f1;
  cursor: not-allowed;
  opacity: 0.65;
}
</style>
