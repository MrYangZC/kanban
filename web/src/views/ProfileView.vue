<script setup lang="ts">
import { PASSWORD_MIN_LENGTH, passwordError } from '@/utils/formRules'
import { ref, onMounted, computed } from 'vue'
import { getMyProfile, updateMyProfile, changeMyPassword, uploadImage } from '@/api'
import { useUserStore } from '@/stores/user'
import type { ProfileVO } from '@/api/types'
import { ElMessage } from 'element-plus'
import { reportError } from '@/utils/error'
import { AVATAR_ACCEPT, validateAvatarFile } from '@/utils/avatar'
import { User, Lock, Save, Camera, ShieldCheck, Clock, ImagePlus } from 'lucide-vue-next'

const userStore = useUserStore()

const profile = ref<ProfileVO | null>(null)
const loading = ref(true)

const form = ref({ displayName: '', email: '', avatarUrl: '' })
const savingProfile = ref(false)
const uploadingAvatar = ref(false)
const avatarInput = ref<HTMLInputElement | null>(null)

const pwdForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const savingPwd = ref(false)

// 预览跟随表单：选好图先看到效果，点「保存」才写库
const avatarDisplay = computed(() => form.value.avatarUrl || '')
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
  } catch (e: unknown) {
    reportError(e, '加载个人信息失败')
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
    // 只回填表单：点「保存」才落库，避免误选一张就永久生效
    form.value.avatarUrl = res.url
  } catch (e: unknown) {
    reportError(e, '头像上传失败')
  } finally {
    uploadingAvatar.value = false
  }
}

function onClearAvatar() {
  form.value.avatarUrl = ''
}

