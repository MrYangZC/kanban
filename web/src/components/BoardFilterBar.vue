<script setup lang="ts">
import { Search, BarChart3, Plus, LayoutGrid, Table2, AlertTriangle } from 'lucide-vue-next'
import type { LabelVO } from '@/api/types'

export interface FilterPayload {
  scope: string
  keyword: string
  priority: string
  severity: string
  statusId: string
  labelId: string
}

const filter = defineModel<FilterPayload>({ required: true })

const props = defineProps<{
  boardName: string
  tags: LabelVO[]
  view: 'board' | 'table' | 'defects'
  total: number
  canWrite: boolean
}>()

const emit = defineEmits<{
  (e: 'change'): void
  (e: 'switch-view', view: 'board' | 'table' | 'defects'): void
  (e: 'new'): void
  (e: 'dashboard'): void
  (e: 'back'): void
}>()

const scopes = [
  { k: '', label: '全部' },
  { k: 'ASSIGNED', label: '我负责的' },
  { k: 'REPORTED', label: '我创建的' },
  { k: 'VERIFIED', label: '我验证的' },
  { k: 'ARCHIVED', label: '已归档' }
]
const priorityOptions = [
  { k: 'P0', label: '紧急' },
  { k: 'P1', label: '高' },
  { k: 'P2', label: '中' },
  { k: 'P3', label: '低' }
]
const severityOptions = [
  { k: 'S1', label: '致命' },
  { k: 'S2', label: '严重' },
  { k: 'S3', label: '一般' },
  { k: 'S4', label: '轻微' }
]

function setScope(k: string) {
  filter.value.scope = k
  emit('change')
}
</script>

<template>
  <header class="flex h-14 shrink-0 flex-wrap items-center gap-3 border-b border-surface-muted bg-white px-4 py-2">
    <button class="flex items-center gap-1 text-sm text-ink-600 hover:text-ink-900" @click="emit('back')">
      <LayoutGrid :size="16" /> 项目
    </button>
    <div class="flex items-center gap-2">
      <LayoutGrid :size="18" class="text-brand" />
      <span class="font-semibold text-ink-900">{{ boardName || '任务看板' }}</span>
    </div>

    <div class="flex items-center gap-2 rounded-lg bg-surface-muted px-2.5 py-1.5">
      <Search :size="15" class="text-ink-400" />
      <input
        v-model="filter.keyword"
        placeholder="搜索任务"
        class="w-40 bg-transparent text-sm outline-none"
        @keyup.enter="emit('change')"
      />
    </div>

    <select
      v-model="filter.priority"
      class="rounded-lg border border-surface-muted px-2 py-1.5 text-sm outline-none focus:border-brand"
      @change="emit('change')"
    >
      <option value="">优先级</option>
      <option v-for="p in priorityOptions" :key="p.k" :value="p.k">{{ p.label }}</option>
    </select>
    <select
      v-model="filter.severity"
      class="rounded-lg border border-surface-muted px-2 py-1.5 text-sm outline-none focus:border-brand"
      @change="emit('change')"
    >
      <option value="">严重度</option>
      <option v-for="s in severityOptions" :key="s.k" :value="s.k">{{ s.label }}</option>
    </select>
    <select
      v-model="filter.labelId"
      class="rounded-lg border border-surface-muted px-2 py-1.5 text-sm outline-none focus:border-brand"
      @change="emit('change')"
    >
      <option value="">标签</option>
      <option v-for="t in tags" :key="t.id" :value="t.id">{{ t.name }}</option>
    </select>

    <div class="ml-auto flex items-center gap-2">
      <!-- 视图切换：看板 / 表格 -->
      <div class="flex items-center gap-1 rounded-lg border border-surface-muted p-0.5">
        <button
          class="rounded-md px-2.5 py-1 text-xs font-medium transition"
          :class="view === 'board' ? 'bg-brand text-white' : 'text-ink-600 hover:bg-surface-muted'"
          @click="emit('switch-view', 'board')"
        >
          <LayoutGrid :size="12" class="mr-1 inline" />看板
        </button>
        <button
          class="rounded-md px-2.5 py-1 text-xs font-medium transition"
          :class="view === 'table' ? 'bg-brand text-white' : 'text-ink-600 hover:bg-surface-muted'"
          @click="emit('switch-view', 'table')"
        >
          <Table2 :size="12" class="mr-1 inline" />表格
        </button>
        <button
          class="rounded-md px-2.5 py-1 text-xs font-medium transition"
          :class="view === 'defects' ? 'bg-brand text-white' : 'text-ink-600 hover:bg-surface-muted'"
          @click="emit('switch-view', 'defects')"
        >
          <AlertTriangle :size="12" class="mr-1 inline" />缺陷详情
        </button>
      </div>

      <button
        class="flex items-center gap-1 rounded-lg border border-surface-muted px-3 py-1.5 text-sm text-ink-600 transition hover:bg-surface-muted"
        @click="emit('dashboard')"
      >
        <BarChart3 :size="15" /> 报表
      </button>
      <span class="text-xs text-ink-400">共 {{ total }} 个任务</span>
      <button
        v-if="canWrite"
        class="flex items-center gap-1 rounded-lg bg-brand px-3 py-1.5 text-sm font-medium text-white transition hover:bg-brand-dark"
        @click="emit('new')"
      >
        <Plus :size="15" /> 新建任务
      </button>
    </div>

    <!-- 范围胶囊 -->
    <div class="flex w-full items-center gap-1.5">
      <button
        v-for="s in scopes"
        :key="s.k"
        class="rounded-full px-3 py-1 text-xs font-medium transition"
        :class="filter.scope === s.k ? 'bg-brand/10 text-brand' : 'text-ink-500 hover:bg-surface-muted'"
        @click="setScope(s.k)"
      >
        {{ s.label }}
      </button>
    </div>
  </header>
</template>
