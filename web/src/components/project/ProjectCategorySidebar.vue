<script setup lang="ts">
import { useProjectCategoryStore } from '@/stores/projectCategory'
import { storeToRefs } from 'pinia'
import { FolderKanban, ShieldCheck, Users, Star, Archive } from 'lucide-vue-next'
import { computed } from 'vue'
import type { ProjectCategoryKey } from '@/api/types'

const store = useProjectCategoryStore()
const { categories, selectedCategory } = storeToRefs(store)

const iconMap: Record<ProjectCategoryKey, any> = {
  all: FolderKanban,
  managed: ShieldCheck,
  participated: Users,
  favorited: Star,
  archived: Archive
}

const iconColorMap: Record<ProjectCategoryKey, string> = {
  all: 'text-primary-500',
  managed: 'text-success',
  participated: 'text-primary-400',
  favorited: 'text-warning',
  archived: 'text-ink-secondary'
}

const items = computed(() =>
  categories.value.map((c) => ({
    ...c,
    icon: iconMap[c.key],
    iconColor: iconColorMap[c.key]
  }))
)

function onSelect(key: typeof selectedCategory.value) {
  store.selectCategory(key)
}
</script>

<template>
  <nav class="flex-1 space-y-1 overflow-y-auto px-3 py-3">
    <button
      v-for="cat in items"
      :key="cat.key"
      class="group flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left text-sm transition-all duration-200 ease-out-expo"
      :class="selectedCategory === cat.key
        ? 'bg-primary-50 font-medium text-primary-700 shadow-sm'
        : 'text-ink-regular hover:bg-surface-hover hover:text-ink'"
      @click="onSelect(cat.key)"
    >
      <span
        class="flex h-7 w-7 shrink-0 items-center justify-center rounded-md transition-all duration-200"
        :class="selectedCategory === cat.key
          ? 'bg-primary-500 text-white shadow-sm'
          : 'bg-surface-light text-ink-secondary group-hover:bg-primary-100 group-hover:text-primary-500'"
      >
        <component :is="cat.icon" :size="15" />
      </span>
      <span class="flex-1 truncate">{{ cat.label }}</span>
      <span
        class="inline-flex min-w-[22px] shrink-0 items-center justify-center rounded-full px-2 py-0.5 text-xs font-medium transition-all duration-200"
        :class="selectedCategory === cat.key
          ? 'bg-primary-500 text-white'
          : 'bg-surface-light text-ink-secondary group-hover:bg-primary-100 group-hover:text-primary-600'"
      >{{ cat.count }}</span>
    </button>
  </nav>
</template>
