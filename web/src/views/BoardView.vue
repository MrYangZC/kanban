<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import draggable from 'vuedraggable'
import { getBoardGrouped, createIssue, moveIssue, filterIssues, listProjectTags, listProjectMembers, listTransitions } from '@/api'
import { Plus } from 'lucide-vue-next'
import type { BoardGroupView, Issue, GroupColumnView, LabelVO, ProjectMember, WorkflowTransitionVO } from '@/api/types'
import IssueCard from '@/components/IssueCard.vue'
import BoardFilterBar, { type FilterPayload } from '@/components/BoardFilterBar.vue'
import IssueDetailDrawer from '@/components/IssueDetailDrawer.vue'
import { createBoardFilter } from '@/stores/boardFilter'
import { useUserStore } from '@/stores/user'
import { ElMessageBox, ElMessage } from 'element-plus'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const projectId = route.params.projectId as string

const board = ref<BoardGroupView | null>(null)
const boardName = ref('')
const loading = ref(false)
const allTags = ref<LabelVO[]>([])
const filter = createBoardFilter()
const filteredIds = ref<Set<string>>(new Set())
const canWrite = ref(true)

async function loadPermission() {
  try {
    const members = await listProjectMembers(projectId)
    const me = (members as ProjectMember[]).find((m) => m.userId === userStore.user?.id)
    canWrite.value = !me || ['OWNER', 'ADMIN', 'PM', 'DEV', 'QA', 'MEMBER'].some((r) => me.roles.includes(r))
  } catch {
    canWrite.value = true
  }
}

async function load() {
  loading.value = true
  try {
    // 并行拉取看板数据与流转规则；规则接口异常时不阻塞看板渲染
    const [boardRes, transitions] = await Promise.all([
      getBoardGrouped(projectId, 'STATUS'),
      listTransitions(projectId).catch(() => [] as WorkflowTransitionVO[])
    ])
    board.value = boardRes
    boardName.value = boardRes.name || '任务看板'
    transitionMap.value = buildTransitionMap(transitions)
    allTags.value = await listProjectTags(projectId).catch(() => [])
    await applyFilter()
  } finally {
    loading.value = false
  }
}

async function applyFilter() {
  const issues = await filterIssues({
    projectId,
    scope: filter.scope || undefined,
    keyword: filter.keyword || undefined,
    priority: filter.priority || undefined,
    severity: filter.severity || undefined,
    statusId: filter.statusId || undefined,
    labelId: filter.labelId || undefined,
    // 看板需要全量分组渲染，不能走默认分页（否则只看得到前 20 条）
    size: 1000
  })
  filteredIds.value = new Set(issues.list.map((i: Issue) => i.id))
}

const renderGroups = computed(() => {
  if (!board.value) return []
  return board.value.groups.map((g) => ({
    ...g,
    columns: g.columns.map((c: GroupColumnView) => ({
      ...c,
      issues: c.issues.filter((i) => filteredIds.value.has(i.id))
    }))
  }))
})

const totalCount = computed(() =>
  renderGroups.value.reduce((s, g) => s + g.columns.reduce((cs, c) => cs + c.issues.length, 0), 0)
)

// 工作流流转规则：from statusId → 允许的 to statusId 集合
// 数据来源为后端 workflow_transition 表（唯一事实来源），前端不再硬编码
const transitionMap = ref<Record<string, Set<string>>>({})

function buildTransitionMap(transitions: WorkflowTransitionVO[]): Record<string, Set<string>> {
  const map: Record<string, Set<string>> = {}
  for (const t of transitions) {
    if (!map[t.fromStatusId]) map[t.fromStatusId] = new Set()
    map[t.fromStatusId].add(t.toStatusId)
  }
  return map
}

