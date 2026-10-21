<script setup lang="ts">
import { AlertTriangle } from 'lucide-vue-next'
import StatusPill from '@/components/common/StatusPill.vue'
import PriorityPill from '@/components/common/PriorityPill.vue'
import type { IssueTreeNode } from '@/api/types'

defineOptions({ name: 'TreeNode' })

defineProps<{
  node: IssueTreeNode
  statusName: (id: string) => string
  statusColor: (id: string) => string
}>()

const emit = defineEmits<{ (e: 'open', issue: IssueTreeNode): void }>()
</script>

<template>
  <div>
    <div class="tree-node" @click="emit('open', node)">
      <AlertTriangle :size="14" class="shrink-0 text-danger" />
      <span class="cell-key">{{ node.issueKey }}</span>
      <span class="flex-1 truncate text-ink">{{ node.title }}</span>
      <StatusPill :label="statusName(node.statusId)" :color="statusColor(node.statusId)" />
      <PriorityPill :priority="node.priority" />
    </div>
    <div v-if="node.children?.length" class="ml-6 border-l border-line-light pl-2">
      <TreeNode
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :status-name="statusName"
        :status-color="statusColor"
        @open="emit('open', $event)"
      />
    </div>
  </div>
</template>
