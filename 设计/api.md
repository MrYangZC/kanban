> **【最新状态 · 2026-08-24 已落地】**
>
> 本文档为设计阶段 API 规范讨论稿，接口语义已按两次架构级重构调整，以当前 `server/src/main/java/com/seven7m/kanban/controller` 与 `web/src/api` 为准，本文档仅作设计演进记录。
>
> **接口语义关键变更：**
> - Workspace 相关接口随 workspace 模块移除而废弃。
> - 状态/类型/标签/工作流接口由「项目维度」改为「全局字典 + 项目选用」：全局字典由全局接口管理，项目通过 `workflow_id` 绑定模板、通过 `project_label` 选用标签。
> - 标签选择器改为「全局标签库 + 项目选用关联」。
> - 看板列不再返回冗余 `name`，由 `statusId → issue_status.name` 获取。
> - **工作流按类别拆成三套**（`workflow.applies_to` = TASK / REQUIREMENT / BUG，V31）：`/statuses`、`/transitions`、`/board`、`/board/grouped` 均支持 `category` 参数，缺省 `BUG` 仅为兼容旧调用。
> - 下文示例中的 `TODO` / `DONE` / `IN_PROGRESS` 等状态 code 属设计期示意，**实际取值以 §20 的三类节点表为准**（`TASK_*` / `REQ_*` / 缺陷 `TO_CONFIRM`…`CLOSED`）；`status.category` 仍是 `TODO / IN_PROGRESS / DONE` 三值。
> - 角色授权基于 `role_id`，菜单仅导航、按钮权限由 `permission` 承载。
>
> **权威参考：** 后端 `server/src/main/java/com/seven7m/kanban/controller`；前端 `web/src/api`。
>
> ---

API 层统一遵循一套规范，前后端所有接口一致。响应体固定 `code / msg / data` 三个字段，不使用 `message`：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {}
}
```

其中：

```text
code = 200    成功（复用 HTTP 语义码，400/401/403/404/405/500）
code != 200   业务/系统错误，HTTP 层通常仍返回 200，需按 body.code 判定
msg            给前端展示或开发定位的信息
data           业务数据
```

以下为 API 设计规范 V1.0 正文。

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
  "code": 200,
  "msg": "操作成功",
  "data": {}
}
```

成功：