function syncLoginStore(p: ProfileVO) {
  if (userStore.user) {
    // 同步头像：否则右上角等全局头像位要等下次登录才换成新图
    userStore.setLogin({
      ...userStore.user,
      displayName: p.displayName,
      avatarUrl: p.avatarUrl
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
  } catch (e: unknown) {
    reportError(e, '保存失败')
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
  const pwdError = passwordError(userStore.user?.username || '', pwdForm.value.newPassword)
  if (pwdError) {
    ElMessage.warning(pwdError)
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
  } catch (e: unknown) {
    reportError(e, '修改密码失败')
  } finally {
    savingPwd.value = false
  }
}

onMounted(loadProfile)
</script>

<template>
  <div class="page-container">
    <!-- 标题区（与用户管理页一致） -->
    <div class="mb-6 flex items-center justify-between">
      <div>
        <h1 class="page-title">个人中心</h1>
        <p class="page-subtitle">管理个人信息、头像与登录密码</p>
      </div>
    </div>

    <!-- 加载中 -->
    <div v-if="loading" class="card flex items-center gap-3 p-12 text-sm text-ink-secondary">
      <div class="h-5 w-5 animate-spin rounded-full border-2 border-primary-200 border-t-primary-500"></div>
      <span>加载中…</span>
    </div>

    <div v-else-if="profile" class="grid grid-cols-1 gap-5 lg:grid-cols-3">
      <!-- 左栏：账号概览 -->
      <div class="card p-6">
        <div class="flex flex-col items-center text-center">
          <div class="relative">
            <div
              v-if="avatarDisplay"
              class="h-20 w-20 overflow-hidden rounded-full shadow-card"
            >
              <img :src="avatarDisplay" :alt="profile.displayName" class="h-full w-full object-cover" />
            </div>
            <div
              v-else
              class="flex h-20 w-20 items-center justify-center rounded-full bg-primary text-2xl font-medium text-white shadow-card"
            >
              {{ initial }}
            </div>
            <button
              class="absolute bottom-0 right-0 flex h-7 w-7 items-center justify-center rounded-full border-2 border-white bg-surface-light text-ink-secondary shadow-sm transition hover:text-primary"
              title="更换头像"
              :disabled="uploadingAvatar"
              @click="triggerAvatarUpload"
            >
              <Camera :size="14" />
            </button>
            <input
              ref="avatarInput"
              type="file"
              :accept="AVATAR_ACCEPT"
              class="hidden"
              @change="onAvatarChange"
            />
          </div>
          <div class="mt-4 text-base font-semibold text-ink">{{ profile.displayName }}</div>
          <div class="mt-0.5 text-sm text-ink-secondary">@{{ profile.username }}</div>
          <div class="mt-3">
            <span
              class="tag-status"
              :class="profile.enabled ? 'bg-success-light text-success' : 'bg-surface-hover text-ink-secondary'"
            >
              <span class="h-1.5 w-1.5 rounded-full" :class="profile.enabled ? 'bg-success' : 'bg-ink-placeholder'" />
              {{ profile.enabled ? '正常' : '已禁用' }}
            </span>
          </div>
        </div>

        <div class="mt-6 border-t border-line-lighter pt-4">
          <div class="space-y-3 text-sm">
            <div class="flex items-center justify-between">
              <span class="text-ink-secondary">用户名</span>
              <span class="font-medium text-ink">{{ profile.username }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="text-ink-secondary">邮箱</span>
              <span class="font-medium text-ink">{{ profile.email || '—' }}</span>
            </div>
            <div class="flex items-center justify-between">
              <span class="flex items-center gap-1 text-ink-secondary">
                <Clock :size="13" /> 注册时间
              </span>
              <span class="font-medium text-ink">{{ fmtTime(profile.createdAt) }}</span>
            </div>
          </div>
        </div>
      </div>

      <!-- 右栏：编辑表单 -->
      <div class="space-y-5 lg:col-span-2">
        <!-- 基本信息 -->
        <div class="card p-6">
          <div class="mb-5 flex items-center gap-2">
            <User :size="18" class="text-primary-500" />
            <h2 class="text-sm font-semibold text-ink">基本信息</h2>
          </div>
          <div class="space-y-4">
            <div>
              <label class="label-base">显示名</label>
              <input v-model="form.displayName" class="input-base" placeholder="显示名" />
            </div>
            <div>
              <label class="label-base">邮箱</label>
              <input v-model="form.email" type="email" class="input-base" placeholder="name@example.com" />
            </div>
            <div>
              <label class="label-base">头像</label>
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
                  v-if="avatarDisplay"
                  type="button"
                  class="rounded px-2.5 py-1.5 text-xs text-ink-secondary transition hover:bg-surface-hover"
                  @click="onClearAvatar"
                >
                  清除
                </button>
              </div>
              <p class="mt-1.5 text-xs text-ink-secondary">
                支持 jpg / png / gif / webp，不超过 5MB；选好后点下方「保存」生效
              </p>
            </div>
            <div class="flex justify-end">
              <button class="btn-primary" :disabled="savingProfile" @click="onSaveProfile">
                <Save :size="15" />
                <span>{{ savingProfile ? '保存中…' : '保存' }}</span>
              </button>
            </div>
          </div>
        </div>

        <!-- 修改密码 -->
        <div class="card p-6">
          <div class="mb-5 flex items-center gap-2">
            <ShieldCheck :size="18" class="text-primary-500" />
            <h2 class="text-sm font-semibold text-ink">修改密码</h2>
          </div>
          <div class="space-y-4">
            <div>
              <label class="label-base">原密码</label>
              <input v-model="pwdForm.oldPassword" type="password" class="input-base" placeholder="请输入原密码" autocomplete="current-password" />
            </div>
            <div>
              <label class="label-base">新密码</label>
              <input v-model="pwdForm.newPassword" type="password" class="input-base" :placeholder="`至少 ${PASSWORD_MIN_LENGTH} 位`" autocomplete="new-password" />
            </div>
            <div>
              <label class="label-base">确认新密码</label>
              <input v-model="pwdForm.confirmPassword" type="password" class="input-base" placeholder="再次输入新密码" autocomplete="new-password" />
            </div>
            <div class="flex justify-end">
              <button class="btn-primary" :disabled="savingPwd" @click="onChangePassword">
                <Lock :size="15" />
                <span>{{ savingPwd ? '提交中…' : '修改密码' }}</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>