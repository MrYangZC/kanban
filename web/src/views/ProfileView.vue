<script setup lang="ts">
/**
 * 个人中心（方案 B：左侧只读名片 + 右侧表单）。
 *
 * <p>职责切得很干净：
 * <ul>
 *   <li>左栏是「名片」——头像 + 身份 + 只读资料，唯一可交互的是头像本身（点击即换，选完立刻生效）；</li>
 *   <li>右栏是「表单」——基本信息（显示名/邮箱）与修改密码，各自一个保存按钮；</li>
 *   <li>头像不再进表单：换完就提交，避免出现「选了一张图但忘了保存」这种半生效状态。</li>
 * </ul>
 */
import { computed, onMounted, ref, watch } from 'vue'
import { getMyProfile, updateMyProfile, changeMyPassword, uploadImage } from '@/api'
import { useUserStore } from '@/stores/user'
import type { ProfileVO } from '@/api/types'
import { ElMessage } from 'element-plus'
import { reportError } from '@/utils/error'
import { AVATAR_ACCEPT, avatarColor, validateAvatarFile } from '@/utils/avatar'
import { PASSWORD_MIN_LENGTH, passwordError } from '@/utils/formRules'
import {
  AtSign, CalendarClock, Camera, Loader2, Lock, Save, ShieldCheck, User, UserRound
} from 'lucide-vue-next'

const userStore = useUserStore()

const profile = ref<ProfileVO | null>(null)
const loading = ref(true)

/** 基本信息表单：只保留显示名与邮箱（头像走左栏点击，不进表单） */
const form = ref({ displayName: '', email: '' })
const savingProfile = ref(false)

const uploadingAvatar = ref(false)
const avatarInput = ref<HTMLInputElement | null>(null)

const pwdForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const savingPwd = ref(false)

const avatarUrl = computed(() => profile.value?.avatarUrl || '')
/** 图片加载失败（历史上传文件被清理、静态资源 401 等）时退回文字头像，别把 alt 文字甩在圆里 */
const avatarBroken = ref(false)
watch(avatarUrl, () => { avatarBroken.value = false })
const initial = computed(() => profile.value?.displayName?.charAt(0) || profile.value?.username?.charAt(0) || 'U')
const initialBg = computed(() => avatarColor(profile.value?.displayName || profile.value?.username))

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
      email: profile.value.email || ''
    }
  } catch (e: unknown) {
    reportError(e, '加载个人信息失败')
  } finally {
    loading.value = false
  }
}

/** 右上角头像等全局位置要立刻跟着变，否则要等下次登录 */
function syncLoginStore(p: ProfileVO) {
  if (userStore.user) {
    userStore.setLogin({
      ...userStore.user,
      displayName: p.displayName,
      avatarUrl: p.avatarUrl
    })
  }
}

function pickAvatar() {
  if (uploadingAvatar.value) return
  avatarInput.value?.click()
}

