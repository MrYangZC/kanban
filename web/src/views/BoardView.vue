<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import draggable from 'vuedraggable'
import { getBoardGrouped, createIssue, moveIssue, filterIssues, listProjectTags, listProjectMembers, listTransitions } from '@/api'
import { Archive, BarChart3, Bug, FileText, Inbox, LayoutGrid, ListTodo, Plus, Table2, User, UserCheck } from 'lucide-vue-next'
import type { BoardGroupView, Issue, GroupColumnView, LabelVO, ProjectMember, WorkflowTransitionVO, WorkItemCategory } from '@/api/types'
import { TASK_TYPE_CODES, REQUIREMENT_TYPE_CODES, DEFECT_TYPE_CODES } from '@/api/types'
import IssueCard from '@/components/IssueCard.vue'
import SimpleListView from '@/components/SimpleListView.vue'
import type { ViewTab } from '@/components/ViewTabs.vue'
import IssueDetailDrawer from '@/components/IssueDetailDrawer.vue'
import CreateIssueDialog from '@/components/CreateIssueDialog.vue'
import { useBoardFilter } from '@/stores/boardFilter'
import { ElMessage } from 'element-plus'
import { useWorkflowTransition } from '@/composables/useWorkflowTransition'
import { useProjectPermission } from '@/composables/useProjectPermission'
import {
  draggedIssue, dragFromStatusId, dropStatusId, dragNeighbors, findColumnIssues,
  type WorkItemDragEvent
} from '@/utils/dragEvent'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => route.params.projectId as string)

// 看板类型过滤：通过 ?type=task/requirement/defect 限定工作项类型
const TYPE_CODE_MAP: Record<string, string[]> = {
  task: TASK_TYPE_CODES,
  requirement: REQUIREMENT_TYPE_CODES,
  defect: DEFECT_TYPE_CODES
}
const TYPE_LABEL: Record<string, string> = {
  task: '任务看板',
  requirement: '需求看板',
  defect: '缺陷看板'
}
const typeKey = computed(() => (route.query.type as string) || '')
// 看板类型 → 后端聚合类别（TASK/REQUIREMENT/BUG），保证看板列数据按类型取数
const TYPE_CATEGORY_MAP: Record<string, string> = {
  task: 'TASK',
  requirement: 'REQUIREMENT',
  defect: 'BUG'
}
const boardCategory = computed(() => TYPE_CATEGORY_MAP[typeKey.value] || 'TASK')

const itemLabel = computed(() => {
  switch (typeKey.value) {
    case 'task': return '任务'
    case 'requirement': return '需求'
    case 'defect': return '缺陷'
    default: return '工作项'
  }
})

// 页面图标随工作项类型切换，与表格页保持一致
const pageIcon = computed(() => {
  switch (typeKey.value) {
    case 'task': return ListTodo
    case 'requirement': return FileText
    case 'defect': return Bug
    default: return LayoutGrid
  }
})

// 看板/表格为两个独立路由页，这里仅作为视图切换入口
const viewTabs: ViewTab[] = [
  { key: 'board', label: '看板', icon: LayoutGrid },
  { key: 'table', label: '表格', icon: Table2 }
]

const PRIORITY_OPTIONS = [
  { k: 'P0', label: '紧急' },
  { k: 'P1', label: '高' },
  { k: 'P2', label: '中' },
  { k: 'P3', label: '低' }
]
const SEVERITY_OPTIONS = [
  { k: 'S1', label: '致命' },
  { k: 'S2', label: '严重' },
  { k: 'S3', label: '一般' },
  { k: 'S4', label: '轻微' }
]

const board = ref<BoardGroupView | null>(null)
const boardName = ref('')
const loading = ref(false)
const allTags = ref<LabelVO[]>([])
const filter = useBoardFilter(projectId.value)
const filteredIds = ref<Set<string>>(new Set())
const { hasPermission, refresh: refreshPermission } = useProjectPermission(() => projectId.value)
const canWrite = computed(() => hasPermission('issue:create'))

async function load() {
  loading.value = true
  try {
    // 并行拉取看板数据与流转规则；规则接口异常时不阻塞看板渲染
    const [boardRes, transitions] = await Promise.all([
      getBoardGrouped(projectId.value, 'STATUS', boardCategory.value),
      listTransitions(projectId.value, boardCategory.value as WorkItemCategory).catch(() => [] as WorkflowTransitionVO[])
    ])
    board.value = boardRes
    boardName.value = TYPE_LABEL[typeKey.value] || boardRes.name || '任务看板'
    transitionMap.value = buildTransitionMap(transitions)
    // 标签用于筛选下拉，失败降级为空（接口层已提示错误）
    allTags.value = await listProjectTags(projectId.value).catch(() => [])
    await applyFilter()
  } finally {
    loading.value = false
  }
}

