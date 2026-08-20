<template>
  <div class="risks">
    <div class="header">
      <h2>风险管理</h2>
      <el-button type="primary" @click="openCreate" :disabled="!canManage">新建风险</el-button>
    </div>

    <el-table :data="risks" v-loading="loading" border stripe style="width: 100%">
      <el-table-column prop="title" label="风险" min-width="200" />
      <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
      <el-table-column label="级别" width="110">
        <template #default="{ row }">
          <el-tag :type="levelType(row.level)">{{ levelLabel(row.level) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ownerName" label="负责人" width="120" />
      <el-table-column label="截止日" width="130">
        <template #default="{ row }">{{ row.dueDate || '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" :disabled="!canAdmin" @click="doDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

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
          <el-select v-model="form.ownerId" filterable clearable placeholder="选择成员">
            <el-option v-for="m in members" :key="m.userId" :label="m.displayName || m.username" :value="m.userId" />
          </el-select>
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listRisks,
  createRisk,
  updateRisk,
  deleteRisk,
  listProjectMembers
} from '@/api'
import type { RiskVO, ProjectMember } from '@/api/types'
import { useProjectPermission } from '@/composables/useProjectPermission'

const route = useRoute()
const projectId = route.params.projectId as string

const risks = ref<RiskVO[]>([])
const members = ref<ProjectMember[]>([])
const loading = ref(false)
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

const { canWrite, canAdmin, refresh } = useProjectPermission(() => projectId)
const canManage = computed(() => canWrite.value)

async function load() {
  loading.value = true
  try {
    risks.value = await listRisks(projectId)
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载风险失败')
  } finally {
    loading.value = false
  }
}

function levelType(l: string) {
  return l === 'CRITICAL' ? 'danger' : l === 'HIGH' ? 'warning' : l === 'MEDIUM' ? 'primary' : 'info'
}
function levelLabel(l: string) {
  return l === 'CRITICAL' ? '严重' : l === 'HIGH' ? '高' : l === 'MEDIUM' ? '中' : '低'
}
function statusType(s: string) {
  return s === 'CLOSED' ? 'info' : s === 'MITIGATING' ? 'warning' : 'danger'
}
function statusLabel(s: string) {
  return s === 'CLOSED' ? '已关闭' : s === 'MITIGATING' ? '缓解中' : '待处理'
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
    const payload = { ...form.value }
    if (editing.value) {
      await updateRisk(projectId, editing.value.id, payload)
      ElMessage.success('已更新')
    } else {
      await createRisk(projectId, payload)
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '保存失败')
  }
}
async function doDelete(row: RiskVO) {
  try {
    await ElMessageBox.confirm(`确认删除风险「${row.title}」？`, '提示', { type: 'warning' })
    await deleteRisk(projectId, row.id)
    ElMessage.success('已删除')
    await load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.msg || '删除失败')
  }
}

onMounted(async () => {
  await refresh()
  try {
    members.value = await listProjectMembers(projectId)
  } catch {
    members.value = []
  }
  await load()
})
</script>

<style scoped>
.risks { padding: 20px; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
</style>
