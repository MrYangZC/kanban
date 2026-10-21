<script setup lang="ts">
import type { Component } from 'vue'

export interface ScopeNavItem {
  key: string
  label: string
  icon: Component
  count?: number
}

export interface ScopeNavGroup {
  title: string
  items: ScopeNavItem[]
}

withDefaults(defineProps<{
  groups: ScopeNavGroup[]
  modelValue?: string
}>(), {
  modelValue: ''
})

const emit = defineEmits<{
  (e: 'update:modelValue', key: string): void
}>()
</script>

<template>
  <aside class="scope-nav">
    <div v-for="grp in groups" :key="grp.title" class="scope-nav-group">
      <div class="scope-nav-title">{{ grp.title }}</div>
      <button
        v-for="item in grp.items"
        :key="item.key"
        class="scope-nav-item"
        :class="modelValue === item.key ? 'scope-nav-item-active' : 'scope-nav-item-idle'"
        @click="emit('update:modelValue', item.key)"
      >
        <component :is="item.icon" :size="15" class="shrink-0" />
        <span class="flex-1 text-left">{{ item.label }}</span>
        <span v-if="item.count !== undefined" class="scope-nav-count">{{ item.count }}</span>
      </button>
    </div>
  </aside>
</template>
