<script setup lang="ts">
/**
 * 工作项树形表格（el-table 树形数据版）。
 *
 * <p>表格渲染交给 el-table（树形缩进、展开箭头、sticky 表头、列宽拖拽、横向滚动），
 * 只保留两件"必须自己实现"的事：
 * <ol>
 *   <li><b>勾选语义</b>：勾父节点连带子孙、可再单独取消某个子孙、父节点显示半选。
 *       el-table 的 selection 列不支持父行 half-check，也不做父子联动，因此用自定义勾选列；</li>
 *   <li><b>与业务无关的排版</b>：优先级色点、子任务数量徽标、根节点浅底。</li>
 * </ol>
 *
 * <p>层级引导竖线由 style.css 在首列 td 上按 wi-depth-N 绘制（行上带层级类），
 * 因此迁移到 el-table 后仍保留了"这行属于谁"的可视引导。
 */
import { computed, nextTick, ref, watch } from 'vue'
import { ChevronsDownUp, ChevronsUpDown, GitBranch } from 'lucide-vue-next'
import StatusPill from '@/components/common/StatusPill.vue'
import UserCell from '@/components/common/UserCell.vue'
import { fmtDateOrDash as fmtDate } from '@/utils/datetime'
import { PRIORITY_LABEL, PRIORITY_COLOR } from '@/constants/issueDict'
import { useViewportWidth } from '@/composables/useViewportWidth'
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
  /** 勾选变化：parent 传 true 表示勾上 node 及其所有子孙 */
  (e: 'select', payload: { ids: string[]; checked: boolean }): void
}>()

const { hideMdColumns, hideSmColumns } = useViewportWidth()

const tableRef = ref<{ toggleRowExpansion: (row: IssueTreeNode, expanded?: boolean) => void } | null>(null)

const totalCount = computed(() => countAll(props.nodes))
/** 当前展开后可见行数（由 el-table 的 expand-change 维护） */
const visibleCount = ref(0)
const expandedIds = ref<Set<string>>(new Set())

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

function subtreeIds(node: IssueTreeNode): string[] {
  const out: string[] = []
  const walk = (n: IssueTreeNode) => {
    out.push(n.id)
    for (const c of n.children ?? []) walk(c)
  }
  walk(node)
  return out
}

function allIds(list: IssueTreeNode[]): string[] {
  return subtreeIds({ id: '', children: list } as unknown as IssueTreeNode)
}

function isChecked(id: string) {
  return props.selected?.has(id) ?? false
}

/** 子树勾选状态：全选 / 部分 / 未选 —— 部分选中必须显示为半选，否则会误导用户 */
function subtreeState(raw: unknown): 'all' | 'some' | 'none' {
  const node = raw as IssueTreeNode
  const ids = subtreeIds(node)
  const picked = ids.filter((id) => isChecked(id)).length
  if (picked === 0) return 'none'
  return picked === ids.length ? 'all' : 'some'
}

function onToggle(raw: unknown) {
  const node = raw as IssueTreeNode
  emit('select', { ids: subtreeIds(node), checked: subtreeState(node) !== 'all' })
}

const allSelected = computed(() => {
  const ids = allIds(props.nodes)
  return ids.length > 0 && ids.every((id) => isChecked(id))
})

const someSelected = computed(() => {
  const ids = allIds(props.nodes)
  const picked = ids.filter((id) => isChecked(id)).length
  return picked > 0 && picked < ids.length
})

function toggleSelectAll() {
  emit('select', { ids: allIds(props.nodes), checked: !allSelected.value })
}

// ---- 展开/收起 ----

/** 默认只展开根节点（238 条一次铺满没有可读性） */
function expandRoots() {
  const set = new Set<string>()
  for (const node of props.nodes) {
    if (node.children?.length) set.add(node.id)
  }
  expandedIds.value = set
  nextTick(() => {
    for (const node of props.nodes) {
      if (node.children?.length) tableRef.value?.toggleRowExpansion(node, true)
    }
    recountVisible()
  })
}

watch(() => props.nodes, () => {
  expandRoots()
}, { immediate: true })

/**
 * 展开变化。el-table 对树形表格的 expand-change 有两种重载（布尔 or 展开行数组），
 * 参数用 unknown 承接再收窄，避免模板里写类型断言。
 */
function onExpandChange(rawRow: unknown, arg: unknown) {
  const row = rawRow as IssueTreeNode
  const expanded = typeof arg === 'boolean' ? arg : !expandedIds.value.has(row.id)
  const next = new Set(expandedIds.value)
  if (expanded) next.add(row.id)
  else next.delete(row.id)
  expandedIds.value = next
  recountVisible()
}

/** 可见行 = 所有未被折叠节点的行；折叠父节点的子孙都不计入 */
function recountVisible() {
  let n = 0
  const walk = (list: IssueTreeNode[]) => {
    for (const node of list) {
      n++
      if (node.children?.length && expandedIds.value.has(node.id)) walk(node.children)
    }
  }
  walk(props.nodes)
  visibleCount.value = n
}

const allExpanded = computed(() => {
  const parents = collectParents(props.nodes)
  return parents.length > 0 && parents.every((id) => expandedIds.value.has(id))
})

function expandAll() {
  expandedIds.value = new Set(collectParents(props.nodes))
  nextTick(() => {
    const walk = (list: IssueTreeNode[]) => {
      for (const node of list) {
        if (node.children?.length) {
          tableRef.value?.toggleRowExpansion(node, true)
          walk(node.children)
        }
      }
    }
    walk(props.nodes)
    recountVisible()
  })
}

