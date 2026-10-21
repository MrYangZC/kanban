<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { BarChart3 } from 'lucide-vue-next'
import ProjectMetricsPanel from '@/components/ProjectMetricsPanel.vue'

const route = useRoute()
// 响应式：切换项目时 prop 随之变化，度量面板可感知并重新加载
const projectId = computed(() => route.params.projectId as string)
</script>

<template>
  <div class="h-full overflow-auto p-6">
    <div class="mb-4 flex items-center gap-2">
      <BarChart3 :size="20" class="text-brand" />
      <h1 class="text-lg font-semibold text-ink-900">度量</h1>
    </div>
    <ProjectMetricsPanel :project-id="projectId" variant="border" />
  </div>
</template>
