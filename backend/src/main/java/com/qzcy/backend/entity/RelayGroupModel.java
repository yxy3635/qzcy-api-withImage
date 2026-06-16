package com.qzcy.backend.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("relay_group_model")
public class RelayGroupModel {
    private Long id;
    private Long groupId;
    private Long modelId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
