<script setup lang="ts">
import type { Project } from '@/api/types'
import { useProjectCategoryStore } from '@/stores/projectCategory'
import {
  Star, Pencil, Users, UserPlus, ListTodo, FolderKanban, ArrowRight, Trash2, Archive, ArchiveRestore
} from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { reportError } from '@/utils/error'
import { projectStatusBadge } from '@/constants/issueDict'

const props = defineProps<{
  project: Project
}>()

const emit = defineEmits<{
  (e: 'openBoard', p: Project): void
  (e: 'openMembers', p: Project): void
  (e: 'addMember', p: Project): void
  (e: 'openEdit', p: Project): void
  (e: 'delete', p: Project): void
}>()

const store = useProjectCategoryStore()
const router = useRouter()

const statusConfig = computed(() => projectStatusBadge(props.project.status))

const formattedDate = computed(() => {
  if (!props.project.createdAt) return '—'
  return props.project.createdAt.slice(0, 10)
})

async function onFavorite(p: Project) {
  const wasFavorited = p.favorited || false
  try {
    await store.toggleFavorite(p.id)
    ElMessage.success(wasFavorited ? '已取消收藏' : '已收藏')
  } catch (e: unknown) {
    reportError(e, '操作失败')
  }
}

/** 与卡片视图保持一致：点「任务」直接进该项目的任务页 */
function goTasks() {
  router.push(`/projects/${props.project.id}/tasks`)
}

/** 与卡片视图保持一致：归档 / 取消归档 */
async function onArchive(p: Project) {
  const isArchived = p.status === 'ARCHIVED'
  try {
    await store.toggleArchive(p.id)
    ElMessage.success(isArchived ? '已取消归档' : '已归档')
  } catch (e: unknown) {
    reportError(e, '操作失败')
  }
}
</script>

<template>
  <tr class="table-row group">
    <td class="px-5 py-3.5">
      <div class="flex items-center gap-2">
        <div class="flex h-7 w-7 items-center justify-center rounded-md bg-primary-50 text-primary-500 transition-all duration-200 group-hover:bg-primary-500 group-hover:text-white">
          <FolderKanban :size="14" />
        </div>
        <span class="text-xs font-semibold uppercase tracking-wider text-primary-500">{{ project.projectKey }}</span>
      </div>
    </td>
    <td class="px-5 py-3.5">
      <span
        class="cursor-pointer font-medium text-ink transition-colors duration-200 hover:text-primary-600"
        @click="emit('openBoard', project)"
      >{{ project.name }}</span>
    </td>
    <td class="px-5 py-3.5">
      <span
        class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium"
        :class="statusConfig.cls"
      >
        <span class="h-1.5 w-1.5 rounded-full" :class="statusConfig.dot"></span>
        {{ statusConfig.label }}
      </span>
    </td>
    <td class="px-5 py-3.5 text-sm text-ink-secondary">
      <span class="line-clamp-1">{{ project.description || '暂无描述' }}</span>
    </td>
    <td class="px-5 py-3.5 text-center">
      <button
        class="inline-flex items-center gap-1 rounded-md px-2 py-1 text-sm text-ink-secondary transition-all duration-200 hover:bg-surface-hover hover:text-primary-600"
        title="成员"
        @click.stop="emit('openMembers', project)"
      >
        <Users :size="14" class="text-ink-secondary" />
        <span class="font-medium">{{ project.stats?.memberCount || 0 }}</span>
      </button>
    </td>
    <td class="px-5 py-3.5 text-center">
      <button
        class="inline-flex items-center gap-1 rounded-md px-2 py-1 text-sm text-ink-secondary transition-all duration-200 hover:bg-surface-hover hover:text-primary-600"
        title="任务"
        @click.stop="goTasks"
      >
        <ListTodo :size="14" class="text-ink-secondary" />
        <span class="font-medium">{{ project.stats?.issueCount || 0 }}</span>
      </button>
    </td>
    <td class="px-5 py-3.5 text-sm text-ink-secondary">
      {{ formattedDate }}
    </td>
    <td class="px-5 py-3.5">
      <div class="flex items-center justify-end gap-1">
        <button
          class="rounded-md p-1.5 transition-all duration-200 hover:bg-surface-hover"
          :class="project.favorited ? 'text-warning' : 'text-ink-secondary hover:text-warning'"
          title="收藏"
          @click.stop="onFavorite(project)"
        >
          <Star :size="15" :fill="project.favorited ? 'currentColor' : 'none'" />
        </button>
        <button
          class="rounded-md p-1.5 text-ink-secondary transition-all duration-200 hover:bg-surface-hover hover:text-primary"
          :class="{ 'cursor-not-allowed opacity-50': !project.canEdit }"
          title="编辑项目"
          :disabled="!project.canEdit"
          @click.stop="emit('openEdit', project)"
        >
          <Pencil :size="15" />
        </button>
        <button
          class="rounded-md p-1.5 text-ink-secondary transition-all duration-200 hover:bg-surface-hover hover:text-primary"
          :class="{ 'cursor-not-allowed opacity-50': !project.canManage }"
          :title="project.canManage ? '添加成员' : '仅项目所有者/管理员/项目经理可添加成员'"
          :disabled="!project.canManage"
          @click.stop="emit('addMember', project)"
        >
          <UserPlus :size="15" />
        </button>
        <button
          class="rounded-md p-1.5 text-ink-secondary transition-all duration-200 hover:bg-surface-hover hover:text-primary"
          :class="{ 'cursor-not-allowed opacity-50': !project.canManage }"
          :title="project.status === 'ARCHIVED' ? '取消归档' : '归档'"
          :disabled="!project.canManage"
          @click.stop="onArchive(project)"
        >
          <ArchiveRestore v-if="project.status === 'ARCHIVED'" :size="15" />
          <Archive v-else :size="15" />
        </button>
        <button
          class="rounded-md p-1.5 text-ink-secondary transition-all duration-200 hover:bg-rose-50 hover:text-rose-500"
          :class="{ 'cursor-not-allowed opacity-50': !project.canManage }"
          :title="project.canManage ? '删除项目' : '仅项目所有者/管理员/项目经理可删除项目'"
          :disabled="!project.canManage"
          @click.stop="emit('delete', project)"
        >
          <Trash2 :size="15" />
        </button>
        <button
          class="ml-1 flex items-center gap-1 rounded-md px-2 py-1 text-xs font-medium text-primary-600 transition-all duration-200 hover:bg-primary-500 hover:text-white"
          @click.stop="emit('openBoard', project)"
        >
          进入
          <ArrowRight :size="12" />
        </button>
      </div>
    </td>
  </tr>
</template>
