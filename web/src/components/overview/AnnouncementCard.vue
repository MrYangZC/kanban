<script setup lang="ts">
import { Megaphone, Plus } from 'lucide-vue-next'
import type { AnnouncementVO } from '@/api/types'

interface Props {
  announcements?: AnnouncementVO[]
}

const props = withDefaults(defineProps<Props>(), {
  announcements: () => []
})

function onAdd() {
  // emit or callback
}
</script>

<template>
  <div class="rounded-lg border border-[#E9ECEF] bg-white shadow-sm">
    <div class="flex items-center justify-between border-b border-[#E9ECEF] px-4 py-3">
      <div class="flex items-center gap-2 text-sm font-medium text-ink-900">
        <Megaphone :size="16" class="text-brand" /> 公告
      </div>
      <button class="flex items-center gap-1 text-xs text-brand transition hover:opacity-80" @click="onAdd">
        <Plus :size="12" /> 发布
      </button>
    </div>
    <div class="p-4">
      <div v-if="!props.announcements.length" class="py-6 text-center text-sm text-ink-400">暂无公告</div>
      <div v-else class="space-y-3">
        <div v-for="a in props.announcements" :key="a.id" class="border-b border-[#E9ECEF] pb-3 last:border-0 last:pb-0">
          <div class="flex items-center justify-between">
            <span class="text-sm font-medium text-ink-900">{{ a.title }}</span>
            <span class="text-xs text-ink-400">{{ a.createdAt?.slice(0, 10) }}</span>
          </div>
          <p class="mt-1 line-clamp-2 text-sm text-ink-600">{{ a.content }}</p>
          <div class="mt-1 text-xs text-ink-400">发布人：{{ a.authorName || '—' }}</div>
        </div>
      </div>
    </div>
  </div>
</template>