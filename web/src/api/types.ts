export interface ProjectStats {
  projectId: string
  memberCount: number
  issueCount: number
  attachmentCount: number
}

export interface Project {
  id: string
  projectKey: string
  name: string
  description: string
  status: string
  leadUserId: string
  createdAt: string
  stats?: ProjectStats
  favorited?: boolean
  /** 权限码 project:setting（OWNER/ADMIN/PM） */
  canEdit?: boolean
  /** 权限码 project:member（OWNER/ADMIN/PM） */
  canManage?: boolean
  /** 权限码 project:archive（种子数据仅 OWNER/ADMIN） */
  canArchive?: boolean
  /** 权限码 project:delete（种子数据仅 OWNER/ADMIN） */
  canDelete?: boolean
}

/** 项目分类筛选键 */
export type ProjectCategoryKey = 'all' | 'managed' | 'participated' | 'favorited' | 'archived'

/**
 * `/v1/issues/filter` 请求体，与后端 `IssueFilterRequest` 字段对齐。
 *
 * <p>抽成公共类型的原因：这个结构此前在列表页、缺陷页、看板页、表格页、范围计数里
 * 各拼一遍，字段增删要改 5 处，漏改一处就是"某个视图筛选不生效"。
 */
export interface IssueFilterPayload {
  projectId: string
  scope?: string
  statusId?: string
  priority?: string
  severity?: string
  labelId?: string
  keyword?: string
  /** 负责人过滤（UUID）；与 scope=ASSIGNED（只看我负责的）并存，是"任选一名成员" */
  assigneeId?: string
  /** 创建者过滤（UUID） */
  reporterId?: string
  /** 创建时间区间（yyyy-MM-dd，两端都含当天） */
  createdFrom?: string
  createdTo?: string
  /** 更新时间区间（yyyy-MM-dd，两端都含当天） */
  updatedFrom?: string
  updatedTo?: string
  /** 计划完成（due_date）区间（yyyy-MM-dd，两端都含当天） */
  dueFrom?: string
  dueTo?: string
  /** 状态类别过滤：TODO / IN_PROGRESS / DONE（度量页点统计数字跳转时用） */
  statusCategory?: string
  /** 只看逾期（未完成且已过截止日） */
  overdue?: boolean
  issueTypeCodes?: string[]
  /** 迭代过滤：为空表示不过滤，后端会跨 task/requirement/bug 三表查询 */
  sprintId?: string
  page?: number
  size?: number
  sortField?: string
  sortOrder?: string
}

/** 项目分类计数 */
export interface ProjectCategoryCounts {
  all: number
  managed: number
  participated: number
  favorited: number
  archived: number
}

/** 项目列表视图模式 */
export type ViewMode = 'card' | 'list'

/** 项目列表分页查询参数 */
export interface ProjectFilterRequest {
  category?: ProjectCategoryKey
  keyword?: string
  page?: number
  size?: number
  sortField?: string
  sortOrder?: string
}

export interface LabelVO {
  id: string
  name: string
  color: string
}

export interface IssueStatusVO {
  id: string
  name: string
  code: string
  category: string
  color: string
  sortOrder: number
}

export interface Issue {
  id: string
  projectId: string
  issueNumber: string
  issueKey: string
  issueTypeId: string
  statusId: string
  sprintId: string | null
  title: string
  description: string
  priority: string
  reporterId: string
  assigneeId: string
  assigneeName: string | null
  reporterName: string | null
  /** 对应人员的头像地址，列表/卡片人员列显示真实头像 */
  assigneeAvatarUrl?: string | null
  reporterAvatarUrl?: string | null
  verifierAvatarUrl?: string | null
  statusCode: string
  issueTypeCode: string
  issueTypeIcon: string
  issueTypeName: string
  severity: string
  module: string | null
  archived: number
  dueDate: string | null
  verifierId: string | null
  verifierName: string | null
  fixVersion: string | null
  source: string | null
  iteration: string | null
  participants: string[] | null
  ccUsers: string[] | null
  planStart: string | null
  noFixReason: string | null
  labels: LabelVO[]
  commentCount: number
  sortKey: string
  parentId: string | null
  createdAt: string
  updatedAt: string
  /** 实际完成时间（进入「已完成」类别时落值） */
  doneAt?: string | null
}

/**
 * 工作项树节点：由前端根据 parentId 组装（非接口返回结构）。
 * 用于列表页"树形视图"的渲染。
 */
export interface IssueTreeNode extends Issue {
  children: IssueTreeNode[]
}

export interface UserVO {
  id: string
  username: string
  displayName: string
  email: string | null
  avatarUrl: string | null
  enabled: boolean
  isAdmin?: boolean | null
}

/**
 * 人员下拉用的精简用户（GET /users/options）。
 * 与 UserVO 刻意区分：不含 email/enabled，任何登录用户可读（负责人/验证人/按人筛选用）。
 */
export interface UserOption {
  id: string
  username: string
  displayName: string
  avatarUrl: string | null
}