function collapseAll() {
  expandedIds.value = new Set()
  nextTick(() => {
    const walk = (list: IssueTreeNode[]) => {
      for (const node of list) {
        if (node.children?.length) {
          tableRef.value?.toggleRowExpansion(node, false)
          walk(node.children)
        }
      }
    }
    walk(props.nodes)
    recountVisible()
  })
}

// ---- 单元格辅助 ----

function descendantCount(raw: unknown): number {
  const node = raw as IssueTreeNode
  return node.children?.length ? countAll(node.children) : 0
}

function showPriority(priority: string) {
  return !!priority && priority !== 'P2'
}

function priorityLabel(priority: string) {
  return (PRIORITY_LABEL as Record<string, string>)[priority] || priority
}

function priorityDotColor(priority: string) {
  return (PRIORITY_COLOR as Record<string, string>)[priority] || 'var(--ink-placeholder)'
}

function shortDate(value: string | null | undefined) {
  const full = fmtDate(value)
  return full && full !== '—' ? full.slice(5) : '—'
}

function assigneeName(raw: unknown) {
  const node = raw as IssueTreeNode
  return props.memberName ? props.memberName(node.assigneeId) : (node.assigneeName || '')
}

/**
 * 每个节点在树中的层级。
 * el-table 的树形只做缩进、不画层级引导线，而 238 条摊平后很难看出「这行属于谁」，
 * 所以在行上带一个 wi-depth-N 类，由 style.css 在首列画竖线（深度 1-8 级全覆盖）。
 */
const depthMap = computed(() => {
  const map = new Map<string, number>()
  const walk = (list: IssueTreeNode[], depth: number) => {
    for (const node of list) {
      map.set(node.id, depth)
      if (node.children?.length) walk(node.children, depth + 1)
    }
  }
  walk(props.nodes, 0)
  return map
})

/** 行样式：根节点浅底 + 选中态（el-table 不提供选中底色）+ 层级（用于画引导线） */
function rowClass({ row }: { row: IssueTreeNode }) {
  const classes: string[] = []
  if (!row.parentId) classes.push('wi-row-root')
  if (isChecked(row.id)) classes.push('wi-row-selected')
  const depth = depthMap.value.get(row.id)
  if (depth) classes.push(`wi-depth-${depth}`)
  return classes.join(' ')
}
</script>

<template>
  <div class="wi-card glass-panel">
    <div class="wi-card-head">
      <GitBranch :size="16" class="text-brand" />
      <span class="wi-card-title">全部{{ noun }}</span>
      <span class="count-pill">{{ totalCount }}</span>
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
      <el-table
        ref="tableRef"
        :data="nodes"
        row-key="id"
        :tree-props="{ children: 'children' }"
        height="100%"
        class="wi-el-table"
        :row-class-name="rowClass"
        @row-click="(row: unknown) => emit('open', (row as IssueTreeNode).id)"
        @expand-change="onExpandChange"
      >
        <el-table-column min-width="200">
          <template #header>
            <div class="flex items-center gap-2">
              <input
                v-if="selectable"
                type="checkbox"
                class="wi-check"
                :checked="allSelected"
                :indeterminate="someSelected"
                title="全选/全不选"
                @click.stop="toggleSelectAll"
              />
              <span>任务</span>
            </div>
          </template>
          <template #default="{ row }">
            <div class="flex items-center gap-1.5">
              <input
                v-if="selectable"
                type="checkbox"
                class="wi-check"
                :checked="subtreeState(row) === 'all'"
                :indeterminate="subtreeState(row) === 'some'"
                @click.stop="onToggle(row)"
              />
              <span
                v-if="showPriority(row.priority)"
                class="wi-prio"
                :style="{ color: priorityDotColor(row.priority) }"
                :title="`优先级 ${priorityLabel(row.priority)}`"
              >
                <span class="wi-prio-dot" />
              </span>
              <span class="min-w-0 truncate text-[13px]" :class="row.parentId ? 'wi-title-leaf' : 'wi-title-root'">
                {{ row.title }}
              </span>
              <span v-if="row.children?.length" class="wi-count">{{ descendantCount(row) }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="编号" width="110">
          <template #default="{ row }"><span class="wi-key">{{ row.issueKey }}</span></template>
        </el-table-column>
        <el-table-column v-if="!hideSmColumns" label="负责人" width="136">
          <template #default="{ row }">
            <UserCell
              v-if="row.assigneeId"
              :avatar-name="row.assigneeName"
              :avatar-url="row.assigneeAvatarUrl"
              :display-name="assigneeName(row)"
            />
            <span v-else class="wi-unassigned">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <StatusPill :label="statusName(row.statusId)" :color="statusColor(row.statusId)" />
          </template>
        </el-table-column>
        <el-table-column v-if="!hideMdColumns" label="计划完成" width="100" align="right">
          <template #default="{ row }"><span class="wi-date">{{ shortDate(row.dueDate) }}</span></template>
        </el-table-column>
        <el-table-column v-if="!hideMdColumns" label="更新于" width="92" align="right">
          <template #default="{ row }"><span class="wi-date">{{ shortDate(row.updatedAt) }}</span></template>
        </el-table-column>
        <template #empty><span class="text-sm text-ink-secondary">暂无{{ noun }}</span></template>
      </el-table>
    </div>
  </div>
</template>
