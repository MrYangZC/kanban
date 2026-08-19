<script setup lang="ts">
import type { Component } from 'vue'
import { Plus, Search } from 'lucide-vue-next'

const props = withDefaults(defineProps<{
  title: string
  icon: Component
  searchPlaceholder?: string
  loading?: boolean
  emptyText?: string
  canCreate?: boolean
  createLabel?: string
  keyword?: string
  showSearch?: boolean
  showRefresh?: boolean
  isEmpty?: boolean
}>(), {
  searchPlaceholder: '搜索…',
  loading: false,
  emptyText: '暂无数据',
  canCreate: true,
  createLabel: '新建',
  keyword: '',
  showSearch: true,
  showRefresh: true,
  isEmpty: false
})

const emit = defineEmits<{
  (e: 'update:keyword', v: string): void
  (e: 'search'): void
  (e: 'refresh'): void
  (e: 'create'): void
}>()

function onInput(e: Event) {
  emit('update:keyword', (e.target as HTMLInputElement).value)
}
</script>

<template>
  <div class="h-full overflow-auto p-6">
    <div class="mb-4 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <component :is="props.icon" :size="20" class="text-brand" />
        <h1 class="text-lg font-semibold text-ink-900">{{ title }}</h1>
      </div>
      <div class="flex items-center gap-2">
        <slot name="header-extra" />
        <button
          v-if="canCreate"
          class="flex items-center gap-1.5 rounded-md bg-brand px-3 py-1.5 text-sm font-medium text-white transition hover:bg-brand-dark"
          @click="emit('create')"
        >
          <Plus :size="14" /> {{ createLabel }}
        </button>
      </div>
    </div>

    <div v-if="showSearch || showRefresh" class="mb-4 flex items-center gap-2">
      <div v-if="showSearch" class="flex flex-1 items-center gap-2 rounded-md border border-[#E5E6EB] bg-white px-3 py-1.5">
        <Search :size="14" class="text-ink-400" />
        <input
          :value="keyword"
          class="flex-1 bg-transparent text-sm outline-none placeholder:text-ink-400"
          :placeholder="searchPlaceholder"
          @input="onInput"
          @keydown.enter="emit('search')"
        />
      </div>
      <button
        v-if="showRefresh"
        class="rounded-md border border-[#E5E6EB] bg-white px-3 py-1.5 text-sm text-ink-600 transition hover:bg-surface-muted"
        @click="emit('refresh')"
      >刷新</button>
      <slot name="toolbar-extra" />
    </div>

    <div class="rounded-lg border border-[#E9ECEF] bg-white">
      <div v-if="loading" class="py-20 text-center text-sm text-ink-400">加载中…</div>
      <div v-else-if="isEmpty" class="py-20 text-center text-sm text-ink-400">{{ emptyText }}</div>
      <slot v-else />
    </div>

    <slot name="footer" />
  </div>
</template>
