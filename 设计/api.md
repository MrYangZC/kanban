> **【最新状态 · 2026-08-24 已落地】**
>
> 本文档为设计阶段 API 规范讨论稿，接口语义已按两次架构级重构调整，以当前 `server/src/main/java/com/seven7m/kanban/controller` 与 `web/src/api` 为准，本文档仅作设计演进记录。
>
> **接口语义关键变更：**
> - Workspace 相关接口随 workspace 模块移除而废弃。
> - 状态/类型/标签/工作流接口由「项目维度」改为「全局字典 + 项目选用」：全局字典由全局接口管理，项目通过 `workflow_id` 绑定模板、通过 `project_label` 选用标签。
> - 标签选择器改为「全局标签库 + 项目选用关联」。
> - 看板列不再返回冗余 `name`，由 `statusId → issue_status.name` 获取。
> - 角色授权基于 `role_id`，菜单仅导航、按钮权限由 `permission` 承载。
>
> **权威参考：** 后端 `server/src/main/java/com/seven7m/kanban/controller`；前端 `web/src/api`。
>
> ---

可以。API 这一层建议现在直接定成一套规范，后续前后端所有接口都严格遵循。你明确要求 `code / msg / data`，那我建议不要再用 `message`，统一就是：

```json
{
  "code": 0,
  "msg": "success",
  "data": {}
}
```

其中：

```text
code = 0      成功
code != 0     业务/系统错误
msg            给前端展示或开发定位的信息
data           业务数据
```

下面给你一份可以直接放进 `docs/04-api/api-design.md` 的版本。

# API 设计规范 V1.0

## 1. 基础规范

协议：

```text
HTTP/HTTPS
RESTful API
JSON
UTF-8
```

统一前缀：

```text
/api/v1
```

例如：

```text
GET /api/v1/projects
GET /api/v1/projects/{projectId}
POST /api/v1/projects
```

请求头：

```http
Content-Type: application/json
Authorization: Bearer {token}
```

---

# 2. 统一响应结构

所有 API 统一：

```json
{
  "code": 0,
  "msg": "success",
  "data": {}
}
```

成功：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": "018f4c9a-..."
  }
}
```

失败：

```json
{
  "code": 40001,
  "msg": "参数校验失败",
  "data": null
}
```

系统异常：

```json
{
  "code": 50000,
  "msg": "系统内部错误",
  "data": null
}
```

---

# 3. Code 规范

建议不要直接把 HTTP Status 当业务 Code。

统一：

```text
0       SUCCESS

40000   INVALID_PARAMETER
40001   VALIDATION_ERROR

40100   UNAUTHORIZED
40300   FORBIDDEN

40400   NOT_FOUND

40900   CONFLICT
40901   BUSINESS_CONFLICT
40902   INVALID_STATE_TRANSITION

42900   TOO_MANY_REQUESTS

50000   INTERNAL_ERROR
50001   DATABASE_ERROR
50002   THIRD_PARTY_ERROR
```

业务模块可以继续细分：

```text
1xxxx   User
2xxxx   Workspace
3xxxx   Project
4xxxx   Issue
5xxxx   Workflow
6xxxx   Board
7xxxx   Sprint
```

例如：

```text
40001
```

表示通用参数错误。

```text
40001
```

不要让不同模块各自解释成不同含义。

如果需要模块化错误码，可以：

```text
41001 = Issue 不存在
41002 = Issue 已删除
41003 = Issue 状态不允许变更
41004 = Issue 无权限操作
```

我更推荐这种。

---

# 4. HTTP Status 与 code 的关系

HTTP Status 依然正确使用：

```text
200
400
401
403
404
409
422
500
```

但是业务 Code 独立存在。

例如：

```http
HTTP/1.1 409 Conflict
```

Body：

```json
{
  "code": 41003,
  "msg": "Issue 状态不允许从 DONE 变更为 TODO",
  "data": null
}
```

这样：

```text
HTTP Status
```

解决 HTTP 层语义。

```text
code
```

解决前端业务判断。

---

# 5. 分页响应

统一：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "records": [],
    "total": 100,
    "page": 1,
    "size": 20,
    "pages": 5
  }
}
```

Java：

```java
@Data
public class PageResult<T> {

    private List<T> records;

    private long total;

    private long page;

    private long size;

    private long pages;
}
```

---

# 6. 分页请求

统一：

```text
page
size
```

例如：

```http
GET /api/v1/projects/xxx/issues?page=1&size=20
```

默认：

```text
page = 1
size = 20
```

限制：

```text
1 <= page
1 <= size <= 100
```

---

# 7. User API

## 登录

