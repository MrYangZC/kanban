import { test, expect, type APIRequestContext, type Locator, type Page } from '@playwright/test'
import {
  API, loginApi, createProject, deleteProject, createIssue, listProjectStatuses, listTransitions, apiGet
} from './helpers/api'
import { gotoAsAdmin } from './helpers/session'

/**
 * 09 看板拖拽落库
 *
 * 前端事实（对照源码校准）：
 *   - BoardView 用 vuedraggable + :force-fallback="true"（走鼠标事件，不是原生 HTML5 拖放），
 *     因此 Playwright 的 dragTo 无效，必须 mouse.down → 分段 move → mouse.up
 *   - fallback-tolerance=8：移动不足 8px 不会进入拖拽态，起步必须先越过阈值
 *   - 列容器带 data-status-id；卡片是列容器的直接子 div（拖拽 item 包装层）
 *   - 跨列是否放行由前端 transitionMap（GET /projects/{id}/transitions）判定，
 *     不允许时列上叠加 .kanban-col-deny 并回弹；允许时为 .kanban-col-allow
 *   - 后端 PATCH /issues/{id}/position 除状态归属外还会校验 canTransition
 *     （BaseWorkItemService.move:352），即流转非法时后端同样 400
 *
 * 历史缺陷 DEF-01（已修复，本文件即其回归防护）：
 *   force-fallback 模式下 Sortable 用 ghost 克隆体表示落点，evt.item 仍是原节点，
 *   于是旧版 dragNeighbors(evt) 读到的是「原位置」的前后兄弟 ——
 *   同列排序恒等于原地不动；源列有多张卡时跨列移动被后端以
 *   「相邻卡片不属于目标状态列」拒绝（卡片回弹，用户只能看到这句后端原文）。
 *   修复：dragNeighbors 改为优先按落点列的绑定数组（vuedraggable 已就地改序）取邻居，
 *   BoardView 与 DefectManageView 两处调用点同步修正，对应 TC-603 / TC-604 / TC-605。
 */

const PREFIX = 'TEST_DRAG_'

/**
 * 需求工作流有 6 列，默认 1440 宽视口只能看到前 4 列。
 * 拖到视口外的列不会产生 dragover（浏览器不会为窗口外坐标派发拖拽事件），
 * 于是「被拒绝」「同列排序」这类用例会假失败 —— 这里放大视口让所有列同屏。
 */
test.use({ viewport: { width: 2200, height: 1000 } })

interface BoardGrouped {
  groups: Array<{
    columns: Array<{ statusId: string; name: string; issues: Array<{ id: string; title: string }> }>
  }>
}

/** 取看板的列数据（服务端真值，用于断言落库结果而非只看 DOM） */
async function boardColumns(
  request: APIRequestContext,
  projectId: string,
  category: 'REQUIREMENT' | 'BUG' = 'REQUIREMENT'
) {
  const board = await apiGet<BoardGrouped>(
    request,
    `${API}/projects/${projectId}/board/grouped?groupBy=STATUS&category=${category}`,
    '查询看板分组'
  )
  return board.groups?.[0]?.columns ?? []
}

/** 列容器（data-status-id 透传在 vuedraggable 根节点上） */
const column = (page: Page, statusId: string) => page.locator(`[data-status-id="${statusId}"]`)

/** 列内卡片（拖拽 item 包装层是列的直接子 div） */
const cards = (page: Page, statusId: string) => page.locator(`[data-status-id="${statusId}"] > div`)

/**
 * 按住卡片并拖到指定坐标，返回时鼠标仍处于按下状态，
 * 便于调用方在「拖拽中」断言列的允许/拒绝高亮，再自行 mouse.up()。
 */
