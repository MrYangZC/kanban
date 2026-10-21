<script setup lang="ts">
export type RowActionTone = 'default' | 'success' | 'warning' | 'danger'

export interface RowAction {
  label: string
  tone?: RowActionTone
  disabled?: boolean
  hidden?: boolean
  onClick: () => void
}

defineProps<{
  actions: RowAction[]
}>()

// 配色取自 tailwind 主题令牌（success/warning/danger），与 issueDict 功能色同源
const TONE_CLASS: Record<RowActionTone, string> = {
  default: 'row-action-default',
  success: 'row-action-success',
  warning: 'row-action-warning',
  danger: 'row-action-danger',
}
</script>

<template>
  <div class="row-actions">
    <button
      v-for="action in actions"
      v-show="!action.hidden"
      :key="action.label"
      type="button"
      class="row-action"
      :class="TONE_CLASS[action.tone || 'default']"
      :disabled="action.disabled"
      @click.stop="action.onClick()"
    >
      {{ action.label }}
    </button>
  </div>
</template>
