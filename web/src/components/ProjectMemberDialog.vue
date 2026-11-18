<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  listProjectMembers,
  removeProjectMember,
  updateProjectMemberRoles
} from '@/api'
import type { ProjectMember } from '@/api/types'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'
import { confirmDelete } from '@/utils/confirm'
import { reportError } from '@/utils/error'
import { UserPlus, Trash2, Crown } from 'lucide-vue-next'
import UserAvatar from '@/components/common/UserAvatar.vue'
import AddMemberDialog from '@/components/project/AddMemberDialog.vue'

const props = withDefaults(
  defineProps<{
    projectId: string
    /** 当前用户是否可管理成员（OWNER/ADMIN/PM）；未传则按成员列表中的当前用户角色推算 */
    canManage?: boolean
  }>(),
  { canManage: undefined }
)
const visible = defineModel<boolean>('visible', { default: false })
/** 成员增删后通知调用方（项目列表据此刷新卡片上的成员数） */
const emit = defineEmits<{ (e: 'changed'): void }>()
const userStore = useUserStore()

// RBAC 全局角色字典（与后端 role 表种子数据一致）
const ROLE_OPTIONS = [
  { value: 'OWNER', label: '所有者' },
  { value: 'ADMIN', label: '管理员' },
  { value: 'PM', label: '项目经理' },
  { value: 'DEV', label: '开发' },
  { value: 'QA', label: '测试' },
  { value: 'MEMBER', label: '参与人' },
  { value: 'VIEWER', label: '只读' }
]

function roleLabel(role: string): string {
  return ROLE_OPTIONS.find((o) => o.value === role)?.label ?? role
}

const canManageResolved = computed(() => {
  if (props.canManage !== undefined) return props.canManage
  const me = userStore.user?.id
  const rs = members.value.find((m) => m.userId === me)?.roles ?? []
  return rs.includes('OWNER') || rs.includes('ADMIN') || rs.includes('PM')
})

const members = ref<ProjectMember[]>([])
const loading = ref(false)
const addDialogVisible = ref(false)
/** 添加弹窗是否由成员列表发起：是则关闭后回到成员列表，否则（项目列表快捷入口）直接收场 */
const addFromList = ref(false)

async function load() {
  if (!props.projectId) return
  loading.value = true
  try {
    members.value = await listProjectMembers(props.projectId)
  } finally {
    loading.value = false
  }
}

watch(visible, (v) => {
  if (v) load()
})

async function onRemove(m: ProjectMember) {
  try {
    await confirmDelete(`移除成员「${m.displayName}」？`, '确认')
  } catch {
    return
  }
  await removeProjectMember(props.projectId, m.userId)
  ElMessage.success('已移除')
  await load()
  emit('changed')
}

/** 添加成员成功：刷新成员列表，同时让外层（项目列表卡片）的成员数一起更新 */
function onMemberAdded() {
  load()
  emit('changed')
}

// 行内编辑角色
// 注意：不要用「popover + el-select」组合——el-select 的下拉默认 teleport 到 body，
// 点选选项会被外层 popover 当作「点击外部」而关闭，导致「保存」按钮消失、角色无法提交。
const editingUser = ref<string>('')
const editingRoles = ref<string[]>([])
const savingRoles = ref<string>('')

function toggleEdit(m: ProjectMember) {
  if (editingUser.value === m.userId) {
    cancelEditRoles()
    return
  }
  editingUser.value = m.userId
  editingRoles.value = [...m.roles]
}

function cancelEditRoles() {
  editingUser.value = ''
  editingRoles.value = []
}

async function saveRoles(m: ProjectMember) {
  if (!editingRoles.value.length) {
    ElMessage.warning('请至少保留一个角色')
    return
  }
  savingRoles.value = m.userId
  try {
    await updateProjectMemberRoles(props.projectId, m.userId, editingRoles.value)
    ElMessage.success('角色已更新')
    cancelEditRoles()
    await load()
  } catch (e: unknown) {
    reportError(e, '角色更新失败')
  } finally {
    savingRoles.value = ''
  }
}

onMounted(() => {
  if (visible.value) load()
})

