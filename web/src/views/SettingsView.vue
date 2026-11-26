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
import { Settings, Users, UserPlus, Trash2, Crown, KeyRound, Info } from 'lucide-vue-next'
import ProjectInfoCard from '@/components/overview/ProjectInfoCard.vue'
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

/** el-table 的插槽/事件给的是宽松行类型，入口统一收窄 */
const asMember = (raw: unknown) => raw as ProjectMember

function roleLabel(role: string): string {
  return ROLE_OPTIONS.find((o) => o.value === role)?.label ?? role
}

/**
 * 角色配色：一个角色一种颜色，扫视时能直接分辨谁有管理权。
 * 一律用主题令牌 + 透明度修饰符（bg-primary/10 等），深色皮肤下才不会出现
 * 「浅黄底 + 深黄字」这种在暗底上刺眼的白块。
 */
const ROLE_CLASS: Record<string, string> = {
  OWNER: 'bg-warning/10 text-warning ring-1 ring-inset ring-warning/25',
  ADMIN: 'bg-primary/10 text-primary ring-1 ring-inset ring-primary/25',
  PM: 'bg-primary/10 text-primary-dark ring-1 ring-inset ring-primary/20',
  DEV: 'bg-success/10 text-success ring-1 ring-inset ring-success/25',
  QA: 'bg-danger/10 text-danger ring-1 ring-inset ring-danger/25',
  MEMBER: 'bg-surface-light text-ink-regular',
  VIEWER: 'bg-surface-light text-ink-secondary'
}

function roleClass(role: string): string {
  return ROLE_CLASS[role] ?? 'bg-surface-light text-ink-regular'
}

/** 当前用户在该项目是否具备成员管理权限（OWNER/ADMIN/PM） */
const canManage = computed(() => {
  const me = userStore.user?.id
  const rs = members.value.find((m) => m.userId === me)?.roles ?? []
  return rs.includes('OWNER') || rs.includes('ADMIN') || rs.includes('PM')
})

/** 右侧信息卡的数据：全部来自 project store，不额外请求 */
const current = computed(() => projectStore.current)

/**
 * 是否有未保存的改动。
 * 表单原先无论改没改都能点保存，点了还会提示"设置已保存"，
 * 让人以为改了东西；这里按值与当前项目比对，没改动时按钮置灰。
 */
/** 角色分布：右栏用，纯前端从成员列表统计，不额外请求接口 */
const roleDistribution = computed(() =>
  ROLE_OPTIONS.map((o) => ({ ...o, count: members.value.filter((m) => m.roles.includes(o.value)).length }))
    .filter((o) => o.count > 0)
)

