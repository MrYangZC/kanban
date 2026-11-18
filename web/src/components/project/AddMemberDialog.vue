<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { addProjectMember, getMemberCandidates } from '@/api'
import type { MemberCandidate } from '@/api/types'
import { ElMessage } from 'element-plus'
import { reportError } from '@/utils/error'
import { Search, UserPlus, UserRound } from 'lucide-vue-next'
import UserAvatar from '@/components/common/UserAvatar.vue'

const props = defineProps<{ projectId: string }>()
const emit = defineEmits<{ (e: 'added'): void }>()
const visible = defineModel<boolean>('visible', { default: false })

/** 默认角色：先加进来再在成员列表里调，避免「选人 + 选角色 + 点添加」三步挤在一起 */
const DEFAULT_ROLES = ['MEMBER']

const keyword = ref('')
const candidates = ref<MemberCandidate[]>([])
const loading = ref(false)
const addingId = ref('')
let timer: ReturnType<typeof setTimeout> | undefined

async function load() {
  if (!props.projectId) return
  loading.value = true
  try {
    candidates.value = await getMemberCandidates(props.projectId, keyword.value.trim() || undefined)
  } catch (e: unknown) {
    candidates.value = []
    reportError(e, '加载用户失败')
  } finally {
    loading.value = false
  }
}

// 关键字变化后防抖检索，避免逐字请求
watch(keyword, () => {
  if (!visible.value) return
  if (timer) clearTimeout(timer)
  timer = setTimeout(load, 250)
})

// 组件卸载时取消挂起的防抖任务，避免卸载后仍发起请求写已销毁的响应式状态
onBeforeUnmount(() => {
  if (timer) clearTimeout(timer)
})

watch(visible, (v) => {
  if (!v) return
  keyword.value = ''
  candidates.value = []
  load()
})

async function onAdd(u: MemberCandidate) {
  addingId.value = u.id
  try {
    await addProjectMember(props.projectId, u.id, DEFAULT_ROLES)
    ElMessage.success(`已添加「${u.displayName || u.username}」`)
    // 加入成功即从候选里摘掉，避免再次点击报「已是项目成员」
    candidates.value = candidates.value.filter((c) => c.id !== u.id)
    emit('added')
  } catch (e: unknown) {
    reportError(e, '添加成员失败')
  } finally {
    addingId.value = ''
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="添加成员" width="520px" append-to-body :close-on-click-modal="false">
    <div class="space-y-3">
      <el-input
        v-model="keyword"
        :prefix-icon="Search"
        placeholder="搜索账号或姓名"
        clearable
      />

      <div v-loading="loading" class="min-h-[160px]">
        <ul v-if="candidates.length" class="max-h-[320px] space-y-1 overflow-y-auto pr-1">
          <li
            v-for="u in candidates"
            :key="u.id"
            class="flex items-center gap-3 rounded-md px-2 py-2 transition hover:bg-surface-muted"
          >
            <UserAvatar :name="u.displayName || u.username" :src="u.avatarUrl" :size="32" />
            <div class="min-w-0 flex-1">
              <div class="truncate text-sm text-ink-900">{{ u.displayName || u.username }}</div>
              <div class="truncate text-xs text-ink-400">@{{ u.username }}</div>
            </div>
            <button
              class="flex items-center gap-1 rounded-md border border-line-light px-2.5 py-1 text-xs text-ink-600 transition hover:border-brand hover:text-brand disabled:opacity-50"
              :disabled="!!addingId"
              @click="onAdd(u)"
            >
              <UserPlus :size="12" />{{ addingId === u.id ? '添加中…' : '添加' }}
            </button>
          </li>
        </ul>
        <div v-else-if="!loading" class="flex flex-col items-center justify-center gap-2 py-10 text-center">
          <UserRound :size="22" class="text-ink-300" />
          <div class="text-sm text-ink-400">
            {{ keyword.trim() ? '没有匹配的用户' : '所有用户都已加入本项目' }}
          </div>
          <div v-if="keyword.trim()" class="text-xs text-ink-300">换个账号或姓名再试试</div>
        </div>
      </div>

      <p class="text-xs text-ink-400">
        添加后默认为「参与人」，可在成员列表中调整角色。
      </p>
    </div>
  </el-dialog>
</template>