async function applyFilter() {
  const issues = await filterIssues({
    projectId: projectId.value,
    scope: filter.scope || undefined,
    keyword: filter.keyword || undefined,
    priority: filter.priority || undefined,
    severity: filter.severity || undefined,
    statusId: filter.statusId || undefined,
    labelId: filter.labelId || undefined,
    issueTypeCodes: (TYPE_CODE_MAP[typeKey.value] || []).length ? TYPE_CODE_MAP[typeKey.value] : undefined,
    // 看板需要全量分组渲染，不能走默认分页（否则只看得到前 20 条）
    size: 1000
  })
  filteredIds.value = new Set(issues.list.map((i: Issue) => i.id))
}

const renderGroups = computed(() => {
  if (!board.value) return []
  return board.value.groups.map((g) => {
    const columns = g.columns.map((c: GroupColumnView) => ({
      ...c,
      issues: c.issues.filter((i) => filteredIds.value.has(i.id))
    }))
    return {
      ...g,
      columns,
      issueCount: columns.reduce((s, c) => s + c.issues.length, 0)
    }
  })
})

const totalCount = computed(() =>
  renderGroups.value.reduce((s, g) => s + g.columns.reduce((cs, c) => cs + c.issues.length, 0), 0)
)

const navGroups = computed(() => [
  {
    title: '个人',
    items: [
      { key: 'REPORTED', label: '我创建的', icon: User },
      { key: 'ASSIGNED', label: '我负责的', icon: UserCheck },
      { key: 'VERIFIED', label: '待我验证', icon: Inbox }
    ]
  },
  {
    title: '公共',
    items: [
      { key: '', label: `全部${itemLabel.value}`, icon: pageIcon.value, count: totalCount.value },
      { key: 'ARCHIVED', label: '已归档', icon: Archive }
    ]
  }
])

// 工作流流转规则：from statusId → 允许的 to statusId 集合
// 数据来源为后端 workflow_transition 表（唯一事实来源），前端不再硬编码
const { transitionMap, build: buildTransitionMap } = useWorkflowTransition()

const dragOverCol = ref<{ statusId: string; allowed: boolean } | null>(null)

function onMove(evt: WorkItemDragEvent): boolean {
  const fromId = dragFromStatusId(evt)
  const toId = dropStatusId(evt)
  if (!fromId || !toId) return true
  // 同列排序恒允许；跨列须存在后端工作流规则
  const allowed = fromId === toId || transitionMap.value[fromId]?.has(toId) === true
  if (dragOverCol.value?.statusId !== toId || dragOverCol.value?.allowed !== allowed) {
    dragOverCol.value = { statusId: toId, allowed }
  }
  return allowed
}

async function onEnd(event: WorkItemDragEvent) {
  dragOverCol.value = null
  // 被拖卡片（vuedraggable 渲染时注入，指向真实 Issue 对象）
  const moved = draggedIssue(event)
  if (!moved) return
  // 落点列状态（data-status-id 透传到拖拽根元素上）
  const toStatusId = dropStatusId(event)
  if (!toStatusId) return
  // 前端预判流转合法性：同列排序恒允许，跨列须有规则
  const fromStatusId = moved.statusId
  const allowed = fromStatusId === toStatusId || transitionMap.value[fromStatusId]?.has(toStatusId) === true
  if (!allowed) {
    // 规则不允许：vuedraggable 已阻止放置并回弹，无需调用后端，仅重新拉取保证数据一致
    await load()
    return
  }
  // 取落点列绑定的数组（vuedraggable 已按拖拽结果就地改序），按数组下标定 before/after
  const { before, after } = dragNeighbors(event, findColumnIssues(renderGroups.value, toStatusId))
  try {
    await moveIssue(moved.id, {
      statusId: toStatusId,
      beforeId: before?.id,
      afterId: after?.id
    })
  } catch (e) {
    console.error('[onEnd] moveIssue failed', e)
  } finally {
    // 以服务端数据为准刷新看板：跨列移动与同列排序均实时生效，无需手动刷新
    await load()
  }
}

// 宽抽屉（60% 屏宽）承载云效风格详情页，点击卡片原地查看，不跳转路由
const detailDrawer = ref<InstanceType<typeof IssueDetailDrawer> | null>(null)

function openIssue(issue: Issue) {
  detailDrawer.value?.open(issue.id)
}

async function onNewIssue(col?: GroupColumnView) {
  // 顶部"新建"未指定列时，取看板第一个状态列作为默认状态，避免 statusId 为空导致创建失败
  const statusId = col?.statusId || board.value?.groups?.[0]?.columns?.[0]?.statusId
  createPayload.value = { statusId: statusId || '' }
  createOpen.value = true
}

const createOpen = ref(false)
const createPayload = ref<{ statusId: string }>({ statusId: '' })

function onCreated() {
  load()
}

