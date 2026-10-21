<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { useProjectPermission } from '@/composables/useProjectPermission'
import {
  LayoutDashboard, FileText, ListTodo, Bug,
  Repeat, Target, ShieldAlert, BarChart3, Settings,
  ArrowRight
} from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const projectStore = useProjectStore()

const projectId = computed(() => route.params.projectId as string)
const { hasPermission, refresh: refreshPermission } = useProjectPermission(() => projectId.value)

const sidebarCollapsed = ref(false)
/** 项目详情返回 403（非成员）时整区展示无权限提示 */
const accessDenied = ref(false)

interface NavItem {
  name: string
  label: string
  icon: typeof LayoutDashboard
  perm?: string
}

const ALL_NAV_ITEMS: NavItem[] = [
  { name: 'overview', label: '概览', icon: LayoutDashboard },
  { name: 'requirements', label: '需求', icon: FileText, perm: 'issue:view' },
  { name: 'tasks', label: '任务', icon: ListTodo, perm: 'issue:view' },
  { name: 'defects', label: '缺陷', icon: Bug, perm: 'issue:view' },
  { name: 'iterations', label: '迭代', icon: Repeat, perm: 'sprint:view' },
  { name: 'milestones', label: '里程碑', icon: Target, perm: 'milestone:view' },
  { name: 'risks', label: '风险', icon: ShieldAlert, perm: 'risk:view' },
  { name: 'metrics', label: '度量', icon: BarChart3 },
  { name: 'settings', label: '设置', icon: Settings, perm: 'project:setting' }
]

const NAV_ITEMS = computed(() =>
  ALL_NAV_ITEMS.filter((item) => !item.perm || hasPermission(item.perm))
)

const currentNav = computed(() => {
  const segments = route.path.split('/')
  return segments[3] || 'overview'
})

// 子视图 key：项目切换或工作项数据变更（顶部快捷新建等）时重建视图，保证列表重新拉取
const viewKey = computed(() => `${projectId.value}-${projectStore.refreshKey}`)

async function loadCurrent() {
  if (!projectId.value) return
  accessDenied.value = false
  try {
    await projectStore.loadCurrent(projectId.value)
  } catch (e: any) {
    // 403 = 非项目成员且非系统管理员：直接给明确的无权限页，避免整页空白只剩一条 toast
    if (e?.code === 403 || e?.status === 403) accessDenied.value = true
    // 其余错误（网络抖动等）沿用 store 中的当前项目，不阻断导航渲染
  }
}

function navigate(name: string) {
  router.push(`/projects/${projectId.value}/${name}`)
}

watch(projectId, () => { loadCurrent().then(() => refreshPermission()) }, { immediate: false })

onMounted(async () => {
  if (!projectStore.loaded) {
    try { await projectStore.loadList() } catch { /* 切换项目下拉降级为空列表 */ }
  }
  await loadCurrent()
  await refreshPermission()
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

    <!--
      主内容区。
      :key 绑定 viewKey：切换项目时路径参数变化但路由记录相同，
      不加 key 会复用同一组件实例（onMounted 不再触发），导致子视图展示上一个项目的数据。
    -->
    <main class="app-canvas flex-1 overflow-hidden">
      <RouterView v-if="!accessDenied" :key="viewKey" />
      <div v-else class="flex h-full flex-col items-center justify-center gap-3 px-6 text-center">
        <div class="flex h-12 w-12 items-center justify-center rounded-full bg-danger-light">
          <ShieldAlert :size="22" class="text-danger" />
        </div>
        <h2 class="text-base font-semibold text-ink">无权访问该项目</h2>
        <p class="max-w-sm text-sm text-ink-secondary">
          你当前不是该项目成员，无法查看其中的工作项、迭代与设置。请联系项目管理员将你加入成员。
        </p>
        <button class="btn-primary mt-1" @click="router.push('/projects')">返回项目列表</button>
      </div>
    </main>
  </div>
</template>
