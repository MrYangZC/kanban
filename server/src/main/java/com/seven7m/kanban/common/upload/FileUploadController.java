package com.seven7m.kanban.common.upload;

import com.seven7m.kanban.common.domain.R;
import com.seven7m.kanban.common.exception.ServiceException;
import com.seven7m.kanban.common.security.UserContext;
import com.seven7m.kanban.common.util.Uuids;
import com.seven7m.kanban.entity.upload.UploadFile;
import com.seven7m.kanban.mapper.upload.UploadFileMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 文件上传接口（当前仅支持图片，供评论/附件内嵌展示）。
 *
 * 上传后会登记归属（V30 upload_file）：带 projectId 的图片只对该项目成员可见，
 * 不带的（如头像）作为全站共享资源，仅需登录即可读取。
 */
@RestController
@RequestMapping("/upload")
@RequiredArgsConstructor
public class FileUploadController {

    private final UploadProperties uploadProperties;
    private final UploadFileMapper uploadFileMapper;

    /** 服务 context-path（默认 /api），用于返回可直接访问的站内地址 */
    @Value("${server.servlet.context-path:}")
    private String contextPath;

    @PostMapping("/image")
    public R<UploadImageVO> uploadImage(@RequestParam("file") MultipartFile file,
                                        @RequestParam(required = false) String projectId) throws Exception {
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

        Path dir = uploadProperties.root();
        Files.createDirectories(dir);
        String filename = UUID.randomUUID() + "." + ext;
        Files.write(dir.resolve(filename), data);

        UUID uploaderId = UserContext.getUserId();
        if (uploaderId == null) {
            throw new ServiceException(401, "未登录，无法上传文件");
        }
        UploadFile record = new UploadFile();
        record.setId(UUID.randomUUID());
        record.setFilename(filename);
        record.setUploaderId(uploaderId);
        if (projectId != null && !projectId.isBlank()) {
            record.setProjectId(Uuids.parseOrThrow(projectId, "项目 ID"));
        }
        record.setCreatedAt(LocalDateTime.now());
        uploadFileMapper.insert(record);

        // 由配置推导，不写死 /api：部署前缀变化时前后端无需同步改
        return R.ok(new UploadImageVO(contextPath + "/uploads/" + filename));
    }
}
