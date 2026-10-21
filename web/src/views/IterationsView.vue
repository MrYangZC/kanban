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
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { reportError } from '@/utils/error'
import { useEntityCrud } from '@/composables/useEntityCrud'
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

const { hasPermission, refresh } = useProjectPermission(() => projectId.value)
const canManage = computed(() => hasPermission('sprint:manage'))

// 列表 + 弹窗表单骨架（与里程碑页同构），解构重命名以保持模板不变
const {
  rows: sprints,
  filtered: filteredSprints,
  loading,
  keyword,
  dialogVisible,
  editing,
  form,
  load,
  openCreate,
  openEdit,
  submit,
  doDelete
} = useEntityCrud(() => projectId.value, {
  label: '迭代',
  fetch: (pid) => listSprints(pid),
  create: (pid, payload) => createSprint(pid, payload),
  update: (pid, id, payload) => updateSprint(pid, id, payload),
  remove: (pid, id) => deleteSprint(pid, id),
  emptyForm: () => ({ name: '', goal: '', startDate: '', endDate: '' }),
  toForm: (row: SprintVO) => ({
    name: row.name,
    goal: row.goal || '',
    startDate: row.startDate || '',
    endDate: row.endDate || ''
  }),
  searchText: (row: SprintVO) => [row.name, row.goal]
})

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

async function doStart(row: SprintVO) {
  try {
    await startSprint(projectId.value, row.id)
    ElMessage.success('迭代已开始')
    await load()
  } catch (e: unknown) {
    reportError(e, '操作失败')
  }
}
async function doComplete(row: SprintVO) {
  try {
    await completeSprint(projectId.value, row.id)
    ElMessage.success('迭代已完成')
    await load()
  } catch (e: unknown) {
    reportError(e, '操作失败')
  }
}
onMounted(async () => {
  await refresh()
  await load()
})
</script>
