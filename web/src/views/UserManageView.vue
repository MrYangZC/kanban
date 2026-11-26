<script setup lang="ts">
import { PASSWORD_RULES_TEXT, passwordError } from '@/utils/formRules'
import { computed, onBeforeUnmount, onMounted, ref, reactive, watch } from 'vue'
import { listUsersPage, createUser, updateUser, deleteUser, uploadImage } from '@/api'
import type { UserVO } from '@/api/types'
import { Plus, Search, Trash2, Pencil, X, ImagePlus, Users } from 'lucide-vue-next'
import UserAvatar from '@/components/common/UserAvatar.vue'
import TableSkeleton from '@/components/common/TableSkeleton.vue'
import TableEmpty from '@/components/common/TableEmpty.vue'
import Pagination from '@/components/project/Pagination.vue'
import { useViewportWidth } from '@/composables/useViewportWidth'
import { AVATAR_ACCEPT, validateAvatarFile } from '@/utils/avatar'
import { required, maxLen, minLen, email, codePattern } from '@/utils/formRules'
import { ElMessage } from 'element-plus'
import { confirmDelete } from '@/utils/confirm'

const users = ref<UserVO[]>([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)
// 窄屏列降级：从 DOM 移除列，避免 el-table 的 colgroup 留下空白占位
const { hideSmColumns } = useViewportWidth()
const dialogOpen = ref(false)
const editing = ref<UserVO | null>(null)
const form = reactive({
  username: '',
  displayName: '',
  email: '',
  avatarUrl: '',
  password: '',
  /** 系统管理员（sys_user.is_admin）：可进入用户管理/RBAC，并对全部项目有管理权 */
  isAdmin: false
})

/** 检索关键词：交由服务端按 username/显示名/邮箱匹配，避免前端全量过滤掩盖分页问题 */
const keyword = ref('')
const filteredUsers = computed(() => users.value)

async function load() {
  loading.value = true
  try {
    const r = await listUsersPage({
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim() || undefined
    })
    users.value = r.list
    total.value = r.total
  } finally {
    loading.value = false
  }
}

let searchTimer: ReturnType<typeof setTimeout> | undefined
watch(keyword, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    page.value = 1
    load()
  }, 300)
})

// 离开页面时取消挂起的防抖检索
onBeforeUnmount(() => {
  clearTimeout(searchTimer)
})

function onPageChange(p: number) {
  page.value = p
  load()
}

/** 改每页条数后回到第 1 页，否则可能停在不存在的页码上看到空列表 */
function onSizeChange(s: number) {
  size.value = s
  page.value = 1
  load()
}

function openCreate() {
  editing.value = null
  form.username = ''
  form.displayName = ''
  form.email = ''
  form.avatarUrl = ''
  form.password = ''
  form.isAdmin = false
  dialogOpen.value = true
}

const avatarInput = ref<HTMLInputElement | null>(null)
const uploadingAvatar = ref(false)

function triggerAvatarUpload() {
  avatarInput.value?.click()
}

async function onAvatarChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  // 立刻清空，否则重复选同一张图不会再触发 change
  input.value = ''
  if (!file) return
  const invalid = validateAvatarFile(file)
  if (invalid) {
    ElMessage.warning(invalid)
    return
  }
  uploadingAvatar.value = true
  try {
    const res = await uploadImage(file)
    // 只落到表单，点「保存」才写库：管理员可能先改了别的字段
    form.avatarUrl = res.url
  } catch {
    /* 拦截器已提示错误 */
  } finally {
    uploadingAvatar.value = false
  }
}

/** el-table 插槽给的是宽松行类型，入口统一收窄 */
function openEdit(raw: unknown) {
  const u = raw as UserVO
  editing.value = u
  form.username = u.username
  form.displayName = u.displayName
  form.email = u.email || ''
  form.avatarUrl = u.avatarUrl || ''
  form.password = ''
  form.isAdmin = !!u.isAdmin
  dialogOpen.value = true
}

/** 表单校验：新建时密码必填，编辑时留空表示不修改 */
const userFormRef = ref<{ validate: () => Promise<boolean>; clearValidate: () => void } | null>(null)

