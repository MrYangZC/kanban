<template>
  <div class="iterations">
    <div class="header">
      <h2>迭代管理</h2>
      <el-button type="primary" @click="openCreate" :disabled="!canManage">新建迭代</el-button>
    </div>

    <el-table :data="sprints" v-loading="loading" border stripe style="width: 100%">
      <el-table-column prop="name" label="名称" min-width="160" />
      <el-table-column prop="goal" label="目标" min-width="200" show-overflow-tooltip />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="起止日期" width="220">
        <template #default="{ row }">
          {{ row.startDate || '-' }} ~ {{ row.endDate || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="creatorName" label="创建人" width="120" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button
            v-if="row.status === 'PLANNED'"
            size="small"
            type="success"
            :disabled="!canManage"
            @click="doStart(row)"
          >开始</el-button>
          <el-button
            v-if="row.status === 'ACTIVE'"
            size="small"
            type="warning"
            :disabled="!canManage"
            @click="doComplete(row)"
          >完成</el-button>
          <el-button size="small" type="danger" :disabled="!canManage" @click="doDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listSprints,
  createSprint,
  updateSprint,
  deleteSprint,
  startSprint,
  completeSprint
} from '@/api'
import type { SprintVO } from '@/api/types'
import { useProjectPermission } from '@/composables/useProjectPermission'

const route = useRoute()
const projectId = route.params.projectId as string

const sprints = ref<SprintVO[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref<SprintVO | null>(null)
const form = ref({ name: '', goal: '', startDate: '', endDate: '' })

const { hasPermission, refresh } = useProjectPermission(() => projectId)
const canManage = computed(() => hasPermission('sprint:manage'))

async function load() {
  loading.value = true
  try {
    sprints.value = await listSprints(projectId)
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载迭代失败')
  } finally {
    loading.value = false
  }
}

function statusType(s: string) {
  return s === 'ACTIVE' ? 'success' : s === 'COMPLETED' ? 'info' : 'warning'
}
function statusLabel(s: string) {
  return s === 'ACTIVE' ? '进行中' : s === 'COMPLETED' ? '已完成' : '规划中'
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
      await updateSprint(projectId, editing.value.id, { ...form.value })
      ElMessage.success('已更新')
    } else {
      await createSprint(projectId, { ...form.value })
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '保存失败')
  }
}
async function doStart(row: SprintVO) {
  try {
    await startSprint(projectId, row.id)
    ElMessage.success('迭代已开始')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '操作失败')
  }
}
async function doComplete(row: SprintVO) {
  try {
    await completeSprint(projectId, row.id)
    ElMessage.success('迭代已完成')
    await load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '操作失败')
  }
}
async function doDelete(row: SprintVO) {
  try {
    await ElMessageBox.confirm(`确认删除迭代「${row.name}」？`, '提示', { type: 'warning' })
    await deleteSprint(projectId, row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.msg || '删除失败')
  }
}

onMounted(async () => {
  await refresh()
  await load()
})
</script>

<style scoped>
.iterations { padding: 20px; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
</style>
