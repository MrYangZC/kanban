<script setup lang="ts">
import { ref } from 'vue'
import { X } from 'lucide-vue-next'
import UserSelect from '@/components/common/UserSelect.vue'
import { POPPER_OPTIONS } from '@/constants/popper'
import type { IssueStatusVO, ProjectMember } from '@/api/types'
import { PRIMARY } from '@/theme/colorMap'

interface Props {
  /**
   * 基础字段表单模型。字段集合随工作项类型（缺陷/需求/任务）与后端类型配置增减，
   * 且父组件的表单对象是 `reactive` 动态对象（如 dueDate 允许 null），
   * 因此这里保持"可扩展字段袋"语义，由父组件的 field-change 处理器决定落库字段。
   */
  modelValue: Record<string, unknown>
  statuses?: IssueStatusVO[]
  members: ProjectMember[]
  priorityOptions: { v: string; label: string }[]
  severityOptions: { v: string; label: string }[]
  tags: { id: string; name: string; color?: string }[]
  canWrite?: boolean
  showSeverity?: boolean
  /** 是否显示不修复理由（缺陷专属） */
  showNoFixReason?: boolean
  /** 是否显示工作项类型行 */
  showType?: boolean
  issueTypeName?: string
  /** 是否显示状态行（详情页显示，新建弹窗用预设状态故隐藏） */
  showStatus?: boolean
}
const props = withDefaults(defineProps<Props>(), {
  statuses: () => [] as IssueStatusVO[],
  canWrite: true,
  showSeverity: true,
  showNoFixReason: false,
  showType: false,
  issueTypeName: '工作项',
  showStatus: true,
})

const emit = defineEmits<{
  'update:modelValue': [val: Record<string, unknown>]
  'field-change': [payload: { field: string; payload: { patch: Record<string, unknown>; clear?: string[] } }]
  'add-tag': []
  'remove-tag': [id: string]
}>()

// 统一弹窗定位（避免抽屉/弹窗容器内定位错位）
const POPPER = POPPER_OPTIONS

/**
 * modelValue 是"可扩展字段袋"（Record<string, unknown>），直接绑定到 el-select / el-date-picker
 * 时值是 unknown，与组件要求的字面量类型不兼容。这里按字段收窄：非字符串一律当作未设置。
 */
function strField(key: string): string | undefined {
  const v = props.modelValue[key]
  return typeof v === 'string' ? v : undefined
}



const tagInput = ref('')

// 向父组件提交单个字段变更
function emitChange(field: string, patch: Record<string, unknown>, clear?: string[]) {
  emit('field-change', { field, payload: { patch, clear } })
}
// 直接双向绑定（用于不需要即时保存的字段，父组件自行处理）
function set(key: string, val: unknown) {
  emit('update:modelValue', { ...props.modelValue, [key]: val })
}

// 供父组件读取标签输入并清空
function clearTagInput() {
  tagInput.value = ''
}
defineExpose({ tagInput, clearTagInput })
</script>

