<script setup lang="ts">
import { onMounted, ref, reactive, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  filterIssues, listIssueChildren, updateIssue, getBoardGrouped,
  listProjectTags, listProjectMembers, listUsers
} from '@/api'
import type { Issue, LabelVO, ProjectMember, UserVO } from '@/api/types'
import { TASK_TYPE_CODES, REQUIREMENT_TYPE_CODES, DEFECT_TYPE_CODES, categoryOfTypeCode } from '@/api/types'
import IssueDetailDrawer from '@/components/IssueDetailDrawer.vue'
import CreateIssueDialog from '@/components/CreateIssueDialog.vue'
import SimpleListView from '@/components/SimpleListView.vue'
import UserSelect from '@/components/common/UserSelect.vue'
import type { ViewTab } from '@/components/ViewTabs.vue'
import { useBoardFilter } from '@/stores/boardFilter'
import { ElMessage } from 'element-plus'
import { Archive, BarChart3, Bug, FileText, Inbox, LayoutGrid, ListTodo, Table2, User, UserCheck } from 'lucide-vue-next'
import { useProjectPermission } from '@/composables/useProjectPermission'
import { PRIORITY_COLOR as priorityColor, SEVERITY_COLOR as severityColor, PRIORITY_LABEL as priorityLabel, SEVERITY_LABEL as severityLabel } from '@/theme/colorMap'

const route = useRoute()
const router = useRouter()
const projectId = computed(() => route.params.projectId as string)

const boardName = ref('')
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const size = ref(25)
const tableData = ref<Issue[]>([])
const allTags = ref<LabelVO[]>([])
const members = ref<ProjectMember[]>([])
const users = ref<UserVO[]>([])
const filter = useBoardFilter(projectId.value)
const { hasPermission, refresh: refreshPermission } = useProjectPermission(() => projectId.value)
const canWrite = computed(() => hasPermission('issue:edit'))

const typeKey = computed(() => (route.query.type as string) || '')
const TYPE_LABEL: Record<string, string> = {
  task: '任务列表',
  requirement: '需求列表',
  defect: '缺陷列表'
}
const TYPE_CODE_MAP: Record<string, string[]> = {
  task: TASK_TYPE_CODES,
  requirement: REQUIREMENT_TYPE_CODES,
  defect: DEFECT_TYPE_CODES
}
const itemLabel = computed(() => {
  switch (typeKey.value) {
    case 'task': return '任务'
    case 'requirement': return '需求'
    case 'defect': return '缺陷'
    default: return '工作项'
  }
})

// 页面图标随工作项类型切换，与任务/需求/缺陷页保持一致
const pageIcon = computed(() => {
  switch (typeKey.value) {
    case 'task': return ListTodo
    case 'requirement': return FileText
    case 'defect': return Bug
    default: return LayoutGrid
  }
})

const defaultTypeCode = computed(() => TYPE_CODE_MAP[typeKey.value]?.[0] || '')

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
      { key: '', label: `全部${itemLabel.value}`, icon: pageIcon.value, count: total.value },
      { key: 'ARCHIVED', label: '已归档', icon: Archive }
    ]
  }
])

// 状态列（来自看板分组），用于行内编辑下拉
const statusOptions = ref<{ id: string; name: string; color: string }[]>([])
const sortField = ref('')
const sortOrder = ref<'asc' | 'desc'>('asc')

const detailDrawer = ref<InstanceType<typeof IssueDetailDrawer> | null>(null)
const createOpen = ref(false)
const expandChildren = reactive<Record<string, Issue[]>>({})



