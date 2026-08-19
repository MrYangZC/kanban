<script setup lang="ts">
import { ref } from 'vue'
import { Plus, Repeat, Calendar, MoreHorizontal } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'

interface Iteration {
  id: string
  name: string
  startDate: string
  endDate: string
  status: 'planning' | 'active' | 'closed'
  taskCount: number
  doneCount: number
}

const iterations = ref<Iteration[]>([
  { id: '1', name: 'Sprint 1', startDate: '2026-08-01', endDate: '2026-08-14', status: 'closed', taskCount: 20, doneCount: 18 },
  { id: '2', name: 'Sprint 2', startDate: '2026-08-15', endDate: '2026-08-28', status: 'active', taskCount: 15, doneCount: 8 },
  { id: '3', name: 'Sprint 3', startDate: '2026-08-29', endDate: '2026-09-11', status: 'planning', taskCount: 0, doneCount: 0 }
])

const STATUS_STYLE: Record<string, { label: string; color: string; bg: string }> = {
  planning: { label: '计划中', color: '#8F959E', bg: 'rgba(143,149,158,0.1)' },
  active: { label: '进行中', color: '#1677FF', bg: 'rgba(22,119,255,0.1)' },
  closed: { label: '已关闭', color: '#28A745', bg: 'rgba(40,167,69,0.1)' }
}

function onCreate() {
  ElMessage.info('新建迭代功能开发中')
}
</script>

<template>
  <div class="h-full overflow-auto p-6">
    <div class="mb-4 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <Repeat :size="20" class="text-brand" />
        <h1 class="text-lg font-semibold text-ink-900">迭代</h1>
      </div>
      <button class="flex items-center gap-1.5 rounded-md bg-brand px-3 py-1.5 text-sm font-medium text-white transition hover:bg-brand-dark" @click="onCreate">
        <Plus :size="14" /> 新建迭代
      </button>
    </div>

    <div class="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-3">
      <div v-for="it in iterations" :key="it.id" class="rounded-lg border border-[#E9ECEF] bg-white p-4 shadow-sm transition hover:shadow-md">
        <div class="flex items-center justify-between">
          <span class="text-base font-medium text-ink-900">{{ it.name }}</span>
          <span class="rounded px-2 py-0.5 text-xs" :style="{ color: STATUS_STYLE[it.status].color, backgroundColor: STATUS_STYLE[it.status].bg }">
            {{ STATUS_STYLE[it.status].label }}
          </span>
        </div>
        <div class="mt-2 flex items-center gap-2 text-xs text-ink-400">
          <Calendar :size="12" />
          <span>{{ it.startDate }} ~ {{ it.endDate }}</span>
        </div>
        <div class="mt-3">
          <div class="mb-1 flex items-center justify-between text-xs text-ink-400">
            <span>任务进度</span>
            <span>{{ it.doneCount }}/{{ it.taskCount }}</span>
          </div>
          <div class="h-1.5 overflow-hidden rounded-full bg-surface-muted">
            <div class="h-full rounded-full bg-brand" :style="{ width: (it.taskCount ? (it.doneCount / it.taskCount) * 100 : 0) + '%' }" />
          </div>
        </div>
        <div class="mt-3 flex justify-end">
          <button class="text-ink-400 transition hover:text-ink-900" @click="ElMessage.info('查看迭代详情功能开发中')">
            <MoreHorizontal :size="16" />
          </button>
        </div>
      </div>
    </div>
  </div>
</template>