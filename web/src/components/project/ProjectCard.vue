<script setup lang="ts">
import type { Project } from '@/api/types'
import { useProjectCategoryStore } from '@/stores/projectCategory'
import { FolderKanban, Users, Star, Pencil, FileText, Paperclip, Calendar, ArrowRight } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { computed } from 'vue'

const props = defineProps<{
  project: Project
}>()

const emit = defineEmits<{
  (e: 'openBoard', p: Project): void
  (e: 'openMembers', p: Project): void
  (e: 'openEdit', p: Project): void
}>()

const store = useProjectCategoryStore()

const statusConfig = computed(() => {
  const s = (props.project.status || '').toUpperCase()
  if (s === 'ARCHIVED') return { label: '已归档', cls: 'bg-surface-light text-ink-secondary', dot: 'bg-ink-secondary' }
  if (s === 'COMPLETED' || s === 'DONE') return { label: '已完成', cls: 'bg-success-light text-success', dot: 'bg-success' }
  if (s === 'IN_PROGRESS' || s === 'ACTIVE' || s === 'ONGOING') return { label: '进行中', cls: 'bg-primary-50 text-primary-600', dot: 'bg-primary-500' }
  if (s === 'NOT_STARTED' || s === 'PENDING' || s === 'TODO') return { label: '未开始', cls: 'bg-warning-light text-warning', dot: 'bg-warning' }
  return { label: '进行中', cls: 'bg-primary-50 text-primary-600', dot: 'bg-primary-500' }
})

const formattedDate = computed(() => {
  if (!props.project.createdAt) return ''
  return props.project.createdAt.slice(0, 10)
})

async function onFavorite(p: Project) {
  const wasFavorited = p.favorited || false
  try {
    await store.toggleFavorite(p.id)
    ElMessage.success(wasFavorited ? '已取消收藏' : '已收藏')
  } catch (e: any) {
    ElMessage.error(e?.message || '操作失败')
  }
}
</script>

<template>
  <div class="group card-interactive relative flex flex-col p-5">
    <div class="absolute right-3 top-3 flex items-center gap-1 opacity-0 transition-all duration-200 group-hover:opacity-100">
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
        @click.stop="emit('openEdit', project)"
      >
        <Pencil :size="15" />
      </button>
    </div>

    <div class="mb-4 flex items-start justify-between">
      <div class="flex h-11 w-11 items-center justify-center rounded-xl bg-gradient-to-br from-primary-50 to-primary-100 text-primary-500 shadow-sm transition-all duration-300 group-hover:from-primary-500 group-hover:to-primary-600 group-hover:text-white">
        <FolderKanban :size="22" />
      </div>
      <span
        class="inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium"
        :class="statusConfig.cls"
      >
        <span class="h-1.5 w-1.5 rounded-full" :class="statusConfig.dot"></span>
        {{ statusConfig.label }}
      </span>
    </div>

    <div class="mb-1 text-xs font-semibold uppercase tracking-wider text-primary-500">{{ project.projectKey }}</div>
    <div
      class="cursor-pointer text-base font-semibold leading-snug text-ink transition-colors duration-200 hover:text-primary-600"
      @click="emit('openBoard', project)"
    >{{ project.name }}</div>
    <div class="mt-2 line-clamp-2 text-sm leading-relaxed text-ink-secondary">{{ project.description || '暂无描述' }}</div>

    <div class="mt-4 flex items-center gap-3 text-xs text-ink-secondary">
      <span class="flex items-center gap-1" :title="`${project.stats?.memberCount || 0} 名成员`">
        <Users :size="14" class="text-ink-secondary" />
        <span class="font-medium">{{ project.stats?.memberCount || 0 }}</span>
      </span>
      <span class="h-3 w-px bg-line-light"></span>
      <span class="flex items-center gap-1" :title="`${project.stats?.issueCount || 0} 个任务`">
        <FileText :size="14" class="text-ink-secondary" />
        <span class="font-medium">{{ project.stats?.issueCount || 0 }}</span>
      </span>
      <span class="h-3 w-px bg-line-light"></span>
      <span class="flex items-center gap-1" :title="`${project.stats?.attachmentCount || 0} 个附件`">
        <Paperclip :size="14" class="text-ink-secondary" />
        <span class="font-medium">{{ project.stats?.attachmentCount || 0 }}</span>
      </span>
    </div>

    <div class="mt-4 flex items-center justify-between border-t border-line-lighter pt-3">
      <div class="flex items-center gap-1.5 text-xs text-ink-secondary">
        <Calendar :size="13" />
        <span>{{ formattedDate || '—' }}</span>
      </div>
      <div class="flex items-center gap-2">
        <button
          class="rounded-md px-2.5 py-1 text-xs font-medium text-ink-secondary transition-all duration-200 hover:bg-surface-hover hover:text-ink"
          @click.stop="emit('openMembers', project)"
        >管理成员</button>
        <button
          class="flex items-center gap-1 rounded-md bg-primary-50 px-2.5 py-1 text-xs font-medium text-primary-600 transition-all duration-200 hover:bg-primary-500 hover:text-white"
          @click.stop="emit('openBoard', project)"
        >
          进入
          <ArrowRight :size="12" />
        </button>
      </div>
    </div>
  </div>
</template>
