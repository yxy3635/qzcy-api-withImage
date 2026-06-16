package com.qzcy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qzcy.backend.entity.RelayGroupModel;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RelayGroupModelMapper extends BaseMapper<RelayGroupModel> {
    @Select("""
            SELECT m.model
            FROM relay_group_model gm
            JOIN relay_model m ON m.id = gm.model_id
            WHERE gm.group_id = #{groupId}
              AND m.enabled = 1
            ORDER BY m.sort_order, m.id
            """)
    List<String> modelsForGroup(@Param("groupId") Long groupId);

    @Select("""
            SELECT COUNT(*)
            FROM relay_group_model gm
            WHERE gm.group_id = #{groupId}
              AND gm.model_id = #{modelId}
            """)
    Long countGroupModel(@Param("groupId") Long groupId, @Param("modelId") Long modelId);

    @Select("""
            SELECT COUNT(*)
            FROM relay_group_model gm
            WHERE gm.group_id = #{groupId}
            """)
    Long countModelsForGroup(@Param("groupId") Long groupId);

    @Select("""
            SELECT COUNT(*)
            FROM relay_group_model gm
            JOIN relay_model m ON m.id = gm.model_id
            WHERE gm.group_id = #{groupId}
              AND m.enabled = 1
            """)
    Long countEnabledModelsForGroup(@Param("groupId") Long groupId);
}
