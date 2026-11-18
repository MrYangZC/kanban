import { defineStore } from 'pinia'
import { ref, computed, onScopeDispose } from 'vue'
import type { Project, ProjectCategoryKey, ProjectCategoryCounts, ViewMode } from '@/api/types'
import {
  filterProjects,
  getProjectCategoryCounts,
  favoriteProject,
  unfavoriteProject,
  archiveProject,
  unarchiveProject
} from '@/api'
import { errorMessage } from '@/utils/error'

const CATEGORY_STORAGE_KEY = 'kanban:projectCategory'
const VIEW_MODE_STORAGE_KEY = 'kanban:projectViewMode'
const PAGE_SIZE_STORAGE_KEY = 'kanban:projectPageSize'

interface CategoryItem {
  key: ProjectCategoryKey
  label: string
  count: number
}

const DEFAULT_CATEGORIES: CategoryItem[] = [
  { key: 'all', label: '全部项目', count: 0 },
  { key: 'managed', label: '我管理的', count: 0 },
  { key: 'participated', label: '我参与的', count: 0 },
  { key: 'favorited', label: '我收藏的', count: 0 },
  { key: 'archived', label: '已归档', count: 0 }
]

function loadStoredCategory(): ProjectCategoryKey {
  const stored = sessionStorage.getItem(CATEGORY_STORAGE_KEY)
  if (stored && DEFAULT_CATEGORIES.some((c) => c.key === stored)) {
    return stored as ProjectCategoryKey
  }
  return 'all'
}

function loadStoredViewMode(): ViewMode {
  const stored = localStorage.getItem(VIEW_MODE_STORAGE_KEY)
  return stored === 'list' ? 'list' : 'card'
}

function loadStoredPageSize(): number {
  const stored = localStorage.getItem(PAGE_SIZE_STORAGE_KEY)
  const n = Number(stored)
  return [10, 20, 25, 50].includes(n) ? n : 10
}