```json
{
  "code": 200,
  "msg": "操作成功",
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
  "code": 200,
  "msg": "操作成功",
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
  "code": 200,
  "msg": "操作成功",
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
  "code": 200,
  "msg": "操作成功",
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
  "code": 200,
  "msg": "操作成功",
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
  "code": 200,
  "msg": "操作成功",
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
  "code": 200,
  "msg": "操作成功",
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
  "code": 200,
  "msg": "操作成功",
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
  "code": 200,
  "msg": "操作成功",
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
  "code": 200,
  "msg": "操作成功",
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
  "code": 200,
  "msg": "操作成功",
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

**本接口可以带 `statusId` 改状态**（当前实现的改状态主路径之一）。服务端按该工作项所属类别（TASK / REQUIREMENT / BUG）的工作流校验流转，非法返回 `400 不允许从「A」流转到「B」`；跨类别状态返回 `400 状态不属于该工作项类型的工作流`。局部更新语义：未传字段不覆盖。

---

# 15. Issue 状态变更（独立接口，未落地）

> 现状：未实现 `PATCH /issues/{id}/status`。改状态走 `PUT /issues/{id}`（带 `statusId`）或拖拽 `PATCH /issues/{id}/position`，两者共用同一套流转校验。

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

Request（实际实现为 `statusId` + `beforeId`/`afterId`，`sortKey` 由服务端用 `SortKeyUtils.between` 算出，前端不传）：

```json
{
  "statusId": "xxx",
  "beforeId": "xxx",
  "afterId": "xxx"
}
```

Response：

```json
{
  "code": 200,
  "msg": "操作成功",
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
GET /api/v1/projects/{projectId}/board?category=BUG
```

`category` 取 `TASK / REQUIREMENT / BUG`，缺省 `BUG`（兼容旧的单工作流调用）；分组看板 `GET /projects/{id}/board/grouped?groupBy=STATUS&category=` 同理。列不返回冗余 `name`，列名由 `statusId → issue_status.name` 得到。

Response（`category=BUG` 时为缺陷 6 列；`TASK` 4 列、`REQUIREMENT` 6 列）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": "xxx",
    "name": "缺陷看板",
    "type": "KANBAN",
    "columns": [
      {
        "id": "column-1",
        "status": {
          "id": "status-1",
          "code": "TO_CONFIRM",
          "name": "待确认",
          "category": "TODO"
        },
        "sortOrder": 1,
        "wipLimit": null,
        "issues": []
      },
      {
        "id": "column-2",
        "status": {
          "id": "status-2",
          "code": "IN_PROGRESS",
          "name": "处理中",
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
  "code": 200,
  "msg": "操作成功",
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
GET /api/v1/projects/{projectId}/statuses?category=BUG
```

`category` 取 `TASK / REQUIREMENT / BUG`，缺省 `BUG`（兼容旧的单工作流调用）。返回该类别工作流的节点，是看板列、详情状态下拉、新建默认状态的唯一来源。

Response（`category=TASK`）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": [
    {
      "id": "xxx",
      "code": "TASK_TODO",
      "name": "待办",
      "category": "TODO",
      "color": "#999999",
      "sortOrder": 10
    },
    {
      "id": "xxx",
      "code": "TASK_IN_PROGRESS",
      "name": "进行中",
      "category": "IN_PROGRESS",
      "color": "#1677ff",
      "sortOrder": 11
    }
  ]
}
```

三个类别的完整节点（`issue_status` 共 16 条，`workflow_status` 16 条）：

| category | 节点数 | code |
|---|---|---|
| TASK | 4 | `TASK_TODO` 待办 / `TASK_IN_PROGRESS` 进行中 / `TASK_DONE` 已完成 / `TASK_CANCELLED` 已取消 |
| REQUIREMENT | 6 | `REQ_TODO` 待处理 / `REQ_DESIGNING` 设计中 / `REQ_DEVELOPING` 开发中 / `REQ_TESTING` 测试中 / `REQ_DONE` 已完成 / `REQ_CANCELLED` 已取消 |
| BUG | 6 | `TO_CONFIRM` 待确认 / `REOPENED` 再次打开 / `IN_PROGRESS` 处理中 / `FIXED` 已修复 / `WONT_FIX` 暂不修复 / `CLOSED` 已关闭 |

---

# 20.1 Transition API（流转规则）

```http
GET /api/v1/projects/{projectId}/transitions?category=BUG
```

`category` 同上，缺省 `BUG`。返回该类别的流转白名单，前端用于看板拖拽的实时校验（非法列立即回弹）与详情状态下拉过滤。

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": [
    {
      "fromStatusId": "xxx",
      "toStatusId": "xxx",
      "name": "提交修复"
    }
  ]
}
```

边数：任务 6 / 需求 21 / 缺陷 10（`workflow_transition` 共 37 条）。

需求流（V34/V35 起）的连通规则——返工不用绕回「待处理」：

| from \ to | 待处理 | 设计中 | 开发中 | 测试中 | 已完成 | 已取消 |
| --- | --- | --- | --- | --- | --- | --- |
| 待处理 | — | ✓ | ✓ | ✓ | ✗ | ✓ |
| 设计中 | ✓ | — | ✓ | ✓ | ✗ | ✓ |
| 开发中 | ✓ | ✓ | — | ✓ | ✗ | ✓ |
| 测试中 | ✓ | ✓ | ✓ | — | ✓ | ✓ |
| 已完成 | ✗ | ✓ | ✓ | ✓ | — | ✗ |
| 已取消 | ✓ | ✗ | ✗ | ✗ | ✗ | — |

`—` 为同状态（服务端幂等放行），`✗` 返回 `400 不允许从「A」流转到「B」`。

- 四个活跃阶段（待处理 / 设计中 / 开发中 / 测试中）两两互转；
- 进入「已完成」只有「测试中 → 已完成（测试通过）」一条入边（待处理 / 设计中 / 开发中 直接改「已完成」返回 400）；
- 「已完成」可打回设计中 / 开发中 / 测试中（V35：重新设计 / 重新开发 / 重新测试），但**不能回「待处理」、不能改成「已取消」**；
- 任一活跃阶段可 → 已取消；「已取消」只能回到「待处理」（重新打开）。

