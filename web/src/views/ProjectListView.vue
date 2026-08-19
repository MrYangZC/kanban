<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { listProjects, createProject, updateProject, listProjectMembers } from '@/api'
import type { Project, ProjectMember } from '@/api/types'
import { useUserStore } from '@/stores/user'
import ProjectMemberDialog from '@/components/ProjectMemberDialog.vue'
import { Plus, FolderKanban, Users, Star, Pencil, FileText, Paperclip } from 'lucide-vue-next'
import { ElMessageBox, ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const projects = ref<Project[]>([])
const loading = ref(false)
const myRoles = ref<Record<string, string[]>>({})

const MEMBER_ROLES = ['OWNER', 'ADMIN', 'PM', 'DEV', 'QA', 'MEMBER']

const memberDialogVisible = ref(false)
const memberProjectId = ref<string>('')

const editDialogVisible = ref(false)
const editing = ref(false)
const editForm = ref({ id: '', name: '', description: '' })

async function loadProjects() {
  loading.value = true
  try {
    const list = await listProjects()
    projects.value = list
    const roles: Record<string, string[]> = {}
    await Promise.all(list.map(async (p) => {
      try {
        const members: ProjectMember[] = await listProjectMembers(p.id)
        const me = members.find(m => m.userId === userStore.user?.id)
        roles[p.id] = me?.roles ?? ['VIEWER']
      } catch {
        roles[p.id] = ['VIEWER']
      }
    }))
    myRoles.value = roles
  } finally {
    loading.value = false
  }
}

function canEdit(p: Project): boolean {
  const rs = myRoles.value[p.id] ?? ['VIEWER']
  return MEMBER_ROLES.some((r) => rs.includes(r))
}

async function onNewProject() {
  try {
    const { value: name } = await ElMessageBox.prompt('项目命名', '新建项目', {
      inputPlaceholder: '例如：支付中台'
    })
    const key = name.replace(/\s/g, '').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 8) || 'PROJ'
    await createProject(key, name)
    await loadProjects()
    ElMessage.success('项目已创建，默认看板已就绪')
  } catch {
    /* 取消 */
  }
}

function openBoard(p: Project) {
  const last = localStorage.getItem(`kanban:lastView:${p.id}`)
  const path = last === 'table' ? 'table' : last === 'defects' ? 'defects' : last === 'overview' ? 'overview' : 'board'
  router.push(`/projects/${p.id}/${path}`)
}

function openMembers(p: Project) {
  memberProjectId.value = p.id
  memberDialogVisible.value = true
}

function openEdit(p: Project) {
  if (!canEdit(p)) {
    ElMessage.warning('只有项目成员才能编辑')
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

onMounted(loadProjects)
</script>

<template>
  <div class="page-container">
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="page-title">项目</h1>
        <p class="page-subtitle">创建研发项目，创建即自动初始化看板</p>
      </div>
      <button class="btn-primary" @click="onNewProject"><Plus :size="16" /> 新建项目</button>
    </div>

    <div v-if="loading" class="text-sm text-ink-secondary">加载中…</div>
    <div v-else-if="!projects.length" class="rounded-lg border border-dashed border-line p-12 text-center text-sm text-ink-secondary">
      暂无项目，点击右上角「新建项目」开始
    </div>
    <div v-else class="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <div v-for="p in projects" :key="p.id" class="group relative flex flex-col card p-5 transition hover:shadow-card-hover">
        <div class="absolute right-3 top-3 flex items-center gap-1 opacity-0 transition group-hover:opacity-100">
          <button class="rounded-md p-1.5 text-ink-secondary transition hover:bg-surface-hover" title="收藏" @click.stop="ElMessage.info('收藏功能开发中')">
            <Star :size="14" />
          </button>
          <button class="rounded-md p-1.5 text-ink-secondary transition hover:bg-surface-hover" :class="{ 'cursor-not-allowed opacity-50': !canEdit(p) }" title="编辑项目" @click.stop="openEdit(p)">
            <Pencil :size="14" />
          </button>
        </div>

        <div class="mb-3 flex h-10 w-10 items-center justify-center rounded-lg bg-surface-light text-ink-regular">
          <FolderKanban :size="20" />
        </div>
        <div class="text-xs font-medium text-primary">{{ p.projectKey }}</div>
        <div class="mt-0.5 cursor-pointer text-base font-semibold text-ink transition hover:text-primary" @click="openBoard(p)">{{ p.name }}</div>
        <div class="mt-2 line-clamp-2 text-sm text-ink-secondary">{{ p.description || '暂无描述' }}</div>

        <div class="mt-4 flex items-center gap-4 border-t border-line-light pt-3 text-xs text-ink-secondary">
          <span class="flex items-center gap-1" :title="`${p.stats?.memberCount || 0} 名成员`"><Users :size="13" />{{ p.stats?.memberCount || 0 }}</span>
          <span class="flex items-center gap-1" :title="`${p.stats?.issueCount || 0} 个任务`"><FileText :size="13" />{{ p.stats?.issueCount || 0 }}</span>
          <span class="flex items-center gap-1" :title="`${p.stats?.attachmentCount || 0} 个附件`"><Paperclip :size="13" />{{ p.stats?.attachmentCount || 0 }}</span>
          <button class="ml-auto rounded-md border border-line-light px-2 py-1 text-ink-secondary transition hover:border-primary hover:text-primary" @click.stop="openMembers(p)">管理成员</button>
        </div>
      </div>
    </div>

    <el-dialog v-model="editDialogVisible" title="编辑项目" width="420px">
      <div class="space-y-4">
        <div>
          <label class="label-base">项目名称</label>
          <input v-model="editForm.name" class="input-base" placeholder="项目名称" />
        </div>
        <div>
          <label class="label-base">项目描述</label>
          <textarea v-model="editForm.description" rows="3" class="input-base resize-none" placeholder="项目描述（可选）"></textarea>
        </div>
      </div>
      <template #footer>
        <div class="flex justify-end gap-2">
          <button class="rounded-lg px-3.5 py-2 text-sm text-ink-secondary transition hover:bg-surface-hover" @click="editDialogVisible = false">取消</button>
          <button class="btn-primary disabled:opacity-50" :disabled="editing" @click="submitEdit">{{ editing ? '保存中…' : '保存' }}</button>
        </div>
      </template>
    </el-dialog>

    <ProjectMemberDialog v-model:visible="memberDialogVisible" :project-id="memberProjectId" />
  </div>
</template>
