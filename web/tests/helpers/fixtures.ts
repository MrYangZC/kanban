import { mkdirSync, rmSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'
import { tmpdir } from 'node:os'

/**
 * 附件用例的测试文件。
 * 后端对上传做了魔数校验（文件内容必须与扩展名一致），所以「正常」文件必须是真实 PNG 字节，
 * 不能用文本占位；超限文件在合法 PNG 头之后补零，保证走的是「大小超限」而不是「魔数不符」分支。
 */
const PNG_1X1_BASE64 =
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8DwHwAFAAH/q842iQAAAABJRU5ErkJggg=='

export const PNG_1X1 = Buffer.from(PNG_1X1_BASE64, 'base64')

/** 合法 PNG 头 + 补零到指定大小（默认 6MB，超过后端 5MB 上限） */
export function oversizePng(bytes = 6 * 1024 * 1024) {
  const buf = Buffer.alloc(bytes)
  PNG_1X1.copy(buf, 0)
  return buf
}

export interface CollabFixtures {
  dir: string
  small: string
  oversize: string
  empty: string
  chinese: string
  special: string
  names: { small: string; chinese: string; special: string; empty: string; oversize: string }
}

/** 在系统临时目录生成附件用例文件（不污染仓库），afterAll 调用 cleanupCollabFixtures 清理 */
export function ensureCollabFixtures(dirName = 'kanban-collab-fixtures'): CollabFixtures {
  const dir = join(tmpdir(), dirName)
  mkdirSync(dir, { recursive: true })

  const names = {
    small: 'collab-shot.png',
    oversize: 'collab-oversize.png',
    empty: 'collab-empty.png',
    chinese: '中文名截图.png',
    special: 'a b#&!@特殊 名.png'
  }

  const small = join(dir, names.small)
  const oversize = join(dir, names.oversize)
  const empty = join(dir, names.empty)
  const chinese = join(dir, names.chinese)
  const special = join(dir, names.special)

  writeFileSync(small, PNG_1X1)
  writeFileSync(oversize, oversizePng())
  writeFileSync(empty, Buffer.alloc(0))
  writeFileSync(chinese, PNG_1X1)
  writeFileSync(special, PNG_1X1)

  return { dir, small, oversize, empty, chinese, special, names }
}

export function cleanupCollabFixtures(dir: string) {
  try {
    rmSync(dir, { recursive: true, force: true })
  } catch {
    /* 清理失败不影响用例结论 */
  }
}
