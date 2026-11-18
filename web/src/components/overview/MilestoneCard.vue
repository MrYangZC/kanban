<script setup lang="ts">
import { ref } from 'vue'
import { Target, Plus, Calendar } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import type { MilestoneVO } from '@/api/types'
import { createMilestone } from '@/api'
import { reportError } from '@/utils/error'
import { required, maxLen } from '@/utils/formRules'
import ProgressBar from '@/components/ProgressBar.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import type { StatusStyle } from '@/components/StatusBadge.vue'

interface Props {
  milestones?: MilestoneVO[]
  /** 所属项目：概览页在其卡片内直接新建里程碑 */
  projectId?: string
}

const props = withDefaults(defineProps<Props>(), {
  milestones: () => [],
  projectId: ''
})

const emit = defineEmits<{ (e: 'refresh'): void }>()

const STATUS_STYLE: Record<string, StatusStyle> = {
  OPEN: { label: '进行中', color: 'var(--primary)' },
  DONE: { label: '已完成', color: 'var(--success)' },
  OVERDUE: { label: '已逾期', color: 'var(--danger)' }
}

const dialogVisible = ref(false)
const submitting = ref(false)
const form = ref({ name: '', description: '', dueDate: '' })

function onAdd() {
  if (!props.projectId) return
  form.value = { name: '', description: '', dueDate: '' }
  dialogVisible.value = true
}

/** 表单校验 */
const msFormRef = ref<{ validate: () => Promise<boolean> } | null>(null)

const rules = {
  name: [required('名称'), maxLen(60, '名称')],
  description: [maxLen(200, '描述')]
}

async function onSubmit() {
  const valid = await msFormRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  await submit()
}

async function submit() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入里程碑名称')
    return
  }
  submitting.value = true
  try {
    await createMilestone(props.projectId, { ...form.value })
    ElMessage.success(`已创建里程碑「${form.value.name}」`)
    dialogVisible.value = false
    emit('refresh')
  } catch (e: unknown) {
    reportError(e, '保存失败')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <div class="glass-panel rounded-lg">
    <div class="flex items-center justify-between border-b border-line-light px-4 py-3">
      <div class="flex items-center gap-2 text-sm font-medium text-ink-900">
        <Target :size="16" class="text-brand" /> 里程碑
      </div>
      <button class="flex items-center gap-1 text-xs text-brand transition hover:opacity-80" @click="onAdd">
        <Plus :size="12" /> 新建
      </button>
    </div>
    <div class="p-4">
      <div v-if="!props.milestones.length" class="py-6 text-center text-sm text-ink-400">暂无里程碑</div>
      <div v-else class="space-y-3">
        <div v-for="m in props.milestones" :key="m.id" class="rounded-md border border-line-light p-3 transition hover:bg-surface-muted/50">
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium text-ink-900">{{ m.name }}</span>
            <StatusBadge :status="m.status" :dict="STATUS_STYLE" />
          </div>
          <div class="mt-2 flex items-center gap-3">
            <ProgressBar class="flex-1" :value="m.progress" :max="100" height="sm" />
            <span class="text-xs text-ink-400">{{ m.progress }}%</span>
            <span v-if="m.dueDate" class="flex items-center gap-1 text-xs text-ink-400">
              <Calendar :size="11" /> {{ m.dueDate }}
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- append-to-body 必须加：本卡片是 .glass-panel（backdrop-filter），
         它会成为 fixed 定位的包含块，弹窗不挂到 body 就会被裁在这张卡片里 -->
    <el-dialog v-model="dialogVisible" title="新建里程碑" width="520px" append-to-body>
      <el-form ref="msFormRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入里程碑名称" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="截止日期" prop="dueDate">
          <el-date-picker v-model="form.dueDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>
