<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { login as apiLogin } from '@/api'
import { useUserStore } from '@/stores/user'
import { LayoutGrid } from 'lucide-vue-next'

const username = ref('admin')
const password = ref('admin123')
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
  <div class="flex h-full items-center justify-center bg-gradient-to-br from-surface-base to-surface-muted">
    <div class="w-[380px] rounded-2xl bg-white p-8 shadow-xl ring-1 ring-black/5">
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
            placeholder="admin"
          />
        </div>
        <div>
          <label class="mb-1 block text-sm text-ink-600">密码</label>
          <input
            v-model="password"
            type="password"
            class="w-full rounded-lg border border-surface-muted px-3 py-2 text-sm outline-none focus:border-brand"
            placeholder="admin123"
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
      <div class="mt-4 text-center text-xs text-ink-400">默认账号 admin / admin123</div>
    </div>
  </div>
</template>
