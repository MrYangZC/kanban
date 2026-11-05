import { test, expect } from '@playwright/test'
import {
  API, loginApi, createProject, deleteProject, firstStatusId, createIssue, updateIssue,
  listActivities, addCommentViaApi, listAttachments, uploadAttachmentViaApi,
  listLinks, listIssueTags, probeDownload, listProjectStatuses, listTransitions
} from './helpers/api'
import {
  openDefectDetail, openCommentEditor, expectToast, readToastText
} from './helpers/session'
import {
  ensureCollabFixtures, cleanupCollabFixtures, PNG_1X1, type CollabFixtures
} from './helpers/fixtures'

/**
 * 07 工作项详情协作
 *
 * 前端事实（2026-09-29 校准）：详情由 DefectDetailView 渲染，承载在 IssueDetailDrawer → AppDrawer
 * （根容器 .app-drawer-panel）里，从列表/看板/表格点击工作项打开，不跳路由。
 *   - 主标签：动态&评论 / 关联项 / 其他
 *   - 子标签：全部 / 评论·N / 动态
 *   - 评论：底部常驻「点击输入评论…」占位条，点开才渲染 RichTextEditor（textarea），「提交」发送；
 *           删除走二次确认
 *   - 附件：真实文件上传入口在评论编辑器内（「附件」按钮 + 隐藏 file input），
 *           「其他」页签是按文件名登记附件的兼容入口，两者共用同一份附件列表
 *   - 关联项：el-select 远程搜索选择 + 关联类型，选中即建立关联
 *   - 标签：位于右侧信息栏，输入框回车添加，标签上的 X 移除
 * 注意：IssueDetailPanel.vue / IssueDrawer.vue 已无任何引用（死代码），断言一律不使用其选择器。
 */

const PREFIX = 'TEST_COLLAB_'

