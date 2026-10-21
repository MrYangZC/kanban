import { test } from '@playwright/test'
import { API, loginApi, apiGet, deleteProject } from './helpers/api'

const SUITE_KEYS = ['TUIW', 'TBDG', 'TPLN', 'TCOL', 'TBMEM']

test('probe & purge suite projects', async ({ request }) => {
  await loginApi(request)
  const projects = await apiGet<{ id: string; projectKey: string }[]>(request, `${API}/projects`, '查询项目列表')
  const stale = (projects || []).filter((p) => SUITE_KEYS.includes(p.projectKey))
  console.log('SUITE_KEYS >>>', JSON.stringify(stale.map((p) => p.projectKey)))
  for (const p of stale) {
    await deleteProject(request, p.id)
    console.log('PURGED >>>', p.projectKey)
  }
})
