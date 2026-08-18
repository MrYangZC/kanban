<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listWorkspaces, createWorkspace, listProjects, createProject, updateProject, listProjectMembers } from '@/api'
import type { Workspace, Project, ProjectMember } from '@/api/types'
import { useUserStore } from '@/stores/user'
import ProjectMemberDialog from '@/components/ProjectMemberDialog.vue'
import { LayoutGrid, Plus, FolderKanban, Building2, LogOut, Users, UserCog, Star, Pencil, FileText, Paperclip } from 'lucide-vue-next'
import { ElMessageBox, ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const workspaces = ref<Workspace[]>([])
const activeWs = ref<string>('')
const projects = ref<Project[]>([])
const loading = ref(false)
const myRoles = ref<Record<string, string>>({}) // projectId -> 当前用户角色

const memberDialogVisible = ref(false)
const memberProjectId = ref<string>('')

const editDialogVisible = ref(false)
const editing = ref(false)
const editForm = ref({ id: '', name: '', description: '' })

async function loadWorkspaces() {
  workspaces.value = await listWorkspaces()
  if (workspaces.value.length) {
    activeWs.value = workspaces.value[0].id
    await loadProjects()
  }
}

async function loadProjects() {
  if (!activeWs.value) return
  loading.value = true
  try {
    const list = await listProjects(activeWs.value)
    projects.value = list
    // 批量拉取当前用户在各项目的角色，用于权限判断
    const roles: Record<string, string> = {}
    await Promise.all(list.map(async (p) => {
      try {
        const members: ProjectMember[] = await listProjectMembers(p.id)
        const me = members.find(m => m.userId === userStore.user?.id)
        roles[p.id] = me?.role ?? 'VIEWER'
      } catch {
        roles[p.id] = 'VIEWER'
      }
    }))
    myRoles.value = roles
  } finally {
    loading.value = false
  }
}

function canEdit(p: Project): boolean {
  const r = myRoles.value[p.id]
  return r === 'OWNER' || r === 'MEMBER'
}

function selectWs(id: string) {
  activeWs.value = id
  loadProjects()
}

async function onNewWorkspace() {
  try {
    const { value } = await ElMessageBox.prompt('Workspace 名称', '新建工作区', {
      inputPlaceholder: '例如：七米科技'
    })
    const key = value.replace(/\s/g, '').toUpperCase().slice(0, 12)
    await createWorkspace(value, key)
    await loadWorkspaces()
    ElMessage.success('工作区已创建')
  } catch {
    /* 取消 */
  }
}

async function onNewProject() {
  if (!activeWs.value) return
  try {
    const { value: name } = await ElMessageBox.prompt('项目命名', '新建项目', {
      inputPlaceholder: '例如：支付中台'
    })
    const key = name.replace(/\s/g, '').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 8) || 'PROJ'
    await createProject(activeWs.value, key, name)
    await loadProjects()
    ElMessage.success('项目已创建，默认看板已就绪')
  } catch {
    /* 取消 */
  }
}

function openBoard(p: Project) {
  router.push(`/projects/${p.id}/board`)
}

function openMembers(p: Project) {
  memberProjectId.value = p.id
  memberDialogVisible.value = true
}

function openEdit(p: Project) {
  if (!canEdit(p)) {
    ElMessage.warning('只有项目成员（OWNER/MEMBER）才能编辑')
    return
  }
  editForm.value = { id: p.id, name: p.name, description: p.description }
  editDialogVisible.value = true
}

async function submitEdit() {
  if (!editForm.value.name.trim()) {
    ElMessage.warning('项目名称不能为空')
    return
  }
  editing.value = true
  try {
    await updateProject(editForm.value.id, {
      name: editForm.value.name.trim(),
      description: editForm.value.description
    })
    editDialogVisible.value = false
    ElMessage.success('项目已更新')
    await loadProjects()
  } catch (e: any) {
    ElMessage.error(e?.message || '更新失败')
  } finally {
    editing.value = false
  }
}

function logout() {
  userStore.logout()
  router.push('/login')
}

onMounted(loadWorkspaces)
</script>

