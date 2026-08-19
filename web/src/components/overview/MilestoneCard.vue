<script setup lang="ts">
import { Target, Plus, Calendar } from 'lucide-vue-next'
import type { MilestoneVO } from '@/api/types'
import ProgressBar from '@/components/ProgressBar.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import type { StatusStyle } from '@/components/StatusBadge.vue'

interface Props {
  milestones?: MilestoneVO[]
}

const props = withDefaults(defineProps<Props>(), {
  milestones: () => []
})

const STATUS_STYLE: Record<string, StatusStyle> = {
  OPEN: { label: '进行中', color: '#1677FF', bg: 'rgba(22,119,255,0.1)' },
  DONE: { label: '已完成', color: '#28A745', bg: 'rgba(40,167,69,0.1)' },
  OVERDUE: { label: '已逾期', color: '#DC3545', bg: 'rgba(220,53,69,0.1)' }
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
            <StatusBadge :status="m.status" :dict="STATUS_STYLE" />
          </div>
          <div class="mt-2 flex items-center gap-3">
            <ProgressBar class="flex-1" :value="m.progress" :max="100" height="sm" />
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
