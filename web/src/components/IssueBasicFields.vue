<script setup lang="ts">
import { ref } from 'vue'
import { X } from 'lucide-vue-next'
import type { IssueStatusVO, ProjectMember } from '@/api/types'

interface Props {
  modelValue: Record<string, any>
  statuses?: IssueStatusVO[]
  members: ProjectMember[]
  priorityOptions: { v: string; label: string }[]
  severityOptions: { v: string; label: string }[]
  tags: { id: string; name: string; color?: string }[]
  canWrite?: boolean
  showSeverity?: boolean
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
  showType: false,
  issueTypeName: '工作项',
  showStatus: true,
})

const emit = defineEmits<{
  'update:modelValue': [val: Record<string, any>]
  'field-change': [payload: { field: string; payload: { patch: Record<string, any>; clear?: string[] } }]
  'add-tag': []
  'remove-tag': [id: string]
}>()

// 统一弹窗定位（避免抽屉/弹窗容器内定位错位）
const POPPER = "{ strategy: 'fixed', modifiers: [{ name: 'preventOverflow', options: { altAxis: true, boundary: 'viewport', rootBoundary: 'viewport', padding: 8 } }, { name: 'flip', options: { allowedAutoPlacements: ['bottom', 'top'] } }] }"

const PRIMARY = '#409EFF'
const PRIMARY_LIGHT = '#E1F0FF'

const tagInput = ref('')

function memberName(m: ProjectMember) {
  return m.displayName || m.username || m.userId
}
function avatarChar(name: string) {
  return name ? name.trim().charAt(0).toUpperCase() : '?'
}
function avatarColor(name: string) {
  let h = 0
  for (let i = 0; i < name.length; i++) h = (h * 31 + name.charCodeAt(i)) % 360
  return `hsl(${h}, 55%, 50%)`
}

