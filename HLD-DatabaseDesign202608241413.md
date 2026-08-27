# 数据库设计评审报告

> **文档编号**：HLD-DatabaseDesign202608241413  
> **评审日期**：2026-08-24  
> **数据库**：PostgreSQL 16.9  
> **ORM**：MyBatis-Plus（ID 策略 assign_uuid）  
> **迁移工具**：Flyway（基线 V1.0.0）  
> **评审范围**：`V1.0.0__baseline.sql` 全部 31 张表 + 1 序列 + 约束/索引/外键  
> **评审性质**：仅设计评审，不改动任何代码/数据库  
> **删除策略决策**：✅ 采用**物理删除**，废弃逻辑删除（deleted 字段）——本报告已据此更新

---

## 一、数据库总览

### 1.1 技术栈

| 项 | 值 |
|---|---|
| 数据库 | PostgreSQL 16.9（192.168.2.17:5433/kanban） |
| 表总数 | 31 |
| 序列 | 1（issue_number_seq） |
| 主键策略 | uuid + gen_random_uuid() |
| 删除策略 | **物理删除**（当前 deleted 字段将废弃） |
| 外键约束 | 已启用（物理外键） |
| 索引类型 | 全部 btree |

### 1.2 表分类清单（31 张）

| 分类 | 表名 | 计数 |
|---|---|---|
| **项目核心** | project, board, board_column, sprint, milestone, risk, announcement | 7 |
| **工作项** | issue, issue_status, issue_type, issue_activity, issue_cc, issue_comment, issue_label, issue_participant, issue_relation | 9 |
| **标签/附件** | label, project_label, attachment | 3 |
| **用户/成员** | sys_user, project_member, project_favorite | 3 |
| **RBAC 权限** | role, user_role, menu, permission, role_menu, role_permission | 6 |
| **工作流** | workflow, workflow_status, workflow_transition | 3 |

---

## 二、物理删除策略变更影响（首要事项）

> 用户已决策：**采用物理删除，废弃逻辑删除**。以下为该决策带来的完整变更清单。

### 2.0.1 需移除 deleted 字段的表（10 张）

| 表 | 当前定义 | 变更 |
|---|---|---|
| announcement | deleted int4 NOT NULL DEFAULT 0 | 移除字段 |
| issue | deleted int4 NOT NULL DEFAULT 0 | 移除字段 |
| issue_comment | deleted int4 NOT NULL DEFAULT 0 | 移除字段 |
| menu | deleted int4 NOT NULL DEFAULT 0 | 移除字段 |
| milestone | deleted int4 NOT NULL DEFAULT 0 | 移除字段 |
| project | deleted int4 NOT NULL DEFAULT 0 | 移除字段 |
| risk | deleted int4 NOT NULL DEFAULT 0 | 移除字段 |
| role | deleted int4 NOT NULL DEFAULT 0 | 移除字段 |
| sprint | deleted int4 NOT NULL DEFAULT 0 | 移除字段 |
| sys_user | deleted int4 NOT NULL DEFAULT 0 | 移除字段 |

### 2.0.2 需改为普通唯一约束的部分索引（5 个）

| 表 | 当前部分索引 | 改为普通唯一约束 |
|---|---|---|
| issue | uk_issue_number_undeleted (project_id, issue_number) WHERE deleted=0 | uk_issue_number (project_id, issue_number) |
| project | uk_project_key_undeleted (project_key) WHERE deleted=0 | uk_project_key (project_key) |
| sys_user | uk_sys_user_username_undeleted (username) WHERE deleted=0 | uk_sys_user_username (username) |
| role | uk_role_code_undeleted (code) WHERE deleted=0 | uk_role_code (code) |
| menu | uk_menu_code_undeleted (code) WHERE deleted=0 | uk_menu_code (code) |

### 2.0.3 需移除 WHERE deleted=0 条件的索引（7 个）

| 索引 | 表 |
|---|---|
| idx_announcement_project | announcement |
| idx_milestone_project | milestone |
| idx_risk_level | risk |
| idx_risk_project | risk |
| idx_risk_status | risk |
| idx_sprint_project | sprint |
| idx_sprint_status | sprint |

### 2.0.4 MyBatis-Plus 配置变更

`application.yml` 中移除逻辑删除全局配置：

```yaml
# 移除以下配置
global-config:
  db-config:
    logic-delete-field: deleted      # 移除
    logic-delete-value: 1            # 移除
    logic-not-delete-value: 0        # 移除
```

### 2.0.5 应用层变更

