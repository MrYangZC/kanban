<script setup lang="ts">
import { onMounted, ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { listUsers, createUser, updateUser, deleteUser } from '@/api'
import type { UserVO } from '@/api/types'
import { useUserStore } from '@/stores/user'
import { LayoutGrid, Plus, Users, FolderKanban, LogOut, UserCog, Trash2, Pencil } from 'lucide-vue-next'
import { ElMessage, ElMessageBox } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()

const users = ref<UserVO[]>([])
const loading = ref(false)
const dialogOpen = ref(false)
const editing = ref<UserVO | null>(null)
const form = reactive({
  username: '',
  displayName: '',
  email: '',
  avatarUrl: '',
  password: ''
})

async function load() {
  loading.value = true
  try {
    users.value = await listUsers()
  } finally {
    loading.value = false
  }
}

function openCreate() {
  editing.value = null
  form.username = ''
  form.displayName = ''
  form.email = ''
  form.avatarUrl = ''
  form.password = ''
  dialogOpen.value = true
}

function openEdit(u: UserVO) {
  editing.value = u
  form.username = u.username
  form.displayName = u.displayName
  form.email = u.email || ''
  form.avatarUrl = u.avatarUrl || ''
  form.password = ''
  dialogOpen.value = true
}

async function save() {
  if (!form.username.trim() || !form.displayName.trim()) {
    ElMessage.warning('用户名与显示名必填')
    return
  }
  if (editing.value) {
    await updateUser(editing.value.id, {
      displayName: form.displayName,
      email: form.email,
      avatarUrl: form.avatarUrl,
      enabled: editing.value.enabled,
      password: form.password || undefined
    })
    ElMessage.success('已更新')
  } else {
    await createUser({
      username: form.username,
      displayName: form.displayName,
      email: form.email,
      avatarUrl: form.avatarUrl,
      password: form.password || undefined
    })
    ElMessage.success('用户已创建（默认密码 admin123）')
  }
  dialogOpen.value = false
  await load()
}

async function toggleEnabled(u: UserVO) {
  await updateUser(u.id, { enabled: !u.enabled })
  await load()
}

async function remove(u: UserVO) {
  try {
    await ElMessageBox.confirm(`确认删除用户「${u.displayName}」？`, '删除用户', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消'
    })
    await deleteUser(u.id)
    ElMessage.success('已删除')
    await load()
  } catch {
    /* 取消 */
  }
}

function logout() {
  userStore.logout()
  router.push('/login')
}

onMounted(load)
</script>