// 向父组件提交单个字段变更
function emitChange(field: string, patch: Record<string, any>, clear?: string[]) {
  emit('field-change', { field, payload: { patch, clear } })
}
// 直接双向绑定（用于不需要即时保存的字段，父组件自行处理）
function set(key: string, val: any) {
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
      <label class="text-sm" style="color:#606266">工作项类型</label>
      <div class="flex items-center gap-1.5 text-sm" style="color:#303133">
        {{ issueTypeName }}
      </div>
    </div>

    <!-- 状态 -->
    <div v-if="showStatus" class="flex items-center justify-between">
      <label class="text-sm" style="color:#606266">状态</label>
      <el-select
        :model-value="modelValue.statusId"
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
      <label class="text-sm" style="color:#606266">负责人<span style="color:#F56C6C">*</span></label>
      <el-select
        :model-value="modelValue.assigneeId"
        size="small"
        :popper-options="POPPER"
        class="w-44"
        filterable
        :disabled="!canWrite"
        @update:model-value="(v:string)=>{ set('assigneeId', v || null); emitChange('负责人', { assigneeId: v || null }, v ? [] : ['assigneeId']) }"
      >
        <template #label="{ value, label }">
          <span v-if="value" class="inline-flex items-center gap-1.5">
            <span class="inline-flex h-5 w-5 items-center justify-center rounded-full text-[11px] font-medium text-white" :style="{ background: avatarColor(label) }">{{ avatarChar(label) }}</span>
            <span>{{ label }}</span>
          </span>
          <span v-else>{{ label }}</span>
        </template>
        <el-option label="未分配" value="" />
        <el-option v-for="m in members" :key="m.userId" :label="memberName(m)" :value="m.userId">
          <div class="flex items-center gap-2">
            <span class="flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[11px] font-medium text-white" :style="{ background: avatarColor(memberName(m)) }">{{ avatarChar(memberName(m)) }}</span>
            <span class="truncate">{{ memberName(m) }}</span>
          </div>
        </el-option>
      </el-select>
    </div>

    <!-- 验证者 -->
    <div class="flex items-center justify-between">
      <label class="text-sm" style="color:#606266">验证者</label>
      <el-select
        :model-value="modelValue.verifierId"
        size="small"
        :popper-options="POPPER"
        class="w-44"
        filterable
        :disabled="!canWrite"
        @update:model-value="(v:string)=>{ set('verifierId', v || null); emitChange('验证者', { verifierId: v || null }, v ? [] : ['verifierId']) }"
      >
        <template #label="{ value, label }">
          <span v-if="value" class="inline-flex items-center gap-1.5">
            <span class="inline-flex h-5 w-5 items-center justify-center rounded-full text-[11px] font-medium text-white" :style="{ background: avatarColor(label) }">{{ avatarChar(label) }}</span>
            <span>{{ label }}</span>
          </span>
          <span v-else>{{ label }}</span>
        </template>
        <el-option label="未指派" value="" />
        <el-option v-for="m in members" :key="m.userId" :label="memberName(m)" :value="m.userId">
          <div class="flex items-center gap-2">
            <span class="flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[11px] font-medium text-white" :style="{ background: avatarColor(memberName(m)) }">{{ avatarChar(memberName(m)) }}</span>
            <span class="truncate">{{ memberName(m) }}</span>
          </div>
        </el-option>
      </el-select>
    </div>

    <!-- 优先级 -->
    <div>
      <label class="mb-1.5 block text-sm" style="color:#606266">优先级<span style="color:#F56C6C">*</span></label>
      <div class="flex flex-wrap gap-1.5">
        <button
          v-for="p in priorityOptions"
          :key="p.v"
          class="flex items-center gap-1 rounded border px-2 py-1 text-xs transition"
          :style="modelValue.priority === p.v
            ? { background: PRIMARY_LIGHT, color: PRIMARY, borderColor: PRIMARY }
            : { background: '#fff', color: '#909399', borderColor: '#dcdfe6' }"
          :disabled="!canWrite"
          @click="set('priority', p.v); emitChange('优先级', { priority: p.v })"
        >
          <span class="h-1.5 w-1.5 rounded-full" :style="{ background: modelValue.priority === p.v ? PRIMARY : '#c0c4cc' }" />
          {{ p.label }}
        </button>
      </div>
    </div>

    <!-- 严重程度 -->
    <div v-if="showSeverity" class="flex items-center justify-between">
      <label class="text-sm" style="color:#606266">严重程度<span style="color:#F56C6C">*</span></label>
      <el-select
        :model-value="modelValue.severity"
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
      <label class="text-sm" style="color:#606266">参与者</label>
      <el-select
        :model-value="modelValue.participants"
        multiple
        filterable
        clearable
        collapse-tags
        size="small"
        :popper-options="POPPER"
        placeholder="请选择"
        class="w-44"
        :disabled="!canWrite"
        @update:model-value="(v:string[])=>{ set('participants', v || []); emitChange('参与者', { participants: v || [] }) }"
      >
        <el-option v-for="m in members" :key="m.userId" :label="memberName(m)" :value="m.userId">
          <div class="flex items-center gap-2">
            <span class="flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[11px] font-medium text-white" :style="{ background: avatarColor(memberName(m)) }">{{ avatarChar(memberName(m)) }}</span>
            <span class="truncate">{{ memberName(m) }}</span>
          </div>
        </el-option>
      </el-select>
    </div>

    <!-- 抄送 -->
    <div>
      <label class="mb-1.5 block text-sm" style="color:#606266">抄送</label>
      <el-select
        :model-value="modelValue.ccUsers"
        multiple
        filterable
        clearable
        collapse-tags
        size="small"
        :popper-options="POPPER"
        placeholder="请选择"
        class="w-full"
        :disabled="!canWrite"
        @update:model-value="(v:string[])=>{ set('ccUsers', v || []); emitChange('抄送', { ccUsers: v || [] }) }"
      >
        <el-option v-for="m in members" :key="m.userId" :label="memberName(m)" :value="m.userId">
          <div class="flex items-center gap-2">
            <span class="flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[11px] font-medium text-white" :style="{ background: avatarColor(memberName(m)) }">{{ avatarChar(memberName(m)) }}</span>
            <span class="truncate">{{ memberName(m) }}</span>
          </div>
        </el-option>
      </el-select>
    </div>

    <!-- 标签 -->
    <div>
      <label class="mb-1.5 block text-sm" style="color:#606266">标签</label>
      <div class="flex flex-wrap items-center gap-1.5 rounded border px-2 py-1.5" style="border-color:#dcdfe6">
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
          class="min-w-[60px] flex-1 bg-transparent text-sm outline-none"
          style="color:#c0c4cc"
          @keyup.enter="emit('add-tag')"
        />
      </div>
    </div>

    <!-- 计划开始时间 -->
    <div class="flex items-center justify-between">
      <label class="text-sm" style="color:#606266">计划开始时间</label>
      <el-date-picker
        :model-value="modelValue.planStart"
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
      <label class="text-sm" style="color:#606266">计划完成时间</label>
      <el-date-picker
        :model-value="modelValue.dueDate"
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
    <div>
      <label class="mb-1 block text-sm" style="color:#606266">不修复理由</label>
      <input
        :model-value="modelValue.noFixReason"
        placeholder="请输入"
        class="w-full rounded border bg-transparent px-2 py-1 text-sm outline-none"
        style="border-color:#dcdfe6;color:#303133"
        :disabled="!canWrite"
        @update:model-value="(v:string)=>set('noFixReason', v || null)"
        @blur="emitChange('不修复理由', { noFixReason: modelValue.noFixReason || null }, modelValue.noFixReason ? [] : ['noFixReason'])"
      />
    </div>
  </div>
</template>