- 所有 `removeById` / `remove` 调用：MyBatis-Plus 将执行 `DELETE` 而非 `UPDATE SET deleted=1`，行为正确，无需改代码
- 所有查询中的 `deleted = 0` 条件：移除（MyBatis-Plus 不再自动注入）
- 实体类 `@TableLogic` 注解：移除
- **注意**：物理删除不可恢复，建议关键业务表（issue、project）增加删除前确认 + 操作日志记录

### 2.0.6 外键 ON DELETE 策略重新设计

物理删除下，外键级联将**真正生效**，需为每个父表明确删除策略：

| 父表 | 删除策略 | 说明 |
|---|---|---|
| **project** | RESTRICT | 有 issue/board/sprint 等子数据时阻止删除，需先清空子数据 |
| **issue** | CASCADE 子表 | 删除 issue 时级联删除 attachment/activity/cc/comment/label/participant/relation |
| **sys_user** | RESTRICT | 有业务关联时阻止删除，建议用 `enabled=false` 停用而非物理删除 |
| **role** | CASCADE | 级联删除 user_role、role_menu、role_permission |
| **label** | CASCADE | 级联删除 issue_label、project_label |
| **workflow** | CASCADE | 级联删除 workflow_status、workflow_transition |
| **issue_status** | RESTRICT | 被 issue、workflow_status 引用，不允许直接删除 |
| **issue_type** | RESTRICT | 被 issue 引用，不允许直接删除 |
| **menu** | RESTRICT | 有子菜单或 role_menu 关联时阻止删除 |
| **permission** | CASCADE | 级联删除 role_permission |
| **sprint** | RESTRICT | 有 issue 引用时阻止删除（issue.sprint_id 应先置 NULL） |
| **board** | CASCADE | 级联删除 board_column |

---

## 三、问题清单

> 严重度：🔴 高 / 🟡 中 / 🟢 低 / ✅ 已消除（物理删除决策）

### 3.1 🔴 引用完整性缺陷（缺少外键约束）—— ⏸️ 不修复（用户决策）

> **用户决策**：不补全外键约束，引用完整性由应用层保障。以下记录留存备查。

系统部分表建立了外键，部分表未建立，存在引用完整性漏洞。以下 **应当有但缺失** 的外键：

| 子表 | 外键字段 | 应引用父表 | 现状 | 风险 |
|---|---|---|---|---|
| announcement | project_id | project(id) | **缺失** | 公告可指向不存在的项目 |
| announcement | created_by | sys_user(id) | **缺失** | 无法保证创建人存在 |
| announcement | updated_by | sys_user(id) | **缺失** | 同上 |
| attachment | uploaded_by | sys_user(id) | **缺失** | 附件上传人可能无效 |
| board | created_by | sys_user(id) | **缺失** | 看板创建人可能无效 |
| issue | created_by | sys_user(id) | **缺失** | 工作项创建人可能无效 |
| issue | updated_by | sys_user(id) | **缺失** | 工作项更新人可能无效 |
| milestone | project_id | project(id) | **缺失** | 里程碑可指向不存在的项目 |
| milestone | created_by | sys_user(id) | **缺失** | — |
| milestone | updated_by | sys_user(id) | **缺失** | — |
| project | created_by | sys_user(id) | **缺失** | — |
| project | updated_by | sys_user(id) | **缺失** | — |
| project_favorite | user_id | sys_user(id) | **缺失** | 收藏可指向不存在的用户 |
| risk | owner_id | sys_user(id) | **缺失** | 风险负责人可能无效 |
| risk | created_by | sys_user(id) | **缺失** | — |
| risk | updated_by | sys_user(id) | **缺失** | — |
| role | created_by | sys_user(id) | **缺失** | — |
| role | updated_by | sys_user(id) | **缺失** | — |
| sprint | created_by | sys_user(id) | **缺失** | — |
| sprint | updated_by | sys_user(id) | **缺失** | — |
| sys_user | created_by | sys_user(id) | **缺失** | 自引用缺失 |
| sys_user | updated_by | sys_user(id) | **缺失** | 自引用缺失 |
| menu | parent_id | menu(id) | **缺失** | 菜单父子关系无约束 |

**影响**：共 **23 处**外键缺失。任意写入无效 UUID 不会被数据库拦截，只能依赖应用层校验，存在脏数据风险。

> **用户决策：不修复**。引用完整性由应用层保障，物理删除的级联清理也由应用层事务保证。

---

### 3.2 🔴 关键索引缺失（查询性能隐患）

