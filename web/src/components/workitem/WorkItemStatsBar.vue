<script setup lang="ts">
/**
 * 页面级工作项摘要条（统计）。
 *
 * <p>最初是内容区里的 4 张大卡，问题有两个：一是要占掉约 110px 的表格高度，
 * 二是它在滚动容器里，往下翻表格时统计就滚走了——一个"概览"滚没了就失去意义。
 * 现在改由 SimpleListView 的 stats 插槽渲染在内容滚动区之外：
 * 常驻、不滚动、不挤占表格，样式也从卡片降为一条细摘要（标签 + 彩色数值 + 分隔线）。
 */
import { computed } from 'vue'
import type { Component } from 'vue'
import { FileText, Circle, Clock, CheckCircle2, Ban } from 'lucide-vue-next'

export interface WorkItemStat {
  key: string
  label: string
  value: number
  color?: string
  icon?: Component
}

const props = defineProps<{ items: WorkItemStat[] }>()

const ICONS: Record<string, Component> = {
  total: FileText,
  todo: Circle,
  inProgress: Clock,
  done: CheckCircle2,
  cancelled: Ban
}

const resolved = computed(() =>
  props.items.map((it) => ({ ...it, icon: it.icon ?? ICONS[it.key] ?? Circle }))
)
</script>

<template>
  <div class="wi-strip">
    <div v-for="(item, i) in resolved" :key="item.key" class="wi-strip-item">
      <span v-if="i > 0" class="wi-strip-sep" />
      <component
        :is="item.icon"
        :size="13"
        class="wi-strip-icon"
        :style="{ color: item.color || 'var(--ink-secondary)' }"
      />
      <span class="wi-strip-label">{{ item.label }}</span>
      <span class="wi-strip-value" :style="{ color: item.color || 'var(--ink)' }">{{ item.value }}</span>
    </div>
  </div>
</template>
