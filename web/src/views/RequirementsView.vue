<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { filterIssues } from '@/api'
import type { Issue, PageResult } from '@/api/types'
import { Plus, Search, FileText } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'

const route = useRoute()
const projectId = route.params.projectId as string

const keyword = ref('')
const loading = ref(false)
const list = ref<Issue[]>([])
const total = ref(0)

async function load() {
  loading.value = true
  try {
    const res: PageResult<Issue> = await filterIssues({
      projectId,
      keyword: keyword.value || undefined,
      size: 50
    })
    list.value = res.list.filter((i) => i.issueTypeCode === 'REQUIREMENT' || i.issueTypeCode === 'STORY')
    total.value = list.value.length
  } catch {
    list.value = []
    total.value = 0
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
  <div class="h-full overflow-auto p-6">
    <div class="mb-4 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <FileText :size="20" class="text-brand" />
        <h1 class="text-lg font-semibold text-ink-900">需求</h1>
      </div>
      <button class="flex items-center gap-1.5 rounded-md bg-brand px-3 py-1.5 text-sm font-medium text-white transition hover:bg-brand-dark" @click="onCreate">
        <Plus :size="14" /> 新建需求
      </button>
    </div>

    <div class="mb-4 flex items-center gap-2">
      <div class="flex flex-1 items-center gap-2 rounded-md border border-[#E5E6EB] bg-white px-3 py-1.5">
        <Search :size="14" class="text-ink-400" />
        <input v-model="keyword" class="flex-1 bg-transparent text-sm outline-none placeholder:text-ink-400" placeholder="搜索需求…" @keydown.enter="load" />
      </div>
      <button class="rounded-md border border-[#E5E6EB] bg-white px-3 py-1.5 text-sm text-ink-600 transition hover:bg-surface-muted" @click="load">刷新</button>
    </div>

    <div class="rounded-lg border border-[#E9ECEF] bg-white">
      <div v-if="loading" class="py-20 text-center text-sm text-ink-400">加载中…</div>
      <div v-else-if="!list.length" class="py-20 text-center text-sm text-ink-400">暂无需求</div>
      <table v-else class="w-full text-sm">
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
    </div>
  </div>
</template>