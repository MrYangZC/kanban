import type { WorkItemCategory } from './types'

/**
 * 内存缓存的 key 工厂。
 *
 * <p>项目类数据按 projectId 隔离，会话内只请求一次；写操作后由调用方精确失效对应 key。
 * 状态字典额外按类别分开：任务/需求/缺陷是三套节点，混用一个 key 会串数据。
 *
 * <p>集中在一处是因为 key 一旦写错（比如 members 用了 project 的前缀），
 * 失效就会失效错对象——表现为"改了成员列表不刷新"这类难查的问题。
 */
export const C = {
  users: 'users',
  project: (id: string) => `project:${id}`,
  members: (id: string) => `members:${id}`,
  statuses: (id: string, category: WorkItemCategory) => `statuses:${id}:${category}`,
  tags: (id: string) => `tags:${id}`
}
