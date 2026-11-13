<script setup lang="ts">
/**
 * 工作项树形表格。
 *
 * <p>之前「树形」视图只渲染根节点（238 条数据只显示 3 条），递归组件 {@code TreeNode.vue}
 * 也没被用上。这里做成真正的树表：按 parentId 组装的层级 + 逐节点展开/收起 + 缩进与连线，
 * 列结构与列表视图保持一致（任务/编号/负责人/状态/优先级/计划完成/更新于）。
 *
 * <p>默认只展开根节点（根 + 它的直接子级），避免 200+ 条一次铺满；工具栏可一键全展开/全收起。
 */
import { computed, ref, watch } from 'vue'
import { ChevronRight, ChevronsDownUp, ChevronsUpDown } from 'lucide-vue-next'
import DataTable, { type TableColumn } from '@/components/common/DataTable.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import PriorityPill from '@/components/common/PriorityPill.vue'
import UserCell from '@/components/common/UserCell.vue'
import { fmtDateOrDash as fmtDate } from '@/utils/datetime'
import type { IssueTreeNode } from '@/api/types'

const props = withDefaults(defineProps<{
  nodes: IssueTreeNode[]
  statusName: (id: string) => string
  statusColor: (id: string) => string
  memberName?: (id: string | null) => string
  noun?: string
}>(), {
  noun: '工作项'
})

const emit = defineEmits<{ (e: 'open', id: string): void }>()

const columns: TableColumn[] = [
  { key: 'title', label: '任务' },
  { key: 'key', label: '编号', width: '132px' },
  { key: 'assignee', label: '负责人', width: '150px' },
  { key: 'status', label: '状态', width: '110px' },
  { key: 'priority', label: '优先级', width: '92px' },
  { key: 'dueDate', label: '计划完成', width: '116px' },
  { key: 'updatedAt', label: '更新于', width: '116px' }
]

/** 已展开的节点 id 集合 */
const expanded = ref<Set<string>>(new Set())

/** 默认展开所有「有子节点」的根节点，深一层收起 */
function defaultExpanded(nodes: IssueTreeNode[]): Set<string> {
  const set = new Set<string>()
  const walkRoots = (list: IssueTreeNode[]) => {
    for (const n of list) {
      if (n.children?.length) set.add(n.id)
    }
  }
  walkRoots(nodes)
  return set
}

watch(() => props.nodes, (nodes) => {
  expanded.value = defaultExpanded(nodes)
}, { immediate: true })

/** 按展开状态摊平成行，depth 决定缩进层级 */
const rows = computed(() => {
  const out: { node: IssueTreeNode; depth: number }[] = []
  const walk = (list: IssueTreeNode[], depth: number) => {
    for (const node of list) {
      out.push({ node, depth })
      if (node.children?.length && expanded.value.has(node.id)) {
        walk(node.children, depth + 1)
      }
    }
  }
  walk(props.nodes, 0)
  return out
})

const totalCount = computed(() => countAll(props.nodes))
const visibleCount = computed(() => rows.value.length)
const allExpanded = computed(() => {
  const parents = collectParents(props.nodes)
  return parents.length > 0 && parents.every((id) => expanded.value.has(id))
})

function countAll(list: IssueTreeNode[]): number {
  let n = 0
  for (const node of list) {
    n += 1 + (node.children?.length ? countAll(node.children) : 0)
  }
  return n
}

function collectParents(list: IssueTreeNode[]): string[] {
  const out: string[] = []
  const walk = (items: IssueTreeNode[]) => {
    for (const node of items) {
      if (node.children?.length) {
        out.push(node.id)
        walk(node.children)
      }
    }
  }
  walk(list)
  return out
}

function isExpanded(id: string) { return expanded.value.has(id) }

function toggle(node: IssueTreeNode) {
  const next = new Set(expanded.value)
  if (next.has(node.id)) next.delete(node.id)
  else next.add(node.id)
  expanded.value = next
}

function expandAll() {
  expanded.value = new Set(collectParents(props.nodes))
}

function collapseAll() {
  expanded.value = new Set()
}

/** 子孙总数（用于父节点右侧的数量徽标） */
function descendantCount(node: IssueTreeNode): number {
  return node.children?.length ? countAll(node.children) : 0
}
</script>

<template>
  <div class="flex h-full min-h-0 flex-col">
    <div class="mb-2 flex items-center justify-between px-1">
      <div class="flex items-center gap-2 text-xs text-ink-400">
        <span>共 {{ totalCount }} 条</span>
        <span class="text-ink-placeholder">·</span>
        <span>当前显示 {{ visibleCount }} 条</span>
      </div>
      <div class="flex items-center gap-1">
        <button
          class="flex items-center gap-1 rounded-md px-2 py-1 text-xs text-ink-500 transition hover:bg-surface-muted hover:text-brand"
          :disabled="allExpanded"
          :class="allExpanded ? 'cursor-not-allowed opacity-50' : ''"
          @click="expandAll"
        >
          <ChevronsUpDown :size="13" />
          展开全部
        </button>
        <button
          class="flex items-center gap-1 rounded-md px-2 py-1 text-xs text-ink-500 transition hover:bg-surface-muted hover:text-brand"
          @click="collapseAll"
        >
          <ChevronsDownUp :size="13" />
          收起全部
        </button>
      </div>
    </div>

    <div class="min-h-0 flex-1 overflow-auto">
      <DataTable :columns="columns">
        <tr
          v-for="{ node, depth } in rows"
          :key="node.id"
          class="data-tr cursor-pointer"
          @click="emit('open', node.id)"
        >
          <!-- 任务：缩进 + 展开箭头 + 标题 -->
          <td class="data-td data-td-first">
            <div class="flex items-center gap-1.5" :style="{ paddingLeft: `${depth * 18}px` }">
              <button
                v-if="node.children?.length"
                class="flex size-4 shrink-0 items-center justify-center rounded text-ink-400 transition hover:bg-surface-muted hover:text-brand"
                :title="isExpanded(node.id) ? '收起子任务' : '展开子任务'"
                @click.stop="toggle(node)"
              >
                <ChevronRight :size="14" :class="isExpanded(node.id) ? 'rotate-90 transition-transform' : 'transition-transform'" />
              </button>
              <span v-else class="size-4 shrink-0" />
              <span class="cell-title truncate" :class="depth === 0 ? 'font-medium text-ink' : 'text-ink-700'">{{ node.title }}</span>
              <span
                v-if="node.children?.length"
                class="shrink-0 rounded-full bg-surface-muted px-1.5 py-0.5 text-[11px] text-ink-400"
              >{{ descendantCount(node) }}</span>
            </div>
          </td>
          <td class="data-td cell-key">{{ node.issueKey }}</td>
          <td class="data-td">
            <UserCell
              :avatar-name="node.assigneeName"
              :avatar-url="node.assigneeAvatarUrl"
              :display-name="memberName ? memberName(node.assigneeId) : (node.assigneeName || '—')"
            />
          </td>
          <td class="data-td">
            <StatusPill :label="statusName(node.statusId)" :color="statusColor(node.statusId)" />
          </td>
          <td class="data-td">
            <PriorityPill :priority="node.priority" />
          </td>
          <td class="data-td cell-muted">{{ fmtDate(node.dueDate) }}</td>
          <td class="data-td cell-muted">{{ fmtDate(node.updatedAt) }}</td>
        </tr>
      </DataTable>
      <div v-if="!rows.length" class="empty-row text-sm">暂无{{ noun }}</div>
    </div>
  </div>
</template>