/**
 * 换头像：上传成功即写库，不需要再点「保存」。
 * 只提交 avatarUrl——显示名/邮箱用服务端当前值，避免把用户「还没保存的输入」顺带写进去。
 */
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
    const uploaded = await uploadImage(file)
    const updated = await updateMyProfile({ avatarUrl: uploaded.url })
    profile.value = updated
    syncLoginStore(updated)
    ElMessage.success('头像已更新')
  } catch (err: unknown) {
    reportError(err, '头像更新失败')
  } finally {
    uploadingAvatar.value = false
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
      email: form.value.email.trim()
    })
    profile.value = updated
    form.value = { displayName: updated.displayName || '', email: updated.email || '' }
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
    <div class="mb-6">
      <h1 class="page-title">个人中心</h1>
      <p class="page-subtitle">管理个人资料与登录密码</p>
    </div>

    <div v-if="loading" class="card flex items-center gap-3 p-12 text-sm text-ink-secondary">
      <div class="h-5 w-5 animate-spin rounded-full border-2 border-primary-200 border-t-primary-500"></div>
      <span>加载中…</span>
    </div>

    <!-- 左 32% 名片 / 右 68% 表单 -->
    <div v-else-if="profile" class="grid grid-cols-1 items-start gap-5 lg:grid-cols-[32%_minmax(0,1fr)]">
      <aside class="card flex flex-col items-center px-7 py-8">
        <!-- 头像：点击更换，悬停显示相机提示；选完即保存 -->
        <button
          type="button"
          class="group relative h-40 w-40 shrink-0 rounded-full outline-none ring-offset-2 transition focus-visible:ring-2 focus-visible:ring-primary-300 disabled:cursor-not-allowed"
          :disabled="uploadingAvatar"
          :title="uploadingAvatar ? '头像上传中…' : '点击更换头像'"
          @click="pickAvatar"
        >
          <img
            v-if="avatarUrl && !avatarBroken"
            :src="avatarUrl"
            :alt="profile.displayName"
            class="h-full w-full rounded-full object-cover shadow-card ring-4 ring-white"
            @error="avatarBroken = true"
          />
          <span
            v-else
            class="flex h-full w-full items-center justify-center rounded-full text-4xl font-medium text-white shadow-card ring-4 ring-white"
            :style="{ backgroundColor: initialBg }"
          >{{ initial }}</span>

          <!-- 悬停遮罩：提示可点 -->
          <span
            class="pointer-events-none absolute inset-0 flex flex-col items-center justify-center gap-1.5 rounded-full bg-ink/45 text-white opacity-0 transition-opacity duration-200 group-hover:opacity-100"
          >
            <Camera :size="22" />
            <span class="text-[11px]">点击更换头像</span>
          </span>
          <!-- 上传中：遮住遮罩，给出进度反馈 -->
          <span
            v-if="uploadingAvatar"
            class="absolute inset-0 flex flex-col items-center justify-center gap-1.5 rounded-full bg-ink/55 text-white"
          >
            <Loader2 :size="22" class="animate-spin" />
            <span class="text-[11px]">上传中…</span>
          </span>
          <!-- 常驻相机角标：触屏没有 hover，也能看出这里能点 -->
          <span
            class="pointer-events-none absolute bottom-1 right-1 flex h-8 w-8 items-center justify-center rounded-full border-2 border-white bg-surface-light text-ink-secondary shadow-sm transition group-hover:opacity-0"
          >
            <Camera :size="15" />
          </span>
        </button>
        <input ref="avatarInput" type="file" :accept="AVATAR_ACCEPT" class="hidden" @change="onAvatarChange" />

        <h2 class="mt-6 max-w-full truncate text-xl font-semibold text-ink" :title="profile.displayName">
          {{ profile.displayName }}
        </h2>
        <p class="mt-1 text-sm text-ink-secondary">@{{ profile.username }}</p>
        <span
          class="tag-status mt-3"
          :class="profile.enabled ? 'bg-success-light text-success' : 'bg-surface-hover text-ink-secondary'"
        >
          <span class="h-1.5 w-1.5 rounded-full" :class="profile.enabled ? 'bg-success' : 'bg-ink-placeholder'" />
          {{ profile.enabled ? '正常' : '已禁用' }}
        </span>

        <!-- 只读资料 -->
        <dl class="mt-8 w-full space-y-4 border-t border-line-lighter pt-6">
          <div class="flex items-start gap-3">
            <User :size="16" class="mt-0.5 shrink-0 text-ink-placeholder" />
            <div class="min-w-0 flex-1">
              <dt class="text-xs text-ink-secondary">用户名</dt>
              <dd class="mt-0.5 truncate text-sm font-medium text-ink" :title="profile.username">{{ profile.username }}</dd>
            </div>
          </div>
          <div class="flex items-start gap-3">
            <AtSign :size="16" class="mt-0.5 shrink-0 text-ink-placeholder" />
            <div class="min-w-0 flex-1">
              <dt class="text-xs text-ink-secondary">邮箱</dt>
              <dd class="mt-0.5 truncate text-sm font-medium text-ink" :title="profile.email || ''">{{ profile.email || '—' }}</dd>
            </div>
          </div>
          <div class="flex items-start gap-3">
            <CalendarClock :size="16" class="mt-0.5 shrink-0 text-ink-placeholder" />
            <div class="min-w-0 flex-1">
              <dt class="text-xs text-ink-secondary">注册时间</dt>
              <dd class="mt-0.5 text-sm font-medium text-ink">{{ fmtTime(profile.createdAt) }}</dd>
            </div>
          </div>
        </dl>
      </aside>

      <div class="space-y-5">
        <!-- 基本信息：只留显示名 + 邮箱 -->
        <section class="card p-6">
          <header class="mb-6 flex items-center gap-2.5">
            <span class="flex h-8 w-8 items-center justify-center rounded-lg bg-primary-50 text-primary-500">
              <UserRound :size="16" />
            </span>
            <div>
              <h2 class="text-sm font-semibold text-ink">基本信息</h2>
              <p class="mt-0.5 text-xs text-ink-secondary">用于列表、通知与 @提及中展示</p>
            </div>
          </header>

          <div class="grid gap-5 md:grid-cols-2">
            <div>
              <label class="label-base">显示名 <span class="text-danger">*</span></label>
              <input v-model="form.displayName" class="input-base" maxlength="100" placeholder="请输入显示名" />
            </div>
            <div>
              <label class="label-base">邮箱</label>
              <input v-model="form.email" type="email" class="input-base" maxlength="200" placeholder="name@example.com" />
            </div>
          </div>

          <div class="mt-6 flex justify-end">
            <button class="btn-primary" :disabled="savingProfile" @click="onSaveProfile">
              <Save :size="15" />
              <span>{{ savingProfile ? '保存中…' : '保存' }}</span>
            </button>
          </div>
        </section>

        <!-- 修改密码：结构与校验保持原样 -->
        <section class="card p-6">
          <header class="mb-6 flex items-center gap-2.5">
            <span class="flex h-8 w-8 items-center justify-center rounded-lg bg-primary-50 text-primary-500">
              <ShieldCheck :size="16" />
            </span>
            <div>
              <h2 class="text-sm font-semibold text-ink">修改密码</h2>
              <p class="mt-0.5 text-xs text-ink-secondary">修改成功后其它设备需重新登录</p>
            </div>
          </header>

          <div class="max-w-md space-y-4">
            <div>
              <label class="label-base">原密码</label>
              <input
                v-model="pwdForm.oldPassword"
                type="password"
                class="input-base"
                placeholder="请输入原密码"
                autocomplete="current-password"
              />
            </div>
            <div>
              <label class="label-base">新密码</label>
              <input
                v-model="pwdForm.newPassword"
                type="password"
                class="input-base"
                :placeholder="`至少 ${PASSWORD_MIN_LENGTH} 位`"
                autocomplete="new-password"
              />
            </div>
            <div>
              <label class="label-base">确认新密码</label>
              <input
                v-model="pwdForm.confirmPassword"
                type="password"
                class="input-base"
                placeholder="再次输入新密码"
                autocomplete="new-password"
              />
            </div>
            <!-- 按钮与输入框同一右边界，避免在宽卡片里飘到最右侧 -->
            <div class="flex justify-end pt-2">
              <button class="btn-primary" :disabled="savingPwd" @click="onChangePassword">
                <Lock :size="15" />
                <span>{{ savingPwd ? '提交中…' : '修改密码' }}</span>
              </button>
            </div>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>
