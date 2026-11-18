<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  filterIssues, listProjectStatuses, listProjectMembers,
  getBoardGrouped, listTransitions, exportIssuesUrl, batchDeleteIssues
} from '@/api'
import type { Issue, IssueTreeNode, IssueStatusVO, BoardGroupView, GroupColumnView, WorkflowTransitionVO, WorkItemCategory } from '@/api/types'
import type { LucideIcon } from 'lucide-vue-next'
import { List, GitBranch, LayoutGrid, User, UserCheck, Inbox, Archive, Trash2 } from 'lucide-vue-next'
import draggable from 'vuedraggable'
import { useBoardDrag } from '@/composables/useBoardDrag'
import SimpleListView from '@/components/SimpleListView.vue'
import TableSkeleton from '@/components/common/TableSkeleton.vue'
import TableEmpty from '@/components/common/TableEmpty.vue'
import CreateIssueDialog from '@/components/CreateIssueDialog.vue'
import IssueDetailDrawer from '@/components/IssueDetailDrawer.vue'
import { useWorkflowTransition } from '@/composables/useWorkflowTransition'
import { useProjectPermission } from '@/composables/useProjectPermission'
import { useScopeCounts } from '@/composables/useScopeCounts'
import { useIssueFilterParams } from '@/composables/useIssueFilterParams'
import { PRIORITY_COLOR, priorityColor as priorityColorOf, priorityLabel as priorityLabelOf } from '@/constants/issueDict'
import WorkItemTreeTable from '@/components/workitem/WorkItemTreeTable.vue'
import WorkItemStatsBar, { type WorkItemStat } from '@/components/workitem/WorkItemStatsBar.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import PriorityPill from '@/components/common/PriorityPill.vue'
import UserCell from '@/components/common/UserCell.vue'
import type { ViewTab } from '@/components/ViewTabs.vue'
import { fmtDateOrDash as fmtDate } from '@/utils/datetime'
import { useIssueFilterQuery } from '@/composables/useIssueFilterQuery'
import { useViewportWidth } from '@/composables/useViewportWidth'
import { ElMessage } from 'element-plus'
import { confirmAction } from '@/utils/confirm'
import { reportError } from '@/utils/error'

/**
 * 需求/任务列表页通用实现：两类页面结构完全一致，仅业务参数不同。
 * 通过 props 注入差异（类型码、图标、文案、看板类别、新建默认类型、视图持久化 key）。
 */
const props = defineProps<{
  /** 页面标题与空文案名词，如 "需求" / "任务" */
  noun: string
  /** 列表/树形/导航处展示的图标 */
  icon: LucideIcon
  /** 工作项类型码集合（REQUIREMENT_TYPE_CODES / TASK_TYPE_CODES） */
  typeCodes: string[]
  /** 看板分组时的工作项大类（REQUIREMENT / TASK），同时决定取哪套工作流状态 */
  boardCategory: WorkItemCategory
  /** 新建对话框默认类型码（STORY / TASK） */
  defaultIssueTypeCode: string
  /** 视图模式持久化 localStorage key（kanban:<storageKey>:<projectId>） */
  storageKey: string
}>()

const route = useRoute()
const projectId = computed(() => route.params.projectId as string)

type ViewMode = 'list' | 'tree' | 'board'

const loading = ref(false)
const keyword = ref('')
const view = ref<ViewMode>('list')
const scope = ref('')
const page = ref(1)
const size = ref(25)
const total = ref(0)

const statuses = ref<IssueStatusVO[]>([])
const members = ref<{ userId: string; displayName: string }[]>([])
const tableData = ref<Issue[]>([])
const allIssues = ref<Issue[]>([])
const board = ref<BoardGroupView | null>(null)
const filteredIds = ref<Set<string>>(new Set())
/** 拖拽悬停的列及其是否允许落入，用于列头/列体高亮 */
const createOpen = ref(false)
const detailDrawer = ref<InstanceType<typeof IssueDetailDrawer> | null>(null)

