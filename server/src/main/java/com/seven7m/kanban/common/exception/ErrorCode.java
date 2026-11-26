package com.seven7m.kanban.common.exception;

import lombok.Getter;

/**
 * 业务错误码字典：code 复用 HTTP 语义（400/401/403/404/429/500），message 是用户可读的默认文案。
 *
 * <p>为什么要有这张表：此前 130 多处 {@code new ServiceException(400, "项目不存在")} 散落在各 Service，
 * 同一个语义（项目不存在）在 400 与 404 两个码上各写一遍，文案还各有细微差别；
 * 前端也无法按码判断"是参数错还是没权限"。收敛成枚举后：
 * <ul>
 *   <li>码与文案只在一个地方定义，改文案不用全库搜索；</li>
 *   <li>带变量的文案用 {@code %s} 占位，由 {@link ServiceException#ServiceException(ErrorCode, Object...)}
 *       统一格式化，避免手写字符串拼接漏掉分隔符；</li>
 *   <li>枚举常量名即语义，测试可以断言 {@code ErrorCode.PROJECT_NOT_FOUND} 而不必匹配中文。</li>
 * </ul>
 *
 * <p>约定：新增文案优先复用已有常量；确实需要新语义时才在对应分区追加，
 * 并保持「码 + 一句话说清用户该怎么做」的写法。
 */
@Getter
public enum ErrorCode {

    // ==================== 通用 ====================
    /** 参数不合法（默认文案） */
    BAD_REQUEST(400, "请求参数不合法"),
    /** 未登录或会话过期 */
    UNAUTHORIZED(401, "未登录或登录已过期"),
    /** 通用无权限 */
    FORBIDDEN(403, "无权限执行该操作"),
    /** 参数为空（handler 层常用） */
    PARAM_REQUIRED(400, "参数不能为空"),
    /** 缺少必填的 query/form 参数 */
    PARAM_MISSING(400, "缺少参数：%s"),
    /** 参数类型不匹配（如 UUID 传了非 UUID 串） */
    PARAM_TYPE_MISMATCH(400, "参数类型错误：%s"),
    /** 请求体无法解析（JSON 语法错误） */
    BODY_UNREADABLE(400, "请求体格式错误"),
    /** 上传文件超过框架限制 */
    UPLOAD_TOO_LARGE(400, "上传文件超过大小限制（单个文件最大 5MB）"),
    /** 数据库约束（唯一键/长度/外键）冲突 */
    DATA_INTEGRITY_VIOLATION(400, "提交的数据不符合约束（可能超长或重复），请检查后重试"),
    /** 接口路径不存在 */
    API_NOT_FOUND(404, "接口不存在"),
    /** 兜底：未知异常，不泄露内部细节 */
    SYSTEM_ERROR(500, "系统异常，请稍后重试"),

