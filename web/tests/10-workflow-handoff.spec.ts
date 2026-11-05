import { test, expect, type APIRequestContext, type Locator, type Page } from '@playwright/test'
import {
  API, loginApi, createProject, deleteProject, createIssue, createUser, deleteUsersByName, addProjectMember,
  listProjectStatuses, listWorkflowColumns, saveWorkflowHandoff, listActivities, updateIssue, revokeHandoffViaApi,
  unreadNotificationCount, listNotifications, apiGet, createSprint, ensureProjectTag, listIssueTags, moveIssue,
  deleteSprint
} from './helpers/api'
import { gotoAsAdmin, loginViaApi } from './helpers/session'

/**
 * 10 工作流交接（handoff）
 *
 * 前端事实（对照源码校准）：
 *   - 交接配置挂在「项目 × 类别 × 状态」维度（project_handoff），不是全局 workflow_status，
 *     所以同一列在不同项目可以配不同的人
 *   - 列头在有配置时显示「交给 XX」，配置入口是列头右侧悬浮齿轮（title=配置列交接），
 *     仅 hasPermission('project:workflow') 可见
 *   - 状态变更有两个入口：看板拖拽（PATCH /issues/{id}/position）与详情抽屉（PUT /issues/{id}），
 *     两处都会在目标列配了交接且确实换人时弹确认框（utils/handoff.confirmHandoff → ElMessageBox）
 *   - 确认框按钮文案「确认转交」/「取消」；取消时看板回弹、抽屉不提交
 *   - 交接成功后接手人收到站内通知（顶栏铃铛未读角标），默认全部列「不交接」
 */

const PREFIX = 'TEST_HANDOFF_'
const MEMBER_NAME = '交接候选人'

// 需求工作流 6 列，默认 1440 视口只能看到前 4 列，跨列拖拽需要目标列在视口内
test.use({ viewport: { width: 2200, height: 1000 } })

/** 卡片列表容器（data-status-id 挂在 draggable 列表上，不在列根上） */
const column = (page: Page, statusId: string) => page.locator(`[data-status-id="${statusId}"]`)
const cards = (page: Page, statusId: string) => page.locator(`[data-status-id="${statusId}"] > div`)
/** 列根元素：列头（含交接文案与配置齿轮）在这里，不在 data-status-id 元素上 */
const columnBox = (page: Page, statusId: string) =>
  page.locator('.kanban-col').filter({ has: page.locator(`[data-status-id="${statusId}"]`) })

async function centerOf(locator: Locator) {
  const box = await locator.boundingBox()
  if (!box) throw new Error('目标元素不可见')
  return { x: box.x + box.width / 2, y: box.y + box.height / 2 }
}

/** 与 09 相同的拖拽序列：原生 HTML5 DnD 需要 mouse.down → 分段 move → mouse.up */
async function dragCardTo(page: Page, card: Locator, target: { x: number; y: number }) {
  const box = (await card.boundingBox())!
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2)
  await page.mouse.down()
  await page.mouse.move(box.x + box.width / 2 + 12, box.y + box.height / 2 + 12, { steps: 6 })
  await page.mouse.move(target.x, target.y, { steps: 25 })
  await page.waitForTimeout(120)
  await page.mouse.move(target.x + 6, target.y + 6, { steps: 4 })
  await page.mouse.move(target.x, target.y, { steps: 4 })
  await page.waitForTimeout(300)
}

function handoffDialog(page: Page) {
  return page.locator('.el-dialog').filter({ hasText: '列交接' }).last()
}

function confirmBox(page: Page) {
  return page.locator('.el-message-box').last()
}

async function statusOf(request: APIRequestContext, issueId: string) {
  return apiGet<{ statusId: string; assigneeId?: string; sprintId?: string | null }>(
    request, `${API}/issues/${issueId}`, '查询工作项'
  )
}

