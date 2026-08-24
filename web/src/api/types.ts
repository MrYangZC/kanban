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
  canEdit?: boolean
  canManage?: boolean
}

/** 项目分类筛选键 */
export type ProjectCategoryKey = 'all' | 'managed' | 'participated' | 'favorited' | 'archived'

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
}

export interface UserVO {
  id: string
  username: string
  displayName: string
  email: string | null
  avatarUrl: string | null
  enabled: boolean
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
  parentId?: string | null   // 嵌套回复父评论 id，顶楼评论为 null/undefined
  deleted?: number | null    // 1=已逻辑删除（显示「已删除」占位），0/undefined=正常
  content: string | null
  oldValue: string | null
  newValue: string | null
  createdAt: string
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
  summary: { total: number; inProgress: number; done: number; overdue: number }
  statusDist: { name: string; count: number }[]
  priorityDist: { name: string; count: number }[]
  assigneeDist: { name: string; count: number }[]
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
  token: string
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
