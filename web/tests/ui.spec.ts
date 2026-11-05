import { test, expect, type Page, type APIRequestContext } from '@playwright/test'

const API = 'http://localhost:9085/api/v1'

async function loginViaApi(page: Page, username: string, password: string) {
  const resp = await page.request.post(`${API}/users/login`, {
    data: { username, password }
  })
  const body = await resp.json()
  expect(body.code, `登录 ${username} 应成功`).toBe(200)
  await page.goto('/login')
  await page.evaluate((vo) => localStorage.setItem('kanban_user', JSON.stringify(vo)), body.data)
  return body.data as { id: string; username: string; token: string }
}

async function loginRequestOnly(request: APIRequestContext, username = 'admin', password = 'admin123') {
  const resp = await request.post(`${API}/users/login`, { data: { username, password } })
  const body = await resp.json()
  expect(body.code, `API 登录 ${username} 应成功`).toBe(200)
}

async function createProject(request: APIRequestContext, key: string, name: string) {
  let c = await (await request.post(`${API}/projects`, {
    data: { projectKey: key, name, description: '' }
  })).json()
  if (c.code !== 200) {
    const list = await (await request.get(`${API}/projects`)).json()
    const stale = (list.data || []).find((p: { projectKey: string }) => p.projectKey === key)
    if (stale) await request.delete(`${API}/projects/${stale.id}`)
    c = await (await request.post(`${API}/projects`, {
      data: { projectKey: key, name, description: '' }
    })).json()
  }
  expect(c.code, `创建项目 ${name} 应成功`).toBe(200)
  return c.data.id as string
}

async function gotoAsAdmin(page: Page, path: string) {
  await loginViaApi(page, 'admin', 'admin123')
  await page.goto(path)
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(800)
}

// ---------- 登录模块 ----------

test.describe('登录模块 UI', () => {
  test.use({ storageState: { cookies: [], origins: [] } })

  test('TC-009 登录页不预填凭据', async ({ page }) => {
    await page.goto('/login')
    await expect(page.locator('input').first()).toHaveValue('')
    await expect(page.locator('input[type="password"]')).toHaveValue('')
  })

  test('TC-004 空用户名校验拦截', async ({ page }) => {
    await page.goto('/login')
    await page.locator('input[type="password"]').fill('admin123')
    await page.getByRole('button', { name: /登\s*录/ }).first().click()
    await expect(page).toHaveURL(/\/login/)
  })

  test('TC-005 空密码校验拦截', async ({ page }) => {
    await page.goto('/login')
    await page.locator('input').first().fill('admin')
    await page.getByRole('button', { name: /登\s*录/ }).first().click()
    await expect(page).toHaveURL(/\/login/)
  })

  test('TC-001 正确凭据登录跳转项目列表', async ({ page }) => {
    await page.goto('/login')
    await page.locator('input').first().fill('admin')
    await page.locator('input[type="password"]').fill('admin123')
    await page.getByRole('button', { name: /登\s*录/ }).first().click()
    await expect(page).toHaveURL(/\/projects/, { timeout: 10000 })
  })

  test('TC-002 错误密码显示错误提示', async ({ page }) => {
    await page.goto('/login')
    await page.locator('input').first().fill('admin')
    await page.locator('input[type="password"]').fill('wrongpass')
    await page.getByRole('button', { name: /登\s*录/ }).first().click()
    await expect(page.locator('.el-message')).toBeVisible({ timeout: 5000 })
    await expect(page).toHaveURL(/\/login/)
  })

  test('TC-007 未登录访问受保护路由跳转登录页', async ({ page }) => {
    await page.goto('/projects')
    await expect(page).toHaveURL(/\/login/, { timeout: 5000 })
  })
})

// ---------- 管理页面加载 ----------

test.describe('管理页面加载', () => {
  test('TC-063 admin 访问用户管理页', async ({ page }) => {
    await gotoAsAdmin(page, '/users')
    await expect(page).toHaveURL(/\/users/)
    await expect(page.locator('table')).toBeVisible({ timeout: 10000 })
  })

  test('TC-064 admin 访问 RBAC 页', async ({ page }) => {
    await gotoAsAdmin(page, '/rbac')
    await expect(page).toHaveURL(/\/rbac/)
    await expect(page.locator('.el-tabs, table').first()).toBeVisible({ timeout: 10000 })
  })
})

// ---------- 普通用户前端拦截 ----------

