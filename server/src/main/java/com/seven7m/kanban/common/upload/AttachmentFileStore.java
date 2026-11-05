package com.seven7m.kanban.common.upload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;

/**
 * 附件物理文件的删除入口。
 *
 * <p>抽出来的原因：附件的「记录」和「文件」分属两处（MySQL/PG 行 + 本地磁盘），
 * 删除路径分散在工作项删除、项目删除、单附件删除三处，之前只有最后一处清了文件，
 * 于是删工作项/删项目后文件永久留在磁盘上；而 {@code UploadsAuthInterceptor} 对
 * “查不到附件记录”的文件只校验登录态，导致已删除的附件仍可被任意登录用户凭 URL 下载。
 *
 * <p>所有删除都是 best-effort：文件清理失败不影响业务事务（残留文件由运维脚本兜底），
 * 但任何跨出上传根目录的 storageKey 都会被拒绝，避免误删。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AttachmentFileStore {

    private final UploadProperties uploadProperties;

    /** 删除单个 storageKey 对应的文件（仅限本地上传目录内） */
    public void deleteQuietly(String storageKey) {
        if (storageKey == null || !storageKey.startsWith(UploadProperties.STORAGE_KEY_PREFIX)) {
            return;
        }
        try {
            Path root = uploadProperties.root().normalize();
            Path file = root.resolve(storageKey.substring(UploadProperties.STORAGE_KEY_PREFIX.length())).normalize();
            if (!file.startsWith(root)) {
                log.warn("拒绝删除上传目录之外的文件: {}", storageKey);
                return;
            }
            if (Files.deleteIfExists(file)) {
                log.debug("已删除附件文件: {}", storageKey);
            }
        } catch (Exception e) {
            // 清理失败不影响记录删除，交由运维定期清理孤儿文件
            log.warn("删除附件文件失败: {}", storageKey, e);
        }
    }

    /** 批量删除；空集合/空 key 自动跳过 */
    public void deleteQuietly(Collection<String> storageKeys) {
        if (storageKeys == null) {
            return;
        }
        storageKeys.forEach(this::deleteQuietly);
    }
}
