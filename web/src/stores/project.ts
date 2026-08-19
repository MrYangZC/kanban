import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { Project } from '@/api/types'
import { listProjects, getProject } from '@/api'

export const useProjectStore = defineStore('project', () => {
  const current = ref<Project | null>(null)
  const list = ref<Project[]>([])
  const loaded = ref(false)

  async function loadList() {
    list.value = await listProjects()
    loaded.value = true
  }

  async function loadCurrent(id: string) {
    if (current.value?.id === id) return
    current.value = await getProject(id)
  }

  function clear() {
    current.value = null
  }

  return { current, list, loaded, loadList, loadCurrent, clear }
})