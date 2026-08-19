<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { updateProject, listProjectMembers } from '@/api'
import { useProjectStore } from '@/stores/project'
import type { ProjectMember } from '@/api/types'
import { Settings, Users, Plus } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import ProjectMemberDialog from '@/components/ProjectMemberDialog.vue'

const route = useRoute()
const projectId = route.params.projectId as string
const projectStore = useProjectStore()

const form = ref({ name: '', description: '', status: 'ACTIVE' })
const saving = ref(false)
const memberDialogVisible = ref(false)
const members = ref<ProjectMember[]>([])

const STATUS_OPTIONS = [
  { value: 'ACTIVE', label: '进行中' },
  { value: 'ARCHIVED', label: '已归档' },
  { value: 'CLOSED', label: '已关闭' }
]

async function loadMembers() {
  try {
    members.value = await listProjectMembers(projectId)
  } catch {
    members.value = []
  }
}

async function onSave() {
  if (!form.value.name.trim()) {
    ElMessage.warning('项目名称不能为空')
    return
  }
  saving.value = true
  try {
    await updateProject(projectId, {
      name: form.value.name.trim(),
      description: form.value.description,
      status: form.value.status
    })
    ElMessage.success('设置已保存')
    await projectStore.loadCurrent(projectId)
  } catch (e: any) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(async () => {
  await projectStore.loadCurrent(projectId)
  if (projectStore.current) {
    form.value = {
      name: projectStore.current.name,
      description: projectStore.current.description,
      status: projectStore.current.status
    }
  }
  await loadMembers()
})
</script>

<template>
  <div class="h-full overflow-auto p-6">
    <div class="mb-4 flex items-center gap-2">
      <Settings :size="20" class="text-brand" />
      <h1 class="text-lg font-semibold text-ink-900">设置</h1>
    </div>

    <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <!-- 基本设置 -->
      <div class="rounded-lg border border-[#E9ECEF] bg-white p-5 shadow-sm">
        <div class="mb-4 text-sm font-medium text-ink-900">基本信息</div>
        <div class="space-y-4">
          <div>
            <label class="mb-1 block text-sm text-ink-400">项目名称</label>
            <input v-model="form.name" class="w-full rounded-md border border-[#E5E6EB] px-3 py-2 text-sm outline-none focus:border-brand" placeholder="项目名称" />
          </div>
          <div>
            <label class="mb-1 block text-sm text-ink-400">项目描述</label>
            <textarea v-model="form.description" rows="3" class="w-full resize-none rounded-md border border-[#E5E6EB] px-3 py-2 text-sm outline-none focus:border-brand" placeholder="项目描述"></textarea>
          </div>
          <div>
            <label class="mb-1 block text-sm text-ink-400">项目状态</label>
            <select v-model="form.status" class="w-full rounded-md border border-[#E5E6EB] px-3 py-2 text-sm outline-none focus:border-brand">
              <option v-for="s in STATUS_OPTIONS" :key="s.value" :value="s.value">{{ s.label }}</option>
            </select>
          </div>
          <div>
            <label class="mb-1 block text-sm text-ink-400">项目 Key</label>
            <input :value="projectStore.current?.projectKey || ''" disabled class="w-full cursor-not-allowed rounded-md border border-[#E5E6EB] bg-surface-muted px-3 py-2 text-sm text-ink-400" />
          </div>
          <div class="flex justify-end">
            <button class="rounded-md bg-brand px-4 py-2 text-sm font-medium text-white transition hover:bg-brand-dark disabled:opacity-50" :disabled="saving" @click="onSave">
              {{ saving ? '保存中…' : '保存' }}
            </button>
          </div>
        </div>
      </div>

      <!-- 成员管理 -->
      <div class="rounded-lg border border-[#E9ECEF] bg-white p-5 shadow-sm">
        <div class="mb-4 flex items-center justify-between">
          <div class="flex items-center gap-2 text-sm font-medium text-ink-900">
            <Users :size="16" /> 成员管理
          </div>
          <button class="flex items-center gap-1 text-xs text-brand transition hover:opacity-80" @click="memberDialogVisible = true">
            <Plus :size="12" /> 管理成员
          </button>
        </div>
        <div class="space-y-2">
          <div v-for="m in members" :key="m.userId" class="flex items-center gap-3 rounded-md border border-[#E9ECEF] px-3 py-2">
            <div class="flex h-8 w-8 items-center justify-center rounded-full bg-brand text-sm font-medium text-white">
              {{ m.displayName?.charAt(0) || 'U' }}
            </div>
            <div class="flex-1">
              <div class="text-sm text-ink-900">{{ m.displayName }}</div>
              <div class="text-xs text-ink-400">{{ m.username }}</div>
            </div>
            <div class="flex gap-1">
              <span v-for="r in m.roles" :key="r" class="rounded bg-surface-muted px-2 py-0.5 text-xs text-ink-600">{{ r }}</span>
            </div>
          </div>
          <div v-if="!members.length" class="py-6 text-center text-sm text-ink-400">暂无成员</div>
        </div>
      </div>
    </div>

    <ProjectMemberDialog v-model:visible="memberDialogVisible" :project-id="projectId" />
  </div>
</template>