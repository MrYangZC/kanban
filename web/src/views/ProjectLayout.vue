<script setup lang="ts">
import { ref, computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { ElMessage } from 'element-plus'
import {
  LayoutDashboard, FileText, ListTodo, Bug,
  Repeat, Target, ShieldAlert, BarChart3, Settings,
  Search, X, ArrowRight
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()

const projectId = computed(() => route.params.projectId as string)

const showSearchDialog = ref(false)
const searchKeyword = ref('')
const sidebarCollapsed = ref(false)

interface NavItem {
  name: string
  label: string
  icon: typeof LayoutDashboard
}

const NAV_ITEMS: NavItem[] = [
  { name: 'overview', label: '概览', icon: LayoutDashboard },
  { name: 'requirements', label: '需求', icon: FileText },
  { name: 'tasks', label: '任务', icon: ListTodo },
  { name: 'defects', label: '缺陷', icon: Bug },
  { name: 'iterations', label: '迭代', icon: Repeat },
  { name: 'milestones', label: '里程碑', icon: Target },
  { name: 'risks', label: '风险', icon: ShieldAlert },
  { name: 'metrics', label: '度量', icon: BarChart3 },
  { name: 'settings', label: '设置', icon: Settings }
]

const currentNav = computed(() => {
  const segments = route.path.split('/')
  return segments[3] || 'overview'
})

async function loadCurrent() {
  if (!projectId.value) return
  try {
    await projectStore.loadCurrent(projectId.value)
  } catch {
    // ignore
  }
}

function navigate(name: string) {
  router.push(`/projects/${projectId.value}/${name}`)
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
  ElMessage.info(`搜索“${kw}”功能开发中`)
  closeSearch()
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

watch(projectId, () => { loadCurrent() }, { immediate: false })

onMounted(async () => {
  if (!projectStore.loaded) {
    try { await projectStore.loadList() } catch { /* ignore */ }
  }
  await loadCurrent()
  window.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div class="flex h-full overflow-hidden">
    <!-- 左侧导航栏 -->
    <aside
      class="flex shrink-0 flex-col border-r border-line bg-surface-muted transition-all"
      :class="sidebarCollapsed ? 'w-14' : 'w-52'"
    >
      <nav class="flex-1 overflow-y-auto py-3">
        <button
          v-for="item in NAV_ITEMS"
          :key="item.name"
          class="flex w-full items-center gap-2.5 px-4 py-2 text-left text-sm transition"
          :class="currentNav === item.name
            ? 'bg-primary/10 font-medium text-primary'
            : 'text-ink-regular hover:bg-black/5 hover:text-ink'"
          @click="navigate(item.name)"
        >
          <component :is="item.icon" :size="16" class="shrink-0" />
          <span v-if="!sidebarCollapsed" class="truncate">{{ item.label }}</span>
        </button>
      </nav>
      <!-- 折叠按钮 -->
      <button
        class="flex items-center justify-center border-t border-line py-2.5 text-ink-secondary transition hover:bg-black/5 hover:text-ink"
        @click="sidebarCollapsed = !sidebarCollapsed"
      >
        <ArrowRight :size="16" :class="sidebarCollapsed ? '' : 'rotate-180'" />
      </button>
    </aside>

    <!-- 主内容区 -->
    <main class="flex-1 overflow-hidden bg-surface-page">
      <RouterView />
    </main>

    <!-- 全局搜索弹窗 -->
    <div
      v-if="showSearchDialog"
      class="fixed inset-0 z-50 flex items-start justify-center bg-black/40 pt-[15vh]"
      @click="closeSearch"
    >
      <div
        class="w-full max-w-xl rounded-xl bg-white shadow-2xl"
        @click.stop
      >
        <div class="flex items-center gap-3 border-b border-line px-4 py-3">
          <Search :size="18" class="text-ink-secondary" />
          <input
            id="global-search-input"
            v-model="searchKeyword"
            class="flex-1 bg-transparent text-sm text-ink outline-none placeholder:text-ink-secondary"
            placeholder="搜索任务、缺陷、需求…"
            @keydown.enter="executeSearch"
            @keydown.escape="closeSearch"
          />
          <button class="text-ink-secondary transition hover:text-ink" @click="closeSearch">
            <X :size="18" />
          </button>
        </div>
        <div class="max-h-80 overflow-y-auto p-2">
          <div class="px-3 py-6 text-center text-sm text-ink-secondary">
            输入关键词后按回车搜索
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
