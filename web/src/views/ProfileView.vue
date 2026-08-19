<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { getMyProfile, updateMyProfile, changeMyPassword, uploadImage } from '@/api'
import { useUserStore } from '@/stores/user'
import type { ProfileVO } from '@/api/types'
import { ElMessage } from 'element-plus'
import { User, Mail, Lock, Save, ArrowLeft, Camera, ShieldCheck, Clock, BadgeCheck } from 'lucide-vue-next'

const router = useRouter()
const userStore = useUserStore()

const profile = ref<ProfileVO | null>(null)
const loading = ref(true)

const form = ref({ displayName: '', email: '', avatarUrl: '' })
const savingProfile = ref(false)
const uploadingAvatar = ref(false)
const avatarInput = ref<HTMLInputElement | null>(null)

const pwdForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const savingPwd = ref(false)

const avatarDisplay = computed(() => profile.value?.avatarUrl || '')
const initial = computed(() => profile.value?.displayName?.charAt(0) || 'U')

function fmtTime(t?: string | null): string {
  if (!t) return '-'
  const d = new Date(t)
  if (isNaN(d.getTime())) return t
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

async function loadProfile() {
  loading.value = true
  try {
    profile.value = await getMyProfile()
    form.value = {
      displayName: profile.value.displayName || '',
      email: profile.value.email || '',
      avatarUrl: profile.value.avatarUrl || ''
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '加载个人信息失败')
  } finally {
    loading.value = false
  }
}

function triggerAvatarUpload() {
  avatarInput.value?.click()
}

async function onAvatarChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  uploadingAvatar.value = true
  try {
    const res = await uploadImage(file)
    form.value.avatarUrl = res.url
    const updated = await updateMyProfile({ avatarUrl: res.url })
    profile.value = updated
    syncLoginStore(updated)
    ElMessage.success('头像已更新')
  } catch (e: any) {
    ElMessage.error(e?.message || '头像上传失败')
  } finally {
    uploadingAvatar.value = false
    input.value = ''
  }
}

function syncLoginStore(p: ProfileVO) {
  if (userStore.user) {
    userStore.setLogin({
      ...userStore.user,
      displayName: p.displayName,
      token: userStore.user.token
    })
  }
}

async function onSaveProfile() {
  if (!form.value.displayName.trim()) {
    ElMessage.warning('显示名不能为空')
    return
  }
  savingProfile.value = true
  try {
    const updated = await updateMyProfile({
      displayName: form.value.displayName.trim(),
      email: form.value.email.trim(),
      avatarUrl: form.value.avatarUrl
    })
    profile.value = updated
    syncLoginStore(updated)
    ElMessage.success('个人信息已保存')
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    savingProfile.value = false
  }
}

