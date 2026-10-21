<script setup lang="ts">
import { badgeStyle, FALLBACK_COLOR } from '@/theme/colorMap'

withDefaults(defineProps<{
  label: string
  color?: string
  size?: 'xs' | 'sm'
}>(), {
  color: FALLBACK_COLOR,
  size: 'sm',
})
</script>

<template>
  <span class="badge" :class="size === 'xs' ? 'badge-xs' : ''" :style="badgeStyle({ label, color })">
    {{ label }}
  </span>
</template>
