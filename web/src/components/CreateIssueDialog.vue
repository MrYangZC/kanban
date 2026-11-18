<script setup lang="ts">
import { ref, reactive, watch, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { createIssue, getProject, listProjectMembers } from '@/api'
import type { Issue, Project, ProjectMember } from '@/api/types'
import RichTextEditor from '@/components/RichTextEditor.vue'
import IssueBasicFields from '@/components/IssueBasicFields.vue'
import { AlertTriangle, Plus, Link2 } from 'lucide-vue-next'
import { PRIMARY_HEX } from '@/theme/colorMap'

interface Props {
  modelValue: boolean
  projectId: string
  title?: string
  defaultStatusId?: string
  defaultIssueTypeId?: string
  /** 工作项类型编码：BUG 时显示严重程度字段 */
  defaultIssueTypeCode?: 'BUG' | 'TASK' | 'EPIC' | 'REQ' | 'RISK' | string
}

const props = withDefaults(defineProps<Props>(), {
  title: '新建工作项',
  defaultStatusId: '',
  defaultIssueTypeId: '',
  defaultIssueTypeCode: 'TASK',
})

const emit = defineEmits<{
  'update:modelValue': [val: boolean]
  created: [issue: Issue]
}>()



const visible = ref(props.modelValue)
watch(() => props.modelValue, (v) => (visible.value = v))
watch(visible, (v) => emit('update:modelValue', v))

const project = ref<Project | null>(null)
const members = ref<ProjectMember[]>([])

const issueTypeName = computed(() => {
  const map: Record<string, string> = { BUG: '缺陷', TASK: '任务', EPIC: '史诗', REQ: '需求', RISK: '风险' }
  return map[props.defaultIssueTypeCode] || '工作项'
})

const priorityOptions = [
  { v: 'P0', label: '紧急' },
  { v: 'P1', label: '高' },
  { v: 'P2', label: '中' },
  { v: 'P3', label: '低' }
]
const severityOptions = [
  { v: 'S1', label: '致命' },
  { v: 'S2', label: '严重' },
  { v: 'S3', label: '一般' },
  { v: 'S4', label: '轻微' }
]

const form = reactive({
  title: '',
  description: '',
  priority: 'P2',
  severity: 'S3',
  assigneeId: '',
  verifierId: '',
  iteration: '',
  planStart: '',
  dueDate: '',
  noFixReason: '',
  ccUsers: [] as string[],
  participants: [] as string[],
})

const tags = ref<{ id: string; name: string; color?: string }[]>([])
const fieldsRef = ref<InstanceType<typeof IssueBasicFields> | null>(null)
const continueNext = ref(false)
const submitting = ref(false)

function reset() {
  titleError.value = ''
  form.title = ''
  form.description = ''
  form.priority = 'P2'
  form.severity = 'S3'
  form.assigneeId = ''
  form.verifierId = ''
  form.iteration = ''
  form.planStart = ''
  form.dueDate = ''
  form.noFixReason = ''
  form.ccUsers = []
  form.participants = []
  tags.value = []
}

async function loadMeta() {
  if (!props.projectId) return
  try {
    const [p, m] = await Promise.all([
      getProject(props.projectId),
      listProjectMembers(props.projectId),
    ])
    project.value = p
    members.value = m
  } catch {
    /* 忽略，下拉数据加载失败不阻塞创建 */
  }
}

watch(visible, (v) => {
  if (v) {
    reset()
    loadMeta()
  }
})

/** 标题校验：字段级提示（失焦即时反馈，提交前再拦一次） */
const titleError = ref('')

function validateTitle(): boolean {
  const value = form.title.trim()
  if (!value) {
    titleError.value = '请输入标题'
    return false
  }
  if (value.length > 120) {
    titleError.value = '标题不能超过 120 个字符'
    return false
  }
  titleError.value = ''
  return true
}

async function submit() {
  if (!validateTitle()) return
  submitting.value = true
  try {
    const issue = await createIssue({
      projectId: props.projectId,
      statusId: props.defaultStatusId,
      issueTypeId: props.defaultIssueTypeId || undefined,
      issueTypeCode: props.defaultIssueTypeCode || undefined,
      title: form.title.trim(),
      description: form.description.trim() || undefined,
      priority: form.priority,
      severity: props.defaultIssueTypeCode === 'BUG' ? form.severity : undefined,
      assigneeId: form.assigneeId || undefined,
      verifierId: form.verifierId || undefined,
      iteration: form.iteration.trim() || undefined,
      planStart: form.planStart || undefined,
      noFixReason: form.noFixReason.trim() || undefined,
      ccUsers: form.ccUsers.length ? form.ccUsers : undefined,
      participants: form.participants.length ? form.participants : undefined,
    })
    ElMessage.success('已创建')
    emit('created', issue)
    if (continueNext.value) {
      reset()
    } else {
      visible.value = false
    }
  } catch {
    /* 接口错误时 ElMessage 已由拦截器提示 */
  } finally {
    submitting.value = false
  }
}

function addTagFromFields() {
  const input = fieldsRef.value?.tagInput?.trim()
  if (input && !tags.value.some((t) => t.name === input)) {
    tags.value.push({ id: input, name: input, color: PRIMARY_HEX })
  }
  fieldsRef.value?.clearTagInput()
}
function removeTagFromFields(id: string) {
  tags.value = tags.value.filter((t) => t.id !== id)
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="min(1080px, 94vw)"
    append-to-body
    align-center
    :close-on-click-modal="false"
    @close="reset"
  >
    <div class="flex gap-5" style="min-height: 460px">
      <!-- ======== 左侧：标题 + 描述 ======== -->
      <div class="flex min-w-0 flex-1 flex-col gap-3">
        <input
          v-model="form.title"
          placeholder="请输入标题"
          maxlength="120"
          class="w-full rounded border px-3 py-2 text-sm outline-none focus:border-primary"
          :class="titleError ? 'border-danger' : 'border-line'"
          @blur="validateTitle"
          @keyup.ctrl.enter="submit"
        />
        <p v-if="titleError" class="-mt-2 text-xs text-danger">{{ titleError }}</p>
        <div class="flex flex-1 flex-col">
          <label class="mb-1.5 text-xs text-ink-secondary">描述</label>
          <RichTextEditor
            v-model="form.description"
            :project-id="projectId"
            :rows="14"
            placeholder="支持 Markdown，建议填写验收要点、复现步骤等，可 Ctrl+V 粘贴截图"
          />
        </div>
      </div>

      <!-- ======== 右侧：基础字段 ======== -->
      <aside class="flex w-[300px] shrink-0 flex-col">
        <div class="rounded-md border border-line-light p-4">
          <h3 class="mb-4 text-sm font-medium text-ink">基础字段</h3>

          <!-- 工作项类型 -->
          <div class="mb-4 flex items-center justify-between">
            <label class="text-sm text-ink-regular">工作项类型</label>
            <div class="flex items-center gap-1.5 text-sm text-ink">
              <AlertTriangle :size="14" class="text-danger" />
              {{ issueTypeName }}
            </div>
          </div>

          <IssueBasicFields
            ref="fieldsRef"
            :model-value="form"
            @update:model-value="(e) => Object.assign(form, e)"
            :members="members"
            :priority-options="priorityOptions"
            :severity-options="severityOptions"
            :tags="tags"
            :can-write="true"
            :show-severity="defaultIssueTypeCode === 'BUG'"
            :show-status="false"
            :show-type="false"
            @add-tag="addTagFromFields"
            @remove-tag="(id) => removeTagFromFields(id)"
          />

          <!-- 归属项目 -->
          <div class="mt-4">
            <label class="mb-1 block text-sm text-ink-regular">归属项目<span class="text-danger">*</span></label>
            <div class="truncate rounded border border-line px-2 py-1 text-sm text-ink" :title="project?.name">
              {{ project?.name || '—' }}
            </div>
          </div>

          <!-- 迭代 -->
          <div class="mt-4 flex items-center justify-between">
            <label class="text-sm text-ink-regular">迭代</label>
            <input
              v-model="form.iteration"
              placeholder="请选择"
              class="w-40 rounded border border-line bg-transparent px-2 py-1 text-right text-sm text-ink outline-none"
            />
          </div>
        </div>

        <!-- 附件区（创建后可上传） -->
        <div class="mt-3 rounded-md border border-line-light p-4">
          <div class="mb-2 flex items-center justify-between">
            <h3 class="text-sm font-medium text-ink">
              附件 <span class="text-xs font-normal text-ink-secondary">0</span>
            </h3>
            <button
              class="flex h-7 w-7 cursor-not-allowed items-center justify-center rounded bg-surface-page text-lg text-ink-secondary opacity-50"
              title="创建后可上传附件"
            ><Plus :size="16" /></button>
          </div>
          <div class="py-2 text-center text-xs text-ink-placeholder">
            创建后可上传附件
          </div>
        </div>
      </aside>
    </div>

    <template #footer>
      <div class="flex items-center justify-between">
        <!-- 关联项控制区 -->
        <div class="flex items-center gap-2">
          <span
            class="rounded bg-primary-light px-2 py-0.5 text-xs text-primary"
          >关联项 0</span>
          <span class="text-xs text-ink-secondary">全部·0</span>
          <div class="flex items-center gap-1 rounded border border-line px-2 py-1">
            <Link2 :size="13" class="text-ink-placeholder" />
            <input
              placeholder="关联工作项"
              class="w-24 bg-transparent text-xs text-ink-placeholder outline-none"
            />
          </div>
          <label class="flex items-center gap-1 text-xs text-ink-regular">
            <input v-model="continueNext" type="checkbox" class="cursor-pointer" />
            继续新建下一个
          </label>
        </div>
        <!-- 操作按钮 -->
        <div class="flex items-center gap-2">
          <el-button @click="visible = false">取消</el-button>
          <el-button
            type="primary"
            :loading="submitting"
            :disabled="!form.title.trim()"
            @click="submit"
          >新建</el-button>
        </div>
      </div>
    </template>
  </el-dialog>
</template>