```http
POST /api/v1/auth/login
```

Request：

```json
{
  "username": "admin",
  "password": "123456"
}
```

Response：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "accessToken": "xxxxx",
    "tokenType": "Bearer",
    "expiresIn": 7200,
    "user": {
      "id": "xxx",
      "username": "admin",
      "displayName": "管理员",
      "avatarUrl": null
    }
  }
}
```

---

## 当前用户

```http
GET /api/v1/auth/me
```

Response：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": "xxx",
    "username": "admin",
    "displayName": "管理员",
    "email": "admin@example.com",
    "avatarUrl": null
  }
}
```

---

# 8. Workspace API

## Workspace 列表

```http
GET /api/v1/workspaces
```

Response：

```json
{
  "code": 0,
  "msg": "success",
  "data": [
    {
      "id": "xxx",
      "name": "SevenM",
      "workspaceKey": "SEVENM",
      "avatarUrl": null
    }
  ]
}
```

---

## 创建 Workspace

```http
POST /api/v1/workspaces
```

Request：

```json
{
  "name": "SevenM",
  "workspaceKey": "SEVENM",
  "description": "项目管理工作空间"
}
```

Response：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": "xxx",
    "name": "SevenM",
    "workspaceKey": "SEVENM"
  }
}
```

---

## Workspace 详情

```http
GET /api/v1/workspaces/{workspaceId}
```

---

## 修改 Workspace

```http
PUT /api/v1/workspaces/{workspaceId}
```

Request：

```json
{
  "name": "SevenM研发中心",
  "description": "研发项目管理"
}
```

---

# 9. Project API

## 项目列表

```http
GET /api/v1/workspaces/{workspaceId}/projects
```

参数：

```text
page
size
keyword
status
```

例如：

```http
GET /api/v1/workspaces/xxx/projects?page=1&size=20&keyword=yushun
```

Response：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "records": [
      {
        "id": "xxx",
        "projectKey": "YSP",
        "name": "禹舜平台",
        "description": "水利模型平台",
        "status": "ACTIVE",
        "lead": {
          "id": "xxx",
          "displayName": "张三",
          "avatarUrl": null
        }
      }
    ],
    "total": 1,
    "page": 1,
    "size": 20,
    "pages": 1
  }
}
```

---

## 创建项目

```http
POST /api/v1/workspaces/{workspaceId}/projects
```

Request：

```json
{
  "projectKey": "YSP",
  "name": "禹舜平台",
  "description": "水利模型平台",
  "leadUserId": "xxx"
}
```

这里创建项目时，后端自动完成：

```text
Project
+
IssueType
+
IssueStatus
+
Workflow
+
WorkflowStatus
+
WorkflowTransition
+
Board
+
BoardColumn
```

也就是说：

> 用户创建项目后，不需要再手动配置一堆基础数据，就可以直接进入 Kanban。

---

# 10. Project Overview API

```http
GET /api/v1/projects/{projectId}
```

返回：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": "xxx",
    "projectKey": "YSP",
    "name": "禹舜平台",
    "description": "水利模型平台",
    "status": "ACTIVE",
    "lead": {
      "id": "xxx",
      "displayName": "张三"
    },
    "statistics": {
      "totalIssues": 128,
      "todoIssues": 30,
      "inProgressIssues": 42,
      "doneIssues": 56
    }
  }
}
```

这里的 `statistics` 就是 Dashboard 的基础。

---

# 11. Issue API

这是整个系统最重要的一组 API。

## Issue 列表

```http
GET /api/v1/projects/{projectId}/issues
```

参数：

```text
page
size
keyword
issueTypeId
statusId
priority
assigneeId
reporterId
sprintId
labelId
```

例如：

```http
GET /api/v1/projects/xxx/issues?page=1&size=20&statusId=xxx&priority=P1
```

Response：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "records": [
      {
        "id": "xxx",
        "key": "YSP-102",
        "title": "实现洪水模型参数配置",
        "issueType": {
          "id": "xxx",
          "code": "TASK",
          "name": "任务",
          "icon": "task"
        },
        "status": {
          "id": "xxx",
          "code": "IN_PROGRESS",
          "name": "开发中",
          "category": "IN_PROGRESS",
          "color": "#1677ff"
        },
        "priority": "P1",
        "assignee": {
          "id": "xxx",
          "displayName": "张三",
          "avatarUrl": null
        },
        "sprint": {
          "id": "xxx",
          "name": "Sprint 1"
        },
        "labels": [
          {
            "id": "xxx",
            "name": "后端",
            "color": "#1677ff"
          }
        ],
        "createdAt": "2026-08-17T08:00:00+08:00"
      }
    ],
    "total": 128,
    "page": 1,
    "size": 20,
    "pages": 7
  }
}
```

