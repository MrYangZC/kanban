<script setup lang="ts">
import { computed } from 'vue'
import { FALLBACK_COLOR } from '@/theme/colorMap'

export interface StatusStyle {
  label: string
  color: string
  bg?: string
}

const props = withDefaults(defineProps<{
  status: string
  dict: Record<string, StatusStyle>
  size?: 'xs' | 'sm' | 'md'
  fallback?: StatusStyle
}>(), {
  size: 'sm',
  fallback: () => ({ label: '', color: FALLBACK_COLOR })
})

const style = computed<StatusStyle>(() => props.dict[props.status] || props.fallback)
const badgeBg = computed(() => `color-mix(in srgb, ${style.value.color} 10%, transparent)`)

const paddingClass = computed(() => {
  if (props.size === 'xs') return 'px-1.5 py-0.5 text-[10px]'
  if (props.size === 'md') return 'px-2.5 py-1 text-sm'
  return 'px-2 py-0.5 text-xs'
})
</script>

<template>
  <span
    class="inline-flex items-center rounded font-medium"
    :class="paddingClass"
    :style="{ color: style.color, backgroundColor: badgeBg }"
  >{{ style.label || status }}</span>
</template>