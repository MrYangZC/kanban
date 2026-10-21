<script setup lang="ts">
import { computed } from 'vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { POPPER_OPTIONS } from '@/constants/popper'

/**
 * 全站统一的「选一个人」下拉。
 *
 * 此前各页面各写一套：详情页是 el-select + 头像，表格页和风险页只有纯文本，
 * 项目设置里甚至是原生 <select>，同一个动作在四个地方四种长相。
 * 这里按缺陷详情（原 IssueBasicFields）那套定为基准：
 * `el-select` + 20px 圆形头像 + 显示名，可搜索，空选项文案统一。
 */
interface UserLike {
  /** 项目成员用 userId */
  userId?: string
  /** 用户列表用 id */
  id?: string
  displayName?: string | null
  username?: string | null
  avatarUrl?: string | null
}

const props = withDefaults(defineProps<{
  modelValue: string | string[] | null | undefined
  /** 直接喂 ProjectMember[] 或 UserVO[]，两种结构都能识别 */
  users: UserLike[]
  multiple?: boolean
  clearable?: boolean
  filterable?: boolean
  disabled?: boolean
  size?: 'small' | 'default'
  /** 宽度交给调用方，便于放进表格单元格 / 表单行 */
  widthClass?: string
  placeholder?: string
  /** 首个空选项文案（如「未分配」）；不传则不提供空选项 */
  emptyLabel?: string
  /** 下拉项附带 @username，用于区分同名用户（添加成员场景） */
  showUsername?: boolean
}>(), {
  multiple: false,
  clearable: false,
  filterable: true,
  disabled: false,
  size: 'small',
  widthClass: 'w-44',
  placeholder: '请选择',
  emptyLabel: '',
  showUsername: false
})

const emit = defineEmits<{
  'update:modelValue': [value: string | string[] | null]
  change: [value: string | string[] | null]
}>()

// 抽屉/弹窗里 popper 默认 relative 会被容器裁剪，统一 fixed 策略
const POPPER = POPPER_OPTIONS

const options = computed(() => props.users.map((u) => {
  const value = u.userId || u.id || ''
  return {
    value,
    // 显示名口径与动态/评论一致：display_name 优先
    label: u.displayName || u.username || value,
    username: u.username || '',
    avatarUrl: u.avatarUrl || ''
  }
}))

const inner = computed(() => {
  if (props.multiple) return Array.isArray(props.modelValue) ? props.modelValue : []
  return props.modelValue || ''
})

/** 选中态要显示头像，但 #label 插槽只给名字，按 value 反查（比按名字查更准，能应付同名） */
function avatarOf(value: string) {
  return options.value.find((o) => o.value === value)?.avatarUrl || ''
}

function onUpdate(v: string | string[] | undefined) {
  const next = props.multiple ? (Array.isArray(v) ? v : []) : (v || null)
  emit('update:modelValue', next)
  emit('change', next)
}
</script>

<template>
  <el-select
    :model-value="inner"
    :multiple="multiple"
    :clearable="clearable"
    :filterable="filterable"
    :collapse-tags="multiple"
    :size="size"
    :popper-options="POPPER"
    :placeholder="placeholder"
    :class="widthClass"
    :disabled="disabled"
    @update:model-value="onUpdate"
  >
    <!-- 单选：选中项里也带头像，与下拉项一致 -->
    <template v-if="!multiple" #label="{ value, label }">
      <span v-if="value" class="inline-flex min-w-0 items-center gap-1.5">
        <UserAvatar :name="label" :src="avatarOf(value)" :size="20" />
        <span class="truncate">{{ label }}</span>
      </span>
      <span v-else>{{ label }}</span>
    </template>

    <el-option v-if="emptyLabel" :label="emptyLabel" value="" />
    <el-option v-for="o in options" :key="o.value" :label="o.label" :value="o.value">
      <div class="flex items-center gap-2">
        <UserAvatar :name="o.label" :src="o.avatarUrl" :size="20" />
        <span class="min-w-0 flex-1 truncate">{{ o.label }}</span>
        <span
          v-if="showUsername && o.username && o.username !== o.label"
          class="shrink-0 text-xs text-ink-400"
        >@{{ o.username }}</span>
      </div>
    </el-option>
  </el-select>
</template>
