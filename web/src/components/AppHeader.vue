<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { useUserStore } from '@/stores/user'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  LayoutGrid, ChevronDown, Search, Plus, Bell,
  FolderKanban, UserCog, LogOut, User, X, Shield
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const userStore = useUserStore()

const isInProject = computed(() => !!route.params.projectId)
const projectId = computed(() => route.params.projectId as string)

const showProjectDropdown = ref(false)
const showUserDropdown = ref(false)
const showSearchDialog = ref(false)
const searchKeyword = ref('')
const notificationCount = ref(3)

const navItems = [
  { path: '/projects', label: '项目', icon: FolderKanban },
  { path: '/users', label: '用户管理', icon: UserCog },
  { path: '/rbac', label: '权限管理', icon: Shield }
]

function isActive(path: string): boolean {
  if (path === '/projects') return route.path === '/projects' || route.path.startsWith('/projects/')
  return route.path === path
}

function toggleProjectDropdown() {
  showProjectDropdown.value = !showProjectDropdown.value
  showUserDropdown.value = false
}

function toggleUserDropdown() {
  showUserDropdown.value = !showUserDropdown.value
  showProjectDropdown.value = false
}

function switchProject(id: string) {
  showProjectDropdown.value = false
  if (id === projectId.value) return
  router.push(`/projects/${id}/overview`)
}

function openSearch() {
  showSearchDialog.value = true
  searchKeyword.value = ''
  setTimeout(() => {
    const input = document.querySelector('#global-search-input') as HTMLInputElement | null
    input?.focus()
  }, 50)
}

function closeSearch() {
  showSearchDialog.value = false
}

function executeSearch() {
  const kw = searchKeyword.value.trim()
  if (!kw) return
  ElMessage.info(`搜索"${kw}"功能开发中`)
  closeSearch()
}

function onCreate() {
  ElMessageBox.prompt('任务标题', '快速新建', {
    inputPlaceholder: '输入任务标题'
  }).then(({ value }) => {
    if (value) ElMessage.success(`已创建任务：${value}`)
  }).catch(() => {})
}

function logout() {
  userStore.logout()
  router.push('/login')
}

function onKeydown(e: KeyboardEvent) {
  if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
    e.preventDefault()
    openSearch()
  }
  if (e.key === 'Escape' && showSearchDialog.value) {
    closeSearch()
  }
}

function onDocumentClick() {
  showProjectDropdown.value = false
  showUserDropdown.value = false
}

watch(projectId, async () => {
  if (projectId.value) {
    try { await projectStore.loadCurrent(projectId.value) } catch { /* ignore */ }
  }
}, { immediate: false })

onMounted(async () => {
  if (!projectStore.loaded) {
    try { await projectStore.loadList() } catch { /* ignore */ }
  }
  if (projectId.value) {
    try { await projectStore.loadCurrent(projectId.value) } catch { /* ignore */ }
  }
  window.addEventListener('keydown', onKeydown)
  document.addEventListener('click', onDocumentClick)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  document.removeEventListener('click', onDocumentClick)
})
</script>