const dragOverCol = ref<{ statusId: string; allowed: boolean } | null>(null)

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
  // 被拖卡片（vuedraggable 渲染时注入，指向真实 Issue 对象）
  const moved = event?.item?.__draggable_context?.element as Issue | undefined
  if (!moved) return
  // 落点列状态（data-status-id 透传到拖拽根元素上）
  const toStatusId = event?.to?.dataset?.statusId
  if (!toStatusId) return
  // 前端预判流转合法性：同列排序恒允许，跨列须有规则
  const fromStatusId = moved.statusId
  const allowed = fromStatusId === toStatusId || transitionMap.value[fromStatusId]?.has(toStatusId) === true
  if (!allowed) {
    // 规则不允许：vuedraggable 已阻止放置并回弹，无需调用后端，仅重新拉取保证数据一致
    await load()
    return
  }
  // 从被拖 DOM 的前后邻居取 before/after，不依赖列表下标，兼容过滤场景
  const before = event.item.previousElementSibling?.__draggable_context?.element as Issue | undefined
  const after = event.item.nextElementSibling?.__draggable_context?.element as Issue | undefined
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
  try {
    const { value } = await ElMessageBox.prompt('任务标题', `新建 · ${col?.name || '任务'}`, {
      inputPlaceholder: '例如：优化看板拖拽体验'
    })
    await createIssue({
      projectId,
      statusId: statusId || '',
      title: value,
      priority: 'P2',
      severity: 'S3'
    })
    await load()
    ElMessage.success('已创建')
  } catch {
    /* 取消 */
  }
}

function back() {
  router.push('/projects')
}
function toDashboard() {
  router.push(`/projects/${projectId}/metrics`)
}
function switchView(v: 'board' | 'table' | 'defects') {
  // 记忆视图选择：从项目列表再次进入时恢复
  localStorage.setItem(`kanban:lastView:${projectId}`, v)
  if (v === 'defects') {
    router.push(`/projects/${projectId}/defects`)
    return
  }
  router.push(`/projects/${projectId}/${v}`)
}

onMounted(() => {
  loadPermission()
  load()
})
</script>

<template>
  <div class="flex h-full flex-row">
    <!-- 左侧：看板内容区 -->
    <div class="flex min-w-0 flex-1 flex-col">
      <BoardFilterBar
        :board-name="boardName"
        :tags="allTags"
        view="board"
        :total="totalCount"
        :can-write="canWrite"
        v-model="filter"
        @change="applyFilter"
        @switch-view="switchView"
        @new="onNewIssue()"
        @dashboard="toDashboard"
        @back="back"
      />

      <div class="flex flex-1 flex-col gap-4 overflow-auto bg-surface-base p-4">
        <div v-for="g in renderGroups" :key="g.groupKey" class="flex flex-1 flex-col rounded-xl bg-white/60 p-3 ring-1 ring-black/5">
          <div class="mb-2 flex items-center gap-2 px-1">
            <span class="text-sm font-semibold text-ink-900">{{ g.groupName }}</span>
            <span class="rounded-full bg-surface-muted px-2 py-0.5 text-xs text-ink-400">{{ g.issueCount }}</span>
          </div>
          <div class="flex flex-1 items-stretch gap-4 overflow-x-auto pb-1">
            <div
              v-for="col in g.columns"
              :key="col.statusId"
              class="kanban-col flex h-full w-[300px] shrink-0 flex-col rounded-xl bg-surface-muted/60 transition-colors"
              :class="{
                'kanban-col-allow': dragOverCol?.statusId === col.statusId && dragOverCol?.allowed,
                'kanban-col-deny': dragOverCol?.statusId === col.statusId && !dragOverCol?.allowed
              }"
            >
              <div class="flex items-center gap-2 px-3 py-3">
                <span class="h-2.5 w-2.5 rounded-full" :style="{ background: col.color || '#8F959E' }" />
                <span class="text-sm font-medium text-ink-900">{{ col.name }}</span>
                <span class="text-xs text-ink-400">{{ col.issues.length }}</span>
                <button v-if="canWrite" class="ml-auto text-ink-400 hover:text-brand" @click="onNewIssue(col)">
                  <Plus :size="16" />
                </button>
              </div>
              <draggable
                :list="col.issues"
                group="issues"
                item-key="id"
                :data-status-id="col.statusId"
                class="flex-1 select-none space-y-2.5 overflow-y-auto px-3 pb-3"
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
    </div>

    <!-- 云效风格缺陷详情：宽抽屉（60% 屏宽），不跳转路由 -->
    <IssueDetailDrawer ref="detailDrawer" :project-id="projectId" @closed="load" />
  </div>
</template>
