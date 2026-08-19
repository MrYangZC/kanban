<script setup lang="ts">
import { onMounted, ref, reactive, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  filterIssues, listIssueChildren, updateIssue, getBoardGrouped,
  listProjectTags, listProjectMembers, createIssue, listUsers
} from '@/api'
import type { Issue, LabelVO, ProjectMember, UserVO } from '@/api/types'
import IssueDetailDrawer from '@/components/IssueDetailDrawer.vue'
import BoardFilterBar, { type FilterPayload } from '@/components/BoardFilterBar.vue'
import { createBoardFilter } from '@/stores/boardFilter'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { ChevronUp, ChevronDown, Plus } from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const projectId = route.params.projectId as string

const boardName = ref('')
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(20)
const tableData = ref<Issue[]>([])
const allTags = ref<LabelVO[]>([])
const members = ref<ProjectMember[]>([])
const users = ref<UserVO[]>([])
const filter = createBoardFilter()
const canWrite = ref(true)

// 状态列（来自看板分组），用于行内编辑下拉
const statusOptions = ref<{ id: string; name: string; color: string }[]>([])
const sortField = ref('')
const sortOrder = ref<'asc' | 'desc'>('asc')

const detailDrawer = ref<InstanceType<typeof IssueDetailDrawer> | null>(null)
const expandChildren = reactive<Record<string, Issue[]>>({})

const priorityColor: Record<string, string> = {
  P0: '#E34D59', P1: '#E37318', P2: '#2A4FD0', P3: '#8592A6'
}
const severityColor: Record<string, string> = {
  S1: '#E34D59', S2: '#E37318', S3: '#2BA471', S4: '#8592A6'
}
const priorityLabel: Record<string, string> = {
  P0: '紧急', P1: '高', P2: '中', P3: '低'
}
const severityLabel: Record<string, string> = {
  S1: '致命', S2: '严重', S3: '一般', S4: '轻微'
}

async function loadMeta() {
  const board = await getBoardGrouped(projectId, 'STATUS')
  boardName.value = board.name || '任务看板'
  statusOptions.value = board.groups.flatMap((g) => g.columns).map((c) => ({
    id: c.statusId, name: c.name, color: c.color
  }))
  allTags.value = await listProjectTags(projectId).catch(() => [])
  members.value = await listProjectMembers(projectId).catch(() => [])
  users.value = await listUsers().catch(() => [])
  const me = members.value.find((m) => m.userId === userStore.user?.id)
  canWrite.value = !me || ['OWNER', 'ADMIN', 'PM', 'DEV', 'QA', 'MEMBER'].some((r) => me.roles.includes(r))
}

async function load() {
  loading.value = true
  try {
    const res = await filterIssues({
      projectId,
      scope: filter.scope || undefined,
      keyword: filter.keyword || undefined,
      priority: filter.priority || undefined,
      severity: filter.severity || undefined,
      statusId: filter.statusId || undefined,
      labelId: filter.labelId || undefined,
      page: page.value,
      size: size.value,
      sortField: sortField.value || undefined,
      sortOrder: sortOrder.value
    })
    tableData.value = res.list
    total.value = res.total
  } finally {
    loading.value = false
  }
}

async function onSortChange({ prop, order }: { prop: string; order: string | null }) {
  if (!order) {
    sortField.value = ''
  } else {
    sortField.value = prop
    sortOrder.value = order === 'ascending' ? 'asc' : 'desc'
  }
  await load()
}

// 行内编辑
async function patchIssue(issue: Issue, payload: Record<string, unknown>, field: string) {
  if (!canWrite.value) {
    ElMessage.warning('当前角色无编辑权限')
    await load()
    return
  }
  try {
    const updated = await updateIssue(issue.id, payload)
    Object.assign(issue, updated)
    ElMessage.success(`${field}已更新`)
  } catch {
    await load()
  }
}

async function toggleChildren(row: Issue) {
  if (expandChildren[row.id]) {
    delete expandChildren[row.id]
    return
  }
  const children = await listIssueChildren(row.id)
  expandChildren[row.id] = children
}

const statusName = (id: string) => statusOptions.value.find((s) => s.id === id)?.name || '—'
const statusColor = (id: string) => statusOptions.value.find((s) => s.id === id)?.color || '#8592A6'
const memberName = (id: string | null) => members.value.find((m) => m.userId === id)?.displayName || '—'

function openIssue(issue: Issue) {
  detailDrawer.value?.open(issue.id)
}

