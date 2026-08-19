<script setup lang="ts">
import { ref } from 'vue'
import { Plus, ShieldAlert, Search } from 'lucide-vue-next'
import { ElMessage } from 'element-plus'

interface Risk {
  id: string
  title: string
  level: 'high' | 'medium' | 'low'
  status: 'open' | 'mitigating' | 'closed'
  owner: string
  dueDate: string
}

const keyword = ref('')
const risks = ref<Risk[]>([
  { id: '1', title: '第三方接口稳定性风险', level: 'high', status: 'open', owner: '张三', dueDate: '2026-09-01' },
  { id: '2', title: '数据库性能瓶颈', level: 'medium', status: 'mitigating', owner: '李四', dueDate: '2026-09-15' },
  { id: '3', title: '人员流失知识转移', level: 'low', status: 'closed', owner: '王五', dueDate: '2026-08-10' }
])

const LEVEL_STYLE: Record<string, { label: string; color: string; bg: string }> = {
  high: { label: '高', color: '#DC3545', bg: 'rgba(220,53,69,0.1)' },
  medium: { label: '中', color: '#FFC107', bg: 'rgba(255,193,7,0.1)' },
  low: { label: '低', color: '#28A745', bg: 'rgba(40,167,69,0.1)' }
}

const STATUS_STYLE: Record<string, { label: string; color: string; bg: string }> = {
  open: { label: '待处理', color: '#DC3545', bg: 'rgba(220,53,69,0.1)' },
  mitigating: { label: '处理中', color: '#1677FF', bg: 'rgba(22,119,255,0.1)' },
  closed: { label: '已关闭', color: '#28A745', bg: 'rgba(40,167,69,0.1)' }
}

const filteredRisks = ref(risks.value)

function onSearch() {
  filteredRisks.value = risks.value.filter((r) =>
    !keyword.value || r.title.includes(keyword.value)
  )
}

function onCreate() {
  ElMessage.info('新建风险功能开发中')
}
</script>

<template>
  <div class="h-full overflow-auto p-6">
    <div class="mb-4 flex items-center justify-between">
      <div class="flex items-center gap-2">
        <ShieldAlert :size="20" class="text-brand" />
        <h1 class="text-lg font-semibold text-ink-900">风险</h1>
      </div>
      <button class="flex items-center gap-1.5 rounded-md bg-brand px-3 py-1.5 text-sm font-medium text-white transition hover:bg-brand-dark" @click="onCreate">
        <Plus :size="14" /> 新建风险
      </button>
    </div>

    <div class="mb-4 flex items-center gap-2">
      <div class="flex flex-1 items-center gap-2 rounded-md border border-[#E5E6EB] bg-white px-3 py-1.5">
        <Search :size="14" class="text-ink-400" />
        <input v-model="keyword" class="flex-1 bg-transparent text-sm outline-none placeholder:text-ink-400" placeholder="搜索风险…" @keydown.enter="onSearch" />
      </div>
    </div>

    <div class="rounded-lg border border-[#E9ECEF] bg-white">
      <div v-if="!filteredRisks.length" class="py-20 text-center text-sm text-ink-400">暂无风险</div>
      <table v-else class="w-full text-sm">
        <thead class="border-b border-[#E9ECEF] text-xs text-ink-400">
          <tr>
            <th class="px-4 py-3 text-left font-medium">风险描述</th>
            <th class="px-4 py-3 text-left font-medium">风险等级</th>
            <th class="px-4 py-3 text-left font-medium">状态</th>
            <th class="px-4 py-3 text-left font-medium">负责人</th>
            <th class="px-4 py-3 text-left font-medium">截止日期</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="r in filteredRisks" :key="r.id" class="border-b border-[#E9ECEF] last:border-0 transition hover:bg-surface-muted/50">
            <td class="px-4 py-3 text-ink-900">{{ r.title }}</td>
            <td class="px-4 py-3">
              <span class="rounded px-2 py-0.5 text-xs" :style="{ color: LEVEL_STYLE[r.level].color, backgroundColor: LEVEL_STYLE[r.level].bg }">
                {{ LEVEL_STYLE[r.level].label }}
              </span>
            </td>
            <td class="px-4 py-3">
              <span class="rounded px-2 py-0.5 text-xs" :style="{ color: STATUS_STYLE[r.status].color, backgroundColor: STATUS_STYLE[r.status].bg }">
                {{ STATUS_STYLE[r.status].label }}
              </span>
            </td>
            <td class="px-4 py-3 text-ink-600">{{ r.owner }}</td>
            <td class="px-4 py-3 text-ink-400">{{ r.dueDate }}</td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>