import { test, expect, type APIRequestContext, type Page } from '@playwright/test'
import { API, loginApi, createProject, deleteProject, apiPost, apiPut, apiGet, apiDelete } from './helpers/api'
import { gotoAsAdmin } from './helpers/session'

/**
 * 08 项目规划域（迭代 / 里程碑 / 风险 / 度量 / 设置）
 *
 * 前端事实（对照源码校准）：
 *   - 迭代/里程碑/风险 三页同构：SimpleListView + DataTable，标题在 h1.toolbar-title，
 *     空态由 EmptyRow 渲染（表格仍在，只是出现 td.empty-row），行选择器 tr.data-tr
 *   - 新建入口统一 .btn-create；弹窗是 el-dialog，底部主按钮文案「确定」
 *   - 迭代名称有 placeholder「如 Sprint 1」；里程碑名称、风险标题无 placeholder，取弹窗内首个 input
 *   - 删除走 ElMessageBox 二次确认（zh-cn 下按钮为「确定/取消」）
 *   - 度量页无增删改，指标卡文案：总数/进行中/已完成/逾期 + 状态分布/燃尽图
 *   - 设置页两张卡：基本信息（原生 input，placeholder「项目名称」）与成员管理（「共 N 人」）
 */

const PREFIX = 'TEST_PLAN_'

/** 按名称清理资源，避免用例中途失败后残留影响「空态」断言 */
async function purgeByName(
  request: APIRequestContext, projectId: string, resource: string, name: string
) {
  // 必须走 apiGet/apiDelete：裸 request.get 不带登录态且不会自动重登，
  // 一旦 401 就静默返回空数组，清理与「删除后接口不可见」断言都会变成假通过
  const rows = await apiGet<Array<{ id: string; name?: string; title?: string }>>(
    request, `${API}/projects/${projectId}/${resource}`, `查询 ${resource}`
  )
  for (const row of rows ?? []) {
    if (row.name === name || row.title === name) {
      await apiDelete<void>(request, `${API}/projects/${projectId}/${resource}/${row.id}`, `清理 ${name}`)
    }
  }
}

async function confirmMessageBox(page: Page) {
  const box = page.locator('.el-message-box')
  await expect(box).toBeVisible({ timeout: 5000 })
  await box.getByRole('button', { name: '确定' }).click()
}

function rowOf(page: Page, text: string) {
  return page.locator('tr.data-tr').filter({ hasText: text })
}

