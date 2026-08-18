<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import {
  listProjectMembers,
  addProjectMember,
  removeProjectMember,
  updateProjectMemberRole,
  listUsers
} from '@/api'
import type { ProjectMember, UserVO } from '@/api/types'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Users, UserPlus, Trash2, Crown } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    projectId: string
    /** 当前用户是否为项目 OWNER，决定是否可管理成员；未传则按成员列表中的当前用户角色推算 */
    canManage?: boolean
  }>(),
  { canManage: undefined }
)
const visible = defineModel<boolean>('visible', { default: false })
const userStore = useUserStore()

const canManageResolved = computed(() => {
  if (props.canManage !== undefined) return props.canManage
  const me = userStore.user?.id
  return members.value.some((m) => m.userId === me && m.role === 'OWNER')
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
const selectedRole = ref<'MEMBER' | 'OWNER'>('MEMBER')

async function onAdd() {
  if (!selectedUser.value) {
    ElMessage.warning('请选择要添加的用户')
    return
  }
  await addProjectMember(props.projectId, selectedUser.value, selectedRole.value)
  ElMessage.success('已添加成员')
  selectedUser.value = ''
  selectedRole.value = 'MEMBER'
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

async function onChangeRole(m: ProjectMember) {
  const next = m.role === 'OWNER' ? 'MEMBER' : 'OWNER'
  await updateProjectMemberRole(props.projectId, m.userId, next)
  ElMessage.success('角色已更新')
  await load()
}

onMounted(() => {
  if (visible.value) load()
})
</script>

<template>
  <el-dialog v-model="visible" title="项目成员" width="520px">
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
          <div class="flex-1">
            <div class="text-sm font-medium text-ink-900">{{ m.displayName }}</div>
            <div class="text-xs text-ink-400">@{{ m.username }}</div>
          </div>
          <span
            class="rounded px-1.5 py-0.5 text-xs"
            :class="m.role === 'OWNER' ? 'bg-amber-100 text-amber-700' : 'bg-surface-muted text-ink-500'"
          >
            <Crown v-if="m.role === 'OWNER'" :size="11" class="mr-0.5 inline" />{{ m.role }}
          </span>
          <el-button
            v-if="canManageResolved"
            link
            type="primary"
            size="small"
            @click="onChangeRole(m)"
          >{{ m.role === 'OWNER' ? '降为成员' : '设为所有者' }}</el-button>
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

      <div v-if="canManageResolved" class="mt-4 flex items-center gap-2 border-t border-surface-muted pt-4">
        <el-select v-model="selectedUser" placeholder="选择用户" class="flex-1" :disabled="!candidateUsers.length">
          <el-option
            v-for="u in candidateUsers"
            :key="u.id"
            :label="`${u.displayName} (@${u.username})`"
            :value="u.id"
          />
        </el-select>
        <el-select v-model="selectedRole" class="w-28">
          <el-option label="成员" value="MEMBER" />
          <el-option label="所有者" value="OWNER" />
        </el-select>
        <el-button type="primary" :icon="UserPlus" :disabled="!candidateUsers.length" @click="onAdd">添加</el-button>
      </div>
      <div v-if="canManageResolved && !candidateUsers.length" class="mt-2 text-xs text-ink-400">
        所有用户均已加入该项目
      </div>
    </div>
  </el-dialog>
</template>
