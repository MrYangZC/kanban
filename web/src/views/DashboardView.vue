<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, shallowRef } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts/core'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getDashboard } from '@/api'
import type { DashboardVO } from '@/api/types'
import { BarChart3, ChevronLeft, TrendingUp, CheckCircle2, Clock, AlertTriangle } from 'lucide-vue-next'

echarts.use([PieChart, BarChart, LineChart, TooltipComponent, LegendComponent, GridComponent, CanvasRenderer])

const route = useRoute()
const router = useRouter()
const projectId = route.params.projectId as string

const data = ref<DashboardVO | null>(null)
const statusEl = ref<HTMLElement | null>(null)
const priorityEl = ref<HTMLElement | null>(null)
const burndownEl = ref<HTMLElement | null>(null)
const charts = shallowRef<echarts.ECharts[]>([])
const BRAND = ['#1677FF', '#4096FF', '#69B1FF', '#95DEFF', '#0958D9', '#8F959E', '#52C41A', '#FAAD14', '#FF4D4F']
const priorityLabel: Record<string, string> = {
  P0: '紧急', P1: '高', P2: '中', P3: '低'
}

function renderCharts() {
  charts.value.forEach((c) => c.dispose())
  charts.value = []
  if (!data.value) return

  const statusChart = echarts.init(statusEl.value!)
  statusChart.setOption({
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{
      type: 'pie', radius: ['45%', '72%'], center: ['50%', '42%'],
              itemStyle: { borderRadius: 6, borderColor: '#fff', borderWidth: 2 },
      label: { show: false },
      data: data.value.statusDist.map((s, i) => ({ name: s.name, value: s.count, itemStyle: { color: BRAND[i % BRAND.length] } }))
    }]
  })
  charts.value.push(statusChart)

  const priorityChart = echarts.init(priorityEl.value!)
  priorityChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { top: 16, left: 8, right: 8, bottom: 8, containLabel: true },
    xAxis: { type: 'category', data: data.value.priorityDist.map((p) => priorityLabel[p.name] || p.name), axisLine: { lineStyle: { color: '#E5E6EB' } } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#F0F2F5' } } },
    series: [{
      type: 'bar', barWidth: '52%', itemStyle: { color: '#1677FF', borderRadius: [6, 6, 0, 0] },
      data: data.value.priorityDist.map((p) => p.count)
    }]
  })
  charts.value.push(priorityChart)

  const burndownChart = echarts.init(burndownEl.value!)
  burndownChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, data: ['剩余', '已完成'] },
    grid: { top: 36, left: 8, right: 16, bottom: 8, containLabel: true },
    xAxis: { type: 'category', data: data.value.burndown.map((b) => b.date.slice(5)), boundaryGap: false, axisLine: { lineStyle: { color: '#E5E6EB' } } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#F0F2F5' } } },
    series: [
      { name: '剩余', type: 'line', smooth: true, data: data.value.burndown.map((b) => b.remaining), itemStyle: { color: '#FF4D4F' }, areaStyle: { color: 'rgba(255,77,79,0.08)' } },
      { name: '已完成', type: 'line', smooth: true, data: data.value.burndown.map((b) => b.completed), itemStyle: { color: '#52C41A' } }
    ]
  })
  charts.value.push(burndownChart)
}

function resize() { charts.value.forEach((c) => c.resize()) }
window.addEventListener('resize', resize)

onMounted(async () => {
  data.value = await getDashboard(projectId)
  await Promise.resolve()
  renderCharts()
})
onBeforeUnmount(() => { charts.value.forEach((c) => c.dispose()); window.removeEventListener('resize', resize) })

function back() { router.push('/projects') }
function toBoard() { router.push(`/projects/${projectId}/board`) }
</script>

