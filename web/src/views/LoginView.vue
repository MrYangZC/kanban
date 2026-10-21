<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login as apiLogin } from '@/api'
import { useUserStore } from '@/stores/user'
import { LayoutGrid } from 'lucide-vue-next'

const username = ref('')
const password = ref('')
const loading = ref(false)
const router = useRouter()
const userStore = useUserStore()

async function onSubmit() {
  if (!username.value || !password.value) return
  loading.value = true
  try {
    const vo = await apiLogin(username.value, password.value)
    userStore.setLogin(vo)
    router.push('/projects')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="app-canvas flex h-full items-center justify-center">
    <div class="glass-pop w-[380px] rounded-2xl p-8">
      <div class="mb-6 flex items-center gap-3">
        <div class="flex h-10 w-10 items-center justify-center rounded-xl bg-brand text-white">
          <LayoutGrid :size="22" />
        </div>
        <div>
          <div class="text-lg font-semibold text-ink-900">Kanban</div>
          <div class="text-xs text-ink-400">研发项目看板</div>
        </div>
      </div>
      <form class="space-y-4" @submit.prevent="onSubmit">
        <div>
          <label class="mb-1 block text-sm text-ink-600">用户名</label>
          <input
            v-model="username"
            class="w-full rounded-lg border border-surface-muted px-3 py-2 text-sm outline-none focus:border-brand"
            placeholder="请输入用户名"
          />
        </div>
        <div>
          <label class="mb-1 block text-sm text-ink-600">密码</label>
          <input
            v-model="password"
            type="password"
            class="w-full rounded-lg border border-surface-muted px-3 py-2 text-sm outline-none focus:border-brand"
            placeholder="请输入密码"
          />
        </div>
        <button
          type="submit"
          :disabled="loading"
          class="w-full rounded-lg bg-brand py-2.5 text-sm font-medium text-white transition hover:bg-brand-dark active:scale-[0.99] disabled:opacity-60"
        >
          {{ loading ? '登录中…' : '登 录' }}
        </button>
      </form>

    </div>
  </div>
</template>
