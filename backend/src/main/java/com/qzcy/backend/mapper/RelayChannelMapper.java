package com.qzcy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qzcy.backend.entity.RelayChannel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RelayChannelMapper extends BaseMapper<RelayChannel> {
    @Select("""
            SELECT c.*
            FROM relay_channel c
            JOIN relay_channel_model cm ON cm.channel_id = c.id
            WHERE c.enabled = 1
              AND c.status <> 'failed'
              AND c.api_key IS NOT NULL
              AND c.api_key <> ''
              AND c.api_base_url IS NOT NULL
              AND c.api_base_url <> ''
              AND cm.enabled = 1
              AND cm.model_id = #{modelId}
              AND (
                  c.group_names IS NULL
                  OR c.group_names = ''
                  OR FIND_IN_SET(#{groupCode}, REPLACE(c.group_names, ' ', '')) > 0
              )
            ORDER BY c.priority ASC, c.weight DESC, c.id ASC
            """)
    List<RelayChannel> selectDispatchCandidates(@Param("modelId") Long modelId, @Param("groupCode") String groupCode);
}
