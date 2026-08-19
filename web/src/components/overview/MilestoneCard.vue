<script setup lang="ts">
import { Target, Plus, Calendar } from 'lucide-vue-next'
import type { MilestoneVO } from '@/api/types'

interface Props {
  milestones?: MilestoneVO[]
}

const props = withDefaults(defineProps<Props>(), {
  milestones: () => []
})

const STATUS_STYLE: Record<string, { label: string; color: string; bg: string }> = {
  OPEN: { label: '进行中', color: '#1677FF', bg: 'rgba(22,119,255,0.1)' },
  DONE: { label: '已完成', color: '#28A745', bg: 'rgba(40,167,69,0.1)' },
  OVERDUE: { label: '已逾期', color: '#DC3545', bg: 'rgba(220,53,69,0.1)' }
}

function statusStyle(status: string) {
  return STATUS_STYLE[status] || STATUS_STYLE.OPEN
}

function onAdd() {
  // emit or callback
}
</script>

<template>
  <div class="rounded-lg border border-[#E9ECEF] bg-white shadow-sm">
    <div class="flex items-center justify-between border-b border-[#E9ECEF] px-4 py-3">
      <div class="flex items-center gap-2 text-sm font-medium text-ink-900">
        <Target :size="16" class="text-brand" /> 里程碑
      </div>
      <button class="flex items-center gap-1 text-xs text-brand transition hover:opacity-80" @click="onAdd">
        <Plus :size="12" /> 新建
      </button>
    </div>
    <div class="p-4">
      <div v-if="!props.milestones.length" class="py-6 text-center text-sm text-ink-400">暂无里程碑</div>
      <div v-else class="space-y-3">
        <div v-for="m in props.milestones" :key="m.id" class="rounded-md border border-[#E9ECEF] p-3 transition hover:bg-surface-muted/50">
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium text-ink-900">{{ m.name }}</span>
            <span
              class="rounded px-2 py-0.5 text-xs"
              :style="{ color: statusStyle(m.status).color, backgroundColor: statusStyle(m.status).bg }"
            >{{ statusStyle(m.status).label }}</span>
          </div>
          <div class="mt-2 flex items-center gap-3">
            <div class="h-1.5 flex-1 overflow-hidden rounded-full bg-surface-muted">
              <div class="h-full rounded-full bg-brand" :style="{ width: m.progress + '%' }" />
            </div>
            <span class="text-xs text-ink-400">{{ m.progress }}%</span>
            <span v-if="m.dueDate" class="flex items-center gap-1 text-xs text-ink-400">
              <Calendar :size="11" /> {{ m.dueDate }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>