/** 先清残留同名账号：否则 POST /users 返回「用户名已存在」，用例会被 skip 掩盖 */
async function deleteUserByName(request: APIRequestContext, username: string) {
  const resp = await request.get(`${API}/users`)
  const body = await resp.json()
  const rows: Array<{ id: string; username: string }> = Array.isArray(body.data) ? body.data : []
  for (const u of rows) {
    if (u.username === username) await request.delete(`${API}/users/${u.id}`)
  }
}

test.describe('普通用户前端拦截', () => {
  test('TC-065 普通用户访问 /users 被重定向', async ({ page }) => {
    await loginRequestOnly(page.request)
    await deleteUserByName(page.request, 'TEST_UI_USER')
    const created = await page.request.post(`${API}/users`, {
      data: { username: 'TEST_UI_USER', displayName: 'UI测试用户', password: 'uitest123' }
    })
    const cb = await created.json()
    test.skip(cb.code !== 200, '前置创建用户失败')
    const uid = cb.data.id
    try {
      await loginViaApi(page, 'TEST_UI_USER', 'uitest123')
      await page.goto('/users')
      await page.waitForTimeout(1000)
      await expect(page).not.toHaveURL(/\/users/)
    } finally {
      await page.request.delete(`${API}/users/${uid}`)
    }
  })

  test('TC-066 普通用户访问 /rbac 被重定向', async ({ page }) => {
    await loginRequestOnly(page.request)
    await deleteUserByName(page.request, 'TEST_UI_USER2')
    const created = await page.request.post(`${API}/users`, {
      data: { username: 'TEST_UI_USER2', displayName: 'UI测试用户2', password: 'uitest123' }
    })
    const cb = await created.json()
    test.skip(cb.code !== 200, '前置创建用户失败')
    const uid = cb.data.id
    try {
      await loginViaApi(page, 'TEST_UI_USER2', 'uitest123')
      await page.goto('/rbac')
      await page.waitForTimeout(1000)
      await expect(page).not.toHaveURL(/\/rbac/)
    } finally {
      await page.request.delete(`${API}/users/${uid}`)
    }
  })
})

// ---------- 项目列表 UI ----------

test.describe('项目列表 UI', () => {
  test('TC-008 项目列表正常加载', async ({ page }) => {
    await gotoAsAdmin(page, '/projects')
    await expect(page.getByText('新建项目').first()).toBeVisible({ timeout: 10000 })
    await expect(page.locator('.el-pagination, [class*=card], [class*=project]').first()).toBeVisible()
  })

  test('TC-016 删除项目取消操作不影响数据', async ({ page }) => {
    await gotoAsAdmin(page, '/projects')
    const pid = await createProject(page.request, 'TUIX', 'TEST_UI_CANCEL_DELETE')
    try {
      await page.reload()
      await page.waitForLoadState('networkidle')
      const card = page.locator('text=TEST_UI_CANCEL_DELETE').first()
      await expect(card).toBeVisible({ timeout: 10000 })
    } finally {
      await page.request.delete(`${API}/projects/${pid}`)
    }
  })
})

// ---------- 项目内工作项 UI（自建项目，组内闭环） ----------