const passwordValidator = {
  validator: (_rule: unknown, value: string, callback: (e?: Error) => void) => {
    // 留空表示"不修改密码"（编辑态）；新建态的必填由 required 规则负责
    if (!value) return callback()
    const msg = passwordError(form.username || '', value)
    return msg ? callback(new Error(msg)) : callback()
  },
  trigger: 'blur'
}

const userRules = computed(() => ({
  username: editing.value ? [] : [required('用户名'), minLen(3, '用户名'), codePattern()],
  displayName: [required('显示名'), maxLen(30, '显示名')],
  email: [email()],
  password: editing.value
        ? [{ validator: passwordValidator, trigger: 'blur' }]
        : [required('初始密码'), { validator: passwordValidator, trigger: 'blur' }]
}))

async function onSubmitUser() {
  const valid = await userFormRef.value?.validate().then(() => true).catch(() => false)
  if (!valid) return
  await save()
}

async function save() {
  if (!form.username.trim() || !form.displayName.trim()) {
    ElMessage.warning('用户名与显示名必填')
    return
  }
  if (!editing.value && !form.password) {
    ElMessage.warning('新建用户必须设置初始密码')
    return
  }
  if (editing.value) {
    await updateUser(editing.value.id, {
      displayName: form.displayName,
      email: form.email,
      avatarUrl: form.avatarUrl.trim(),
      enabled: editing.value.enabled,
      isAdmin: form.isAdmin,
      password: form.password || undefined
    })
    ElMessage.success(`已更新用户「${form.displayName}」`)
  } else {
    await createUser({
      username: form.username,
      displayName: form.displayName,
      email: form.email,
      avatarUrl: form.avatarUrl,
      isAdmin: form.isAdmin,
      password: form.password || undefined
    })
    ElMessage.success('用户已创建')
  }
  dialogOpen.value = false
  await load()
}

async function toggleEnabled(raw: unknown) {
  const u = raw as UserVO
  await updateUser(u.id, { enabled: !u.enabled })
  await load()
}

