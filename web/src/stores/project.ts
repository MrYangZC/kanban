import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Project } from '@/api/types'
import { listProjects, getProject } from '@/api'

export const useProjectStore = defineStore('project', () => {
  const current = ref<Project | null>(null)
  const list = ref<Project[]>([])
  const loaded = ref(false)

  /**
   * 工作项变更信号：在项目外的入口（如顶部快捷新建）创建/修改工作项后自增，
   * 由 ProjectLayout 作为 RouterView 的 key 的一部分，触发当前视图重建并重新拉取数据。
   */
  const refreshKey = ref(0)

  function bumpRefresh() {
    refreshKey.value += 1
  }

  async function loadList() {
    list.value = await listProjects()
    loaded.value = true
  }

  /**
   * 加载当前项目详情。
   * force=true 强制刷新：编辑项目名称后若沿用缓存，顶部标题会一直显示旧名称。
   */
  async function loadCurrent(id: string, force = false) {
    if (!force && current.value?.id === id) return
    current.value = await getProject(id)
  }

  function clear() {
    current.value = null
    list.value = []
    loaded.value = false
  }

  return { current, list, loaded, refreshKey, loadList, loadCurrent, clear, bumpRefresh }
})