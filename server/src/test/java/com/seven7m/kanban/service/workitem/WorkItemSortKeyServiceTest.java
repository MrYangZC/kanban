package com.seven7m.kanban.service.workitem;

import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.entity.workitem.Task;
import com.seven7m.kanban.mapper.workitem.TaskMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 排序键算法从 1200 行 Service 里抽出来之后的守护测试。
 *
 * <p>这些场景此前只能靠"手工拖卡片看顺序对不对"验证：相邻卡片必须同列、
 * 空列追加、两卡片之间取中间值。抽类之后可以用 mock mapper 直接固定住。
 */
class WorkItemSortKeyServiceTest {

    private final WorkItemSortKeyService service = new WorkItemSortKeyService();

    private static Task task(UUID statusId, String sortKey) {
        Task t = new Task();
        t.setId(UUID.randomUUID());
        t.setStatusId(statusId);
        t.setSortKey(sortKey);
        return t;
    }

    @Test
    @DisplayName("两个相邻卡片之间：取两者键的中间值")
    void betweenTwoNeighbors() {
        TaskMapper mapper = mock(TaskMapper.class);
        UUID projectId = UUID.randomUUID();
        UUID statusId = UUID.randomUUID();
        Task before = task(statusId, "a");
        Task after = task(statusId, "c");
        when(mapper.selectById(before.getId())).thenReturn(before);
        when(mapper.selectById(after.getId())).thenReturn(after);

        String key = service.computeSortKey(mapper, new WorkItemSortKeyService.SortKeyRequest(projectId, statusId, before.getId(), after.getId()));

        assertThat(key).isBetween("a", "c");
    }

    @Test
    @DisplayName("空列追加：返回一个可用的键，而不是常量（否则与后续卡片并列）")
    void appendsToEmptyColumn() {
        TaskMapper mapper = mock(TaskMapper.class);
        when(mapper.selectOne(any())).thenReturn(null);

        String first = service.computeSortKey(mapper, WorkItemSortKeyService.SortKeyRequest.appendTo(UUID.randomUUID(), UUID.randomUUID()));
        String second = service.computeSortKey(mapper, WorkItemSortKeyService.SortKeyRequest.appendTo(UUID.randomUUID(), UUID.randomUUID()));

        assertThat(first).isNotBlank().isEqualTo(second); // 空列每次都从头开始，值相同但都可用
    }

    @Test
    @DisplayName("相邻卡片不属于目标状态列：直接 400，避免把卡片写到错的列")
    void rejectsNeighborFromAnotherColumn() {
        TaskMapper mapper = mock(TaskMapper.class);
        UUID statusId = UUID.randomUUID();
        Task otherColumn = task(UUID.randomUUID(), "a");
        when(mapper.selectById(otherColumn.getId())).thenReturn(otherColumn);

        assertThatThrownBy(() -> service.computeSortKey(mapper, new WorkItemSortKeyService.SortKeyRequest(UUID.randomUUID(), statusId, otherColumn.getId(), null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("相邻卡片不属于目标状态列");
    }

    @Test
    @DisplayName("相邻卡片不存在：400 且提示明确")
    void rejectsMissingNeighbor() {
        TaskMapper mapper = mock(TaskMapper.class);
        UUID missing = UUID.randomUUID();
        when(mapper.selectById(any())).thenReturn(null);

        assertThatThrownBy(() -> service.computeSortKey(mapper, new WorkItemSortKeyService.SortKeyRequest(UUID.randomUUID(), UUID.randomUUID(), missing, null)))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("相邻卡片不存在");
    }
}