以下表数据量会随业务增长，但缺少关键查询路径的索引：

| 表 | 缺失索引字段 | 典型查询场景 | 风险 |
|---|---|---|---|
| **issue_activity** | issue_id | 查看工作项的活动日志 | 全表扫描，活动日志量大 |
| **issue_comment** | issue_id | 查看工作项的评论列表 | 全表扫描，评论量大 |
| **attachment** | issue_id | 查看工作项的附件 | 全表扫描 |
| **issue_relation** | target_issue_id | 反向查询关联关系（谁关联了我） | 全表扫描 |
| **project_member** | user_id | 查询用户参与了哪些项目 | 唯一约束 (project_id,user_id) 中 user_id 在第二位，无法用于此查询 |
| **issue_label** | label_id | 查询标签被哪些工作项使用 | 复合主键 (issue_id,label_id) 中 label_id 在第二位 |
| **project_label** | label_id | 查询标签被哪些项目使用 | 唯一约束 (project_id,label_id) 中 label_id 在第二位 |
| **role_permission** | permission_code | 查询某权限被哪些角色拥有 | 唯一约束 (role_id,permission_code) 中 permission_code 在第二位 |
| **issue** | issue_type_id | 按类型筛选工作项 | 全表扫描 |

**影响**：随数据增长，上述查询将出现明显的性能退化。

---

### 3.3 ✅ 逻辑删除与唯一约束冲突（已消除）

> **物理删除决策后，此问题不再存在。** 所有部分唯一索引 `WHERE deleted=0` 改为普通唯一约束即可，详见 §2.0.2。

---

### 3.4 ✅ 逻辑删除与物理外键的架构冲突（已消除）

> **物理删除决策后，此问题不再存在。** 物理删除直接移除记录，外键级联自然生效，不存在"逻辑删除但物理记录仍在"的孤儿数据问题。外键 ON DELETE 策略详见 §2.0.6。

---

### 3.5 🟡 布尔字段类型不一致

物理删除后 `deleted` 字段移除，但同库中其他布尔语义字段类型仍不统一：

| 表 | 字段 | 类型 |
|---|---|---|
| sys_user | enabled | **bool** |
| workflow | is_default | **bool** |
| issue | archived | int4 (0/1) |
| announcement | pinned | int4 (0/1) |

**建议**：PostgreSQL 原生支持 bool，建议 `archived`、`pinned` 也改为 bool，统一风格。

---

### 3.6 🟡 缺少 CHECK 约束

全库**无任何 CHECK 约束**，以下字段缺少值域校验：

| 表 | 字段 | 建议 CHECK |
|---|---|---|
| milestone | progress | BETWEEN 0 AND 100 |
| board_column | wip_limit | > 0（当非 NULL） |
| attachment | file_size | >= 0 |
| issue | estimate | >= 0 |
| sprint | start_date / end_date | start_date <= end_date |
| issue | priority | IN ('P0','P1','P2','P3','P4') 或应用层枚举 |
| issue | severity | IN ('S1','S2','S3','S4') 或应用层枚举 |

**影响**：依赖应用层校验，数据库可写入非法值。

---

### 3.7 🟡 日期类型不一致

| 表 | 字段 | 类型 |
|---|---|---|
| issue | due_date | **timestamp(6)** |
| issue | plan_start | **timestamp(6)** |
| risk | due_date | **date** |
| milestone | due_date | **date** |
| sprint | start_date / end_date | **date** |

**影响**：issue 的日期带时间，其他表只带日期，跨表比较需类型转换，易出错。

---

### 3.8 🟡 issue 表字段冗余/语义重叠

| 字段 | 类型 | 说明 |
|---|---|---|
| sprint_id | uuid | 迭代外键 |
| iteration | varchar(100) | 迭代字符串标识，**与 sprint_id 语义重叠** |

从数据看 `iteration` 全为 NULL，`sprint_id` 为有效外键。`iteration` 疑似遗留字段，建议确认是否废弃。

---

### 3.9 🟡 project_member 与 user_role 数据同步风险

- `project_member(project_id, user_id)`：项目成员
- `user_role(user_id, role_id, project_id)`：用户在项目中的角色

两表**无同步机制**：project_member 有用户但 user_role 无对应记录（或反之）将导致数据不一致。

> **物理删除下**：删除用户时需同时清理 project_member 和 user_role，建议两者对 sys_user 均用 CASCADE，保证级联一致。

---

### 3.10 🟡 全局共享表缺少项目隔离

