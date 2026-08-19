<script setup lang="ts">
import { useProjectCategoryStore } from '@/stores/projectCategory'
import { storeToRefs } from 'pinia'
import { LayoutGrid, List } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'

const store = useProjectCategoryStore()
const { viewMode } = storeToRefs(store)

function onSwitch(mode: typeof viewMode.value) {
  if (viewMode.value === mode) return
  const prev = viewMode.value
  try {
    store.setViewMode(mode)
  } catch {
    store.setViewMode(prev)
    ElMessage.error('视图切换失败，已恢复原视图')
  }
}
</script>

<template>
  <div class="inline-flex items-center rounded-lg border border-line-light bg-surface-light p-1">
    <button
      class="flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium transition-all duration-200 ease-out-expo"
      :class="viewMode === 'card' ? 'bg-white text-primary-600 shadow-sm' : 'text-ink-secondary hover:text-ink'"
      title="卡片视图"
      @click="onSwitch('card')"
    >
      <LayoutGrid :size="15" />
      <span>卡片</span>
    </button>
    <button
      class="flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-medium transition-all duration-200 ease-out-expo"
      :class="viewMode === 'list' ? 'bg-white text-primary-600 shadow-sm' : 'text-ink-secondary hover:text-ink'"
      title="列表视图"
      @click="onSwitch('list')"
    >
      <List :size="15" />
      <span>列表</span>
    </button>
  </div>
</template>
