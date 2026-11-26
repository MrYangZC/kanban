<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import draggable from 'vuedraggable'
import {
  filterIssues, updateIssue, getBoardGrouped, listProjectStatuses,
  listProjectTags, listProjectMembers, listUsers, getProject,
  listTransitions, exportIssuesCsv, listSprints
} from '@/api'
import { DEFECT_TYPE_CODES } from '@/api/types'
import type {
  Issue, IssueTreeNode, LabelVO, ProjectMember, UserOption, IssueStatusVO,
  BoardGroupView, GroupColumnView, Project, WorkflowTransitionVO, SprintVO
} from '@/api/types'
import { ElMessage } from 'element-plus'
import IssueDetailDrawer from '@/components/IssueDetailDrawer.vue'
import WorkItemStatsBar from '@/components/workitem/WorkItemStatsBar.vue'
import type { WorkItemStat } from '@/components/workitem/WorkItemStatsBar.vue'
import TableSkeleton from '@/components/common/TableSkeleton.vue'
import CreateIssueDialog from '@/components/CreateIssueDialog.vue'
import SimpleListView from '@/components/SimpleListView.vue'
import PriorityPill from '@/components/common/PriorityPill.vue'
import TableEmpty from '@/components/common/TableEmpty.vue'
import UserCell from '@/components/common/UserCell.vue'
import type { ViewTab } from '@/components/ViewTabs.vue'
import { PRIORITY_COLOR as priorityColor, PRIORITY_LABEL } from '@/constants/issueDict'
import { PRIMARY_HEX } from '@/theme/colorMap'
import { fmtDateOrDash as fmtDate } from '@/utils/datetime'
import { useIssueFilterQuery } from '@/composables/useIssueFilterQuery'
import { reportError } from '@/utils/error'
import { useIssueFilterState } from '@/composables/useIssueFilterState'
import IssueFilterBar from '@/components/workitem/IssueFilterBar.vue'
import { groupIssues, createGroupResolver, flattenGroups, isGroupRow, groupRowSpan, type IssueGroupRow } from '@/utils/listGrouping'
import { useWorkflowTransition } from '@/composables/useWorkflowTransition'
import { useProjectPermission } from '@/composables/useProjectPermission'
import { useScopeCounts, isScopeKey } from '@/composables/useScopeCounts'
import { useIssueFilterParams } from '@/composables/useIssueFilterParams'
import { useBoardDrag } from '@/composables/useBoardDrag'
import {
  Bug, List, GitBranch, LayoutGrid,
  AlertTriangle, Inbox, UserCheck, User, Share2, Archive
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => route.params.projectId as string)

const PRIMARY = PRIMARY_HEX
const project = ref<Project | null>(null)
const loading = ref(false)
const allTags = ref<LabelVO[]>([])
const members = ref<ProjectMember[]>([])
const users = ref<UserOption[]>([])
const statuses = ref<IssueStatusVO[]>([])
/** 筛选条件（状态/优先级/严重度/标签/迭代/逾期）与列表分组维度：三视图共用一份 */
const filter = useIssueFilterState(() => projectId.value, 'defects')
const sprints = ref<SprintVO[]>([])
const { hasPermission, refresh: refreshPermission } = useProjectPermission(() => projectId.value)
const canWrite = computed(() => hasPermission('issue:edit'))
/** 导出按钮与后端 issue:export 权限码对齐 */
const canExport = computed(() => hasPermission('issue:export'))

/** 导出中标记：避免连点触发多次下载 */
const exporting = ref(false)

/** 导出当前筛选结果（CSV）：条件与列表同源，屏幕上是几条就导出几条 */
async function onExport() {
  if (exporting.value) return
  exporting.value = true
  try {
    const res = await exportIssuesCsv(
      projectId.value,
      filterParams({
        statusCategory: statusCategoryFilter.value || undefined,
        overdue: (overdueFilter.value || filter.overdue) || undefined
      }),
      [...DEFECT_TYPE_CODES]
    )
    if (res.truncated) {
      ElMessage.warning(`命中 ${res.total} 条，单次最多导出 ${res.rows} 条；请缩小筛选范围后分批导出`)
    } else {
      ElMessage.success(`已导出 ${res.rows} 条`)
    }
  } catch (e: unknown) {
    reportError(e, '导出失败')
  } finally {
    exporting.value = false
  }
}

