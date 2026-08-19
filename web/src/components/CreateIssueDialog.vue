<script setup lang="ts">
import { ref, reactive, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { createIssue } from '@/api'
import type { Issue } from '@/api/types'
import RichTextEditor from '@/components/RichTextEditor.vue'

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

const form = reactive({
  title: '',
  description: '',
  priority: 'P2',
  severity: 'S3',
})

const submitting = ref(false)

function reset() {
  form.title = ''
  form.description = ''
  form.priority = 'P2'
  form.severity = 'S3'
}

async function submit() {
  if (!form.title.trim()) {
    ElMessage.warning('请输入标题')
    return
  }
  submitting.value = true
  try {
    const issue = await createIssue({
      projectId: props.projectId,
      statusId: props.defaultStatusId,
      issueTypeId: props.defaultIssueTypeId || undefined,
      title: form.title.trim(),
      description: form.description.trim() || undefined,
      priority: form.priority,
      severity: props.defaultIssueTypeCode === 'BUG' ? form.severity : undefined,
    })
    ElMessage.success('已创建')
    visible.value = false
    reset()
    emit('created', issue)
  } catch {
    /* 接口错误时 ElMessage 已由拦截器提示 */
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    :title="title"
    width="640px"
    append-to-body
    align-center
    @close="reset"
  >
    <el-form :model="form" label-width="80px" label-position="right">
      <el-form-item label="标题" required>
        <el-input
          v-model="form.title"
          placeholder="请输入标题（Ctrl+Enter 提交）"
          maxlength="120"
          show-word-limit
          @keyup.ctrl.enter="submit"
        />
      </el-form-item>

      <el-form-item label="描述">
        <RichTextEditor
          v-model="form.description"
          :rows="6"
          placeholder="支持 Markdown，建议填写验收要点、复现步骤等，可 Ctrl+V 粘贴截图"
        />
      </el-form-item>

      <el-form-item label="优先级">
        <el-select v-model="form.priority" class="w-full">
          <el-option label="P0 紧急" value="P0" />
          <el-option label="P1 高" value="P1" />
          <el-option label="P2 中" value="P2" />
          <el-option label="P3 低" value="P3" />
        </el-select>
      </el-form-item>

      <el-form-item v-if="defaultIssueTypeCode === 'BUG'" label="严重程度">
        <el-select v-model="form.severity" class="w-full">
          <el-option label="致命" value="S1" />
          <el-option label="严重" value="S2" />
          <el-option label="一般" value="S3" />
          <el-option label="轻微" value="S4" />
        </el-select>
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button
        type="primary"
        :loading="submitting"
        :disabled="!form.title.trim()"
        @click="submit"
      >创建</el-button>
    </template>
  </el-dialog>
</template>
