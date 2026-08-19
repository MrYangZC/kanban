<script setup lang="ts">
import type { Component } from 'vue'

interface Props {
  label: string
  value: number | string
  icon: Component
  color?: string
  hint?: string
}

const props = withDefaults(defineProps<Props>(), {
  color: '#1677FF',
  hint: ''
})
</script>

<template>
  <div class="rounded-lg border border-[#E9ECEF] bg-white p-4 shadow-sm transition hover:shadow-md">
    <div class="flex items-center justify-between">
      <span class="text-sm text-ink-400">{{ props.label }}</span>
      <component :is="props.icon" :size="16" :style="{ color: props.color }" />
    </div>
    <div class="mt-2 text-2xl font-semibold" :style="{ color: props.color }">{{ props.value }}</div>
    <div v-if="props.hint" class="mt-1 text-xs text-ink-400">{{ props.hint }}</div>
  </div>
</template>