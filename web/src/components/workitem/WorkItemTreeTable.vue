<script setup lang="ts">
/**
 * 工作项树形表格。
 *
 * <p>之前「树形」视图只渲染根节点（238 条数据只显示 3 条），递归组件 {@code TreeNode.vue}
 * 也没被用上。这里做成真正的树表：层级引导线 + 逐节点展开/收起 + 与列表视图一致的列。
 *
 * <p>视觉上刻意压低"噪音"：
 * · 编号/日期弱化并等宽右对齐，日期只显示 MM-DD；
 * · 优先级默认值 P2（中）不占列，只在非默认时于标题前显示一个色点；
 * · 未指派不渲染灰色头像圆点，只留一个淡横线；
 * · 根节点整行极浅底色，238 行摊平后靠它区分分组。
 */
import { computed, ref, watch } from 'vue'
import { ChevronRight, ChevronsDownUp, ChevronsUpDown, GitBranch } from 'lucide-vue-next'
import StatusPill from '@/components/common/StatusPill.vue'
import UserCell from '@/components/common/UserCell.vue'
import { fmtDateOrDash as fmtDate } from '@/utils/datetime'
import { PRIORITY_LABEL, PRIORITY_COLOR } from '@/constants/issueDict'
import type { IssueTreeNode } from '@/api/types'

const props = withDefaults(defineProps<{
  nodes: IssueTreeNode[]
  statusName: (id: string) => string
  statusColor: (id: string) => string
  memberName?: (id: string | null) => string
  noun?: string
  /** 已勾选的工作项 id（批量删除用，由父组件持有） */
  selected?: Set<string>
  /** 没有批量删除权限时传 false，勾选框不渲染 */
  selectable?: boolean
}>(), {
  noun: '工作项',
  selectable: false
})

const emit = defineEmits<{
  (e: 'open', id: string): void
  /** 勾选变化：parent 传 true 表示勾上 node 及其所有子孙（删除父任务必然连子任务一起删） */
  (e: 'select', payload: { ids: string[]; checked: boolean }): void
}>()

const columns = computed(() => {
  const base = [
    { key: 'title', label: '任务' },
    { key: 'key', label: '编号', width: '110px' },
    { key: 'assignee', label: '负责人', width: '136px' },
    { key: 'status', label: '状态', width: '96px' },
    { key: 'dueDate', label: '计划完成', width: '92px', align: 'right' as const },
    { key: 'updatedAt', label: '更新于', width: '84px', align: 'right' as const }
  ]
  return props.selectable ? [{ key: 'select', label: '', width: '38px' }, ...base] : base
})

/** 已展开的节点 id 集合 */
const expanded = ref<Set<string>>(new Set())

/** 默认展开根节点的直接子级，深一层收起 */
function defaultExpanded(nodes: IssueTreeNode[]): Set<string> {
  const set = new Set<string>()
  for (const n of nodes) {
    if (n.children?.length) set.add(n.id)
  }
  return set
}

watch(() => props.nodes, (nodes) => {
  expanded.value = defaultExpanded(nodes)
}, { immediate: true })

/** 按展开状态摊平成行，depth 决定缩进与引导线数量 */
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

function descendantCount(node: IssueTreeNode): number {
  return node.children?.length ? countAll(node.children) : 0
}

/** 优先级：默认值 P2（中）不显示，避免每行一个相同的标记 */
function showPriority(priority: string) {
  return !!priority && priority !== 'P2'
}

function priorityLabel(priority: string) {
  return (PRIORITY_LABEL as Record<string, string>)[priority] || priority
}

function priorityDotColor(priority: string) {
  return (PRIORITY_COLOR as Record<string, string>)[priority] || 'var(--ink-placeholder)'
}

/** 短日期：238 行同一天的完整日期是噪音，只留 MM-DD */
function shortDate(value: string | null | undefined) {
  const full = fmtDate(value)
  return full && full !== '—' ? full.slice(5) : '—'
}

function assigneeName(node: IssueTreeNode) {
  return props.memberName ? props.memberName(node.assigneeId) : (node.assigneeName || '')
}

/** 勾选：勾父节点会连带子孙（方便一次删一大片），但**可以**再单独取消某个子孙 */
function isChecked(id: string) { return props.selected?.has(id) ?? false }

function subtreeIds(node: IssueTreeNode): string[] {
  const out: string[] = []
  const walk = (n: IssueTreeNode) => {
    out.push(n.id)
    for (const c of n.children ?? []) walk(c)
  }
  walk(node)
  return out
}

/**
 * 子树勾选状态：全选 / 部分 / 未选。
 * 部分选中必须显示成 indeterminate——否则"父节点显示打勾、里面还有没勾的子任务"会误导用户。
 */
function subtreeState(node: IssueTreeNode): 'all' | 'some' | 'none' {
  const ids = subtreeIds(node)
  const picked = ids.filter((id) => isChecked(id)).length
  if (picked === 0) return 'none'
  return picked === ids.length ? 'all' : 'some'
}

function onToggle(node: IssueTreeNode) {
  // 全选 → 全部取消；未选或部分选中 → 补齐整个子树
  emit('select', { ids: subtreeIds(node), checked: subtreeState(node) !== 'all' })
}