test.describe('08 项目规划域', () => {
  let projectId = ''

  test.beforeAll(async ({ request }) => {
    await loginApi(request)
    projectId = await createProject(request, 'TPLN', `${PREFIX}PROJ`)
  })

  test.afterAll(async ({ request }) => {
    if (projectId) await deleteProject(request, projectId)
  })

  // ---------- 迭代 ----------

  test('TC-501 迭代页加载且初始为空态', async ({ page }) => {
    await gotoAsAdmin(page, `/projects/${projectId}/iterations`)
    await expect(page.locator('h1.toolbar-title')).toHaveText('迭代')
    await expect(page.locator('td.empty-row')).toContainText('暂无迭代')
  })

  test('TC-502 新建迭代成功并出现在列表', async ({ request, page }) => {
    const name = `${PREFIX}SPRINT_A`
    await gotoAsAdmin(page, `/projects/${projectId}/iterations`)
    await page.locator('.btn-create').click()

    const dialog = page.locator('.el-dialog')
    await expect(dialog).toBeVisible({ timeout: 5000 })
    await expect(dialog).toContainText('新建迭代')
    await dialog.getByPlaceholder('如 Sprint 1').fill(name)
    await dialog.getByRole('button', { name: '确定' }).click()

    await expect(page.locator('.el-message').last()).toContainText('已创建')
    await expect(rowOf(page, name)).toBeVisible({ timeout: 10000 })
    await expect(rowOf(page, name)).toContainText('规划中')

    await purgeByName(request, projectId, 'sprints', name)
  })

  test('TC-503 迭代名称必填校验拦截', async ({ page }) => {
    await gotoAsAdmin(page, `/projects/${projectId}/iterations`)
    await page.locator('.btn-create').click()

    const dialog = page.locator('.el-dialog')
    await expect(dialog).toBeVisible({ timeout: 5000 })
    await dialog.getByRole('button', { name: '确定' }).click()

    await expect(page.locator('.el-message').last()).toContainText('请输入迭代名称')
    await expect(dialog).toBeVisible()
    await dialog.getByRole('button', { name: '取消' }).click()
    await expect(dialog).toBeHidden()
  })

  test('TC-504 迭代状态流转：开始后为进行中，完成后为已完成', async ({ request, page }) => {
    const name = `${PREFIX}SPRINT_FLOW`
    await apiPost(request, `${API}/projects/${projectId}/sprints`, `创建迭代 ${name}`, { name })

    await gotoAsAdmin(page, `/projects/${projectId}/iterations`)
    const row = rowOf(page, name)
    await expect(row).toBeVisible({ timeout: 10000 })

    await row.getByRole('button', { name: '开始' }).click()
    await expect(page.locator('.el-message').last()).toContainText('迭代已开始')
    await expect(rowOf(page, name)).toContainText('进行中', { timeout: 10000 })

    await rowOf(page, name).getByRole('button', { name: '完成' }).click()
    await expect(page.locator('.el-message').last()).toContainText('迭代已完成')
    await expect(rowOf(page, name)).toContainText('已完成', { timeout: 10000 })

    await purgeByName(request, projectId, 'sprints', name)
  })

  test('TC-505 删除迭代需二次确认，取消则保留', async ({ request, page }) => {
    const name = `${PREFIX}SPRINT_DEL`
    await apiPost(request, `${API}/projects/${projectId}/sprints`, `创建迭代 ${name}`, { name })

    await gotoAsAdmin(page, `/projects/${projectId}/iterations`)
    await expect(rowOf(page, name)).toBeVisible({ timeout: 10000 })

    await rowOf(page, name).getByRole('button', { name: '删除' }).click()
    const box = page.locator('.el-message-box')
    await expect(box).toBeVisible({ timeout: 5000 })
    await expect(box).toContainText(name)
    await box.getByRole('button', { name: '取消' }).click()
    await expect(rowOf(page, name)).toBeVisible()

    await rowOf(page, name).getByRole('button', { name: '删除' }).click()
    await confirmMessageBox(page)
    await expect(page.locator('.el-message').last()).toContainText('已删除')
    await expect(rowOf(page, name)).toHaveCount(0, { timeout: 10000 })

    await purgeByName(request, projectId, 'sprints', name)
  })

  // ---------- 里程碑 ----------

  test('TC-506 里程碑页加载且新建成功', async ({ request, page }) => {
    const name = `${PREFIX}MILE_A`
    await gotoAsAdmin(page, `/projects/${projectId}/milestones`)
    await expect(page.locator('h1.toolbar-title')).toHaveText('里程碑')
    await expect(page.locator('td.empty-row')).toContainText('暂无里程碑')

    await page.locator('.btn-create').click()
    const dialog = page.locator('.el-dialog')
    await expect(dialog).toBeVisible({ timeout: 5000 })
    await expect(dialog).toContainText('新建里程碑')
    await dialog.locator('input').first().fill(name)
    await dialog.getByRole('button', { name: '确定' }).click()

    await expect(page.locator('.el-message').last()).toContainText('已创建')
    await expect(rowOf(page, name)).toBeVisible({ timeout: 10000 })

    await purgeByName(request, projectId, 'milestones', name)
  })

  test('TC-507 里程碑名称必填校验拦截', async ({ page }) => {
    await gotoAsAdmin(page, `/projects/${projectId}/milestones`)
    await page.locator('.btn-create').click()

    const dialog = page.locator('.el-dialog')
    await expect(dialog).toBeVisible({ timeout: 5000 })
    await dialog.getByRole('button', { name: '确定' }).click()
    await expect(page.locator('.el-message').last()).toContainText('请输入里程碑名称')
    await dialog.getByRole('button', { name: '取消' }).click()
  })

  test('TC-508 删除里程碑需二次确认且真正删除', async ({ request, page }) => {
    const name = `${PREFIX}MILE_DEL`
    await apiPost(request, `${API}/projects/${projectId}/milestones`, `创建里程碑 ${name}`, { name })

    await gotoAsAdmin(page, `/projects/${projectId}/milestones`)
    await expect(rowOf(page, name)).toBeVisible({ timeout: 10000 })

    await rowOf(page, name).getByRole('button', { name: '删除' }).click()
    await confirmMessageBox(page)
    await expect(page.locator('.el-message').last()).toContainText('已删除')
    await expect(rowOf(page, name)).toHaveCount(0, { timeout: 10000 })

    const rows = await apiGet<Array<{ name: string }>>(
      request, `${API}/projects/${projectId}/milestones`, '查询里程碑列表'
    )
    expect(rows.some((m) => m.name === name), '删除后接口不应再返回该里程碑').toBe(false)
  })

  // ---------- 风险 ----------

  test('TC-509 风险页加载且新建成功（默认级别/状态）', async ({ request, page }) => {
    const title = `${PREFIX}RISK_A`
    await gotoAsAdmin(page, `/projects/${projectId}/risks`)
    await expect(page.locator('h1.toolbar-title')).toHaveText('风险')
    await expect(page.locator('td.empty-row')).toContainText('暂无风险')

    await page.locator('.btn-create').click()
    const dialog = page.locator('.el-dialog')
    await expect(dialog).toBeVisible({ timeout: 5000 })
    await expect(dialog).toContainText('新建风险')
    await dialog.locator('input').first().fill(title)
    await dialog.getByRole('button', { name: '确定' }).click()

    await expect(page.locator('.el-message').last()).toContainText('已创建')
    const row = rowOf(page, title)
    await expect(row).toBeVisible({ timeout: 10000 })
    await expect(row).toContainText('中')
    await expect(row).toContainText('待处理')

    await purgeByName(request, projectId, 'risks', title)
  })

  test('TC-510 风险标题必填校验拦截', async ({ page }) => {
    await gotoAsAdmin(page, `/projects/${projectId}/risks`)
    await page.locator('.btn-create').click()

    const dialog = page.locator('.el-dialog')
    await expect(dialog).toBeVisible({ timeout: 5000 })
    await dialog.getByRole('button', { name: '确定' }).click()
    await expect(page.locator('.el-message').last()).toContainText('请输入风险标题')
    await dialog.getByRole('button', { name: '取消' }).click()
  })

  test('TC-511 编辑风险级别后列表同步更新', async ({ request, page }) => {
    const title = `${PREFIX}RISK_EDIT`
    await apiPost(request, `${API}/projects/${projectId}/risks`, `创建风险 ${title}`, { title })

    await gotoAsAdmin(page, `/projects/${projectId}/risks`)
    const row = rowOf(page, title)
    await expect(row).toBeVisible({ timeout: 10000 })

    await row.getByRole('button', { name: '编辑' }).click()
    const dialog = page.locator('.el-dialog')
    await expect(dialog).toBeVisible({ timeout: 5000 })

    await dialog.locator('.el-select').first().click()
    await page.locator('.el-select-dropdown__item').filter({ hasText: '高' }).first().click()
    await dialog.getByRole('button', { name: '确定' }).click()

    await expect(page.locator('.el-message').last()).toContainText('已更新')
    await expect(rowOf(page, title)).toContainText('高', { timeout: 10000 })

    await purgeByName(request, projectId, 'risks', title)
  })

  test('TC-512 删除风险需二次确认且真正删除', async ({ request, page }) => {
    const title = `${PREFIX}RISK_DEL`
    await apiPost(request, `${API}/projects/${projectId}/risks`, `创建风险 ${title}`, { title })

    await gotoAsAdmin(page, `/projects/${projectId}/risks`)
    await expect(rowOf(page, title)).toBeVisible({ timeout: 10000 })

    await rowOf(page, title).getByRole('button', { name: '删除' }).click()
    await confirmMessageBox(page)
    await expect(page.locator('.el-message').last()).toContainText('已删除')
    await expect(rowOf(page, title)).toHaveCount(0, { timeout: 10000 })

    const rows = await apiGet<Array<{ title: string }>>(
      request, `${API}/projects/${projectId}/risks`, '查询风险列表'
    )
    expect(rows.some((r) => r.title === title), '删除后接口不应再返回该风险').toBe(false)
  })

  // ---------- 度量 ----------

  test('TC-513 度量页渲染概览指标与图表面板', async ({ page }) => {
    await gotoAsAdmin(page, `/projects/${projectId}/metrics`)
    await expect(page.getByText('加载报表中…')).toHaveCount(0, { timeout: 15000 })

    // 分类型概览卡片下排的可点数字（ProjectMetricsPanel.TYPE_FILTERS）
    for (const label of ['全部', '待处理', '处理中', '已完成', '逾期']) {
      await expect(page.getByText(label, { exact: true }).first()).toBeVisible({ timeout: 10000 })
    }
    for (const panel of ['状态分布', '优先级分布', '燃尽图', '按负责人工作量', '周期时间']) {
      await expect(page.getByText(panel, { exact: false }).first()).toBeVisible({ timeout: 10000 })
    }
  })

  // ---------- 设置 ----------

  test('TC-514 设置页展示基本信息与成员管理', async ({ page }) => {
    await gotoAsAdmin(page, `/projects/${projectId}/settings`)
    await expect(page.getByText('基本信息').first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByText('成员管理').first()).toBeVisible()
    await expect(page.getByText(/共 \d+ 人/).first()).toBeVisible()
    await expect(page.getByPlaceholder('项目名称')).toHaveValue(new RegExp(PREFIX))
  })

  test('TC-515 项目名称必填校验与保存成功', async ({ request, page }) => {
    const original = `${PREFIX}PROJ`
    const edited = `${PREFIX}PROJ_EDITED`
    await gotoAsAdmin(page, `/projects/${projectId}/settings`)

    const nameInput = page.getByPlaceholder('项目名称')
    await expect(nameInput).toHaveValue(original, { timeout: 10000 })

    await nameInput.fill('')
    await page.getByRole('button', { name: '保存' }).click()
    await expect(page.locator('.el-message').last()).toContainText('项目名称不能为空')

    await nameInput.fill(edited)
    await page.getByRole('button', { name: '保存' }).click()
    await expect(page.locator('.el-message').last()).toContainText('设置已保存')

    const project = await apiGet<{ name: string }>(request, `${API}/projects/${projectId}`, '查询项目详情')
    expect(project.name, '保存后接口应返回新项目名称').toBe(edited)

    await apiPut(request, `${API}/projects/${projectId}`, '还原项目名称', { name: original })
  })
})