---

# 21. Workflow API（未落地）

> 现状：未实现 `GET /projects/{id}/workflow`。工作流定义按类别存在 `workflow`（`applies_to` = TASK / REQUIREMENT / BUG，各一条 `is_default` 模板），前端只用 `statuses?category=` 取节点、`transitions?category=` 取边，不需要整条 workflow 对象。

## 获取 Workflow

```http
GET /api/v1/projects/{projectId}/workflow
```

Response（若实现，结构应为）：

```json
{
  "code": 200,
  "msg": "操作成功",
  "data": {
    "id": "xxx",
    "name": "缺陷工作流",
    "appliesTo": "BUG",
    "statuses": [
      {
        "id": "xxx",
        "code": "TO_CONFIRM",
        "name": "待确认"
      },
      {
        "id": "xxx",
        "code": "IN_PROGRESS",
        "name": "处理中"
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
  "code": 200,
  "msg": "操作成功",
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
  "code": 200,
  "msg": "操作成功",
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
  "code": 200,
  "msg": "操作成功",
  "data": [
    {
      "id": "xxx",
      "type": "STATUS_CHANGED",
      "actorId": "xxx",
      "actorName": "张三",
      "parentId": null,
      "content": null,
      "oldValue": "待处理",
      "newValue": "开发中",
      "createdAt": "2026-08-17T08:30:00+08:00"
    }
  ]
}
```

说明：

- `type`：`COMMENT`（评论，正文在 `content`，走 `commentTree` 楼层渲染）/ `STATUS_CHANGED`（状态变更）/ `CREATED`（创建）。
- `oldValue` / `newValue`：库里 `issue_activity.old_value / new_value` 存的是 `{"status": "REQ_TODO"}` 这类机器值，**接口层已翻成中文状态名**（`待处理`、`开发中`…），前端直接拼「将 状态 从 待处理 置为 开发中」即可，不要再自己维护 code 字典。
- 历史遗留 code（V33 改名前后的 `REQ_TO_REVIEW` / `REQ_SCHEDULED` / 已移除的 `REQ_REJECTED`）由后端兜底翻成「待处理 / 设计中 / 已拒绝」，V36 已就地改写存量数据。
- 项目级动态 `GET /projects/{projectId}/activities` 的 `oldValue` / `newValue` 同样返回中文名。
- 排序：`GET /issues/{id}/activities` 仍按 `createdAt` **升序**返回（楼层组装依赖父评论先于子回复），**倒序展示由前端负责**——`utils/commentTree.ts` 把顶层（楼层根 + 状态动态）按时间倒序，楼层内子回复保持升序。
- 头像字段：`actorAvatarUrl`（工作项动态/评论、项目动态）、`assigneeAvatarUrl` / `reporterAvatarUrl` / `verifierAvatarUrl`（工作项）、`avatarUrl`（`/users/login` 与 `/users/me`）统一返回 `/api/uploads/xxx` 形式；为空表示未上传，前端 `components/common/UserAvatar.vue` 回退首字彩色圆，图片加载失败同样回退。
- 度量接口 `GET /projects/{projectId}/dashboard` 的 `assigneeDist[]` 每项为 `{ name, count, avatarUrl }`：`name` 取 `display_name`（兜底 `username`，再兜底「未分配」，与全站显示名口径一致），`avatarUrl` 为空（含「未分配」行）时前端回退首字/图标。前端另会过滤非图片形式的脏值（历史上曾把学号写进 `avatar_url`），不发起无意义请求。

---

# 28. Dashboard API

这个是你最开始真正需要的“看板数据”。

```http
GET /api/v1/projects/{projectId}/dashboard
```

Response：

```json
{
  "code": 200,
  "msg": "操作成功",
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
├── GET    /projects/{id}/transitions              ?category=TASK|REQUIREMENT|BUG
└── GET    /projects/{id}/workflow                 （未实现）

Status
└── GET    /projects/{id}/statuses                 ?category=TASK|REQUIREMENT|BUG

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
