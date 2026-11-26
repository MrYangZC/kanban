package com.seven7m.kanban.common.upload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 附件物理文件清理：必须删掉 uploads 目录内的文件，且绝不越出根目录。
 */
class AttachmentFileStoreTest {

    @TempDir
    Path tempDir;

    private AttachmentFileStore store() {
        return new AttachmentFileStore(new UploadProperties(tempDir.toString(), 50, ""));
    }

    @Test
    void deletesFileUnderUploadRoot() throws Exception {
        Path file = tempDir.resolve("a.png");
        Files.write(file, new byte[]{1, 2, 3});

        store().deleteQuietly(UploadProperties.STORAGE_KEY_PREFIX + "a.png");

        assertFalse(Files.exists(file), "uploads/ 下的文件应被删除");
    }

    @Test
    void ignoresStorageKeysOutsideUploadProtocol() throws Exception {
        Path file = tempDir.resolve("b.png");
        Files.write(file, new byte[]{1});

        // 存量"仅登记元信息"的附件用 local/ 前缀，不对应任何真实文件
        store().deleteQuietly("local/" + file.getFileName());

        assertTrue(Files.exists(file), "非 uploads/ 前缀不应触发删除");
    }

    @Test
    void refusesPathTraversal() throws Exception {
        Path outside = tempDir.getParent().resolve("outside-" + System.nanoTime() + ".txt");
        Files.write(outside, new byte[]{1});
        try {
            store().deleteQuietly("uploads/../" + outside.getFileName());

            assertTrue(Files.exists(outside), "跨出上传根目录的 storageKey 必须被拒绝");
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void toleratesMissingFileAndNullKey() {
        store().deleteQuietly((String) null);
        store().deleteQuietly(UploadProperties.STORAGE_KEY_PREFIX + "not-exists.png");
    }
}
