<script setup lang="ts">
import { computed, ref } from 'vue'
import type { CsvCompareResult } from '../types'

const props = defineProps<{
  compareFn: (files: File[]) => Promise<CsvCompareResult>
}>()

const emit = defineEmits<{
  close: []
}>()

const files = ref<File[]>([])
const comparing = ref(false)
const error = ref<string | null>(null)
const result = ref<CsvCompareResult | null>(null)

const canSubmit = computed(() => files.value.length >= 2 && !comparing.value)

function onFileChange(event: Event) {
  const input = event.target as HTMLInputElement
  files.value = Array.from(input.files ?? [])
  result.value = null
  error.value = null
}

function formatNumber(value: number) {
  return new Intl.NumberFormat().format(value)
}

function rowPreview(row?: string[] | null) {
  if (!row || row.length === 0) return '—'
  return row.slice(0, 6).join(' | ')
}

async function submit() {
  if (files.value.length < 2) {
    error.value = 'Choose at least two CSV files.'
    return
  }

  comparing.value = true
  error.value = null
  result.value = null
  try {
    result.value = await props.compareFn(files.value)
  } catch (e) {
    error.value = e instanceof Error ? e.message : 'Compare failed'
  } finally {
    comparing.value = false
  }
}
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal compare-modal">
      <div class="modal-header">
        <span class="modal-title">Compare Files</span>
        <button class="modal-close" type="button" @click="emit('close')">×</button>
      </div>

      <div class="modal-body compare-body">
        <div v-if="error" class="alert alert-error">{{ error }}</div>

        <div class="compare-upload">
          <label class="form-label" for="csv-compare-files">CSV Files</label>
          <input
            id="csv-compare-files"
            class="form-control"
            type="file"
            accept=".csv,text/csv"
            multiple
            :disabled="comparing"
            @change="onFileChange"
          />
          <p class="compare-hint">
            The first selected file is the base. Files must share the same header row.
          </p>
        </div>

        <div v-if="files.length > 0" class="selected-files">
          <div
            v-for="(file, index) in files"
            :key="`${file.name}-${file.size}-${index}`"
            class="selected-file"
          >
            <span class="file-role">{{ index === 0 ? 'Base' : 'Compare' }}</span>
            <span class="file-name">{{ file.name }}</span>
            <span class="file-size">{{ formatNumber(file.size) }} bytes</span>
          </div>
        </div>

        <div v-if="result" class="compare-result">
          <div class="result-summary">
            <div>
              <span class="summary-label">Base</span>
              <strong>{{ result.baseFileName }}</strong>
            </div>
            <div>
              <span class="summary-label">Files</span>
              <strong>{{ result.fileCount }}</strong>
            </div>
            <div>
              <span class="summary-label">Differences</span>
              <strong>{{ formatNumber(result.totalDifferences) }}</strong>
            </div>
          </div>

          <div v-if="result.truncated" class="alert alert-info">
            Showing the first 1,000 difference samples per compared file.
          </div>

          <section
            v-for="comparison in result.comparisons"
            :key="comparison.fileName"
            class="comparison-section"
          >
            <div class="comparison-header">
              <div>
                <h3>{{ comparison.fileName }}</h3>
                <p>
                  {{ formatNumber(comparison.matchedRows) }} matched rows ·
                  {{ formatNumber(comparison.changedRows) }} changed ·
                  {{ formatNumber(comparison.addedRows) }} added ·
                  {{ formatNumber(comparison.removedRows) }} removed
                </p>
              </div>
              <span
                class="badge"
                :class="comparison.totalDifferences === 0 ? 'badge-completed' : 'badge-failed'"
              >
                {{ formatNumber(comparison.totalDifferences) }} diffs
              </span>
            </div>

            <div v-if="comparison.differences.length === 0" class="empty-diff">
              No differences found.
            </div>
            <div v-else class="diff-table-wrap">
              <table class="data-table diff-table">
                <thead>
                  <tr>
                    <th>Row</th>
                    <th>Type</th>
                    <th>Column</th>
                    <th>Base</th>
                    <th>Compare</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="(difference, index) in comparison.differences"
                    :key="`${comparison.fileName}-${difference.rowNumber}-${difference.column ?? difference.type}-${index}`"
                  >
                    <td>{{ difference.rowNumber }}</td>
                    <td>{{ difference.type }}</td>
                    <td>{{ difference.column || '—' }}</td>
                    <td>{{ difference.baseValue ?? rowPreview(difference.baseRow) }}</td>
                    <td>{{ difference.compareValue ?? rowPreview(difference.compareRow) }}</td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section>
        </div>
      </div>

      <div class="modal-footer">
        <button class="btn btn-secondary" type="button" :disabled="comparing" @click="emit('close')">
          Close
        </button>
        <button class="btn btn-primary" type="button" :disabled="!canSubmit" @click="submit">
          <span v-if="comparing" class="spinner" style="width:14px;height:14px;border-width:2px;"></span>
          {{ comparing ? 'Comparing...' : 'Submit Compare' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.compare-modal {
  width: min(1040px, calc(100vw - 40px));
}

.compare-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.compare-upload {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.compare-hint {
  color: var(--color-text-muted);
  font-size: 12px;
}

.selected-files {
  display: grid;
  gap: 8px;
}

.selected-file {
  display: grid;
  grid-template-columns: 92px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: center;
  padding: 8px 10px;
  border: 1px solid var(--color-border-subtle);
  border-radius: 6px;
  background: rgba(248, 250, 252, 0.84);
}

.file-role {
  font-weight: 700;
  color: #2563eb;
}

.file-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-size {
  color: var(--color-text-muted);
  font-size: 12px;
}

.compare-result {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.result-summary {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 120px 160px;
  gap: 10px;
}

.result-summary > div {
  padding: 12px;
  border: 1px solid var(--color-border-subtle);
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.76);
}

.summary-label {
  display: block;
  margin-bottom: 4px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.comparison-section {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.comparison-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.comparison-header h3 {
  margin: 0;
  font-size: 16px;
}

.comparison-header p {
  margin-top: 4px;
  color: var(--color-text-muted);
  font-size: 12px;
}

.diff-table-wrap {
  max-height: 360px;
  overflow: auto;
  border-radius: 8px;
}

.diff-table td {
  max-width: 260px;
  overflow-wrap: anywhere;
  font-family: var(--font-mono);
  font-size: 12px;
}

.empty-diff {
  padding: 16px;
  border: 1px solid var(--color-border-subtle);
  border-radius: 8px;
  color: var(--color-text-muted);
  background: rgba(248, 250, 252, 0.82);
}

@media (max-width: 720px) {
  .selected-file,
  .result-summary {
    grid-template-columns: 1fr;
  }
}
</style>
