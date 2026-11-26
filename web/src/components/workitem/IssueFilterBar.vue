<script setup lang="ts">
/**
 * 工作项列表的筛选条（交互对齐云效的「筛选」）。
 *
 * <p>云效的做法是把筛选收成一个入口：工具栏上点「筛选」→ 面板里按字段分组挑条件 →
 * 已加的条件以 chip 形式贴回筛选条、可单独删也可一键清空 → 面板底部还能把整套条件
 * 存成「筛选方案」下次一键套用。这样数据一多，用户不用在一排平铺的下拉框里找条件，
 * 也不会因为换了视图就看不见自己还筛着什么。
 *
 * <p>字段分组：基础字段（状态/优先级/[严重度]/标签）· 人员（负责人/创建者）·
 * 计划（迭代/只看逾期）· 时间（创建/更新/计划完成，按天区间）。
 *
 * <p>本组件只负责「条件的选择与展示」，不发请求：任何改动都 emit('change')，
 * 由页面统一重置页码后重新取数（列表/树形/看板三视图共用同一份条件）。
 */
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Filter, Layers, Trash2, X } from 'lucide-vue-next'
import type { IssueStatusVO, LabelVO, SprintVO, UserOption } from '@/api/types'
import type { IssueFilterState } from '@/composables/useIssueFilterState'
import { countActiveConditions } from '@/composables/useIssueFilterState'
import { PRIORITY_OPTIONS, SEVERITY_OPTIONS } from '@/constants/issueDict'
import { GROUP_OPTIONS } from '@/utils/listGrouping'

/** 项目成员（负责人候选项）的最小结构 */
export interface FilterMemberOption {
  userId: string
  displayName: string
}

const props = withDefaults(defineProps<{
  /** 页面持有的筛选状态（就地修改，改动后 emit('change')） */
  model: IssueFilterState
  statuses?: IssueStatusVO[]
  tags?: LabelVO[]
  sprints?: SprintVO[]
  /** 负责人候选项：项目成员 */
  members?: FilterMemberOption[]
  /** 创建者候选项：全局用户（报告人可能不是项目成员） */
  users?: UserOption[]
  /** 是否暴露「严重度」（只有缺陷适用：后端仅对 BUG 生效） */
  showSeverity?: boolean
  /** 是否显示「分组」下拉（只有列表视图支持分组） */
  showGroup?: boolean
  /** 筛选方案的持久化命名空间，一般传 `${projectId}:${页面 key}` */
  schemeScope: string
}>(), {
  statuses: () => [],
  tags: () => [],
  sprints: () => [],
  members: () => [],
  users: () => [],
  showSeverity: false,
  showGroup: true,
  schemeScope: 'default'
})

const emit = defineEmits<{ (e: 'change'): void }>()

type SelectKey = 'statusId' | 'priority' | 'severity' | 'labelId' | 'sprintId' | 'assigneeId' | 'reporterId'
type RangeKey = 'created' | 'updated' | 'due'
type FieldKey = SelectKey | 'overdue'

interface FieldOption { value: string; label: string }
interface FieldDef { key: FieldKey; label: string; group: string; options: FieldOption[] }
interface RangeDef { key: RangeKey; label: string; group: string }

/** 时间区间的两端字段名：区间在界面上一行，落库是两个参数（类型收窄到字符串字段，便于直接读写） */
type RangeFromKey = 'createdFrom' | 'updatedFrom' | 'dueFrom'
type RangeToKey = 'createdTo' | 'updatedTo' | 'dueTo'
const RANGE_KEYS: Record<RangeKey, { from: RangeFromKey; to: RangeToKey }> = {
  created: { from: 'createdFrom', to: 'createdTo' },
  updated: { from: 'updatedFrom', to: 'updatedTo' },
  due: { from: 'dueFrom', to: 'dueTo' }
}

const activeCount = computed(() => countActiveConditions(props.model))

/** 负责人候选项：项目成员；创建者候选项：全局用户优先，没有就退回成员 */
const memberOptions = computed<FieldOption[]>(() =>
  props.members.map((m) => ({ value: m.userId, label: m.displayName }))
)
const userOptions = computed<FieldOption[]>(() =>
  props.users.length
    ? props.users.map((u) => ({ value: u.id, label: u.displayName }))
    : memberOptions.value
)

