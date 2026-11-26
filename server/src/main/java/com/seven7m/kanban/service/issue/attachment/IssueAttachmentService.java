package com.seven7m.kanban.service.issue.attachment;

import com.baomidou.mybatisplus.extension.service.IService;
import com.seven7m.kanban.dto.issue.AttachmentVO;
import com.seven7m.kanban.entity.issue.attachment.IssueAttachment;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * 工作项附件服务接口。
 *
 * <p>本质是「单实体（attachment）+ 单主 Mapper」的增删查，因此继承 {@link IService}
 * 复用 MyBatis-Plus 的按 id 取 / 保存 / 计数 / 删除能力，实现里不必再写一遍
 * {@code attachmentMapper.xxx} 的样板（这条链路没有自定义 wrapper 或跨表依赖被藏进去）。
 * 领域方法只剩三个：真实文件上传、旧客户端登记元信息、删除（含文件清理）。
 *
 * <p>实现见 {@code com.seven7m.kanban.service.issue.attachment.impl.IssueAttachmentServiceImpl}。
 */
public interface IssueAttachmentService extends IService<IssueAttachment> {

    /** 某工作项的附件列表（按创建时间倒序；上传人一次性批量查出，避免逐行查用户表） */
    List<AttachmentVO> list(UUID issueId);

    /**
     * 真实上传附件：校验图片类型与内容 → 落盘到 uploads 目录 → 写 attachment 记录（含 url）。
     *
     * <p>失败路径分两种：入库失败立即删文件；事务整体回滚时由注册的同步器删文件。
     */
    AttachmentVO upload(UUID issueId, MultipartFile file);

    /** 兼容旧调用：仅登记附件元信息（无真实文件），新客户端请走 {@link #upload} */
    AttachmentVO add(UUID issueId, String fileName, String contentType, Long fileSize);

    /** 删除附件：只有上传人本人或系统管理员可删，并同步清理磁盘文件 */
    void delete(UUID issueId, UUID attachmentId);
}
