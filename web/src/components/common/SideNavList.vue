<script setup lang="ts">
/**
 * 左侧导航列表（通用）。
 *
 * <p>项目列表页的分类导航与项目内的功能导航此前是两套各写一遍的标记：
 * 外侧有圆角、图标 chip、计数徽标，内侧是通栏平铺的 `bg-primary/10`，
 * 于是同一个应用里出现两种"侧边栏"观感。这里把标记收敛成一份，
 * 两个调用点只负责传数据。
 *
 * <p>为什么不直接抽成"数据驱动 + 自带数据源"：两边的数据来源完全不同
 * （一边来自分类 store 并带计数，一边是静态路由表），组件只认 items，
 * 谁提供数据由调用方决定。
 */
import { computed } from 'vue'
import type { Component } from 'vue'

export interface SideNavItem {
  /** 唯一标识，点击时通过 select 事件回传 */
  key: string
  label: string
  icon?: Component
  /** 右侧计数徽标；undefined 时不显示（项目内导航没有计数） */
  count?: number
}

const props = withDefaults(
  defineProps<{
    items: SideNavItem[]
    /** 当前选中项的 key */
    active?: string
    /** 折叠态：只显示图标，标题降级为 title 提示 */
    collapsed?: boolean
  }>(),
  { active: '', collapsed: false }
)

const emit = defineEmits<{ (e: 'select', key: string): void }>()

const navClass = computed(() =>
  // 展开态留 16px 内缩：菜单项不贴侧栏边缘，块状高亮的圆角才不会被切边
  props.collapsed ? 'flex-1 space-y-1 overflow-y-auto px-2 py-3' : 'flex-1 space-y-1 overflow-y-auto px-4 py-3'
)
</script>

<template>
  <nav :class="navClass">
    <button
      v-for="item in items"
      :key="item.key"
      class="group flex w-full items-center rounded-lg text-left text-sm transition-all duration-200 ease-out-expo"
      :class="[
        collapsed ? 'justify-center px-2 py-2' : 'gap-3 px-3 py-2.5',
        active === item.key
          ? 'bg-primary-50 font-medium text-primary-700 shadow-sm'
          : 'text-ink-regular hover:bg-surface-hover hover:text-ink'
      ]"
      :title="collapsed ? item.label : undefined"
      @click="emit('select', item.key)"
    >
      <!-- 图标 chip：选中时整块实心主色，未选中是浅底，悬停转主色 -->
      <span
        class="flex h-7 w-7 shrink-0 items-center justify-center rounded-md transition-all duration-200"
        :class="active === item.key
          ? 'bg-primary-500 text-white shadow-sm'
          : 'bg-surface-light text-ink-secondary group-hover:bg-primary-100 group-hover:text-primary-500'"
      >
        <component :is="item.icon" v-if="item.icon" :size="15" />
      </span>
      <template v-if="!collapsed">
        <span class="flex-1 truncate">{{ item.label }}</span>
        <span
          v-if="item.count !== undefined"
          class="inline-flex min-w-[22px] shrink-0 items-center justify-center rounded-full px-2 py-0.5 text-xs font-medium transition-all duration-200"
          :class="active === item.key
            ? 'bg-primary-500 text-white'
            : 'bg-surface-light text-ink-secondary group-hover:bg-primary-100 group-hover:text-primary-600'"
        >{{ item.count }}</span>
      </template>
    </button>
  </nav>
</template>