/** 下拉型字段：顺序即面板里的展示顺序 */
const selectFields = computed<FieldDef[]>(() => {
  const list: FieldDef[] = [
    { key: 'statusId', label: '状态', group: '基础字段', options: props.statuses.map((s) => ({ value: s.id, label: s.name })) },
    { key: 'priority', label: '优先级', group: '基础字段', options: PRIORITY_OPTIONS.map((p) => ({ value: p.value, label: p.label })) }
  ]
  if (props.showSeverity) {
    list.push({ key: 'severity', label: '严重度', group: '基础字段', options: SEVERITY_OPTIONS.map((s) => ({ value: s.value, label: s.label })) })
  }
  list.push({ key: 'labelId', label: '标签', group: '基础字段', options: props.tags.map((t) => ({ value: t.id, label: t.name })) })
  list.push({ key: 'assigneeId', label: '负责人', group: '人员', options: memberOptions.value })
  list.push({ key: 'reporterId', label: '创建者', group: '人员', options: userOptions.value })
  list.push({ key: 'sprintId', label: '迭代', group: '计划', options: props.sprints.map((s) => ({ value: s.id, label: s.name })) })
  list.push({ key: 'overdue', label: '只看逾期', group: '计划', options: [] })
  return list
})

const rangeFields: RangeDef[] = [
  { key: 'created', label: '创建时间', group: '时间' },
  { key: 'updated', label: '更新时间', group: '时间' },
  { key: 'due', label: '计划完成', group: '时间' }
]

const fieldSearch = ref('')

/** 面板开关自己持有：这样 Esc 能关、套用方案后能自动收起 */
const panelOpen = ref(false)
const searchInput = ref<HTMLInputElement | null>(null)

function onEsc(e: KeyboardEvent) {
  if (e.key === 'Escape' && panelOpen.value) panelOpen.value = false
}

onMounted(() => window.addEventListener('keydown', onEsc))
onBeforeUnmount(() => window.removeEventListener('keydown', onEsc))

// 打开面板就把焦点放到搜索框：条件多的时候用户第一件事就是打字找字段
watch(panelOpen, (open) => {
  if (!open) {
    fieldSearch.value = ''
    return
  }
  nextTick(() => searchInput.value?.focus())
})

/** 面板里的字段分组：按搜索词过滤后仍保留分组标题，字段多了也能快速找到 */
const sections = computed(() => {
  const kw = fieldSearch.value.trim().toLowerCase()
  const hit = (label: string) => !kw || label.toLowerCase().includes(kw)
  const groups: Array<{ title: string; fields: FieldDef[]; ranges: RangeDef[] }> = []
  const ensure = (title: string) => {
    const found = groups.find((g) => g.title === title)
    if (found) return found
    const created = { title, fields: [] as FieldDef[], ranges: [] as RangeDef[] }
    groups.push(created)
    return created
  }
  for (const f of selectFields.value) {
    if (hit(f.label)) ensure(f.group).fields.push(f)
  }
  for (const r of rangeFields) {
    if (hit(r.label)) ensure(r.group).ranges.push(r)
  }
  return groups
})

function stringOf(key: SelectKey): string {
  return props.model[key] || ''
}

function rangeOf(key: RangeKey): { from: string; to: string } {
  const meta = RANGE_KEYS[key]
  return { from: props.model[meta.from], to: props.model[meta.to] }
}

function optionLabel(key: FieldKey, value: string): string {
  if (key === 'overdue') return '是'
  return selectFields.value.find((f) => f.key === key)?.options.find((o) => o.value === value)?.label || value
}

/** 已生效的条件（chip 数据源）：值为空即视为未筛选，不占位置 */
const conditions = computed(() => {
  const out: Array<{ key: string; label: string; value: string; clear: () => void }> = []
  for (const f of selectFields.value) {
    if (f.key === 'overdue') {
      if (props.model.overdue) out.push({ key: 'overdue', label: f.label, value: '', clear: () => clearField('overdue') })
      continue
    }
    const value = stringOf(f.key)
    if (value) out.push({ key: f.key, label: f.label, value: optionLabel(f.key, value), clear: () => clearField(f.key) })
  }
  for (const r of rangeFields) {
    const { from, to } = rangeOf(r.key)
    if (!from && !to) continue
    const value = from && to ? `${from} ~ ${to}` : from ? `≥ ${from}` : `≤ ${to}`
    out.push({ key: `range:${r.key}`, label: r.label, value, clear: () => clearRange(r.key) })
  }
  return out
})

function apply() {
  emit('change')
}

function onSelect(key: SelectKey, e: Event) {
  props.model[key] = (e.target as HTMLSelectElement).value
  apply()
}

