<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import {
  updateProject,
  listProjectMembers,
  removeProjectMember,
  updateProjectMemberRoles
} from '@/api'
import { useProjectStore } from '@/stores/project'
import { useUserStore } from '@/stores/user'
import type { ProjectMember } from '@/api/types'
import { Settings, Users, UserPlus, Trash2, Crown, Pencil } from 'lucide-vue-next'
import UserAvatar from '@/components/common/UserAvatar.vue'
import AddMemberDialog from '@/components/project/AddMemberDialog.vue'
import { ElMessage } from 'element-plus'
import { confirmDelete } from '@/utils/confirm'
import { reportError } from '@/utils/error'

const route = useRoute()
const projectId = computed(() => route.params.projectId as string)
const projectStore = useProjectStore()
const userStore = useUserStore()

const form = ref({ name: '', description: '', status: 'ACTIVE' })
const saving = ref(false)
const members = ref<ProjectMember[]>([])
const loadingMembers = ref(false)

const STATUS_OPTIONS = [
  { value: 'ACTIVE', label: '进行中' },
  { value: 'ARCHIVED', label: '已归档' },
  { value: 'CLOSED', label: '已关闭' }
]

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

/** 当前用户在该项目是否具备成员管理权限（OWNER/ADMIN/PM） */
const canManage = computed(() => {
  const me = userStore.user?.id
  const rs = members.value.find((m) => m.userId === me)?.roles ?? []
  return rs.includes('OWNER') || rs.includes('ADMIN') || rs.includes('PM')
})

async function loadMembers() {
  try {
    members.value = await listProjectMembers(projectId.value)
  } catch {
    // 成员列表加载失败降级为空（接口层已提示错误），不阻断设置页其余功能
    members.value = []
  }
}

async function reloadMembers() {
  loadingMembers.value = true
  try {
    await loadMembers()
  } finally {
    loadingMembers.value = false
  }
}

// ---- 添加成员（弹窗搜索后一键加入） ----
const addDialogVisible = ref(false)

// ---- 移除成员（页内） ----
async function onRemoveMember(m: ProjectMember) {
  try {
    await confirmDelete(`移除成员「${m.displayName}」？`, '确认')
  } catch {
    return
  }
  try {
    await removeProjectMember(projectId.value, m.userId)
    ElMessage.success(`已移除成员「${m.displayName}」`)
    if (editingUser.value === m.userId) editingUser.value = ''
    await loadMembers()
  } catch (e: unknown) {
    reportError(e, '移除成员失败')
  }
}

// ---- 行内编辑角色 ----
const editingUser = ref('')
const editingRoles = ref<string[]>([])
const savingRoles = ref('')

function startEditRoles(m: ProjectMember) {
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
    await updateProjectMemberRoles(projectId.value, m.userId, editingRoles.value)
    ElMessage.success('角色已更新')
    cancelEditRoles()
    await loadMembers()
  } catch (e: unknown) {
    reportError(e, '角色更新失败')
  } finally {
    savingRoles.value = ''
  }
}

