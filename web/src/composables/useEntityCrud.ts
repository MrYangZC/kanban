import { computed, ref, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { confirmRemove } from '@/utils/confirm'
import { isCancel, reportError } from '@/utils/error'

/**
 * 「表格 + 编辑弹窗」类配置页的通用骨架：列表加载、关键字过滤、
 * 新建/编辑共用一个 form、提交后重新拉取、删除带确认。
 *
 * <p>IterationsView 与 MilestonesView 的 script 部分逐字相同（只是字段名与接口不同），
 * 差异只有：接口、表单字段、关键字匹配哪些字段、实体名（提示语用）。</p>
 *
 * <p>调用方用解构重命名保持模板不变，例如：</p>
 * <pre>
 * const { rows: sprints, filtered: filteredSprints, ...rest } = useEntityCrud(projectId, opts)
 * </pre>
 *
 * <p>不适用于表单字段与行字段不同名的页面（如 RisksView 用 title 而非 name），
 * 那种页面套进来只会把差异塞进参数。</p>
 */
export interface EntityCrudOptions<T, F> {
  /** 实体名，用于「请输入{label}名称」「加载{label}失败」与删除确认框 */
  label: string
  fetch: (projectId: string) => Promise<T[]>
  create: (projectId: string, form: F) => Promise<unknown>
  update: (projectId: string, id: string, form: F) => Promise<unknown>
  remove: (projectId: string, id: string) => Promise<unknown>
  /** 新建时的空白表单 */
  emptyForm: () => F
  /** 行 → 编辑表单（后端返回 null 的字段在这里兜成 ''） */
  toForm: (row: T) => F
  /** 关键字参与匹配的字段值（大小写不敏感，任一命中即算）；null/undefined 视为不参与 */
  searchText: (row: T) => (string | null | undefined)[]
}

export function useEntityCrud<T extends { id: string; name: string }, F extends { name: string }>(
  projectId: () => string,
  options: EntityCrudOptions<T, F>
) {
  const rows = ref([]) as Ref<T[]>
  const loading = ref(false)
  const keyword = ref('')
  const dialogVisible = ref(false)
  const editing = ref(null) as Ref<T | null>
  const form = ref(options.emptyForm()) as Ref<F>

  const filtered = computed(() => {
    const kw = keyword.value.trim().toLowerCase()
    if (!kw) return rows.value
    return rows.value.filter((row) =>
      options.searchText(row).some((text) => (text || '').toLowerCase().includes(kw))
    )
  })

  async function load() {
    loading.value = true
    try {
      rows.value = await options.fetch(projectId())
    } catch (e: unknown) {
      reportError(e, `加载${options.label}失败`)
    } finally {
      loading.value = false
    }
  }

  function openCreate() {
    editing.value = null
    form.value = options.emptyForm()
    dialogVisible.value = true
  }

  function openEdit(row: T) {
    editing.value = row
    form.value = options.toForm(row)
    dialogVisible.value = true
  }

  async function submit() {
    if (!form.value.name.trim()) {
      ElMessage.warning(`请输入${options.label}名称`)
      return
    }
    try {
      if (editing.value) {
        await options.update(projectId(), editing.value.id, { ...form.value })
        ElMessage.success('已更新')
      } else {
        await options.create(projectId(), { ...form.value })
        ElMessage.success('已创建')
      }
      dialogVisible.value = false
      await load()
    } catch (e: unknown) {
      reportError(e, '保存失败')
    }
  }

  async function doDelete(row: T) {
    try {
      await confirmRemove(row.name, options.label)
      await options.remove(projectId(), row.id)
      ElMessage.success('已删除')
      await load()
    } catch (e: unknown) {
      if (!isCancel(e)) reportError(e, '删除失败')
    }
  }

  return {
    rows,
    loading,
    keyword,
    dialogVisible,
    editing,
    form,
    filtered,
    load,
    openCreate,
    openEdit,
    submit,
    doDelete
  }
}
