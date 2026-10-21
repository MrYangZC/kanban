package com.seven7m.kanban.entity.upload;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 通用图片上传的归属记录（V30）。
 * project_id 为空表示全站共享资源（如用户头像），仅登录即可读取；
 * 非空表示项目内资源，访问需具备该项目 issue:view 权限。
 */
@Getter
@Setter
@TableName("upload_file")
public class UploadFile {
    @TableId(type = IdType.ASSIGN_UUID)
    private UUID id;

    private String filename;
    private UUID uploaderId;
    private UUID projectId;
    private LocalDateTime createdAt;
}
