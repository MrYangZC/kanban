package com.seven7m.kanban.common.upload;

import com.seven7m.kanban.mapper.issue.attachment.IssueAttachmentMapper;
import com.seven7m.kanban.mapper.upload.UploadFileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 孤儿上传文件清理：只删"没人引用且足够旧"的文件，避免误删在用附件与在途上传。
 */
class UploadMaintenanceServiceTest {

    @TempDir
    Path tempDir;

    private UploadMaintenanceService service;

    @BeforeEach
    void setUp() {
        IssueAttachmentMapper attachmentMapper = mock(IssueAttachmentMapper.class);
        UploadFileMapper uploadFileMapper = mock(UploadFileMapper.class);
        // attachment 里登记了一个仍在使用的文件
        when(attachmentMapper.selectObjs(any())).thenReturn(objs("uploads/keep.png"));
        when(uploadFileMapper.selectObjs(any())).thenReturn(objs());
        service = new UploadMaintenanceService(
                new UploadProperties(tempDir.toString(), 50, ""), attachmentMapper, uploadFileMapper);
    }

    private static List<Object> objs(String... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private Path file(String name, Duration age) throws Exception {
        Path f = tempDir.resolve(name);
        Files.write(f, new byte[]{1, 2, 3});
        if (age != null) {
            Files.setLastModifiedTime(f, FileTime.from(Instant.now().minus(age)));
        }
        return f;
    }

    @Test
    void dryRunReportsOrphansWithoutDeleting() throws Exception {
        Path keep = file("keep.png", Duration.ofDays(2));
        Path orphan = file("orphan.png", Duration.ofDays(2));

        UploadMaintenanceService.CleanupResult result =
                service.cleanupOrphans(true, Duration.ofHours(24));

        assertThat(result.scannedFiles()).isEqualTo(2);
        assertThat(result.orphanFiles()).isEqualTo(1);
        assertThat(result.deletedFiles()).isZero();
        assertThat(result.orphanSamples()).containsExactly("orphan.png");
        assertThat(Files.exists(keep)).isTrue();
        assertThat(Files.exists(orphan)).isTrue();
    }

    @Test
    void deletesOnlyUnreferencedOldFiles() throws Exception {
        Path keep = file("keep.png", Duration.ofDays(2));
        Path orphan = file("orphan.png", Duration.ofDays(2));

        UploadMaintenanceService.CleanupResult result =
                service.cleanupOrphans(false, Duration.ofHours(24));

        assertThat(result.deletedFiles()).isEqualTo(1);
        assertThat(Files.exists(keep)).isTrue();
        assertThat(Files.exists(orphan)).isFalse();
    }

    @Test
    void keepsFreshFilesInFlight() throws Exception {
        // 刚落盘但记录尚未提交（在途上传）不能被清理
        Path fresh = file("fresh.png", null);

        UploadMaintenanceService.CleanupResult result =
                service.cleanupOrphans(false, Duration.ofHours(24));

        assertThat(result.orphanFiles()).isZero();
        assertThat(Files.exists(fresh)).isTrue();
    }

    @Test
    void missingDirectoryIsNoOp() {
        UploadMaintenanceService.CleanupResult result =
                service.cleanupOrphans(false, Duration.ofHours(24));

        assertThat(result.scannedFiles()).isZero();
        assertThat(result.deletedFiles()).isZero();
    }

    @Test
    void onlyReferencesFilesInsideUploadsPrefix() throws Exception {
        // local/ 前缀是"仅登记元信息"的存量附件，不对应真实文件，但也不能因此把同名文件当孤儿之外处理
        Path legacy = file("legacy.png", Duration.ofDays(2));

        UploadMaintenanceService.CleanupResult result =
                service.cleanupOrphans(true, Duration.ofHours(24));

        assertThat(result.orphanFiles()).isEqualTo(1);
        assertThat(Files.exists(legacy)).isTrue();
    }
}
