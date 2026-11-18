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
    <div class="wi-page">
      <div class="wi-card glass-panel">
        <div class="wi-card-head">
          <Repeat :size="16" class="text-brand" />
          <span class="wi-card-title">全部迭代</span>
          <span class="count-pill">{{ filteredSprints.length }}</span>
        </div>
        <div class="wi-card-body wi-table">
          <el-table :data="filteredSprints" row-key="id" height="100%" class="wi-el-table" empty-text="暂无迭代">
            <el-table-column label="名称" min-width="160">
              <template #default="{ row }"><span class="font-medium text-ink">{{ row.name }}</span></template>
            </el-table-column>
            <el-table-column v-if="!hideMdColumns" label="目标" min-width="220" show-overflow-tooltip>
              <template #default="{ row }"><span class="cell-text">{{ row.goal || '—' }}</span></template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <StatusPill
                  :label="SPRINT_STATUS[row.status]?.label || row.status"
                  :color="SPRINT_STATUS[row.status]?.color || 'var(--ink-secondary)'"
                />
              </template>
            </el-table-column>
            <el-table-column v-if="!hideSmColumns" label="起止日期" width="210">
              <template #default="{ row }">
                <span class="cell-text">{{ row.startDate || '—' }} ~ {{ row.endDate || '—' }}</span>
              </template>
            </el-table-column>
            <el-table-column v-if="!hideLgColumns" label="创建人" width="120">
              <template #default="{ row }"><span class="cell-text">{{ row.creatorName || '—' }}</span></template>
            </el-table-column>
            <el-table-column label="操作" :width="hideSmColumns ? 168 : 200" align="right">
              <template #default="{ row }"><RowActions :actions="rowActions(row)" /></template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>

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
import StatusPill from '@/components/common/StatusPill.vue'
import RowActions, { type RowAction } from '@/components/common/RowActions.vue'
import { useProjectPermission } from '@/composables/useProjectPermission'
import { useViewportWidth } from '@/composables/useViewportWidth'
import { SPRINT_STATUS } from '@/constants/issueDict'

const route = useRoute()
// 窄屏列降级：从 DOM 移除列，避免 el-table 的 colgroup 留下空白占位
const { hideLgColumns, hideMdColumns, hideSmColumns } = useViewportWidth()
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

/** el-table 插槽给的是宽松行类型，入口处收窄，避免模板里写断言 */
function rowActions(raw: unknown): RowAction[] {
  const row = raw as SprintVO
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