async function dragCardTo(page: Page, card: Locator, target: { x: number; y: number }) {
  const box = await card.boundingBox()
  if (!box) throw new Error('卡片不可见，无法拖拽')
  const sx = box.x + box.width / 2
  const sy = box.y + box.height / 2
  await page.mouse.move(sx, sy)
  await page.mouse.down()
  // 先越过 fallback-tolerance(8px) 才会进入拖拽态
  await page.mouse.move(sx + 12, sy + 12, { steps: 6 })
  await page.mouse.move(target.x, target.y, { steps: 25 })
  // 跨多列长距离拖动时，最后一个 dragover 常常仍落在中途经过的列上（ghost 已插入该列，
  // 光标下的元素变成 ghost 本身），导致目标列高亮不生效。收尾时在原位附近微移两次，
  // 保证最后一次 dragover 确实命中目标列。
  await page.waitForTimeout(120)
  await page.mouse.move(target.x + 6, target.y + 6, { steps: 4 })
  await page.mouse.move(target.x, target.y, { steps: 4 })
  // 等 Vue 把 dragOverCol 的允许/拒绝态渲染到 DOM 再断言
  await page.waitForTimeout(300)
}

async function centerOf(locator: Locator) {
  const box = await locator.boundingBox()
  if (!box) throw new Error('目标元素不可见')
  return { x: box.x + box.width / 2, y: box.y + box.height / 2 }
}

