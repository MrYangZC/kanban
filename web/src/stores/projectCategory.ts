import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { Project, ProjectCategoryKey, ProjectCategoryCounts, ViewMode } from '@/api/types'
import {
  filterProjects,
  getProjectCategoryCounts,
  favoriteProject,
  unfavoriteProject,
  archiveProject,
  unarchiveProject
} from '@/api'

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

  async function loadProjects() {
    loading.value = true
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
    } catch (e: any) {
      error.value = e?.message || '项目列表加载失败'
      projects.value = []
      total.value = 0
    } finally {
      loading.value = false
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

  async function toggleFavorite(projectId: string) {
    const project = projects.value.find((p) => p.id === projectId)
    if (!project) return
    const wasFavorited = project.favorited || false
    project.favorited = !wasFavorited
    try {
      if (wasFavorited) {
        await unfavoriteProject(projectId)
      } else {
        await favoriteProject(projectId)
      }
      await loadCategoryCounts()
      if (selectedCategory.value === 'favorited' && !wasFavorited) {
        await loadProjects()
      }
    } catch (e: any) {
      project.favorited = wasFavorited
      throw e
    }
  }

  async function toggleArchive(projectId: string) {
    const project = projects.value.find((p) => p.id === projectId)
    if (!project) return
    const isArchived = project.status === 'ARCHIVED'
    try {
      if (isArchived) {
        await unarchiveProject(projectId)
      } else {
        await archiveProject(projectId)
      }
      await loadCategoryCounts()
      await loadProjects()
    } catch (e: any) {
      throw e
    }
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
    toggleArchive
  }
})
