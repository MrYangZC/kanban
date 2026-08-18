package com.seven7m.kanban.workspace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.workspace.dto.CreateWorkspaceRequest;
import com.seven7m.kanban.workspace.dto.WorkspaceVO;
import com.seven7m.kanban.workspace.entity.Workspace;
import com.seven7m.kanban.workspace.mapper.WorkspaceMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WorkspaceService {

    private final WorkspaceMapper workspaceMapper;

    @Transactional
    public WorkspaceVO create(CreateWorkspaceRequest req) {
        long dup = workspaceMapper.selectCount(Wrappers.<Workspace>lambdaQuery()
                .eq(Workspace::getWorkspaceKey, req.getWorkspaceKey()));
        if (dup > 0) {
            throw new ServiceException(400, "Workspace Key 已存在：" + req.getWorkspaceKey());
        }
        Workspace ws = new Workspace();
        BeanUtils.copyProperties(req, ws);
        ws.setId(UUID.randomUUID());
        ws.setCreatedBy(UserContext.getUserId());
        ws.setCreatedAt(LocalDateTime.now());
        ws.setUpdatedAt(LocalDateTime.now());
        workspaceMapper.insert(ws);
        return toVO(ws);
    }

    public List<WorkspaceVO> listAll() {
        return workspaceMapper.selectList(Wrappers.<Workspace>lambdaQuery()
                        .eq(Workspace::getDeleted, 0)
                        .orderByDesc(Workspace::getCreatedAt))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    public WorkspaceVO get(UUID id) {
        Workspace ws = workspaceMapper.selectById(id);
        if (ws == null || ws.getDeleted() != null && ws.getDeleted() == 1) {
            throw new ServiceException(404, "Workspace 不存在");
        }
        return toVO(ws);
    }

    private WorkspaceVO toVO(Workspace ws) {
        WorkspaceVO vo = new WorkspaceVO();
        BeanUtils.copyProperties(ws, vo);
        return vo;
    }
}