/** 项目负责人：取成员里带 OWNER 角色的人，供顶部项目信息卡展示 */
const leadName = computed(() => {
  const owner = members.value.find((m) => m.roles.includes('OWNER'))
  return owner?.displayName || ''
})

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
  <!-- 与概览页同一套骨架（p-6 全宽 + space-y-4 + glass-panel rounded-lg），
       差别只在内容用左右两栏：左栏放需要操作的（表单、成员），右栏放只读概览 -->
  <div class="h-full overflow-auto p-6">
    <div class="space-y-4">
      <ProjectInfoCard v-if="current" :project="current" :lead-name="leadName" />

      <div class="grid grid-cols-1 items-start gap-4 xl:grid-cols-3">
        <!-- 左栏：需要交互的内容 -->
        <div class="space-y-4 xl:col-span-2">
          <!-- 基本信息 -->
          <section class="glass-panel rounded-lg">
            <div class="flex items-center justify-between border-b border-line-light px-4 py-3">
              <div class="flex items-center gap-2 text-sm font-medium text-ink-900">
                <Settings :size="16" class="text-brand" /> 基本信息
                <span v-if="dirty" class="rounded-full bg-amber-50 px-2 py-0.5 text-[11px] text-amber-700">未保存</span>
              </div>
              <span class="text-xs text-ink-400">修改后点「保存」生效</span>
            </div>
            <div class="p-4">
              <el-form label-position="top" @submit.prevent>
                <div class="grid grid-cols-1 gap-x-4 sm:grid-cols-2">
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
                  <div class="w-full">
                    <el-input :model-value="current?.projectKey || ''" disabled class="sm:max-w-xs">
                      <template #prefix><KeyRound :size="13" /></template>
                    </el-input>
                    <div class="mt-1 text-xs text-ink-400">创建后不可修改，用于工作项编号前缀（如 P161-3189）</div>
                  </div>
                </el-form-item>
                <div class="flex items-center justify-end gap-2 border-t border-line-light pt-4">
                  <span v-if="dirty" class="mr-auto text-xs text-ink-400">有未保存的修改</span>
                  <el-button :disabled="saving || !dirty" @click="resetForm">重置</el-button>
                  <el-button type="primary" :loading="saving" :disabled="saving || !dirty" @click="onSave">保存</el-button>
                </div>
              </el-form>
            </div>
          </section>

          <!-- 成员管理 -->
          <section class="glass-panel overflow-hidden rounded-lg">
            <div class="flex items-center justify-between border-b border-line-light px-4 py-3">
              <div class="flex items-center gap-2 text-sm font-medium text-ink-900">
                <Users :size="16" class="text-brand" /> 成员管理
                <span class="text-xs font-normal text-ink-400">共 {{ members.length }} 人</span>
              </div>
              <el-button v-if="canManage" type="primary" size="small" :icon="UserPlus" @click="addDialogVisible = true">
                添加成员
              </el-button>
              <span v-else class="text-xs text-ink-400">仅所有者/管理员/项目经理可调整成员</span>
            </div>

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
                    <div class="mb-2 flex items-center gap-1.5 text-xs text-ink-400">
                      <Info :size="13" /> 调整「{{ asMember(row).displayName }}」在当前项目中的角色（可多选）
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
                        :loading="savingRoles === asMember(row).userId"
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
                    <UserAvatar :name="asMember(row).displayName" :src="asMember(row).avatarUrl" :size="28" />
                    <div class="min-w-0">
                      <div class="truncate text-[13px] font-medium text-ink">{{ asMember(row).displayName }}</div>
                      <div class="truncate text-[11px] text-ink-400">@{{ asMember(row).username }}</div>
                    </div>
                  </div>
                </template>
              </el-table-column>

              <el-table-column label="角色" min-width="150">
                <template #default="{ row }">
                  <div class="flex flex-wrap gap-1">
                    <span
                      v-for="r in asMember(row).roles"
                      :key="r"
                      class="inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium"
                      :class="roleClass(r)"
                    >
                      <Crown v-if="r === 'OWNER'" :size="10" class="mr-0.5" />{{ roleLabel(r) }}
                    </span>
                  </div>
                </template>
              </el-table-column>

              <el-table-column label="加入时间" width="112">
                <template #default="{ row }">
                  <span class="project-date">{{ asMember(row).createdAt?.slice(0, 10) || '—' }}</span>
                </template>
              </el-table-column>

              <el-table-column v-if="canManage" label="操作" width="140" align="right">
                <template #default="{ row }">
                  <div class="flex items-center justify-end gap-1">
                    <!-- 文字按钮比图标更明确：皇冠图标容易被误读成"标记为所有者" -->
                    <button class="project-enter" @click.stop="toggleRoles(asMember(row))">编辑角色</button>
                    <button
                      class="project-icon-btn hover:!text-danger"
                      title="移除成员"
                      @click.stop="onRemoveMember(asMember(row))"
                    >
                      <Trash2 :size="14" />
                    </button>
                  </div>
                </template>
              </el-table-column>

              <template #empty>
                <div class="py-10 text-center text-sm text-ink-400">暂无成员</div>
              </template>
            </el-table>
          </section>
        </div>

        <!-- 右栏：只读概览与说明，避免宽屏下左栏之外出现大片空白 -->
        <aside class="space-y-4 xl:col-span-1">
          <!-- 角色分布 -->
          <section class="glass-panel rounded-lg">
            <div class="border-b border-line-light px-4 py-3">
              <div class="flex items-center gap-2 text-sm font-medium text-ink-900">
                <Crown :size="16" class="text-brand" /> 角色分布
              </div>
            </div>
            <div class="p-4">
              <div v-if="!roleDistribution.length" class="py-4 text-center text-sm text-ink-400">暂无成员</div>
              <ul v-else class="space-y-2.5">
                <li
                  v-for="r in roleDistribution"
                  :key="r.value"
                  class="flex items-center justify-between gap-2 text-sm"
                >
                  <span class="inline-flex items-center rounded-full px-2 py-0.5 text-[11px] font-medium" :class="roleClass(r.value)">
                    {{ r.label }}
                  </span>
                  <span class="text-sm font-medium tabular-nums text-ink">{{ r.count }} 人</span>
                </li>
              </ul>
            </div>
          </section>

          <!-- 说明 -->
          <section class="glass-panel rounded-lg">
            <div class="border-b border-line-light px-4 py-3">
              <div class="flex items-center gap-2 text-sm font-medium text-ink-900">
                <Info :size="16" class="text-brand" /> 说明
              </div>
            </div>
            <div class="space-y-2.5 p-4 text-xs leading-relaxed text-ink-400">
              <p>· 项目 Key 创建后不可修改，工作项编号以此为前提。</p>
              <p>· 只有所有者、管理员、项目经理可以修改设置与成员。</p>
              <p>· 归档后项目从常规列表隐藏，可在「已归档」中找到并恢复。</p>
              <p>· 移除成员不影响其已创建的工作项，负责人字段会保留。</p>
            </div>
          </section>
        </aside>
      </div>
    </div>

    <AddMemberDialog v-model:visible="addDialogVisible" :project-id="projectId" @added="reloadMembers" />
  </div>
</template>