以下表**无 project_id**，为全局共享：

| 表 | 影响 |
|---|---|
| issue_status | 所有项目共享相同状态集，无法项目级自定义状态 |
| issue_type | 所有项目共享相同类型集 |
| label | 全局标签，uk_label_name 全局唯一，不同项目无法使用同名标签 |
| workflow | 全局工作流，project.workflow_id 引用，但无法项目级自定义工作流 |

**影响**：限制了项目级定制能力。若业务需要项目级自定义，需重构为带 project_id 的隔离模型。

---

### 3.11 🟡 外键 ON DELETE 策略需全面重设

物理删除下，当前 ON DELETE 策略存在不一致和风险：

| 当前策略 | 问题 | 建议 |
|---|---|---|
| issue_cc→sys_user: CASCADE | 删除用户静默删除抄送 | 改 SET NULL（保留 issue 上的抄送痕迹）或 RESTRICT |
| issue_comment→sys_user: NO ACTION | 删除用户失败 | 改 SET NULL（评论保留，作者置空）或 RESTRICT |
| issue_participant→sys_user: CASCADE | 删除用户静默删除参与记录 | 改 SET NULL 或 RESTRICT |
| project_member→sys_user: NO ACTION | 删除用户失败 | 改 CASCADE（成员关系随用户消失） |
| user_role→sys_user: NO ACTION | 删除用户失败 | 改 CASCADE |
| issue→project: NO ACTION | 删除项目失败 | 保持 RESTRICT（有 issue 时禁止删项目） |
| issue.parent_id→issue: NO ACTION | 删除父 issue 失败 | 改 CASCADE 或 SET NULL |
| sprint→project: NO ACTION | 删除项目失败 | 改 CASCADE（项目删除时迭代随之消失） |

**核心原则**：
- 业务主体数据（issue、project）删除用 **RESTRICT**，要求先清理子数据
- 关联/从属数据（attachment、comment、activity）删除用 **CASCADE**，随父记录消失
- 人员引用（created_by、assignee_id）用 **SET NULL**，保留业务记录，置空人员

---

### 3.12 🟡 issue_label 表设计不一致

`issue_label` 仅 `(issue_id, label_id)` 复合主键，无 id、无 created_at。而同类关联表 issue_cc、issue_participant、issue_relation 均有 id + created_at。

**影响**：关联表设计风格不统一，issue_label 无法记录"谁在何时添加了标签"。

---

### 3.13 🟢 缺少字段/表注释

全库无 `COMMENT ON TABLE` / `COMMENT ON COLUMN`。对后续维护和 DBA 协作不友好。

---

### 3.14 🟢 issue 表低基数索引

`idx_issue_archived` ON (archived)：archived 仅 0/1 两值，B-tree 索引选择性极差。

**建议**：改为部分索引 `WHERE archived = 1`（仅索引归档记录），或直接删除（若归档查询少）。

---

### 3.15 ✅ issue 索引未带 deleted 过滤（已消除）

> **物理删除后无 deleted 字段，此问题不再存在。** 所有索引自然只包含有效记录。

---

### 3.16 🟢 permission 表主键风格不一致

permission 表主键为 `code`（varchar），其他表主键为 `id`（uuid）。role_permission.permission_code 外键引用 permission.code。

**影响**：主键策略不统一，但功能正确，属风格问题。

---

### 3.17 🟢 sys_user.email 无唯一约束

email 字段无唯一约束，允许多个用户同邮箱。从数据看大部分为 NULL。若未来需要邮箱登录或密码找回，需补充唯一约束。

---

### 3.18 🟢 BCrypt 前缀不一致

sys_user.password_hash 中混用 `$2a$10$` 与 `$2b$10$` 前缀。两者均为 BCrypt，但版本不同。建议统一为 `$2a$`（Java BCrypt 原生支持）或 `$2b$`（修复了长密码漏洞的版本），并明确选型。

---

### 3.19 🟢 配置安全（附带发现）—— ⏸️ 不处理（用户决策）

> **用户决策：不处理。** 以下记录留存备查。

`application.yml` 中数据库密码与 Redis 密码**明文存储**：

```yaml
password: X@bN!7n9m1          # PostgreSQL
password: z4e5r3u7-Redis5-1   # Redis
```

**建议**：迁移至环境变量 `${DB_PASSWORD}` 或配置中心/Vault，避免明文入库版本控制。

---

### 3.20 🟢 迁移脚本含生产数据 —— ⏸️ 不处理（用户决策）

> **用户决策：不处理。** 以下记录留存备查。

