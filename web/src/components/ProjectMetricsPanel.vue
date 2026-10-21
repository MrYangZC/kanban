<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, shallowRef, computed } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts/core'
import { PieChart, BarChart, LineChart } from 'echarts/charts'
import { TooltipComponent, LegendComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { getDashboard } from '@/api'
import type { DashboardVO } from '@/api/types'
import { UserRound, FileText, Bug, ListTodo } from 'lucide-vue-next'
import type { LucideIcon } from 'lucide-vue-next'
import ProgressBar from '@/components/ProgressBar.vue'
import UserAvatar from '@/components/common/UserAvatar.vue'
import { CHART_PALETTE, CHART_AXIS_LINE, CHART_SPLIT_LINE, METRIC_DONE, METRIC_OVERDUE } from '@/theme/colorMap'

echarts.use([PieChart, BarChart, LineChart, TooltipComponent, LegendComponent, GridComponent, CanvasRenderer])

const props = withDefaults(defineProps<{
  projectId: string
  variant?: 'border' | 'ring'
}>(), {
  variant: 'border'
})

const data = ref<DashboardVO | null>(null)
const statusEl = ref<HTMLElement | null>(null)
const priorityEl = ref<HTMLElement | null>(null)
const burndownEl = ref<HTMLElement | null>(null)
const charts = shallowRef<echarts.ECharts[]>([])
const priorityLabel: Record<string, string> = { P0: '紧急', P1: '高', P2: '中', P3: '低' }

const panelClass = computed(() =>
  props.variant === 'ring' ? 'glass-panel rounded-xl p-4' : 'glass-panel rounded-lg p-4'
)

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
      data: data.value.statusDist.map((s, i) => ({ name: s.name, value: s.count, itemStyle: { color: CHART_PALETTE[i % CHART_PALETTE.length] } }))
    }]
  })
  charts.value.push(statusChart)

  const priorityChart = echarts.init(priorityEl.value!)
  priorityChart.setOption({
    tooltip: { trigger: 'axis' },
    grid: { top: 16, left: 8, right: 8, bottom: 8, containLabel: true },
    xAxis: { type: 'category', data: data.value.priorityDist.map((p) => priorityLabel[p.name] || p.name), axisLine: { lineStyle: { color: CHART_AXIS_LINE } } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: CHART_SPLIT_LINE } } },
    series: [{
      type: 'bar', barWidth: '52%', itemStyle: { color: CHART_PALETTE[0], borderRadius: [6, 6, 0, 0] },
      data: data.value.priorityDist.map((p) => p.count)
    }]
  })
  charts.value.push(priorityChart)

  const burndownChart = echarts.init(burndownEl.value!)
  burndownChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, data: ['剩余', '已完成'] },
    grid: { top: 36, left: 8, right: 16, bottom: 8, containLabel: true },
    xAxis: { type: 'category', data: data.value.burndown.map((b) => b.date.slice(5)), boundaryGap: false, axisLine: { lineStyle: { color: CHART_AXIS_LINE } } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: CHART_SPLIT_LINE } } },
    series: [
      { name: '剩余', type: 'line', smooth: true, data: data.value.burndown.map((b) => b.remaining), itemStyle: { color: METRIC_OVERDUE.ring }, areaStyle: { color: METRIC_OVERDUE.area } },
      { name: '已完成', type: 'line', smooth: true, data: data.value.burndown.map((b) => b.completed), itemStyle: { color: METRIC_DONE.ring } }
    ]
  })
  charts.value.push(burndownChart)
}

function resize() { charts.value.forEach((c) => c.resize()) }

onMounted(async () => {
  try {
    data.value = await getDashboard(props.projectId)
  } catch {
    data.value = null
  }
  await Promise.resolve()
  renderCharts()
  window.addEventListener('resize', resize)
})
onBeforeUnmount(() => {
  charts.value.forEach((c) => c.dispose())
  window.removeEventListener('resize', resize)
})

const doneColor = computed(() => METRIC_DONE[props.variant])
const overdueColor = computed(() => METRIC_OVERDUE[props.variant])

const router = useRouter()

/** 分类型 → 列表页路由与图标；键与后端 typeCode 对应 */
const TYPE_META: Record<string, { path: string; icon: LucideIcon }> = {
  REQUIREMENT: { path: 'requirements', icon: FileText },
  BUG: { path: 'defects', icon: Bug },
  TASK: { path: 'tasks', icon: ListTodo }
}

type StatusFilter = { statusCategory?: 'TODO' | 'IN_PROGRESS' | 'DONE'; overdue?: boolean }

/** 点数字 → 跳到对应列表页并带上筛选参数，列表页读 query 后会自动套用 */
function jump(path: string, filter: StatusFilter = {}) {
  const query: Record<string, string> = {}
  if (filter.statusCategory) query.statusCategory = filter.statusCategory
  if (filter.overdue) query.overdue = '1'
  router.push({ path: `/projects/${props.projectId}/${path}`, query })
}

