<script setup lang="ts">
import { computed, ref } from 'vue'

const props = defineProps<{
  entityType: 'scope' | 'group' | 'link'
  title: string
  removedGroups?: number
  removedLinks?: number
  saving?: boolean
}>()

const emit = defineEmits<{
  close: []
  confirm: []
}>()

const submitting = ref(false)

const impactText = computed(() => {
  if (props.entityType === 'link') {
    return null
  }
  if (props.entityType === 'group') {
    const links = props.removedLinks ?? 0
    if (links === 0) {
      return null
    }
    return links === 1 ? 'This removes 1 link.' : `This removes ${links} links.`
  }
  const groups = props.removedGroups ?? 0
  const links = props.removedLinks ?? 0
  if (groups === 0 && links === 0) {
    return null
  }
  const groupLabel = groups === 1 ? '1 group' : `${groups} groups`
  const linkLabel = links === 1 ? '1 link' : `${links} links`
  return `This removes ${groupLabel} and ${linkLabel}.`
})

const dialogTitle = computed(() => {
  const label = props.entityType === 'scope' ? 'Scope' : props.entityType === 'group' ? 'Group' : 'Link'
  return `Delete ${label}`
})

async function confirmDelete() {
  submitting.value = true
  try {
    emit('confirm')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="modal-overlay" @click.self="emit('close')">
    <div class="modal">
      <div class="modal-header">
        <span class="modal-title">{{ dialogTitle }}</span>
        <button class="modal-close" type="button" @click="emit('close')">✕</button>
      </div>

      <div class="modal-body">
        <div class="alert alert-error">
          Delete "{{ title }}"?<span v-if="impactText"> {{ impactText }}</span>
        </div>
      </div>

      <div class="modal-footer">
        <button class="btn btn-secondary" type="button" @click="emit('close')">Cancel</button>
        <button
          class="btn btn-danger"
          type="button"
          :disabled="submitting || saving"
          @click="confirmDelete"
        >
          {{ submitting || saving ? 'Deleting...' : 'Delete' }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.btn-danger {
  background: #dc2626;
  border-color: #dc2626;
  color: #fff;
}

.btn-danger:hover:not(:disabled) {
  background: #b91c1c;
  border-color: #b91c1c;
}
</style>