export interface BoardColumnView {
  id: string
  statusId: string
  name: string
  category: string
  color: string
  sortOrder: number
  wipLimit: number | null
  issues: Issue[]
}

export interface IssueActivity {
  id: string
  type: string
  actorId: string
  actorName: string
  /** 操作人头像地址（/api/uploads/xxx），为空时用首字头像 */
  actorAvatarUrl?: string | null
  parentId?: string | null   // 嵌套回复父评论 id，顶楼评论为 null/undefined
  deleted?: number | null    // 1=已逻辑删除（显示「已删除」占位），0/undefined=正常
  content: string | null
  oldValue: string | null
  newValue: string | null
  createdAt: string
  /** 仅交接动态会有：后端判定的「这次交接现在还能不能撤销」，前端只按标记显示按钮 */
  revocable?: boolean | null
}

/**
 * 工作项时间线一页（评论 + 动态）。
 * 比通用 PageResult 多带 commentCount：分页后前端手里的评论数只是当前页，
 * 「评论·N」角标必须用后端给的全量计数。
 */
export interface IssueTimeline {
  total: number
  commentCount: number
  page: number
  size: number
  list: IssueActivity[]
}

export interface AttachmentVO {
  id: string
  issueId: string
  fileName: string
  contentType: string
  fileSize: number
  url?: string
  uploadedBy: string
  uploaderName: string
  createdAt: string
}

/** 图片上传结果 */
export interface UploadImageVO {
  url: string
}

/** 工作流流转规则：fromStatus → toStatus 是否允许（看板拖拽蓝/红提示依据） */
export interface WorkflowTransitionVO {
  fromStatusId: string
  toStatusId: string
  name: string
}

/** 进入某一状态列后，负责人是否被转交 */
export type HandoffType = 'NONE' | 'MEMBER' | 'PROJECT_LEAD'

/** 工作流的一列（状态）+ 该列的交接配置 */
export interface WorkflowColumnVO {
  statusId: string
  name: string
  category: string
  color: string
  sortOrder: number
  handoffType: HandoffType
  /** 解析后的目标处理人，PROJECT_LEAD 时已替换为项目负责人 */
  handoffUserId?: string | null
  handoffUserName?: string | null
  handoffNote?: string | null
  /** 落点迭代：交接时把工作项放进这个迭代，可空 */
  targetSprintId?: string | null
  targetSprintName?: string | null
  /** 落点标签：交接时给工作项补上这个标签，可空 */
  targetLabelId?: string | null
  targetLabelName?: string | null
}

/** 保存某一列的交接配置 */
export interface SaveHandoffRequest {
  handoffType: HandoffType
  handoffUserId?: string | null
  handoffNote?: string | null
  /** 落点迭代，可空；handoffType = NONE 时后端会忽略 */
  targetSprintId?: string | null
  /** 落点标签，可空；handoffType = NONE 时后端会忽略 */
  targetLabelId?: string | null
}

/** 站内通知 */
export interface NotificationVO {
  id: string
  type: string
  title: string
  body?: string | null
  projectId?: string | null
  issueId?: string | null
  /** 工作项大类，决定跳转路由 */
  category?: string | null
  readAt?: string | null
  createdAt: string
}

export interface LinkVO {
  id: string
  issueId: string
  targetIssueId: string
  relationType: string
  targetKey: string
  targetTitle: string
  createdAt: string
}

export interface BoardView {
  id: string
  projectId: string
  name: string
  columns: BoardColumnView[]
}

export interface GroupColumnView {
  statusId: string
  name: string
  category: string
  color: string
  sortOrder: number
  issues: Issue[]
  /** 进入本列后的交接配置，由后端随看板一次返回，避免逐列再请求 */
  handoffType?: HandoffType
  handoffUserId?: string | null
  handoffUserName?: string | null
  handoffNote?: string | null
  targetSprintId?: string | null
  targetSprintName?: string | null
  targetLabelId?: string | null
  targetLabelName?: string | null
}

export interface BoardGroupView {
  name: string
  groupBy: string
  groups: {
    groupKey: string
    groupName: string
    issueCount: number
    columns: GroupColumnView[]
  }[]
}

export interface DashboardVO {
  /** todo/inProgress/done 按 issue_status.category 真实统计（inProgress 不再等同于「未完成」） */
  summary: { total: number; todo: number; inProgress: number; done: number; overdue: number }
  /** 分类型概览，顺序固定：需求(REQUIREMENT) / 缺陷(BUG) / 任务(TASK) */
  typeSummaries: {
    typeCode: 'REQUIREMENT' | 'BUG' | 'TASK'
    typeName: string
    total: number
    todo: number
    inProgress: number
    done: number
    overdue: number
  }[]
  statusDist: { name: string; count: number }[]
  priorityDist: { name: string; count: number }[]
  /** 按负责人工作量：name 为显示名（未分配兜底），avatarUrl 为空时前端回退首字 */
  assigneeDist: { name: string; count: number; avatarUrl?: string | null }[]
  cycleTime: { avgHours: number; maxHours: number }
  burndown: { date: string; remaining: number; completed: number }[]
}