<template>
  <div class="flex h-full">
    <!-- 侧边栏 -->
    <aside class="flex w-60 flex-col bg-ink-900 text-white/90">
      <div class="flex items-center gap-2 px-5 py-5">
        <LayoutGrid :size="20" class="text-brand-light" />
        <span class="font-semibold">Kanban</span>
      </div>
      <div class="px-3">
        <div class="mb-2 px-2 text-xs uppercase text-white/40">管理</div>
        <button
          class="mb-1 flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm transition hover:bg-white/5"
          @click="router.push('/projects')"
        >
          <FolderKanban :size="15" />
          <span>项目</span>
        </button>
        <button
          class="mb-1 flex w-full items-center gap-2 rounded-lg bg-white/10 px-3 py-2 text-left text-sm transition"
        >
          <UserCog :size="15" />
          <span>用户管理</span>
        </button>
      </div>
      <div class="mt-auto px-3 py-4">
        <div class="flex items-center gap-2 rounded-lg px-2 py-2">
          <div class="flex h-8 w-8 items-center justify-center rounded-full bg-brand text-sm font-medium">
            {{ userStore.user?.displayName?.charAt(0) || 'U' }}
          </div>
          <div class="flex-1 truncate text-sm">{{ userStore.user?.displayName }}</div>
          <button class="hover:text-white/60" title="退出" @click="logout"><LogOut :size="15" /></button>
        </div>
      </div>
    </aside>

    <!-- 主区 -->
    <main class="flex-1 overflow-auto p-8">
      <div class="mb-6 flex items-center justify-between">
        <div>
          <h1 class="text-xl font-semibold text-ink-900">用户管理</h1>
          <p class="text-sm text-ink-400">维护系统成员，支持新建、编辑、启用/禁用与删除</p>
        </div>
        <button
          class="flex items-center gap-1.5 rounded-lg bg-brand px-3.5 py-2 text-sm font-medium text-white transition hover:bg-brand-dark"
          @click="openCreate"
        >
          <Plus :size="16" /> 新建用户
        </button>
      </div>

      <div class="overflow-hidden rounded-xl bg-white shadow-sm ring-1 ring-black/5">
        <table class="w-full text-sm">
          <thead>
            <tr class="border-b border-surface-muted bg-surface-muted/40 text-left text-xs text-ink-400">
              <th class="px-5 py-3">用户</th>
              <th class="px-5 py-3">用户名</th>
              <th class="px-5 py-3">邮箱</th>
              <th class="px-5 py-3">状态</th>
              <th class="px-5 py-3 text-right">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="u in users"
              :key="u.id"
              class="border-b border-surface-muted/60 transition last:border-0 hover:bg-surface-muted/30"
            >
              <td class="px-5 py-3">
                <div class="flex items-center gap-3">
                  <div class="flex h-8 w-8 items-center justify-center rounded-full bg-brand/10 text-sm font-medium text-brand">
                    {{ u.displayName?.charAt(0) || 'U' }}
                  </div>
                  <span class="font-medium text-ink-900">{{ u.displayName }}</span>
                </div>
              </td>
              <td class="px-5 py-3 text-ink-600">{{ u.username }}</td>
              <td class="px-5 py-3 text-ink-600">{{ u.email || '—' }}</td>
              <td class="px-5 py-3">
                <button
                  class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium transition"
                  :class="u.enabled ? 'bg-green-50 text-green-600' : 'bg-ink-100 text-ink-400'"
                  @click="toggleEnabled(u)"
                >
                  <span class="h-1.5 w-1.5 rounded-full" :class="u.enabled ? 'bg-green-500' : 'bg-ink-300'" />
                  {{ u.enabled ? '启用' : '禁用' }}
                </button>
              </td>
              <td class="px-5 py-3">
                <div class="flex items-center justify-end gap-1">
                  <button class="rounded-lg p-2 text-ink-400 transition hover:bg-surface-muted hover:text-brand" title="编辑" @click="openEdit(u)">
                    <Pencil :size="15" />
                  </button>
                  <button class="rounded-lg p-2 text-ink-400 transition hover:bg-red-50 hover:text-red-500" title="删除" @click="remove(u)">
                    <Trash2 :size="15" />
                  </button>
                </div>
              </td>
            </tr>
            <tr v-if="!users.length">
              <td colspan="5" class="px-5 py-12 text-center text-sm text-ink-400">暂无用户</td>
            </tr>
          </tbody>
        </table>
      </div>
    </main>

    <!-- 新建/编辑弹窗 -->
    <div v-if="dialogOpen" class="fixed inset-0 z-40 flex items-center justify-center">
      <div class="absolute inset-0 bg-black/30" @click="dialogOpen = false" />
      <div class="relative z-50 w-[420px] rounded-2xl bg-white p-6 shadow-2xl">
        <h3 class="mb-4 text-base font-semibold text-ink-900">
          {{ editing ? '编辑用户' : '新建用户' }}
        </h3>
        <div class="space-y-3">
          <div>
            <label class="mb-1 block text-xs text-ink-400">用户名</label>
            <input
              v-model="form.username"
              :disabled="!!editing"
              placeholder="登录账号，如 zhangsan"
              class="w-full rounded-lg border border-surface-muted px-3 py-2 text-sm outline-none focus:border-brand disabled:bg-surface-muted/50"
            />
          </div>
          <div>
            <label class="mb-1 block text-xs text-ink-400">显示名</label>
            <input v-model="form.displayName" placeholder="如 张三" class="w-full rounded-lg border border-surface-muted px-3 py-2 text-sm outline-none focus:border-brand" />
          </div>
          <div>
            <label class="mb-1 block text-xs text-ink-400">邮箱</label>
            <input v-model="form.email" placeholder="如 zhangsan@kanban.dev" class="w-full rounded-lg border border-surface-muted px-3 py-2 text-sm outline-none focus:border-brand" />
          </div>
          <div>
            <label class="mb-1 block text-xs text-ink-400">头像 URL</label>
            <input v-model="form.avatarUrl" placeholder="可选" class="w-full rounded-lg border border-surface-muted px-3 py-2 text-sm outline-none focus:border-brand" />
          </div>
          <div>
            <label class="mb-1 block text-xs text-ink-400">{{ editing ? '重置密码（留空不改）' : '密码（留空默认 admin123）' }}</label>
            <input v-model="form.password" type="password" placeholder="留空则使用默认密码" class="w-full rounded-lg border border-surface-muted px-3 py-2 text-sm outline-none focus:border-brand" />
          </div>
        </div>
        <div class="mt-6 flex justify-end gap-2">
          <button class="rounded-lg px-3.5 py-2 text-sm text-ink-600 transition hover:bg-surface-muted" @click="dialogOpen = false">取消</button>
          <button class="rounded-lg bg-brand px-3.5 py-2 text-sm font-medium text-white transition hover:bg-brand-dark" @click="save">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>