async function onNewIssue() {
  try {
    const title = window.prompt('任务标题')
    if (!title) return
    await createIssue({
      projectId,
      statusId: statusOptions.value[0]?.id || '',
      title,
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
  loadMeta().then(load)
})
</script>

<template>
  <div class="flex h-full flex-col">
    <BoardFilterBar
      :board-name="boardName"
      :tags="allTags"
      view="table"
      :total="total"
      :can-write="canWrite"
      v-model="filter"
      @change="() => { page = 1; load() }"
      @switch-view="switchView"
      @new="onNewIssue"
      @dashboard="toDashboard"
      @back="back"
    />

    <div class="flex-1 overflow-auto bg-surface-base p-4">
      <div class="overflow-hidden rounded-xl bg-white ring-1 ring-black/5">
        <el-table
          :data="tableData"
          v-loading="loading"
          row-key="id"
          style="width: 100%"
          @sort-change="onSortChange"
        >
          <el-table-column type="expand">
            <template #default="{ row }">
              <div class="px-6 py-3">
                <button class="mb-2 text-xs text-brand hover:underline" @click="toggleChildren(row)">
                  {{ expandChildren[row.id] ? '收起子任务' : '查看子任务' }}
                </button>
                <div v-if="expandChildren[row.id]" class="space-y-1.5">
                  <div
                    v-for="c in expandChildren[row.id]"
                    :key="c.id"
                    class="flex items-center gap-3 rounded-lg bg-surface-muted/60 px-3 py-2 text-sm"
                    @click="openIssue(c)"
                  >
                    <span class="font-mono text-xs text-ink-400">{{ c.issueKey }}</span>
                    <span class="text-ink-900">{{ c.title }}</span>
                    <span class="ml-auto rounded px-1.5 py-0.5 text-xs" :style="{ background: priorityColor[c.priority] + '22', color: priorityColor[c.priority] }">{{ priorityLabel[c.priority] || c.priority }}</span>
                  </div>
                  <div v-if="!expandChildren[row.id].length" class="text-xs text-ink-400">暂无子任务</div>
                </div>
              </div>
            </template>
          </el-table-column>

          <el-table-column prop="issueKey" label="Key" width="120" sortable="custom">
            <template #default="{ row }">
              <span class="font-mono text-xs text-ink-500">{{ row.issueKey }}</span>
            </template>
          </el-table-column>

          <el-table-column prop="title" label="标题" min-width="240">
            <template #default="{ row }">
              <div class="flex items-center gap-2">
                <span class="text-base">{{ row.issueTypeIcon }}</span>
                <span class="cursor-pointer truncate text-ink-900 hover:text-brand" @click="openIssue(row)">{{ row.title }}</span>
              </div>
            </template>
          </el-table-column>

          <el-table-column prop="statusId" label="状态" width="150" sortable="custom">
            <template #default="{ row }">
              <el-select
                :model-value="row.statusId"
                size="small"
                :disabled="!canWrite"
                @change="(v: string) => patchIssue(row, { statusId: v }, '状态')"
              >
                <el-option v-for="s in statusOptions" :key="s.id" :value="s.id" :label="s.name">
                  <span class="mr-1 inline-block h-2 w-2 rounded-full" :style="{ background: s.color }" />{{ s.name }}
                </el-option>
              </el-select>
            </template>
          </el-table-column>

          <el-table-column prop="priority" label="优先级" width="110" sortable="custom">
            <template #default="{ row }">
              <el-select
                :model-value="row.priority"
                size="small"
                :disabled="!canWrite"
                @change="(v: string) => patchIssue(row, { priority: v }, '优先级')"
              >
                <el-option v-for="p in ['P0','P1','P2','P3']" :key="p" :value="p" :label="priorityLabel[p]" />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column prop="severity" label="严重度" width="100" sortable="custom">
            <template #default="{ row }">
              <span class="rounded px-1.5 py-0.5 text-xs font-medium" :style="{ background: severityColor[row.severity] + '22', color: severityColor[row.severity] }">{{ severityLabel[row.severity] || row.severity }}</span>
            </template>
          </el-table-column>

          <el-table-column prop="assigneeId" label="负责人" width="140">
            <template #default="{ row }">
              <el-select
                :model-value="row.assigneeId || ''"
                size="small"
                clearable
                :disabled="!canWrite"
                placeholder="未分配"
                @change="(v: string) => patchIssue(row, { assigneeId: v || null }, '负责人')"
              >
                <el-option v-for="m in members" :key="m.userId" :value="m.userId" :label="m.displayName" />
              </el-select>
            </template>
          </el-table-column>

          <el-table-column prop="reporterName" label="报告人" width="120">
            <template #default="{ row }">
              <span class="text-sm text-ink-600">{{ row.reporterName || '—' }}</span>
            </template>
          </el-table-column>

          <el-table-column label="标签" min-width="160">
            <template #default="{ row }">
              <span
                v-for="t in row.labels"
                :key="t.id"
                class="mr-1 rounded px-1.5 py-0.5 text-xs"
                :style="{ background: t.color + '22', color: t.color }"
              >{{ t.name }}</span>
            </template>
          </el-table-column>

          <el-table-column prop="updatedAt" label="更新时间" width="160" sortable="custom">
            <template #default="{ row }">
              <span class="text-xs text-ink-400">{{ row.updatedAt?.slice(0, 16)?.replace('T', ' ') || '—' }}</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="flex items-center justify-end px-4 py-3">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="size"
            :total="total"
            :page-sizes="[20, 50, 100]"
            layout="total, sizes, prev, pager, next"
            background
            @current-change="load"
            @size-change="() => { page = 1; load() }"
          />
        </div>
      </div>
    </div>

    <!-- 云效风格缺陷详情：宽抽屉（60% 屏宽），不跳转路由 -->
    <IssueDetailDrawer ref="detailDrawer" :project-id="projectId" @closed="load" />
  </div>
</template>