const { transitionMap, build: buildTransitionMap } = useWorkflowTransition()
// 只读访问者（VIEWER）不应看到「新建」入口：后端的写权限校验拦得住请求，但入口必须同步隐藏
const { canWrite, hasPermission, refresh: refreshPermission } = useProjectPermission(() => projectId.value)
/** 导出按钮与后端 issue:export 权限码对齐（此前该权限码从未被任何功能使用） */
const canExport = computed(() => hasPermission('issue:export'))
/** 批量删除入口的可见性：与后端 issue:delete 同一权限码 */
const canDelete = computed(() => hasPermission('issue:delete'))

function onExport() {
  window.open(exportIssuesUrl(projectId.value, {
    types: props.typeCodes,
    scope: scope.value || undefined,
    keyword: keyword.value.trim() || undefined
  }), '_blank')
}

// 左侧各范围的条数：与视图无关，切到看板/树形后仍取自 filter 的 total
const { counts: scopeCounts, refresh: refreshScopeCounts } = useScopeCounts(
  () => projectId.value,
  () => props.typeCodes
)
const scopeCount = (key: string) => scopeCounts.value[key] ?? 0

const navGroups = computed(() => [
  {
    title: '个人',
    items: [
      { key: 'REPORTED', label: '我创建的', icon: User, count: scopeCount('REPORTED') },
      { key: 'ASSIGNED', label: '我负责的', icon: UserCheck, count: scopeCount('ASSIGNED') },
      { key: 'VERIFIED', label: '待我验证', icon: Inbox, count: scopeCount('VERIFIED') }
    ]
  },
  {
    title: '公共',
    items: [
      { key: '', label: `全部${props.noun}`, icon: props.icon, count: scopeCounts.value[''] ?? total.value },
      { key: 'ARCHIVED', label: '已归档', icon: Archive, count: scopeCount('ARCHIVED') }
    ]
  }
])

const viewTabs: ViewTab[] = [
  { key: 'list', label: '列表', icon: List },
  { key: 'tree', label: '树形', icon: GitBranch },
  { key: 'board', label: '看板', icon: LayoutGrid }
]


// 窄屏列降级：从 DOM 移除列，避免 el-table 的 colgroup 留下空白占位
const { hideMdColumns, hideSmColumns } = useViewportWidth()

// ---- 批量删除：勾选状态由页面持有，列表与树形共用 ----
const selectedIds = ref<Set<string>>(new Set())
const selectedCount = computed(() => selectedIds.value.size)
const deleting = ref(false)

function isSelected(id: string) {
  return selectedIds.value.has(id)
}

function setSelected(ids: string[], checked: boolean) {
  const next = new Set(selectedIds.value)
  for (const id of ids) {
    if (checked) next.add(id)
    else next.delete(id)
  }
  selectedIds.value = next
}

/** el-table 实例：清空选择要调用它的 clearSelection */
const listTableRef = ref<{ clearSelection: () => void } | null>(null)

/** 列表行样式：el-table 不提供选中行底色，这里按勾选集合补上 */
function listRowClass({ row }: { row: Issue }) {
  return isSelected(row.id) ? 'wi-row-selected' : ''
}

/**
 * el-table 的 selection 变化 → 同步到页面持有的勾选集合。
 * 列表页用它内置的 selection 列（表头自带全选/半选），树形页仍用自定义勾选列
 * 以保留"父节点半选"和父子联动。
 */
function onSelectionChange(rows: Issue[]) {
  selectedIds.value = new Set(rows.map((r) => r.id))
}

function clearSelection() {
  selectedIds.value = new Set()
  listTableRef.value?.clearSelection()
}

/** 树形勾选：父节点会连带其全部子孙一起勾上 */
function onTreeSelect({ ids, checked }: { ids: string[]; checked: boolean }) {
  setSelected(ids, checked)
}

