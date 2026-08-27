<script setup lang="ts">
import { computed } from 'vue'
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-vue-next'

const props = defineProps<{
  currentPage: number
  totalPages: number
  total: number
  pageSize: number
}>()

const emit = defineEmits<{
  (e: 'update:page', page: number): void
  (e: 'update:pageSize', size: number): void
}>()

const pageSizeOptions = [10, 20, 25, 50]

const rangeStart = computed(() =>
  props.total === 0 ? 0 : (props.currentPage - 1) * props.pageSize + 1
)
const rangeEnd = computed(() =>
  Math.min(props.currentPage * props.pageSize, props.total)
)

const pages = computed<(number | '...')[]>(() => {
  const total = props.totalPages
  const cur = props.currentPage
  if (total <= 7) {
    return Array.from({ length: total }, (_, i) => i + 1)
  }
  const result: (number | '...')[] = [1]
  if (cur > 4) result.push('...')
  const start = Math.max(2, cur - 1)
  const end = Math.min(total - 1, cur + 1)
  for (let i = start; i <= end; i++) result.push(i)
  if (cur < total - 3) result.push('...')
  result.push(total)
  return result
})

function go(page: number) {
  if (page < 1 || page > props.totalPages || page === props.currentPage) return
  emit('update:page', page)
}

function onPageSize(e: Event) {
  const size = Number((e.target as HTMLSelectElement).value)
  emit('update:pageSize', size)
}
</script>

<template>
  <div class="flex flex-wrap items-center justify-between gap-4 px-1 py-1.5 text-sm">
    <div class="flex items-center gap-4 text-ink-secondary">
      <span>共 <span class="font-semibold text-ink">{{ total }}</span> 条</span>
      <span v-if="total > 0" class="hidden sm:inline">
        第 {{ rangeStart }}-{{ rangeEnd }} 条
      </span>
      <div class="flex items-center gap-1.5">
        <span class="hidden sm:inline">每页</span>
        <select
          :value="pageSize"
          class="cursor-pointer rounded-md border border-line-light bg-white px-2 py-1 text-xs text-ink-regular transition hover:border-primary-300 focus:border-primary focus:outline-none"
          @change="onPageSize"
        >
          <option v-for="opt in pageSizeOptions" :key="opt" :value="opt">{{ opt }} 条/页</option>
        </select>
      </div>
    </div>

    <div class="flex items-center gap-1">
      <button
        class="flex h-8 w-8 items-center justify-center rounded-md text-ink-secondary transition-all duration-200 hover:bg-surface-hover hover:text-ink disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent"
        title="第一页"
        :disabled="currentPage <= 1"
        @click="go(1)"
      >
        <ChevronsLeft :size="16" />
      </button>
      <button
        class="flex h-8 w-8 items-center justify-center rounded-md text-ink-secondary transition-all duration-200 hover:bg-surface-hover hover:text-ink disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent"
        title="上一页"
        :disabled="currentPage <= 1"
        @click="go(currentPage - 1)"
      >
        <ChevronLeft :size="16" />
      </button>

      <template v-for="(p, i) in pages" :key="i">
        <span v-if="p === '...'" class="flex h-8 w-8 items-center justify-center text-xs text-ink-secondary">…</span>
        <button
          v-else
          class="flex h-8 min-w-[32px] items-center justify-center rounded-md px-2 text-sm font-medium transition-all duration-200"
          :class="p === currentPage
            ? 'bg-primary-500 text-white shadow-sm'
            : 'text-ink-regular hover:bg-surface-hover hover:text-primary-600'"
          @click="go(p as number)"
        >{{ p }}</button>
      </template>

      <button
        class="flex h-8 w-8 items-center justify-center rounded-md text-ink-secondary transition-all duration-200 hover:bg-surface-hover hover:text-ink disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent"
        title="下一页"
        :disabled="currentPage >= totalPages"
        @click="go(currentPage + 1)"
      >
        <ChevronRight :size="16" />
      </button>
      <button
        class="flex h-8 w-8 items-center justify-center rounded-md text-ink-secondary transition-all duration-200 hover:bg-surface-hover hover:text-ink disabled:cursor-not-allowed disabled:opacity-40 disabled:hover:bg-transparent"
        title="最后一页"
        :disabled="currentPage >= totalPages"
        @click="go(totalPages)"
      >
        <ChevronsRight :size="16" />
      </button>
    </div>
  </div>
</template>