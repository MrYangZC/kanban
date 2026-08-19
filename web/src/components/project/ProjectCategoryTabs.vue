<script setup lang="ts">
import { useProjectCategoryStore } from '@/stores/projectCategory'
import { storeToRefs } from 'pinia'

const store = useProjectCategoryStore()
const { categories, selectedCategory } = storeToRefs(store)

function onSelect(key: typeof selectedCategory.value) {
  store.selectCategory(key)
}
</script>

<template>
  <div class="flex items-center gap-1 overflow-x-auto border-b border-line-light pb-px">
    <button
      v-for="cat in categories"
      :key="cat.key"
      class="flex shrink-0 items-center gap-1.5 rounded-t-md px-4 py-2.5 text-sm font-medium transition"
      :class="selectedCategory === cat.key
        ? 'border-b-2 border-primary text-primary'
        : 'border-b-2 border-transparent text-ink-secondary hover:text-ink hover:bg-surface-hover'"
      @click="onSelect(cat.key)"
    >
      <span>{{ cat.label }}</span>
      <span
        class="inline-flex min-w-[20px] items-center justify-center rounded-full px-1.5 py-0.5 text-xs"
        :class="selectedCategory === cat.key
          ? 'bg-primary text-white'
          : 'bg-surface-light text-ink-secondary'"
      >{{ cat.count }}</span>
    </button>
  </div>
</template>