type ViewMode = 'list' | 'tree' | 'board'
const view = ref<ViewMode>('list')
const scope = ref<string>('')
const keyword = ref('')
const page = ref(1)
const size = ref(25)
const total = ref(0)
const tableData = ref<Issue[]>([])
const allIssues = ref<Issue[]>([])

const board = ref<BoardGroupView | null>(null)
/**
 * 统计条：全部 + 缺陷工作流的每个状态节点各一条（与任务/需求页同一套逻辑）。
 *
 * <p>不按 category 分桶：缺陷的「已修复/暂不修复/已关闭」都归 category=DONE，
 * 汇总成一个「已修复」会把没修的也算进去，和看板列数对不上。
 */
const statItems = computed<WorkItemStat[]>(() => {
  // 看板取 renderGroups（已套用筛选），不能用 board.groups 的全量：
  // 否则顶上的「全部缺陷 N」会与看板实际可见的卡片数互相打架
  const source =
    view.value === 'board' && board.value
      ? renderGroups.value.flatMap((g) => g.columns.flatMap((c) => c.issues))
      : view.value === 'tree'
        ? allIssues.value
        : tableData.value

  const byStatus = new Map<string, number>()
  for (const i of source) byStatus.set(i.statusId, (byStatus.get(i.statusId) || 0) + 1)

  const items: WorkItemStat[] = [
    { key: 'total', label: '全部缺陷', value: source.length, color: 'var(--primary)' }
  ]
  for (const s of statuses.value) {
    items.push({
      key: s.id,
      label: s.name,
      value: byStatus.get(s.id) || 0,
      color: s.color || 'var(--ink-secondary)'
    })
  }
  // 字典外的状态（历史脏数据/工作流改过）不能凭空消失，否则合计对不上「全部」
  const known = new Set(statuses.value.map((s) => s.id))
  let orphan = 0
  for (const [statusId, n] of byStatus) if (!known.has(statusId)) orphan += n
  if (orphan > 0) {
    items.push({ key: 'unknown', label: '未匹配状态', value: orphan, color: 'var(--ink-secondary)' })
  }
  return items
})

const filteredIds = ref<Set<string>>(new Set())

// 工作流流转规则：from statusId → 允许的 to statusId 集合
// 唯一事实来源为后端 workflow_transition 表，前端不硬编码
const { transitionMap, build: buildTransitionMap, load: loadTransitions } = useWorkflowTransition()

const statusNameMap = computed(() => {
  const m: Record<string, string> = {}
  if (board.value) {
    for (const g of board.value.groups) {
      for (const c of g.columns) m[c.statusId] = c.name
    }
  }
  for (const s of statuses.value) if (!m[s.id]) m[s.id] = s.name
  return m
})

// 左侧各范围的条数：与视图无关，切到看板/树形后仍取自 filter 的 total
// 计数套用当前关键词 + 筛选条条件（scope 自己遍历），这样左栏数字与列表口径一致
const { counts: scopeCounts, refresh: refreshScopeCounts } = useScopeCounts(
  () => projectId.value,
  () => DEFECT_TYPE_CODES,
  () => ({
    keyword: keyword.value.trim() || undefined,
    statusId: filter.statusId || undefined,
    priority: filter.priority || undefined,
    severity: filter.severity || undefined,
    labelId: filter.labelId || undefined,
    sprintId: filter.sprintId || undefined,
    assigneeId: filter.assigneeId || undefined,
    reporterId: filter.reporterId || undefined,
    createdFrom: filter.createdFrom || undefined,
    createdTo: filter.createdTo || undefined,
    updatedFrom: filter.updatedFrom || undefined,
    updatedTo: filter.updatedTo || undefined,
    dueFrom: filter.dueFrom || undefined,
    dueTo: filter.dueTo || undefined,
    overdue: (overdueFilter.value || filter.overdue) || undefined
  })
)
const scopeCount = (key: string) => scopeCounts.value[key] ?? 0

