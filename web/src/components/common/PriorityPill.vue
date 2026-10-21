<script setup lang="ts">
import { PRIORITY_COLOR, PRIORITY_LABEL, FALLBACK_COLOR } from '@/theme/colorMap'

const props = withDefaults(defineProps<{
  priority?: string | null
  size?: 'xs' | 'sm'
}>(), {
  priority: '',
  size: 'sm',
})
</script>

<template>
  <span
    class="badge-solid"
    :class="props.size === 'xs' ? 'badge-xs' : ''"
    :style="{ background: PRIORITY_COLOR[props.priority || ''] || FALLBACK_COLOR }"
  >
    {{ PRIORITY_LABEL[props.priority || ''] || props.priority || '—' }}
  </span>
</template>