async function loadMeta() {
  // 状态列按当前页面的工作项类别取（任务/需求/缺陷是三套工作流节点）
  const board = await getBoardGrouped(projectId.value, 'STATUS', categoryOfTypeCode(defaultTypeCode.value))
  boardName.value = TYPE_LABEL[typeKey.value] || board.name || '任务列表'
  statusOptions.value = board.groups.flatMap((g) => g.columns).map((c) => ({
    id: c.statusId, name: c.name, color: c.color
  }))
  // 以下为筛选辅助数据，失败降级为空列表（接口层已统一提示错误）
  allTags.value = await listProjectTags(projectId.value).catch(() => [])
  members.value = await listProjectMembers(projectId.value).catch(() => [])
  users.value = await listUsers().catch(() => [])
  refreshPermission()
}

async function load() {
  loading.value = true
  try {
    const res = await filterIssues({
      projectId: projectId.value,
      scope: filter.scope || undefined,
      keyword: filter.keyword || undefined,
      priority: filter.priority || undefined,
      severity: filter.severity || undefined,
      statusId: filter.statusId || undefined,
      labelId: filter.labelId || undefined,
      issueTypeCodes: TYPE_CODE_MAP[typeKey.value]?.length ? TYPE_CODE_MAP[typeKey.value] : undefined,
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


const memberName = (id: string | null) => members.value.find((m) => m.userId === id)?.displayName || '—'

function openIssue(issue: Issue) {
  detailDrawer.value?.open(issue.id)
}

function onNewIssue() {
  createOpen.value = true
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
function onSearch() {
  page.value = 1
  load()
}
function onScopeChange(v: string) {
  filter.scope = v
  page.value = 1
  load()
}

onMounted(() => {
  loadMeta().then(load)
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
    :loading="loading"
    :total="total"
    :is-empty="!tableData.length"
    :empty-text="`暂无${itemLabel}`"
    :nav-groups="navGroups"
    :scope="filter.scope"
    :view-tabs="viewTabs"
    view="table"
    v-model:page="page"
    v-model:size="size"
    show-pagination
    v-model:keyword="filter.keyword"
    @update:scope="onScopeChange"
    @update:view="onViewChange"
    @search="onSearch"
    @refresh="load"
    @create="onNewIssue"
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
        @change="onSearch"
      >
        <option value="">优先级</option>
        <option v-for="p in PRIORITY_OPTIONS" :key="p.k" :value="p.k">{{ p.label }}</option>
      </select>
      <select
        v-if="typeKey === 'defect'"
        v-model="filter.severity"
        class="rounded-lg border border-surface-muted px-2 py-1.5 text-sm outline-none focus:border-brand"
        @change="onSearch"
      >
        <option value="">严重度</option>
        <option v-for="s in SEVERITY_OPTIONS" :key="s.k" :value="s.k">{{ s.label }}</option>
      </select>
      <select
        v-model="filter.labelId"
        class="rounded-lg border border-surface-muted px-2 py-1.5 text-sm outline-none focus:border-brand"
        @change="onSearch"
      >
        <option value="">标签</option>
        <option v-for="t in allTags" :key="t.id" :value="t.id">{{ t.name }}</option>
      </select>
    </div>

    <div class="p-4">
      <div class="glass-panel overflow-hidden">
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

          <el-table-column prop="assigneeId" label="负责人" width="160">
            <template #default="{ row }">
              <UserSelect
                :model-value="row.assigneeId"
                :users="members"
                empty-label="未分配"
                clearable
                width-class="w-full"
                :disabled="!canWrite"
                @change="(v) => patchIssue(row, v ? { assigneeId: v } : { assigneeId: null, clearFields: ['assigneeId'] }, '负责人')"
              />
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

      </div>
    </div>

    <template #footer>
      <!-- 云效风格缺陷详情：宽抽屉（60% 屏宽），不跳转路由 -->
      <IssueDetailDrawer ref="detailDrawer" :project-id="projectId" @closed="load" />
      <CreateIssueDialog
        v-model="createOpen"
        :project-id="projectId"
        :title="`新建${itemLabel}`"
        :default-issue-type-code="defaultTypeCode"
        :default-status-id="statusOptions[0]?.id || ''"
        @created="load"
      />
    </template>
  </SimpleListView>
</template>