/** 批量删除：只删勾选的这些，未勾选的子任务会被上提到上一级 */
async function onBatchDelete() {
  const ids = [...selectedIds.value]
  if (!ids.length || deleting.value) return
  const keptChildren = countKeptDescendants(ids)
  const ok = await confirmAction(
    `将删除选中的 ${ids.length} 个工作项。` +
      (keptChildren > 0
        ? `其中 ${keptChildren} 个子任务未被勾选，会保留并上提到其父级的上一级。`
        : '') +
      '评论与附件会随工作项一并删除，且不可恢复。',
    '批量删除',
    '删除'
  )
  if (!ok) return
  deleting.value = true
  try {
    const res = await batchDeleteIssues(projectId.value, ids)
    const parts = [`已删除 ${res.deleted} 个`]
    if (res.promoted) parts.push(`${res.promoted} 个子任务已上提到上一级`)
    if (res.skipped) parts.push(`跳过 ${res.skipped} 个（不属于当前项目或已被删除）`)
    if (res.failed) parts.push(`失败 ${res.failed} 个`)
    if (res.failed) ElMessage.warning(parts.join('，'))
    else ElMessage.success(parts.join('，'))
    clearSelection()
    reloadAll()
  } catch (e: unknown) {
    reportError(e, '批量删除失败')
  } finally {
    deleting.value = false
  }
}

/**
 * 统计"父节点被删、但自身未被勾选"的子任务数量。
 *
 * 后端会把这些子任务上提到上一级，而不是删掉；确认框里先把这个后果说清楚，
 * 避免用户以为取消勾选就能让子任务原地不动（它保住了，但会换父级）。
 * 数据不够时（列表分页只加载当前页）返回 0，退化为后端返回的 promoted 数字。
 */
function countKeptDescendants(ids: string[]): number {
  const parents = new Map<string, string | null>()
  for (const i of allIssues.value) parents.set(i.id, i.parentId ?? null)
  if (!parents.size) return 0
  const selected = new Set(ids)
  let kept = 0
  for (const i of allIssues.value) {
    if (selected.has(i.id)) continue
    // 自身未被选中，但某个祖先在删除集合里 → 会被上提
    let cursor = i.parentId ?? null
    let guard = 0
    while (cursor && guard++ < 200) {
      if (selected.has(cursor)) {
        kept++
        break
      }
      cursor = parents.get(cursor) ?? null
    }
  }
  return kept
}

/** 统计条的数据源：树形视图用已全量加载的 allIssues，列表视图单独拉一次全量（分页只有当页） */
const statsIssues = ref<Issue[]>([])

async function loadStats() {
  try {
    const res = await filterIssues(filterParams({ page: 1, size: 1000 }))
    statsIssues.value = res.list
  } catch {
    statsIssues.value = []
  }
}

/** 统计条：标签 + 彩色数字 + 图标，语义取自状态字典的 category */
const statItems = computed<WorkItemStat[]>(() => {
  const source = view.value === 'tree' ? allIssues.value : statsIssues.value
  const byCategory: Record<string, number> = {}
  const categoryOf = (statusId: string) =>
    statuses.value.find((s) => s.id === statusId)?.category || 'TODO'
  for (const i of source) {
    const c = categoryOf(i.statusId)
    byCategory[c] = (byCategory[c] || 0) + 1
  }
  return [
    { key: 'total', label: `全部${props.noun}`, value: source.length, color: 'var(--primary)' },
    { key: 'todo', label: '待办', value: byCategory.TODO || 0, color: 'var(--ink-secondary)' },
    { key: 'inProgress', label: '进行中', value: byCategory.IN_PROGRESS || 0, color: '#e6a23c' },
    { key: 'done', label: '已完成', value: byCategory.DONE || 0, color: 'var(--success)' }
  ]
})

/** 优先级文案：默认值 P2（中）在列表里不显示，避免整列同色块 */
function priorityText(priority: string) {
  return priorityLabelOf(priority)
}