function onToggleOverdue(e: Event) {
  props.model.overdue = (e.target as HTMLInputElement).checked
  apply()
}

function onRangeInput(key: RangeKey, side: 'from' | 'to', e: Event) {
  const meta = RANGE_KEYS[key]
  props.model[side === 'from' ? meta.from : meta.to] = (e.target as HTMLInputElement).value
  apply()
}

function clearField(key: FieldKey) {
  if (key === 'overdue') props.model.overdue = false
  else props.model[key] = ''
  apply()
}

function clearRange(key: RangeKey) {
  const meta = RANGE_KEYS[key]
  props.model[meta.from] = ''
  props.model[meta.to] = ''
  apply()
}

function clearAll() {
  props.model.statusId = ''
  props.model.priority = ''
  props.model.severity = ''
  props.model.labelId = ''
  props.model.sprintId = ''
  props.model.assigneeId = ''
  props.model.reporterId = ''
  props.model.overdue = false
  for (const r of rangeFields) {
    props.model[RANGE_KEYS[r.key].from] = ''
    props.model[RANGE_KEYS[r.key].to] = ''
  }
  apply()
}

// ---- 筛选方案：把整套条件存进 localStorage，下次一键套用 ----

interface SavedScheme {
  id: string
  name: string
  values: Omit<IssueFilterState, 'groupBy'>
}

const schemes = ref<SavedScheme[]>([])
const schemeName = ref('')

const schemeStorageKey = computed(() => `kanban:filterSchemes:${props.schemeScope}`)

function loadSchemes() {
  try {
    const raw = localStorage.getItem(schemeStorageKey.value)
    schemes.value = raw ? (JSON.parse(raw) as SavedScheme[]) : []
  } catch {
    // 历史脏数据直接丢弃，筛选方案属于"便利功能"，不该因为它打不开筛选面板
    schemes.value = []
  }
}

watch(schemeStorageKey, loadSchemes, { immediate: true })

function persistSchemes() {
  try {
    localStorage.setItem(schemeStorageKey.value, JSON.stringify(schemes.value))
  } catch {
    ElMessage.warning('筛选方案保存失败（浏览器存储不可用）')
  }
}

/** 取当前条件的快照（不含分组维度）；字段列全，漏一个编译器会报错 */
function snapshot(): Omit<IssueFilterState, 'groupBy'> {
  const s = props.model
  return {
    statusId: s.statusId,
    priority: s.priority,
    severity: s.severity,
    labelId: s.labelId,
    sprintId: s.sprintId,
    assigneeId: s.assigneeId,
    reporterId: s.reporterId,
    createdFrom: s.createdFrom,
    createdTo: s.createdTo,
    updatedFrom: s.updatedFrom,
    updatedTo: s.updatedTo,
    dueFrom: s.dueFrom,
    dueTo: s.dueTo,
    overdue: s.overdue
  }
}

function saveScheme() {
  const name = schemeName.value.trim()
  if (!name) {
    ElMessage.warning('请先给筛选方案起个名字')
    return
  }
  if (!activeCount.value) {
    ElMessage.warning('当前还没有筛选条件，没有可保存的内容')
    return
  }
  const values = snapshot()
  // 同名覆盖：用户反复微调后重存，不该堆出一串同名方案
  const hit = schemes.value.find((s) => s.name === name)
  if (hit) hit.values = values
  else schemes.value.unshift({ id: `${Date.now()}`, name, values })
  persistSchemes()
  schemeName.value = ''
  ElMessage.success(`已保存筛选方案「${name}」`)
}

function applyScheme(scheme: SavedScheme) {
  // 分组维度不属于筛选条件，套用方案时保留用户当前的分组选择
  Object.assign(props.model, { ...scheme.values, groupBy: props.model.groupBy })
  apply()
  panelOpen.value = false
  ElMessage.success(`已套用筛选方案「${scheme.name}」`)
}

function removeScheme(scheme: SavedScheme) {
  schemes.value = schemes.value.filter((s) => s.id !== scheme.id)
  persistSchemes()
}
</script>

