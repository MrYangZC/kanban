<script setup lang="ts">
import { AlertCircle, RefreshCw } from 'lucide-vue-next'

defineProps<{
  message?: string
}>()

const emit = defineEmits<{
  (e: 'retry'): void
}>()
</script>

<template>
  <div class="flex flex-col items-center justify-center rounded-lg border border-dashed border-line p-12 text-center">
    <AlertCircle :size="40" class="mb-3 text-danger opacity-60" />
    <p class="mb-4 text-sm text-ink-secondary">{{ message || '项目列表加载失败，请重试' }}</p>
    <button class="btn-primary" @click="emit('retry')">
      <RefreshCw :size="14" class="mr-1" /> 重试
    </button>
  </div>
</template>