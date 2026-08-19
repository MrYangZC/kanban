<script setup lang="ts">
import { onMounted, ref, reactive } from 'vue'
import { listUsers, createUser, updateUser, deleteUser } from '@/api'
import type { UserVO } from '@/api/types'
import { Plus, Trash2, Pencil } from 'lucide-vue-next'
import { ElMessage, ElMessageBox } from 'element-plus'

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

onMounted(load)
</script>

<template>
  <div class="page-container">
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="page-title">用户管理</h1>
        <p class="page-subtitle">维护系统成员，支持新建、编辑、启用/禁用与删除</p>
      </div>
      <button class="btn-primary" @click="openCreate"><Plus :size="16" /> 新建用户</button>
    </div>

    <div class="table-wrapper">
      <table class="w-full text-sm">
        <thead class="table-head">
          <tr>
            <th>用户</th><th>用户名</th><th>邮箱</th><th>状态</th><th class="text-right">操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="u in users" :key="u.id" class="table-row">
            <td class="px-5 py-3">
              <div class="flex items-center gap-3">
                <div class="avatar-sm avatar-primary">{{ u.displayName?.charAt(0) || 'U' }}</div>
                <span class="font-medium text-ink">{{ u.displayName }}</span>
              </div>
            </td>
            <td class="px-5 py-3 text-ink-regular">{{ u.username }}</td>
            <td class="px-5 py-3 text-ink-regular">{{ u.email || '—' }}</td>
            <td class="px-5 py-3">
              <button
                class="tag-status"
                :class="u.enabled ? 'bg-success-light text-success' : 'bg-surface-hover text-ink-secondary'"
                @click="toggleEnabled(u)"
              >
                <span class="h-1.5 w-1.5 rounded-full" :class="u.enabled ? 'bg-success' : 'bg-ink-placeholder'" />
                {{ u.enabled ? '启用' : '禁用' }}
              </button>
            </td>
            <td class="px-5 py-3">
              <div class="flex items-center justify-end gap-1">
                <button class="rounded-lg p-2 text-ink-secondary transition hover:bg-surface-hover" title="编辑" @click="openEdit(u)"><Pencil :size="15" /></button>
                <button class="rounded-lg p-2 text-ink-secondary transition hover:bg-danger-light" title="删除" @click="remove(u)"><Trash2 :size="15" /></button>
              </div>
            </td>
          </tr>
          <tr v-if="!users.length">
            <td colspan="5" class="px-5 py-12 text-center text-sm text-ink-secondary">暂无用户</td>
          </tr>
        </tbody>
      </table>
    </div>

    <div v-if="dialogOpen" class="fixed inset-0 z-40 flex items-center justify-center">
      <div class="absolute inset-0 bg-black/30" @click="dialogOpen = false" />
      <div class="relative z-50 w-[420px] rounded-lg bg-white p-6 shadow-modal">
        <h3 class="mb-4 text-base font-semibold text-ink">{{ editing ? '编辑用户' : '新建用户' }}</h3>
        <div class="space-y-3">
          <div>
            <label class="label-base">用户名</label>
            <input v-model="form.username" :disabled="!!editing" placeholder="登录账号，如 zhangsan" class="input-base disabled:bg-surface-hover" />
          </div>
          <div>
            <label class="label-base">显示名</label>
            <input v-model="form.displayName" placeholder="如 张三" class="input-base" />
          </div>
          <div>
            <label class="label-base">邮箱</label>
            <input v-model="form.email" placeholder="如 zhangsan@kanban.dev" class="input-base" />
          </div>
          <div>
            <label class="label-base">头像 URL</label>
            <input v-model="form.avatarUrl" placeholder="可选" class="input-base" />
          </div>
          <div>
            <label class="label-base">{{ editing ? '重置密码（留空不改）' : '密码（留空默认 admin123）' }}</label>
            <input v-model="form.password" type="password" placeholder="留空则使用默认密码" class="input-base" />
          </div>
        </div>
        <div class="mt-6 flex justify-end gap-2">
          <button class="rounded px-3.5 py-2 text-sm text-ink-secondary transition hover:bg-surface-hover" @click="dialogOpen = false">取消</button>
          <button class="btn-primary" @click="save">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>