`V1.0.0__baseline.sql` 包含真实用户姓名、密码哈希、项目名称等数据。若该文件进入版本控制，存在信息泄露风险。建议基线脚本仅含结构 + 必要的字典数据，业务数据通过独立初始化脚本管理。

---

## 四、改进建议汇总

> 以下建议按优先级排序，供评估取舍。本报告**不改动任何代码/数据库**，仅提供方案。

### 4.1 高优先级（建议尽快处理）

| # | 建议 | 涉及问题 | 改动范围 |
|---|---|---|---|
| 1 | **执行物理删除迁移**：移除 10 张表 deleted 字段 + 5 个部分索引改普通约束 + 7 个索引移除 WHERE 条件 + MyBatis-Plus 配置移除逻辑删除 + 实体类移除 @TableLogic | §2.0 | 迁移脚本 V1.0.1 + 代码改动 |
| 2 | **重设外键 ON DELETE 策略**：按 §2.0.6 / §3.11 原则统一（业务主体 RESTRICT、从属 CASCADE、人员引用 SET NULL） | §3.11 | 迁移脚本 |
| 3 | **补全 9 处关键索引**（issue_activity.issue_id、issue_comment.issue_id、attachment.issue_id 等） | §3.2 | 迁移脚本 |

> ⏸️ **已排除项**（用户决策不处理）：补全外键约束（§3.1）、配置密码外部化（§3.19）、基线剥离生产数据（§3.20）

### 4.2 中优先级（建议迭代处理）

| # | 建议 | 涉及问题 |
|---|---|---|
| 6 | 统一日期类型：issue.due_date/plan_start 改为 date（若无需时间精度），或全部统一为 timestamp | §3.7 |
| 7 | 确认 issue.iteration 是否废弃，若废弃则移除 | §3.8 |
| 8 | project_member 与 user_role 增加同步机制（应用层事务保证，或考虑合并为单表） | §3.9 |
| 9 | 评估 issue_status/issue_type/label/workflow 是否需要项目级隔离 | §3.10 |
| 10 | 补充关键 CHECK 约束（progress 范围、日期先后、金额非负） | §3.6 |
| 11 | 统一 issue_label 设计风格（加 id + created_at，或明确纯关联表不记录元数据） | §3.12 |
| 12 | 布尔字段类型统一（archived/pinned 改 bool） | §3.5 |

### 4.3 低优先级（建议有空处理）

| # | 建议 | 涉及问题 |
|---|---|---|
| 13 | 补全表/字段注释（COMMENT ON） | §3.13 |
| 14 | 优化 idx_issue_archived 为部分索引或删除 | §3.14 |
| 15 | 统一 BCrypt 前缀版本 | §3.18 |
| 16 | sys_user.email 视业务需要加唯一约束 | §3.17 |


---

## 五、整体评价

### 5.1 优点

- ✅ 主键统一使用 UUID，分布式友好
- ✅ 核心表（issue）索引覆盖较全面（11 个索引）
- ✅ 外键约束基本覆盖了核心业务关系（issue→project、issue→status、issue→type 等）
- ✅ 使用 Flyway 管理迁移，有版本化基线
- ✅ 时间字段统一 timestamp(6) 微秒精度
- ✅ 工作流模型（workflow + status + transition）设计完整
- ✅ **物理删除决策消除了逻辑删除与物理外键的架构冲突**，数据一致性更易保障

### 5.2 主要风险

- ⚠️ **9 处关键索引缺失**，随数据增长将出现性能问题
- ⚠️ **ON DELETE 策略不一致**，物理删除时行为不可预测
- ⏸️ 外键缺失（23 处）：用户决策不修复，由应用层保障引用完整性

### 5.3 结论

数据库整体设计**合理可用**，核心模型（项目-工作项-状态-工作流-RBAC）结构清晰。**物理删除决策是正确的方向**，消除了原逻辑删除与物理外键的架构冲突。

主要治理工作集中在：
1. **物理删除迁移**（移除 deleted 字段 + 配置 + 代码适配）
2. **ON DELETE 策略重设**（物理删除的级联保障，现有外键部分）
3. **索引补全**（查询性能）

> ⏸️ 已排除：外键补全、配置安全、基线数据剥离（用户决策不处理）

建议按 §4.1 高优先级项逐步治理，通过增量迁移脚本 + 代码适配完成。无需大规模重构。

---

> **说明**：本报告仅作设计评审，不改动任何代码或数据库。是否采纳各项建议，请评估后决定。
