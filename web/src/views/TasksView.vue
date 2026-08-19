<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { filterIssues, listProjectStatuses } from '@/api'
import type { Issue, PageResult, IssueStatusVO } from '@/api/types'
import { ListTodo } from 'lucide-vue-next'
import SimpleListView from '@/components/SimpleListView.vue'
import CreateIssueDialog from '@/components/CreateIssueDialog.vue'

const route = useRoute()
const router = useRouter()
const projectId = route.params.projectId as string

const keyword = ref('')
const loading = ref(false)
const list = ref<Issue[]>([])
const statuses = ref<IssueStatusVO[]>([])

const createOpen = ref(false)

async function load() {
  loading.value = true
  try {
    const res: PageResult<Issue> = await filterIssues({
      projectId,
      keyword: keyword.value || undefined,
      size: 50
    })
    list.value = res.list.filter((i) => i.issueTypeCode === 'TASK' || i.issueTypeCode === 'EPIC')
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function openCreate() {
  if (!statuses.value.length) {
    try { statuses.value = await listProjectStatuses(projectId) } catch { /* ignore */ }
  }
  createOpen.value = true
}

function onCreated() {
  load()
}

function toBoard() {
  router.push(`/projects/${projectId}/board`)
}

onMounted(load)
</script>

<template>
  <SimpleListView
    title="任务"
    :icon="ListTodo"
    search-placeholder="搜索任务…"
    :loading="loading"
    empty-text="暂无任务"
    create-label="新建任务"
    v-model:keyword="keyword"
    @search="load"
    @refresh="load"
    @create="openCreate"
  >
    <template #header-extra>
      <button class="rounded-md border border-[#E5E6EB] bg-white px-3 py-1.5 text-sm text-ink-600 transition hover:bg-surface-muted" @click="toBoard">看板视图</button>
    </template>

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
    <div v-else class="py-20 text-center text-sm text-ink-400">暂无任务</div>

    <template #footer>
      <CreateIssueDialog
        v-model="createOpen"
        :project-id="projectId"
        title="新建任务"
        default-issue-type-code="TASK"
        :default-status-id="statuses[0]?.id || ''"
        @created="onCreated"
      />
    </template>
  </SimpleListView>
</template>