async function onSave() {
  if (!form.value.name.trim()) {
    ElMessage.warning('项目名称不能为空')
    return
  }
  saving.value = true
  try {
    await updateProject(projectId.value, {
      name: form.value.name.trim(),
      description: form.value.description,
      status: form.value.status
    })
    ElMessage.success('设置已保存')
    // 强制刷新：否则名称/描述沿用 store 缓存，顶部标题不会更新
    await projectStore.loadCurrent(projectId.value, true)
  } catch (e: unknown) {
    reportError(e, '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await projectStore.loadCurrent(projectId.value)
  if (projectStore.current) {
    form.value = {
      name: projectStore.current.name,
      description: projectStore.current.description,
      status: projectStore.current.status
    }
  }
  await reloadMembers()
})
</script>

<template>
  <div class="h-full overflow-auto p-6">
    <div class="mb-4 flex items-center gap-2">
      <Settings :size="20" class="text-brand" />
      <h1 class="text-lg font-semibold text-ink-900">设置</h1>
    </div>

    <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <!-- 基本设置 -->
      <div class="glass-panel rounded-lg p-5">
        <div class="mb-4 text-sm font-medium text-ink-900">基本信息</div>
        <div class="space-y-4">
          <div>
            <label class="mb-1 block text-sm text-ink-400">项目名称</label>
            <input v-model="form.name" class="w-full rounded-md border border-line-light px-3 py-2 text-sm outline-none focus:border-brand" placeholder="项目名称" />
          </div>
          <div>
            <label class="mb-1 block text-sm text-ink-400">项目描述</label>
            <textarea v-model="form.description" rows="3" class="w-full resize-none rounded-md border border-line-light px-3 py-2 text-sm outline-none focus:border-brand" placeholder="项目描述"></textarea>
          </div>
          <div>
            <label class="mb-1 block text-sm text-ink-400">项目状态</label>
            <select v-model="form.status" aria-label="项目状态" class="w-full rounded-md border border-line-light px-3 py-2 text-sm outline-none focus:border-brand">
              <option v-for="s in STATUS_OPTIONS" :key="s.value" :value="s.value">{{ s.label }}</option>
            </select>
          </div>
          <div>
            <label class="mb-1 block text-sm text-ink-400">项目 Key</label>
            <input :value="projectStore.current?.projectKey || ''" disabled class="w-full cursor-not-allowed rounded-md border border-line-light bg-surface-muted px-3 py-2 text-sm text-ink-400" />
          </div>
          <div class="flex justify-end">
            <button class="rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark disabled:opacity-50" :disabled="saving" @click="onSave">
              {{ saving ? '保存中…' : '保存' }}
            </button>
          </div>
        </div>
      </div>

      <!-- 成员管理（页内直接调整，不再弹窗） -->
      <div class="glass-panel rounded-lg p-5 lg:col-span-2">
        <div class="mb-4 flex items-center justify-between">
          <div class="flex items-center gap-2 text-sm font-medium text-ink-900">
            <Users :size="16" /> 成员管理
            <span class="text-xs font-normal text-ink-400">共 {{ members.length }} 人</span>
          </div>
          <button
            v-if="canManage"
            class="flex items-center gap-1 rounded-md bg-brand px-3 py-1.5 text-xs font-medium text-white transition hover:bg-brand-dark"
            @click="addDialogVisible = true"
          >
            <UserPlus :size="13" /> 添加成员
          </button>
          <span v-else class="text-xs text-ink-400">仅所有者/管理员/项目经理可调整成员</span>
        </div>

        <div v-loading="loadingMembers" class="space-y-2">
          <div v-for="m in members" :key="m.userId" class="rounded-md border border-line-light">
            <div class="flex items-center gap-3 px-3 py-2">
              <UserAvatar :name="m.displayName" :src="m.avatarUrl" :size="32" />
              <div class="min-w-0 flex-1">
                <div class="truncate text-sm text-ink-900">{{ m.displayName }}</div>
                <div class="truncate text-xs text-ink-400">@{{ m.username }}</div>
              </div>
              <div class="flex flex-wrap gap-1">
                <span
                  v-for="r in m.roles"
                  :key="r"
                  class="inline-flex items-center rounded px-2 py-0.5 text-xs"
                  :class="r === 'OWNER' ? 'bg-warning-bg text-warning' : 'bg-surface-muted text-ink-600'"
                >
                  <Crown v-if="r === 'OWNER'" :size="11" class="mr-0.5" />{{ roleLabel(r) }}
                </span>
              </div>
              <template v-if="canManage">
                <button
                  class="flex items-center gap-1 text-xs text-brand transition hover:opacity-80"
                  @click="editingUser === m.userId ? cancelEditRoles() : startEditRoles(m)"
                >
                  <Pencil :size="12" /> {{ editingUser === m.userId ? '收起' : '编辑角色' }}
                </button>
                <button
                  class="text-ink-400 transition hover:text-danger"
                  title="移除成员"
                  @click="onRemoveMember(m)"
                >
                  <Trash2 :size="14" />
                </button>
              </template>
            </div>

            <!-- 行内角色编辑 -->
            <div v-if="canManage && editingUser === m.userId" class="border-t border-line-lighter px-3 py-3">
              <div class="mb-2 text-xs text-ink-400">调整「{{ m.displayName }}」在当前项目中的角色（可多选）</div>
              <div class="flex flex-wrap gap-1.5">
                <label
                  v-for="o in ROLE_OPTIONS"
                  :key="o.value"
                  class="cursor-pointer rounded-full border px-2.5 py-1 text-xs transition"
                  :class="editingRoles.includes(o.value) ? 'border-brand bg-brand-light text-brand' : 'border-line-light text-ink-400 hover:border-brand'"
                >
                  <input v-model="editingRoles" type="checkbox" class="sr-only" :value="o.value" />
                  {{ o.label }}
                </label>
              </div>
              <div class="mt-3 flex justify-end gap-2">
                <button class="rounded-md border border-line-light px-3 py-1.5 text-xs text-ink-600 transition hover:bg-surface-muted" @click="cancelEditRoles">取消</button>
                <button
                  class="rounded-md bg-brand px-3 py-1.5 text-xs font-medium text-white transition hover:bg-brand-dark disabled:opacity-50"
                  :disabled="savingRoles === m.userId || !editingRoles.length"
                  @click="saveRoles(m)"
                >
                  {{ savingRoles === m.userId ? '保存中…' : '保存角色' }}
                </button>
              </div>
            </div>
          </div>

          <div v-if="!members.length && !loadingMembers" class="py-6 text-center text-sm text-ink-400">暂无成员</div>
        </div>
      </div>
    </div>

    <AddMemberDialog v-model:visible="addDialogVisible" :project-id="projectId" @added="reloadMembers" />
  </div>
</template>
