package com.seven7m.kanban.common.constant;

/**
 * 工作项三张物理表（task / requirement / bug）共有的列名常量。
 *
 * <p>三表的公共列完全同构，所以「列名」这件事只需要一份事实源：
 * 之前 {@code UpdateWrapper#set("assignee_id", ...)} 这类调用散落在业务方法里，
 * 列名拼错只有运行时才会以 500 的形式炸出来；常量化之后，IDE 能跳转、能重构、能改一处生效。
 *
 * <p>为什么不用 {@code LambdaUpdateWrapper} 的方法引用：工作项服务是泛型基类
 * （{@code T extends AbstractWorkItem}），无法在基类里给出具体实体的列映射；
 * 而这里又要写"清空为 null"这种 lambda 表达不了的语句。列名常量是这层唯一可共用的答案。
 */
public final class WorkItemColumns {

    public static final String ID = "id";
    public static final String PROJECT_ID = "project_id";
    public static final String ISSUE_NUMBER = "issue_number";
    public static final String ISSUE_TYPE_ID = "issue_type_id";
    public static final String STATUS_ID = "status_id";
    public static final String TITLE = "title";
    public static final String DESCRIPTION = "description";
    public static final String PRIORITY = "priority";
    public static final String ASSIGNEE_ID = "assignee_id";
    public static final String VERIFIER_ID = "verifier_id";
    public static final String REPORTER_ID = "reporter_id";
    public static final String PARENT_ID = "parent_id";
    public static final String SPRINT_ID = "sprint_id";
    public static final String DUE_DATE = "due_date";
    public static final String DONE_AT = "done_at";
    public static final String ARCHIVED = "archived";
    public static final String MODULE = "module";
    public static final String SEVERITY = "severity";
    public static final String FIX_VERSION = "fix_version";
    public static final String SOURCE = "source";
    public static final String ITERATION = "iteration";
    public static final String PLAN_START = "plan_start";
    public static final String NO_FIX_REASON = "no_fix_reason";
    public static final String SORT_KEY = "sort_key";
    public static final String CREATED_AT = "created_at";
    public static final String UPDATED_AT = "updated_at";

    private WorkItemColumns() {
    }
}
