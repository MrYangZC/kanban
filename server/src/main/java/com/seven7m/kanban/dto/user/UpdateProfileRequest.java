package com.seven7m.kanban.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 当前用户修改个人资料请求（不允许改 username/enabled）。
 * 约束与 sys_user 列宽一致（display_name varchar(100)、email varchar(200)、avatar_url varchar(500)）。
 */
@Data
public class UpdateProfileRequest {

    @Size(max = 100, message = "显示名长度不能超过 100")
    private String displayName;

    @Email(message = "邮箱格式不正确")
    @Size(max = 200, message = "邮箱长度不能超过 200")
    private String email;

    @Size(max = 500, message = "头像地址长度不能超过 500")
    private String avatarUrl;
}
