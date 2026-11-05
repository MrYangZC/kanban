import { expect, type Page } from '@playwright/test'
import { ADMIN, API } from './api'

/**
 * 前端登录约定（与既有 ui.spec.ts 保持一致）：
 * 调用后端登录接口拿到 user VO，写入 localStorage 的 kanban_user，前端路由守卫据此放行。
 */
export async function loginViaApi(page: Page, username: string, password: string) {
  const resp = await page.request.post(`${API}/users/login`, { data: { username, password } })
  const body = await resp.json()
  expect(body.code, `登录 ${username} 应成功`).toBe(200)
  await page.goto('/login')
  await page.evaluate((vo) => {
    localStorage.clear()
    localStorage.setItem('kanban_user', JSON.stringify(vo))
  }, body.data)
  // 必须 reload：SPA 已在 /login 挂载并持有上一个用户的内存态，
  // 若直接 goto 目标页，路由守卫与 store 可能沿用旧用户（同一 context 内换人时会串号）
  await page.reload()
  return body.data as { id: string; username: string; token: string }
}

export async function gotoAsAdmin(page: Page, path: string) {
  await loginViaApi(page, ADMIN.username, ADMIN.password)
  await page.goto(path)
  await page.waitForLoadState('networkidle')
  await page.waitForTimeout(600)
}

/** 详情抽屉根容器（AppDrawer 里的 DefectDetailView） */
export function detailDrawer(page: Page) {
  return page.locator('.app-drawer-panel')
}

/**
 * 从缺陷列表点击行打开详情抽屉。
 * 缺陷列表默认 list 视图，行选择器为 tr.data-tr。
 */
export async function openDefectDetail(page: Page, projectId: string, title: string) {
  await gotoAsAdmin(page, `/projects/${projectId}/defects`)
  const row = page.locator('tr.data-tr').filter({ hasText: title }).first()
  await expect(row, `缺陷列表应出现 ${title}`).toBeVisible({ timeout: 15000 })
  const drawer = detailDrawer(page)
  // 行点击偶发未触发行事件：最多重试三次，末次点行内偏左位置（避开右侧操作列）。
  // 注意：点击必须全部走本循环，循环外不要再补一次 —— 抽屉打开后行会被遮罩覆盖，
  // 那一次多余的点击会一直等到可操作性超时才失败，等于每次打开详情白等一个超时。
  for (let i = 0; i < 3; i++) {
    const opts = i < 2 ? { timeout: 8000 } : { position: { x: 10, y: 10 }, timeout: 8000 }
    await row.click(opts).catch(() => undefined)
    if (await drawer.isVisible().catch(() => false)) break
    await page.waitForTimeout(500)
  }
  await expect(drawer, '详情抽屉应打开').toBeVisible({ timeout: 15000 })
  // 标题渲染在 input 的 value 上（不是文本节点），必须用 toHaveValue 断言
  await expect(
    drawer.getByRole('textbox', { name: /标题/ }).first(),
    `抽屉应加载工作项 ${title}`
  ).toHaveValue(title, { timeout: 15000 })
  return drawer
}

/**
 * 展开评论富文本编辑器。
 * 评论区是钉在左侧面板底部的常驻条：未编辑时是「点击输入评论…」占位条（整条可点），
 * 点开才 v-if 切到 RichTextEditor（placeholder = Ctrl+V 直接上传截图…），此时占位条消失，
 * 因此重复调用不会误点。
 */
export async function openCommentEditor(page: Page) {
  const drawer = detailDrawer(page)
  const trigger = drawer.locator('div.cursor-text').filter({ hasText: '点击输入评论' })
  if (await trigger.count()) await trigger.first().click()
  const editor = drawer.getByPlaceholder(/Ctrl\+V 直接上传截图/)
  await expect(editor).toBeVisible({ timeout: 10000 })
  return editor
}

/** 等待 Element Plus 提示条出现并断言文案 */
export async function expectToast(page: Page, pattern: RegExp, kind: 'success' | 'error' | 'warning' | 'info' = 'success') {
  const toast = page.locator(`.el-message--${kind}`).last()
  await expect(toast, `应出现 ${kind} 提示：${pattern}`).toBeVisible({ timeout: 10000 })
  await expect(toast).toContainText(pattern)
  return toast
}

/** 读取当前可见提示条文案（用于记录实际行为，做宽松断言时使用） */
export async function readToastText(page: Page, kind: 'error' | 'success' = 'error') {
  const toast = page.locator(`.el-message--${kind}`).last()
  await toast.waitFor({ state: 'visible', timeout: 10000 })
  return (await toast.innerText()).replace(/\s+/g, ' ').trim()
}
