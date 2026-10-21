<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  filterIssues, listProjectStatuses, listProjectMembers,
  getBoardGrouped, listTransitions, moveIssue
} from '@/api'
import type { Issue, IssueTreeNode, IssueStatusVO, BoardGroupView, GroupColumnView, WorkflowTransitionVO, WorkItemCategory } from '@/api/types'
import type { LucideIcon } from 'lucide-vue-next'
import { List, GitBranch, LayoutGrid, User, UserCheck, Inbox, Archive } from 'lucide-vue-next'
import draggable from 'vuedraggable'
import {
  draggedIssue, dragFromStatusId, dropStatusId, dragNeighbors, findColumnIssues,
  type WorkItemDragEvent
} from '@/utils/dragEvent'
import SimpleListView from '@/components/SimpleListView.vue'
import CreateIssueDialog from '@/components/CreateIssueDialog.vue'
import IssueDetailDrawer from '@/components/IssueDetailDrawer.vue'
import { useWorkflowTransition } from '@/composables/useWorkflowTransition'
import { useProjectPermission } from '@/composables/useProjectPermission'
import { useScopeCounts } from '@/composables/useScopeCounts'
import { PRIORITY_COLOR as priorityColor } from '@/constants/issueDict'
import DataTable, { type TableColumn } from '@/components/common/DataTable.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import PriorityPill from '@/components/common/PriorityPill.vue'
import UserCell from '@/components/common/UserCell.vue'
import EmptyRow from '@/components/common/EmptyRow.vue'
import type { ViewTab } from '@/components/ViewTabs.vue'

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
const dragOverCol = ref<{ statusId: string; allowed: boolean } | null>(null)

const createOpen = ref(false)
const detailDrawer = ref<InstanceType<typeof IssueDetailDrawer> | null>(null)

const { transitionMap, build: buildTransitionMap } = useWorkflowTransition()
// 只读访问者（VIEWER）不应看到「新建」入口：后端的写权限校验拦得住请求，但入口必须同步隐藏
const { canWrite, refresh: refreshPermission } = useProjectPermission(() => projectId.value)

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

const columns: TableColumn[] = [
  { key: 'title', label: '标题' },
  { key: 'status', label: '状态' },
  { key: 'priority', label: '优先级' },
  { key: 'assignee', label: '负责人' },
  { key: 'createdAt', label: '创建时间' }
]

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
function fmtDate(t?: string) { return t ? t.slice(0, 10) : '—' }

async function loadMeta() {
  // 状态/成员为筛选辅助数据，失败降级为空（接口层已统一提示错误）
  try { statuses.value = await listProjectStatuses(projectId.value, props.boardCategory) } catch { /* 状态字典降级为空 */ }
  try { members.value = await listProjectMembers(projectId.value) } catch { /* 成员筛选降级为空 */ }
}

function baseParams(extra: Record<string, unknown> = {}) {
  return {
    projectId: projectId.value,
    scope: scope.value || undefined,
    keyword: keyword.value || undefined,
    issueTypeCodes: props.typeCodes,
    ...extra
  }
}

async function loadList() {
  loading.value = true
  try {
    const res = await filterIssues(baseParams({ page: page.value, size: size.value }))
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
    const res = await filterIssues(baseParams({ page: 1, size: 1000 }))
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
    const res = await filterIssues(baseParams({ page: 1, size: 1000 }))
    filteredIds.value = new Set(res.list.map((i: Issue) => i.id))
    total.value = res.total
  } catch {
    board.value = null
  } finally {
    loading.value = false
  }
}

async function load() {
  if (view.value === 'list') return loadList()
  if (view.value === 'tree') return loadTree()
  return loadBoard()
}

/** 拖拽过程中的实时校验：不合格的列会立刻回弹（返回 false），并高亮为拒绝色 */
function onMove(evt: WorkItemDragEvent): boolean {
  const fromId = dragFromStatusId(evt)
  const toId = dropStatusId(evt)
  if (!fromId || !toId) return true
  // 同列排序恒允许；跨列须存在本类别工作流的流转规则
  const allowed = fromId === toId || transitionMap.value[fromId]?.has(toId) === true
  if (dragOverCol.value?.statusId !== toId || dragOverCol.value?.allowed !== allowed) {
    dragOverCol.value = { statusId: toId, allowed }
  }
  return allowed
}