<template>
  <div class="filter-bar">
    <el-popover v-model:visible="panelOpen" trigger="click" placement="bottom-start" :width="400" :show-arrow="false" popper-class="issue-filter-popper">
      <template #reference>
        <button
          class="filter-trigger"
          :class="{ 'is-active': activeCount > 0 }"
          :title="activeCount ? `已应用 ${activeCount} 个筛选条件` : '添加筛选条件'"
        >
          <Filter :size="14" />
          <span>筛选</span>
          <span v-if="activeCount" class="filter-badge">{{ activeCount }}</span>
        </button>
      </template>

      <div class="max-h-[460px] overflow-auto">
        <div class="p-2 pb-1">
          <input
            ref="searchInput"
            v-model="fieldSearch"
            class="w-full rounded-md bg-surface-page px-2 py-1.5 text-xs text-ink outline-none placeholder:text-ink-placeholder"
            placeholder="搜索筛选项"
          />
        </div>

        <div v-for="sec in sections" :key="sec.title">
          <div class="filter-panel-title">{{ sec.title }}</div>
          <div v-for="f in sec.fields" :key="f.key" class="filter-field-row">
            <span class="shrink-0 text-xs text-ink-regular">{{ f.label }}</span>
            <input
              v-if="f.key === 'overdue'"
              type="checkbox"
              class="h-3.5 w-3.5 accent-[var(--primary)]"
              :checked="model.overdue"
              @change="onToggleOverdue"
            />
            <select
              v-else
              class="filter-select max-w-[180px]"
              :value="stringOf(f.key as SelectKey)"
              @change="onSelect(f.key as SelectKey, $event)"
            >
              <option value="">不限</option>
              <option v-for="o in f.options" :key="o.value" :value="o.value">{{ o.label }}</option>
            </select>
          </div>
          <!-- 时间区间：一行两个日期，两端都含当天 -->
          <div v-for="r in sec.ranges" :key="r.key" class="filter-field-row">
            <span class="shrink-0 text-xs text-ink-regular">{{ r.label }}</span>
            <span class="flex items-center gap-1">
              <input
                type="date"
                class="filter-date"
                :value="rangeOf(r.key).from"
                :title="`${r.label}起始（含当天）`"
                @change="onRangeInput(r.key, 'from', $event)"
              />
              <span class="text-xs text-ink-placeholder">~</span>
              <input
                type="date"
                class="filter-date"
                :value="rangeOf(r.key).to"
                :title="`${r.label}截止（含当天）`"
                @change="onRangeInput(r.key, 'to', $event)"
              />
            </span>
          </div>
        </div>

        <div class="border-t border-line-lighter p-2">
          <div class="filter-panel-title is-first">筛选方案</div>
          <div class="flex items-center gap-1">
            <input
              v-model="schemeName"
              class="min-w-0 flex-1 rounded-md bg-surface-page px-2 py-1 text-xs text-ink outline-none placeholder:text-ink-placeholder"
              placeholder="把当前条件存为方案"
              @keydown.enter="saveScheme"
            />
            <button class="rounded-md bg-brand px-2 py-1 text-xs text-white transition hover:bg-brand-dark" @click="saveScheme">保存</button>
          </div>
          <div v-if="schemes.length" class="mt-1 flex flex-col">
            <div
              v-for="s in schemes"
              :key="s.id"
              class="group flex items-center justify-between gap-2 rounded-md px-1 py-1 transition hover:bg-surface-page"
            >
              <button class="truncate text-left text-xs text-ink-regular transition hover:text-brand" :title="`套用「${s.name}」`" @click="applyScheme(s)">
                {{ s.name }}
              </button>
              <button class="shrink-0 text-ink-placeholder opacity-0 transition group-hover:opacity-100 hover:text-danger" title="删除方案" @click="removeScheme(s)">
                <Trash2 :size="12" />
              </button>
            </div>
          </div>
          <div v-else class="mt-1 text-[11px] text-ink-placeholder">还没有保存的筛选方案</div>
        </div>
      </div>
    </el-popover>

    <!-- 已生效条件：贴在条上，单条可删、整体可清空 -->
    <span v-for="c in conditions" :key="c.key" class="filter-chip">
      <span class="text-ink-placeholder">{{ c.label }}</span>
      <span v-if="c.value" class="max-w-[220px] truncate font-medium">{{ c.value }}</span>
      <button class="filter-chip-x" :title="`移除${c.label}条件`" @click="c.clear()">
        <X :size="12" />
      </button>
    </span>
    <button v-if="activeCount" class="text-xs text-brand transition hover:underline" @click="clearAll">清空</button>

    <label v-if="showGroup" class="ml-auto flex items-center gap-1 text-xs text-ink-secondary">
      <Layers :size="13" />
      <span>分组</span>
      <select v-model="model.groupBy" class="filter-select" @change="apply">
        <option v-for="o in GROUP_OPTIONS" :key="o.value" :value="o.value">{{ o.label }}</option>
      </select>
    </label>
  </div>
</template>