/** 每个类型卡片下排的可点数字：全部 / 待处理 / 处理中 / 已完成 / 逾期 */
const TYPE_FILTERS: { key: string; label: string; filter: StatusFilter }[] = [
  { key: 'total', label: '全部', filter: {} },
  { key: 'todo', label: '待处理', filter: { statusCategory: 'TODO' } },
  { key: 'inProgress', label: '处理中', filter: { statusCategory: 'IN_PROGRESS' } },
  { key: 'done', label: '已完成', filter: { statusCategory: 'DONE' } },
  { key: 'overdue', label: '逾期', filter: { overdue: true } }
]

const typeCards = computed(() => (data.value?.typeSummaries ?? []).map((t) => ({
  ...t,
  icon: TYPE_META[t.typeCode]?.icon ?? FileText,
  path: TYPE_META[t.typeCode]?.path ?? 'table'
})))
</script>

<template>
  <div v-if="data" class="space-y-4">
    <!-- 分类型概览：需求 / 缺陷 / 任务，数字可点进对应列表 -->
    <div class="grid grid-cols-1 gap-4 md:grid-cols-3">
      <div v-for="t in typeCards" :key="t.typeCode" :class="panelClass">
        <div class="mb-3 flex items-center justify-between">
          <div class="flex items-center gap-2 text-sm font-medium text-ink-900">
            <component :is="t.icon" :size="15" class="text-brand" />
            {{ t.typeName }}
          </div>
          <span class="text-xs text-ink-400">共 {{ t.total }}</span>
        </div>
        <div class="grid grid-cols-5 gap-1">
          <button
            v-for="f in TYPE_FILTERS"
            :key="f.key"
            class="rounded-md px-1 py-1.5 text-center transition hover:bg-surface-muted"
            :title="`查看${f.label}的${t.typeName}`"
            @click="jump(t.path, f.filter)"
          >
            <div
              class="text-lg font-semibold leading-tight"
              :class="f.key === 'overdue' && (t[f.key] as number) > 0 ? '' : 'text-ink-900'"
              :style="f.key === 'overdue' ? { color: (t[f.key] as number) > 0 ? overdueColor : undefined } : {}"
            >{{ t[f.key as keyof typeof t] }}</div>
            <div class="mt-0.5 text-[11px] text-ink-400">{{ f.label }}</div>
          </button>
        </div>
      </div>
    </div>

    <div class="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <div :class="panelClass">
        <div class="mb-2 text-sm font-medium text-ink-900">状态分布</div>
        <div ref="statusEl" class="h-[260px]" />
      </div>
      <div :class="panelClass">
        <div class="mb-2 text-sm font-medium text-ink-900">优先级分布</div>
        <div ref="priorityEl" class="h-[260px]" />
      </div>
      <div :class="panelClass" class="lg:col-span-2">
        <div class="mb-2 text-sm font-medium text-ink-900">燃尽图（近 14 天）</div>
        <div ref="burndownEl" class="h-[280px]" />
      </div>
    </div>

    <div class="grid grid-cols-1 gap-4 lg:grid-cols-3">
      <div :class="panelClass" class="lg:col-span-2">
        <div class="mb-3 text-sm font-medium text-ink-900">按负责人工作量</div>
        <div class="space-y-2">
          <div v-for="(a, idx) in data.assigneeDist" :key="a.name + idx" class="flex items-center gap-2 text-sm">
            <span
              v-if="a.name === '未分配'"
              class="inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-ink-100 text-ink-400"
              title="未分配"
            >
              <UserRound :size="13" />
            </span>
            <UserAvatar v-else :name="a.name" :src="a.avatarUrl" :size="24" />
            <span class="w-20 shrink-0 truncate text-ink-600">{{ a.name }}</span>
            <ProgressBar
              class="flex-1"
              :value="a.count"
              :max="data.assigneeDist[0]?.count || 1"
              height="lg"
            />
            <span class="w-8 text-right text-ink-400">{{ a.count }}</span>
          </div>
        </div>
      </div>
      <div :class="panelClass">
        <div class="mb-3 text-sm font-medium text-ink-900">周期时间</div>
        <div class="space-y-3 text-sm">
          <div class="flex items-center justify-between"><span class="text-ink-400">平均解决</span><span class="font-semibold text-ink-900">{{ data.cycleTime.avgHours }} h</span></div>
          <div class="flex items-center justify-between"><span class="text-ink-400">最长解决</span><span class="font-semibold text-ink-900">{{ data.cycleTime.maxHours }} h</span></div>
          <div class="flex items-center justify-between"><span class="text-ink-400">总体完成率</span><span class="font-semibold" :style="{ color: doneColor }">{{ data.summary.total ? Math.round((data.summary.done / data.summary.total) * 100) : 0 }}%</span></div>
        </div>
      </div>
    </div>
  </div>
  <div v-else class="py-20 text-center text-sm text-ink-400">加载报表中…</div>
</template>