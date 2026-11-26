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
import { Settings, Users, UserPlus, Trash2, Crown, CalendarClock, KeyRound, Hash } from 'lucide-vue-next'
import UserAvatar from '@/components/common/UserAvatar.vue'
import AddMemberDialog from '@/components/project/AddMemberDialog.vue'
import { projectStatusBadge } from '@/constants/issueDict'
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

/** el-table 的插槽/事件给的是宽松行类型，入口统一收窄 */
const asMember = (raw: unknown) => raw as ProjectMember

function roleLabel(role: string): string {
  return ROLE_OPTIONS.find((o) => o.value === role)?.label ?? role
}

/**
 * 角色配色：一个角色一种颜色，扫视时能直接分辨谁有管理权。
 * 用标准调色板（amber/violet/sky/emerald/slate）而不是 bg-warning-bg 这类自定义 token——
 * 后者在当前 Tailwind 配置下没有生成对应类，背景是透明的（实测 computed = rgba(0,0,0,0)）。
 */
const ROLE_CLASS: Record<string, string> = {
  OWNER: 'bg-amber-50 text-amber-700 ring-1 ring-amber-200',
  ADMIN: 'bg-violet-50 text-violet-700 ring-1 ring-violet-200',
  PM: 'bg-sky-50 text-sky-700 ring-1 ring-sky-200',
  DEV: 'bg-emerald-50 text-emerald-700 ring-1 ring-emerald-200',
  QA: 'bg-orange-50 text-orange-700 ring-1 ring-orange-200',
  MEMBER: 'bg-slate-100 text-slate-600',
  VIEWER: 'bg-slate-100 text-slate-500'
}

function roleClass(role: string): string {
  return ROLE_CLASS[role] ?? 'bg-slate-100 text-slate-600'
}

/** 当前用户在该项目是否具备成员管理权限（OWNER/ADMIN/PM） */
const canManage = computed(() => {
  const me = userStore.user?.id
  const rs = members.value.find((m) => m.userId === me)?.roles ?? []
  return rs.includes('OWNER') || rs.includes('ADMIN') || rs.includes('PM')
})

/** 右侧信息卡的数据：全部来自 project store，不额外请求 */
const current = computed(() => projectStore.current)
const statusBadge = computed(() => projectStatusBadge(form.value.status))
const createdAt = computed(() => current.value?.createdAt?.slice(0, 10) || '—')

/**
 * 是否有未保存的改动。
 * 表单原先无论改没改都能点保存，点了还会提示"设置已保存"，
 * 让人以为改了东西；这里按值与当前项目比对，没改动时按钮置灰。
 */
const dirty = computed(() => {
  const p = current.value
  if (!p) return false
  return (
    form.value.name.trim() !== (p.name || '').trim() ||
    (form.value.description || '') !== (p.description || '') ||
    form.value.status !== p.status
  )
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
    expandedKeys.value = expandedKeys.value.filter((k) => k !== m.userId)
    await loadMembers()
  } catch (e: unknown) {
    reportError(e, '移除成员失败')
  }
}

// ---- 行内展开编辑角色 ----
// 用 el-table 的展开行承载角色编辑：比"卡片套卡片"紧凑，也不会把 7 个复选框全铺在列表里
const expandedKeys = ref<string[]>([])
const editingRoles = ref<string[]>([])
const savingRoles = ref('')

function onExpandChange(row: unknown, expanded: unknown) {
  const member = asMember(row)
  // Element 的 expand-change 在不同调用场景下回调第二参可能是布尔或行数组，两种都兼容
  const isExpanded = Array.isArray(expanded)
    ? (expanded as ProjectMember[]).some((r) => r.userId === member.userId)
    : Boolean(expanded)
  // 展开时用该成员当前角色初始化勾选；收起时清空，避免下次展开带出上一个人的选择
  editingRoles.value = isExpanded ? [...member.roles] : []
}

function toggleRoles(m: ProjectMember) {
  expandedKeys.value = expandedKeys.value.includes(m.userId)
    ? expandedKeys.value.filter((k) => k !== m.userId)
    : [...expandedKeys.value, m.userId]
  if (expandedKeys.value.includes(m.userId)) {
    editingRoles.value = [...m.roles]
  }
}

