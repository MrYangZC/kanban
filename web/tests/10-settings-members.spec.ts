import { test, expect, type APIRequestContext, type Locator, type Page } from '@playwright/test'
import {
  loginApi, createProject, deleteProject, createUser, deleteUsersByName,
  listProjectMembers, addProjectMember
} from './helpers/api'
import { gotoAsAdmin, loginViaApi, expectToast, readToastText } from './helpers/session'

/**
 * 10 设置页成员管理（RBAC 多角色）
 *
 * 前后端事实（对照源码校准）：
 *   - 成员接口：GET/POST /projects/{id}/members、DELETE /members/{userId}、
 *     PUT /members/{userId}/roles（请求体是角色码数组，覆盖式）
 *   - 前端 canManage = 我在本项目拥有 OWNER/ADMIN/PM 之一，页面提示写「仅所有者/管理员/项目经理可调整成员」
 *   - 后端 ProjectMemberService.requireAdmin 实际只认 isOwner（仅项目所有者），
 *     并对「移除最后一个所有者」「把最后一个所有者降级」单独返回 400
 *   - 设置页成员区一次性加载：listProjectMembers 成功后才在 canManage 时请求 listUsers()，
 *     任一失败都会走同一个 catch 把 members 置空
 */

const PREFIX = 'TEST_MEM_'

/** 成员行：外层容器按用户名唯一匹配（行内 @username 是唯一稳定标识） */
const memberRow = (page: Page, username: string) =>
  page.locator('div.rounded-md.border.border-line-light').filter({ hasText: `@${username}` }).first()

/** 行内角色标签（编辑面板未展开时才只有标签） */
const roleTag = (row: Locator, label: string) => row.locator('span').filter({ hasText: label })

/** 展开「编辑角色」面板 */
async function openRoleEditor(row: Locator) {
  await row.locator('button', { hasText: '编辑角色' }).click()
  await expect(row.locator('button', { hasText: '保存角色' })).toBeVisible({ timeout: 5000 })
}

/** 切换某个角色的选中状态（标签是 label 包着 sr-only checkbox） */
async function toggleRole(row: Locator, label: string, checked: boolean) {
  const box = roleCheckbox(row, label)
  if ((await box.isChecked()) !== checked) await row.locator('label', { hasText: label }).first().click()
  await expect(box).toBeChecked({ checked })
}

/** 角色中文标签 → 角色码（与 SettingsView 的 ROLE_OPTIONS 一致） */
const ROLE_CODE: Record<string, string> = {
  所有者: 'OWNER', 管理员: 'ADMIN', 项目经理: 'PM', 开发: 'DEV',
  测试: 'QA', 参与人: 'MEMBER', 只读: 'VIEWER'
}

/** 角色复选框（checkbox 的 value 就是角色码，标签文案包在这个 label 里） */
const roleCheckbox = (row: Locator, label: string) => {
  const code = ROLE_CODE[label]
  if (!code) throw new Error(`未知角色标签：${label}`)
  return row.locator(`input[type="checkbox"][value="${code}"]`)
}

async function rolesOf(page: Page, username: string): Promise<string[]> {
  const tags = memberRow(page, username).locator('span')
  return (await tags.allInnerTexts()).map((t) => t.trim()).filter(Boolean)
}