    // ==================== 用户与登录 ====================
    /** 账号或口令不正确（不区分两者，避免用户名枚举） */
    LOGIN_FAILED(401, "用户名或密码错误"),
    /** 同一账号窗口期内失败次数过多 */
    LOGIN_LOCKED(429, "登录失败次数过多，请稍后再试"),
    USER_NOT_FOUND(404, "用户不存在"),
    USERNAME_EXISTS(400, "用户名已存在"),
    /** 初始口令为空 */
    PASSWORD_REQUIRED(400, "初始密码不能为空"),
    PASSWORD_EMPTY(400, "密码不能为空"),
    PASSWORD_TOO_SHORT(400, "密码长度不能少于 %s 位"),
    PASSWORD_TOO_LONG(400, "密码过长（超过 %s 字节的部分不会被校验，请缩短）"),
    PASSWORD_TOO_COMMON(400, "密码过于常见，请更换"),
    PASSWORD_NEEDS_LETTER_AND_DIGIT(400, "密码必须同时包含字母和数字"),
    PASSWORD_CONTAINS_USERNAME(400, "密码不能包含用户名"),
    PASSWORD_TOO_REPETITIVE(400, "密码不能包含 3 个及以上连续相同字符"),
    PASSWORD_OLD_INCORRECT(400, "原密码不正确"),
    PASSWORD_UNCHANGED(400, "新密码不能与原密码相同"),
    /** 内置种子管理员不允许删除 */
    DEFAULT_ADMIN_UNDELETABLE(400, "内置管理员账号不允许删除"),
    /** 内置种子管理员不允许降级 */
    DEFAULT_ADMIN_INDEMOTABLE(400, "内置管理员账号不允许取消管理员身份"),
    CANNOT_DELETE_SELF(400, "不能删除当前登录账号"),
    /** 删除用户前仍有项目成员引用 */
    USER_STILL_PROJECT_MEMBER(400, "该用户仍是项目成员，请先将其移出所有项目"),
    /** 删除用户前仍有角色授权 */
    USER_STILL_ROLE_HOLDER(400, "该用户仍持有角色授权，请先移除其项目角色"),
    /** 删除用户前仍有评论/动态等业务痕迹 */
    USER_HAS_ACTIVITY(400, "该用户存在评论或动态记录，不允许删除，可改为禁用账号"),
    UUID_INVALID(400, "%s 格式非法: %s"),
    /**
     * 通用长度校验：第一个参数是字段人话名（如"迭代名称"），第二个是上限字符数。
     *
     * <p>为什么统一成一条：库里的 varchar(n) 才是最终约束，服务层不提前拦就会以 500 的形式暴露
     * （PG 报 value too long），前端拿不到"哪个字段超了多少"。各域只需传自己的上限常量。
     */
    FIELD_TOO_LONG(400, "%s不能超过 %s 个字符"),

    // ==================== 项目 ====================
    PROJECT_NOT_FOUND(404, "项目不存在"),
    /**
     * 保存工作流交接配置（WorkflowService#saveHandoff）时引用到的项目不存在。
     *
     * <p>与 {@link #PROJECT_NOT_FOUND} 文案相同、码不同（历史接口对"读项目"返回 404、
     * 对"写操作里引用了不存在的项目"返回 400），为保持前端行为不变而保留两个常量。
     * 新增代码请优先用 404 的那个，除非确实是在校验请求体。
     */
    PROJECT_MISSING(400, "项目不存在"),
    PROJECT_KEY_EXISTS(400, "项目 Key 已存在：%s"),
    PROJECT_ACCESS_DENIED(403, "无项目访问权限"),
    PROJECT_EDIT_DENIED(403, "无项目编辑权限"),
    PROJECT_ADMIN_ONLY(403, "仅项目管理员可操作"),
    PROJECT_ARCHIVE_ADMIN_ONLY(403, "仅项目管理员可归档"),
    PROJECT_DELETE_ADMIN_ONLY(403, "仅项目管理员可删除"),
    PROJECT_ALREADY_ARCHIVED(400, "项目已归档"),
    PROJECT_NOT_ARCHIVED(400, "项目未归档"),
    PROJECT_ARCHIVED_READONLY(400, "项目已归档，请先取消归档后再修改"),
    /**
     * 项目至少要留一个所有者（改角色时触发）。
     *
     * <p>与 {@code MEMBER_LAST_OWNER} 是同一约束的两个入口：移除成员说"不能移除最后一个所有者"，
     * 改角色说"至少需保留一个所有者"。两条都是历史接口原话，为不改前端提示而分别保留。
     */
    PROJECT_AT_LEAST_ONE_OWNER(400, "项目至少需保留一个所有者"),
    /** 删除项目前仍有工作项 */
    PROJECT_HAS_WORK_ITEMS(400, "项目下还有 %s 个工作项，请先删除或归档后再删除项目"),

    // ==================== 项目成员 ====================
    MEMBER_NOT_FOUND(404, "该用户不是项目成员"),
    MEMBER_ALREADY_EXISTS(400, "该用户已是项目成员"),
    MEMBER_LAST_OWNER(400, "不能移除最后一个项目所有者"),
    MEMBER_MANAGE_DENIED(403, "仅项目所有者/管理员/项目经理可管理成员"),
    MEMBER_ROLE_NOT_FOUND(400, "角色不存在：%s"),
    MEMBER_ROLE_UNSUPPORTED(400, "不支持的角色：%s"),

