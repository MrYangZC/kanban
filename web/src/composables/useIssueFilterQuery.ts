import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

const STATUS_CATEGORY_LABEL: Record<string, string> = {
  TODO: '待处理',
  IN_PROGRESS: '处理中',
  DONE: '已完成'
}

/**
 * 度量页点统计数字跳转过来时携带的筛选（`?statusCategory=TODO|IN_PROGRESS|DONE` / `?overdue=1`）。
 *
 * <p>这段逻辑原本在 WorkItemListPage / TableView / DefectManageView 里逐字复制了三份：
 * 两个 ref、一个 label、一个清除函数、一个 query 同步 watch。</p>
 *
 * <p>筛选与页面上手选的筛选互不覆盖：这里只负责「来自度量页的那一层」，由顶部 chip 单独清除。</p>
 *
 * @param onChange 筛选变化后的重新加载回调（调用方通常在此重置页码并取数）
 */
export function useIssueFilterQuery(onChange: () => void) {
  const route = useRoute()
  const router = useRouter()

  const statusCategoryFilter = ref('')
  const overdueFilter = ref(false)

  const activeFilterLabel = computed(() => {
    if (overdueFilter.value) return '逾期'
    return STATUS_CATEGORY_LABEL[statusCategoryFilter.value] || ''
  })

  /** 进入页面时读一次 query */
  function syncFromQuery() {
    statusCategoryFilter.value = route.query.statusCategory ? String(route.query.statusCategory) : ''
    overdueFilter.value = route.query.overdue === '1'
  }

  function clearQueryFilter() {
    statusCategoryFilter.value = ''
    overdueFilter.value = false
    // 顺手把地址栏里的筛选参数去掉，免得刷新后又「复活」
    if (route.query.statusCategory || route.query.overdue) {
      router.replace({
        path: route.path,
        query: { ...route.query, statusCategory: undefined, overdue: undefined }
      })
    }
    onChange()
  }

  // 同一路由只改 query 时组件不会重建（例如从度量页带筛选进来后再点左侧导航），
  // 这里跟 query 双向同步，避免筛选「粘」在页面上出不去
  watch(
    () => [route.query.statusCategory, route.query.overdue],
    ([sc, od]) => {
      const nextSc = sc ? String(sc) : ''
      const nextOd = od === '1'
      if (nextSc === statusCategoryFilter.value && nextOd === overdueFilter.value) return
      statusCategoryFilter.value = nextSc
      overdueFilter.value = nextOd
      onChange()
    }
  )

  return {
    statusCategoryFilter,
    overdueFilter,
    activeFilterLabel,
    clearQueryFilter,
    syncFromQuery
  }
}
