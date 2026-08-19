package com.seven7m.kanban.common.upload;

/**
 * 图片上传结果：返回可访问的静态资源 URL（含 context-path /api，前端直接使用）。
 */
public record UploadImageVO(String url) {
}
