<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { filterIssues, createIssue, listProjectStatuses } from '@/api'
import { REQUIREMENT_TYPE_CODES } from '@/api/types'
import type { Issue, PageResult, IssueStatusVO } from '@/api/types'
import { FileText } from 'lucide-vue-next'
import { ElMessage, ElMessageBox } from 'element-plus'
import SimpleListView from '@/components/SimpleListView.vue'

const route = useRoute()
const projectId = route.params.projectId as string

const keyword = ref('')
const loading = ref(false)
const list = ref<Issue[]>([])
const statuses = ref<IssueStatusVO[]>([])

async function load() {
  loading.value = true
  try {
    const res: PageResult<Issue> = await filterIssues({
      projectId,
      keyword: keyword.value || undefined,
      issueTypeCodes: REQUIREMENT_TYPE_CODES,
      size: 50
    })
    list.value = res.list
  } catch {
    list.value = []
  } finally {
    loading.value = false
  }
}

async function onCreate() {
  try {
    const { value } = await ElMessageBox.prompt('请输入需求标题', '新建需求', {
      confirmButtonText: '创建',
      cancelButtonText: '取消',
      inputValidator: (v) => (v && v.trim() ? true : '标题不能为空')
    })
    await createIssue({
      projectId,
      title: value.trim(),
      statusId: statuses.value[0]?.id || '',
      priority: 'P2',
      issueTypeCode: 'STORY'
    })
    ElMessage.success('需求已创建')
    await load()
  } catch (e: any) {
    if (e !== 'cancel') ElMessage.error(e?.msg || '创建失败')
  }
}

onMounted(async () => {
  try { statuses.value = await listProjectStatuses(projectId) } catch { /* ignore */ }
  await load()
})
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
