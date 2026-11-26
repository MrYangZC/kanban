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
    /**
     * 附件/头像的对外访问前缀（含网关或 nginx 的部署前缀，不含结尾的 /uploads）。
     * 默认取 server.servlet.context-path（直连后端时就是 /api）；
     * 部署到 nginx 的专属前缀下（如 /kanban-api）时用 kanban.upload.public-prefix 覆盖，
     * 这样浏览器拿到的地址和它实际发出的前缀一致，不必在前端二次拼接。
     */
    private final String publicPrefix;

    public UploadProperties(@Value("${kanban.upload.dir:./uploads}") String dir,
                            @Value("${kanban.upload.max-per-issue:50}") int maxAttachmentsPerIssue,
                            @Value("${kanban.upload.public-prefix:${server.servlet.context-path:}}")
                            String publicPrefix) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        this.maxAttachmentsPerIssue = maxAttachmentsPerIssue;
        this.publicPrefix = trimTrailingSlash(publicPrefix);
    }

    private static String trimTrailingSlash(String prefix) {
        if (prefix == null) {
            return "";
        }
        String trimmed = prefix.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    /** 附件/头像的对外访问地址：&lt;public-prefix&gt;/uploads/&lt;filename&gt; */
    public String publicUrl(String filename) {
        return publicPrefix + "/" + STORAGE_KEY_PREFIX + filename;
    }

    /** 附件存储根目录（绝对规范化路径，便于做路径穿越校验） */
    /**
     * 附件/头像的对外访问前缀（如 {@code /api} 或部署后的 {@code /kanban-api}）。
     *
     * <p>附件 Cookie 的作用路径必须用它而不是 {@code request.getContextPath()}：
     * 经过 nginx/vite 的路径改写后，浏览器看到的路径前缀与 Servlet 的 context-path 并不相同，
     * 用错会导致 Cookie 根本发不出去（表现为头像/附件 401）。
     */
    public String publicPrefix() {
        return publicPrefix;
    }

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
