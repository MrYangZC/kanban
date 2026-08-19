<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  value: number
  max?: number
  height?: 'sm' | 'md' | 'lg'
  color?: string
  showLabel?: boolean
  label?: string
}>(), {
  max: 100,
  height: 'md',
  color: '',
  showLabel: false,
  label: ''
})

const percent = computed(() => {
  if (!props.max) return 0
  const p = (props.value / props.max) * 100
  return Math.min(100, Math.max(0, p))
})

const heightClass = computed(() => {
  if (props.height === 'sm') return 'h-1.5'
  if (props.height === 'lg') return 'h-2.5'
  return 'h-2'
})

const barStyle = computed(() => {
  const base: Record<string, string> = { width: percent.value + '%' }
  if (props.color) base.background = props.color
  return base
})
</script>

<template>
  <div>
    <div v-if="showLabel" class="mb-1 flex items-center justify-between text-xs text-ink-400">
      <span>{{ label }}</span>
      <span>{{ value }}/{{ max }}</span>
    </div>
    <div class="overflow-hidden rounded-full bg-surface-muted" :class="heightClass">
      <div class="h-full rounded-full bg-brand" :style="barStyle" />
    </div>
  </div>
</template>