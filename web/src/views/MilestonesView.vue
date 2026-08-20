<template>
  <div class="milestones">
    <div class="header">
      <h2>里程碑</h2>
      <el-button type="primary" @click="openCreate" :disabled="!canManage">新建里程碑</el-button>
    </div>

    <el-table :data="milestones" v-loading="loading" border stripe style="width: 100%">
      <el-table-column prop="name" label="名称" min-width="180" />
      <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
      <el-table-column prop="dueDate" label="截止日期" width="140" />
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="row.status === 'COMPLETED' ? 'success' : 'warning'">
            {{ row.status === 'COMPLETED' ? '已完成' : '进行中' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" :disabled="!canManage" @click="doDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listMilestones,
  createMilestone,
  updateMilestone,
  deleteMilestone
} from '@/api'
import type { MilestoneVO } from '@/api/types'
import { useProjectPermission } from '@/composables/useProjectPermission'

const route = useRoute()
const projectId = route.params.projectId as string

const milestones = ref<MilestoneVO[]>([])
const loading = ref(false)
const dialogVisible = ref(false)
const editing = ref<MilestoneVO | null>(null)
const form = ref({ name: '', description: '', dueDate: '' })

const { canWrite, refresh } = useProjectPermission(() => projectId)
const canManage = computed(() => canWrite.value)

async function load() {
  loading.value = true
  try {
    milestones.value = await listMilestones(projectId)
  } catch (e: any) {
    ElMessage.error(e?.msg || '加载里程碑失败')
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
      await updateMilestone(projectId, editing.value.id, { ...form.value })
      ElMessage.success('已更新')
    } else {
      await createMilestone(projectId, { ...form.value })
      ElMessage.success('已创建')
    }
    dialogVisible.value = false
    await load()
  } catch (e: any) {
    ElMessage.error(e?.msg || '保存失败')
  }
}
async function doDelete(row: MilestoneVO) {
  try {
    await ElMessageBox.confirm(`确认删除里程碑「${row.name}」？`, '提示', { type: 'warning' })
    await deleteMilestone(projectId, row.id)
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
.milestones { padding: 20px; }
.header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
</style>
