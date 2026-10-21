<script setup lang="ts">
import type { Project } from '@/api/types'
import { useProjectCategoryStore } from '@/stores/projectCategory'
import { Users, Star, UserPlus, Lock, ListTodo, Lightbulb, Trash2, Archive, ArchiveRestore } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { errorMessage } from '@/utils/error'

const props = defineProps<{
  project: Project
}>()

const emit = defineEmits<{
  (e: 'openBoard', p: Project): void
  (e: 'openMembers', p: Project): void
  /** 卡片上的「添加成员」：直接拉起检索弹窗，不必先进成员列表 */
  (e: 'addMember', p: Project): void
  (e: 'openEdit', p: Project): void
  (e: 'delete', p: Project): void
}>()

const store = useProjectCategoryStore()
const router = useRouter()

async function onFavorite(p: Project) {
  const wasFavorited = p.favorited || false
  try {
    await store.toggleFavorite(p.id)
    ElMessage.success(wasFavorited ? '已取消收藏' : '已收藏')
  } catch (e: unknown) {
    ElMessage.error(errorMessage(e, '操作失败'))
  }
}

async function onArchive(p: Project) {
  const isArchived = p.status === 'ARCHIVED'
  try {
    await store.toggleArchive(p.id)
    ElMessage.success(isArchived ? '已取消归档' : '已归档')
  } catch (e: unknown) {
    ElMessage.error(errorMessage(e, '操作失败'))
  }
}

function goTasks() {
  router.push(`/projects/${props.project.id}/tasks`)
}

function goRequirements() {
  router.push(`/projects/${props.project.id}/requirements`)
}
</script>

<template>
  <div
    class="card-interactive group relative flex h-[160px] cursor-pointer flex-col overflow-hidden rounded-xl transition-all duration-[400ms] ease-spring hover:shadow-card-hover-lg hover:ring-primary-200"
    @click="emit('openBoard', project)"
  >
    <!-- 顶部渐变封面区：默认 88px，悬停收缩到 52px -->
    <div class="h-[88px] shrink-0 bg-gradient-to-br from-blue-300 via-blue-100 to-blue-50 transition-all duration-[400ms] ease-spring group-hover:h-[52px]" />

    <!-- 右上角收藏按钮 -->
    <button
      class="absolute right-3 top-3 z-20 flex h-8 w-8 items-center justify-center rounded-full bg-white/90 shadow-sm backdrop-blur-sm transition-all duration-[400ms] ease-spring hover:scale-110 hover:bg-white"
      :class="project.favorited ? 'text-yellow-500' : 'text-slate-400 hover:text-yellow-500'"
      title="收藏"
      @click.stop="onFavorite(project)"
    >
      <Star :size="16" :fill="project.favorited ? 'currentColor' : 'none'" />
    </button>

    <!-- 白色内容区：默认 72px（仅名称），悬停展开到 108px 并显示统计 + 操作 -->
    <div
      class="flex h-[72px] shrink-0 flex-col overflow-hidden px-5 pt-3 transition-all duration-[400ms] ease-spring group-hover:h-[108px] group-hover:pt-4"
    >
      <!-- 项目名称 -->
      <div class="flex items-center gap-2">
        <Lock :size="15" class="shrink-0 text-slate-400 transition-colors duration-[400ms] ease-spring group-hover:text-primary-400" />
        <span class="truncate text-sm font-semibold text-slate-800 transition-colors duration-[400ms] ease-spring group-hover:text-primary-600">{{ project.name }}</span>
      </div>

      <!-- 展开内容：统计数据 + 操作栏 -->
      <div class="mt-3 flex flex-1 flex-col justify-between">
        <div class="flex items-center gap-4">
          <button
            class="flex items-center gap-1 text-xs text-slate-600 transition hover:text-primary"
            title="成员"
            @click.stop="emit('openMembers', project)"
          >
            <Users :size="14" class="text-emerald-500" />
            <span class="font-medium">{{ project.stats?.memberCount ?? 0 }}</span>
          </button>
          <button
            class="flex items-center gap-1 text-xs text-slate-600 transition hover:text-primary"
            title="任务"
            @click.stop="goTasks"
          >
            <ListTodo :size="14" class="text-rose-500" />
            <span class="font-medium">{{ project.stats?.issueCount ?? 0 }}</span>
          </button>
          <button
            class="flex items-center gap-1 text-xs text-slate-600 transition hover:text-primary"
            title="需求"
            @click.stop="goRequirements"
          >
            <Lightbulb :size="14" class="text-indigo-500" />
            <span class="font-medium">0</span>
          </button>
        </div>

        <div class="flex items-center justify-between border-t border-slate-100 pt-2.5">
          <button
            class="flex items-center gap-1.5 rounded-md px-2 py-1 text-xs text-slate-500 transition hover:bg-primary-50 hover:text-primary"
            title="添加成员"
            @click.stop="emit('addMember', project)"
          >
            <UserPlus :size="15" />
            <span>添加成员</span>
          </button>
          <div class="flex items-center gap-1">
            <button
              class="flex h-7 w-7 items-center justify-center rounded-md text-slate-400 transition hover:bg-primary-50 hover:text-primary"
              :title="project.status === 'ARCHIVED' ? '取消归档' : '归档'"
              @click.stop="onArchive(project)"
            >
              <ArchiveRestore v-if="project.status === 'ARCHIVED'" :size="15" />
              <Archive v-else :size="15" />
            </button>
            <button
              class="flex h-7 w-7 items-center justify-center rounded-md text-slate-400 transition hover:bg-rose-50 hover:text-rose-500"
              title="删除项目"
              @click.stop="emit('delete', project)"
            >
              <Trash2 :size="15" />
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