<template>
  <header class="flex h-14 shrink-0 items-center gap-4 border-b border-line-light bg-white px-4">
    <div class="flex items-center gap-2">
      <div class="sidebar-logo-icon"><LayoutGrid :size="18" /></div>
      <span class="font-semibold text-ink">Kanban</span>
    </div>

    <nav class="flex items-center gap-1">
      <button
        v-for="item in navItems"
        :key="item.path"
        class="flex items-center gap-1.5 rounded-md px-3 py-1.5 text-sm transition"
        :class="isActive(item.path) ? 'bg-primary-light text-primary' : 'text-ink-regular hover:bg-surface-hover'"
        @click="router.push(item.path)"
      >
        <component :is="item.icon" :size="15" />
        <span>{{ item.label }}</span>
      </button>
    </nav>

    <div v-if="isInProject" class="relative" @click.stop>
      <button class="flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-sm font-medium text-ink transition hover:bg-surface-hover" @click="toggleProjectDropdown">
        <FolderKanban :size="15" class="text-ink-secondary" />
        <span class="max-w-[160px] truncate">{{ projectStore.current?.name || '选择项目' }}</span>
        <ChevronDown :size="14" class="text-ink-secondary" />
      </button>
      <div v-if="showProjectDropdown" class="absolute left-0 top-full z-50 mt-1 w-64 rounded-lg border border-line-light bg-white py-1 shadow-modal">
        <div class="px-3 py-1.5 text-xs text-ink-secondary">切换项目</div>
        <div class="max-h-72 overflow-y-auto">
          <button
            v-for="p in projectStore.list"
            :key="p.id"
            class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm transition hover:bg-surface-hover"
            :class="p.id === projectId ? 'text-primary' : 'text-ink-regular'"
            @click="switchProject(p.id)"
          >
            <FolderKanban :size="14" class="shrink-0" />
            <span class="flex-1 truncate">{{ p.name }}</span>
            <span class="text-xs text-ink-secondary">{{ p.projectKey }}</span>
          </button>
          <div v-if="!projectStore.list.length" class="px-3 py-3 text-center text-sm text-ink-secondary">暂无项目</div>
        </div>
      </div>
    </div>

    <button
      v-if="isInProject"
      class="ml-2 flex items-center gap-2 rounded-md border border-line-light bg-surface-hover px-3 py-1.5 text-sm text-ink-secondary transition hover:border-primary hover:text-primary"
      @click="openSearch"
    >
      <Search :size="14" />
      <span class="hidden md:inline">搜索</span>
      <kbd class="hidden rounded border border-line bg-white px-1.5 text-xs text-ink-secondary md:inline">Ctrl K</kbd>
    </button>

    <div class="ml-auto flex items-center gap-1">
      <button
        v-if="isInProject"
        class="flex h-8 w-8 items-center justify-center rounded-md text-ink-regular transition hover:bg-surface-hover hover:text-primary"
        title="新建"
        @click="onCreate"
      >
        <Plus :size="18" />
      </button>
      <button
        v-if="isInProject"
        class="relative flex h-8 w-8 items-center justify-center rounded-md text-ink-regular transition hover:bg-surface-hover hover:text-primary"
        title="通知"
      >
        <Bell :size="18" />
        <span v-if="notificationCount > 0" class="absolute right-1 top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-danger px-1 text-[10px] font-medium text-white">
          {{ notificationCount }}
        </span>
      </button>
      <div class="relative" @click.stop>
        <button class="flex items-center gap-1.5 rounded-md p-1 transition hover:bg-surface-hover" @click="toggleUserDropdown">
          <div class="avatar-sm avatar-primary">{{ userStore.user?.displayName?.charAt(0) || 'U' }}</div>
          <ChevronDown :size="12" class="text-ink-secondary" />
        </button>
        <div v-if="showUserDropdown" class="absolute right-0 top-full z-50 mt-1 w-48 rounded-lg border border-line-light bg-white py-1 shadow-modal">
          <div class="border-b border-line-light px-3 py-2">
            <div class="text-sm font-medium text-ink">{{ userStore.user?.displayName }}</div>
            <div class="text-xs text-ink-secondary">{{ userStore.user?.username }}</div>
          </div>
          <button class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-ink-regular transition hover:bg-surface-hover" @click="showUserDropdown = false; router.push('/profile')">
            <User :size="14" /> 个人信息
          </button>
          <button class="flex w-full items-center gap-2 px-3 py-2 text-left text-sm text-ink-regular transition hover:bg-surface-hover" @click="logout">
            <LogOut :size="14" /> 退出登录
          </button>
        </div>
      </div>
    </div>

    <div v-if="showSearchDialog" class="fixed inset-0 z-50 flex items-start justify-center bg-black/40 pt-[15vh]" @click="closeSearch">
      <div class="w-full max-w-xl rounded-lg bg-white shadow-modal" @click.stop>
        <div class="flex items-center gap-3 border-b border-line-light px-4 py-3">
          <Search :size="18" class="text-ink-secondary" />
          <input
            id="global-search-input"
            v-model="searchKeyword"
            class="flex-1 bg-transparent text-sm text-ink outline-none placeholder:text-ink-secondary"
            placeholder="搜索任务、缺陷、需求…"
            @keydown.enter="executeSearch"
            @keydown.escape="closeSearch"
          />
          <button class="text-ink-secondary transition hover:text-ink" @click="closeSearch"><X :size="18" /></button>
        </div>
        <div class="max-h-80 overflow-y-auto p-2">
          <div class="px-3 py-6 text-center text-sm text-ink-secondary">输入关键词后按回车搜索</div>
        </div>
      </div>
    </div>
  </header>
</template>