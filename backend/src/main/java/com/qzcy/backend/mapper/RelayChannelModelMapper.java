package com.qzcy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qzcy.backend.dto.RelayChannelModelDto;
import com.qzcy.backend.entity.RelayChannelModel;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RelayChannelModelMapper extends BaseMapper<RelayChannelModel> {
    @Select("""
            SELECT cm.id,
                   cm.channel_id,
                   cm.model_id,
                   m.model,
                   m.display_name,
                   m.model_type,
                   cm.upstream_model,
                   cm.enabled
            FROM relay_channel_model cm
            JOIN relay_model m ON m.id = cm.model_id
            WHERE cm.channel_id = #{channelId}
            ORDER BY m.sort_order, m.id
            """)
    List<RelayChannelModelDto> modelsForChannel(@Param("channelId") Long channelId);

    @Select("""
            SELECT cm.*
            FROM relay_channel_model cm
            JOIN relay_model m ON m.id = cm.model_id
            WHERE cm.channel_id = #{channelId}
              AND m.id = #{modelId}
            LIMIT 1
            """)
    RelayChannelModel selectByChannelAndModel(@Param("channelId") Long channelId, @Param("modelId") Long modelId);

    @Select("""
            SELECT cm.*
            FROM relay_channel_model cm
            JOIN relay_model m ON m.id = cm.model_id
            WHERE cm.channel_id = #{channelId}
              AND m.model = #{model}
            LIMIT 1
            """)
    RelayChannelModel selectByChannelAndModelName(@Param("channelId") Long channelId, @Param("model") String model);

    @Select("""
            SELECT COUNT(*)
            FROM relay_channel_model cm
            JOIN relay_model m ON m.id = cm.model_id
            WHERE cm.channel_id = #{channelId}
              AND cm.enabled = 1
              AND m.enabled = 1
            """)
    Long countEnabledModelsForChannel(@Param("channelId") Long channelId);

    @Select("""
            SELECT item.public_model
            FROM (
                SELECT COALESCE(NULLIF(m.display_name, ''), m.model) AS public_model, MIN(m.sort_order) AS sort_order, MIN(m.id) AS id
                FROM relay_channel_model cm
                JOIN relay_model m ON m.id = cm.model_id
                JOIN relay_channel c ON c.id = cm.channel_id
                WHERE cm.enabled = 1
                  AND m.enabled = 1
                  AND c.enabled = 1
                  AND c.status <> 'failed'
                GROUP BY COALESCE(NULLIF(m.display_name, ''), m.model)
            ) item
            ORDER BY item.sort_order, item.id
            """)
    List<String> enabledModelNames();

    @Select("""
            SELECT item.public_model
            FROM (
                SELECT COALESCE(NULLIF(m.display_name, ''), m.model) AS public_model, MIN(m.sort_order) AS sort_order, MIN(m.id) AS id
                FROM relay_channel_model cm
                JOIN relay_model m ON m.id = cm.model_id
                JOIN relay_channel c ON c.id = cm.channel_id
                WHERE cm.enabled = 1
                  AND m.enabled = 1
                  AND c.enabled = 1
                  AND c.status <> 'failed'
                  AND (
                      c.group_names IS NULL
                      OR c.group_names = ''
                      OR FIND_IN_SET(#{groupCode}, REPLACE(c.group_names, ' ', '')) > 0
                  )
                GROUP BY COALESCE(NULLIF(m.display_name, ''), m.model)
            ) item
            ORDER BY item.sort_order, item.id
            """)
    List<String> enabledModelNamesForGroup(@Param("groupCode") String groupCode);

    @Delete("""
            DELETE FROM relay_channel_model
            WHERE channel_id = #{channelId}
            """)
    void deleteByChannelId(@Param("channelId") Long channelId);
}
