package com.seven7m.kanban.common.upload;

import com.seven7m.kanban.common.exception.ServiceException;

import java.util.Locale;
import java.util.Set;

/**
 * 图片上传通用校验工具：扩展名白名单 + magic bytes 内容校验。
 * 供通用上传接口与缺陷附件上传共用。
 */
public final class ImageFileUtil {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024L;
    private static final Set<String> ALLOWED_EXT = Set.of("jpg", "jpeg", "png", "gif", "webp");

    private ImageFileUtil() {
    }

    public static long maxFileSize() {
        return MAX_FILE_SIZE;
    }

    /** 校验扩展名，返回小写扩展名（不含点）；不合法抛 400 */
    public static String resolveExtension(String filename) {
        String ext = "";
        if (filename != null && filename.lastIndexOf('.') >= 0) {
            ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        }
        if (!ALLOWED_EXT.contains(ext)) {
            throw new ServiceException(400, "仅支持 jpg/jpeg/png/gif/webp 图片格式");
        }
        return ext;
    }

    /** 校验图片 magic bytes：JPEG=FFD8FF，PNG=89504E47，GIF=GIF8，WebP=RIFF....WEBP */
    public static boolean matchesMagicBytes(byte[] data, String ext) {
        if (data == null || data.length == 0) {
            return false;
        }
        return switch (ext) {
            case "jpg", "jpeg" -> data.length >= 3
                    && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF;
            case "png" -> data.length >= 8
                    && (data[0] & 0xFF) == 0x89 && data[1] == 'P' && data[2] == 'N' && data[3] == 'G'
                    && (data[4] & 0xFF) == 0x0D && (data[5] & 0xFF) == 0x0A
                    && (data[6] & 0xFF) == 0x1A && (data[7] & 0xFF) == 0x0A;
            case "gif" -> data.length >= 4
                    && data[0] == 'G' && data[1] == 'I' && data[2] == 'F' && data[3] == '8';
            case "webp" -> data.length >= 12
                    && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                    && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P';
            default -> false;
        };
    }
}