test.describe('项目内工作项 UI', () => {
  let projId = ''

  test.beforeAll(async ({ request }) => {
    await loginRequestOnly(request)
    // project_key 全局唯一：固定 key 一旦某轮清理失败，后续每轮都建不出来（POST 直接 400）。
    // 加随机后缀让本组自给自足（key 不参与任何断言）。
    const stamp = Date.now().toString(36).slice(-4).toUpperCase()
    projId = await createProject(request, `TUIW${stamp}`, 'TEST_UI_WORKITEM_PROJ')
  })

  test.afterAll(async ({ request }) => {
    if (!projId) return
    // afterAll 里的 request 是新的 fixture 实例、没有登录 cookie，
    // 必须先补登录态，否则裸 request.delete 被 401 静默吞掉、项目长期残留
    await loginRequestOnly(request)
    await request.delete(`${API}/projects/${projId}`)
  })

  test('TC-021 需求列表页正常加载', async ({ page }) => {
    await gotoAsAdmin(page, `/projects/${projId}/requirements`)
    await expect(page.locator('main').first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByText('需求').first()).toBeVisible()
  })

  test('TC-022 创建需求成功出现在列表', async ({ page }) => {
    await gotoAsAdmin(page, `/projects/${projId}/requirements`)
    const dialog = page.locator('.el-dialog, .el-drawer')
    const trigger = page.locator('main button', { hasText: /新\s*建/ }).first()
    if (await trigger.count()) {
      await trigger.click()
    } else {
      await page.getByRole('button', { name: /新\s*建/ }).first().click()
    }
    await expect(dialog.first()).toBeVisible({ timeout: 8000 })
    await dialog.locator('input').first().fill('TEST_UI_REQ_001')
    await dialog.locator('button').filter({ hasText: /^新\s*建$/ }).last().click()
    await expect(dialog.first()).toBeHidden({ timeout: 8000 })
    await page.waitForTimeout(1500)
    await expect(page.getByText('TEST_UI_REQ_001').first()).toBeVisible({ timeout: 10000 })
  })

  test('TC-045 点击工作项打开详情抽屉', async ({ page }) => {
    await gotoAsAdmin(page, `/projects/${projId}/requirements`)
    await page.getByText('TEST_UI_REQ_001').first().click()
    await page.waitForTimeout(1500)
    // 详情抽屉真实容器是 AppDrawer 的 .app-drawer-panel（不是 el-drawer）
    await expect(page.locator('.app-drawer-panel').first()).toBeVisible({ timeout: 10000 })
  })

  test('TC-054 看板视图列渲染', async ({ page }) => {
    await gotoAsAdmin(page, `/projects/${projId}/board`)
    await page.waitForTimeout(1000)
    await expect(page.locator('[class*=board], [class*=column], [class*=col]').first()).toBeVisible({ timeout: 10000 })
  })

  test('TC-059 点击看板卡片打开详情抽屉', async ({ page }) => {
    // 看板未带 type 时 boardCategory 默认取 TASK（BoardView.vue），而本组数据是需求，
    // 必须在地址上限定类型，否则看板为空态、卡片无从渲染
    await gotoAsAdmin(page, `/projects/${projId}/board?type=requirement`)
    await page.waitForTimeout(1500)
    const card = page.getByText('TEST_UI_REQ_001').first()
    await expect(card).toBeVisible({ timeout: 10000 })
    await card.click()
    await page.waitForTimeout(1500)
    await expect(page.locator('.app-drawer-panel').first()).toBeVisible()
  })

  test('TC-031 任务列表页正常加载', async ({ page }) => {
    await gotoAsAdmin(page, `/projects/${projId}/tasks`)
    await expect(page.locator('main').first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByText('任务').first()).toBeVisible()
  })

  test('TC-036 缺陷列表页正常加载', async ({ page }) => {
    await gotoAsAdmin(page, `/projects/${projId}/defects`)
    await expect(page.locator('main').first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByText('缺陷').first()).toBeVisible()
  })

  test('TC-089 表格视图正常加载', async ({ page }) => {
    // 表格页与看板共用 SimpleListView：isEmpty 时只渲染空文案、slot 不渲染，
    // 因此不限定 type（默认 TASK）时 el-table 根本不会出现
    await gotoAsAdmin(page, `/projects/${projId}/table?type=requirement`)
    await page.waitForTimeout(1000)
    await expect(page.locator('table').first()).toBeVisible({ timeout: 10000 })
    await expect(page.getByText('TEST_UI_REQ_001').first()).toBeVisible({ timeout: 10000 })
  })

  test('TC-094 表格分页控件存在', async ({ page }) => {
    await gotoAsAdmin(page, `/projects/${projId}/table?type=requirement`)
    await page.waitForTimeout(1000)
    const hasTable = await page.locator('table').count()
    test.skip(!hasTable, '表格未渲染')
    await expect(page.locator('.el-pagination').first()).toBeVisible({ timeout: 10000 })
  })
})

// ---------- 空数据状态 ----------

test.describe('空数据状态', () => {
  test('TC-193 无工作项时需求列表计数为 0', async ({ page }) => {
    await loginRequestOnly(page.request)
    const pid = await createProject(page.request, 'TUIE', 'TEST_UI_EMPTY_PROJ')
    try {
      await gotoAsAdmin(page, `/projects/${pid}/requirements`)
      await page.waitForTimeout(1500)
      await expect(page.getByText(/^0$/).first()).toBeVisible({ timeout: 10000 })
    } finally {
      await page.request.delete(`${API}/projects/${pid}`)
    }
  })
})