/** 短日期：列表里 238 行同一天的完整日期是噪音 */
function shortDate(value: string | null | undefined) {
  const full = fmtDate(value)
  return full && full !== '—' ? full.slice(5) : '—'
}

const statusNameMap = computed(() => {
  const m: Record<string, string> = {}
  for (const g of board.value?.groups ?? []) {
    for (const c of g.columns) m[c.statusId] = c.name
  }
  for (const s of statuses.value) if (!m[s.id]) m[s.id] = s.name
  return m
})

const treeData = computed<IssueTreeNode[]>(() => {
  const map = new Map<string, IssueTreeNode>()
  const roots: IssueTreeNode[] = []
  for (const i of allIssues.value) map.set(i.id, { ...i, children: [] })
  for (const i of allIssues.value) {
    const node = map.get(i.id)!
    if (i.parentId && map.has(i.parentId)) map.get(i.parentId)!.children.push(node)
    else roots.push(node)
  }
  return roots
})

const renderGroups = computed(() => {
  if (!board.value) return []
  return board.value.groups.map((g) => {
    const columns = g.columns.map((c: GroupColumnView) => ({
      ...c,
      issues: c.issues.filter((i) => filteredIds.value.has(i.id))
    }))
    // 分组头计数用过滤后的实际卡片数，否则会显示未套用范围/关键字的全量值
    return { ...g, columns, issueCount: columns.reduce((n, c) => n + c.issues.length, 0) }
  })
})

function statusName(id: string) { return statusNameMap.value[id] || '—' }
function statusColor(id: string) { return statuses.value.find((s) => s.id === id)?.color || 'var(--primary)' }
function memberName(id: string | null) { return members.value.find((m) => m.userId === id)?.displayName || '—' }

async function loadMeta() {
  // 状态/成员为筛选辅助数据，失败降级为空（接口层已统一提示错误）
  try { statuses.value = await listProjectStatuses(projectId.value, props.boardCategory) } catch { /* 状态字典降级为空 */ }
  try { members.value = await listProjectMembers(projectId.value) } catch { /* 成员筛选降级为空 */ }
}

// 度量页点统计数字跳过来时带的筛选（?statusCategory / ?overdue），同步逻辑见 composable
const { statusCategoryFilter, overdueFilter, activeFilterLabel, clearQueryFilter, syncFromQuery } =
  useIssueFilterQuery(() => {
    page.value = 1
    load()
  })

// 列表/树/看板共用的筛选参数：字段定义收敛在 useIssueFilterParams，避免各视图各拼一份
/** 服务端排序状态：el-table 的 sort-change 写入，随筛选参数一起下发 */
const sortField = ref('')
const sortOrder = ref('')

const filterParams = useIssueFilterParams({
  projectId: () => projectId.value,
  typeCodes: () => props.typeCodes,
  scope: () => scope.value,
  keyword: () => keyword.value,
  statusCategory: () => statusCategoryFilter.value,
  overdue: () => overdueFilter.value
})

async function loadList() {
  loading.value = true
  try {
    const res = await filterIssues(
      filterParams({
        page: page.value,
        size: size.value,
        sortField: sortField.value || undefined,
        sortOrder: sortOrder.value || undefined
      })
    )
    tableData.value = res.list
    total.value = res.total
  } catch {
    tableData.value = []
  } finally {
    loading.value = false
  }
}

async function loadTree() {
  loading.value = true
  try {
    const res = await filterIssues(filterParams({ page: 1, size: 1000 }))
    allIssues.value = res.list
    total.value = res.total
  } catch {
    allIssues.value = []
  } finally {
    loading.value = false
  }
}