---

# 12. 创建 Issue

```http
POST /api/v1/projects/{projectId}/issues
```

Request：

```json
{
  "title": "实现洪水模型参数配置",
  "description": "完成模型参数配置页面和后端接口",
  "issueTypeId": "xxx",
  "priority": "P1",
  "assigneeId": "xxx",
  "sprintId": "xxx",
  "labelIds": [
    "xxx",
    "xxx"
  ]
}
```

Response：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": "xxx",
    "key": "YSP-102",
    "title": "实现洪水模型参数配置",
    "status": {
      "code": "TODO",
      "name": "待处理"
    },
    "priority": "P1"
  }
}
```

---

# 13. Issue 详情

```http
GET /api/v1/issues/{issueId}
```

返回详细对象：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": "xxx",
    "key": "YSP-102",
    "title": "实现洪水模型参数配置",
    "description": "完成模型参数配置页面",
    "issueType": {},
    "status": {},
    "priority": "P1",
    "reporter": {},
    "assignee": {},
    "sprint": {},
    "labels": [],
    "parent": null,
    "children": [],
    "relations": [],
    "createdAt": "2026-08-17T08:00:00+08:00",
    "updatedAt": "2026-08-17T08:30:00+08:00"
  }
}
```

---

# 14. 修改 Issue

```http
PUT /api/v1/issues/{issueId}
```

Request：

```json
{
  "title": "实现洪水模型参数配置 V2",
  "description": "更新后的描述",
  "priority": "P2",
  "assigneeId": "xxx",
  "sprintId": "xxx"
}
```

注意：

**不要通过这个 API 修改 Status。**

Status 有独立的状态流转接口。

---

# 15. Issue 状态变更

```http
PATCH /api/v1/issues/{issueId}/status
```

Request：

```json
{
  "statusId": "xxx"
}
```

后端：

```text
当前状态
    ↓
Workflow
    ↓
Transition
    ↓
目标状态
```

如果不允许：

```json
{
  "code": 41003,
  "msg": "Issue 不允许从「已完成」变更为「开发中」",
  "data": null
}
```

---

# 16. Issue 拖拽

状态和位置其实最好放在同一个接口：

```http
PATCH /api/v1/issues/{issueId}/position
```

Request：

```json
{
  "statusId": "xxx",
  "sortKey": "M"
}
```

Response：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": "xxx",
    "key": "YSP-102",
    "statusId": "xxx",
    "sortKey": "M"
  }
}
```

这个接口就是 Kanban 最核心的 API。

---

# 17. Board API

## 获取 Board

```http
GET /api/v1/projects/{projectId}/board
```

Response：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": "xxx",
    "name": "研发看板",
    "type": "KANBAN",
    "columns": [
      {
        "id": "column-1",
        "name": "待处理",
        "status": {
          "id": "status-1",
          "code": "TODO",
          "name": "待处理",
          "category": "TODO"
        },
        "sortOrder": 1,
        "wipLimit": null,
        "issues": []
      },
      {
        "id": "column-2",
        "name": "开发中",
        "status": {
          "id": "status-2",
          "code": "IN_PROGRESS",
          "name": "开发中",
          "category": "IN_PROGRESS"
        },
        "sortOrder": 2,
        "wipLimit": 10,
        "issues": []
      }
    ]
  }
}
```

---

# 18. Board Issue 查询

这里我建议实际开发时不要让：

```http
GET /board
```

一次返回项目几千个 Issue。

所以 Board API 最终建议：

```http
GET /api/v1/projects/{projectId}/board
```

只返回：

```text
Board
Column
Status
```

然后：

```http
GET /api/v1/projects/{projectId}/board/issues
```

返回 Issue。

参数：

```text
assigneeId
sprintId
priority
labelId
keyword
```

