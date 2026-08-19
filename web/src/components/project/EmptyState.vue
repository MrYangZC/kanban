<script setup lang="ts">
import type { ProjectCategoryKey } from '@/api/types'
import { FolderOpen } from 'lucide-vue-next'

const props = defineProps<{
  category: ProjectCategoryKey
}>()

const emptyTexts: Record<ProjectCategoryKey, string> = {
  all: '暂无项目，点击右上角「新建项目」开始',
  managed: '暂无管理的项目',
  participated: '暂无参与的项目',
  favorited: '暂无收藏项目，点击星标收藏你关注的项目',
  archived: '暂无已归档项目'
}
</script>

<template>
  <div class="flex flex-col items-center justify-center rounded-lg border border-dashed border-line p-12 text-center">
    <FolderOpen :size="40" class="mb-3 text-ink-secondary opacity-40" />
    <p class="text-sm text-ink-secondary">{{ emptyTexts[props.category] }}</p>
  </div>
</template>