<template>
  <div class="flex h-full">
    <!-- 侧边栏 -->
    <aside class="flex w-60 flex-col bg-ink-900 text-white/90">
      <div class="flex items-center gap-2 px-5 py-5">
        <LayoutGrid :size="20" class="text-brand-light" />
        <span class="font-semibold">Kanban</span>
      </div>
      <div class="px-3">
        <div class="mb-2 px-2 text-xs uppercase text-white/40">管理</div>
        <button
          class="mb-1 flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm transition hover:bg-white/5"
          @click="router.push('/projects')"
        >
          <FolderKanban :size="15" />
          <span>项目</span>
        </button>
        <button
          class="mb-1 flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm transition hover:bg-white/5"
          @click="router.push('/users')"
        >
          <UserCog :size="15" />
          <span>用户管理</span>
        </button>
      </div>
      <div class="px-3">
        <div class="mb-2 flex items-center justify-between px-2 text-xs uppercase text-white/40">
          <span>工作区</span>
          <button class="hover:text-white" title="新建工作区" @click="onNewWorkspace"><Plus :size="14" /></button>
        </div>
        <button
          v-for="ws in workspaces"
          :key="ws.id"
          class="mb-1 flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm transition"
          :class="activeWs === ws.id ? 'bg-white/10' : 'hover:bg-white/5'"
          @click="selectWs(ws.id)"
        >
          <Building2 :size="15" />
          <span class="truncate">{{ ws.name }}</span>
        </button>
      </div>
      <div class="mt-auto px-3 py-4">
        <div class="flex items-center gap-2 rounded-lg px-2 py-2">
          <div class="flex h-8 w-8 items-center justify-center rounded-full bg-brand text-sm font-medium">
            {{ userStore.user?.displayName?.charAt(0) || 'U' }}
          </div>
          <div class="flex-1 truncate text-sm">{{ userStore.user?.displayName }}</div>
          <button class="hover:text-white/60" title="退出" @click="logout"><LogOut :size="15" /></button>
        </div>
      </div>
    </aside>

    <!-- 主区 -->
    <main class="flex-1 overflow-auto p-8">
      <div class="mb-6 flex items-center justify-between">
        <div>
          <h1 class="text-xl font-semibold text-ink-900">项目</h1>
          <p class="text-sm text-ink-400">创建工作区下的研发项目，创建即自动初始化看板</p>
        </div>
        <button
          class="flex items-center gap-1.5 rounded-lg bg-brand px-3.5 py-2 text-sm font-medium text-white transition hover:bg-brand-dark"
          @click="onNewProject"
        >
          <Plus :size="16" /> 新建项目
        </button>
      </div>

      <div v-if="loading" class="text-sm text-ink-400">加载中…</div>
      <div v-else-if="!projects.length" class="rounded-xl border border-dashed border-surface-muted p-12 text-center text-sm text-ink-400">
        暂无项目，点击右上角「新建项目」开始
      </div>
      <div v-else class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
        <div
          v-for="p in projects"
          :key="p.id"
          class="group relative flex flex-col rounded-xl bg-white p-5 shadow-sm ring-1 ring-black/5 transition hover:-translate-y-0.5 hover:shadow-md"
        >
          <!-- 悬停操作栏 -->
          <div class="absolute right-3 top-3 flex items-center gap-1 opacity-0 transition group-hover:opacity-100">
            <button
              class="rounded-md p-1.5 text-ink-400 ring-1 ring-surface-muted transition hover:bg-surface-muted hover:text-brand"
              title="收藏"
              @click.stop="ElMessage.info('收藏功能开发中')"
            >
              <Star :size="14" />
            </button>
            <button
              class="rounded-md p-1.5 text-ink-400 ring-1 ring-surface-muted transition hover:bg-surface-muted hover:text-brand"
              :class="{ 'cursor-not-allowed opacity-50': !canEdit(p) }"
              title="编辑项目"
              @click.stop="openEdit(p)"
            >
              <Pencil :size="14" />
            </button>
          </div>

          <div class="mb-3 flex h-10 w-10 items-center justify-center rounded-lg bg-surface-muted text-ink-600">
            <FolderKanban :size="20" />
          </div>
          <div class="text-xs font-medium text-brand">{{ p.projectKey }}</div>
          <div class="mt-0.5 cursor-pointer text-base font-semibold text-ink-900 group-hover:text-brand" @click="openBoard(p)">{{ p.name }}</div>
          <div class="mt-2 line-clamp-2 text-sm text-ink-400">{{ p.description || '暂无描述' }}</div>

          <!-- 底部统计行 -->
          <div class="mt-4 flex items-center gap-4 border-t border-surface-muted pt-3 text-xs text-ink-400">
            <span class="flex items-center gap-1" :title="`${p.stats?.memberCount || 0} 名成员`">
              <Users :size="13" />{{ p.stats?.memberCount || 0 }}
            </span>
            <span class="flex items-center gap-1" :title="`${p.stats?.issueCount || 0} 个任务`">
              <FileText :size="13" />{{ p.stats?.issueCount || 0 }}
            </span>
            <span class="flex items-center gap-1" :title="`${p.stats?.attachmentCount || 0} 个附件`">
              <Paperclip :size="13" />{{ p.stats?.attachmentCount || 0 }}
            </span>
            <button
              class="ml-auto rounded-md px-2 py-1 text-ink-400 ring-1 ring-surface-muted transition hover:text-brand hover:ring-brand"
              @click.stop="openMembers(p)"
            >
              管理成员
            </button>
          </div>
        </div>
      </div>
    </main>

    <!-- 编辑项目弹窗 -->
    <el-dialog v-model="editDialogVisible" title="编辑项目" width="420px">
      <div class="space-y-4">
        <div>
          <label class="mb-1 block text-sm text-ink-400">项目名称</label>
          <input
            v-model="editForm.name"
            class="w-full rounded-lg border border-surface-muted px-3 py-2 text-sm outline-none focus:border-brand"
            placeholder="项目名称"
          />
        </div>
        <div>
          <label class="mb-1 block text-sm text-ink-400">项目描述</label>
          <textarea
            v-model="editForm.description"
            rows="3"
            class="w-full resize-none rounded-lg border border-surface-muted px-3 py-2 text-sm outline-none focus:border-brand"
            placeholder="项目描述（可选）"
          ></textarea>
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end gap-2">
          <button class="rounded-lg px-3.5 py-2 text-sm text-ink-400 transition hover:bg-surface-muted" @click="editDialogVisible = false">取消</button>
          <button
            class="rounded-lg bg-brand px-3.5 py-2 text-sm font-medium text-white transition hover:bg-brand-dark disabled:opacity-50"
            :disabled="editing"
            @click="submitEdit"
          >
            {{ editing ? '保存中…' : '保存' }}
          </button>
        </div>
      </template>
    </el-dialog>

    <ProjectMemberDialog v-model:visible="memberDialogVisible" :project-id="memberProjectId" />
  </div>
</template>
