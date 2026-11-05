<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import draggable from 'vuedraggable'
import {
  filterIssues, updateIssue, getBoardGrouped, listProjectStatuses,
  listProjectTags, listProjectMembers, listUsers, getProject,
  listTransitions, exportIssuesUrl
} from '@/api'
import { DEFECT_TYPE_CODES } from '@/api/types'
import type {
  Issue, IssueTreeNode, LabelVO, ProjectMember, UserOption, IssueStatusVO,
  BoardGroupView, GroupColumnView, Project, WorkflowTransitionVO
} from '@/api/types'
import { ElMessage } from 'element-plus'
import IssueDetailDrawer from '@/components/IssueDetailDrawer.vue'
import TreeNode from '@/components/TreeNode.vue'
import CreateIssueDialog from '@/components/CreateIssueDialog.vue'
import SimpleListView from '@/components/SimpleListView.vue'
import DataTable, { type TableColumn } from '@/components/common/DataTable.vue'
import PriorityPill from '@/components/common/PriorityPill.vue'
import UserCell from '@/components/common/UserCell.vue'
import EmptyRow from '@/components/common/EmptyRow.vue'
import type { ViewTab } from '@/components/ViewTabs.vue'
import { PRIORITY_COLOR as priorityColor } from '@/constants/issueDict'
import { PRIMARY_HEX } from '@/theme/colorMap'
import { fmtDateOrDash as fmtDate } from '@/utils/datetime'
import { useIssueFilterQuery } from '@/composables/useIssueFilterQuery'
import { useWorkflowTransition } from '@/composables/useWorkflowTransition'
import { useProjectPermission } from '@/composables/useProjectPermission'
import { useScopeCounts, isScopeKey } from '@/composables/useScopeCounts'
import { useIssueFilterParams } from '@/composables/useIssueFilterParams'
import { useBoardDrag } from '@/composables/useBoardDrag'
import {
  Bug, List, GitBranch, LayoutGrid,
  AlertTriangle, MoreHorizontal, Inbox, UserCheck, User, Share2, Archive
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
const { hasPermission, refresh: refreshPermission } = useProjectPermission(() => projectId.value)
const canWrite = computed(() => hasPermission('issue:edit'))
/** 导出按钮与后端 issue:export 权限码对齐 */
const canExport = computed(() => hasPermission('issue:export'))

function onExport() {
  window.open(exportIssuesUrl(projectId.value, {
    types: [...DEFECT_TYPE_CODES],
    scope: scope.value || undefined,
    keyword: keyword.value.trim() || undefined
  }), '_blank')
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
const { counts: scopeCounts, refresh: refreshScopeCounts } = useScopeCounts(
  () => projectId.value,
  () => DEFECT_TYPE_CODES
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

async function loadMeta() {
  // 各项资料相互独立：单项失败保留默认值即可，接口层已统一提示错误
  const pid = projectId.value
  try { project.value = await getProject(pid) } catch { /* 项目信息失败时沿用 store 中的当前项目 */ }
  try { statuses.value = await listProjectStatuses(pid, 'BUG') } catch { /* 状态字典缺失时列表按状态 ID 兜底展示 */ }
  try { allTags.value = await listProjectTags(pid) } catch { /* 标签筛选降级为空 */ }
  try { members.value = await listProjectMembers(pid) } catch { /* 成员筛选降级为空 */ }
  try { users.value = await listUsers() } catch { /* 用户筛选降级为空 */ }
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
const filterParams = useIssueFilterParams({
  projectId: () => projectId.value,
  typeCodes: () => DEFECT_TYPE_CODES,
  scope: () => scope.value,
  keyword: () => keyword.value,
  statusCategory: () => statusCategoryFilter.value,
  overdue: () => overdueFilter.value
})

async function loadList() {
  loading.value = true
  try {
    const res = await filterIssues(filterParams({ page: page.value, size: size.value }))
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

const columns: TableColumn[] = [
  { key: 'title', label: '标题' },
  { key: 'status', label: '状态' },
  { key: 'priority', label: '优先级' },
  { key: 'labels', label: '标签' },
  { key: 'assignee', label: '负责人' },
  { key: 'reporter', label: '创建者' },
  { key: 'createdAt', label: '创建时间' },
  { key: 'actions', label: '操作' },
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
    empty-text="暂无缺陷"
    :nav-groups="navGroups"
    :scope="scope"
    :view-tabs="viewTabs"
    :view="view"
    v-model:page="page"
    v-model:size="size"
    show-pagination
    v-model:keyword="keyword"
    @update:view="setView"
    @update:scope="setScope"
    @search="onSearch"
    @refresh="reloadAll"
    @export="onExport"
    @create="onNewIssue"
    @back="back"
  >
    <template #header-extra>
      <div v-if="activeFilterLabel" class="flex items-center gap-1.5">
        <span class="rounded-full bg-brand-light px-2.5 py-1 text-xs text-brand">筛选：{{ activeFilterLabel }}</span>
        <button class="text-xs text-ink-400 hover:text-brand" @click="clearQueryFilter">清除</button>
      </div>
    </template>

    <!-- 列表视图 -->
    <DataTable v-if="view === 'list'" :columns="columns">
      <tr
        v-for="issue in tableData"
        :key="issue.id"
        class="data-tr cursor-pointer"
        @click="openDetail(issue)"
      >
        <td class="data-td data-td-first">
          <div class="flex items-center gap-2">
            <AlertTriangle :size="14" class="shrink-0 text-danger" />
            <span class="cell-key">{{ issue.issueKey }}</span>
            <span class="cell-title">{{ issue.title }}</span>
          </div>
        </td>
        <td class="data-td" @click.stop>
          <select
            :value="issue.statusId"
            class="status-select"
            :style="{ background: statusColor(issue.statusId) + '1A', color: statusColor(issue.statusId) }"
            :disabled="!canWrite"
            @change="onStatusSelect(issue, $event)"
          >
            <option v-for="s in statusOptionsFor(issue.statusId)" :key="s.id" :value="s.id">{{ s.name }}</option>
          </select>
        </td>
        <td class="data-td">
          <PriorityPill :priority="issue.priority" />
        </td>
        <td class="data-td">
          <div class="flex max-w-[180px] flex-wrap gap-1">
            <span
              v-for="t in issue.labels"
              :key="t.id"
              class="badge"
              :style="{ background: (t.color || PRIMARY) + '1A', color: t.color || PRIMARY }"
            >{{ t.name }}</span>
            <span v-if="!issue.labels?.length" class="text-xs text-ink-placeholder">—</span>
          </div>
        </td>
        <td class="data-td">
          <UserCell :avatar-name="issue.assigneeName" :avatar-url="issue.assigneeAvatarUrl" :display-name="memberName(issue.assigneeId)" />
        </td>
        <td class="data-td">
          <UserCell :avatar-name="issue.reporterName" :avatar-url="issue.reporterAvatarUrl" :display-name="userName(issue.reporterId)" />
        </td>
        <td class="data-td cell-muted">{{ fmtDate(issue.createdAt) }}</td>
        <td class="data-td" @click.stop>
          <button class="text-ink-placeholder transition hover:opacity-70">
            <MoreHorizontal :size="16" />
          </button>
        </td>
      </tr>
      <EmptyRow v-if="!loading && !tableData.length" :colspan="columns.length" text="暂无缺陷" />
    </DataTable>

    <!-- 树形视图 -->
    <div v-else-if="view === 'tree'" class="tree-page">
      <div v-if="!treeData.length" class="empty-row text-sm">暂无缺陷</div>
      <TreeNode
        v-for="node in treeData"
        :key="node.id"
        :node="node"
        :status-name="statusName"
        :status-color="statusColor"
        @open="openDetail"
      />
    </div>

    <!-- 看板视图 -->
    <div v-else class="kanban-page">
      <div v-for="g in renderGroups" :key="g.groupKey" class="kanban-group h-full">
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