export interface PageResult<T> {
  total: number
  pageNum: number
  pageSize: number
  list: T[]
}

export interface LoginVO {
  id: string
  username: string
  displayName: string
  /** 头像地址（/api/uploads/xxx），为空时回退首字头像 */
  avatarUrl?: string | null
  /**
   * 是否系统管理员。由后端按 sys_user.is_admin 或种子管理员 UUID 的口径下发，
   * 前端不应再用 username === 'admin' 自行推断（与后端 AdminAuthInterceptor 口径不一致）。
   */
  isAdmin?: boolean
  /** 访问令牌（JWT），请求时放在 Authorization: Bearer 里 */
  token?: string
  /** token 过期时刻（epoch 毫秒），用于提前提示重新登录 */
  expiresAt?: number
}

/** 当前登录用户个人资料 */
export interface ProfileVO {
  id: string
  username: string
  displayName: string
  email: string | null
  avatarUrl: string | null
  enabled: boolean
  createdAt: string | null
}

/** 修改个人资料请求 */
export interface UpdateProfileRequest {
  displayName?: string
  email?: string
  avatarUrl?: string
}

/** 修改密码请求 */
export interface ChangePasswordRequest {
  oldPassword: string
  newPassword: string
}

export interface ProjectMember {
  id: string
  projectId: string
  userId: string
  username: string
  displayName: string
  avatarUrl: string | null
  roles: string[]
  createdAt: string
}

/** 「添加成员」检索出的候选用户（尚未加入项目） */
export interface MemberCandidate {
  id: string
  username: string
  displayName: string
  avatarUrl: string | null
}

export interface MilestoneVO {
  id: string
  projectId: string
  name: string
  description: string | null
  dueDate: string | null
  status: string
  progress: number
  creatorName: string | null
  createdAt: string
  updatedAt: string
}

export interface AnnouncementVO {
  id: string
  projectId: string
  title: string
  content: string | null
  pinned: number
  authorName: string | null
  createdAt: string
  updatedAt: string
}

export interface ProjectActivityVO {
  id: string
  actorId: string
  actorName: string | null
  /** 操作人头像地址（/api/uploads/xxx），为空时用首字头像 */
  actorAvatarUrl?: string | null
  action: string
  issueId: string
  issueNumber: string
  issueTitle: string
  oldValue: string | null
  newValue: string | null
  createdAt: string
}

// ---- Sprint (迭代) ----
export interface SprintVO {
  id: string
  projectId: string
  name: string
  goal?: string
  status: 'PLANNED' | 'ACTIVE' | 'COMPLETED'
  startDate?: string
  endDate?: string
  creatorName?: string
  createdAt?: string
  updatedAt?: string
}

// ---- Risk (风险) ----
export interface RiskVO {
  id: string
  projectId: string
  title: string
  description?: string
  level: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  status: 'OPEN' | 'MITIGATING' | 'CLOSED'
  ownerId?: string
  ownerName?: string
  dueDate?: string
  creatorName?: string
  createdAt?: string
  updatedAt?: string
}

// ---- RBAC (全局管理) ----
export interface RbacRole {
  id: string
  code: string
  name: string
  description?: string
  sortOrder?: number
}

export interface RbacMenu {
  id: string
  code: string
  name: string
  parentId?: string
  /** 当前仅 MENU，按钮权限已收敛至 permission 表 */
  type?: string
  sortOrder?: number
}

export interface PermissionDef {
  code: string
  name: string
  description?: string
  module: string
  sortOrder: number
}

// ---- 工作项类型编码分类（与后端 issue_type 表 seed 数据一致） ----
/** 需求类（故事 + 特性） */
export const REQUIREMENT_TYPE_CODES: string[] = ['STORY', 'FEATURE']
/** 任务类 */
export const TASK_TYPE_CODES: string[] = ['TASK']
/** 缺陷类 */
export const DEFECT_TYPE_CODES: string[] = ['BUG', 'FUNC_DEFECT', 'PERF_DEFECT', 'SEC_DEFECT', 'COMPAT_DEFECT', 'UI_DEFECT']

/** 工作项大类：工作流、状态字典、看板列均按它隔离（与后端 ItemCategory 对应） */
export type WorkItemCategory = 'TASK' | 'REQUIREMENT' | 'BUG'

/**
 * 由工作项类型编码推出所属大类。
 * 任务「待办→进行中→已完成」、需求「待处理→设计中→开发中→测试中→已完成」、
 * 缺陷「待确认→处理中→已修复→已关闭」是三套不同状态，取状态/流转规则前必须先定类别。
 */
export function categoryOfTypeCode(code?: string | null): WorkItemCategory {
  const c = (code || '').toUpperCase()
  if (DEFECT_TYPE_CODES.includes(c)) return 'BUG'
  if (REQUIREMENT_TYPE_CODES.includes(c)) return 'REQUIREMENT'
  return 'TASK'
}
