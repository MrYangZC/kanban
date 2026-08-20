package com.seven7m.kanban.common.upload;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.exception.ServiceException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 文件上传接口（当前仅支持图片，供评论/附件内嵌展示）。
 */
@RestController
@RequestMapping("/upload")
public class FileUploadController {

    private static final Path UPLOAD_DIR = Paths.get("./uploads");

    @PostMapping("/image")
    public R<UploadImageVO> uploadImage(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            throw new ServiceException(400, "上传文件不能为空");
        }
        if (file.getSize() > ImageFileUtil.maxFileSize()) {
            throw new ServiceException(400, "图片大小不能超过 5MB");
        }
        String ext = ImageFileUtil.resolveExtension(file.getOriginalFilename());
        byte[] data = file.getBytes();
        // 文件头（magic bytes）内容校验，防止伪装扩展名上传任意文件
        if (!ImageFileUtil.matchesMagicBytes(data, ext)) {
            throw new ServiceException(400, "文件内容与扩展名不符，已拒绝上传");
        }

        Files.createDirectories(UPLOAD_DIR);
        String filename = UUID.randomUUID() + "." + ext;
        Files.write(UPLOAD_DIR.resolve(filename), data);
        return R.ok(new UploadImageVO("/api/uploads/" + filename));
    }
}