Response：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "columns": [
      {
        "statusId": "xxx",
        "issues": []
      }
    ]
  }
}
```

这样后面做 Board 筛选更加合理。

---

# 19. Board 移动 Issue

前端拖拽：

```text
POST/PATCH
```

推荐：

```http
PATCH /api/v1/issues/{issueId}/position
```

不要：

```http
PUT /api/v1/boards/{boardId}/issues/{issueId}
```

因为真正变化的是：

```text
Issue.status
Issue.sortKey
```

不是 Board 本身。

---

# 20. Status API

```http
GET /api/v1/projects/{projectId}/statuses
```

Response：

```json
{
  "code": 0,
  "msg": "success",
  "data": [
    {
      "id": "xxx",
      "code": "TODO",
      "name": "待处理",
      "category": "TODO",
      "color": "#999999",
      "sortOrder": 1
    },
    {
      "id": "xxx",
      "code": "IN_PROGRESS",
      "name": "开发中",
      "category": "IN_PROGRESS",
      "color": "#1677ff",
      "sortOrder": 2
    }
  ]
}
```

---

# 21. Workflow API

## 获取 Workflow

```http
GET /api/v1/projects/{projectId}/workflow
```

Response：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": "xxx",
    "name": "默认工作流",
    "statuses": [
      {
        "id": "xxx",
        "code": "TODO",
        "name": "待处理"
      },
      {
        "id": "xxx",
        "code": "IN_PROGRESS",
        "name": "开发中"
      }
    ],
    "transitions": [
      {
        "fromStatusId": "xxx",
        "toStatusId": "xxx",
        "name": "开始开发"
      }
    ]
  }
}
```

---

# 22. Sprint API

## Sprint 列表

```http
GET /api/v1/projects/{projectId}/sprints
```

## 创建

```http
POST /api/v1/projects/{projectId}/sprints
```

Request：

```json
{
  "name": "Sprint 1",
  "goal": "完成项目基础框架",
  "startDate": "2026-08-17",
  "endDate": "2026-08-28"
}
```

## 开始 Sprint

```http
POST /api/v1/sprints/{sprintId}/start
```

## 完成 Sprint

```http
POST /api/v1/sprints/{sprintId}/complete
```

---

# 23. Comment API

```http
GET    /api/v1/issues/{issueId}/comments

POST   /api/v1/issues/{issueId}/comments

PUT    /api/v1/comments/{commentId}

DELETE /api/v1/comments/{commentId}
```

创建：

```json
{
  "content": "这个问题需要调整一下参数校验。"
}
```

---

# 24. Label API

```http
GET    /api/v1/projects/{projectId}/labels

POST   /api/v1/projects/{projectId}/labels

PUT    /api/v1/labels/{labelId}

DELETE /api/v1/labels/{labelId}
```

Issue 添加 Label：

```http
POST /api/v1/issues/{issueId}/labels/{labelId}
```

删除：

```http
DELETE /api/v1/issues/{issueId}/labels/{labelId}
```

---

# 25. Issue Relation API

```http
POST /api/v1/issues/{issueId}/relations
```

Request：

```json
{
  "targetIssueId": "xxx",
  "relationType": "BLOCKS"
}
```

Response：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": "xxx",
    "relationType": "BLOCKS",
    "targetIssue": {
      "id": "xxx",
      "key": "YSP-103",
      "title": "实现模型计算接口"
    }
  }
}
```

---

# 26. Attachment API

上传文件不能走 JSON。

```http
POST /api/v1/issues/{issueId}/attachments
Content-Type: multipart/form-data
```

返回：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "id": "xxx",
    "fileName": "design.pdf",
    "contentType": "application/pdf",
    "fileSize": 1024000
  }
}
```

下载：

```http
GET /api/v1/attachments/{attachmentId}/download
```

后端返回文件流或者临时对象存储 URL。

---

# 27. Activity API

```http
GET /api/v1/issues/{issueId}/activities
```

Response：

```json
{
  "code": 0,
  "msg": "success",
  "data": [
    {
      "id": "xxx",
      "action": "STATUS_CHANGED",
      "actor": {
        "id": "xxx",
        "displayName": "张三"
      },
      "oldValue": {
        "status": "TODO"
      },
      "newValue": {
        "status": "IN_PROGRESS"
      },
      "createdAt": "2026-08-17T08:30:00+08:00"
    }
  ]
}
```

---

# 28. Dashboard API

这个是你最开始真正需要的“看板数据”。

```http
GET /api/v1/projects/{projectId}/dashboard
```

Response：

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "summary": {
      "total": 128,
      "todo": 30,
      "inProgress": 42,
      "done": 56,
      "bugs": 12
    },

    "statusDistribution": [
      {
        "status": "TODO",
        "name": "待处理",
        "count": 30
      },
      {
        "status": "IN_PROGRESS",
        "name": "开发中",
        "count": 42
      },
      {
        "status": "DONE",
        "name": "完成",
        "count": 56
      }
    ],

    "priorityDistribution": [
      {
        "priority": "P0",
        "count": 2
      },
      {
        "priority": "P1",
        "count": 18
      },
      {
        "priority": "P2",
        "count": 82
      },
      {
        "priority": "P3",
        "count": 26
      }
    ],

    "recentIssues": [],

    "recentActivities": []
  }
}
```

前端拿到这个结构以后，可以直接画：

```text
Issue 总量
├── 128