const navGroups = computed(() => [
  {
    title: '个人',
    items: [
      { key: 'REPORTED', label: '我创建的', icon: User, count: scopeCount('REPORTED') },
      { key: 'ASSIGNED', label: '我负责的', icon: UserCheck, count: scopeCount('ASSIGNED') },
      { key: 'VERIFIED', label: '待我验证', icon: Inbox, count: scopeCount('VERIFIED') },
    ],
  },
  {
    title: '公共',
    items: [
      // 「全部缺陷」优先用范围计数，计数未回时退回当前列表 total，避免首屏闪 0
      { key: '', label: '全部缺陷', icon: AlertTriangle, count: scopeCounts.value[''] ?? total.value },
      { key: 'SHARED', label: '共享到本项目', icon: Share2, count: scopeCount('SHARED') },
      { key: 'ARCHIVED', label: '已归档', icon: Archive, count: scopeCount('ARCHIVED') },
    ],
  },
])

const renderGroups = computed(() => {
  if (!board.value) return []
  return board.value.groups.map((g) => {
    const columns = g.columns.map((c: GroupColumnView) => ({
      ...c,
      issues: c.issues.filter((i) => filteredIds.value.has(i.id)),
    }))
    // 分组头计数必须用过滤后的实际卡片数，否则会显示「未套用范围/关键字」的全量值
    return { ...g, columns, issueCount: columns.reduce((n, c) => n + c.issues.length, 0) }
  })
})

const treeData = computed<IssueTreeNode[]>(() => {
  const map = new Map<string, IssueTreeNode>()
  const roots: IssueTreeNode[] = []
  for (const i of allIssues.value) {
    map.set(i.id, { ...i, children: [] })
  }
  for (const i of allIssues.value) {
    const node = map.get(i.id)!
    if (i.parentId && map.has(i.parentId)) {
      map.get(i.parentId)!.children.push(node)
    } else {
      roots.push(node)
    }
  }
  return roots
})

/**
 * 列表分组：状态按工作流顺序、优先级按 P0→P3、迭代按迭代列表顺序，负责人按名称。
 * 解析器与任务/需求页共用（utils/listGrouping），这里只注入本页的字典。
 */
function buildGroupResolver(groupBy: string) {
  return createGroupResolver(groupBy, {
    statusName: (id) => statusName(id),
    statusOrder: (id) => {
      const idx = statuses.value.findIndex((s) => s.id === id)
      return idx < 0 ? 999 : idx
    },
    priorityLabel: (p) => PRIORITY_LABEL[p] || p,
    memberName: (id) => memberName(id),
    sprintName: (id) => sprints.value.find((s) => s.id === id)?.name || '未规划',
    sprintOrder: (id) => {
      const idx = sprints.value.findIndex((s) => s.id === id)
      return idx < 0 ? 999 : idx
    }
  })
}

const listGroups = computed(() => {
  if (!filter.groupBy) return []
  return groupIssues(tableData.value, buildGroupResolver(filter.groupBy))
})

/** el-table 的数据源：分组模式下插入组标题伪行 */
const listRows = computed<Array<Issue | IssueGroupRow>>(() =>
  filter.groupBy ? flattenGroups(listGroups.value) : tableData.value
)

/** 缺陷列表没有勾选列：组标题直接从第一列铺到最后一列（列数需与模板里的列保持一致） */
const listColumnCount = 7
const listSpanMethod = computed(() => groupRowSpan(listColumnCount, 0))

/** 组标题行不是数据：不响应「点行开详情」 */
function onRowOpen(row: unknown) {
  if (isGroupRow(row)) return
  openDetail(row as Issue)
}

/** 行样式：组标题行单独标记，用途见 style.css 的 .wi-group-row */
function defectRowClass({ row }: { row: Issue | IssueGroupRow }) {
  return isGroupRow(row) ? 'wi-group-row' : ''
}

