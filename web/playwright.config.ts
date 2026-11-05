import { defineConfig } from '@playwright/test'

/**
 * 每次运行使用带时间戳的独立产物目录。
 * 原因：Playwright 启动时会先清空 outputDir，而本机安全删除钩子对既有产物目录
 * （尤其是中文路径或文件数较多的目录）会失败，导致整轮测试在启动阶段就中断。
 * 用新的空目录可完全绕开该删除动作。
 */
const runStamp = new Date().toISOString().replace(/\.\d+Z$/, '').replace(/[:T]/g, '-')

export default defineConfig({
  testDir: './tests',
  timeout: 30000,
  retries: 0,
  workers: 1,
  // testDir 下的 `_xxx.spec.ts` 是调试/清理脚本（例如 _dbg-keys 会删除指定 key 的项目），
  // 不能跟着正式用例一起跑：默认执行 `npx playwright test` 时必须排除掉。
  testIgnore: ['**/_*.spec.ts'],
  // 产物目录保持 ASCII 路径（本机安全删除钩子对中文路径会失败），
  // 执行结束后由测试脚本/人工把 HTML 报告归档到仓库根目录的「测试」文件夹
  reporter: [['list'], ['html', { outputFolder: `./playwright-report/${runStamp}`, open: 'never' }]],
  use: {
    // 端口可被 PW_BASE_URL 覆盖：本机 5173 常被其他项目的前端占用，
    // 此时用 `PW_BASE_URL=http://localhost:5273 npx playwright test` 指向本仓库的 dev server
    baseURL: process.env.PW_BASE_URL || 'http://localhost:5173',
    channel: 'chrome',
    headless: true,
    viewport: { width: 1440, height: 900 },
    screenshot: 'only-on-failure',
    // 默认关闭 trace：失败用例多时 trace.zip 会让 outputDir 体积暴涨，
    // 触发本机批量删除保护导致下次运行无法清理 outputDir。
    // 需要精查单条用例时用 PW_TRACE=1 运行：PW_TRACE=1 npx playwright test tests/xx.spec.ts --grep "TC-xxx"
    trace: process.env.PW_TRACE === '1' ? 'retain-on-failure' : 'off',
    // 关闭录屏：产物文件量过大时会触发本机批量删除保护，导致 Playwright 无法清理 outputDir
    video: 'off',
    actionTimeout: 15000,
    navigationTimeout: 20000
  },
  outputDir: `./test-results/${runStamp}`
})
