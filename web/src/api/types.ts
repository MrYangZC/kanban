export interface Workspace {
  id: string
  name: string
  workspaceKey: string
  description: string
  avatarUrl: string
  createdAt: string
}

export interface ProjectStats {
  projectId: string
  memberCount: number
  issueCount: number
  attachmentCount: number
}

export interface Project {
  id: string
  workspaceId: string
  projectKey: string
  name: string
  description: string
  status: string
  leadUserId: string
  createdAt: string
  stats?: ProjectStats
}

export interface LabelVO {
  id: string
  name: string
  color: string
}

export interface IssueStatusVO {
  id: string
  projectId: string
  name: string
  code: string
  category: string
  color: string
  sortOrder: number
}

export interface Issue {
  id: string
  projectId: string
  issueNumber: number
  issueKey: string
  issueTypeId: string
  statusId: string
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
  uploadedBy: string
  uploaderName: string
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

export interface ProjectMember {
  id: string
  projectId: string
  userId: string
  username: string
  displayName: string
  avatarUrl: string | null
  role: 'OWNER' | 'MEMBER' | 'VIEWER'
  createdAt: string
}