async function loadMeta() {
  // 各项资料相互独立：单项失败保留默认值即可，接口层已统一提示错误
  const pid = projectId.value
  try { project.value = await getProject(pid) } catch { /* 项目信息失败时沿用 store 中的当前项目 */ }
  try { statuses.value = await listProjectStatuses(pid, 'BUG') } catch { /* 状态字典缺失时列表按状态 ID 兜底展示 */ }
  try { allTags.value = await listProjectTags(pid) } catch { /* 标签筛选降级为空 */ }
  try { members.value = await listProjectMembers(pid) } catch { /* 成员筛选降级为空 */ }
  try { users.value = await listUsers() } catch { /* 用户筛选降级为空 */ }
  try { sprints.value = await listSprints(pid) } catch { /* 迭代筛选降级为空 */ }
  // 状态流转边必须提前加载：列表视图的行内状态下拉依赖它收窄可选目标
  try { await loadTransitions(pid, 'BUG') } catch { /* 流转字典缺失时下拉只保留当前状态 */ }
  refreshPermission()
}

/**
 * 列表状态下拉：当前状态 + 工作流可达状态，与看板拖拽规则一致。
 * 流转字典缺失时只保留当前状态——绝不能退化成「全部状态」，
 * 否则会把非法目标暴露给用户（选了之后才被后端 400 拒绝）。
 */
function statusOptionsFor(fromStatusId: string): IssueStatusVO[] {
  const reachable = transitionMap.value[fromStatusId]
  if (!reachable || !reachable.size) return statuses.value.filter((s) => s.id === fromStatusId)
  return statuses.value.filter((s) => s.id === fromStatusId || reachable.has(s.id))
}

// 度量页点统计数字跳过来时带的筛选（?statusCategory / ?overdue），同步逻辑见 composable
const { statusCategoryFilter, overdueFilter, activeFilterLabel, clearQueryFilter, syncFromQuery } =
  useIssueFilterQuery(() => {
    page.value = 1
    load()
  })

/** 列表/树/看板共用的筛选参数，保证三个视图口径一致 */
/** 服务端排序（缺陷列表）：el-table 的表头点击写入，随筛选参数一起下发 */
const defectSortField = ref('')
const defectSortOrder = ref('')

const filterParams = useIssueFilterParams({
  projectId: () => projectId.value,
  typeCodes: () => DEFECT_TYPE_CODES,
  scope: () => scope.value,
  keyword: () => keyword.value,
  statusCategory: () => statusCategoryFilter.value,
  // 筛选条上的条件（与度量页带来的 ?statusCategory/overdue 是两层，互不覆盖）
  statusId: () => filter.statusId,
  priority: () => filter.priority,
  severity: () => filter.severity,
  labelId: () => filter.labelId,
  sprintId: () => filter.sprintId,
  assigneeId: () => filter.assigneeId,
  reporterId: () => filter.reporterId,
  createdFrom: () => filter.createdFrom,
  createdTo: () => filter.createdTo,
  updatedFrom: () => filter.updatedFrom,
  updatedTo: () => filter.updatedTo,
  dueFrom: () => filter.dueFrom,
  dueTo: () => filter.dueTo,
  overdue: () => overdueFilter.value || filter.overdue
})

function onDefectSortChange({ prop, order }: { prop: string | null; order: string | null }) {
  defectSortField.value = order && prop ? prop : ''
  defectSortOrder.value = order === 'ascending' ? 'asc' : order === 'descending' ? 'desc' : ''
  page.value = 1
  loadList()
}

