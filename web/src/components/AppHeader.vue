<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { useUserStore } from '@/stores/user'
import { getMyProfile, listProjectStatuses } from '@/api'
import { ElMessage } from 'element-plus'
import CreateIssueDialog from '@/components/CreateIssueDialog.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
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
const isAdmin = computed(() => userStore.isAdmin)

const showProjectDropdown = ref(false)
const showUserDropdown = ref(false)
const showSearchDialog = ref(false)
const searchKeyword = ref('')
const createOpen = ref(false)
const createDefaultStatusId = ref('')
/** 搜索框聚焦用模板引用：避免 querySelector + 固定 DOM id（多实例渲染时会重复 id） */
const searchInputRef = ref<HTMLInputElement | null>(null)

const navItems = computed(() => {
  const items: { path: string; label: string; icon: typeof FolderKanban }[] = [
    { path: '/projects', label: '项目', icon: FolderKanban }
  ]
  if (isAdmin.value) {
    items.push({ path: '/users', label: '用户管理', icon: UserCog })
    items.push({ path: '/rbac', label: '权限管理', icon: Shield })
  }
  return items
})

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
  // 等对话框渲染完成再聚焦，替代原先的 setTimeout(50) 猜时序
  nextTick(() => searchInputRef.value?.focus())
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

/**
 * 顶部快捷新建：复用新建工作项弹窗真正落库。
 * 后端校验 statusId 必填，这里取「任务工作流」的首个状态（待办）作为默认落点——
 * 弹窗未指定类型时按任务创建，不能再用缺陷流的「待确认」。
 */
async function onCreate() {
  if (!projectId.value) return
  try {
    const statuses = await listProjectStatuses(projectId.value, 'TASK')
    createDefaultStatusId.value = statuses[0]?.id || ''
    if (!createDefaultStatusId.value) {
      ElMessage.warning('当前项目尚未配置工作流状态，无法新建工作项')
      return
    }
    createOpen.value = true
  } catch {
    // 状态字典加载失败已由请求层统一提示
  }
}

/** 新建成功后通知项目视图重建，避免列表仍显示旧数据 */
function onCreated() {
  projectStore.bumpRefresh()
}

async function logout() {
  // 通过 store 通知后端销毁会话；失败也已由 store 兜底完成本地清理
  await userStore.logout()
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
    // 顶部项目名展示属增强信息，失败不影响页面主体（接口层已提示错误）
    try { await projectStore.loadCurrent(projectId.value) } catch { /* 项目名降级为占位文案 */ }
  }
}, { immediate: false })

onMounted(async () => {
  if (!projectStore.loaded) {
    try { await projectStore.loadList() } catch { /* 切换项目下拉降级为空列表 */ }
  }
  if (projectId.value) {
    try { await projectStore.loadCurrent(projectId.value) } catch { /* 项目名降级为占位文案 */ }
  }
  // 本地缓存的登录信息可能早于头像上传（老用户/换头像后未重新登录），启动时用 /users/me 校正一次，
  // 失败就沿用缓存，不阻塞头部渲染
  if (userStore.user) {
    try {
      const me = await getMyProfile()
      userStore.patchProfile({ displayName: me.displayName, avatarUrl: me.avatarUrl })
    } catch { /* 保留缓存头像 */ }
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
  <!-- z-50：.glass-bar 的 backdrop-filter 会生成独立层叠上下文，header 若不显式提层，
       其内部下拉仍会被 DOM 顺序靠后的内容区（同样带 backdrop-filter 的 toolbar/粘性表头）盖住 -->
  <header class="glass-bar relative z-50 flex h-14 shrink-0 items-center gap-4 border-b px-4">
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

    <div v-if="isInProject" class="relative flex items-center" @click.stop>
      <button
        class="flex items-center gap-1.5 rounded-md px-2.5 py-1.5 text-sm font-medium text-ink transition hover:bg-surface-hover"
        title="返回项目列表"
        @click="router.push('/projects')"
      >
        <FolderKanban :size="15" class="text-ink-secondary" />
        <span class="max-w-[160px] truncate">{{ projectStore.current?.name || '选择项目' }}</span>
      </button>
      <button
        class="flex h-7 w-7 items-center justify-center rounded-md text-ink-secondary transition hover:bg-surface-hover"
        title="切换项目"
        @click="toggleProjectDropdown"
      >
        <ChevronDown :size="14" />
      </button>
      <div v-if="showProjectDropdown" class="glass-pop absolute left-0 top-full z-50 mt-1 w-64 rounded-lg py-1">
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
      <kbd class="hidden rounded border border-line bg-white/70 px-1.5 text-xs text-ink-secondary md:inline">Ctrl K</kbd>
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
      <!-- 通知中心尚未接入数据源，暂不显示未读数，避免展示编造的数字 -->
      <button
        v-if="isInProject"
        class="relative flex h-8 w-8 items-center justify-center rounded-md text-ink-regular transition hover:bg-surface-hover hover:text-primary"
        title="通知"
        disabled
      >
        <Bell :size="18" />
      </button>
      <div class="relative" @click.stop>
        <button class="flex items-center gap-1.5 rounded-md p-1 transition hover:bg-surface-hover" @click="toggleUserDropdown">
          <UserAvatar :name="userStore.user?.displayName" :src="userStore.user?.avatarUrl" :size="32" />
          <ChevronDown :size="12" class="text-ink-secondary" />
        </button>
        <div v-if="showUserDropdown" class="glass-pop absolute right-0 top-full z-50 mt-1 w-48 rounded-lg py-1">
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

    <!-- 传送到 body：header 的 backdrop-filter 会成为 fixed 后代的包含块，
         写在 header 里遮罩只能盖住页头那一条，点击空白处也关不掉 -->
    <Teleport to="body">
      <div v-if="showSearchDialog" class="fixed inset-0 z-[60] flex items-start justify-center bg-black/40 pt-[15vh]" @click="closeSearch">
        <div class="glass-pop w-full max-w-xl rounded-lg" @click.stop>
          <div class="flex items-center gap-3 border-b border-line-light px-4 py-3">
            <Search :size="18" class="text-ink-secondary" />
            <input
              ref="searchInputRef"
              v-model="searchKeyword"
              class="flex-1 bg-transparent text-sm text-ink outline-none placeholder:text-ink-secondary"
              placeholder="搜索任务、缺陷、需求…"
              @keydown.enter="executeSearch"
            />
            <button class="text-ink-secondary transition hover:text-ink" @click="closeSearch"><X :size="18" /></button>
          </div>
          <div class="max-h-80 overflow-y-auto p-2">
            <div class="px-3 py-6 text-center text-sm text-ink-secondary">输入关键词后按回车搜索</div>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 顶部快捷新建：真正落库的新建工作项弹窗（此前 createOpen 只被赋值、未渲染） -->
    <CreateIssueDialog
      v-if="isInProject"
      v-model="createOpen"
      :project-id="projectId"
      :default-status-id="createDefaultStatusId"
      @created="onCreated"
    />
  </header>
</template>