test.describe('10 设置页成员管理', () => {
  let projectId = ''
  let devUserId = ''      // 角色变更 / 移除 用
  let adminUserId = ''    // 「项目 ADMIN 打开设置页」用

  const DEV_USER = `${PREFIX}DEV`
  const ADMIN_USER = `${PREFIX}ADMIN`

  test.beforeAll(async ({ request }) => {
    await loginApi(request)
    projectId = await createProject(request, 'TBMEM', `${PREFIX}PROJ`)
    devUserId = await createUser(request, DEV_USER, '成员测试甲')
    adminUserId = await createUser(request, ADMIN_USER, '成员测试乙')
    // 默认角色：一个普通参与人、一个项目管理员（用于验证非 OWNER 的成员管理口径）
    await addProjectMember(request, projectId, devUserId, ['MEMBER'])
    await addProjectMember(request, projectId, adminUserId, ['ADMIN'])
  })

  test.afterAll(async ({ request }) => {
    // 顺序很重要：project_member 对 sys_user 是 NO ACTION 外键，
    // 项目没删干净时删账号会 400「该用户仍是项目成员」
    if (projectId) await deleteProject(request, projectId)
    await deleteUsersByName(request, DEV_USER)
    await deleteUsersByName(request, ADMIN_USER)
  })

  test('TC-701 变更成员角色并持久化', async ({ request, page }) => {
    await gotoAsAdmin(page, `/projects/${projectId}/settings`)
    const row = memberRow(page, DEV_USER)
    await expect(row, '成员列表应包含被添加的用户').toBeVisible({ timeout: 15000 })
    await expect(roleTag(row, '参与人'), '初始角色应为参与人').toBeVisible()

    await openRoleEditor(row)
    await toggleRole(row, '开发', true)
    await toggleRole(row, '参与人', false)
    await row.locator('button', { hasText: '保存角色' }).click()

    await expectToast(page, /角色已更新/)
    // 面板收起后行内标签应变为「开发」
    await expect(roleTag(row, '开发'), '行内角色标签应更新为开发').toBeVisible({ timeout: 10000 })
    expect(await rolesOf(page, DEV_USER), '行内应只剩开发一个角色').toEqual(['开发'])

    // 落库校验（不看 UI）
    const members = await listProjectMembers(request, projectId)
    const mine = members.find((m) => m.userId === devUserId)
    expect(mine?.roles, '服务端角色应变为 DEV').toEqual(['DEV'])

    // 刷新后保持
    await page.reload()
    const reloaded = memberRow(page, DEV_USER)
    await expect(reloaded).toBeVisible({ timeout: 15000 })
    await expect(roleTag(reloaded, '开发')).toBeVisible()
  })

  test('TC-702 移除成员并从服务端删除', async ({ request, page }) => {
    await gotoAsAdmin(page, `/projects/${projectId}/settings`)
    const row = memberRow(page, DEV_USER)
    await expect(row).toBeVisible({ timeout: 15000 })

    await row.locator('button[title="移除成员"]').click()
    // ElMessageBox 二次确认
    const confirm = page.locator('.el-message-box')
    await expect(confirm, '移除成员应先弹确认框').toBeVisible()
    await confirm.getByRole('button', { name: '确定' }).click()

    await expectToast(page, /已移除/)
    await expect(memberRow(page, DEV_USER), '确认移除后该成员行应消失').toHaveCount(0, { timeout: 10000 })

    const members = await listProjectMembers(request, projectId)
    expect(members.some((m) => m.userId === devUserId), '服务端不应再有该成员').toBe(false)

    // 顺手把成员加回来，保持本文件用例间数据稳定（TC-701 已证明可改角色）
    await addProjectMember(request, projectId, devUserId, ['MEMBER'])
  })

  test('TC-703 不能移除最后一个项目所有者', async ({ request, page }) => {
    await gotoAsAdmin(page, `/projects/${projectId}/settings`)
    const row = memberRow(page, 'admin')
    await expect(row, '所有者 admin 应在成员列表中').toBeVisible({ timeout: 15000 })

    await row.locator('button[title="移除成员"]').click()
    const confirm = page.locator('.el-message-box')
    await expect(confirm).toBeVisible()
    await confirm.getByRole('button', { name: '确定' }).click()

    // 后端守卫：400「不能移除最后一个项目所有者」，拦截器统一 ElMessage.error 透出原文
    const text = await readToastText(page, 'error')
    expect(text, '应提示不能移除最后一个所有者').toContain('不能移除最后一个项目所有者')

    await expect(memberRow(page, 'admin'), '被拒绝后成员行仍在').toBeVisible()
    const members = await listProjectMembers(request, projectId)
    const owner = members.find((m) => m.username === 'admin')
    expect(owner, 'admin 仍是项目成员').toBeTruthy()
    expect(owner?.roles, 'admin 仍是所有者').toEqual(['OWNER'])
  })

  test('TC-704 不能把最后一个所有者降级', async ({ request, page }) => {
    await gotoAsAdmin(page, `/projects/${projectId}/settings`)
    const row = memberRow(page, 'admin')
    await expect(row).toBeVisible({ timeout: 15000 })

    await openRoleEditor(row)
    await toggleRole(row, '开发', true)
    await toggleRole(row, '所有者', false)
    await row.locator('button', { hasText: '保存角色' }).click()

    const text = await readToastText(page, 'error')
    expect(text, '应提示项目至少保留一个所有者').toContain('项目至少需保留一个所有者')

    // 角色保持原样
    const members = await listProjectMembers(request, projectId)
    expect(members.find((m) => m.username === 'admin')?.roles, 'admin 角色不应被改动').toEqual(['OWNER'])
  })

  test('TC-705 项目 ADMIN 打开设置页应能看到成员列表', async ({ page }) => {
    // 项目管理员（非项目所有者）进入设置页：前端 canManage 为真，
    // 会额外请求 listUsers()（全局用户列表，仅系统管理员可访问）
    await loginViaApi(page, ADMIN_USER, 'uitest123')
    await page.goto(`/projects/${projectId}/settings`)
    await page.waitForLoadState('networkidle')
    await page.waitForTimeout(800)

    // 正确行为：既是项目成员，成员列表就应正常渲染
    await expect(
      memberRow(page, ADMIN_USER),
      '项目 ADMIN 应能在设置页看到成员列表（含自己）'
    ).toBeVisible({ timeout: 15000 })
    await expect(page.getByText('暂无成员'), '不应退化为空列表').toHaveCount(0)
  })
})
