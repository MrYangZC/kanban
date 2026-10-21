package com.seven7m.kanban.common.upload;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 附件存储配置。
 * 物理目录由 kanban.upload.dir 指定（默认 ./uploads，相对进程工作目录，也可给绝对路径）。
 * 注意：数据库 storage_key 前缀固定为 "uploads/"（存量数据协议，且 UploadsAuthInterceptor
 * 依赖它反查附件归属），与物理目录解耦，不随 kanban.upload.dir 变更。
 */
@Component
public class UploadProperties {

    /** storage_key 前缀：属数据协议，勿随物理目录调整 */
    public static final String STORAGE_KEY_PREFIX = "uploads/";

    private final Path root;
    private final int maxAttachmentsPerIssue;

    public UploadProperties(@Value("${kanban.upload.dir:./uploads}") String dir,
                            @Value("${kanban.upload.max-per-issue:50}") int maxAttachmentsPerIssue) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        this.maxAttachmentsPerIssue = maxAttachmentsPerIssue;
    }

    /** 附件存储根目录（绝对规范化路径，便于做路径穿越校验） */
    public Path root() {
        return root;
    }

    /** 单个工作项附件数量上限，&lt;= 0 表示不限制 */
    public int maxAttachmentsPerIssue() {
        return maxAttachmentsPerIssue;
    }

    /** 静态资源映射用的目录 URI（补结尾斜杠，否则 Spring 会当成单个文件） */
    public String rootUri() {
        String uri = root.toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }
}