async function loadBoard() {
  loading.value = true
  try {
    const [boardRes, transitions] = await Promise.all([
      getBoardGrouped(projectId.value, 'STATUS', props.boardCategory),
      // 流转规则用于拖拽校验，接口异常时不阻塞看板渲染
      listTransitions(projectId.value, props.boardCategory).catch(() => [] as WorkflowTransitionVO[])
    ])
    board.value = boardRes
    transitionMap.value = buildTransitionMap(transitions)
    const res = await filterIssues(filterParams({ page: 1, size: 1000 }))
    filteredIds.value = new Set(res.list.map((i: Issue) => i.id))
    total.value = res.total
  } catch {
    board.value = null
  } finally {
    loading.value = false
  }
}

/**
 * 表头排序。el-table 的 prop 用后端认的字段名（issueKey / updatedAt / createdAt …），
 * order 为 ascending / descending / null（取消排序时回落到默认排序）。
 */
function onSortChange({ prop, order }: { prop: string | null; order: string | null }) {
  sortField.value = order && prop ? prop : ''
  sortOrder.value = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
  page.value = 1
  load()
}

async function load() {
  if (view.value === 'list') return loadList()
  if (view.value === 'tree') return loadTree()
  return loadBoard()
}

/** 拖拽编排收敛在 useBoardDrag：列高亮、流转预判、落点邻接与失败回滚只有一份实现 */
const { dragOverCol, onMove, onEnd } = useBoardDrag({
  transitionMap,
  groups: () => renderGroups.value,
  reload: loadBoard
})

/** 数据发生增删改后：刷新列表与左侧范围计数（计数与当前视图无关，需单独拉取） */
function reloadAll() {
  load()
  refreshScopeCounts()
  loadStats()
}

function onSearch() { page.value = 1; load(); loadStats() }
function onViewChange(v: string) {
  view.value = v as ViewMode
  localStorage.setItem(`kanban:${props.storageKey}:${projectId.value}`, v)
  load()
}
function onScopeChange(v: string) {
  scope.value = v
  page.value = 1
  load()
  loadStats()
}

async function openCreate() {
  if (!statuses.value.length) {
    try { statuses.value = await listProjectStatuses(projectId.value, props.boardCategory) } catch { /* 状态字典降级为空 */ }
  }
  createOpen.value = true
}

onMounted(() => {
  const saved = localStorage.getItem(`kanban:${props.storageKey}:${projectId.value}`)
  if (saved === 'list' || saved === 'tree' || saved === 'board') view.value = saved
  // 度量页跳转带来的筛选
  syncFromQuery()
  refreshPermission()
  loadMeta().then(() => {
    load()
    refreshScopeCounts()
    // 统计条依赖状态字典把 statusId 映射到 category，必须等 loadMeta 完成后再拉起
    loadStats()
  })
})
</script>