/**
 * 成员列表里的「添加成员」：收起成员列表再开检索弹窗。
 * 两个弹窗同时可见会像「叠了两层」，且下层列表此时没有可操作性，纯属干扰。
 */
function openAddFromList() {
  addFromList.value = true
  addDialogVisible.value = true
  visible.value = false
}

/** 供调用方（如项目卡片的「添加成员」）直接拉起检索弹窗，不经过成员列表 */
function openAddDialog() {
  addFromList.value = false
  addDialogVisible.value = true
}
defineExpose({ openAddDialog })

// 添加弹窗关闭：从成员列表进来的就退回成员列表（顺带刷新，刚加的人立刻可见）
watch(addDialogVisible, (v) => {
  if (v || !addFromList.value) return
  addFromList.value = false
  visible.value = true
})
</script>

<template>
  <el-dialog v-model="visible" title="项目成员" width="640px" :close-on-click-modal="false">
    <div v-loading="loading">
      <div v-if="canManageResolved" class="mb-3 flex justify-end">
        <el-button type="primary" size="small" :icon="UserPlus" @click="openAddFromList">
          添加成员
        </el-button>
      </div>
      <div v-if="!members.length" class="py-8 text-center text-sm text-ink-400">暂无成员</div>
      <ul v-else class="space-y-2">
        <li v-for="m in members" :key="m.id" class="rounded-lg border border-surface-muted">
          <div class="flex items-center gap-3 px-3 py-2">
            <UserAvatar :name="m.displayName" :src="m.avatarUrl" :size="32" />
            <div class="min-w-0 flex-1">
              <div class="text-sm font-medium text-ink-900">{{ m.displayName }}</div>
              <div class="text-xs text-ink-400">@{{ m.username }}</div>
              <div class="mt-1 flex flex-wrap gap-1">
                <span
                  v-for="r in m.roles"
                  :key="r"
                  class="rounded px-1.5 py-0.5 text-xs"
                  :class="r === 'OWNER' ? 'bg-warning-bg text-warning' : 'bg-surface-muted text-ink-600'"
                >
                  <Crown v-if="r === 'OWNER'" :size="11" class="mr-0.5 inline" />{{ roleLabel(r) }}
                </span>
              </div>
            </div>
            <el-button
              v-if="canManageResolved"
              link
              type="primary"
              size="small"
              @click="toggleEdit(m)"
            >
              {{ editingUser === m.userId ? '收起' : '编辑角色' }}
            </el-button>
            <el-button
              v-if="canManageResolved"
              link
              type="danger"
              size="small"
              :icon="Trash2"
              @click="onRemove(m)"
            />
          </div>

          <!-- 行内角色编辑：避免 popover 内嵌 el-select 时点选选项触发「点击外部」而误关外层浮层 -->
          <div
            v-if="canManageResolved && editingUser === m.userId"
            class="border-t border-surface-muted px-3 py-3"
          >
            <div class="mb-2 text-xs text-ink-400">调整「{{ m.displayName }}」的角色（可多选）</div>
            <div class="flex flex-wrap gap-2">
              <label
                v-for="o in ROLE_OPTIONS"
                :key="o.value"
                class="cursor-pointer select-none rounded-full border px-2.5 py-1 text-xs transition"
                :class="
                  editingRoles.includes(o.value)
                    ? 'border-brand bg-brand-light text-brand'
                    : 'border-line-light text-ink-400 hover:border-brand'
                "
              >
                <input v-model="editingRoles" type="checkbox" class="sr-only" :value="o.value" />
                {{ o.label }}
              </label>
            </div>
            <div class="mt-3 flex justify-end gap-2">
              <el-button size="small" @click="cancelEditRoles">取消</el-button>
              <el-button
                size="small"
                type="primary"
                :disabled="!editingRoles.length"
                :loading="savingRoles === m.userId"
                @click="saveRoles(m)"
              >
                保存角色
              </el-button>
            </div>
          </div>
        </li>
      </ul>

    </div>
  </el-dialog>

  <!-- 必须与「项目成员」平级：嵌在 el-dialog 内会出现两个弹窗叠着显示 -->
  <AddMemberDialog v-model:visible="addDialogVisible" :project-id="projectId" @added="onMemberAdded" />
</template>
