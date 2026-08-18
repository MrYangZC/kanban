<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import draggable from 'vuedraggable'
import { getBoardGrouped, createIssue, moveIssue, filterIssues, listProjectTags, listProjectMembers } from '@/api'
import { Plus } from 'lucide-vue-next'
import type { BoardGroupView, Issue, GroupColumnView, LabelVO, ProjectMember } from '@/api/types'
import IssueCard from '@/components/IssueCard.vue'
import IssueDrawer from '@/components/IssueDrawer.vue'
import BoardFilterBar, { type FilterPayload } from '@/components/BoardFilterBar.vue'
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
const drawerOpen = ref(false)
const selectedIssue = ref<Issue | null>(null)
const allTags = ref<LabelVO[]>([])
const filter = createBoardFilter()
const filteredIds = ref<Set<string>>(new Set())
const canWrite = ref(true)

async function loadPermission() {
  try {
    const members = await listProjectMembers(projectId)
    const me = (members as ProjectMember[]).find((m) => m.userId === userStore.user?.id)
    canWrite.value = !me || me.role !== 'VIEWER'
  } catch {
    canWrite.value = true
  }
}

async function load() {
  loading.value = true
  try {
    board.value = await getBoardGrouped(projectId, 'STATUS')
    boardName.value = board.value.name || '任务看板'
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
    labelId: filter.labelId || undefined
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

async function onEnd(event: any) {
  // 被拖卡片（vuedraggable 渲染时注入，指向真实 Issue 对象）
  const moved = event?.item?.__draggable_context?.element as Issue | undefined
  if (!moved) return
  // 落点列状态（data-status-id 透传到拖拽根元素上）
  const toStatusId = event?.to?.dataset?.statusId
  if (!toStatusId) return
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

function openIssue(issue: Issue) {
  router.push(`/projects/${projectId}/defects/${issue.id}`)
}

async function onIssueUpdated(updated: Issue) {
  const prevStatus = selectedIssue.value?.statusId
  selectedIssue.value = updated
  if (!board.value) return
  if (prevStatus !== updated.statusId) {
    // 状态变化：重新拉取看板，使卡片移动到对应列并保持正确排序
    await load()
    return
  }
  // 状态未变：原位同步字段（标题/优先级等），无需整板刷新
  for (const g of board.value.groups) {
    for (const c of g.columns) {
      const item = c.issues.find((i) => i.id === updated.id)
      if (item) Object.assign(item, updated)
    }
  }
}

async function onNewIssue(col?: GroupColumnView) {
  const statusId = col?.statusId
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
  router.push(`/projects/${projectId}/dashboard`)
}
function switchView(v: 'board' | 'table' | 'defects') {
  router.push(`/projects/${projectId}/${v}`)
}

onMounted(() => {
  loadPermission()
  load()
})
</script>

<template>
  <div class="flex h-full flex-col">
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
      <div v-for="g in renderGroups" :key="g.groupKey" class="rounded-xl bg-white/60 p-3 ring-1 ring-black/5">
        <div class="mb-2 flex items-center gap-2 px-1">
          <span class="text-sm font-semibold text-ink-900">{{ g.groupName }}</span>
          <span class="rounded-full bg-surface-muted px-2 py-0.5 text-xs text-ink-400">{{ g.issueCount }}</span>
        </div>
        <div class="flex gap-4 overflow-x-auto pb-1">
          <div
            v-for="col in g.columns"
            :key="col.statusId"
            class="flex h-full w-[300px] shrink-0 flex-col rounded-xl bg-surface-muted/60"
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
              class="flex-1 space-y-2.5 overflow-y-auto px-3 pb-3"
              ghost-class="sortable-ghost"
              drag-class="sortable-drag"
              :animation="180"
              :disabled="!canWrite"
              :force-fallback="true"
              :fallback-tolerance="8"
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

    <IssueDrawer
      :issue="selectedIssue"
      :open="drawerOpen"
      :projectId="projectId"
      @close="drawerOpen = false"
      @updated="onIssueUpdated"
    />
  </div>
</template>