async function remove(raw: unknown) {
  const u = raw as UserVO
  try {
    await confirmDelete(`确认删除用户「${u.displayName}」？`, '删除用户', '删除')
    await deleteUser(u.id)
    ElMessage.success(`已删除用户「${u.displayName}」`)
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
      <div class="flex items-center gap-3">
        <div class="search-box">
          <Search :size="15" class="text-ink-secondary" />
          <input v-model="keyword" class="search-input" placeholder="检索昵称，如 刘超" />
          <button v-if="keyword" class="text-ink-placeholder transition hover:text-ink-regular" title="清空" @click="keyword = ''"><X :size="13" /></button>
        </div>
        <button class="btn-primary" @click="openCreate"><Plus :size="16" /> 新建用户</button>
      </div>
    </div>

    <div class="table-wrapper">
      <div class="wi-card glass-panel">
        <div class="wi-card-body wi-table">
          <el-table
            :data="filteredUsers"
            row-key="id"
            class="wi-el-table"
                      >
            <el-table-column label="用户" min-width="220">
              <template #default="{ row }">
                <div class="flex items-center gap-3">
                  <UserAvatar :name="row.displayName" :src="row.avatarUrl" :size="32" />
                  <span class="font-medium text-ink">{{ row.displayName }}</span>
                  <span v-if="row.isAdmin" class="rounded bg-surface-hover px-1.5 py-0.5 text-[11px] text-ink-secondary">系统管理员</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="用户名" min-width="160">
              <template #default="{ row }"><span class="text-ink-regular">{{ row.username }}</span></template>
            </el-table-column>
            <el-table-column v-if="!hideSmColumns" label="邮箱" min-width="200">
              <template #default="{ row }"><span class="text-ink-regular">{{ row.email || '—' }}</span></template>
            </el-table-column>
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <button
                  class="tag-status"
                  :class="row.enabled ? 'bg-success-light text-success' : 'bg-surface-hover text-ink-secondary'"
                  @click="toggleEnabled(row)"
                >
                  <span class="h-1.5 w-1.5 rounded-full" :class="row.enabled ? 'bg-success' : 'bg-ink-placeholder'" />
                  {{ row.enabled ? '启用' : '禁用' }}
                </button>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" align="right">
              <template #default="{ row }">
                <div class="flex items-center justify-end gap-1">
                  <button class="rounded-lg p-2 text-ink-secondary transition hover:bg-surface-hover" title="编辑" @click="openEdit(row)"><Pencil :size="15" /></button>
                  <button class="rounded-lg p-2 text-ink-secondary transition hover:bg-danger-light" title="删除" @click="remove(row)"><Trash2 :size="15" /></button>
                </div>
              </template>
            </el-table-column>
            <template #empty>
              <TableSkeleton v-if="loading" :rows="6" :cols="4" />
              <TableEmpty
                v-else
                :icon="Users"
                :title="keyword ? '未找到匹配的用户' : '暂无用户'"
                :hint="keyword ? `没有匹配「${keyword}」的用户，换个关键字试试` : '点击右上角「新建用户」添加成员'"
              />
            </template>
          </el-table>
        </div>
      </div>
    </div>

    <div class="mt-4 flex justify-end">
      <Pagination
        :current-page="page"
        :total="total"
        :page-size="size"
        :page-sizes="[10, 20, 50]"
        @update:page="onPageChange"
        @update:page-size="onSizeChange"
      />
    </div>

    <div v-if="dialogOpen" class="fixed inset-0 z-40 flex items-center justify-center">
      <div class="absolute inset-0 bg-black/30" @click="dialogOpen = false" />
      <div class="glass-pop relative z-50 w-[420px] rounded-lg p-6">
        <h3 class="mb-4 text-base font-semibold text-ink">{{ editing ? '编辑用户' : '新建用户' }}</h3>
        <el-form ref="userFormRef" :model="form" :rules="userRules" label-width="88px" label-position="top">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" :disabled="!!editing" placeholder="登录账号，如 zhangsan" />
          </el-form-item>
          <el-form-item label="显示名" prop="displayName">
            <el-input v-model="form.displayName" placeholder="如 张三" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="form.email" placeholder="如 zhangsan@kanban.dev" />
          </el-form-item>
          <el-form-item label="头像">
            <div class="flex items-center gap-3">
              <UserAvatar :name="form.displayName" :src="form.avatarUrl" :size="44" />
              <div class="flex items-center gap-2">
                <button
                  type="button"
                  class="btn-ghost"
                  :disabled="uploadingAvatar"
                  @click="triggerAvatarUpload"
                >
                  <ImagePlus :size="14" />
                  <span>{{ uploadingAvatar ? '上传中…' : '上传图片' }}</span>
                </button>
                <button
                  v-if="form.avatarUrl"
                  type="button"
                  class="rounded px-2 py-1.5 text-xs text-ink-secondary transition hover:bg-surface-hover"
                  @click="form.avatarUrl = ''"
                >
                  清除
                </button>
              </div>
              <input
                ref="avatarInput"
                type="file"
                :accept="AVATAR_ACCEPT"
                class="hidden"
                @change="onAvatarChange"
              />
            </div>
            <p class="mt-1.5 text-xs text-ink-secondary">支持 jpg / png / gif / webp，不超过 5MB</p>
          </el-form-item>
          <el-form-item label="系统管理员">
            <label class="flex items-center gap-2 text-sm text-ink-regular">
              <input v-model="form.isAdmin" type="checkbox" />
              <span>可进入用户管理 / RBAC，并对全部项目拥有管理权</span>
            </label>
          </el-form-item>
          <el-form-item :label="editing ? '重置密码（留空不改）' : '初始密码'" prop="password">
            <el-input v-model="form.password" type="password" show-password :placeholder="editing ? '留空则不修改密码' : '请输入初始密码'" />
            <div class="mt-1 text-xs text-ink-secondary">{{ PASSWORD_RULES_TEXT }}</div>
          </el-form-item>
        </el-form>
        <div class="mt-6 flex justify-end gap-2">
          <button class="rounded px-3.5 py-2 text-sm text-ink-secondary transition hover:bg-surface-hover" @click="dialogOpen = false">取消</button>
          <button class="btn-primary" @click="onSubmitUser">保存</button>
        </div>
      </div>
    </div>
  </div>
</template>
