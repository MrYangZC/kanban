package com.seven7m.kanban.service.workitem;

import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.entity.workitem.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 父级校验守护测试。
 *
 * <p>接口支持设置 parentId 之后，父级来自外部输入，以下几类脏数据必须被挡住：
 * 父级不存在 / 跨项目 / 挂到自己 / 挂到自己的子孙（成环）。
 * 成环尤其危险：树形渲染会无限递归，批量删除会沿着父子链死循环。
 */
class WorkItemParentGuardTest {

    private final UUID projectA = UUID.randomUUID();
    private final UUID projectB = UUID.randomUUID();

    /** 内存版 loader：按 id 取任务，null 表示不存在 */
    private final Map<UUID, Task> table = new HashMap<>();

    private final Function<UUID, Task> loader = table::get;

    private Task task(UUID id, UUID projectId, UUID parentId) {
        Task t = new Task();
        t.setId(id);
        t.setProjectId(projectId);
        t.setParentId(parentId);
        table.put(id, t);
        return t;
    }

    @Test
    @DisplayName("父级为 null 时直接放行（顶层节点）")
    void nullParentIsAllowed() {
        assertThatCode(() -> WorkItemParentGuard.requireValid(null, null, projectA, loader))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("父级不存在：报「父工作项不存在」")
    void missingParentIsRejected() {
        assertThatThrownBy(() -> WorkItemParentGuard.requireValid(null, UUID.randomUUID(), projectA, loader))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("父工作项不存在");
    }

    @Test
    @DisplayName("父级属于别的项目：报「不属于本项目」")
    void crossProjectParentIsRejected() {
        UUID parentId = UUID.randomUUID();
        task(parentId, projectB, null);

        assertThatThrownBy(() -> WorkItemParentGuard.requireValid(null, parentId, projectA, loader))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不属于本项目");
    }

    @Test
    @DisplayName("把工作项挂到自己下面：拒绝")
    void selfParentIsRejected() {
        UUID selfId = UUID.randomUUID();
        task(selfId, projectA, null);

        assertThatThrownBy(() -> WorkItemParentGuard.requireValid(selfId, selfId, projectA, loader))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("不能把工作项挂到自己下面");
    }

    @Test
    @DisplayName("把工作项挂到自己的子孙下面（成环）：拒绝")
    void cycleThroughDescendantIsRejected() {
        UUID rootId = UUID.randomUUID();
        UUID childId = UUID.randomUUID();
        UUID grandChildId = UUID.randomUUID();
        task(rootId, projectA, null);
        task(childId, projectA, rootId);
        task(grandChildId, projectA, childId);

        // 把 root 挂到它的孙子下 → 形成环
        assertThatThrownBy(() -> WorkItemParentGuard.requireValid(rootId, grandChildId, projectA, loader))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("子工作项下");
    }

    @Test
    @DisplayName("正常的层级调整：放行")
    void validReparentIsAllowed() {
        UUID aId = UUID.randomUUID();
        UUID bId = UUID.randomUUID();
        task(aId, projectA, null);
        task(bId, projectA, null);

        assertThatCode(() -> WorkItemParentGuard.requireValid(bId, aId, projectA, loader))
                .doesNotThrowAnyException();
    }
}
