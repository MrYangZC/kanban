package com.seven7m.kanban.common.upload;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.seven7m.kanban.entity.issue.attachment.IssueAttachment;
import com.seven7m.kanban.entity.upload.UploadFile;
import com.seven7m.kanban.mapper.issue.attachment.IssueAttachmentMapper;
import com.seven7m.kanban.mapper.upload.UploadFileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 上传目录的孤儿文件判定。
 *
 * <p>从 {@code UploadMaintenanceService} 里抽出来，一是为了能单独测试，
 * 二是这层"什么算被引用"的规则本身的正确性比清理动作更关键：
 * 漏算一种引用方式，清理任务就会**删掉还在用的文件**。
 *
 * <p>当前需要覆盖的引用来源：
 * <ol>
 *   <li>{@code attachment.storage_key}（工作项附件，协议为 uploads/&lt;filename&gt;）；</li>
 *   <li>{@code upload_file.filename}（通用图片上传登记）；</li>
 *   <li>{@code sys_user.avatar_url}（头像直接存 URL，<b>没有</b> upload_file 记录）；</li>
 *   <li>富文本里内嵌的图片：工作项描述、评论、公告正文中出现的 /uploads/&lt;filename&gt;。</li>
 * </ol>
 *
 * <p>第 3、4 条是后补的：此前只统计前两条，导致"头像文件在磁盘上、但不在登记表里"被判定为孤儿，
 * 清理后头像 404（线上已实际发生一次）。富文本同理——评论里贴的截图没有任何登记。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadReferenceResolver {

    /** 从 /uploads/<filename> 或 uploads/<filename> 里取出文件名 */
    private static final Pattern UPLOAD_URL = Pattern.compile("/uploads/([A-Za-z0-9._-]+)");
    private static final Pattern STORAGE_KEY = Pattern.compile("^uploads/([A-Za-z0-9._-]+)$");

    private final IssueAttachmentMapper attachmentMapper;
    private final UploadFileMapper uploadFileMapper;
    private final JdbcTemplate jdbcTemplate;

    /** 仍被引用的文件名集合 */
    public Set<String> referencedFilenames() {
        Set<String> referenced = new HashSet<>();

        // 1) 工作项附件
        for (Object key : attachmentMapper.selectObjs(
                Wrappers.<IssueAttachment>query().select("storage_key"))) {
            if (key == null) {
                continue;
            }
            Matcher m = STORAGE_KEY.matcher(key.toString().trim());
            if (m.matches()) {
                referenced.add(m.group(1));
            }
        }

        // 2) 通用图片上传登记
        for (Object name : uploadFileMapper.selectObjs(
                Wrappers.<UploadFile>query().select("filename"))) {
            if (name != null) {
                referenced.add(name.toString());
            }
        }

        // 3) 头像
        collectFromText("select avatar_url from sys_user where avatar_url is not null", referenced);

        // 4) 富文本：描述 / 评论 / 公告
        collectFromText("select description from task where description like '%/uploads/%'", referenced);
        collectFromText("select description from requirement where description like '%/uploads/%'", referenced);
        collectFromText("select description from bug where description like '%/uploads/%'", referenced);
        collectFromText("select content from issue_comment where content like '%/uploads/%'", referenced);
        collectFromText("select content from announcement where content like '%/uploads/%'", referenced);

        return referenced;
    }

    /**
     * 扫描一列文本并抽出其中的图片文件名。
     * 表或列不存在时只记日志不中断——清理是运维动作，不能因为某个可选表缺失就整体失败。
     */
    private void collectFromText(String sql, Set<String> referenced) {
        List<String> values;
        try {
            values = jdbcTemplate.queryForList(sql, String.class);
        } catch (RuntimeException e) {
            log.debug("跳过引用扫描（表/列不可用）: {} → {}", sql, e.getMessage());
            return;
        }
        for (String text : values) {
            if (text == null || text.isEmpty()) {
                continue;
            }
            Matcher m = UPLOAD_URL.matcher(text);
            while (m.find()) {
                referenced.add(m.group(1));
            }
        }
    }
}