export const useProjectCategoryStore = defineStore('projectCategory', () => {
  const selectedCategory = ref<ProjectCategoryKey>(loadStoredCategory())
  const categories = ref<CategoryItem[]>(DEFAULT_CATEGORIES.map((c) => ({ ...c })))
  const projects = ref<Project[]>([])
  const loading = ref(false)
  const error = ref<string | null>(null)
  const viewMode = ref<ViewMode>(loadStoredViewMode())
  const searchKeyword = ref('')
  const currentPage = ref(1)
  const pageSize = ref(loadStoredPageSize())
  const total = ref(0)

  let searchTimer: ReturnType<typeof setTimeout> | null = null

  // 防抖定时器需随 store 作用域释放而清理，避免悬空回调触发已卸载后的请求
  onScopeDispose(() => {
    if (searchTimer) {
      clearTimeout(searchTimer)
      searchTimer = null
    }
  })

  const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize.value)))

  const currentCategoryItem = computed<CategoryItem | undefined>(() =>
    categories.value.find((c) => c.key === selectedCategory.value)
  )

  function updateCategoryCounts(counts: ProjectCategoryCounts) {
    const map: Record<ProjectCategoryKey, number> = {
      all: counts.all,
      managed: counts.managed,
      participated: counts.participated,
      favorited: counts.favorited,
      archived: counts.archived
    }
    categories.value = DEFAULT_CATEGORIES.map((c) => ({ ...c, count: map[c.key] }))
  }

  async function loadCategoryCounts() {
    try {
      const counts = await getProjectCategoryCounts()
      updateCategoryCounts(counts)
    } catch {
      categories.value = DEFAULT_CATEGORIES.map((c) => ({ ...c, count: 0 }))
    }
  }

  async function selectCategory(key: ProjectCategoryKey) {
    if (selectedCategory.value === key && projects.value.length > 0) return
    selectedCategory.value = key
    sessionStorage.setItem(CATEGORY_STORAGE_KEY, key)
    searchKeyword.value = ''
    currentPage.value = 1
    await loadProjects()
  }

  /**
   * 拉取项目列表。
   * silent=true 时不切加载态：用于「成员数变了」这类后台静默刷新，
   * 否则整块列表会被「加载中…」顶掉，用户正在看的卡片闪一下。
   */
  async function loadProjects(silent = false) {
    if (!silent) loading.value = true
    error.value = null
    try {
      const res = await filterProjects({
        category: selectedCategory.value,
        keyword: searchKeyword.value.trim() || undefined,
        page: currentPage.value,
        size: pageSize.value
      })
      projects.value = res.list
      total.value = res.total
    } catch (e: unknown) {
      error.value = errorMessage(e, '项目列表加载失败')
      projects.value = []
      total.value = 0
    } finally {
      // 静默刷新不碰 loading，否则会把并发中的正常加载态提前关掉
      if (!silent) loading.value = false
    }
  }

  function setViewMode(mode: ViewMode) {
    viewMode.value = mode
    localStorage.setItem(VIEW_MODE_STORAGE_KEY, mode)
  }

  function setSearchKeyword(keyword: string) {
    searchKeyword.value = keyword
    if (searchTimer) clearTimeout(searchTimer)
    searchTimer = setTimeout(async () => {
      currentPage.value = 1
      await loadProjects()
    }, 300)
  }

  async function setPage(page: number) {
    const clamped = Math.min(Math.max(1, page), totalPages.value)
    if (clamped === currentPage.value) return
    currentPage.value = clamped
    await loadProjects()
  }

  async function setPageSize(size: number) {
    pageSize.value = size
    localStorage.setItem(PAGE_SIZE_STORAGE_KEY, String(size))
    currentPage.value = 1
    await loadProjects()
  }

  /**
   * 设置收藏状态。
   *
   * <p>显式传目标态而不是"切换"：撤销操作可能在列表已经刷新之后触发，
   * 此时项目不一定还在当前列表里（例如在「已归档」分类下操作），
   * 依赖"读当前值再取反"会静默变成空操作。</p>
   */
  async function setFavorite(projectId: string, favorited: boolean) {
    const project = projects.value.find((p) => p.id === projectId)
    const previous = project?.favorited || false
    if (project) project.favorited = favorited
    try {
      if (favorited) {
        await favoriteProject(projectId)
      } else {
        await unfavoriteProject(projectId)
      }
      await loadCategoryCounts()
      if (selectedCategory.value === 'favorited') {
        await loadProjects()
      }
    } catch (e: unknown) {
      // 失败回滚乐观更新，由调用方决定提示方式
      if (project) project.favorited = previous
      throw e
    }
  }

  /** 切换收藏（界面上的星标按钮用），内部转成 setFavorite */
  async function toggleFavorite(projectId: string) {
    const project = projects.value.find((p) => p.id === projectId)
    if (!project) return
    await setFavorite(projectId, !(project.favorited || false))
  }

  /**
   * 设置归档状态（显式目标态，理由同 setFavorite：
   * 归档后项目会从当前列表消失，撤销时再"读状态取反"就找不到它了）。
   */
  async function setArchived(projectId: string, archived: boolean) {
    if (archived) {
      await archiveProject(projectId)
    } else {
      await unarchiveProject(projectId)
    }
    await loadCategoryCounts()
    await loadProjects()
  }

  /** 切换归档（行内按钮用），内部转成 setArchived */
  async function toggleArchive(projectId: string) {
    const project = projects.value.find((p) => p.id === projectId)
    if (!project) return
    await setArchived(projectId, project.status !== 'ARCHIVED')
  }

  return {
    selectedCategory,
    categories,
    projects,
    total,
    totalPages,
    currentPage,
    pageSize,
    currentCategoryItem,
    loading,
    error,
    viewMode,
    searchKeyword,
    loadCategoryCounts,
    selectCategory,
    loadProjects,
    setViewMode,
    setSearchKeyword,
    setPage,
    setPageSize,
    toggleFavorite,
    setFavorite,
    toggleArchive,
    setArchived
  }
})
