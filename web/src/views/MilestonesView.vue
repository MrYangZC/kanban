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
    <div class="wi-page">
      <div class="wi-card glass-panel">
        <div class="wi-card-head">
          <Target :size="16" class="text-brand" />
          <span class="wi-card-title">全部里程碑</span>
          <span class="count-pill">{{ filteredMilestones.length }}</span>
        </div>
        <div class="wi-card-body wi-table">
          <el-table :data="filteredMilestones" row-key="id" height="100%" class="wi-el-table" empty-text="暂无里程碑">
            <el-table-column label="名称" min-width="180">
              <template #default="{ row }"><span class="font-medium text-ink">{{ row.name }}</span></template>
            </el-table-column>
            <el-table-column label="描述" min-width="240" show-overflow-tooltip>
              <template #default="{ row }"><span class="cell-text">{{ row.description || '—' }}</span></template>
            </el-table-column>
            <el-table-column label="截止日期" width="130">
              <template #default="{ row }"><span class="cell-text">{{ row.dueDate || '—' }}</span></template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <StatusPill
                  :label="MILESTONE_STATUS[row.status]?.label || row.status"
                  :color="MILESTONE_STATUS[row.status]?.color || 'var(--ink-secondary)'"
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="200" align="right">
              <template #default="{ row }"><RowActions :actions="rowActions(row)" /></template>
            </el-table-column>
          </el-table>
        </div>
      </div>
    </div>

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
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useEntityCrud } from '@/composables/useEntityCrud'
import { Target } from 'lucide-vue-next'
import {
  listMilestones,
  createMilestone,
  updateMilestone,
  deleteMilestone
} from '@/api'
import type { MilestoneVO } from '@/api/types'
import SimpleListView from '@/components/SimpleListView.vue'
import StatusPill from '@/components/common/StatusPill.vue'
import RowActions, { type RowAction } from '@/components/common/RowActions.vue'
import { useProjectPermission } from '@/composables/useProjectPermission'
import { MILESTONE_STATUS } from '@/constants/issueDict'

const route = useRoute()
const projectId = computed(() => route.params.projectId as string)

const { hasPermission, refresh } = useProjectPermission(() => projectId.value)
const canManage = computed(() => hasPermission('milestone:manage'))

// 列表 + 弹窗表单骨架（与迭代页同构），解构重命名以保持模板不变
const {
  rows: milestones,
  filtered: filteredMilestones,
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
  label: '里程碑',
  fetch: (pid) => listMilestones(pid),
  create: (pid, payload) => createMilestone(pid, payload),
  update: (pid, id, payload) => updateMilestone(pid, id, payload),
  remove: (pid, id) => deleteMilestone(pid, id),
  emptyForm: () => ({ name: '', description: '', dueDate: '' }),
  toForm: (row: MilestoneVO) => ({
    name: row.name,
    description: row.description || '',
    dueDate: row.dueDate || ''
  }),
  searchText: (row: MilestoneVO) => [row.name, row.description]
})

/** el-table 插槽给的是宽松行类型，入口处收窄 */
function rowActions(raw: unknown): RowAction[] {
  const row = raw as MilestoneVO
  return [
    { label: '编辑', onClick: () => openEdit(row) },
    { label: '删除', tone: 'danger', disabled: !canManage.value, onClick: () => doDelete(row) }
  ]
}

onMounted(async () => {
  await refresh()
  await load()
})
</script>