/** 可见行全选/全不选（含已折叠节点，全部数据都在 nodes 里） */
const allVisibleSelected = computed(() => {
  if (!props.selectable || !rows.value.length) return false
  return rows.value.every((r) => isChecked(r.node.id))
})

function toggleSelectAll() {
  emit('select', { ids: allIds(props.nodes), checked: !allVisibleSelected.value })
}

function allIds(list: IssueTreeNode[]): string[] {
  const out: string[] = []
  const walk = (items: IssueTreeNode[]) => {
    for (const n of items) {
      out.push(n.id)
      if (n.children?.length) walk(n.children)
    }
  }
  walk(list)
  return out
}
</script>

<template>
  <div class="wi-card glass-panel">
    <div class="wi-card-head">
      <GitBranch :size="16" class="text-brand" />
      <span class="wi-card-title">全部{{ noun }}</span>
      <span class="count-pill">{{ totalCount }}</span>
      <!-- 批量操作由页面注入：勾选状态在页面里统一持有，组件不重复管一份 -->
      <slot name="head-actions" />
      <span class="ml-auto text-xs text-ink-secondary">当前显示 {{ visibleCount }} 条</span>
      <div class="flex items-center gap-0.5">
        <button
          class="flex items-center gap-1 rounded-md px-2 py-1 text-xs text-ink-500 transition hover:bg-surface-muted hover:text-brand disabled:cursor-not-allowed disabled:opacity-40"
          :disabled="allExpanded"
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

    <div class="wi-card-body wi-table">
      <div class="min-h-0 flex-1 overflow-auto">
        <table class="data-table">
        <thead class="data-thead">
          <tr>
            <th v-if="selectable" class="data-th wi-th-select" style="width: 38px">
              <input
                type="checkbox"
                class="wi-check"
                :checked="allVisibleSelected"
                title="全选/全不选"
                @click.stop="toggleSelectAll"
              />
            </th>
            <th
              v-for="(col, i) in columns.filter((c) => c.key !== 'select')"
              :key="col.key"
              class="data-th"
              :class="[i === 0 ? 'data-th-first' : '', col.align === 'right' ? 'text-right' : '']"
              :style="col.width ? { width: col.width } : undefined"
            >
              {{ col.label }}
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="{ node, depth } in rows"
            :key="node.id"
            class="data-tr cursor-pointer"
            :class="[depth === 0 ? 'wi-row-root' : '', isChecked(node.id) ? 'wi-row-selected' : '']"
            @click="emit('open', node.id)"
          >
            <td v-if="selectable" class="data-td wi-td-select" @click.stop>
              <input
                type="checkbox"
                class="wi-check"
                :checked="subtreeState(node) === 'all'"
                :indeterminate="subtreeState(node) === 'some'"
                @click.stop="onToggle(node)"
              />
            </td>
            <!-- 任务：整行贯通的层级引导线 + 展开箭头 + 优先级点 + 标题 + 子任务数 -->
            <td class="data-td data-td-first wi-tree-cell" :style="{ '--depth': depth }">
              <span v-if="depth > 0" class="wi-guides" />
              <div class="flex items-center gap-1" :style="{ paddingLeft: `${depth * 18}px` }">
                <button
                  v-if="node.children?.length"
                  class="wi-caret"
                  :title="isExpanded(node.id) ? '收起子任务' : '展开子任务'"
                  @click.stop="toggle(node)"
                >
                  <ChevronRight
                    :size="13"
                    :class="isExpanded(node.id) ? 'rotate-90 transition-transform' : 'transition-transform'"
                  />
                </button>
                <span v-else class="wi-caret-spacer" />
                <span
                  v-if="showPriority(node.priority)"
                  class="wi-prio"
                  :style="{ color: priorityDotColor(node.priority) }"
                  :title="`优先级 ${priorityLabel(node.priority)}`"
                >
                  <span class="wi-prio-dot" />
                </span>
                <span
                  class="min-w-0 truncate text-[13px]"
                  :class="depth === 0 ? 'wi-title-root' : 'wi-title-leaf'"
                >{{ node.title }}</span>
                <span v-if="node.children?.length" class="wi-count">{{ descendantCount(node) }}</span>
              </div>
            </td>
            <td class="data-td"><span class="wi-key">{{ node.issueKey }}</span></td>
            <td class="data-td">
              <UserCell
                v-if="node.assigneeId"
                :avatar-name="node.assigneeName"
                :avatar-url="node.assigneeAvatarUrl"
                :display-name="assigneeName(node)"
              />
              <span v-else class="wi-unassigned">—</span>
            </td>
            <td class="data-td">
              <StatusPill :label="statusName(node.statusId)" :color="statusColor(node.statusId)" />
            </td>
            <td class="data-td"><span class="wi-date">{{ shortDate(node.dueDate) }}</span></td>
            <td class="data-td"><span class="wi-date">{{ shortDate(node.updatedAt) }}</span></td>
          </tr>
        </tbody>
        </table>
        <div v-if="!rows.length" class="empty-row text-sm">暂无{{ noun }}</div>
      </div>
    </div>
  </div>
</template>
