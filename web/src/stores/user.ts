import { defineStore } from 'pinia'
import { ref } from 'vue'
import type { LoginVO } from '@/api/types'

export const useUserStore = defineStore('user', () => {
  const user = ref<LoginVO | null>(null)
  const token = ref<string>('')

  function setLogin(vo: LoginVO) {
    user.value = vo
    token.value = vo.token
    localStorage.setItem('kanban_user', JSON.stringify(vo))
  }

  function restore() {
    const raw = localStorage.getItem('kanban_user')
    if (raw) {
      user.value = JSON.parse(raw)
      token.value = user.value?.token || ''
    }
  }

  function logout() {
    user.value = null
    token.value = ''
    localStorage.removeItem('kanban_user')
  }

  return { user, token, setLogin, restore, logout }
})
