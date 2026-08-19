<script setup lang="ts">
import { onMounted, ref, reactive, computed, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import draggable from 'vuedraggable'
import {
  filterIssues, updateIssue, getBoardGrouped, listProjectStatuses,
  listProjectTags, listProjectMembers, listUsers, getProject,
  createIssue, moveIssue, listIssueChildren, listTransitions
} from '@/api'
import type {
  Issue, LabelVO, ProjectMember, UserVO, IssueStatusVO,
  BoardGroupView, GroupColumnView, Project, PageResult, WorkflowTransitionVO
} from '@/api/types'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useUserStore } from '@/stores/user'
import IssueDetailDrawer from '@/components/IssueDetailDrawer.vue'
import TreeNode from '@/components/TreeNode.vue'
import {
  Plus, Search, List, GitBranch, LayoutGrid, ChevronDown,
  AlertTriangle, MoreHorizontal, ArrowLeft, RefreshCw, Save,
  FolderTree, Inbox, UserCheck, User, Share2, Archive
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const projectId = route.params.projectId as string

const PRIMARY = '#409EFF'
const project = ref<Project | null>(null)
const loading = ref(false)
const allTags = ref<LabelVO[]>([])
const members = ref<ProjectMember[]>([])
const users = ref<UserVO[]>([])
const statuses = ref<IssueStatusVO[]>([])
const canWrite = ref(true)

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
const dragOverCol = ref<{ statusId: string; allowed: boolean } | null>(null)

const priorityColor: Record<string, string> = {
  P0: '#F56C6C', P1: '#E6A23C', P2: '#409EFF', P3: '#909399'
}
const severityColor: Record<string, string> = {
  S1: '#F56C6C', S2: '#FA8C16', S3: '#409EFF', S4: '#909399'
}
const priorityLabel: Record<string, string> = {
  P0: '紧急', P1: '高', P2: '中', P3: '低'
}
const severityLabel: Record<string, string> = {
  S1: '1-致命', S2: '2-严重', S3: '3-一般', S4: '4-轻微'
}

// 工作流流转规则：from statusId → 允许的 to statusId 集合
// 唯一事实来源为后端 workflow_transition 表，前端不硬编码
const transitionMap = ref<Record<string, Set<string>>>({})
function buildTransitionMap(transitions: WorkflowTransitionVO[]): Record<string, Set<string>> {
  const map: Record<string, Set<string>> = {}
  for (const t of transitions) {
    if (!map[t.fromStatusId]) map[t.fromStatusId] = new Set()
    map[t.fromStatusId].add(t.toStatusId)
  }
  return map
}
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

const navGroups = computed(() => [
  {
    title: '个人',
    items: [
      { key: 'REPORTED', label: '我创建的', icon: User, count: 0 },
      { key: 'ASSIGNED', label: '我负责的', icon: UserCheck, count: 0 },
      { key: 'VERIFIED', label: '待我验证', icon: Inbox, count: 0 },
    ],
  },
  {
    title: '公共',
    items: [
      { key: '', label: '全部缺陷', icon: AlertTriangle, count: total.value },
      { key: 'SHARED', label: '共享到本项目', icon: Share2, count: 0 },
      { key: 'ARCHIVED', label: '已归档', icon: Archive, count: 0 },
    ],
  },
])

const renderGroups = computed(() => {
  if (!board.value) return []
  return board.value.groups.map((g) => ({
    ...g,
    columns: g.columns.map((c: GroupColumnView) => ({
      ...c,
      issues: c.issues.filter((i) => filteredIds.value.has(i.id)),
    })),
  }))
})

const treeData = computed(() => {
  const map = new Map<string, Issue & { children: any[] }>()
  const roots: any[] = []
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
  try { project.value = await getProject(projectId) } catch { /* ignore */ }
  try { statuses.value = await listProjectStatuses(projectId) } catch { /* ignore */ }
  try { allTags.value = await listProjectTags(projectId) } catch { /* ignore */ }
  try { members.value = await listProjectMembers(projectId) } catch { /* ignore */ }
  try { users.value = await listUsers() } catch { /* ignore */ }
  // 权限依据当前登录用户（与看板一致），不再硬编码 admin
  const me = members.value.find((m) => m.userId === userStore.user?.id)
  canWrite.value = !me || ['OWNER', 'ADMIN', 'PM', 'DEV', 'QA', 'MEMBER'].some((r) => me.roles.includes(r))
}

/** 列表状态下拉：当前状态 + 工作流可达状态，与看板拖拽规则一致 */
function statusOptionsFor(fromStatusId: string): IssueStatusVO[] {
  const reachable = transitionMap.value[fromStatusId]
  if (!reachable || !reachable.size) return statuses.value
  return statuses.value.filter((s) => s.id === fromStatusId || reachable.has(s.id))
}

async function loadList() {
  loading.value = true
  try {
    const res = await filterIssues({
      projectId,
      scope: scope.value || undefined,
      keyword: keyword.value || undefined,
      page: page.value,
      size: size.value,
    })
    tableData.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function loadAllIssues() {
  const res = await filterIssues({ projectId, scope: scope.value || undefined, keyword: keyword.value || undefined, page: 1, size: 1000 })
  allIssues.value = res.list
  total.value = res.total
}

async function loadBoard() {
  loading.value = true
  try {
    const [boardRes, transitions] = await Promise.all([
      getBoardGrouped(projectId, 'STATUS'),
      listTransitions(projectId).catch(() => [] as WorkflowTransitionVO[]),
    ])
    board.value = boardRes
    transitionMap.value = buildTransitionMap(transitions)
    const issues = await filterIssues({
      projectId,
      scope: scope.value || undefined,
      keyword: keyword.value || undefined,
      size: 1000,
    })
    filteredIds.value = new Set(issues.list.map((i: Issue) => i.id))
  } finally {
    loading.value = false
  }
}

async function load() {
  if (view.value === 'list') await loadList()
  else if (view.value === 'tree') await loadAllIssues()
  else await loadBoard()
}

function setScope(s: string) {
  scope.value = s
  localStorage.setItem(`kanban:defectScope:${projectId}`, s)
  page.value = 1
  load()
}

function setView(v: ViewMode) {
  view.value = v
  localStorage.setItem(`kanban:defectView:${projectId}`, v)
  load()
}

function statusName(id: string) { return statusNameMap.value[id] || '—' }
function statusColor(id: string) {
  const s = statuses.value.find((x) => x.id === id)
  return s?.color || PRIMARY
}
function memberName(id: string | null) { return members.value.find((m) => m.userId === id)?.displayName || '—' }
function userName(id: string | null) { return users.value.find((u) => u.id === id)?.displayName || '—' }
function fmtDate(t?: string) { return t ? t.slice(0, 10) : '—' }
function avatarChar(name: string | null) { return name ? name.charAt(0) : '·' }
function avatarColor(name: string | null) {
  if (!name) return '#C0C4CC'
  const cs = ['#409EFF', '#67C23A', '#E6A23C', '#F56C6C', '#909399', '#9B59B6']
  return cs[name.charCodeAt(0) % cs.length]
}

async function patchStatus(issue: Issue, statusId: string) {
  if (!canWrite.value) { ElMessage.warning('无编辑权限'); return }
  try {
    const updated = await updateIssue(issue.id, { statusId })
    Object.assign(issue, updated)
    ElMessage.success('状态已更新')
  } catch { await load() }
}

// 云效风格缺陷详情：宽抽屉（60% 屏宽），不跳转路由
const detailDrawer = ref<InstanceType<typeof IssueDetailDrawer> | null>(null)

function openDetail(issue: Issue) {
  detailDrawer.value?.open(issue.id)
}

async function onNewIssue() {
  try {
    const { value } = await ElMessageBox.prompt('缺陷标题', '新建缺陷', {
      inputPlaceholder: '例如：监测管理--雨量站异常值',
    })
    await createIssue({
      projectId,
      statusId: statuses.value[0]?.id || '',
      title: value,
      priority: 'P2',
      severity: 'S3',
    })
    await load()
    ElMessage.success('已创建')
  } catch { /* cancel */ }
}

function onMove(evt: any): boolean {
  const fromId = evt.from?.dataset?.statusId
  const toId = evt.to?.dataset?.statusId
  if (!fromId || !toId) return true
  // 同列排序恒允许；跨列须存在后端工作流规则
  const allowed = fromId === toId || transitionMap.value[fromId]?.has(toId) === true
  if (dragOverCol.value?.statusId !== toId || dragOverCol.value?.allowed !== allowed) {
    dragOverCol.value = { statusId: toId, allowed }
  }
  return allowed
}

async function onEnd(event: any) {
  dragOverCol.value = null
  const moved = event?.item?.__draggable_context?.element as Issue | undefined
  if (!moved) return
  const toStatusId = event?.to?.dataset?.statusId
  if (!toStatusId) return
  // 前端预判流转合法性：同列排序恒允许，跨列须有规则
  const fromStatusId = moved.statusId
  const allowed = fromStatusId === toStatusId || transitionMap.value[fromStatusId]?.has(toStatusId) === true
  if (!allowed) {
    await loadBoard()
    return
  }
  const before = event.item.previousElementSibling?.__draggable_context?.element as Issue | undefined
  const after = event.item.nextElementSibling?.__draggable_context?.element as Issue | undefined
  try {
    await moveIssue(moved.id, { statusId: toStatusId, beforeId: before?.id, afterId: after?.id })
  } catch { /* ignore */ } finally {
    await loadBoard()
  }
}

function back() { router.push('/projects') }

const viewTabs: { k: ViewMode; label: string; icon: any }[] = [
  { k: 'list', label: '列表', icon: List },
  { k: 'tree', label: '树形', icon: GitBranch },
  { k: 'board', label: '看板', icon: LayoutGrid },
]

onMounted(() => {
  // 恢复上次使用的视图与左侧范围，避免刷新后丢失页面状态
  const savedView = localStorage.getItem(`kanban:defectView:${projectId}`)
  if (savedView === 'list' || savedView === 'tree' || savedView === 'board') view.value = savedView
  const savedScope = localStorage.getItem(`kanban:defectScope:${projectId}`)
  if (savedScope) scope.value = savedScope
  loadMeta().then(load)
})
</script>

<template>
  <div class="flex h-full flex-col" style="background:#f5f7fa">
    <!-- 顶部工具栏 -->
    <header class="flex h-14 shrink-0 items-center gap-3 border-b bg-white px-4" style="border-color:#e4e7ed">
      <button class="flex items-center gap-1 text-sm hover:opacity-80" style="color:#606266" @click="back">
        <ArrowLeft :size="16" />
      </button>
      <h1 class="text-base font-semibold" style="color:#303133">缺陷</h1>
      <span class="rounded-full px-2 py-0.5 text-xs" style="background:#f0f2f5;color:#909399">{{ total }}</span>

      <div class="ml-4 flex items-center gap-1 rounded-lg border p-0.5" style="border-color:#dcdfe6">
        <button
          v-for="t in viewTabs"
          :key="t.k"
          class="flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm transition"
          :style="view === t.k ? { background: PRIMARY, color: '#fff' } : { color: '#606266' }"
          @click="setView(t.k)"
        >
          <component :is="t.icon" :size="14" /> {{ t.label }}
        </button>
      </div>

      <div class="ml-auto flex items-center gap-3">
        <div class="flex items-center gap-2 rounded-lg px-2.5 py-1.5" style="background:#f5f7fa">
          <Search :size="15" style="color:#909399" />
          <input
            v-model="keyword"
            placeholder="搜索缺陷"
            class="w-44 bg-transparent text-sm outline-none"
            @keyup.enter="() => { page = 1; load() }"
          />
        </div>
        <button
          class="flex items-center gap-1 rounded-lg px-4 py-1.5 text-sm text-white hover:opacity-90"
          :style="{ background: PRIMARY }"
          @click="onNewIssue"
        >
          <Plus :size="16" /> 新建
        </button>
      </div>
    </header>

    <div class="flex flex-1 overflow-hidden">
      <!-- 左侧导航 -->
      <aside class="flex w-[200px] shrink-0 flex-col overflow-y-auto border-r bg-white px-2 py-3" style="border-color:#e4e7ed">
        <div v-for="grp in navGroups" :key="grp.title" class="mb-4">
          <div class="mb-1.5 px-2 text-xs font-medium" style="color:#909399">{{ grp.title }}</div>
          <button
            v-for="item in grp.items"
            :key="item.key"
            class="flex w-full items-center gap-2 rounded-lg px-2 py-2 text-sm transition"
            :style="scope === item.key ? { background: '#e6f7ff', color: PRIMARY } : { color: '#606266' }"
            @click="setScope(item.key)"
          >
            <component :is="item.icon" :size="15" />
            <span class="flex-1 text-left">{{ item.label }}</span>
            <span class="text-xs" style="color:#c0c4cc">{{ item.count }}</span>
          </button>
        </div>
      </aside>

      <!-- 主内容区 -->
      <main class="flex-1 overflow-hidden">
        <!-- 列表视图 -->
        <div v-if="view === 'list'" class="flex h-full flex-col">
          <div class="flex-1 overflow-auto bg-white">
            <table class="w-full text-sm">
              <thead class="sticky top-0 z-10" style="background:#fafafa">
                <tr style="border-bottom:1px solid #e4e7ed">
                  <th class="px-4 py-3 text-left font-medium" style="color:#909399">标题</th>
                  <th class="px-3 py-3 text-left font-medium" style="color:#909399">状态</th>
                  <th class="px-3 py-3 text-left font-medium" style="color:#909399">优先级</th>
                  <th class="px-3 py-3 text-left font-medium" style="color:#909399">标签</th>
                  <th class="px-3 py-3 text-left font-medium" style="color:#909399">负责人</th>
                  <th class="px-3 py-3 text-left font-medium" style="color:#909399">创建者</th>
                  <th class="px-3 py-3 text-left font-medium" style="color:#909399">创建时间</th>
                  <th class="px-3 py-3 text-left font-medium" style="color:#909399">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="issue in tableData"
                  :key="issue.id"
                  class="cursor-pointer transition hover:bg-[#f5f7fa]"
                  style="border-bottom:1px solid #f0f0f0"
                  @click="openDetail(issue)"
                >
                  <td class="px-4 py-3">
                    <div class="flex items-center gap-2">
                      <AlertTriangle :size="14" class="shrink-0" style="color:#F56C6C" />
                      <span class="font-mono text-xs" style="color:#c0c4cc">{{ issue.issueKey }}</span>
                      <span class="truncate" style="color:#303133">{{ issue.title }}</span>
                    </div>
                  </td>
                  <td class="px-3 py-3" @click.stop>
                    <select
                      :value="issue.statusId"
                      class="cursor-pointer rounded border-none px-2 py-1 text-xs outline-none"
                      :style="{ background: statusColor(issue.statusId) + '1A', color: statusColor(issue.statusId) }"
                      :disabled="!canWrite"
                      @change="(e: any) => patchStatus(issue, e.target.value)"
                    >
                      <option v-for="s in statusOptionsFor(issue.statusId)" :key="s.id" :value="s.id">{{ s.name }}</option>
                    </select>
                  </td>
                  <td class="px-3 py-3">
                    <span
                      class="rounded px-1.5 py-0.5 text-xs font-medium text-white"
                      :style="{ background: priorityColor[issue.priority] || '#909399' }"
                    >{{ priorityLabel[issue.priority] || issue.priority }}</span>
                  </td>
                  <td class="px-3 py-3">
                    <div class="flex max-w-[180px] flex-wrap gap-1">
                      <span
                        v-for="t in issue.labels"
                        :key="t.id"
                        class="rounded px-1.5 py-0.5 text-xs"
                        :style="{ background: (t.color || PRIMARY) + '1A', color: t.color || PRIMARY }"
                      >{{ t.name }}</span>
                      <span v-if="!issue.labels?.length" class="text-xs" style="color:#c0c4cc">—</span>
                    </div>
                  </td>
                  <td class="px-3 py-3">
                    <div class="flex items-center gap-1.5">
                      <span
                        class="flex h-6 w-6 items-center justify-center rounded-full text-xs font-medium text-white"
                        :style="{ background: avatarColor(issue.assigneeName) }"
                      >{{ avatarChar(issue.assigneeName) }}</span>
                      <span style="color:#606266">{{ memberName(issue.assigneeId) }}</span>
                    </div>
                  </td>
                  <td class="px-3 py-3">
                    <div class="flex items-center gap-1.5">
                      <span
                        class="flex h-6 w-6 items-center justify-center rounded-full text-xs font-medium text-white"
                        :style="{ background: avatarColor(issue.reporterName) }"
                      >{{ avatarChar(issue.reporterName) }}</span>
                      <span style="color:#606266">{{ userName(issue.reporterId) }}</span>
                    </div>
                  </td>
                  <td class="px-3 py-3 text-xs" style="color:#909399">{{ fmtDate(issue.createdAt) }}</td>
                  <td class="px-3 py-3" @click.stop>
                    <button class="hover:opacity-70" style="color:#c0c4cc"><MoreHorizontal :size="16" /></button>
                  </td>
                </tr>
                <tr v-if="!loading && !tableData.length">
                  <td colspan="8" class="py-12 text-center" style="color:#c0c4cc">暂无缺陷</td>
                </tr>
              </tbody>
            </table>
          </div>
          <div class="flex items-center justify-between border-t px-4 py-3" style="border-color:#e4e7ed;background:#fff">
            <span class="text-xs" style="color:#909399">共 {{ total }} 条</span>
            <el-pagination
              v-model:current-page="page"
              v-model:page-size="size"
              :total="total"
              :page-sizes="[25, 50, 100]"
              layout="prev, pager, next, sizes"
              background
              @current-change="loadList"
              @size-change="() => { page = 1; loadList() }"
            />
          </div>
        </div>

        <!-- 树形视图 -->
        <div v-else-if="view === 'tree'" class="h-full overflow-auto bg-white p-4">
          <div v-if="!treeData.length" class="py-12 text-center text-sm" style="color:#c0c4cc">暂无缺陷</div>
          <TreeNode
            v-for="node in treeData"
            :key="node.id"
            :node="node"
            :status-name="statusName"
            :status-color="statusColor"
            :priority-color="priorityColor"
            @open="openDetail"
          />
        </div>

        <!-- 看板视图 -->
        <div v-else class="h-full overflow-auto p-4">
          <div v-for="g in renderGroups" :key="g.groupKey" class="mb-4 flex h-full flex-col rounded-xl bg-white/60 p-3 ring-1 ring-black/5">
            <div class="mb-2 flex items-center gap-2 px-1">
              <span class="text-sm font-semibold" style="color:#303133">{{ g.groupName }}</span>
              <span class="rounded-full px-2 py-0.5 text-xs" style="background:#f0f2f5;color:#909399">{{ g.issueCount }}</span>
            </div>
            <div class="flex flex-1 items-stretch gap-4 overflow-x-auto pb-1">
              <div
                v-for="col in g.columns"
                :key="col.statusId"
                class="kanban-col flex h-full w-[280px] shrink-0 flex-col rounded-xl bg-[#f5f7fa] transition-colors"
                :class="{
                  'kanban-col-allow': dragOverCol?.statusId === col.statusId && dragOverCol?.allowed,
                  'kanban-col-deny': dragOverCol?.statusId === col.statusId && !dragOverCol?.allowed,
                }"
              >
                <div class="flex items-center gap-2 px-3 py-3">
                  <span class="h-2.5 w-2.5 rounded-full" :style="{ background: col.color || '#909399' }" />
                  <span class="text-sm font-medium" style="color:#303133">{{ col.name }}</span>
                  <span class="text-xs" style="color:#909399">{{ col.issues.length }}</span>
                </div>
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
                  :force-fallback="true"
                  :fallback-tolerance="8"
                  @move="onMove"
                  @end="onEnd"
                >
                  <template #item="{ element }">
                    <div
                      class="cursor-pointer rounded-lg bg-white p-3 shadow-sm ring-1 ring-black/5 transition hover:shadow-md"
                      @click="openDetail(element)"
                    >
                      <div class="mb-1.5 flex items-center gap-2">
                        <span class="h-1.5 w-1.5 rounded-full" :style="{ background: priorityColor[element.priority] || '#909399' }" />
                        <span class="font-mono text-xs" style="color:#909399">{{ element.issueKey }}</span>
                      </div>
                      <div class="line-clamp-2 text-sm leading-snug" style="color:#303133">{{ element.title }}</div>
                      <div v-if="element.labels?.length" class="mt-2 flex flex-wrap gap-1">
                        <span
                          v-for="t in element.labels"
                          :key="t.id"
                          class="rounded px-1.5 py-0.5 text-[10px]"
                          :style="{ background: (t.color || PRIMARY) + '1A', color: t.color || PRIMARY }"
                        >{{ t.name }}</span>
                      </div>
                      <div class="mt-2 flex items-center justify-between">
                        <span
                          class="rounded px-1.5 py-0.5 text-[10px] font-medium text-white"
                          :style="{ background: priorityColor[element.priority] || '#909399' }"
                        >{{ priorityLabel[element.priority] || element.priority }}</span>
                        <span class="flex min-w-0 items-center gap-1.5">
                          <span
                            v-if="element.assigneeName"
                            class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-[11px] font-medium text-white"
                            :style="{ background: avatarColor(element.assigneeName) }"
                          >{{ avatarChar(element.assigneeName) }}</span>
                          <span
                            v-if="element.assigneeName"
                            class="max-w-[100px] truncate text-[11px]"
                            style="color:#606266"
                            :title="element.assigneeName"
                          >{{ element.assigneeName }}</span>
                          <span v-else class="text-[11px]" style="color:#c0c4cc">未指派</span>
                        </span>
                      </div>
                    </div>
                  </template>
                </draggable>
              </div>
            </div>
          </div>
        </div>
      </main>
    </div>

    <!-- 云效风格缺陷详情：宽抽屉（60% 屏宽），不跳转路由 -->
    <IssueDetailDrawer ref="detailDrawer" :project-id="projectId" @closed="load" />
  </div>
</template>