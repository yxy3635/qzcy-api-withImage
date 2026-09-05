package com.qzcy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qzcy.backend.entity.RelayChannelProvider;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RelayChannelProviderMapper extends BaseMapper<RelayChannelProvider> {
    @Select("SELECT * FROM relay_channel_provider WHERE channel_id = #{channelId} ORDER BY priority ASC, weight DESC, id ASC")
    List<RelayChannelProvider> selectByChannelId(@Param("channelId") Long channelId);
}
