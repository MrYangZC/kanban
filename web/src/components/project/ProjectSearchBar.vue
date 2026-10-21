<script setup lang="ts">
import { useProjectCategoryStore } from '@/stores/projectCategory'
import { Search, X } from 'lucide-vue-next'
import { ref, watch } from 'vue'

const store = useProjectCategoryStore()
const inputValue = ref(store.searchKeyword)

watch(() => store.searchKeyword, (val) => {
  if (val !== inputValue.value) inputValue.value = val
})

function onInput() {
  store.setSearchKeyword(inputValue.value)
}

function onClear() {
  inputValue.value = ''
  store.setSearchKeyword('')
}
</script>

<template>
  <div class="relative inline-flex items-center">
    <Search :size="15" class="absolute left-3 text-ink-secondary transition-colors" />
    <input
      v-model="inputValue"
      type="text"
      placeholder="搜索项目名称、Key 或描述"
      class="glass-inset w-72 rounded-lg py-2 pl-9 pr-9 text-sm text-ink transition-all duration-200 placeholder:text-ink-placeholder focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary-100"
      @input="onInput"
    />
    <button
      v-if="inputValue"
      class="absolute right-2.5 text-ink-secondary transition-all duration-200 hover:text-ink"
      title="清空"
      @click="onClear"
    >
      <X :size="15" />
    </button>
    <kbd
      v-else
      class="absolute right-2.5 hidden items-center gap-0.5 rounded border border-line-light bg-surface-light px-1.5 py-0.5 text-[10px] font-medium text-ink-secondary sm:inline-flex"
    >⌘K</kbd>
  </div>
</template>
