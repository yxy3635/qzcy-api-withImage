package com.qzcy.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("mail_config")
public class MailConfig {
    private Long id;
    private String host;
    private Integer port;
    private String username;
    private String password;
    private String fromAddress;
    private Boolean sslEnabled;
    private Boolean starttlsEnabled;
    private Boolean enabled;
    private Boolean devReturnCode;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
