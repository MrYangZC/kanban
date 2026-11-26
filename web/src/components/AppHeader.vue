<script setup lang="ts">
import { ref, computed, nextTick, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notification'
import { getMyProfile, listProjectStatuses } from '@/api'
import type { NotificationVO } from '@/api/types'
import { fmtTime } from '@/utils/datetime'
import { ElMessage } from 'element-plus'
import CreateIssueDialog from '@/components/CreateIssueDialog.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import {
  ChevronDown, Search, Plus, Bell,
  FolderKanban, UserCog, LogOut, User, X, Shield
} from 'lucide-vue-next'

/** 品牌标识：放在 public/logo.svg，用 BASE_URL 拼接以适配 /kanban/ 子路径部署 */
const logoUrl = `${import.meta.env.BASE_URL}logo.svg`

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()
const userStore = useUserStore()
const notificationStore = useNotificationStore()

const isInProject = computed(() => !!route.params.projectId)
const projectId = computed(() => route.params.projectId as string)
const isAdmin = computed(() => userStore.isAdmin)

const showProjectDropdown = ref(false)
const showUserDropdown = ref(false)
const showNoticeDropdown = ref(false)
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
  showNoticeDropdown.value = false
}

function toggleNotice() {
  showNoticeDropdown.value = !showNoticeDropdown.value
  showProjectDropdown.value = false
  showUserDropdown.value = false
  if (showNoticeDropdown.value) {
    notificationStore.loadRecent()
    notificationStore.refreshUnread()
  }
}

/**
 * 点开通知即视为已读，随后跳到对应工作项。
 * 三类工作项的落地页不同：缺陷有独立详情页，任务/需求只有列表页。
 */
async function openNotification(n: NotificationVO) {
  showNoticeDropdown.value = false
  await notificationStore.markRead(n.id)
  if (!n.projectId || !n.issueId) return
  if (n.category === 'BUG') {
    router.push(`/projects/${n.projectId}/defects/${n.issueId}`)
  } else if (n.category === 'REQUIREMENT') {
    router.push(`/projects/${n.projectId}/requirements`)
  } else {
    router.push(`/projects/${n.projectId}/tasks`)
  }
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
  showNoticeDropdown.value = false
}

/** 切回标签页时立即校正未读数，避免等下一个轮询周期 */
function onWindowFocus() {
  // 未登录时不打这个接口：会在控制台留下 401 噪声，也拿不到有意义的数据
  if (!userStore.isLoggedIn) return
  notificationStore.refreshUnread()
}

watch(projectId, async () => {
  if (projectId.value) {
    // 顶部项目名展示属增强信息，失败不影响页面主体（接口层已提示错误）
    try { await projectStore.loadCurrent(projectId.value) } catch { /* 项目名降级为占位文案 */ }
  }
}, { immediate: false })

onMounted(async () => {
  // 未登录（首次访问 / 会话过期）时不要打任何用户级接口：
  // 头部在路由守卫跳转 /login 之前会短暂挂载，否则控制台会留下 401 噪声。
  if (userStore.isLoggedIn && !projectStore.loaded) {
    try { await projectStore.loadList() } catch { /* 切换项目下拉降级为空列表 */ }
  }
  if (userStore.isLoggedIn && projectId.value) {
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
  window.addEventListener('focus', onWindowFocus)
  // 通知是用户级数据，进站即开始轮询未读数（不依赖是否在项目内）。
  // 但必须先确认已登录：启动时若先落到受保护路由再被守卫弹回 /login，
  // 头部会短暂挂载，无条件请求 unread-count 会在控制台留下 401 噪声。
  if (userStore.isLoggedIn) {
    notificationStore.refreshUnread()
    notificationStore.startPolling()
  }
})

/**
 * 登录态变化时启停轮询：
 * 之前只在 onMounted 里启动，登录页不挂头部 → 登录成功后要刷新页面才开始轮询，
 * 反过来登出后定时器还活着，仍在后台打接口。
 */
watch(() => userStore.isLoggedIn, (loggedIn) => {
  if (loggedIn) {
    notificationStore.refreshUnread()
    notificationStore.startPolling()
  } else {
    notificationStore.reset()
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onKeydown)
  document.removeEventListener('click', onDocumentClick)
  window.removeEventListener('focus', onWindowFocus)
  notificationStore.stopPolling()
})
</script>

<template>
  <!-- z-50：.glass-bar 的 backdrop-filter 会生成独立层叠上下文，header 若不显式提层，
       其内部下拉仍会被 DOM 顺序靠后的内容区（同样带 backdrop-filter 的 toolbar/粘性表头）盖住 -->
  <header class="glass-bar relative z-50 flex h-14 shrink-0 items-center gap-4 border-b px-4">
    <div class="flex items-center">
      <img :src="logoUrl" alt="Kanban" class="h-8 w-auto select-none" draggable="false" />
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
      <!-- 通知：用户级数据，不在项目内也要能看（交接通知可能来自其他项目） -->
      <div class="relative" @click.stop>
        <button
          class="relative flex h-8 w-8 items-center justify-center rounded-md text-ink-regular transition hover:bg-surface-hover hover:text-primary"
          title="通知"
          @click="toggleNotice"
        >
          <Bell :size="18" />
          <span
            v-if="notificationStore.unreadCount"
            class="absolute -right-0.5 -top-0.5 flex h-4 min-w-[16px] items-center justify-center rounded-full bg-danger px-1 text-[10px] font-medium leading-none text-white"
          >
            {{ notificationStore.unreadCount > 99 ? '99+' : notificationStore.unreadCount }}
          </span>
        </button>
        <div v-if="showNoticeDropdown" class="glass-pop absolute right-0 top-full z-50 mt-1 w-80 rounded-lg py-1">
          <div class="flex items-center justify-between px-3 py-2">
            <span class="text-sm font-medium text-ink">通知</span>
            <button
              v-if="notificationStore.unreadCount"
              class="text-xs text-primary hover:underline"
              @click="notificationStore.markAllRead()"
            >
              全部已读
            </button>
          </div>
          <div class="max-h-80 overflow-y-auto">
            <div v-if="notificationStore.loading" class="px-3 py-6 text-center text-sm text-ink-secondary">加载中…</div>
            <div
              v-else-if="!notificationStore.items.length"
              class="px-3 py-6 text-center text-sm text-ink-secondary"
            >
              暂无通知
            </div>
            <button
              v-for="n in notificationStore.items"
              :key="n.id"
              class="flex w-full gap-2 px-3 py-2 text-left transition hover:bg-surface-hover"
              @click="openNotification(n)"
            >
              <span
                class="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full"
                :class="n.readAt ? 'bg-transparent' : 'bg-primary'"
              />
              <div class="min-w-0 flex-1">
                <div class="flex items-center justify-between gap-2">
                  <span class="truncate text-sm" :class="n.readAt ? 'text-ink-regular' : 'font-medium text-ink'">
                    {{ n.title }}
                  </span>
                  <span class="shrink-0 text-xs text-ink-secondary">{{ fmtTime(n.createdAt) }}</span>
                </div>
                <p class="mt-0.5 line-clamp-2 text-xs leading-relaxed text-ink-secondary">{{ n.body }}</p>
              </div>
            </button>
          </div>
        </div>
      </div>
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