test.describe('07 工作项详情协作', () => {
  let projectId = ''
  let statusId = ''
  let mainId = ''
  let targetKey = ''
  let activityId = ''
  const mainTitle = `${PREFIX}MAIN`
  const targetTitle = `${PREFIX}TARGET`
  let fx: CollabFixtures

  test.beforeAll(async ({ request }) => {
    await loginApi(request)
    fx = ensureCollabFixtures()
    projectId = await createProject(request, 'TCOL', `${PREFIX}PROJ`)
    statusId = await firstStatusId(request, projectId)

    const main = await createIssue(request, {
      projectId, statusId, issueTypeCode: 'BUG', title: mainTitle, severity: 'S3'
    })
    mainId = main.id

    const target = await createIssue(request, {
      projectId, statusId, issueTypeCode: 'BUG', title: targetTitle, severity: 'S3'
    })
    targetKey = target.issueKey

    // 活动流用例同样走缺陷列表，故与主工作项同类型，避免进不到列表
    const act = await createIssue(request, {
      projectId, statusId, issueTypeCode: 'BUG', title: `${PREFIX}ACT`, severity: 'S3'
    })
    activityId = act.id
  })

  test.afterAll(async ({ request }) => {
    if (projectId) await deleteProject(request, projectId)
    if (fx) cleanupCollabFixtures(fx.dir)
  })

  test('TC-401 发表评论进入动态流并持久化', async ({ page, request }) => {
    const content = `${PREFIX}评论内容A`
    const drawer = await openDefectDetail(page, projectId, mainTitle)

    const editor = await openCommentEditor(page)
    await editor.fill(content)
    await drawer.getByRole('button', { name: '提交' }).click()

    await expect(
      drawer.locator('.comment-content').filter({ hasText: content }).first(),
      '评论应出现在动态流'
    ).toBeVisible({ timeout: 10000 })

    const activities = await listActivities(request, mainId)
    const hit = activities.find((a) => a.type === 'COMMENT' && (a.content || '').includes(content))
    expect(hit, '接口动态流应存在该评论').toBeTruthy()
    expect(hit!.deleted ?? 0, '新评论不应是删除态').toBe(0)
  })

  test('TC-402 空内容评论被前端拦截', async ({ page, request }) => {
    const before = (await listActivities(request, mainId)).filter((a) => a.type === 'COMMENT').length

    await openDefectDetail(page, projectId, mainTitle)
    const editor = await openCommentEditor(page)
    const submit = page.locator('.app-drawer-panel').getByRole('button', { name: '提交' })

    await expect(submit, '空内容时「提交」应禁用').toBeDisabled()
    await editor.fill('   ')
    await expect(submit, '纯空格内容时「提交」应保持禁用').toBeDisabled()

    await editor.press('Control+Enter')
    await page.waitForTimeout(800)

    const after = (await listActivities(request, mainId)).filter((a) => a.type === 'COMMENT').length
    expect(after, '空/纯空格评论不应入库').toBe(before)
  })

  test('TC-403 评论删除需二次确认且删除后不可恢复', async ({ page, request }) => {
    const content = `${PREFIX}待删除评论`
    const created = await addCommentViaApi(request, mainId, content)

    const drawer = await openDefectDetail(page, projectId, mainTitle)
    const block = drawer.locator('div.flex.gap-3').filter({ hasText: content }).last()
    await expect(block, '前置评论应可见').toBeVisible({ timeout: 10000 })

    // 先取消：确认框只是拦截，不应产生删除
    await block.locator('button').last().click()
    const box = page.locator('.el-message-box')
    await expect(box, '删除评论应弹二次确认').toBeVisible({ timeout: 8000 })
    await expect(box).toContainText('确定删除该评论吗')
    await box.getByRole('button', { name: '取消' }).click()
    await expect(box).toBeHidden({ timeout: 8000 })
    await page.waitForTimeout(600)

    let activities = await listActivities(request, mainId)
    expect(
      activities.find((a) => a.id === created.id)?.deleted ?? 0,
      '取消确认后评论不应被删除'
    ).not.toBe(1)

    // 再确认：后端对评论是硬删除，评论直接从动态流消失（不存在「已删除」占位分支）
    await block.locator('button').last().click()
    await box.getByRole('button', { name: '确定' }).click()
    await expect(block, '确认删除后评论应从动态流移除').toBeHidden({ timeout: 10000 })

    activities = await listActivities(request, mainId)
    expect(
      activities.some((a) => a.id === created.id),
      '接口不应再返回已删除的评论'
    ).toBe(false)
  })

  test('TC-404 变更写入动态流（含变更前后值）', async ({ page, request }) => {
    // 后端 recordActivity 只覆盖 CREATED / STATUS_CHANGED，普通字段修改不留痕，
    // 因此「含变更前后值」只能用真实的状态流转验证；改 title 的不留痕问题记为观察项。
    const statuses = await listProjectStatuses(request, projectId)
    const transitions = await listTransitions(request, projectId)
    const from = statuses.find((s) => s.id === statusId)!
    const move = transitions.find((t) => t.fromStatusId === statusId)
    expect(
      move,
      `初始状态「${from.name}」应存在可流转目标（工作流共 ${transitions.length} 条规则）`
    ).toBeTruthy()
    const to = statuses.find((s) => s.id === move!.toStatusId)!

    const newTitle = `${PREFIX}ACT_已改标题`
    await updateIssue(request, activityId, { title: newTitle, statusId: to.id })

    const acts = await listActivities(request, activityId)
    const changed = acts.find((a) => a.type === 'STATUS_CHANGED')
    expect(changed, '状态流转应写入动态').toBeTruthy()
    // 后端在 ActivityTextResolver 里已把状态 code 翻成中文名再下发，前端只负责拼句
    expect(changed!.oldValue ?? '', '动态应记录变更前状态名').toBe(from.name)
    expect(changed!.newValue ?? '', '动态应记录变更后状态名').toBe(to.name)
    expect(
      acts.length,
      '该工作项动态应只有「创建 + 状态变更」两条（title 变更未留痕）'
    ).toBe(2)

    const drawer = await openDefectDetail(page, projectId, newTitle)
    await drawer.getByRole('button', { name: /^动态$/ }).click()

    const row = drawer.locator('div.flex.gap-3').filter({ hasText: '将 状态 从' }).first()
    await expect(row, '动态流应展示状态变更记录').toBeVisible({ timeout: 10000 })
    await expect(row, '动态流应展示变更前状态').toContainText(from.name)
    await expect(row, '动态流应展示变更后状态').toContainText(to.name)

    test.info().annotations.push({
      type: '观察',
      description:
        `PUT 修改 title 等普通字段不写动态流：后端只记录 CREATED 与 STATUS_CHANGED，` +
        `字段级变更没有审计痕迹（本条用例中标题已改为「${newTitle}」但动态流无对应记录）`
    })
  })

  test('TC-405 附件上传成功且下载地址可访问', async ({ page, request }) => {
    const drawer = await openDefectDetail(page, projectId, mainTitle)
    const editor = await openCommentEditor(page)

    await drawer.getByRole('button', { name: '附件' }).click()
    await drawer.locator('input[type="file"]').last().setInputFiles(fx.small)

    await expectToast(page, /上传成功/)
    await expect(editor, '上传成功后应在评论正文插入附件引用').toHaveValue(
      new RegExp(`\\[附件\\]\\(${fx.names.small}`)
    )

    const list = await listAttachments(request, mainId)
    const hit = list.find((a) => a.fileName === fx.names.small)
    expect(hit, '接口附件列表应存在该文件').toBeTruthy()
    expect(hit!.url, '附件应返回可访问 url').toBeTruthy()

    const probe = await probeDownload(request, hit!.url!)
    expect(probe.status, '附件下载地址应返回 200').toBe(200)
    expect(probe.contentType, '附件下载应为图片类型').toContain('image/png')
  })

  test('TC-406 附件删除需二次确认且真正下线', async ({ page, request }) => {
    const name = 'collab-del.png'
    await uploadAttachmentViaApi(request, mainId, { name, mimeType: 'image/png', buffer: PNG_1X1 })

    const drawer = await openDefectDetail(page, projectId, mainTitle)
    await drawer.getByRole('button', { name: '其他' }).click()

    const item = drawer.locator('div.flex.items-center.justify-between').filter({ hasText: name }).first()
    await expect(item, '「其他」页签应列出该附件').toBeVisible({ timeout: 10000 })

    let list = await listAttachments(request, mainId)
    const before = list.find((a) => a.fileName === name)
    expect(before, '前置附件应已登记').toBeTruthy()

    await item.locator('button').last().click()
    const box = page.locator('.el-message-box')
    await expect(box, '删除附件应弹二次确认').toBeVisible({ timeout: 8000 })
    await expect(box).toContainText('确定删除该附件吗')
    await box.getByRole('button', { name: '确定' }).click()

    await expect(item, '确认后附件应从列表移除').toBeHidden({ timeout: 10000 })
    await page.waitForTimeout(600)

    list = await listAttachments(request, mainId)
    expect(list.some((a) => a.fileName === name), '接口不应再返回该附件').toBe(false)

    if (before!.url) {
      // 物理文件被删除，原地址落到应用统一信封（HTTP 200 + application/json），不再是图片内容
      const probe = await probeDownload(request, before!.url)
      expect(probe.contentType, '删除后原下载地址不应再返回图片内容').not.toContain('image/png')
    }
  })

  test('TC-407 超大附件被拒绝且不入库', async ({ page, request }) => {
    const before = await listAttachments(request, mainId)

    const drawer = await openDefectDetail(page, projectId, mainTitle)
    await openCommentEditor(page)
    await drawer.locator('input[type="file"]').last().setInputFiles(fx.oversize)

    const text = await readToastText(page, 'error')
    test.info().annotations.push({
      type: '观察',
      description: `超大附件实际提示文案：「${text}」。容器层 max-file-size=5MB 会直接断连，前端拿不到后端 400 文案，用户看到的是不可读的 Network Error`
    })
    expect(text.length, '超大附件应给出错误提示').toBeGreaterThan(0)
    expect(text, `超大附件不应提示成功，实际：${text}`).not.toMatch(/上传成功/)

    const after = await listAttachments(request, mainId)
    expect(after.length, '超限附件不应入库').toBe(before.length)
    expect(
      after.some((a) => a.fileName === fx.names.oversize),
      '超限文件不应出现在附件列表'
    ).toBe(false)
  })

  test('TC-408 空文件附件被拒绝且不入库', async ({ page, request }) => {
    const before = await listAttachments(request, mainId)

    const drawer = await openDefectDetail(page, projectId, mainTitle)
    await openCommentEditor(page)
    await drawer.locator('input[type="file"]').last().setInputFiles(fx.empty)

    const text = await readToastText(page, 'error')
    expect(text, `空文件提示应明确，实际提示：${text}`).toMatch(/空|不能为空/)

    const after = await listAttachments(request, mainId)
    expect(after.length, '空文件不应入库').toBe(before.length)
  })

  test('TC-409 中文名与特殊字符名附件可上传可下载', async ({ page, request }) => {
    const drawer = await openDefectDetail(page, projectId, mainTitle)
    await openCommentEditor(page)
    const input = drawer.locator('input[type="file"]').last()

    for (const file of [fx.chinese, fx.special]) {
      await input.setInputFiles(file)
      await expectToast(page, /上传成功/)
      await page.waitForTimeout(400)
    }

    const list = await listAttachments(request, mainId)
    for (const name of [fx.names.chinese, fx.names.special]) {
      const hit = list.find((a) => a.fileName === name)
      expect(hit, `接口附件列表应保留原始文件名 ${name}`).toBeTruthy()
      const probe = await probeDownload(request, hit!.url!)
      expect(probe.status, `${name} 应可下载`).toBe(200)
    }

    // 现状记录：file input 未声明 multiple，UI 上一次只能选一个文件，
    // 多文件场景只能在脚本层逐个上传（已在上面覆盖两个文件）
    const multiple = await input.getAttribute('multiple')
    test.info().annotations.push({
      type: '观察',
      description: `附件 file input 的 multiple 属性=${multiple === null ? 'null（不支持一次多选）' : multiple}`
    })
    expect(multiple, '当前 UI 不支持一次多选上传').toBeNull()
  })

  test('TC-410 关联工作项添加与移除', async ({ page, request }) => {
    const drawer = await openDefectDetail(page, projectId, mainTitle)
    await drawer.getByRole('button', { name: /^关联项/ }).click()

    const select = drawer.locator('.el-select').first()
    await select.click()
    const search = select.locator('input').first()

    // 远程搜索走后端 keyword，后端只对 title 做 LIKE，用复合编号「TCOL-xxxx」搜不到任何结果
    // （下拉会显示「无数据」），因此这里用标题片段触发搜索。
    await search.fill(targetTitle)

    const option = page.locator('.el-select-dropdown__item').filter({ hasText: targetTitle }).first()
    await expect(option, `搜索应返回关联目标 ${targetTitle}`).toBeVisible({ timeout: 10000 })
    await expect(option, '候选项应展示工作项真实编号').toContainText(targetKey)

    await option.click({ timeout: 8000 })

    await expect
      .poll(async () => (await listLinks(request, mainId)).length, {
        message: '选中目标工作项后应通过接口建立关联',
        timeout: 15000
      })
      .toBeGreaterThan(0)

    // 已关联行按标题（测试数据里唯一）定位，比按编号定位更稳，避免编号口径变化影响定位
    const linkItem = drawer
      .locator('div.flex.items-center.justify-between')
      .filter({ hasText: targetTitle })
      .first()
    await expect(linkItem, '已关联列表应出现目标工作项').toBeVisible({ timeout: 10000 })

    const links = await listLinks(request, mainId)
    const hit = links.find((l) => l.targetTitle === targetTitle)
    expect(hit, '接口应存在关联记录').toBeTruthy()
    expect(hit!.relationType, '默认关联类型应为「关联」').toBe('关联')

    // 编号口径（回归防护）：后端曾在关联接口里硬编码 `ISSUE-<编号>`，与工作项真实编号
    // （项目 key + 编号，如 TCOL-2）不符，同一工作项在两处显示不同编号。
    // 已改为由 getBriefById 统一下发 issueKey，故这里用硬断言守住，防止回退。
    expect(hit!.targetKey, '接口下发的关联编号应等于工作项真实编号').toBe(targetKey)
    await expect(
      linkItem,
      `已关联列表编号应与工作项真实编号一致（${targetKey}）`
    ).toContainText(targetKey)

    await linkItem.locator('button').last().click()
    await expect
      .poll(async () => (await listLinks(request, mainId)).length, {
        message: '移除后接口不应再有该关联',
        timeout: 15000
      })
      .toBe(0)
    await expect(drawer.getByText('暂无关联'), '移除后应回到空态').toBeVisible({ timeout: 10000 })
  })

  test('TC-411 工作项标签添加与移除', async ({ page, request }) => {
    const tag = `${PREFIX}TAG`
    const drawer = await openDefectDetail(page, projectId, mainTitle)

    const tagInput = drawer.getByPlaceholder('加标签')
    await tagInput.fill(tag)
    await tagInput.press('Enter')

    let tags = await listIssueTags(request, mainId)
    expect(tags.some((t) => t.name === tag), '接口应存在该标签').toBe(true)

    // 标签芯片：带 X 按钮的 span（class 含 rounded），右侧信息栏内
    const chip = drawer
      .locator('span')
      .filter({ hasText: tag })
      .filter({ has: page.locator('button') })
      .last()
    await expect(chip, '右侧信息栏应显示标签').toBeVisible({ timeout: 10000 })

    await chip.locator('button').first().click()
    await page.waitForTimeout(1000)

    tags = await listIssueTags(request, mainId)
    expect(tags.some((t) => t.name === tag), '移除后接口不应再有该标签').toBe(false)
  })
})
