<script setup lang="ts">
import { ref } from 'vue'
import { Plus, Target, Calendar, CheckCircle2 } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'

interface Milestone {
  id: string
  name: string
  dueDate: string
  progress: number
  status: 'open' | 'done' | 'overdue'
  taskCount: number
  doneCount: number
}

const milestones = ref<Milestone[]>([
  { id: '1', name: 'V1.0 发布', dueDate: '2026-09-30', progress: 75, status: 'open', taskCount: 40, doneCount: 30 },
  { id: '2', name: 'V1.1 迭代', dueDate: '2026-10-31', progress: 30, status: 'open', taskCount: 20, doneCount: 6 },
  { id: '3', name: 'V0.9 Beta', dueDate: '2026-08-15', progress: 100, status: 'done', taskCount: 30, doneCount: 30 }
])

const STATUS_STYLE: Record<string, { label: string; color: string; bg: string }> = {
  open: { label: '进行中', color: '#1677FF', bg: 'rgba(22,119,255,0.1)' },
  done: { label: '已完成', color: '#28A745', bg: 'rgba(40,167,69,0.1)' },
  overdue: { label: '已逾期', color: '#DC3545', bg: 'rgba(220,53,69,0.1)' }
}

function onCreate() {
  ElMessage.info('新建里程碑功能开发中')
}
</script>

<template>
  <div class="h-full overflow-auto p-6">
    <div class="mb-4 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <Target :size="20" class="text-brand" />
        <h1 class="text-lg font-semibold text-ink-900">里程碑</h1>
      </div>
      <button class="flex items-center gap-1.5 rounded-md bg-brand px-3 py-1.5 text-sm font-medium text-white transition hover:bg-brand-dark" @click="onCreate">
        <Plus :size="14" /> 新建里程碑
      </button>
    </div>

    <div class="space-y-4">
      <div v-for="m in milestones" :key="m.id" class="rounded-lg border border-[#E9ECEF] bg-white p-5 shadow-sm transition hover:shadow-md">
        <div class="flex items-start justify-between">
          <div class="flex items-center gap-3">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-brand/10 text-brand">
              <Target :size="20" />
            </div>
            <div>
              <div class="text-base font-medium text-ink-900">{{ m.name }}</div>
              <div class="mt-1 flex items-center gap-3 text-xs text-ink-400">
                <span class="flex items-center gap-1"><Calendar :size="12" /> 截止 {{ m.dueDate }}</span>
                <span class="flex items-center gap-1"><CheckCircle2 :size="12" /> {{ m.doneCount }}/{{ m.taskCount }} 任务</span>
              </div>
            </div>
          </div>
          <span class="rounded px-2 py-0.5 text-xs" :style="{ color: STATUS_STYLE[m.status].color, backgroundColor: STATUS_STYLE[m.status].bg }">
            {{ STATUS_STYLE[m.status].label }}
          </span>
        </div>
        <div class="mt-4">
          <div class="mb-1 flex items-center justify-between text-xs text-ink-400">
            <span>完成进度</span>
            <span>{{ m.progress }}%</span>
          </div>
          <div class="h-2 overflow-hidden rounded-full bg-surface-muted">
            <div class="h-full rounded-full bg-brand" :style="{ width: m.progress + '%' }" />
          </div>
        </div>
      </div>
    </div>
  </div>
</template>