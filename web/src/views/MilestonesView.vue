<template>
  <SimpleListView
    title="里程碑"
    :icon="Target"
    search-placeholder="搜索里程碑"
    create-label="新建里程碑"
    :loading="loading"
    :total="milestones.length"
    empty-text="暂无里程碑"
    :can-create="canManage"
    v-model:keyword="keyword"
    @search="load"
    @refresh="load"
    @create="openCreate"
  >
    <DataTable :columns="columns">
      <tr v-for="row in filteredMilestones" :key="row.id" class="data-tr">
        <td class="data-td data-td-first font-medium text-ink">{{ row.name }}</td>
        <td class="data-td max-w-[280px] truncate cell-text" :title="row.description || ''">{{ row.description || '—' }}</td>
        <td class="data-td cell-text">{{ row.dueDate || '—' }}</td>
        <td class="data-td">
          <StatusPill
            :label="MILESTONE_STATUS[row.status]?.label || row.status"
            :color="MILESTONE_STATUS[row.status]?.color || 'var(--ink-secondary)'"
          />
        </td>
        <td class="data-td">
          <RowActions :actions="rowActions(row)" />
        </td>
      </tr>
      <EmptyRow v-if="!loading && !filteredMilestones.length" :colspan="columns.length" text="暂无里程碑" />
    </DataTable>

    <template #footer>
      <el-dialog v-model="dialogVisible" :title="editing ? '编辑里程碑' : '新建里程碑'" width="520px">
        <el-form :model="form" label-width="80px">
          <el-form-item label="名称" required>
            <el-input v-model="form.name" />
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="form.description" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="截止日期">
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
import { ElMessage, ElMessageBox } from 'element-plus'
import { errorMessage, isCancel } from '@/utils/error'
import { Target } from 'lucide-vue-next'
import {
  listMilestones,
  createMilestone,
  updateMilestone,
  deleteMilestone
} from '@/api'
import type { MilestoneVO } from '@/api/types'
import SimpleListView from '@/components/SimpleListView.vue'
import DataTable, { type TableColumn } from '@/components/common/DataTable.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import RowActions, { type RowAction } from '@/components/common/RowActions.vue'
import EmptyRow from '@/components/common/EmptyRow.vue'
import { useProjectPermission } from '@/composables/useProjectPermission'
import { MILESTONE_STATUS } from '@/constants/issueDict'

const route = useRoute()
const projectId = computed(() => route.params.projectId as string)

const milestones = ref<MilestoneVO[]>([])
const loading = ref(false)
const keyword = ref('')
const dialogVisible = ref(false)
const editing = ref<MilestoneVO | null>(null)
const form = ref({ name: '', description: '', dueDate: '' })

const { hasPermission, refresh } = useProjectPermission(() => projectId.value)
const canManage = computed(() => hasPermission('milestone:manage'))

const columns: TableColumn[] = [
  { key: 'name', label: '名称' },
  { key: 'description', label: '描述' },
  { key: 'dueDate', label: '截止日期' },
  { key: 'status', label: '状态' },
  { key: 'actions', label: '操作', align: 'right' }
]

function rowActions(row: MilestoneVO): RowAction[] {
  return [
    { label: '编辑', onClick: () => openEdit(row) },
    { label: '删除', tone: 'danger', disabled: !canManage.value, onClick: () => doDelete(row) }
  ]
}

const filteredMilestones = computed(() => {
  const kw = keyword.value.trim().toLowerCase()
  if (!kw) return milestones.value
  return milestones.value.filter((m) =>
    m.name.toLowerCase().includes(kw) || (m.description || '').toLowerCase().includes(kw)
  )
})

async function load() {
  loading.value = true
  try {
    milestones.value = await listMilestones(projectId.value)
  } catch (e: unknown) {
    ElMessage.error(errorMessage(e, '加载里程碑失败'))
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.value = { name: '', description: '', dueDate: '' }
  dialogVisible.value = true
}
function openEdit(row: MilestoneVO) {
  editing.value = row
  form.value = {
    name: row.name,
    description: row.description || '',
    dueDate: row.dueDate || ''
  }
  dialogVisible.value = true
}
async function submit() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入里程碑名称')
    return
  }
  try {
    if (editing.value) {
      await updateMilestone(projectId.value, editing.value.id, { ...form.value })
      ElMessage.success('已更新')
    } else {
      await createMilestone(projectId.value, { ...form.value })
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (e: unknown) {
    ElMessage.error(errorMessage(e, '保存失败'))
  }
}
async function doDelete(row: MilestoneVO) {
  try {
    await ElMessageBox.confirm(`确认删除里程碑「${row.name}」？`, '提示', { type: 'warning' })
    await deleteMilestone(projectId.value, row.id)
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
