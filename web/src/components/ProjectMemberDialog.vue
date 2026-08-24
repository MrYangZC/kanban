<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  listProjectMembers,
  addProjectMember,
  removeProjectMember,
  updateProjectMemberRoles,
  listUsers
} from '@/api'
import type { ProjectMember, UserVO } from '@/api/types'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Users, UserPlus, Trash2, Crown } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    projectId: string
    /** 当前用户是否可管理成员（OWNER/ADMIN/PM）；未传则按成员列表中的当前用户角色推算 */
    canManage?: boolean
  }>(),
  { canManage: undefined }
)
const visible = defineModel<boolean>('visible', { default: false })
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
const allUsers = ref<UserVO[]>([])
const loading = ref(false)

async function load() {
  if (!props.projectId) return
  loading.value = true
  try {
    members.value = await listProjectMembers(props.projectId)
    if (canManageResolved.value) {
      allUsers.value = await listUsers()
    }
  } finally {
    loading.value = false
  }
}

watch(visible, (v) => {
  if (v) load()
})

const candidateUsers = computed(() =>
  allUsers.value.filter((u) => !members.value.some((m) => m.userId === u.id))
)

const selectedUser = ref<string>('')
const selectedRoles = ref<string[]>(['MEMBER'])

async function onAdd() {
  if (!selectedUser.value) {
    ElMessage.warning('请选择要添加的用户')
    return
  }
  await addProjectMember(props.projectId, selectedUser.value, selectedRoles.value)
  ElMessage.success('已添加成员')
  selectedUser.value = ''
  selectedRoles.value = ['MEMBER']
  await load()
}

async function onRemove(m: ProjectMember) {
  try {
    await ElMessageBox.confirm(`移除成员「${m.displayName}」？`, '确认', { type: 'warning' })
  } catch {
    return
  }
  await removeProjectMember(props.projectId, m.userId)
  ElMessage.success('已移除')
  await load()
}

// 行内编辑角色（popover）
const editingUser = ref<string>('')
const editingRoles = ref<string[]>([])

function startEditRoles(m: ProjectMember) {
  editingUser.value = m.userId
  editingRoles.value = [...m.roles]
}

async function saveRoles(m: ProjectMember) {
  await updateProjectMemberRoles(props.projectId, m.userId, editingRoles.value)
  ElMessage.success('角色已更新')
  editingUser.value = ''
  await load()
}

onMounted(() => {
  if (visible.value) load()
})
</script>

<template>
  <el-dialog v-model="visible" title="项目成员" width="560px">
    <div v-loading="loading">
      <div v-if="!members.length" class="py-8 text-center text-sm text-ink-400">暂无成员</div>
      <ul v-else class="space-y-2">
        <li
          v-for="m in members"
          :key="m.id"
          class="flex items-center gap-3 rounded-lg border border-surface-muted px-3 py-2"
        >
          <div class="flex h-8 w-8 items-center justify-center rounded-full bg-brand text-sm font-medium text-white">
            {{ m.displayName?.charAt(0) || 'U' }}
          </div>
          <div class="min-w-0 flex-1">
            <div class="text-sm font-medium text-ink-900">{{ m.displayName }}</div>
            <div class="text-xs text-ink-400">@{{ m.username }}</div>
            <div class="mt-1 flex flex-wrap gap-1">
              <span
                v-for="r in m.roles"
                :key="r"
                class="rounded px-1.5 py-0.5 text-xs"
                :class="r === 'OWNER' ? 'bg-amber-100 text-amber-700' : 'bg-surface-muted text-ink-500'"
              >
                <Crown v-if="r === 'OWNER'" :size="11" class="mr-0.5 inline" />{{ roleLabel(r) }}
              </span>
            </div>
          </div>
          <el-popover
            v-if="canManageResolved"
            placement="top"
            :width="240"
            trigger="click"
            :disabled="editingUser !== m.userId"
          >
            <template #reference>
              <el-button link type="primary" size="small" @click="startEditRoles(m)">编辑角色</el-button>
            </template>
            <div class="space-y-2">
              <el-select v-model="editingRoles" multiple placeholder="选择角色（可多选）" class="w-full">
                <el-option v-for="o in ROLE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
              </el-select>
              <div class="flex justify-end">
                <el-button size="small" type="primary" @click="saveRoles(m)">保存</el-button>
              </div>
            </div>
          </el-popover>
          <el-button
            v-if="canManageResolved"
            link
            type="danger"
            size="small"
            :icon="Trash2"
            @click="onRemove(m)"
          />
        </li>
      </ul>

      <div v-if="canManageResolved" class="mt-4 border-t border-surface-muted pt-4">
        <div class="flex items-center gap-2">
          <el-select v-model="selectedUser" placeholder="选择用户" class="w-40" :disabled="!candidateUsers.length">
            <el-option
              v-for="u in candidateUsers"
              :key="u.id"
              :label="`${u.displayName} (@${u.username})`"
              :value="u.id"
            />
          </el-select>
          <el-select v-model="selectedRoles" multiple placeholder="选择角色（可多选）" class="flex-1">
            <el-option v-for="o in ROLE_OPTIONS" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <el-button type="primary" :icon="UserPlus" :disabled="!candidateUsers.length" @click="onAdd">添加</el-button>
        </div>
        <div v-if="!candidateUsers.length" class="mt-2 text-xs text-ink-400">
          所有用户均已加入该项目
        </div>
      </div>
    </div>
  </el-dialog>
</template>
