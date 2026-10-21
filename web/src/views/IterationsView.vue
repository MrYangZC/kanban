<template>
  <SimpleListView
    title="迭代"
    :icon="Repeat"
    search-placeholder="搜索迭代"
    create-label="新建迭代"
    :loading="loading"
    :total="sprints.length"
    empty-text="暂无迭代"
    :can-create="canManage"
    v-model:keyword="keyword"
    @search="load"
    @refresh="load"
    @create="openCreate"
  >
    <DataTable :columns="columns">
      <tr v-for="row in filteredSprints" :key="row.id" class="data-tr">
        <td class="data-td data-td-first font-medium text-ink">{{ row.name }}</td>
        <td class="data-td max-w-[240px] truncate cell-text" :title="row.goal || ''">{{ row.goal || '—' }}</td>
        <td class="data-td">
          <StatusPill
            :label="SPRINT_STATUS[row.status]?.label || row.status"
            :color="SPRINT_STATUS[row.status]?.color || 'var(--ink-secondary)'"
          />
        </td>
        <td class="data-td cell-text">{{ row.startDate || '—' }} ~ {{ row.endDate || '—' }}</td>
        <td class="data-td cell-text">{{ row.creatorName || '—' }}</td>
        <td class="data-td">
          <RowActions :actions="rowActions(row)" />
        </td>
      </tr>
      <EmptyRow v-if="!loading && !filteredSprints.length" :colspan="columns.length" text="暂无迭代" />
    </DataTable>

    <template #footer>
      <el-dialog v-model="dialogVisible" :title="editing ? '编辑迭代' : '新建迭代'" width="520px">
        <el-form :model="form" label-width="80px">
          <el-form-item label="名称" required>
            <el-input v-model="form.name" placeholder="如 Sprint 1" />
          </el-form-item>
          <el-form-item label="目标">
            <el-input v-model="form.goal" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="开始日期">
            <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" />
          </el-form-item>
          <el-form-item label="结束日期">
            <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" />
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { errorMessage, isCancel } from '@/utils/error'
import { Repeat } from 'lucide-vue-next'
import {
  listSprints,
  createSprint,
  updateSprint,
  deleteSprint,
  startSprint,
  completeSprint
} from '@/api'
import type { SprintVO } from '@/api/types'
import SimpleListView from '@/components/SimpleListView.vue'
import DataTable, { type TableColumn } from '@/components/common/DataTable.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import RowActions, { type RowAction } from '@/components/common/RowActions.vue'
import EmptyRow from '@/components/common/EmptyRow.vue'
import { useProjectPermission } from '@/composables/useProjectPermission'
import { SPRINT_STATUS } from '@/constants/issueDict'

const route = useRoute()
const projectId = computed(() => route.params.projectId as string)

const sprints = ref<SprintVO[]>([])
const loading = ref(false)
const keyword = ref('')
const dialogVisible = ref(false)
const editing = ref<SprintVO | null>(null)
const form = ref({ name: '', goal: '', startDate: '', endDate: '' })

const { hasPermission, refresh } = useProjectPermission(() => projectId.value)
const canManage = computed(() => hasPermission('sprint:manage'))

const columns: TableColumn[] = [
  { key: 'name', label: '名称' },
  { key: 'goal', label: '目标' },
  { key: 'status', label: '状态' },
  { key: 'range', label: '起止日期' },
  { key: 'creator', label: '创建人' },
  { key: 'actions', label: '操作', align: 'right' }
]

function rowActions(row: SprintVO): RowAction[] {
  return [
    { label: '编辑', onClick: () => openEdit(row) },
    { label: '开始', tone: 'success', hidden: row.status !== 'PLANNED', disabled: !canManage.value, onClick: () => doStart(row) },
    { label: '完成', tone: 'warning', hidden: row.status !== 'ACTIVE', disabled: !canManage.value, onClick: () => doComplete(row) },
    { label: '删除', tone: 'danger', disabled: !canManage.value, onClick: () => doDelete(row) }
  ]
}

const filteredSprints = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return sprints.value
  return sprints.value.filter((s) =>
    s.name.toLowerCase().includes(kw) || (s.goal || '').toLowerCase().includes(kw)
  )
})

async function load() {
  loading.value = true
  try {
    sprints.value = await listSprints(projectId.value)
  } catch (e: unknown) {
    ElMessage.error(errorMessage(e, '加载迭代失败'))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.value = { name: '', goal: '', startDate: '', endDate: '' }
  dialogVisible.value = true
}
function openEdit(row: SprintVO) {
  editing.value = row
  form.value = {
    name: row.name,
    goal: row.goal || '',
    startDate: row.startDate || '',
    endDate: row.endDate || ''
  }
  dialogVisible.value = true
}
async function submit() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入迭代名称')
    return
  }
  try {
    if (editing.value) {
      await updateSprint(projectId.value, editing.value.id, { ...form.value })
      ElMessage.success('已更新')
    } else {
      await createSprint(projectId.value, { ...form.value })
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (e: unknown) {
    ElMessage.error(errorMessage(e, '保存失败'))
  }
}
async function doStart(row: SprintVO) {
  try {
    await startSprint(projectId.value, row.id)
    ElMessage.success('迭代已开始')
    await load()
  } catch (e: unknown) {
    ElMessage.error(errorMessage(e, '操作失败'))
  }
}
async function doComplete(row: SprintVO) {
  try {
    await completeSprint(projectId.value, row.id)
    ElMessage.success('迭代已完成')
    await load()
  } catch (e: unknown) {
    ElMessage.error(errorMessage(e, '操作失败'))
  }
}
async function doDelete(row: SprintVO) {
  try {
    await ElMessageBox.confirm(`确认删除迭代「${row.name}」？`, '提示', { type: 'warning' })
    await deleteSprint(projectId.value, row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: unknown) {
    if (!isCancel(e)) ElMessage.error(errorMessage(e, '删除失败'))
  }
}

onMounted(async () => {
  await refresh()
  await load()
})
</script>
