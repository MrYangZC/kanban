package com.seven7m.kanban.dto.issue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class MoveIssueRequest {

    @NotNull
    private UUID statusId;

    /**
     * 落点相邻的卡片 id，用于后端计算 fractional index。
     * beforeId：拖到某卡片之前；afterId：拖到某卡片之后。二选一，皆空则追加到末尾。
     */
    private UUID beforeId;

    private UUID afterId;
}
