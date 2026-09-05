package com.qzcy.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("relay_channel_provider")
public class RelayChannelProvider {
    private Long id;
    private Long channelId;
    private String name;
    private String apiBaseUrl;
    private String apiKey;
    private String channelRule;
    private Integer priority;
    private Integer weight;
    private String status;
    private Boolean enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