async function onChangePassword() {
  if (!pwdForm.value.oldPassword) {
    ElMessage.warning('请输入原密码')
    return
  }
  if (!pwdForm.value.newPassword) {
    ElMessage.warning('请输入新密码')
    return
  }
  if (pwdForm.value.newPassword.length < 6) {
    ElMessage.warning('新密码长度不能少于 6 位')
    return
  }
  if (pwdForm.value.newPassword !== pwdForm.value.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  savingPwd.value = true
  try {
    await changeMyPassword({
      oldPassword: pwdForm.value.oldPassword,
      newPassword: pwdForm.value.newPassword
    })
    pwdForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
    ElMessage.success('密码修改成功')
  } catch (e: any) {
    ElMessage.error(e?.message || '修改密码失败')
  } finally {
    savingPwd.value = false
  }
}

onMounted(loadProfile)
</script>

<template>
  <div class="page-container">
    <div class="mx-auto max-w-5xl">
      <!-- 顶部标题 -->
      <div class="mb-6 flex items-center gap-3">
        <button
          class="flex h-8 w-8 items-center justify-center rounded-md text-ink-regular transition hover:bg-surface-hover hover:text-primary"
          title="返回"
          @click="router.back()"
        >
          <ArrowLeft :size="18" />
        </button>
        <User :size="20" class="text-brand" />
        <h1 class="text-lg font-semibold text-ink-900">个人中心</h1>
      </div>

      <!-- 加载中 -->
      <div v-if="loading" class="rounded-lg border border-[#E9ECEF] bg-white p-12 text-center text-sm text-ink-400">
        加载中…
      </div>

      <template v-else-if="profile">
        <!-- 头部卡片：头像 + 概览 -->
        <div class="mb-4 overflow-hidden rounded-lg border border-[#E9ECEF] bg-white shadow-sm">
          <div class="h-24 bg-gradient-to-r from-primary-light to-brand"></div>
          <div class="flex items-end gap-4 px-6 pb-5">
            <div class="relative -mt-12">
              <div
                v-if="avatarDisplay"
                class="h-24 w-24 overflow-hidden rounded-full border-4 border-white bg-white shadow-sm"
              >
                <img :src="avatarDisplay" :alt="profile.displayName" class="h-full w-full object-cover" />
              </div>
              <div
                v-else
                class="flex h-24 w-24 items-center justify-center rounded-full border-4 border-white bg-primary text-3xl font-medium text-white shadow-sm"
              >
                {{ initial }}
              </div>
              <button
                class="absolute bottom-1 right-1 flex h-7 w-7 items-center justify-center rounded-full border border-[#E5E6EB] bg-white text-ink-secondary shadow-sm transition hover:text-primary"
                title="更换头像"
                :disabled="uploadingAvatar"
                @click="triggerAvatarUpload"
              >
                <Camera :size="14" />
              </button>
              <input
                ref="avatarInput"
                type="file"
                accept="image/*"
                class="hidden"
                @change="onAvatarChange"
              />
            </div>
            <div class="flex-1 pb-1">
              <div class="flex items-center gap-2">
                <span class="text-lg font-semibold text-ink-900">{{ profile.displayName }}</span>
                <span
                  v-if="profile.enabled"
                  class="inline-flex items-center gap-1 rounded-full bg-success-light px-2 py-0.5 text-xs font-medium text-success"
                >
                  <BadgeCheck :size="12" /> 正常
                </span>
                <span
                  v-else
                  class="inline-flex items-center gap-1 rounded-full bg-surface-muted px-2 py-0.5 text-xs font-medium text-ink-secondary"
                >
                  已禁用
                </span>
              </div>
              <div class="mt-0.5 text-sm text-ink-400">@{{ profile.username }}</div>
            </div>
          </div>
        </div>

        <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
          <!-- 基本信息编辑 -->
          <div class="rounded-lg border border-[#E9ECEF] bg-white p-5 shadow-sm">
            <div class="mb-4 flex items-center gap-2 text-sm font-medium text-ink-900">
              <User :size="16" class="text-brand" /> 基本信息
            </div>
            <div class="space-y-4">
              <div>
                <label class="mb-1 block text-sm text-ink-400">显示名</label>
                <input
                  v-model="form.displayName"
                  class="w-full rounded-md border border-[#E5E6EB] px-3 py-2 text-sm outline-none focus:border-brand"
                  placeholder="显示名"
                />
              </div>
              <div>
                <label class="mb-1 block text-sm text-ink-400">邮箱</label>
                <div class="relative">
                  <Mail :size="14" class="absolute left-3 top-1/2 -translate-y-1/2 text-ink-secondary" />
                  <input
                    v-model="form.email"
                    type="email"
                    class="w-full rounded-md border border-[#E5E6EB] py-2 pl-9 pr-3 text-sm outline-none focus:border-brand"
                    placeholder="name@example.com"
                  />
                </div>
              </div>
              <div>
                <label class="mb-1 block text-sm text-ink-400">头像 URL</label>
                <input
                  v-model="form.avatarUrl"
                  class="w-full rounded-md border border-[#E5E6EB] px-3 py-2 text-sm outline-none focus:border-brand"
                  placeholder="留空则使用首字母头像"
                />
                <div class="mt-1 text-xs text-ink-secondary">点击左上角相机图标可直接上传图片</div>
              </div>
              <div class="flex justify-end">
                <button
                  class="inline-flex items-center gap-1.5 rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark disabled:opacity-50"
                  :disabled="savingProfile"
                  @click="onSaveProfile"
                >
                  <Save :size="14" />
                  {{ savingProfile ? '保存中…' : '保存' }}
                </button>
              </div>
            </div>
          </div>

          <!-- 修改密码 -->
          <div class="rounded-lg border border-[#E9ECEF] bg-white p-5 shadow-sm">
            <div class="mb-4 flex items-center gap-2 text-sm font-medium text-ink-900">
              <ShieldCheck :size="16" class="text-brand" /> 修改密码
            </div>
            <div class="space-y-4">
              <div>
                <label class="mb-1 block text-sm text-ink-400">原密码</label>
                <div class="relative">
                  <Lock :size="14" class="absolute left-3 top-1/2 -translate-y-1/2 text-ink-secondary" />
                  <input
                    v-model="pwdForm.oldPassword"
                    type="password"
                    class="w-full rounded-md border border-[#E5E6EB] py-2 pl-9 pr-3 text-sm outline-none focus:border-brand"
                    placeholder="请输入原密码"
                    autocomplete="current-password"
                  />
                </div>
              </div>
              <div>
                <label class="mb-1 block text-sm text-ink-400">新密码</label>
                <div class="relative">
                  <Lock :size="14" class="absolute left-3 top-1/2 -translate-y-1/2 text-ink-secondary" />
                  <input
                    v-model="pwdForm.newPassword"
                    type="password"
                    class="w-full rounded-md border border-[#E5E6EB] py-2 pl-9 pr-3 text-sm outline-none focus:border-brand"
                    placeholder="至少 6 位"
                    autocomplete="new-password"
                  />
                </div>
              </div>
              <div>
                <label class="mb-1 block text-sm text-ink-400">确认新密码</label>
                <div class="relative">
                  <Lock :size="14" class="absolute left-3 top-1/2 -translate-y-1/2 text-ink-secondary" />
                  <input
                    v-model="pwdForm.confirmPassword"
                    type="password"
                    class="w-full rounded-md border border-[#E5E6EB] py-2 pl-9 pr-3 text-sm outline-none focus:border-brand"
                    placeholder="再次输入新密码"
                    autocomplete="new-password"
                  />
                </div>
              </div>
              <div class="flex justify-end">
                <button
                  class="inline-flex items-center gap-1.5 rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark disabled:opacity-50"
                  :disabled="savingPwd"
                  @click="onChangePassword"
                >
                  <Lock :size="14" />
                  {{ savingPwd ? '提交中…' : '修改密码' }}
                </button>
              </div>
            </div>
          </div>

          <!-- 账号信息（只读） -->
          <div class="rounded-lg border border-[#E9ECEF] bg-white p-5 shadow-sm lg:col-span-2">
            <div class="mb-4 flex items-center gap-2 text-sm font-medium text-ink-900">
              <BadgeCheck :size="16" class="text-brand" /> 账号信息
            </div>
            <div class="grid grid-cols-1 gap-4 sm:grid-cols-3">
              <div>
                <div class="text-xs text-ink-400">用户名</div>
                <div class="mt-1 text-sm text-ink-900">{{ profile.username }}</div>
              </div>
              <div>
                <div class="text-xs text-ink-400">账号状态</div>
                <div class="mt-1 text-sm">
                  <span
                    v-if="profile.enabled"
                    class="inline-flex items-center gap-1 rounded-full bg-success-light px-2 py-0.5 text-xs font-medium text-success"
                  >正常</span>
                  <span
                    v-else
                    class="inline-flex items-center gap-1 rounded-full bg-surface-muted px-2 py-0.5 text-xs font-medium text-ink-secondary"
                  >已禁用</span>
                </div>
              </div>
              <div>
                <div class="flex items-center gap-1 text-xs text-ink-400">
                  <Clock :size="12" /> 注册时间
                </div>
                <div class="mt-1 text-sm text-ink-900">{{ fmtTime(profile.createdAt) }}</div>
              </div>
            </div>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>