状态分布
├── 待处理 30
├── 开发中 42
└── 完成   56

Priority
├── P0 2
├── P1 18
├── P2 82
└── P3 26
```

---

# 29. API 命名最终规范

统一使用：

```text
GET       查询
POST      创建/执行动作
PUT       完整更新
PATCH     局部更新
DELETE    删除
```

例如：

```text
POST  /projects
PUT   /projects/{id}
DELETE /projects/{id}

POST  /issues
PUT   /issues/{id}
PATCH /issues/{id}/status
PATCH /issues/{id}/position

POST /sprints/{id}/start
POST /sprints/{id}/complete
```

不要使用：

```text
GET /getProject
POST /createProject
POST /updateProject
POST /deleteProject
```

---

# 30. 前端统一请求处理

Vue 端的 Axios 封装应该统一处理：

```ts
export interface ApiResponse<T> {
  code: number
  msg: string
  data: T
}
```

例如：

```ts
export interface Issue {
  id: string
  key: string
  title: string
  status: IssueStatus
  priority: string
  assignee?: UserSimple
}
```

Axios：

```ts
const request = axios.create({
  baseURL: '/api/v1',
  timeout: 15000
})
```

响应拦截：

```ts
request.interceptors.response.use(
  response => {

    const result = response.data as ApiResponse<unknown>

    if (result.code !== 0) {
      return Promise.reject(
        new Error(result.msg)
      )
    }

    return result
  }
)
```

这样业务代码：

```ts
const result = await getIssue(id)

console.log(result.data)
```

而不是每个接口都自己判断：

```ts
if (res.code === 0) {}
```

---

# 31. API 文档建议

建议直接使用：

```text
Springdoc OpenAPI
Swagger UI
```

自动生成：

```text
/api-docs
/swagger-ui
```

同时把人工设计文档保存在：

```text
docs/
└── 04-api/
    ├── api-design.md
    ├── auth.md
    ├── workspace.md
    ├── project.md
    ├── issue.md
    ├── board.md
    ├── workflow.md
    ├── sprint.md
    └── dashboard.md
```

**Swagger 是接口实现文档，`api-design.md` 是接口规范。两者不要互相替代。**

---

# 32. 最终 API 总览

第一版核心 API 可以控制在下面这个规模：

```text
认证
├── POST   /auth/login
└── GET    /auth/me

Workspace
├── GET    /workspaces
├── POST   /workspaces
├── GET    /workspaces/{id}
└── PUT    /workspaces/{id}

Project
├── GET    /workspaces/{id}/projects
├── POST   /workspaces/{id}/projects
├── GET    /projects/{id}
└── PUT    /projects/{id}

Issue
├── GET    /projects/{id}/issues
├── POST   /projects/{id}/issues
├── GET    /issues/{id}
├── PUT    /issues/{id}
├── DELETE /issues/{id}
├── PATCH  /issues/{id}/status
├── PATCH  /issues/{id}/position
├── GET    /issues/{id}/activities
├── GET    /issues/{id}/comments
├── POST   /issues/{id}/comments
└── GET    /issues/{id}/relations

Board
├── GET    /projects/{id}/board
└── GET    /projects/{id}/board/issues

Workflow
└── GET    /projects/{id}/workflow

Status
└── GET    /projects/{id}/statuses

Sprint
├── GET    /projects/{id}/sprints
├── POST   /projects/{id}/sprints
├── PUT    /sprints/{id}
├── POST   /sprints/{id}/start
└── POST   /sprints/{id}/complete

Label
├── GET    /projects/{id}/labels
├── POST   /projects/{id}/labels
├── PUT    /labels/{id}
└── DELETE /labels/{id}

Attachment
├── POST   /issues/{id}/attachments
└── GET    /attachments/{id}/download

Dashboard
└── GET    /projects/{id}/dashboard
```

这个 API 设计有一个核心原则：**资源 API 和业务动作 API 分开**。

比如：

```text
修改 Issue 基础信息
PUT /issues/{id}

改变 Issue 状态
PATCH /issues/{id}/status

拖动 Issue
PATCH /issues/{id}/position

开始 Sprint
POST /sprints/{id}/start

完成 Sprint
POST /sprints/{id}/complete
```

这样后面 Vue、移动端甚至第三方客户端都比较容易使用。

下一步就可以直接进入 **Java 后端代码规范 + `ApiResponse<T>`、异常码、分页、Entity、Mapper、Service、Controller 的完整模板**。这一部分定下来之后，后面每个模块基本都是按模板开发，不需要再反复讨论架构。
