<script setup lang="ts">
import { onMounted, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { createProject, updateProject, deleteProject } from '@/api'
import type { Project } from '@/api/types'
import { useProjectCategoryStore } from '@/stores/projectCategory'
import ProjectCategorySidebar from '@/components/project/ProjectCategorySidebar.vue'
import ViewModeSwitch from '@/components/project/ViewModeSwitch.vue'
import ProjectSearchBar from '@/components/project/ProjectSearchBar.vue'
import ProjectCard from '@/components/project/ProjectCard.vue'
import ProjectTableList from '@/components/project/ProjectTableList.vue'
import EmptyState from '@/components/project/EmptyState.vue'
import ErrorRetry from '@/components/project/ErrorRetry.vue'
import Pagination from '@/components/project/Pagination.vue'
import ProjectMemberDialog from '@/components/ProjectMemberDialog.vue'
import { Plus, Menu, FolderKanban, Sparkles } from 'lucide-vue-next'
import { ElMessageBox, ElMessage } from 'element-plus'
import { errorMessage } from '@/utils/error'

const router = useRouter()
const store = useProjectCategoryStore()
const { projects, total, totalPages, currentPage, pageSize, loading, error, viewMode, selectedCategory, categories } = storeToRefs(store)

const sidebarOpen = ref(false)

const memberDialogVisible = ref(false)
const memberProjectId = ref<string>('')

const editDialogVisible = ref(false)
const editing = ref(false)
const editForm = ref({ id: '', name: '', description: '' })

const currentCategoryLabel = computed(() => {
  const cat = categories.value.find((c) => c.key === selectedCategory.value)
  return cat?.label || '全部项目'
})

const currentCategoryCount = computed(() => {
  const cat = categories.value.find((c) => c.key === selectedCategory.value)
  return cat?.count || 0
})

const totalMembers = computed(() =>
  projects.value.reduce((sum, p) => sum + (p.stats?.memberCount || 0), 0)
)

const totalIssues = computed(() =>
  projects.value.reduce((sum, p) => sum + (p.stats?.issueCount || 0), 0)
)

async function onNewProject() {
  try {
    const { value: name } = await ElMessageBox.prompt('项目命名', '新建项目', {
      inputPlaceholder: '例如：支付中台'
    })
    const key = name.replace(/\s/g, '').toUpperCase().replace(/[^A-Z0-9]/g, '').slice(0, 8) || 'PROJ'
    await createProject(key, name)
    await store.loadCategoryCounts()
    await store.loadProjects()
    ElMessage.success('项目已创建，默认看板已就绪')
  } catch {
    /* 取消 */
  }
}

function openBoard(p: Project) {
  const last = localStorage.getItem(`kanban:lastView:${p.id}`)
  const path = last === 'table' ? 'table' : last === 'defects' ? 'defects' : last === 'overview' ? 'overview' : 'overview'
  router.push(`/projects/${p.id}/${path}`)
}

function openMembers(p: Project) {
  memberProjectId.value = p.id
  memberDialogVisible.value = true
}

function openEdit(p: Project) {
  if (!p.canEdit) {
    ElMessage.warning('只有项目管理员才能编辑')
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
    await store.loadProjects()
  } catch (e: unknown) {
    ElMessage.error(errorMessage(e, '更新失败'))
  } finally {
    editing.value = false
  }
}

async function onDelete(p: Project) {
  try {
    await ElMessageBox.confirm(
      `确定要删除项目「${p.name}」吗？此操作将删除项目下所有任务、看板等数据，且不可恢复。`,
      '删除项目',
      {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch {
    return
  }
  try {
    await deleteProject(p.id)
    ElMessage.success('项目已删除')
    await store.loadCategoryCounts()
    await store.loadProjects()
  } catch (e: unknown) {
    ElMessage.error(errorMessage(e, '删除失败'))
  }
}

async function onRetry() {
  await store.loadCategoryCounts()
  await store.loadProjects()
}

onMounted(async () => {
  await store.loadCategoryCounts()
  await store.selectCategory(store.selectedCategory)
})
</script>

<template>
  <div class="flex h-full overflow-hidden">
    <div
      v-if="sidebarOpen"
      class="fixed inset-0 z-30 bg-black/40 backdrop-blur-sm transition-opacity md:hidden"
      @click="sidebarOpen = false"
    />

    <aside
      class="flex shrink-0 flex-col border-r border-line-light bg-gradient-sidebar w-64 transition-all duration-300 ease-out-expo"
      :class="sidebarOpen
        ? 'fixed inset-y-0 left-0 z-40 shadow-xl md:static md:translate-x-0 md:shadow-none'
        : 'fixed inset-y-0 left-0 z-40 -translate-x-full md:static md:translate-x-0 md:shadow-none'"
    >


      <ProjectCategorySidebar />
      <div class="border-t border-line-light px-5 py-4">
        <div class="flex items-center gap-2 rounded-lg bg-primary-50/60 px-3 py-2.5">
          <Sparkles :size="16" class="text-primary-500" />
          <div class="flex-1">
            <div class="text-xs font-medium text-primary-700">快速入门</div>
            <div class="text-[11px] text-ink-secondary">查看使用指南</div>
          </div>
        </div>
      </div>
    </aside>

    <main class="flex flex-1 flex-col overflow-hidden bg-gradient-page">
      <div class="flex-1 overflow-auto px-8 py-7">
        <div class="mb-7 flex items-start justify-between gap-4">
          <div class="flex items-start gap-3">
            <button
              class="rounded-lg p-2 text-ink-secondary transition hover:bg-surface-hover hover:text-ink md:hidden"
              title="分类导航"
              @click="sidebarOpen = true"
            >
              <Menu :size="20" />
            </button>
            <div>
              <div class="flex items-center gap-2.5">
                <h1 class="page-title">{{ currentCategoryLabel }}</h1>
                <span class="inline-flex items-center justify-center rounded-full bg-primary-50 px-2.5 py-0.5 text-xs font-medium text-primary-600">
                  {{ currentCategoryCount }}
                </span>
              </div>
              <p class="page-subtitle">创建研发项目，创建即自动初始化看板</p>
            </div>
          </div>
          <div class="flex items-center gap-2.5">
            <ProjectSearchBar />
            <ViewModeSwitch />
            <button class="btn-primary" @click="onNewProject">
              <Plus :size="16" />
              <span>新建项目</span>
            </button>
          </div>
        </div>

        <div
          v-if="!loading && !error && projects.length"
          class="mb-6 grid grid-cols-2 gap-4 sm:grid-cols-4"
        >
          <div class="card flex items-center gap-3 p-4">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-primary-50 text-primary-500">
              <FolderKanban :size="20" />
            </div>
            <div>
              <div class="text-xl font-semibold text-ink">{{ total }}</div>
              <div class="text-xs text-ink-secondary">总项目数</div>
            </div>
          </div>
          <div class="card flex items-center gap-3 p-4">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-success-light text-success">
              <Sparkles :size="20" />
            </div>
            <div>
              <div class="text-xl font-semibold text-ink">{{ totalIssues }}</div>
              <div class="text-xs text-ink-secondary">本页任务</div>
            </div>
          </div>
          <div class="card flex items-center gap-3 p-4">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-warning-light text-warning">
              <FolderKanban :size="20" />
            </div>
            <div>
              <div class="text-xl font-semibold text-ink">{{ totalMembers }}</div>
              <div class="text-xs text-ink-secondary">本页成员</div>
            </div>
          </div>
          <div class="card flex items-center gap-3 p-4">
            <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-primary-100 text-primary-600">
              <Plus :size="20" />
            </div>
            <div>
              <div class="text-xl font-semibold text-ink">{{ categories[0]?.count || 0 }}</div>
              <div class="text-xs text-ink-secondary">全部项目</div>
            </div>
          </div>
        </div>

        <div v-if="loading" class="flex items-center gap-3 py-20 text-sm text-ink-secondary">
          <div class="h-5 w-5 animate-spin rounded-full border-2 border-primary-200 border-t-primary-500"></div>
          <span>加载中…</span>
        </div>
        <ErrorRetry v-else-if="error" :message="error" @retry="onRetry" />
        <EmptyState v-else-if="!projects.length" :category="selectedCategory" />
        <div
          v-else-if="viewMode === 'card'"
          class="grid animate-fade-in-up grid-cols-1 items-start gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-3"
        >
          <ProjectCard
            v-for="p in projects"
            :key="p.id"
            :project="p"
            @open-board="openBoard"
            @open-members="openMembers"
            @open-edit="openEdit"
            @delete="onDelete"
          />
        </div>
        <ProjectTableList
          v-else
          :projects="projects"
          @open-board="openBoard"
          @open-members="openMembers"
          @open-edit="openEdit"
        />


        <el-dialog v-model="editDialogVisible" title="编辑项目" width="460px">
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
              <button class="btn-default" @click="editDialogVisible = false">取消</button>
              <button class="btn-primary disabled:opacity-50" :disabled="editing" @click="submitEdit">{{ editing ? '保存中…' : '保存' }}</button>
            </div>
          </template>
        </el-dialog>

        <ProjectMemberDialog v-model:visible="memberDialogVisible" :project-id="memberProjectId" />
      </div>

      <div
        v-if="!loading && !error && projects.length"
        class="glass-bar flex shrink-0 items-center justify-center border-t px-8 py-2"
      >
        <Pagination
          :current-page="currentPage"
          :total-pages="totalPages"
          :total="total"
          :page-size="pageSize"
          @update:page="store.setPage"
          @update:page-size="store.setPageSize"
        />
      </div>
    </main>
  </div>
</template>
