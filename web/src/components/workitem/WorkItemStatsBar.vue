<script setup lang="ts">
/**
 * 工作项统计条：概览页 StatCard 的同族紧凑版。
 *
 * <p>列表/树形页原先从「工具栏」直接跳到「一张裸表」，没有汇总、没有配色、没有留白节奏，
 * 与概览页那种"卡片分组 + 彩色 KPI"的层级差得很远。这里补上同一套视觉语法：
 * 每张迷你卡片 = 标签（小字灰）+ 彩色数值（大字）+ 右上角同色图标。
 *
 * <p>相比概览页 StatCard 只缩小了内边距与字号：数据页的主角仍是表格，
 * 统计只负责说明"总量与分布"，不应抢走整屏。
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
  <div class="wi-stats">
    <div v-for="item in resolved" :key="item.key" class="wi-stat glass-panel">
      <div class="flex items-start justify-between gap-2">
        <span class="wi-stat-label">{{ item.label }}</span>
        <component
          :is="item.icon"
          :size="15"
          class="wi-stat-icon"
          :style="{ color: item.color || 'var(--ink-secondary)' }"
        />
      </div>
      <div class="wi-stat-value" :style="{ color: item.color || 'var(--ink)' }">{{ item.value }}</div>
    </div>
  </div>
</template>