test.describe('工作流交接', () => {
  let projectId = ''
  let todoId = ''
  let doingId = ''
  let testingId = ''
  let doneId = ''
  let memberId = ''
  // 带随机后缀：上一轮若在删除阶段失败，固定用户名会让下一轮 POST /users 撞「已存在」
  let memberUsername = ''

  test.beforeAll(async ({ request }) => {
    await loginApi(request)
    projectId = await createProject(request, 'TBHF', `${PREFIX}PROJ`)
    const statuses = await listProjectStatuses(request, projectId, 'REQUIREMENT')
    const pick = (code: string) => {
      const s = statuses.find((x) => x.code === code)
      if (!s) throw new Error(`需求工作流缺少状态 ${code}`)
      return s.id
    }
    todoId = pick('REQ_TODO')
    doingId = pick('REQ_DESIGNING')
    testingId = pick('REQ_TESTING')
    doneId = pick('REQ_DONE')

    memberUsername = `hf${Date.now().toString(36)}`
    memberId = await createUser(request, memberUsername, MEMBER_NAME)
    await addProjectMember(request, projectId, memberId, ['MEMBER'])

    // 前置校验：交接默认关闭，否则「默认不交接」的断言失去意义
    const cols = await listWorkflowColumns(request, projectId, 'REQUIREMENT')
    expect(cols.every((c) => c.handoffType === 'NONE'), '默认所有列应为不交接').toBe(true)
  })

  test.afterAll(async ({ request }) => {
    // 复位成不交接再删项目：交接配置是项目级数据，避免残留影响同库其他用例的人工排查
    try {
      const cols = await listWorkflowColumns(request, projectId, 'REQUIREMENT')
      for (const col of cols.filter((c) => c.handoffType !== 'NONE')) {
        await saveWorkflowHandoff(request, projectId, col.statusId, 'REQUIREMENT', { handoffType: 'NONE' })
      }
    } catch { /* 项目已被删除时忽略 */ }
    await deleteProject(request, projectId)
    try {
      await deleteUsersByName(request, memberUsername)
    } catch { /* 用户已是历史数据时忽略 */ }
  })

  test('TC-701 列头展示交接配置，可在弹窗里设置与清除', async ({ request, page }) => {
    const note = '拆完子任务再往下走'
    await gotoAsAdmin(page, `/projects/${projectId}/board?type=requirement`)
    const doingCol = columnBox(page, doingId)
    await expect(doingCol).toBeVisible({ timeout: 15000 })
    // 默认没有交接文案
    await expect(doingCol).not.toContainText('交给')

    // 打开「设计中」列的交接弹窗
    await doingCol.getByTitle('配置列交接').click()
    const dialog = handoffDialog(page)
    await expect(dialog).toBeVisible({ timeout: 10000 })
    // 点 el-radio 的 label 本体：直接点内部文字不一定会激活关联 input
    await dialog.locator('.el-radio').filter({ hasText: '交给指定成员' }).click()
    // 弹窗里有三个下拉（接手人 / 落点迭代 / 落点标签），按占位文案锁定接手人那个
    await dialog.locator('.el-select__wrapper').filter({ hasText: '选择接手的成员' }).click()
    await expect(page.locator('.el-select-dropdown__item').first()).toBeVisible({ timeout: 10000 })
    await page.locator('.el-select-dropdown__item').filter({ hasText: MEMBER_NAME }).first().click()
    await dialog.locator('textarea').fill(note)
    await dialog.getByRole('button', { name: '保存' }).click()
    await expect(dialog).toBeHidden({ timeout: 10000 })

    // 列头只显示「交给 XX」，交接说明留在弹窗与通知里；刷新后仍在（配置落在后端）
    await expect(doingCol).toContainText(`交给 ${MEMBER_NAME}`, { timeout: 10000 })
    await page.reload()
    await expect(columnBox(page, doingId)).toContainText(`交给 ${MEMBER_NAME}`, { timeout: 15000 })

    const cols = await listWorkflowColumns(request, projectId, 'REQUIREMENT')
    const doing = cols.find((c) => c.statusId === doingId)!
    expect(doing.handoffType, '交接类型应为 MEMBER').toBe('MEMBER')
    expect(doing.handoffUserId, '接手人应为测试成员').toBe(memberId)
    expect(doing.handoffNote, '交接说明应保存').toBe(note)

    // 清除
    const colAfterReload = columnBox(page, doingId)
    await colAfterReload.getByTitle('配置列交接').click()
    await handoffDialog(page).locator('.el-radio').filter({ hasText: '不交接' }).click()
    await handoffDialog(page).getByRole('button', { name: '保存' }).click()
    await expect(colAfterReload).not.toContainText('交给', { timeout: 10000 })
    await expect
      .poll(async () => (await listWorkflowColumns(request, projectId, 'REQUIREMENT')).find((c) => c.statusId === doingId)?.handoffType)
      .toBe('NONE')
  })

  test('TC-702 拖拽到交接列：取消则回弹，确认则转交并留痕', async ({ request, page, playwright }) => {
    const title = `${PREFIX}DRAG`
    const issue = await createIssue(request, { projectId, statusId: todoId, issueTypeCode: 'STORY', title })
    await saveWorkflowHandoff(request, projectId, doingId, 'REQUIREMENT', {
      handoffType: 'MEMBER', handoffUserId: memberId, handoffNote: '接手后先补设计稿'
    })

    await gotoAsAdmin(page, `/projects/${projectId}/board?type=requirement`)
    const card = cards(page, todoId).filter({ hasText: title })
    await expect(card).toBeVisible({ timeout: 15000 })

    // 第一次：取消 —— 卡片回弹，状态与负责人都不变
    await dragCardTo(page, card, await centerOf(column(page, doingId)))
    await expect(page.locator('.kanban-col.kanban-col-allow'), '设计中列应允许放下').toHaveCount(1, { timeout: 10000 })
    await page.mouse.up()
    const box = confirmBox(page)
    await expect(box, '拖入配置了交接的列应弹确认框').toBeVisible({ timeout: 10000 })
    await expect(box).toContainText(`会转交给「${MEMBER_NAME}」`)
    await box.getByRole('button', { name: '取消' }).click()
    await expect(box).toBeHidden({ timeout: 10000 })

    await expect(cards(page, todoId).filter({ hasText: title }), '取消后卡片应回到原列').toBeVisible({ timeout: 10000 })
    const afterCancel = await statusOf(request, issue.id)
    expect(afterCancel.statusId, '取消交接后状态不应变更').toBe(todoId)
    expect(afterCancel.assigneeId ?? '', '取消交接后负责人不应变更').toBe('')

    // 第二次：确认 —— 负责人改写 + 动态 + 通知
    await dragCardTo(page, cards(page, todoId).filter({ hasText: title }), await centerOf(column(page, doingId)))
    await page.mouse.up()
    await expect(confirmBox(page)).toBeVisible({ timeout: 10000 })
    await confirmBox(page).getByRole('button', { name: '确认转交' }).click()

    await expect(cards(page, doingId).filter({ hasText: title }), '确认后卡片应落在新列').toBeVisible({ timeout: 15000 })
    const afterConfirm = await statusOf(request, issue.id)
    expect(afterConfirm.statusId, '确认后状态应为设计中').toBe(doingId)
    expect(afterConfirm.assigneeId, '确认后负责人应为接手人').toBe(memberId)

    const acts = await listActivities(request, issue.id)
    const assigneeAct = acts.find((a) => a.type === 'ASSIGNEE_CHANGED')
    expect(assigneeAct, '应产生「负责人变更」动态').toBeTruthy()
    expect(assigneeAct!.newValue, '动态新值应为接手人姓名').toBe(MEMBER_NAME)

    // 通知发给接手人，必须用他的会话查（admin 的通知列表应为空）
    // 注意：helpers 里的 apiGet 会先 ensureLogin(admin) 兜底，跨用户查询必须用裸 request
    const memberReq = await playwright.request.newContext({ baseURL: test.info().project.use.baseURL })
    const memberGet = async (path: string) =>
      ((await (await memberReq.get(`${API}${path}`)).json()) as { code: number; data: unknown }).data
    try {
      const login = await memberReq.post(`${API}/users/login`, {
        data: { username: memberUsername, password: 'uitest123' }
      })
      expect((await login.json()).code, '接手人登录应成功').toBe(200)
      const notifications = (await memberGet('/users/notifications?page=1&size=20')) as {
        total: number; list: { type: string; issueId: string; category: string }[]
      }
      expect(notifications.total, '接手人应收到一条通知').toBe(1)
      expect(notifications.list[0].type).toBe('HANDOFF')
      expect(notifications.list[0].issueId).toBe(issue.id)
      expect(notifications.list[0].category).toBe('REQUIREMENT')
      expect(await memberGet('/users/notifications/unread-count'), '接手人应有 1 条未读').toBe(1)
      expect(await unreadNotificationCount(request), '交接人自己不应收到通知').toBe(0)
    } finally {
      await memberReq.dispose()
    }
  })

  test('TC-703 详情里改状态同样需要确认，取消则不提交', async ({ request, page }) => {
    const title = `${PREFIX}DETAIL`
    const issue = await createIssue(request, { projectId, statusId: todoId, issueTypeCode: 'STORY', title })
    // 用「设计中」而不是「已完成」：详情下拉只列 workflow_transition 里可达的状态
    await saveWorkflowHandoff(request, projectId, doingId, 'REQUIREMENT', {
      handoffType: 'MEMBER', handoffUserId: memberId
    })

    await gotoAsAdmin(page, `/projects/${projectId}/board?type=requirement`)
    const card = cards(page, todoId).filter({ hasText: title })
    await expect(card).toBeVisible({ timeout: 15000 })
    await card.click()

    const drawer = page.locator('.app-drawer-panel')
    await expect(drawer, '详情抽屉应打开').toBeVisible({ timeout: 15000 })
    // 详情页状态是 el-select 且即时保存（没有「保存」按钮），右栏「状态」label 的兄弟节点即它
    const select = drawer.locator('label:text-is("状态") + .el-select')
    await expect(select).toBeVisible({ timeout: 10000 })
    await select.locator('.el-select__wrapper').click()
    const option = page.locator('.el-select-dropdown__item').filter({ hasText: `交给 ${MEMBER_NAME}` }).first()
    await expect(option, '状态下拉应标注交接目标').toBeVisible({ timeout: 10000 })
    await option.click()
    const box = confirmBox(page)
    await expect(box, '详情改状态到交接列应弹确认框').toBeVisible({ timeout: 10000 })
    await box.getByRole('button', { name: '取消' }).click()
    await expect(box).toBeHidden({ timeout: 10000 })

    await page.waitForTimeout(800)
    const after = await statusOf(request, issue.id)
    expect(after.statusId, '取消后状态不应提交').toBe(todoId)
    expect(after.assigneeId ?? '', '取消后负责人不应变更').toBe('')
  })

  test('TC-704 接手人顶栏铃铛显示未读角标，点击通知后已读并跳转', async ({ request, browser, playwright }) => {
    test.setTimeout(90_000)
    const title = `${PREFIX}BELL`
    // 前面用例已给同一接手人发过通知，先清空未读，角标断言才有确定值。
    // 未读必须在断言角标前确认为 0：read-all 若被静默吞掉，后面角标就会因为历史未读叠加而随机失败，
    // 报错点会误导到 UI 上。
    const seed = await playwright.request.newContext({ baseURL: test.info().project.use.baseURL })
    try {
      await seed.post(`${API}/users/login`, { data: { username: memberUsername, password: 'uitest123' } })
      await seed.put(`${API}/users/notifications/read-all`)
      const unread = await seed.get(`${API}/users/notifications/unread-count`)
      expect((await unread.json()).data, '前置清空未读应生效').toBe(0)
    } finally {
      await seed.dispose()
    }

    const issue = await createIssue(request, { projectId, statusId: todoId, issueTypeCode: 'STORY', title })
    await saveWorkflowHandoff(request, projectId, testingId, 'REQUIREMENT', {
      handoffType: 'MEMBER', handoffUserId: memberId
    })
    // 走接口条数：先制造一次转交（详情改状态，避免再依赖拖拽）
    await updateIssue(request, issue.id, { statusId: testingId })

    const ctx = await browser.newContext({ viewport: { width: 1600, height: 1000 }, baseURL: test.info().project.use.baseURL })
    const memberPage = await ctx.newPage()
    try {
      await loginViaApi(memberPage, memberUsername, 'uitest123')
      await memberPage.goto(`/projects/${projectId}/board?type=requirement`)
      await memberPage.waitForLoadState('networkidle')

      const bell = memberPage.getByTitle('通知')
      await expect(bell).toBeVisible({ timeout: 15000 })
      await expect(bell.locator('span').first(), '未读角标应显示').toHaveText('1', { timeout: 15000 })

      await bell.click()
      const item = memberPage.locator('button').filter({ hasText: '收到一条交接工作项' }).first()
      await expect(item).toBeVisible({ timeout: 10000 })
      await expect(item).toContainText(title)

      await item.click()
      // 点击后跳转到对应工作项所在列表，且会话内的未读数归零
      await memberPage.waitForURL(/\/projects\/.*\/(requirements|tasks)/, { timeout: 15000 })
      await expect
        .poll(async () => {
          const resp = await memberPage.request.get(`/api/v1/users/notifications/unread-count`)
          return (await resp.json()).data
        }, { timeout: 15000 })
        .toBe(0)
    } finally {
      await ctx.close()
    }
  })

  test('TC-706 撤销最近一次交接：负责人退回原负责人，且不能重复撤销', async ({ request, page }) => {
    const title = `${PREFIX}REVOKE`
    const issue = await createIssue(request, { projectId, statusId: todoId, issueTypeCode: 'STORY', title })
    await saveWorkflowHandoff(request, projectId, doingId, 'REQUIREMENT', {
      handoffType: 'MEMBER', handoffUserId: memberId
    })
    // 确认框是前端行为，这里只关心落库效果：接口改状态同样会触发交接
    await updateIssue(request, issue.id, { statusId: doingId })
    expect((await statusOf(request, issue.id)).assigneeId, '接口改状态应完成交接').toBe(memberId)

    // 后端应把「最后一条交接」标记为可撤销
    let acts = await listActivities(request, issue.id)
    const handoff = acts.find((a) => a.type === 'ASSIGNEE_CHANGED')
    expect(handoff, '应产生交接动态').toBeTruthy()
    expect(handoff!.revocable, '最后一条交接应标记为可撤销').toBe(true)

    // UI：详情抽屉的动态里点「撤销交接」
    await gotoAsAdmin(page, `/projects/${projectId}/board?type=requirement`)
    const card = cards(page, doingId).filter({ hasText: title })
    await expect(card).toBeVisible({ timeout: 15000 })
    await card.click()
    const drawer = page.locator('.app-drawer-panel')
    await expect(drawer, '详情抽屉应打开').toBeVisible({ timeout: 15000 })
    const revokeBtn = drawer.getByRole('button', { name: '撤销交接' })
    await expect(revokeBtn, '交接动态上应出现撤销入口').toBeVisible({ timeout: 15000 })
    await revokeBtn.click()

    const box = confirmBox(page)
    await expect(box, '撤销前应二次确认').toBeVisible({ timeout: 10000 })
    await box.getByRole('button', { name: '撤销' }).click()
    await expect(box).toBeHidden({ timeout: 10000 })

    // 交接前没人负责，撤销后应回到「无人负责」；撤销记录进时间线
    await expect
      .poll(async () => (await statusOf(request, issue.id)).assigneeId ?? '', { timeout: 15000 })
      .toBe('')
    acts = await listActivities(request, issue.id)
    const revoked = acts.find((a) => a.type === 'HANDOFF_REVOKED')
    expect(revoked, '应产生「撤销交接」动态').toBeTruthy()
    expect(acts.some((a) => a.revocable), '撤销后不应还有可撤销的交接').toBe(false)

    // 再撤一次：按钮已消失，接口也必须拒绝（不能靠前端隐藏兜底）
    const resp = await revokeHandoffViaApi(request, issue.id)
    expect((await resp.json()).code, '重复撤销应被后端拒绝').not.toBe(200)
  })

  test('TC-707 交接落点：进列时一并移入迭代、补上标签，撤销只退迭代', async ({ request }) => {
    const title = `${PREFIX}LANDING`
    const sprint = await createSprint(request, projectId, `${PREFIX}SPRINT`)
    const issue = await createIssue(request, { projectId, statusId: todoId, issueTypeCode: 'STORY', title })
    // 借这个工作项把标签建出来再摘掉，交接时补标签才有「从未关联到已关联」的变化
    const labelId = await ensureProjectTag(request, projectId, `${PREFIX}TAG`, issue.id)

    // 落点跟着配置走：列头与详情下拉共用这份数据，先确认能回读到名称
    const saved = await saveWorkflowHandoff(request, projectId, doingId, 'REQUIREMENT', {
      handoffType: 'MEMBER', handoffUserId: memberId,
      targetSprintId: sprint.id, targetLabelId: labelId
    })
    expect(saved.targetSprintName, '落点迭代应回读名称').toBe(`${PREFIX}SPRINT`)
    expect(saved.targetLabelName, '落点标签应回读名称').toBe(`${PREFIX}TAG`)

    const before = await statusOf(request, issue.id)
    expect(before.sprintId ?? null, '新建时不应带迭代').toBeNull()
    expect((await listIssueTags(request, issue.id)).length, '交接前不应带标签').toBe(0)

    await updateIssue(request, issue.id, { statusId: doingId })

    const after = await statusOf(request, issue.id)
    expect(after.assigneeId, '应完成交接').toBe(memberId)
    expect(after.sprintId, '交接应写入落点迭代').toBe(sprint.id)
    expect(
      (await listIssueTags(request, issue.id)).map((t) => t.name),
      '交接应补上落点标签'
    ).toContain(`${PREFIX}TAG`)

    const acts = await listActivities(request, issue.id)
    expect(acts.find((a) => a.type === 'SPRINT_CHANGED'), '迭代变更应进动态').toBeTruthy()
    expect(acts.find((a) => a.type === 'LABEL_ADDED'), '打标签应进动态').toBeTruthy()

    // 撤销：负责人与迭代一起退回；标签按设计保留（可累加属性，可能是别人手工加的）
    const resp = await revokeHandoffViaApi(request, issue.id)
    expect(resp.ok(), '撤销交接应成功').toBe(true)
    const restored = await statusOf(request, issue.id)
    expect(restored.assigneeId ?? '', '撤销后应退回无人负责').toBe('')
    expect(restored.sprintId ?? null, '撤销后应退回无迭代').toBeNull()
    expect(
      (await listIssueTags(request, issue.id)).map((t) => t.name),
      '撤销不删标签'
    ).toContain(`${PREFIX}TAG`)
    expect(
      (await listActivities(request, issue.id)).find((a) => a.type === 'HANDOFF_REVOKED'),
      '应产生撤销动态'
    ).toBeTruthy()

    // 拖拽（PATCH /position）是看板主入口，走的 move() 与改状态不是同一条代码，落点也得生效
    const dragged = await createIssue(request, {
      projectId, statusId: todoId, issueTypeCode: 'STORY', title: `${PREFIX}LANDING_DRAG`
    })
    await moveIssue(request, dragged.id, { statusId: doingId })
    const moved = await statusOf(request, dragged.id)
    expect(moved.assigneeId, '拖拽也应完成交接').toBe(memberId)
    expect(moved.sprintId, '拖拽应写入落点迭代').toBe(sprint.id)
    expect(
      (await listIssueTags(request, dragged.id)).map((t) => t.name),
      '拖拽也应补上落点标签'
    ).toContain(`${PREFIX}TAG`)

    // 迭代被别人改过之后再撤销：只退负责人，不能把别人的调整抹掉
    const otherSprint = await createSprint(request, projectId, `${PREFIX}SPRINT2`)
    await updateIssue(request, dragged.id, { sprintId: otherSprint.id })
    expect((await statusOf(request, dragged.id)).sprintId, '前置：迭代已被人改成另一个').toBe(otherSprint.id)
    expect((await revokeHandoffViaApi(request, dragged.id)).ok(), '此时仍可撤销负责人').toBe(true)
    const kept = await statusOf(request, dragged.id)
    expect(kept.assigneeId ?? '', '负责人照样退回').toBe('')
    expect(kept.sprintId, '被改过的迭代应原样保留').toBe(otherSprint.id)

    // 这列的落点配置会污染后续用例（其它用例也拖到「设计中」），用完即清
    await saveWorkflowHandoff(request, projectId, doingId, 'REQUIREMENT', { handoffType: 'NONE' })
  })

  test('TC-708 落点迭代被删除后，交接降级为只换人', async ({ request }) => {
    const doomed = await createSprint(request, projectId, `${PREFIX}SPRINT_GONE`)
    const issue = await createIssue(request, {
      projectId, statusId: todoId, issueTypeCode: 'STORY', title: `${PREFIX}LANDING_DELETED`
    })
    await saveWorkflowHandoff(request, projectId, doingId, 'REQUIREMENT', {
      handoffType: 'MEMBER', handoffUserId: memberId, targetSprintId: doomed.id
    })

    // 删迭代：配置里的落点靠 ON DELETE SET NULL 自动清空，不该把删除本身挡下来
    await deleteSprint(request, projectId, doomed.id)
    const col = (await listWorkflowColumns(request, projectId, 'REQUIREMENT')).find((c) => c.statusId === doingId)
    expect(col?.targetSprintId ?? null, '迭代删除后配置不应再指向它').toBeNull()
    expect(col?.targetSprintName ?? null, '迭代删除后名称也应为空').toBeNull()
    expect(col?.handoffUserId, '换人配置本身不受影响').toBe(memberId)

    // 交接照常发生：交接比落点重要，落点残缺不能把人也不转了
    await updateIssue(request, issue.id, { statusId: doingId })
    const after = await statusOf(request, issue.id)
    expect(after.assigneeId, '仍应完成交接').toBe(memberId)
    expect(after.sprintId ?? null, '没有落点就不该动迭代').toBeNull()
    expect(
      (await listActivities(request, issue.id)).find((a) => a.type === 'SPRINT_CHANGED'),
      '没动迭代就不该有迭代变更动态'
    ).toBeFalsy()

    await saveWorkflowHandoff(request, projectId, doingId, 'REQUIREMENT', { handoffType: 'NONE' })
  })

  test('TC-705 没有 project:workflow 权限的成员看不到配置入口，接口也拒绝', async ({ page, browser }) => {
    await gotoAsAdmin(page, `/projects/${projectId}/board?type=requirement`)
    await expect(column(page, doingId)).toBeVisible({ timeout: 15000 })
    expect(await page.getByTitle('配置列交接').count(), '管理员应能看到配置入口').toBeGreaterThan(0)

    // 换人必须开新 context：同一 context 内重新登录，SPA 会沿用上一个用户的内存态（前端以 localStorage 为准）
    const ctx = await browser.newContext({
      viewport: { width: 2200, height: 1000 },
      baseURL: test.info().project.use.baseURL
    })
    const memberPage = await ctx.newPage()
    try {
      await loginViaApi(memberPage, memberUsername, 'uitest123')
      await memberPage.goto(`/projects/${projectId}/board?type=requirement`)
      await expect(column(memberPage, doingId)).toBeVisible({ timeout: 15000 })
      expect(await memberPage.getByTitle('配置列交接').count(), '普通成员不应看到配置入口').toBe(0)

      // 前端藏按钮只是体验，后端必须独立拦截
      const resp = await memberPage.request.put(
        `${API}/projects/${projectId}/workflow-columns/${doingId}/handoff?category=REQUIREMENT`,
        { data: { handoffType: 'PROJECT_LEAD' } }
      )
      expect(resp.status(), '成员改交接配置应被拒绝').toBe(403)
    } finally {
      await ctx.close()
    }
  })
})