test.describe('09 看板拖拽落库', () => {
  let projectId = ''
  let todoId = ''      // 需求：待处理 REQ_TODO
  let doingId = ''     // 需求：设计中 REQ_DESIGNING
  let closedId = ''    // 需求：已完成 REQ_DONE
  let reopenedId = ''  // 需求：测试中 REQ_TESTING（独占列，避免与其他用例互相干扰）
  let bugTodoId = ''   // 缺陷：待确认 TO_CONFIRM
  let bugDoingId = ''  // 缺陷：处理中 IN_PROGRESS

  test.beforeAll(async ({ request }) => {
    await loginApi(request)
    projectId = await createProject(request, 'TBDG', `${PREFIX}PROJ`)

    // 状态与工作项类别必须匹配：用缺陷工作流的状态去建 STORY 会被后端拒绝
    const statuses = await listProjectStatuses(request, projectId, 'REQUIREMENT')
    const pick = (code: string) => {
      const s = statuses.find((x) => x.code === code)
      if (!s) throw new Error(`需求工作流缺少状态 ${code}，拖拽用例前提不成立`)
      return s.id
    }
    todoId = pick('REQ_TODO')
    doingId = pick('REQ_DESIGNING')
    closedId = pick('REQ_DONE')
    reopenedId = pick('REQ_TESTING')

    const bugStatuses = await listProjectStatuses(request, projectId, 'BUG')
    const pickBug = (code: string) => {
      const s = bugStatuses.find((x) => x.code === code)
      if (!s) throw new Error(`缺陷工作流缺少状态 ${code}，拖拽用例前提不成立`)
      return s.id
    }
    bugTodoId = pickBug('TO_CONFIRM')
    bugDoingId = pickBug('IN_PROGRESS')

    // 前置校验：流转规则必须包含「待处理→设计中」且不含「待处理→已完成」，
    // 否则本组「合法/非法各一条」的前提不成立，应直接失败而不是含糊放过
    const transitions = await listTransitions(request, projectId, 'REQUIREMENT')
    const has = (from: string, to: string) =>
      transitions.some((t) => t.fromStatusId === from && t.toStatusId === to)
    expect(has(todoId, doingId), '需求工作流应有「待处理 → 设计中」').toBe(true)
    expect(has(todoId, closedId), '需求工作流不应有「待处理 → 已完成」').toBe(false)
  })

  test.afterAll(async ({ request }) => {
    if (projectId) await deleteProject(request, projectId)
  })

  test('TC-601 跨列拖拽（合法流转、源列单卡）改变状态并落库', async ({ request, page }) => {
    const title = `${PREFIX}MOVE`
    const issue = await createIssue(request, {
      projectId, statusId: todoId, issueTypeCode: 'STORY', title
    })

    await gotoAsAdmin(page, `/projects/${projectId}/board?type=requirement`)
    await expect(cards(page, todoId).filter({ hasText: title })).toBeVisible({ timeout: 15000 })

    // 源列只放这一张卡：邻居为空，后端按「追加到目标列尾」处理。
    // 源列有多张卡的情形（DEF-01 的触发条件）由 TC-604 覆盖
    const target = await centerOf(column(page, doingId))
    await dragCardTo(page, cards(page, todoId).filter({ hasText: title }), target)
    await expect(page.locator('.kanban-col.kanban-col-allow')).toHaveCount(1)
    await page.mouse.up()

    await expect(cards(page, doingId).filter({ hasText: title })).toBeVisible({ timeout: 10000 })
    await expect(cards(page, todoId).filter({ hasText: title })).toHaveCount(0)

    // 服务端确实改状态，不只是前端挪了 DOM
    const cols = await boardColumns(request, projectId)
    const inDoing = cols.find((c) => c.statusId === doingId)
    expect(inDoing?.issues.some((i) => i.id === issue.id), '处理中列应包含该工作项').toBe(true)

    // 刷新后仍在新列（持久化）
    await page.reload()
    await expect(cards(page, doingId).filter({ hasText: title })).toBeVisible({ timeout: 15000 })
  })

  test('TC-602 无流转规则的跨列拖拽被拦截且不落库', async ({ request, page }) => {
    const title = `${PREFIX}DENY`
    const issue = await createIssue(request, {
      projectId, statusId: todoId, issueTypeCode: 'STORY', title
    })

    await gotoAsAdmin(page, `/projects/${projectId}/board?type=requirement`)
    await expect(cards(page, todoId).filter({ hasText: title })).toBeVisible({ timeout: 15000 })

    const target = await centerOf(column(page, closedId))
    await dragCardTo(page, cards(page, todoId).filter({ hasText: title }), target)
    // 拖拽中：目标列给出「拒绝」高亮，这是前端拦截的可视化证据
    try {
      await expect(page.locator('.kanban-col.kanban-col-deny')).toHaveCount(1)
    } finally {
      await page.mouse.up()
    }

    // 回弹：卡片仍在原列
    await expect(cards(page, todoId).filter({ hasText: title })).toBeVisible({ timeout: 10000 })
    await expect(cards(page, closedId).filter({ hasText: title })).toHaveCount(0)

    // 状态未被写库
    const cols = await boardColumns(request, projectId)
    expect(
      cols.find((c) => c.statusId === todoId)?.issues.some((i) => i.id === issue.id),
      '被拒绝后仍应留在待确认列'
    ).toBe(true)
    expect(
      cols.find((c) => c.statusId === closedId)?.issues.some((i) => i.id === issue.id),
      '已关闭列不应出现该工作项'
    ).toBe(false)
  })

  test('TC-603 同列拖拽排序应持久化', async ({ request, page }) => {
    const first = `${PREFIX}SORT_A`
    const second = `${PREFIX}SORT_B`
    // 放在独占列（再次打开）里，保证只有这两张卡，排序断言不受其他用例干扰
    const a = await createIssue(request, { projectId, statusId: reopenedId, issueTypeCode: 'STORY', title: first })
    const b = await createIssue(request, { projectId, statusId: reopenedId, issueTypeCode: 'STORY', title: second })

    await gotoAsAdmin(page, `/projects/${projectId}/board?type=requirement`)
    await expect(cards(page, reopenedId).filter({ hasText: first })).toBeVisible({ timeout: 15000 })
    await expect(cards(page, reopenedId).filter({ hasText: second })).toBeVisible()

    const orderOf = async () => {
      const texts = await cards(page, reopenedId).allInnerTexts()
      return texts.map((t) => (t.includes(first) ? 'A' : t.includes(second) ? 'B' : '?')).join('')
    }
    const before = await orderOf()
    expect(before, '列内应恰有 A、B 两张卡').toHaveLength(2)

    // 把靠上的一张拖到靠下那张的下缘，触发同列换序
    const upperIndex = before.indexOf('A') === 0 ? 0 : 1
    const lowerIndex = upperIndex === 0 ? 1 : 0
    const lowerBox = await cards(page, reopenedId).nth(lowerIndex).boundingBox()
    if (!lowerBox) throw new Error('目标卡片不可见')
    const target = { x: lowerBox.x + lowerBox.width / 2, y: lowerBox.y + lowerBox.height - 4 }

    await dragCardTo(page, cards(page, reopenedId).nth(upperIndex), target)
    await page.mouse.up()

    // vuedraggable 在 @end 里会先把节点还原到原位（onDragUpdate 的 insertNodeAt），
    // 等 onEnd 的 moveIssue + load() 回来后才按服务端顺序重排 —— 必须轮询而非立即断言
    await expect
      .poll(orderOf, { timeout: 10000, message: '同列拖拽后顺序应发生变化' })
      .not.toBe(before)
    const after = await orderOf()
    expect(after, '列内应仍是 A、B 两张卡').toHaveLength(2)

    // 服务端顺序与 UI 一致（后端按 sort_key 升序渲染）
    const cols = await boardColumns(request, projectId)
    const col = cols.find((c) => c.statusId === reopenedId)
    const serverIds = (col?.issues ?? []).map((i) => i.id).filter((id) => id === a.id || id === b.id)
    expect(serverIds, '服务端排序应与拖拽后的顺序一致').toEqual(after === 'AB' ? [a.id, b.id] : [b.id, a.id])

    // 刷新后顺序保持
    await page.reload()
    await expect(cards(page, reopenedId).filter({ hasText: first })).toBeVisible({ timeout: 15000 })
    expect(await orderOf(), '刷新后顺序应保持拖拽结果').toBe(after)
  })

  test('TC-604 源列存在多张卡时跨列拖拽仍应成功', async ({ request, page }) => {
    const first = `${PREFIX}MULTI_A`
    const second = `${PREFIX}MULTI_B`
    const a = await createIssue(request, { projectId, statusId: todoId, issueTypeCode: 'STORY', title: first })
    const b = await createIssue(request, { projectId, statusId: todoId, issueTypeCode: 'STORY', title: second })

    await gotoAsAdmin(page, `/projects/${projectId}/board?type=requirement`)
    await expect(cards(page, todoId).filter({ hasText: first })).toBeVisible({ timeout: 15000 })

    // 拖「第二张卡」，其原位存在兄弟卡 —— 正是触发缺陷的条件
    const target = await centerOf(column(page, doingId))
    await dragCardTo(page, cards(page, todoId).filter({ hasText: second }), target)
    await page.mouse.up()

    await expect(cards(page, doingId).filter({ hasText: second })).toBeVisible({ timeout: 10000 })
    const cols = await boardColumns(request, projectId)
    const doing = cols.find((c) => c.statusId === doingId)
    expect(doing?.issues.some((i) => i.id === b.id), '处理中列应包含被拖拽的工作项').toBe(true)
    expect(
      cols.find((c) => c.statusId === todoId)?.issues.some((i) => i.id === a.id),
      '未被拖拽的卡片应留在原列'
    ).toBe(true)
  })

  test('TC-605 缺陷看板跨列拖拽（源列多卡）同样落库', async ({ request, page }) => {
    const first = `${PREFIX}BUG_A`
    const second = `${PREFIX}BUG_B`
    await createIssue(request, { projectId, statusId: bugTodoId, issueTypeCode: 'BUG', title: first })
    const moved = await createIssue(request, { projectId, statusId: bugTodoId, issueTypeCode: 'BUG', title: second })

    await gotoAsAdmin(page, `/projects/${projectId}/defects`)
    // 缺陷页视图存在 localStorage，默认是列表视图，这里固定切到看板
    await page.evaluate(
      (pid) => localStorage.setItem(`kanban:defectView:${pid}`, 'board'),
      projectId
    )
    await page.reload()
    await expect(cards(page, bugTodoId).filter({ hasText: first })).toBeVisible({ timeout: 15000 })

    // DefectManageView 与 BoardView 共用 dragNeighbors，同一缺陷曾同时存在于两处，这里单独回归
    const target = await centerOf(column(page, bugDoingId))
    await dragCardTo(page, cards(page, bugTodoId).filter({ hasText: second }), target)
    await page.mouse.up()

    await expect(cards(page, bugDoingId).filter({ hasText: second })).toBeVisible({ timeout: 10000 })
    const cols = await boardColumns(request, projectId, 'BUG')
    expect(
      cols.find((c) => c.statusId === bugDoingId)?.issues.some((i) => i.id === moved.id),
      '缺陷看板：处理中列应包含被拖拽的缺陷'
    ).toBe(true)
  })

  test('TC-606 拖到目标列两张卡之间应插入中间位置', async ({ request, page }) => {
    const upper = `${PREFIX}POS_A`
    const lower = `${PREFIX}POS_B`
    const source = `${PREFIX}POS_SRC`
    // 目标列预置两张卡 + 源列一张卡。只断言这 3 张的相对顺序，
    // 这样不受本文件其他用例在同一列留下的卡片影响
    const a = await createIssue(request, { projectId, statusId: doingId, issueTypeCode: 'STORY', title: upper })
    const b = await createIssue(request, { projectId, statusId: doingId, issueTypeCode: 'STORY', title: lower })
    const moved = await createIssue(request, { projectId, statusId: todoId, issueTypeCode: 'STORY', title: source })

    await gotoAsAdmin(page, `/projects/${projectId}/board?type=requirement`)
    await expect(cards(page, doingId).filter({ hasText: upper })).toBeVisible({ timeout: 15000 })
    await expect(cards(page, doingId).filter({ hasText: lower })).toBeVisible()
    await expect(cards(page, todoId).filter({ hasText: source })).toBeVisible()

    // 落在 B 上缘 1/4 处：Sortable 以「越过卡片中线」判定插入位置，
    // 停在 B 中线之上即插到 B 之前，也就是 A、B 之间
    const lowerBox = await cards(page, doingId).filter({ hasText: lower }).boundingBox()
    if (!lowerBox) throw new Error('目标卡片不可见')
    const target = { x: lowerBox.x + lowerBox.width / 2, y: lowerBox.y + lowerBox.height * 0.25 }

    await dragCardTo(page, cards(page, todoId).filter({ hasText: source }), target)
    await page.mouse.up()

    await expect(cards(page, doingId).filter({ hasText: source })).toBeVisible({ timeout: 10000 })

    // 服务端顺序：A 在前、被拖卡片居中、B 在后
    const serverOrder = async () => {
      const cols = await boardColumns(request, projectId)
      return (cols.find((c) => c.statusId === doingId)?.issues ?? []).map((i) => i.id)
    }
    await expect
      .poll(
        async () => {
          const ids = await serverOrder()
          const [ia, im, ib] = [ids.indexOf(a.id), ids.indexOf(moved.id), ids.indexOf(b.id)]
          return ia >= 0 && im >= 0 && ib >= 0 && ia < im && im < ib
        },
        { timeout: 10000, message: '被拖拽卡片应落在 A、B 之间，而不是列尾' }
      )
      .toBe(true)

    // DOM 与之一致
    const domTexts = await cards(page, doingId).allInnerTexts()
    const at = (title: string) => domTexts.findIndex((t) => t.includes(title))
    expect(at(upper), 'A 应在被拖卡片之前').toBeLessThan(at(source))
    expect(at(source), '被拖卡片应在 B 之前').toBeLessThan(at(lower))
  })
})
