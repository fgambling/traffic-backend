package com.traffic.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.traffic.admin.entity.BugLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Map;
import java.util.List;

@Mapper
public interface BugLogMapper extends BaseMapper<BugLog> {

    @Select("SELECT level, COUNT(*) AS cnt FROM bug_log GROUP BY level")
    List<Map<String, Object>> countByLevel();
}