    // ==================== 工作项（任务/需求/缺陷） ====================
    /** 工作项不存在，格式化参数为类别名（TASK/REQUIREMENT/BUG） */
    WORK_ITEM_NOT_FOUND(404, "%s 不存在"),
    ISSUE_NOT_FOUND(404, "工作项不存在"),
    STATUS_NOT_FOUND(404, "状态不存在"),
    /** 请求体里传了无效的状态 id（参数问题，沿用历史 400 语义） */
    STATUS_INVALID(400, "状态不存在"),
    STATUS_MISSING_FOR_MOVE(400, "该工作项缺少当前状态，无法移动，请先在详情中设置状态"),
    STATUS_NOT_IN_WORKFLOW(400, "该状态不属于当前工作项类型的工作流"),
    STATUS_TRANSITION_DENIED(400, "不允许从「%s」流转到「%s」"),
    WORK_ITEM_TYPE_NOT_FOUND(400, "工作项类型不存在: %s"),
    /** 路由前置校验用：只给了 issueTypeId 但查不到对应类型（历史文案不带 id） */
    WORK_ITEM_TYPE_MISSING(400, "工作项类型不存在"),
    WORK_ITEM_TYPE_REQUIRED(400, "缺少工作项类型：issueTypeId 与 issueTypeCode 至少填一个"),
    WORK_ITEM_TYPE_UNKNOWN(400, "未知的工作项类型: %s"),
    WORK_ITEM_TYPE_UNINITIALIZED(400, "未初始化 IssueType: %s"),
    WORK_ITEM_TYPE_UNREGISTERED(400, "未注册的工作项类型: %s"),
    WORK_ITEM_TYPE_CATEGORY_MISMATCH(400, "工作项类型「%s」属于 %s 类别，不能用于 %s 类别的工作项"),
    WORK_ITEM_PROJECT_MISMATCH(400, "工作项不属于该项目"),
    PARENT_NOT_FOUND(400, "父工作项不存在，或不属于当前工作项类型"),
    PARENT_CROSS_PROJECT(400, "父工作项不属于本项目"),
    PARENT_IS_SELF(400, "不能把工作项挂到自己下面"),
    PARENT_IS_DESCENDANT(400, "不能把工作项移动到它自己的子工作项下"),
    PARENT_CYCLE(400, "工作项层级异常（可能存在环）"),
    CLEAR_FIELD_UNSUPPORTED(400, "不支持清空字段: %s"),
    /** 同一次请求里既给某字段赋值、又要求清空它 */
    CLEAR_FIELD_CONFLICT(400, "同一字段不能既赋值又清空: %s"),
    NEIGHBOR_NOT_FOUND(400, "相邻卡片不存在"),
    NEIGHBOR_WRONG_COLUMN(400, "相邻卡片不属于目标状态列"),
    PERMISSION_DENIED_ACTION(403, "无权限%s"),

    // ==================== 评论 / 附件 / 标签 / 关联 ====================
    COMMENT_PARENT_NOT_FOUND(400, "父评论不存在"),
    COMMENT_PARENT_CROSS_ITEM(400, "父评论不属于当前工作项，无法回复"),
    COMMENT_DELETE_FORBIDDEN(403, "只能删除自己发表的评论"),
    COMMENT_HAS_REPLIES(400, "该评论下还有 %s 条回复，请先删除回复后再删除"),
    ATTACHMENT_DELETE_FORBIDDEN(403, "只能删除自己上传的附件"),
    ATTACHMENT_LIMIT_EXCEEDED(400, "单个工作项附件数量不能超过 %s 个"),
    LABEL_NAME_REQUIRED(400, "标签名称不能为空"),
    LABEL_NAME_TOO_LONG(400, "标签名称不能超过 50 个字符"),
    LABEL_NOT_IN_PROJECT_LIBRARY(400, "标签不在本项目的标签库中"),
    LINK_SELF(400, "不能关联自身"),
    LINK_TYPE_INVALID(400, "关联类型不合法"),
    LINK_TARGET_NOT_FOUND(404, "关联工作项不存在"),
    LINK_CROSS_PROJECT(400, "不能跨项目关联"),
    LINK_ALREADY_EXISTS(400, "关联已存在"),

