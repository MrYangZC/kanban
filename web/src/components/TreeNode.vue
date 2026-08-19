<script setup lang="ts">
import { AlertTriangle } from 'lucide-vue-next'
import { PRIORITY_LABEL } from '@/constants/issueDict'

defineOptions({ name: 'TreeNode' })

defineProps<{
  node: any
  statusName: (id: string) => string
  statusColor: (id: string) => string
  priorityColor: Record<string, string>
}>()

const emit = defineEmits<{ (e: 'open', issue: any): void }>()
</script>

<template>
  <div class="mb-1">
    <div
      class="flex cursor-pointer items-center gap-2 rounded-lg px-3 py-2 text-sm hover:bg-[#f5f7fa]"
      @click="emit('open', node)"
    >
      <AlertTriangle :size="14" class="shrink-0" style="color:#F56C6C" />
      <span class="font-mono text-xs" style="color:#c0c4cc">{{ node.issueKey }}</span>
      <span class="flex-1 truncate" style="color:#303133">{{ node.title }}</span>
      <span
        class="rounded px-1.5 py-0.5 text-xs text-white"
        :style="{ background: statusColor(node.statusId) }"
      >{{ statusName(node.statusId) }}</span>
      <span
        class="rounded px-1.5 py-0.5 text-xs font-medium text-white"
        :style="{ background: priorityColor[node.priority] || '#909399' }"
      >{{ PRIORITY_LABEL[node.priority] || node.priority }}</span>
    </div>
    <div v-if="node.children?.length" class="ml-6 border-l pl-2" style="border-color:#e4e7ed">
      <TreeNode
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :status-name="statusName"
        :status-color="statusColor"
        :priority-color="priorityColor"
        @open="emit('open', $event)"
      />
    </div>
  </div>
</template>
