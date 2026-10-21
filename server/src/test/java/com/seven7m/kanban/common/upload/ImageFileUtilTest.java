package com.seven7m.kanban.common.upload;

import com.seven7m.kanban.common.exception.ServiceException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 上传文件的类型与内容校验测试：白名单与 magic bytes 是防伪装上传的唯一屏障。
 */
class ImageFileUtilTest {

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};
    private static final byte[] GIF = {'G', 'I', 'F', '8', '9', 'a'};
    private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

    @Test
    @DisplayName("扩展名白名单：大小写不敏感，非图片格式与缺失扩展名一律拒绝")
    void resolveExtensionChecksWhitelist() {
        assertThat(ImageFileUtil.resolveExtension("a.PNG")).isEqualTo("png");
        assertThat(ImageFileUtil.resolveExtension("a.jpeg")).isEqualTo("jpeg");
        assertThat(ImageFileUtil.resolveExtension("a.WebP")).isEqualTo("webp");

        assertThatThrownBy(() -> ImageFileUtil.resolveExtension("a.svg"))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> ImageFileUtil.resolveExtension("a.jsp"))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> ImageFileUtil.resolveExtension("noext"))
                .isInstanceOf(ServiceException.class);
        assertThatThrownBy(() -> ImageFileUtil.resolveExtension(null))
                .isInstanceOf(ServiceException.class);
    }

    @Test
    @DisplayName("magic bytes：内容与扩展名不符时拒绝")
    void matchesMagicBytesRejectsSpoofedContent() {
        assertThat(ImageFileUtil.matchesMagicBytes(PNG, "png")).isTrue();
        assertThat(ImageFileUtil.matchesMagicBytes(JPEG, "jpg")).isTrue();
        assertThat(ImageFileUtil.matchesMagicBytes(JPEG, "jpeg")).isTrue();
        assertThat(ImageFileUtil.matchesMagicBytes(GIF, "gif")).isTrue();
        assertThat(ImageFileUtil.matchesMagicBytes(WEBP, "webp")).isTrue();

        // 把脚本/文本内容伪装成图片
        assertThat(ImageFileUtil.matchesMagicBytes("<?php echo 1;".getBytes(), "png")).isFalse();
        assertThat(ImageFileUtil.matchesMagicBytes(JPEG, "png")).isFalse();
        // 未知扩展名不参与匹配
        assertThat(ImageFileUtil.matchesMagicBytes(PNG, "svg")).isFalse();
    }

    @Test
    @DisplayName("magic bytes：空数据与超短数据不得越界或误判")
    void matchesMagicBytesHandlesShortData() {
        assertThat(ImageFileUtil.matchesMagicBytes(null, "png")).isFalse();
        assertThat(ImageFileUtil.matchesMagicBytes(new byte[0], "png")).isFalse();
        assertThat(ImageFileUtil.matchesMagicBytes(new byte[]{0x01}, "png")).isFalse();
        assertThat(ImageFileUtil.matchesMagicBytes(new byte[]{0x01, 0x02}, "jpg")).isFalse();
        // 前缀正确但长度不足 12 的 webp 不应通过
        assertThat(ImageFileUtil.matchesMagicBytes(new byte[]{'R', 'I', 'F', 'F'}, "webp")).isFalse();
    }

    @Test
    @DisplayName("单文件大小上限为 5MB")
    void maxFileSizeIsFiveMb() {
        assertThat(ImageFileUtil.maxFileSize()).isEqualTo(5L * 1024 * 1024);
    }
}