function cancelEditRoles() {
  expandedKeys.value = []
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

/** 重置：把表单回填成服务端当前值（丢弃未保存的修改） */
function resetForm() {
  const p = current.value
  if (!p) return
  form.value = { name: p.name, description: p.description, status: p.status }
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
  <div class="h-full overflow-auto">
    <div class="mx-auto w-full max-w-[1180px] px-6 py-6">
      <!-- 页头：标题 + 归属项目，明确"这一页改的是哪个项目" -->
      <header class="mb-5 flex items-start gap-3">
        <div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary-50 text-primary-600">
          <Settings :size="18" />
        </div>
        <div class="min-w-0">
          <h1 class="text-lg font-semibold text-ink">项目设置</h1>
          <p class="mt-0.5 truncate text-xs text-ink-secondary">
            {{ current?.name || '—' }} · {{ current?.projectKey || '—' }} · 管理基本信息与成员权限
          </p>
        </div>
      </header>

      <div class="grid grid-cols-1 gap-5 lg:grid-cols-3">
        <!-- 基本信息：占两栏，字段两列排布，描述整行 -->
        <section class="glass-panel overflow-hidden rounded-xl lg:col-span-2">
          <div class="flex items-center gap-2 border-b border-line-lighter px-5 py-3">
            <span class="wi-card-title">基本信息</span>
            <span v-if="dirty" class="rounded-full bg-warning-bg px-2 py-0.5 text-[11px] text-warning">未保存</span>
          </div>
          <el-form label-position="top" class="p-5" @submit.prevent>
            <div class="grid grid-cols-1 gap-x-5 sm:grid-cols-2">
              <el-form-item label="项目名称" required>
                <el-input v-model="form.name" maxlength="60" show-word-limit placeholder="请输入项目名称" />
              </el-form-item>
              <el-form-item label="项目状态">
                <el-select v-model="form.status" class="w-full" placeholder="请选择状态">
                  <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :label="s.label" :value="s.value" />
                </el-select>
              </el-form-item>
            </div>
            <el-form-item label="项目描述">
              <el-input
                v-model="form.description"
                type="textarea"
                :rows="3"
                maxlength="200"
                show-word-limit
                resize="none"
                placeholder="一句话说明这个项目做什么（可选）"
              />
            </el-form-item>
            <el-form-item label="项目 Key">
              <el-input :model-value="current?.projectKey || ''" disabled>
                <template #prefix><KeyRound :size="13" /></template>
              </el-input>
              <div class="mt-1 text-xs text-ink-secondary">创建后不可修改，用于工作项编号前缀（如 P161-3189）</div>
            </el-form-item>
            <div class="mt-1 flex items-center justify-end gap-2">
              <span v-if="dirty" class="text-xs text-ink-secondary">有未保存的修改</span>
              <el-button :disabled="saving || !dirty" @click="resetForm">重置</el-button>
              <el-button type="primary" :loading="saving" :disabled="saving || !dirty" @click="onSave">
                保存
              </el-button>
            </div>
          </el-form>
        </section>

        <!-- 项目信息：只读摘要，把"值"从表单里拆出来，避免表单字段又当展示又当编辑 -->
        <section class="glass-panel h-fit overflow-hidden rounded-xl">
          <div class="border-b border-line-lighter px-5 py-3">
            <span class="wi-card-title">项目信息</span>
          </div>
          <dl class="divide-y divide-line-lighter">
            <div class="flex items-center justify-between gap-3 px-5 py-3">
              <dt class="flex items-center gap-1.5 text-xs text-ink-secondary"><Hash :size="13" />项目 Key</dt>
              <dd><span class="project-key">{{ current?.projectKey || '—' }}</span></dd>
            </div>
            <div class="flex items-center justify-between gap-3 px-5 py-3">
              <dt class="text-xs text-ink-secondary">当前状态</dt>
              <dd>
                <span class="project-badge" :class="statusBadge.cls">
                  <span class="h-1.5 w-1.5 rounded-full" :class="statusBadge.dot"></span>
                  {{ statusBadge.label }}
                </span>
              </dd>
            </div>
            <div class="flex items-center justify-between gap-3 px-5 py-3">
              <dt class="flex items-center gap-1.5 text-xs text-ink-secondary"><Users :size="13" />成员</dt>
              <dd class="text-sm font-medium text-ink">{{ members.length }} 人</dd>
            </div>
            <div class="flex items-center justify-between gap-3 px-5 py-3">
              <dt class="flex items-center gap-1.5 text-xs text-ink-secondary"><CalendarClock :size="13" />创建于</dt>
              <dd class="text-sm text-ink-secondary">{{ createdAt }}</dd>
            </div>
          </dl>
        </section>

        <!-- 成员管理：卡片头 + 表格，行内展开改角色 -->
        <section class="glass-panel overflow-hidden rounded-xl lg:col-span-3">
          <div class="flex items-center gap-2 border-b border-line-lighter px-5 py-3">
            <Users :size="15" class="text-primary-600" />
            <span class="wi-card-title">成员管理</span>
            <span class="count-pill">{{ members.length }}</span>
            <div class="ml-auto flex items-center gap-2">
              <span v-if="!canManage" class="text-xs text-ink-secondary">仅所有者/管理员/项目经理可调整成员</span>
              <el-button v-else type="primary" size="small" :icon="UserPlus" @click="addDialogVisible = true">
                添加成员
              </el-button>
            </div>
          </div>

          <div class="wi-table project-table border-t border-line-lighter">
          <el-table
            v-loading="loadingMembers"
            :data="members"
            row-key="userId"
            class="wi-el-table"
            :expand-row-keys="expandedKeys"
            @expand-change="onExpandChange"
          >
            <el-table-column type="expand" width="40">
              <template #default="{ row }">
                <div class="bg-surface-page/60 px-5 py-4">
                  <div class="mb-2 text-xs text-ink-secondary">
                    调整「{{ row.displayName }}」在当前项目中的角色（可多选）
                  </div>
                  <el-checkbox-group v-model="editingRoles">
                    <el-checkbox v-for="o in ROLE_OPTIONS" :key="o.value" :value="o.value" class="mr-4">
                      {{ o.label }}
                    </el-checkbox>
                  </el-checkbox-group>
                  <div class="mt-3 flex justify-end gap-2">
                    <el-button size="small" @click="cancelEditRoles">取消</el-button>
                    <el-button
                      size="small"
                      type="primary"
                      :loading="savingRoles === row.userId"
                      :disabled="!editingRoles.length"
                      @click="saveRoles(asMember(row))"
                    >
                      保存角色
                    </el-button>
                  </div>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="成员" min-width="200">
              <template #default="{ row }">
                <div class="flex items-center gap-2.5">
                  <UserAvatar :name="row.displayName" :src="row.avatarUrl" :size="28" />
                  <div class="min-w-0">
                    <div class="truncate text-[13px] font-medium text-ink">{{ row.displayName }}</div>
                    <div class="truncate text-[11px] text-ink-secondary">@{{ row.username }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="角色" min-width="220">
              <template #default="{ row }">
                <div class="flex flex-wrap gap-1">
                  <span
                    v-for="r in row.roles"
                    :key="r"
                    class="inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium"
                    :class="roleClass(r)"
                  >
                    <Crown v-if="r === 'OWNER'" :size="10" class="mr-0.5" />{{ roleLabel(r) }}
                  </span>
                </div>
              </template>
            </el-table-column>

            <el-table-column label="加入时间" width="120">
              <template #default="{ row }">
                <span class="project-date">{{ asMember(row).createdAt?.slice(0, 10) || '—' }}</span>
              </template>
            </el-table-column>

            <el-table-column v-if="canManage" label="操作" width="150" align="right">
              <template #default="{ row }">
                <div class="flex items-center justify-end gap-1">
                  <button class="project-icon-btn" title="编辑角色" @click.stop="toggleRoles(asMember(row))">
                    <Crown :size="14" />
                  </button>
                  <button class="project-icon-btn hover:!text-danger" title="移除成员" @click.stop="onRemoveMember(asMember(row))">
                    <Trash2 :size="14" />
                  </button>
                  <button class="project-enter" @click.stop="toggleRoles(asMember(row))">
                    角色
                  </button>
                </div>
              </template>
            </el-table-column>

            <template #empty>
              <div class="py-10 text-center text-sm text-ink-secondary">暂无成员</div>
            </template>
          </el-table>
          </div>
        </section>
      </div>

      <AddMemberDialog v-model:visible="addDialogVisible" :project-id="projectId" @added="reloadMembers" />
    </div>
  </div>
</template>