async function onEnd(event: WorkItemDragEvent) {
  dragOverCol.value = null
  const moved = draggedIssue(event)
  if (!moved) return
  const toStatusId = dropStatusId(event)
  if (!toStatusId) return
  const fromStatusId = moved.statusId
  const allowed = fromStatusId === toStatusId || transitionMap.value[fromStatusId]?.has(toStatusId) === true
  if (!allowed) {
    // 服务端也会拒绝，这里先按规则回弹，避免不必要的请求
    await loadBoard()
    return
  }
  const { before, after } = dragNeighbors(event, findColumnIssues(renderGroups.value, toStatusId))
  try {
    await moveIssue(moved.id, { statusId: toStatusId, beforeId: before?.id, afterId: after?.id })
  } catch {
    // 失败提示由接口层统一弹出；finally 重新拉取看板，把乐观移动的卡片回滚到服务端真实顺序
  } finally {
    await loadBoard()
  }
}

/** 数据发生增删改后：刷新列表与左侧范围计数（计数与当前视图无关，需单独拉取） */
function reloadAll() {
  load()
  refreshScopeCounts()
}

function onSearch() { page.value = 1; load() }
function onViewChange(v: string) {
  view.value = v as ViewMode
  localStorage.setItem(`kanban:${props.storageKey}:${projectId.value}`, v)
  load()
}
function onScopeChange(v: string) {
  scope.value = v
  page.value = 1
  load()
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
  refreshPermission()
  loadMeta().then(() => {
    load()
    refreshScopeCounts()
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
    show-pagination
    v-model:keyword="keyword"
    @update:view="onViewChange"
    @update:scope="onScopeChange"
    @search="onSearch"
    @refresh="reloadAll"
    @create="openCreate"
  >
    <!-- 列表视图 -->
    <DataTable v-if="view === 'list'" :columns="columns">
      <tr
        v-for="i in tableData"
        :key="i.id"
        class="data-tr cursor-pointer"
        @click="detailDrawer?.open(i.id)"
      >
        <td class="data-td data-td-first">
          <div class="flex items-center gap-2">
            <component :is="icon" :size="14" class="shrink-0 text-brand" />
            <span class="cell-key">{{ i.issueKey }}</span>
            <span class="cell-title">{{ i.title }}</span>
          </div>
        </td>
        <td class="data-td">
          <StatusPill :label="statusName(i.statusId)" :color="statusColor(i.statusId)" />
        </td>
        <td class="data-td">
          <PriorityPill :priority="i.priority" />
        </td>
        <td class="data-td">
          <UserCell :avatar-name="i.assigneeName" :avatar-url="i.assigneeAvatarUrl" :display-name="memberName(i.assigneeId)" />
        </td>
        <td class="data-td cell-muted">{{ fmtDate(i.createdAt) }}</td>
      </tr>
      <EmptyRow v-if="!loading && !tableData.length" :colspan="columns.length" :text="`暂无${noun}`" />
    </DataTable>

    <!-- 树形视图 -->
    <div v-else-if="view === 'tree'" class="tree-page">
      <div v-if="!treeData.length" class="empty-row text-sm">{{ `暂无${noun}` }}</div>
      <div
        v-for="node in treeData"
        :key="node.id"
        class="tree-node"
        @click="detailDrawer?.open(node.id)"
      >
        <component :is="icon" :size="14" class="shrink-0 text-brand" />
        <span class="cell-key">{{ node.issueKey }}</span>
        <span class="flex-1 truncate text-ink">{{ node.title }}</span>
        <StatusPill :label="statusName(node.statusId)" :color="statusColor(node.statusId)" />
        <PriorityPill :priority="node.priority" />
      </div>
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
                    <span class="h-1.5 w-1.5 rounded-full" :style="{ background: priorityColor[element.priority] || 'var(--ink-secondary)' }" />
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