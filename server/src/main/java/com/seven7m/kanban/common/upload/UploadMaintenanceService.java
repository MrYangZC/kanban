package com.seven7m.kanban.common.upload;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.entity.issue.attachment.IssueAttachment;
import com.seven7m.kanban.entity.upload.UploadFile;
import com.seven7m.kanban.mapper.issue.attachment.IssueAttachmentMapper;
import com.seven7m.kanban.mapper.upload.UploadFileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * 上传目录的孤儿文件清理。
 *
 * <p>背景：附件记录与磁盘文件是两套存储。修复了"删除工作项/项目时不清文件"之后，
 * 历史遗留（以及任何绕过 Service 的删除）仍会留下永远没人引用的文件——
 * 它们占着磁盘，且 {@code UploadsAuthInterceptor} 对"查不到登记信息"的文件只校验登录态，
 * 意味着凭 URL 仍可被任何登录用户下载。
 *
 * <p>判定"孤儿"：磁盘上存在、但既不在 attachment.storage_key 里、也不在 upload_file.filename 里。
 * 只清理超过 {@code minAge} 的文件，避免删掉"刚上传、记录还没落库"的在途文件。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadMaintenanceService {

    private final UploadProperties uploadProperties;
    private final IssueAttachmentMapper attachmentMapper;
    private final UploadFileMapper uploadFileMapper;
    private final UploadReferenceResolver referenceResolver;

    /**
     * @param dryRun  只统计不删除（默认，避免误删）
     * @param minAge  只清理早于该时长的文件
     */
    public CleanupResult cleanupOrphans(boolean dryRun, Duration minAge) {
        Set<String> referenced = referenceResolver.referencedFilenames();
        Path root = uploadProperties.root();
        if (!Files.isDirectory(root)) {
            return new CleanupResult(0, referenced.size(), 0, 0, List.of(), List.of(), dryRun);
        }

        Instant deadline = Instant.now().minus(minAge);
        int scanned = 0;
        int orphanCount = 0;
        int deleted = 0;
        List<String> samples = new ArrayList<>();

        try (Stream<Path> files = Files.list(root)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                scanned++;
                String filename = file.getFileName().toString();
                if (referenced.contains(filename)) {
                    continue;
                }
                if (Files.getLastModifiedTime(file).toInstant().isAfter(deadline)) {
                    // 太新：可能是在途上传（文件已落盘、记录尚未提交），本轮跳过
                    continue;
                }
                orphanCount++;
                if (samples.size() < 20) {
                    samples.add(filename);
                }
                if (!dryRun) {
                    try {
                        if (Files.deleteIfExists(file)) {
                            deleted++;
                        }
                    } catch (IOException e) {
                        log.warn("删除孤儿上传文件失败: {}", filename, e);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("扫描上传目录失败: " + root, e);
        }

        if (!dryRun && deleted > 0) {
            log.info("已清理孤儿上传文件 {} 个（扫描 {}，引用 {}）", deleted, scanned, referenced.size());
        }
        return new CleanupResult(scanned, referenced.size(), orphanCount, deleted, samples,
                List.of(), dryRun);
    }


    /** 清理结果；dryRun=true 时 deleted 恒为 0 */
    public record CleanupResult(int scannedFiles,
                                int referencedFiles,
                                int orphanFiles,
                                int deletedFiles,
                                List<String> orphanSamples,
                                List<String> errors,
                                boolean dryRun) {
    }
}