<template>
  <SimpleListView
    :title="noun"
    :icon="icon"
    :search-placeholder="`搜索${noun}`"
    create-label="新建"
    :can-create="canWrite"
    :can-export="canExport"
    :loading="loading"
    :total="total"
    :is-empty="view === 'list' && !tableData.length"
    :empty-text="`暂无${noun}`"
    :nav-groups="navGroups"
    :scope="scope"
    :view-tabs="viewTabs"
    :view="view"
    v-model:page="page"
    v-model:size="size"
    :show-pagination="view === 'list'"
    v-model:keyword="keyword"
    @update:view="onViewChange"
    @update:scope="onScopeChange"
    @search="onSearch"
    @refresh="reloadAll"
    @export="onExport"
    @create="openCreate"
  >
    <template #stats>
      <WorkItemStatsBar v-if="view !== 'board'" :items="statItems" />
    </template>
    <template #header-extra>
      <div v-if="activeFilterLabel" class="flex items-center gap-1.5">
        <span class="rounded-full bg-brand-light px-2.5 py-1 text-xs text-brand">筛选：{{ activeFilterLabel }}</span>
        <button class="text-xs text-ink-400 hover:text-brand" @click="clearQueryFilter">清除</button>
      </div>
    </template>
    <!-- 列表 / 树形：套上概览页那套「卡片 + 彩色统计 + 统一留白」的外壳 -->
    <div v-if="view !== 'board'" class="wi-page">

      <!-- 列表视图：列与树形视图保持一致，视觉收紧见 style.css 的 .wi-table -->
      <div v-if="view === 'list'" class="wi-card glass-panel">
        <div class="wi-card-head">
          <component :is="icon" :size="16" class="text-brand" />
          <span class="wi-card-title">全部{{ noun }}</span>
          <span class="count-pill">{{ total }}</span>
          <!-- 勾选后就地出现批量操作，不额外占一行 -->
          <template v-if="selectedCount">
            <span class="wi-sel-pill">已选 {{ selectedCount }} 项</span>
            <button class="wi-sel-clear" @click="clearSelection">清空</button>
            <button class="wi-danger-btn" :disabled="deleting" @click="onBatchDelete">
              <Trash2 :size="13" /> {{ deleting ? '删除中…' : '删除选中' }}
            </button>
          </template>
          <span v-else class="ml-auto text-xs text-ink-secondary">按更新时间倒序</span>
        </div>
        <div class="wi-card-body wi-table">
          <!-- 表格交给 el-table：列宽可拖拽、表头 sticky、横向滚动、selection 列自带全选/半选 -->
          <el-table
            ref="listTableRef"
            :data="tableData"
            row-key="id"
            height="100%"
            class="wi-el-table"
            :row-class-name="listRowClass"
            @row-click="(row: Issue) => detailDrawer?.open(row.id)"
            @selection-change="onSelectionChange"
            @sort-change="onSortChange"
          >
            <el-table-column v-if="canDelete" type="selection" width="38" class-name="wi-td-select" label-class-name="wi-th-select" />
            <el-table-column label="任务" min-width="200" show-overflow-tooltip>
              <template #default="{ row }">
                <div class="flex items-center gap-2">
                  <span
                    v-if="row.priority && row.priority !== 'P2'"
                    class="wi-prio"
                    :style="{ color: priorityColorOf(row.priority) }"
                    :title="`优先级 ${priorityText(row.priority)}`"
                  >
                    <span class="wi-prio-dot" />
                  </span>
                  <span class="wi-title-leaf min-w-0 truncate">{{ row.title }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="issueKey" label="编号" width="110" sortable="custom">
              <template #default="{ row }"><span class="wi-key">{{ row.issueKey }}</span></template>
            </el-table-column>
            <el-table-column v-if="!hideSmColumns" label="负责人" width="136">
              <template #default="{ row }">
                <UserCell
                  v-if="row.assigneeId"
                  :avatar-name="row.assigneeName"
                  :avatar-url="row.assigneeAvatarUrl"
                  :display-name="memberName(row.assigneeId)"
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
            <el-table-column v-if="!hideMdColumns" prop="updatedAt" label="更新于" width="92" align="right" sortable="custom">
              <template #default="{ row }"><span class="wi-date">{{ shortDate(row.updatedAt) }}</span></template>
            </el-table-column>
            <template #empty>
              <TableSkeleton v-if="loading" :rows="6" :cols="4" />
              <TableEmpty
                :icon="icon"
                :title="`暂无${noun}`"
                :hint="keyword ? `没有匹配「${keyword}」的${noun}，换个关键字试试` : `点击右上角「新建」创建第一个${noun}`"
              />
            </template>
          </el-table>
        </div>
      </div>

      <!-- 树形视图：层级 + 展开/收起（此前只渲染根节点，238 条数据只显示 3 条） -->
      <WorkItemTreeTable
        v-else-if="view === 'tree'"
        :nodes="treeData"
        :status-name="statusName"
        :status-color="statusColor"
        :member-name="memberName"
        :noun="noun"
        :selected="selectedIds"
        :selectable="canDelete"
        :loading="loading"
        @select="onTreeSelect"
        @open="(id: string) => detailDrawer?.open(id)"
      >
        <template #head-actions>
          <template v-if="selectedCount">
            <span class="wi-sel-pill">已选 {{ selectedCount }} 项</span>
            <button class="wi-sel-clear" @click="clearSelection">清空</button>
            <button class="wi-danger-btn" :disabled="deleting" @click="onBatchDelete">
              <Trash2 :size="13" /> {{ deleting ? '删除中…' : '删除选中' }}
            </button>
          </template>
        </template>
      </WorkItemTreeTable>
    </div>
    <!-- 看板视图 -->
    <div v-else class="kanban-page">
      <div
        v-for="g in renderGroups"
        :key="g.groupKey"
        class="kanban-group h-full"
      >
        <div class="kanban-group-head">
          <span class="text-sm font-semibold text-ink">{{ g.groupName }}</span>
          <span class="count-pill">{{ g.issueCount }}</span>
        </div>
        <div class="kanban-cols flex-1">
          <div
            v-for="col in g.columns"
            :key="col.statusId"
            class="kanban-col"
            :class="{
              'kanban-col-allow': dragOverCol?.statusId === col.statusId && dragOverCol?.allowed,
              'kanban-col-deny': dragOverCol?.statusId === col.statusId && !dragOverCol?.allowed,
            }"
          >
            <div class="kanban-col-head">
              <span class="h-2.5 w-2.5 rounded-full" :style="{ background: col.color || 'var(--ink-secondary)' }" />
              <span class="text-sm font-medium text-ink">{{ col.name }}</span>
              <span class="text-xs text-ink-secondary">{{ col.issues.length }}</span>
            </div>
            <!-- 不要加 force-fallback：.kanban-col / .kanban-group 的 backdrop-filter 会接管
                 fixed 定位，Sortable 用 fixed 跟随鼠标的那张卡片会被算到列容器上，
                 于是拖拽影像飘到别处；原生 HTML5 拖拽由浏览器绘制影像，不受此影响 -->
            <draggable
              :list="col.issues"
              group="issues"
              item-key="id"
              :data-status-id="col.statusId"
              class="flex-1 select-none space-y-2 overflow-y-auto px-3 pb-3"
              ghost-class="sortable-ghost"
              drag-class="sortable-drag"
              chosen-class="sortable-chosen"
              :animation="180"
              :disabled="!canWrite"
              @move="onMove"
              @end="onEnd"
            >
              <template #item="{ element }">
                <div class="kanban-card" @click="detailDrawer?.open(element.id)">
                  <div class="mb-1.5 flex items-center gap-2">
                    <span class="h-1.5 w-1.5 rounded-full" :style="{ background: PRIORITY_COLOR[element.priority] || 'var(--ink-secondary)' }" />
                    <span class="font-mono text-xs text-ink-secondary">{{ element.issueKey }}</span>
                  </div>
                  <div class="line-clamp-2 text-sm leading-snug text-ink">{{ element.title }}</div>
                  <div class="mt-2 flex items-center justify-between">
                    <PriorityPill :priority="element.priority" size="xs" />
                    <UserCell
                      v-if="element.assigneeName"
                      :avatar-name="element.assigneeName"
                      :avatar-url="element.assigneeAvatarUrl"
                      :display-name="element.assigneeName"
                    />
                    <span v-else class="text-[11px] text-ink-placeholder">未指派</span>
                  </div>
                </div>
              </template>
            </draggable>
          </div>
        </div>
      </div>
    </div>

    <template #footer>
      <IssueDetailDrawer ref="detailDrawer" :project-id="projectId" @closed="reloadAll" />
      <CreateIssueDialog
        v-model="createOpen"
        :project-id="projectId"
        :title="`新建${noun}`"
        :default-issue-type-code="defaultIssueTypeCode"
        :default-status-id="statuses[0]?.id || ''"
        @created="reloadAll"
      />
    </template>
  </SimpleListView>
</template>