function back() {
  router.push('/projects')
}
function toDashboard() {
  router.push(`/projects/${projectId.value}/metrics`)
}
function switchView(v: 'board' | 'table') {
  // 记忆视图选择：从项目列表再次进入时恢复
  localStorage.setItem(`kanban:lastView:${projectId.value}`, v)
  router.push(`/projects/${projectId.value}/${v}`)
}
function onViewChange(v: string) {
  switchView(v as 'board' | 'table')
}
function onScopeChange(v: string) {
  filter.scope = v
  applyFilter()
}

onMounted(() => {
  refreshPermission()
  load()
})
</script>

<template>
  <SimpleListView
    :title="boardName"
    :icon="pageIcon"
    show-back
    :search-placeholder="`搜索${itemLabel}`"
    :can-create="canWrite"
    create-label="新建"
    :total="totalCount"
    :nav-groups="navGroups"
    :scope="filter.scope"
    :view-tabs="viewTabs"
    view="board"
    v-model:keyword="filter.keyword"
    @update:scope="onScopeChange"
    @update:view="onViewChange"
    @search="applyFilter"
    @refresh="load"
    @create="() => onNewIssue()"
    @back="back"
  >
    <template #header-extra>
      <button class="btn-ghost" @click="toDashboard"><BarChart3 :size="15" /> 报表</button>
    </template>

    <!-- 高级筛选：优先级 / 严重度(仅缺陷) / 标签，保留原筛选条能力 -->
    <div class="glass-bar flex flex-wrap items-center gap-2 border-b px-4 py-2">
      <select
        v-model="filter.priority"
        class="rounded-lg border border-surface-muted px-2 py-1.5 text-sm outline-none focus:border-brand"
        @change="applyFilter"
      >
        <option value="">优先级</option>
        <option v-for="p in PRIORITY_OPTIONS" :key="p.k" :value="p.k">{{ p.label }}</option>
      </select>
      <select
        v-if="typeKey === 'defect'"
        v-model="filter.severity"
        class="rounded-lg border border-surface-muted px-2 py-1.5 text-sm outline-none focus:border-brand"
        @change="applyFilter"
      >
        <option value="">严重度</option>
        <option v-for="s in SEVERITY_OPTIONS" :key="s.k" :value="s.k">{{ s.label }}</option>
      </select>
      <select
        v-model="filter.labelId"
        class="rounded-lg border border-surface-muted px-2 py-1.5 text-sm outline-none focus:border-brand"
        @change="applyFilter"
      >
        <option value="">标签</option>
        <option v-for="t in allTags" :key="t.id" :value="t.id">{{ t.name }}</option>
      </select>
    </div>

    <div class="flex flex-1 flex-col gap-4 p-4">
        <div v-for="g in renderGroups" :key="g.groupKey" class="glass-panel flex flex-1 flex-col rounded-xl p-3">
          <div class="mb-2 flex items-center gap-2 px-1">
            <span class="text-sm font-semibold text-ink-900">{{ g.groupName }}</span>
            <span class="rounded-full bg-surface-muted px-2 py-0.5 text-xs text-ink-400">{{ g.issueCount }}</span>
          </div>
          <div class="flex flex-1 items-stretch gap-4 overflow-x-auto pb-1">
            <div
              v-for="col in g.columns"
              :key="col.statusId"
              class="kanban-col flex h-full w-[280px] shrink-0 flex-col rounded-xl transition-colors"
              :class="{
                'kanban-col-allow': dragOverCol?.statusId === col.statusId && dragOverCol?.allowed,
                'kanban-col-deny': dragOverCol?.statusId === col.statusId && !dragOverCol?.allowed
              }"
            >
              <div class="flex items-center gap-2 px-3 py-3">
                <span class="h-2.5 w-2.5 rounded-full" :style="{ background: col.color || 'var(--ink-secondary)' }" />
                <span class="text-sm font-medium text-ink-900">{{ col.name }}</span>
                <span class="text-xs text-ink-400">{{ col.issues.length }}</span>
                <button v-if="canWrite" class="ml-auto text-ink-400 hover:text-brand" @click="onNewIssue(col)">
                  <Plus :size="16" />
                </button>
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
                  <div @click="openIssue(element)">
                    <IssueCard :issue="element" />
                  </div>
                </template>
              </draggable>
            </div>
          </div>
        </div>
        <div v-if="loading" class="w-full pt-10 text-center text-sm text-ink-400">加载看板中…</div>
        <div v-if="!loading && !totalCount" class="w-full pt-10 text-center text-sm text-ink-400">暂无任务</div>
    </div>

    <template #footer>
      <!-- 云效风格缺陷详情：宽抽屉（60% 屏宽），不跳转路由 -->
      <IssueDetailDrawer ref="detailDrawer" :project-id="projectId" @closed="load" />

      <!-- 新建工作项：富文本弹窗 -->
      <CreateIssueDialog
        v-model="createOpen"
        :project-id="projectId"
        title="新建工作项"
        :default-issue-type-code="TYPE_CODE_MAP[typeKey]?.[0] || 'TASK'"
        :default-status-id="createPayload.statusId"
        @created="onCreated"
      />
    </template>
  </SimpleListView>
</template>