async function loadList() {
  loading.value = true
  try {
    // 分组模式按组统计条数，只能全量取（后端单次上限 1000）；不分组时仍走服务端分页
    const res = filter.groupBy
      ? await filterIssues(filterParams({ page: 1, size: 1000 }))
      : await filterIssues(
        filterParams({
          page: page.value,
          size: size.value,
          sortField: defectSortField.value || undefined,
          sortOrder: defectSortOrder.value || undefined
        })
      )
    tableData.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadAllIssues() {
  const res = await filterIssues(filterParams({ page: 1, size: 1000 }))
  allIssues.value = res.list
  total.value = res.total
}

async function loadBoard() {
  loading.value = true
  try {
    const [boardRes, transitions] = await Promise.all([
      getBoardGrouped(projectId.value, 'STATUS', 'BUG'),
      listTransitions(projectId.value, 'BUG').catch(() => [] as WorkflowTransitionVO[]),
    ])
    board.value = boardRes
    transitionMap.value = buildTransitionMap(transitions)
    const issues = await filterIssues(filterParams({ size: 1000 }))
    filteredIds.value = new Set(issues.list.map((i: Issue) => i.id))
    // 看板视图同样要回写 total，否则顶栏与「全部缺陷」在看板下恒为 0
    total.value = issues.total
  } finally {
    loading.value = false
  }
}

async function load() {
  if (view.value === 'list') await loadList()
  else if (view.value === 'tree') await loadAllIssues()
  else await loadBoard()
}

/** 数据发生增删改后：刷新列表与左侧范围计数（计数与当前视图无关，需单独拉取） */
function reloadAll() {
  load()
  refreshScopeCounts()
}

function setScope(s: string) {
  scope.value = s
  localStorage.setItem(`kanban:defectScope:${projectId.value}`, s)
  page.value = 1
  load()
}

function setView(v: string) {
  view.value = v as ViewMode
  localStorage.setItem(`kanban:defectView:${projectId.value}`, v)
  load()
}

function onSearch() {
  page.value = 1
  load()
  refreshScopeCounts()
}

/** 筛选条/分组维度变化：回到第一页再取数，避免停在已不存在的页码上 */
function onFilterChange() {
  page.value = 1
  load()
  refreshScopeCounts()
}

function statusName(id: string) { return statusNameMap.value[id] || '—' }
function statusColor(id: string) {
  const s = statuses.value.find((x) => x.id === id)
  return s?.color || PRIMARY
}
function memberName(id: string | null) { return members.value.find((m) => m.userId === id)?.displayName || '—' }
function userName(id: string | null) { return users.value.find((u) => u.id === id)?.displayName || '—' }


async function patchStatus(issue: Issue, statusId: string) {
  if (!canWrite.value) { ElMessage.warning('无编辑权限'); return }
  try {
    const updated = await updateIssue(issue.id, { statusId })
    Object.assign(issue, updated)
    ElMessage.success('状态已更新')
  } catch {
    // 更新失败（含并发流转冲突）：以服务端数据为准重新拉取列表
    await load()
  }
}

/** 列表内联状态下拉：从原生 select 事件中取出选中值，避免在模板里写 any 断言 */
function onStatusSelect(issue: Issue, e: Event) {
  const target = e.target as HTMLSelectElement | null
  if (target?.value) patchStatus(issue, target.value)
}

// 云效风格缺陷详情：宽抽屉（60% 屏宽），不跳转路由
const detailDrawer = ref<InstanceType<typeof IssueDetailDrawer> | null>(null)

function openDetail(issue: Issue) {
  detailDrawer.value?.open(issue.id)
}

// 新建缺陷：富文本弹窗
const createOpen = ref(false)
function onCreated() {
  reloadAll()
}

async function onNewIssue() {
  createOpen.value = true
}

/** 拖拽编排收敛在 useBoardDrag：列高亮、流转预判、落点邻接与失败回滚只有一份实现 */
const { dragOverCol, onMove, onEnd } = useBoardDrag({
  transitionMap,
  groups: () => renderGroups.value,
  reload: loadBoard
})

function back() { router.push('/projects') }

const viewTabs: ViewTab[] = [
  { key: 'list', label: '列表', icon: List },
  { key: 'tree', label: '树形', icon: GitBranch },
  { key: 'board', label: '看板', icon: LayoutGrid },
]


onMounted(() => {
  // 恢复上次使用的视图与左侧范围，避免刷新后丢失页面状态
  const pid = projectId.value
  const savedView = localStorage.getItem(`kanban:defectView:${pid}`)
  if (savedView === 'list' || savedView === 'tree' || savedView === 'board') view.value = savedView
  const savedScope = localStorage.getItem(`kanban:defectScope:${pid}`)
  if (savedScope && isScopeKey(savedScope)) {
    scope.value = savedScope
  } else if (savedScope) {
    // 历史脏值（含旧版写入的非法 scope）：清掉，否则会把导航钉在空范围上并显示 0
    localStorage.removeItem(`kanban:defectScope:${pid}`)
  }
  // 度量页跳转带来的筛选
  syncFromQuery()
  loadMeta().then(() => {
    load()
    refreshScopeCounts()
  })
})
</script>

<template>
  <SimpleListView
    title="缺陷"
    :icon="Bug"
    show-back
    search-placeholder="搜索缺陷"
    create-label="新建"
    :can-create="canWrite"
    :can-export="canExport"
    :loading="loading"
    :total="total"
    :is-empty="view === 'list' && !tableData.length"
        :nav-groups="navGroups"
    :scope="scope"
    :view-tabs="viewTabs"
    :view="view"
    v-model:page="page"
    v-model:size="size"
    :show-pagination="view === 'list' && !filter.groupBy"
    v-model:keyword="keyword"
    @update:view="setView"
    @update:scope="setScope"
    @search="onSearch"
    @refresh="reloadAll"
    @export="onExport"
    @create="onNewIssue"
    @back="back"
  >
    <template #stats>
      <WorkItemStatsBar :items="statItems" />
    </template>
    <template #header-extra>
      <div v-if="activeFilterLabel" class="flex items-center gap-1.5">
        <span class="rounded-full bg-brand-light px-2.5 py-1 text-xs text-brand">筛选：{{ activeFilterLabel }}</span>
        <button class="text-xs text-ink-400 hover:text-brand" @click="clearQueryFilter">清除</button>
      </div>
    </template>

    <template #filters>
      <!-- 筛选条：状态/优先级/严重度/标签/迭代/逾期 + 列表分组，三视图共用同一份条件 -->
      <IssueFilterBar
        :model="filter"
        :statuses="statuses"
        :tags="allTags"
        :sprints="sprints"
        :members="members"
        :users="users"
        show-severity
        :show-group="view === 'list'"
        :scheme-scope="`${projectId}:defects`"
        @change="onFilterChange"
      />
    </template>

    <!-- 列表视图 -->
    <div v-if="view === 'list'" class="wi-page">
      <div class="wi-card glass-panel">
        <div class="wi-card-body wi-table">
          <el-table
            :data="listRows"
            row-key="id"
            height="100%"
            class="wi-el-table"
            :row-class-name="defectRowClass"
            :span-method="listSpanMethod"
            @row-click="onRowOpen"
            @sort-change="onDefectSortChange"
          >
            <el-table-column prop="issueKey" label="缺陷" min-width="260" sortable="custom">
              <template #default="{ row }">
                <!-- 分组标题行：整行 span 成一格 -->
                <div v-if="isGroupRow(row)" class="wi-group-title">
                  <AlertTriangle :size="13" class="text-danger" />
                  <span>{{ row.label }}</span>
                  <span class="wi-group-count">{{ row.count }}</span>
                </div>
                <div v-else class="flex items-center gap-2">
                  <AlertTriangle :size="14" class="shrink-0 text-danger" />
                  <span class="wi-key">{{ row.issueKey }}</span>
                  <span class="cell-title">{{ row.title }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="130">
              <template #default="{ row }">
                <select
                  :value="row.statusId"
                  class="status-select"
                  :style="{ background: statusColor(row.statusId) + '1A', color: statusColor(row.statusId) }"
                  :disabled="!canWrite"
                  @click.stop
                  @change="onStatusSelect(row as unknown as Issue, $event)"
                >
                  <option v-for="s in statusOptionsFor(row.statusId)" :key="s.id" :value="s.id">{{ s.name }}</option>
                </select>
              </template>
            </el-table-column>
            <el-table-column label="优先级" width="92">
              <template #default="{ row }"><PriorityPill :priority="row.priority" /></template>
            </el-table-column>
            <el-table-column label="标签" min-width="180">
              <template #default="{ row }">
                <div class="flex max-w-[180px] flex-wrap gap-1">
                  <span
                    v-for="t in row.labels"
                    :key="t.id"
                    class="badge"
                    :style="{ background: (t.color || PRIMARY) + '1A', color: t.color || PRIMARY }"
                  >{{ t.name }}</span>
                  <span v-if="!row.labels?.length" class="text-xs text-ink-placeholder">—</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="负责人" width="136">
              <template #default="{ row }">
                <UserCell :avatar-name="row.assigneeName" :avatar-url="row.assigneeAvatarUrl" :display-name="memberName(row.assigneeId)" />
              </template>
            </el-table-column>
            <el-table-column label="报告人" width="136">
              <template #default="{ row }">
                <UserCell :avatar-name="row.reporterName" :avatar-url="row.reporterAvatarUrl" :display-name="userName(row.reporterId)" />
              </template>
            </el-table-column>
            <el-table-column prop="createdAt" label="创建时间" width="120" sortable="custom">
              <template #default="{ row }"><span class="cell-muted">{{ fmtDate(row.createdAt) }}</span></template>
            </el-table-column>
            <template #empty>
              <TableSkeleton v-if="loading" :rows="6" :cols="4" />
              <TableEmpty :icon="AlertTriangle" title="暂无缺陷" hint="提测或上线后发现的缺陷会集中在这里跟踪" />
            </template>
          </el-table>
        </div>
      </div>
    </div>

    <!-- 树形视图：改用 el-table 树形数据（旧的递归 TreeNode 已删除） -->
    <div v-else-if="view === 'tree'" class="wi-page">
      <div class="wi-card glass-panel">
        <div class="wi-card-body wi-table">
          <el-table
            :data="treeData"
            row-key="id"
            :tree-props="{ children: 'children' }"
            height="100%"
            class="wi-el-table"
            default-expand-all
            @row-click="(row: unknown) => openDetail(row as Issue)"
          >
            <el-table-column min-width="200">
              <template #header><span>缺陷</span></template>
              <template #default="{ row }">
                <div class="flex items-center gap-2">
                  <AlertTriangle :size="14" class="shrink-0 text-danger" />
                  <span class="wi-key">{{ row.issueKey }}</span>
                  <span class="cell-title">{{ row.title }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <StatusPill :label="statusName(row.statusId)" :color="statusColor(row.statusId)" />
              </template>
            </el-table-column>
            <el-table-column label="优先级" width="92">
              <template #default="{ row }"><PriorityPill :priority="row.priority" /></template>
            </el-table-column>
            <template #empty>
              <TableEmpty :icon="AlertTriangle" title="暂无缺陷" hint="提测或上线后发现的缺陷会集中在这里跟踪" />
            </template>
          </el-table>
        </div>
      </div>
    </div>

    <!-- 看板视图 -->
    <!-- 看板视图：与列表/树形共用同一套页面外壳（.wi-page + .wi-card + 卡片头） -->
    <div v-else class="wi-page">
      <div class="wi-card glass-panel">
        <div class="wi-card-head">
          <Bug :size="16" class="text-brand" />
          <span class="wi-card-title">全部缺陷</span>
          <span class="count-pill">{{ total }}</span>
          <span class="ml-auto text-xs text-ink-secondary">按状态分列，拖拽卡片可改状态</span>
        </div>
        <div class="kanban-page">
          <div v-for="g in renderGroups" :key="g.groupKey" class="kanban-group h-full">
        <div v-if="renderGroups.length > 1" class="kanban-group-head">
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
                <div class="kanban-card" @click="openDetail(element)">
                  <div class="mb-1.5 flex items-center gap-2">
                    <span class="h-1.5 w-1.5 rounded-full" :style="{ background: priorityColor[element.priority] || 'var(--ink-secondary)' }" />
                    <span class="font-mono text-xs text-ink-secondary">{{ element.issueKey }}</span>
                  </div>
                  <div class="line-clamp-2 text-sm leading-snug text-ink">{{ element.title }}</div>
                  <div v-if="element.labels?.length" class="mt-2 flex flex-wrap gap-1">
                    <span
                      v-for="t in element.labels"
                      :key="t.id"
                      class="badge badge-xs"
                      :style="{ background: (t.color || PRIMARY) + '1A', color: t.color || PRIMARY }"
                    >{{ t.name }}</span>
                  </div>
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
      </div>
    </div>

    <template #footer>
      <!-- 云效风格缺陷详情：宽抽屉（60% 屏宽），不跳转路由 -->
      <IssueDetailDrawer ref="detailDrawer" :project-id="projectId" @closed="reloadAll" />

      <!-- 新建缺陷：富文本弹窗 -->
      <CreateIssueDialog
        v-model="createOpen"
        :project-id="projectId"
        title="新建缺陷"
        default-issue-type-code="BUG"
        :default-status-id="statuses[0]?.id || ''"
        @created="onCreated"
      />
    </template>
  </SimpleListView>
</template>
