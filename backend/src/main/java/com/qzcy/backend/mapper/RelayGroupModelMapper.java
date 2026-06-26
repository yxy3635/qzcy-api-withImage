package com.qzcy.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.qzcy.backend.entity.RelayModel;
import com.qzcy.backend.entity.RelayGroupModel;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface RelayGroupModelMapper extends BaseMapper<RelayGroupModel> {
    @Select("""
            SELECT COALESCE(NULLIF(m.display_name, ''), m.model) AS model
            FROM relay_group_model gm
            JOIN relay_model m ON m.id = gm.model_id
            WHERE gm.group_id = #{groupId}
              AND m.enabled = 1
            ORDER BY m.sort_order, m.id
            """)
    List<String> modelsForGroup(@Param("groupId") Long groupId);

    @Select("""
            SELECT m.*
            FROM relay_group_model gm
            JOIN relay_model m ON m.id = gm.model_id
            WHERE gm.group_id = #{groupId}
              AND (m.display_name = #{model} OR m.model = #{model})
              AND m.enabled = 1
            ORDER BY m.sort_order, m.id
            LIMIT 1
            """)
    RelayModel selectEnabledModelForGroup(@Param("groupId") Long groupId, @Param("model") String model);

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
            JOIN relay_model m ON m.id = gm.model_id
            WHERE gm.group_id = #{groupId}
              AND (m.display_name = #{model} OR m.model = #{model})
            """)
    Long countGroupModelName(@Param("groupId") Long groupId, @Param("model") String model);

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

    @Select("""
            SELECT gm.model_id
            FROM relay_group_model gm
            JOIN relay_model m ON m.id = gm.model_id
            WHERE gm.group_id = #{groupId}
            ORDER BY m.sort_order, m.id
            """)
    List<Long> modelIdsForGroup(@Param("groupId") Long groupId);

    @Delete("""
            DELETE FROM relay_group_model
            WHERE group_id = #{groupId}
            """)
    void deleteByGroupId(@Param("groupId") Long groupId);
}