    // ==================== 上传 ====================
    UPLOAD_EMPTY(400, "上传文件不能为空"),
    UPLOAD_IMAGE_TOO_LARGE(400, "图片大小不能超过 5MB"),
    UPLOAD_CONTENT_MISMATCH(400, "文件内容与扩展名不符，已拒绝上传"),
    UPLOAD_IMAGE_TYPE_UNSUPPORTED(400, "仅支持 jpg/jpeg/png/gif/webp 图片格式"),
    UPLOAD_UNAUTHENTICATED(401, "未登录，无法上传文件"),
    UPLOAD_READ_FAILED(400, "读取上传文件失败"),
    UPLOAD_DIR_CREATE_FAILED(500, "创建上传目录失败"),
    UPLOAD_SAVE_FAILED(500, "保存附件文件失败"),

    // ==================== 迭代 / 里程碑 / 风险 / 公告 / 通知 ====================
    SPRINT_NOT_FOUND(404, "迭代不存在"),
    SPRINT_IN_OTHER_PROJECT(400, "迭代不存在或不属于该项目"),
    SPRINT_DATE_RANGE_INVALID(400, "结束日期不能早于开始日期"),
    SPRINT_ALREADY_STARTED(400, "仅 PLANNED 状态可开始迭代"),
    SPRINT_NOT_ACTIVE(400, "仅 ACTIVE 状态可完成迭代"),
    MILESTONE_NOT_FOUND(404, "里程碑不存在"),
    RISK_NOT_FOUND(404, "风险不存在"),
    ANNOUNCEMENT_NOT_FOUND(404, "公告不存在"),
    NOTIFICATION_NOT_FOUND(404, "通知不存在"),
    FAVORITE_ALREADY_EXISTS(400, "已收藏"),
    FAVORITE_NOT_FOUND(404, "未收藏该项目"),
    DEV_EXCEL_NOT_CONFIGURED(400, "未配置 kanban.dev.excel-path，无法执行导入"),

    // ==================== 工作流交接 ====================
    HANDOFF_TYPE_INVALID(400, "交接类型非法"),
    HANDOFF_NOTE_TOO_LONG(400, "交接说明不能超过 500 字"),
    HANDOFF_MEMBER_REQUIRED(400, "请选择接手的成员"),
    HANDOFF_MEMBER_NOT_FOUND(400, "接手的成员不存在"),
    /**
     * 交接列配置的接手人不是项目成员。
     *
     * <p>与 {@link #MEMBER_NOT_FOUND} 文案相同、码不同：成员管理接口用 404（"查无此成员"），
     * 交接配置是校验请求体，沿用历史 400。两者语义与前端行为都不同，故分开保留。
     */
    HANDOFF_MEMBER_NOT_IN_PROJECT(400, "该用户不是项目成员"),
    HANDOFF_SPRINT_IN_OTHER_PROJECT(400, "落点迭代不存在或不属于该项目"),
    HANDOFF_LABEL_NOT_IN_LIBRARY(400, "落点标签不在本项目的标签库里"),
    HANDOFF_NOTHING_TO_REVOKE(400, "没有可撤销的交接"),
    HANDOFF_ASSIGNEE_CHANGED(400, "负责人已被再次变更，无法撤销这次交接"),
    /** 导入历史数据时：某类工作流缺少对应阶段节点 */
    WORKFLOW_STATUS_MISSING(400, "TASK 工作流缺少 category=%s 的状态，无法导入"),

    // ==================== RBAC ====================
    ROLE_NOT_FOUND(404, "角色不存在"),
    MENU_NOT_FOUND(404, "菜单不存在"),
    ROLE_BUILTIN_CODE_READONLY(400, "内置角色的编码不可修改：%s"),
    ROLE_BUILTIN_UNDELETABLE(400, "内置角色不可删除：%s"),
    /** 删除角色前仍有用户绑定 */
    ROLE_STILL_ASSIGNED(400, "该角色已分配给 %s 个用户，请先解除这些用户的角色绑定后再删除");

    /** 复用 HTTP 语义的业务码 */
    private final int code;

    /** 默认提示文案，可含 {@code %s} 占位符 */
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
