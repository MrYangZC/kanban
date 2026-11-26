package com.seven7m.kanban.service.workitem;

/**
 * 任务（落 task 表）的工作项服务。
 *
 * <p>接口本身不带方法：三个类别共用 {@link WorkItemService} 的全部契约，
 * 之所以仍为每类声明一个接口，是为了让注入点可以按类别取 Bean
 * （例如看板与导入各自只关心"任务"这一种），而不是靠名称限定符去区分三个同类型 Bean。
 */
public interface TaskService extends WorkItemService {
}
