<script setup lang="ts">
/**
 * 表格空态（el-table 的 #empty 插槽内容）。
 *
 * <p>此前各页空态不一致：任务页用自写的 span，其余页用 el-table 默认文案
 * （13px、#909399，白底对比度约 3.0:1，低于 WCAG AA 的 4.5:1，也踩了"灰底浅字看不清"的老问题）。
 * 统一成「图标 + 主文案 + 引导语」三段，颜色走 --ink-secondary / --ink-quiet 两个达标的令牌。
 */
import type { Component } from 'vue'

withDefaults(defineProps<{
  /** 与页面语义匹配的图标（lucide 组件），可省略 */
  icon?: Component
  /** 主文案，如「暂无迭代」 */
  title: string
  /** 引导语：告诉用户下一步能做什么，如「点击右上角新建迭代」 */
  hint?: string
  /** 动作按钮文案（如「重试」），不传则不渲染按钮 */
  actionLabel?: string
}>(), {
  icon: undefined,
  hint: '',
  actionLabel: ''
})

const emit = defineEmits<{ (e: 'action'): void }>()
</script>

<template>
  <div class="wi-empty-state">
    <component :is="icon" v-if="icon" :size="26" class="wi-empty-icon" />
    <p class="wi-empty-title">{{ title }}</p>
    <p v-if="hint" class="wi-empty-hint">{{ hint }}</p>
    <button v-if="actionLabel" class="wi-empty-action" @click.stop="emit('action')">{{ actionLabel }}</button>
  </div>
</template>
