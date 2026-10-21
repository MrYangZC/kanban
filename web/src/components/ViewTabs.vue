<script setup lang="ts">
import type { Component } from 'vue'

export interface ViewTab {
  key: string
  label: string
  icon: Component
}

withDefaults(defineProps<{
  tabs: ViewTab[]
  modelValue?: string
}>(), {
  modelValue: ''
})

const emit = defineEmits<{
  (e: 'update:modelValue', key: string): void
}>()
</script>

<template>
  <div class="view-tabs">
    <button
      v-for="t in tabs"
      :key="t.key"
      class="view-tab"
      :class="modelValue === t.key ? 'view-tab-active' : 'view-tab-idle'"
      @click="emit('update:modelValue', t.key)"
    >
      <component :is="t.icon" :size="14" /> {{ t.label }}
    </button>
  </div>
</template>
