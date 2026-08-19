<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { filterIssues } from '@/api'
import type { Issue, PageResult } from '@/api/types'
import { FileText } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'
import SimpleListView from '@/components/SimpleListView.vue'

const route = useRoute()
const projectId = route.params.projectId as string

const keyword = ref('')
const loading = ref(false)
const list = ref<Issue[]>([])

async function load() {
  loading.value = true
  try {
    const res: PageResult<Issue> = await filterIssues({
      projectId,
      keyword: keyword.value || undefined,
      size: 50
    })
    list.value = res.list.filter((i) => i.issueTypeCode === 'REQUIREMENT' || i.issueTypeCode === 'STORY')
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

function onCreate() {
  ElMessage.info('新建需求功能开发中')
}

onMounted(load)
</script>

<template>
  <SimpleListView
    title="需求"
    :icon="FileText"
    search-placeholder="搜索需求…"
    :loading="loading"
    empty-text="暂无需求"
    create-label="新建需求"
    v-model:keyword="keyword"
    @search="load"
    @refresh="load"
    @create="onCreate"
  >
    <table v-if="list.length" class="w-full text-sm">
      <thead class="border-b border-[#E9ECEF] text-xs text-ink-400">
        <tr>
          <th class="px-4 py-3 text-left font-medium">编号</th>
          <th class="px-4 py-3 text-left font-medium">标题</th>
          <th class="px-4 py-3 text-left font-medium">优先级</th>
          <th class="px-4 py-3 text-left font-medium">负责人</th>
          <th class="px-4 py-3 text-left font-medium">状态</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="i in list" :key="i.id" class="border-b border-[#E9ECEF] last:border-0 transition hover:bg-surface-muted/50">
          <td class="px-4 py-3 text-ink-400">{{ i.issueKey }}</td>
          <td class="px-4 py-3 text-ink-900">{{ i.title }}</td>
          <td class="px-4 py-3 text-ink-600">{{ i.priority }}</td>
          <td class="px-4 py-3 text-ink-600">{{ i.assigneeName || '-' }}</td>
          <td class="px-4 py-3 text-ink-600">{{ i.statusCode }}</td>
        </tr>
      </tbody>
    </table>
    <div v-else class="py-20 text-center text-sm text-ink-400">暂无需求</div>
  </SimpleListView>
</template>
