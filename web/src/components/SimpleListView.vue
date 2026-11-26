<script setup lang="ts">
import type { Component } from 'vue'
import { Plus, Search, ArrowLeft } from 'lucide-vue-next'
import ScopeNav, { type ScopeNavGroup } from './ScopeNav.vue'
import ViewTabs, { type ViewTab } from './ViewTabs.vue'

const props = withDefaults(defineProps<{
  title: string
  icon: Component
  searchPlaceholder?: string
  loading?: boolean
  emptyText?: string
  canCreate?: boolean
  createLabel?: string
  /** 是否显示「导出」按钮（由调用方按 issue:export 权限决定） */
  canExport?: boolean
  keyword?: string
  showSearch?: boolean
  showRefresh?: boolean
  showIcon?: boolean
  isEmpty?: boolean
  // 以下为对齐缺陷页骨架的可选能力，不传则不渲染，保证既有调用方零改动
  total?: number
  showBack?: boolean
  navGroups?: ScopeNavGroup[]
  scope?: string
  viewTabs?: ViewTab[]
  view?: string
  page?: number
  size?: number
  showPagination?: boolean
}>(), {
  searchPlaceholder: '搜索…',
  loading: false,
  emptyText: '暂无数据',
  canCreate: true,
  createLabel: '新建',
  keyword: '',
  showSearch: true,
  showRefresh: true,
  showIcon: true,
  isEmpty: false,
  showBack: false,
  showPagination: false,
  page: 1,
  size: 25
})

const emit = defineEmits<{
  (e: 'update:keyword', v: string): void
  (e: 'update:scope', v: string): void
  (e: 'update:view', v: string): void
  (e: 'update:page', v: number): void
  (e: 'update:size', v: number): void
  (e: 'search'): void
  (e: 'refresh'): void
  (e: 'create'): void
  (e: 'export'): void
  (e: 'back'): void
}>()

function onInput(e: Event) {
  emit('update:keyword', (e.target as HTMLInputElement).value)
}

function onPageChange(v: number) {
  emit('update:page', v)
  emit('refresh')
}

function onSizeChange(v: number) {
  emit('update:size', v)
  emit('update:page', 1)
  emit('refresh')
}
</script>

<template>
  <div class="app-canvas flex h-full flex-col">
    <!-- 顶部工具栏：与缺陷页一致的 h-14 白底 + 底边线 -->
    <header class="toolbar">
      <button v-if="showBack" class="toolbar-back" @click="emit('back')">
        <ArrowLeft :size="16" />
      </button>
      <component v-if="showIcon" :is="props.icon" :size="18" class="text-brand" />
      <h1 class="toolbar-title">{{ title }}</h1>
      <span v-if="total !== undefined" class="count-pill">{{ total }}</span>

      <ViewTabs
        v-if="viewTabs?.length"
        :tabs="viewTabs"
        :model-value="view"
        @update:model-value="emit('update:view', $event)"
      />

      <div class="ml-auto flex items-center gap-3">
        <div v-if="showSearch" class="search-box">
          <Search :size="15" class="text-ink-secondary" />
          <input
            :value="keyword"
            class="search-input"
            :placeholder="searchPlaceholder"
            @input="onInput"
            @keydown.enter="emit('search')"
          />
        </div>
        <button v-if="showRefresh" class="btn-ghost" @click="emit('refresh')">刷新</button>
        <button v-if="canExport" class="btn-ghost" title="导出当前筛选结果为 CSV" @click="emit('export')">导出</button>
        <slot name="header-extra" />
        <button v-if="canCreate" class="btn-create" @click="emit('create')">
          <Plus :size="16" /> {{ createLabel }}
        </button>
      </div>
    </header>

    <!-- 筛选条：由页面注入（条件 + 分组维度）。不传则不渲染，既有页面零改动 -->
    <slot name="filters" />

    <div class="flex flex-1 overflow-hidden">
      <!-- 左侧范围导航：仅工作项类页面需要 -->
      <ScopeNav
        v-if="navGroups?.length"
        :groups="navGroups"
        :model-value="scope ?? ''"
        @update:model-value="emit('update:scope', $event)"
      />

      <!-- 主内容区 -->
      <main class="flex flex-1 flex-col overflow-hidden">
        <!-- 页面级摘要条：由页面注入，落在滚动区之外，不随表格一起滚走，也不占表格高度 -->
        <slot name="stats" />
        <!--
          这里曾经是 v-if="loading" 显示「加载中…」、v-else-if="isEmpty" 显示「暂无数据」，
          把整个插槽顶替掉——结果是加载/空数据时卡片外壳、表头、分页位置全部消失，
          页面高度还会跳一下，而且表格自己的 v-loading 与空态根本渲染不出来。
          现在插槽始终渲染，加载态交给表格的 v-loading、空态交给表格的 #empty 插槽；
          仅当调用方没有提供插槽内容时，才退回这两行文字兜底。
        -->
        <div class="flex-1 overflow-auto">
          <slot>
            <div v-if="loading" class="py-20 text-center text-sm text-ink-quiet">加载中…</div>
            <div v-else-if="isEmpty" class="py-20 text-center text-sm text-ink-quiet">{{ emptyText }}</div>
          </slot>
        </div>

        <!-- 分页条 -->
        <div
          v-if="showPagination && total !== undefined"
          class="glass-bar flex shrink-0 items-center justify-between border-t px-4 py-3"
        >
          <span class="text-xs text-ink-secondary">共 {{ total }} 条</span>
          <el-pagination
            :current-page="page"
            :page-size="size"
            :total="total"
            :page-sizes="[25, 50, 100]"
            layout="prev, pager, next, sizes"
            background
            @current-change="onPageChange"
            @size-change="onSizeChange"
          />
        </div>
      </main>
    </div>

    <slot name="footer" />
  </div>
</template>