<template>
  <div class="min-h-full bg-surface-base">
    <header class="sticky top-0 z-10 flex h-14 items-center gap-3 border-b border-surface-muted bg-white px-4">
      <button class="flex items-center gap-1 text-sm text-ink-600 hover:text-ink-900" @click="back">
        <ChevronLeft :size="18" /> 项目
      </button>
      <div class="flex items-center gap-2">
        <BarChart3 :size="18" class="text-brand" />
        <span class="font-semibold text-ink-900">统计报表</span>
      </div>
      <button class="ml-auto rounded-lg border border-surface-muted px-3 py-1.5 text-sm text-ink-600 transition hover:bg-surface-muted" @click="toBoard">
        返回看板
      </button>
    </header>

    <div class="mx-auto max-w-6xl space-y-5 p-5" v-if="data">
      <!-- 概览卡片 -->
      <div class="grid grid-cols-2 gap-4 md:grid-cols-4">
        <div class="rounded-xl bg-white p-4 ring-1 ring-black/5 transition hover:shadow-md">
          <div class="flex items-center gap-2 text-ink-400"><TrendingUp :size="16" /><span class="text-xs">总数</span></div>
          <div class="mt-2 text-2xl font-semibold text-ink-900">{{ data.summary.total }}</div>
        </div>
        <div class="rounded-xl bg-white p-4 ring-1 ring-black/5 transition hover:shadow-md">
          <div class="flex items-center gap-2 text-ink-400"><Clock :size="16" /><span class="text-xs">进行中</span></div>
          <div class="mt-2 text-2xl font-semibold text-brand">{{ data.summary.inProgress }}</div>
        </div>
        <div class="rounded-xl bg-white p-4 ring-1 ring-black/5 transition hover:shadow-md">
          <div class="flex items-center gap-2 text-ink-400"><CheckCircle2 :size="16" /><span class="text-xs">已完成</span></div>
          <div class="mt-2 text-2xl font-semibold text-[#52C41A]">{{ data.summary.done }}</div>
        </div>
        <div class="rounded-xl bg-white p-4 ring-1 ring-black/5 transition hover:shadow-md">
          <div class="flex items-center gap-2 text-ink-400"><AlertTriangle :size="16" /><span class="text-xs">逾期</span></div>
          <div class="mt-2 text-2xl font-semibold text-[#FF4D4F]">{{ data.summary.overdue }}</div>
        </div>
      </div>

      <!-- 图表区 -->
      <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <div class="rounded-xl bg-white p-4 ring-1 ring-black/5">
          <div class="mb-2 text-sm font-medium text-ink-900">状态分布</div>
          <div ref="statusEl" class="h-[260px]" />
        </div>
        <div class="rounded-xl bg-white p-4 ring-1 ring-black/5">
          <div class="mb-2 text-sm font-medium text-ink-900">优先级分布</div>
          <div ref="priorityEl" class="h-[260px]" />
        </div>
        <div class="rounded-xl bg-white p-4 ring-1 ring-black/5 lg:col-span-2">
          <div class="mb-2 text-sm font-medium text-ink-900">燃尽图（近 14 天）</div>
          <div ref="burndownEl" class="h-[280px]" />
        </div>
      </div>

      <!-- 工作量 & 周期 -->
      <div class="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <div class="rounded-xl bg-white p-4 ring-1 ring-black/5 lg:col-span-2">
          <div class="mb-3 text-sm font-medium text-ink-900">按负责人工作量</div>
          <div class="space-y-2">
            <div v-for="a in data.assigneeDist" :key="a.name" class="flex items-center gap-3 text-sm">
              <span class="w-24 shrink-0 truncate text-ink-600">{{ a.name }}</span>
              <div class="h-2.5 flex-1 overflow-hidden rounded-full bg-surface-muted">
                <div class="h-full rounded-full bg-brand" :style="{ width: (data.assigneeDist[0].count ? (a.count / data.assigneeDist[0].count) * 100 : 0) + '%' }" />
              </div>
              <span class="w-8 text-right text-ink-400">{{ a.count }}</span>
            </div>
          </div>
        </div>
        <div class="rounded-xl bg-white p-4 ring-1 ring-black/5">
          <div class="mb-3 text-sm font-medium text-ink-900">周期时间</div>
          <div class="space-y-3 text-sm">
            <div class="flex items-center justify-between"><span class="text-ink-400">平均解决</span><span class="font-semibold text-ink-900">{{ data.cycleTime.avgHours }} h</span></div>
            <div class="flex items-center justify-between"><span class="text-ink-400">最长解决</span><span class="font-semibold text-ink-900">{{ data.cycleTime.maxHours }} h</span></div>
            <div class="flex items-center justify-between"><span class="text-ink-400">总体完成率</span><span class="font-semibold text-[#52C41A]">{{ data.summary.total ? Math.round((data.summary.done / data.summary.total) * 100) : 0 }}%</span></div>
          </div>
        </div>
      </div>
    </div>
    <div v-else class="p-10 text-center text-sm text-ink-400">加载报表中…</div>
  </div>
</template>
