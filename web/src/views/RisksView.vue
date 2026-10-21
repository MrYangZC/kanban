<template>
  <SimpleListView
    title="风险"
    :icon="ShieldAlert"
    search-placeholder="搜索风险"
    create-label="新建风险"
    :loading="loading"
    :total="risks.length"
    empty-text="暂无风险"
    :can-create="canManage"
    v-model:keyword="keyword"
    @search="load"
    @refresh="load"
    @create="openCreate"
  >
    <DataTable :columns="columns">
      <tr v-for="row in filteredRisks" :key="row.id" class="data-tr">
        <td class="data-td data-td-first font-medium text-ink">{{ row.title }}</td>
        <td class="data-td max-w-[240px] truncate cell-text" :title="row.description || ''">{{ row.description || '—' }}</td>
        <td class="data-td">
          <StatusPill
            :label="RISK_LEVEL[row.level]?.label || row.level"
            :color="RISK_LEVEL[row.level]?.color || 'var(--ink-secondary)'"
          />
        </td>
        <td class="data-td">
          <StatusPill
            :label="RISK_STATUS[row.status]?.label || row.status"
            :color="RISK_STATUS[row.status]?.color || 'var(--ink-secondary)'"
          />
        </td>
        <td class="data-td cell-text">{{ row.ownerName || '—' }}</td>
        <td class="data-td cell-text">{{ row.dueDate || '—' }}</td>
        <td class="data-td">
          <RowActions :actions="rowActions(row)" />
        </td>
      </tr>
      <EmptyRow v-if="!loading && !filteredRisks.length" :colspan="columns.length" text="暂无风险" />
    </DataTable>

    <template #footer>
      <el-dialog v-model="dialogVisible" :title="editing ? '编辑风险' : '新建风险'" width="520px">
        <el-form :model="form" label-width="80px">
          <el-form-item label="标题" required>
            <el-input v-model="form.title" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="form.description" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="级别">
            <el-select v-model="form.level">
              <el-option label="低" value="LOW" />
              <el-option label="中" value="MEDIUM" />
              <el-option label="高" value="HIGH" />
              <el-option label="严重" value="CRITICAL" />
            </el-select>
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="form.status">
              <el-option label="待处理" value="OPEN" />
              <el-option label="缓解中" value="MITIGATING" />
              <el-option label="已关闭" value="CLOSED" />
            </el-select>
          </el-form-item>
          <el-form-item label="负责人">
            <UserSelect
              v-model="form.ownerId"
              :users="members"
              empty-label="未分配"
              clearable
              size="default"
              width-class="w-full"
            />
          </el-form-item>
          <el-form-item label="截止日">
            <el-date-picker v-model="form.dueDate" type="date" value-format="YYYY-MM-DD" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submit">确定</el-button>
        </template>
      </el-dialog>
    </template>
  </SimpleListView>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { confirmRemove } from '@/utils/confirm'
import { isCancel, reportError } from '@/utils/error'
import { ShieldAlert } from 'lucide-vue-next'
import {
  listRisks,
  createRisk,
  updateRisk,
  deleteRisk,
  listProjectMembers
} from '@/api'
import type { RiskVO, ProjectMember } from '@/api/types'
import SimpleListView from '@/components/SimpleListView.vue'
import UserSelect from '@/components/common/UserSelect.vue'
import DataTable, { type TableColumn } from '@/components/common/DataTable.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import RowActions, { type RowAction } from '@/components/common/RowActions.vue'
import EmptyRow from '@/components/common/EmptyRow.vue'
import { useProjectPermission } from '@/composables/useProjectPermission'
import { RISK_LEVEL, RISK_STATUS } from '@/constants/issueDict'

const route = useRoute()
const projectId = computed(() => route.params.projectId as string)

const risks = ref<RiskVO[]>([])
const members = ref<ProjectMember[]>([])
const loading = ref(false)
const keyword = ref('')
const dialogVisible = ref(false)
const editing = ref<RiskVO | null>(null)
const form = ref<{
  title: string
  description: string
  level: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  status: 'OPEN' | 'MITIGATING' | 'CLOSED'
  ownerId: string
  dueDate: string
}>({ title: '', description: '', level: 'MEDIUM', status: 'OPEN', ownerId: '', dueDate: '' })

const { hasPermission, refresh } = useProjectPermission(() => projectId.value)
const canManage = computed(() => hasPermission('risk:manage'))

const columns: TableColumn[] = [
  { key: 'title', label: '风险' },
  { key: 'description', label: '描述' },
  { key: 'level', label: '级别' },
  { key: 'status', label: '状态' },
  { key: 'owner', label: '负责人' },
  { key: 'dueDate', label: '截止日' },
  { key: 'actions', label: '操作', align: 'right' }
]

function rowActions(row: RiskVO): RowAction[] {
  return [
    { label: '编辑', onClick: () => openEdit(row) },
    { label: '删除', tone: 'danger', disabled: !canManage.value, onClick: () => doDelete(row) }
  ]
}

const filteredRisks = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return risks.value
  return risks.value.filter((r) =>
    r.title.toLowerCase().includes(kw) || (r.description || '').toLowerCase().includes(kw)
  )
})

async function load() {
  loading.value = true
  try {
    risks.value = await listRisks(projectId.value)
  } catch (e: unknown) {
    reportError(e, '加载风险失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.value = { title: '', description: '', level: 'MEDIUM', status: 'OPEN', ownerId: '', dueDate: '' }
  dialogVisible.value = true
}
function openEdit(row: RiskVO) {
  editing.value = row
  form.value = {
    title: row.title,
    description: row.description || '',
    level: row.level,
    status: row.status,
    ownerId: row.ownerId || '',
    dueDate: row.dueDate || ''
  }
  dialogVisible.value = true
}
async function submit() {
  if (!form.value.title.trim()) {
    ElMessage.warning('请输入风险标题')
    return
  }
  try {
    // 负责人/截止日被清空时不能只传空值：后端把 null 视为「不修改」，
    // 必须显式列进 clearFields 才会真正置空（与工作项更新同一套语义）
    const { ownerId, dueDate, ...rest } = form.value
    const payload: Parameters<typeof updateRisk>[2] = { ...rest }
    if (ownerId) payload.ownerId = ownerId
    if (dueDate) payload.dueDate = dueDate
    if (editing.value) {
      const clearFields: string[] = []
      if (!ownerId) clearFields.push('ownerId')
      if (!dueDate) clearFields.push('dueDate')
      if (clearFields.length) payload.clearFields = clearFields
      await updateRisk(projectId.value, editing.value.id, payload)
      ElMessage.success('已更新')
    } else {
      await createRisk(projectId.value, {
        title: form.value.title,
        description: form.value.description,
        level: form.value.level,
        status: form.value.status,
        ...(ownerId ? { ownerId } : {}),
        ...(dueDate ? { dueDate } : {})
      })
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (e: unknown) {
    reportError(e, '保存失败')
  }
}
async function doDelete(row: RiskVO) {
  try {
    await confirmRemove(row.title, '风险')
    await deleteRisk(projectId.value, row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: unknown) {
    if (!isCancel(e)) reportError(e, '删除失败')
  }
}

onMounted(async () => {
  await refresh()
  try {
    members.value = await listProjectMembers(projectId.value)
  } catch {
    // 负责人下拉数据缺失不影响列表展示（接口层已提示错误）
    members.value = []
  }
  await load()
})
</script>
