<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { ArrowRightLeft, Target } from 'lucide-vue-next'
import { listProjectMembers, listProjectTags, listSprints, saveWorkflowHandoff } from '@/api'
import type { HandoffType, LabelVO, ProjectMember, SprintVO, WorkItemCategory } from '@/api/types'
import { reportError } from '@/utils/error'
import UserAvatar from '@/components/common/UserAvatar.vue'

const props = defineProps<{
  projectId: string
  category: WorkItemCategory
  statusId: string
  columnName: string
  handoffType: HandoffType
  handoffUserId?: string | null
  handoffNote?: string | null
  /** 落点：进入该列后顺手把工作项放进这个迭代 */
  targetSprintId?: string | null
  /** 落点：进入该列后顺手给工作项打上这个标签 */
  targetLabelId?: string | null
}>()

const emit = defineEmits<{ (e: 'saved'): void }>()
const visible = defineModel<boolean>('visible', { default: false })

const form = ref<{ type: HandoffType; userId: string; note: string; sprintId: string; labelId: string }>({
  type: 'NONE',
  userId: '',
  note: '',
  sprintId: '',
  labelId: '',
})
const saving = ref(false)
const loadingMembers = ref(false)
const members = ref<ProjectMember[]>([])
const sprints = ref<SprintVO[]>([])
const tags = ref<LabelVO[]>([])

const memberOptions = computed(() =>
  members.value.map((m) => ({
    value: m.userId,
    label: m.displayName || m.username,
    username: m.username,
    avatarUrl: m.avatarUrl,
  }))
)

async function loadMembers() {
  if (members.value.length) return
  loadingMembers.value = true
  try {
    members.value = await listProjectMembers(props.projectId)
  } catch (e: unknown) {
    reportError(e, '加载项目成员失败')
  } finally {
    loadingMembers.value = false
  }
}

/**
 * 落点候选项：迭代与项目标签库。
 * 加载失败不阻塞交接本身（落点只是附加动作），提示后按「没有落点可选」继续。
 */
async function loadLanding() {
  if (!sprints.value.length) {
    try {
      sprints.value = await listSprints(props.projectId)
    } catch (e: unknown) {
      reportError(e, '加载迭代列表失败')
    }
  }
  if (!tags.value.length) {
    try {
      tags.value = await listProjectTags(props.projectId)
    } catch (e: unknown) {
      reportError(e, '加载标签失败')
    }
  }
}

watch(visible, (open) => {
  if (!open) return
  form.value = {
    type: props.handoffType || 'NONE',
    userId: props.handoffUserId || '',
    note: props.handoffNote || '',
    sprintId: props.targetSprintId || '',
    labelId: props.targetLabelId || '',
  }
  loadMembers()
  loadLanding()
}, { immediate: true })

async function save() {
  if (form.value.type === 'MEMBER' && !form.value.userId) {
    ElMessage.warning('请选择接手的成员')
    return
  }
  const landing = form.value.type !== 'NONE'
  saving.value = true
  try {
    await saveWorkflowHandoff(
      props.projectId,
      props.statusId,
      {
        handoffType: form.value.type,
        handoffUserId: form.value.type === 'MEMBER' ? form.value.userId : null,
        handoffNote: form.value.note.trim() || null,
        // 不交接时落点没有意义，一并清空，避免留下「没人接手但会改迭代」的半配置
        targetSprintId: landing && form.value.sprintId ? form.value.sprintId : null,
        targetLabelId: landing && form.value.labelId ? form.value.labelId : null,
      },
      props.category
    )
    ElMessage.success(form.value.type === 'NONE' ? '已关闭该列的交接' : '已保存交接配置')
    visible.value = false
    emit('saved')
  } catch (e: unknown) {
    reportError(e, '保存交接配置失败')
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="`列交接 · ${columnName}`" width="520px" append-to-body>
    <div class="space-y-4">
      <p class="text-xs leading-relaxed text-ink-400">
        工作项进入这一列时，负责人会自动转交给指定的人，并给对方发一条站内通知。
      </p>

      <el-radio-group v-model="form.type" class="flex flex-col items-start gap-2">
        <el-radio value="NONE">不交接（保留原负责人）</el-radio>
        <el-radio value="MEMBER">交给指定成员</el-radio>
        <el-radio value="PROJECT_LEAD">交给项目负责人</el-radio>
      </el-radio-group>

      <div v-if="form.type === 'MEMBER'" class="space-y-1">
        <el-select
          v-model="form.userId"
          :loading="loadingMembers"
          filterable
          placeholder="选择接手的成员"
          class="w-full"
        >
          <el-option
            v-for="m in memberOptions"
            :key="m.value"
            :label="m.label"
            :value="m.value"
          >
            <div class="flex items-center gap-2">
              <UserAvatar :name="m.label" :src="m.avatarUrl" :size="20" />
              <span class="text-sm text-ink-900">{{ m.label }}</span>
              <span class="text-xs text-ink-400">@{{ m.username }}</span>
            </div>
          </el-option>
        </el-select>
        <p class="text-xs text-ink-300">只能选择本项目成员，否则接手人可能看不到这条工作项。</p>
      </div>

      <div v-if="form.type !== 'NONE'" class="space-y-3 rounded-md border border-line-light px-3 py-3">
        <div class="flex items-center gap-1.5 text-xs font-medium text-ink-600">
          <Target :size="14" class="text-ink-400" />
          落点（可选）
        </div>
        <div class="space-y-1">
          <div class="text-xs text-ink-500">放进迭代</div>
          <el-select v-model="form.sprintId" clearable filterable placeholder="不调整迭代" class="w-full">
            <el-option v-for="s in sprints" :key="s.id" :label="s.name" :value="s.id" />
          </el-select>
        </div>
        <div class="space-y-1">
          <div class="text-xs text-ink-500">打上标签</div>
          <el-select v-model="form.labelId" clearable filterable placeholder="不加标签" class="w-full">
            <el-option v-for="t in tags" :key="t.id" :label="t.name" :value="t.id">
              <div class="flex items-center gap-2">
                <span
                  class="inline-block h-2.5 w-2.5 rounded-full"
                  :style="{ background: t.color || '#94a3b8' }"
                />
                <span class="text-sm text-ink-900">{{ t.name }}</span>
              </div>
            </el-option>
          </el-select>
        </div>
        <p class="text-xs text-ink-300">
          交接时一并执行：工作项被移进所选迭代、补上所选标签。撤销交接只退回迭代，标签会保留。
        </p>
      </div>

      <div v-if="form.type !== 'NONE'" class="space-y-1">
        <div class="text-xs font-medium text-ink-600">这一步要做什么（可选）</div>
        <el-input
          v-model="form.note"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="例如：跑完回归后把结果贴到评论区，失败的转回「修复中」"
        />
      </div>

      <div
        v-if="form.type !== 'NONE'"
        class="flex items-start gap-2 rounded-md border border-line-light bg-surface-muted/60 px-3 py-2"
      >
        <ArrowRightLeft :size="14" class="mt-0.5 shrink-0 text-ink-400" />
        <p class="text-xs leading-relaxed text-ink-500">
          流转后负责人会被改写，并在动态里留下「负责人变更」「迭代变更」等记录。
        </p>
      </div>
    </div>

    <template #footer>
      <div class="flex justify-end gap-2">
        <button class="btn-ghost" @click="visible = false">取消</button>
        <button class="btn-primary" :disabled="saving" @click="save">
          {{ saving ? '保存中…' : '保存' }}
        </button>
      </div>
    </template>
  </el-dialog>
</template>