<template>
  <div class="space-y-4">
    <!-- 工作项类型 -->
    <div v-if="showType" class="flex items-center justify-between">
      <label class="text-sm text-ink-regular">工作项类型</label>
      <div class="flex items-center gap-1.5 text-sm text-ink">
        {{ issueTypeName }}
      </div>
    </div>

    <!-- 状态 -->
    <div v-if="showStatus" class="flex items-center justify-between">
      <label class="text-sm text-ink-regular">状态</label>
      <el-select
        :model-value="strField('statusId')"
        size="small"
        :popper-options="POPPER"
        class="w-44"
        :disabled="!canWrite"
        @update:model-value="(v:string)=>{ set('statusId', v); emitChange('状态', { statusId: v }) }"
      >
        <el-option v-for="s in statuses" :key="s.id" :label="s.name" :value="s.id" />
      </el-select>
    </div>

    <!-- 负责人 -->
    <div class="flex items-center justify-between">
      <label class="text-sm text-ink-regular">负责人<span class="text-danger">*</span></label>
      <UserSelect
        :model-value="modelValue.assigneeId as string | null"
        :users="members"
        empty-label="未分配"
        :disabled="!canWrite"
        @update:model-value="(v)=>{ set('assigneeId', v); emitChange('负责人', { assigneeId: v }, v ? [] : ['assigneeId']) }"
      />
    </div>

    <!-- 验证者 -->
    <div class="flex items-center justify-between">
      <label class="text-sm text-ink-regular">验证者</label>
      <UserSelect
        :model-value="modelValue.verifierId as string | null"
        :users="members"
        empty-label="未指派"
        :disabled="!canWrite"
        @update:model-value="(v)=>{ set('verifierId', v); emitChange('验证者', { verifierId: v }, v ? [] : ['verifierId']) }"
      />
    </div>

    <!-- 优先级 -->
    <div>
      <label class="mb-1.5 block text-sm text-ink-regular">优先级<span class="text-danger">*</span></label>
      <div class="flex flex-wrap gap-1.5">
        <button
          v-for="p in priorityOptions"
          :key="p.v"
          class="flex items-center gap-1 rounded border px-2 py-1 text-xs transition"
          :class="modelValue.priority === p.v
            ? 'border-primary bg-primary-light text-primary'
            : 'border-line bg-white/60 text-ink-secondary'"
          :disabled="!canWrite"
          @click="set('priority', p.v); emitChange('优先级', { priority: p.v })"
        >
          <span class="h-1.5 w-1.5 rounded-full" :class="modelValue.priority === p.v ? 'bg-primary' : 'bg-ink-placeholder'" />
          {{ p.label }}
        </button>
      </div>
    </div>

    <!-- 严重程度 -->
    <div v-if="showSeverity" class="flex items-center justify-between">
      <label class="text-sm text-ink-regular">严重程度<span class="text-danger">*</span></label>
      <el-select
        :model-value="strField('severity')"
        size="small"
        :popper-options="POPPER"
        class="w-44"
        :disabled="!canWrite"
        @update:model-value="(v:string)=>{ set('severity', v); emitChange('严重程度', { severity: v }) }"
      >
        <el-option v-for="s in severityOptions" :key="s.v" :label="s.label" :value="s.v" />
      </el-select>
    </div>

    <!-- 参与者 -->
    <div class="flex items-center justify-between">
      <label class="text-sm text-ink-regular">参与者</label>
      <UserSelect
        :model-value="modelValue.participants as string[]"
        :users="members"
        multiple
        clearable
        :disabled="!canWrite"
        @update:model-value="(v)=>{ set('participants', v); emitChange('参与者', { participants: v }) }"
      />
    </div>

    <!-- 抄送 -->
    <div>
      <label class="mb-1.5 block text-sm text-ink-regular">抄送</label>
      <UserSelect
        :model-value="modelValue.ccUsers as string[]"
        :users="members"
        multiple
        clearable
        width-class="w-full"
        :disabled="!canWrite"
        @update:model-value="(v)=>{ set('ccUsers', v); emitChange('抄送', { ccUsers: v }) }"
      />
    </div>

    <!-- 标签 -->
    <div>
      <label class="mb-1.5 block text-sm text-ink-regular">标签</label>
      <div class="flex flex-wrap items-center gap-1.5 rounded border border-line px-2 py-1.5">
        <span
          v-for="t in tags"
          :key="t.id"
          class="flex items-center gap-1 rounded px-2 py-0.5 text-xs text-white"
          :style="{ background: t.color || PRIMARY }"
        >
          {{ t.name }}
          <button class="hover:opacity-70" @click="emit('remove-tag', t.id)"><X :size="11" /></button>
        </span>
        <input
          v-model="tagInput"
          placeholder="加标签"
          class="min-w-[60px] flex-1 bg-transparent text-sm text-ink-placeholder outline-none"
          @keyup.enter="emit('add-tag')"
        />
      </div>
    </div>

    <!-- 计划开始时间 -->
    <div class="flex items-center justify-between">
      <label class="text-sm text-ink-regular">计划开始时间</label>
      <el-date-picker
        :model-value="strField('planStart')"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="请选择"
        size="small"
        class="w-44"
        :popper-options="POPPER"
        :disabled="!canWrite"
        :clearable="true"
        @update:model-value="(v:string)=>{ set('planStart', v || null); emitChange('计划开始时间', { planStart: v || null }, v ? [] : ['planStart']) }"
      />
    </div>

    <!-- 计划完成时间 -->
    <div class="flex items-center justify-between">
      <label class="text-sm text-ink-regular">计划完成时间</label>
      <el-date-picker
        :model-value="strField('dueDate')"
        type="date"
        value-format="YYYY-MM-DD"
        placeholder="请选择"
        size="small"
        class="w-44"
        :popper-options="POPPER"
        :disabled="!canWrite"
        :clearable="true"
        @update:model-value="(v:string)=>{ set('dueDate', v || null); emitChange('截止时间', { dueDate: v || null }, v ? [] : ['dueDate']) }"
      />
    </div>

    <!-- 不修复理由 -->
    <div v-if="showNoFixReason">
      <label class="mb-1 block text-sm text-ink-regular">不修复理由</label>
      <input
        :model-value="modelValue.noFixReason"
        placeholder="请输入"
        class="w-full rounded border border-line bg-transparent px-2 py-1 text-sm text-ink outline-none"
        :disabled="!canWrite"
        @update:model-value="(v:string)=>set('noFixReason', v || null)"
        @blur="emitChange('不修复理由', { noFixReason: modelValue.noFixReason || null }, modelValue.noFixReason ? [] : ['noFixReason'])"
      />
    </div>
  </div>
</template>
