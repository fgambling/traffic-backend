package com.traffic.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.traffic.admin.entity.SystemConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SystemConfigMapper extends BaseMapper<SystemConfig> {

    default String getValue(String key) {
        SystemConfig cfg = selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, key));
        return cfg != null ? cfg.getConfigValue() : null;
    }

    default void setValue(String key, String value) {
        SystemConfig cfg = selectOne(new LambdaQueryWrapper<SystemConfig>()
                .eq(SystemConfig::getConfigKey, key));
        if (cfg == null) {
            cfg = new SystemConfig();
            cfg.setConfigKey(key);
            cfg.setConfigValue(value);
            insert(cfg);
        } else {
            cfg.setConfigValue(value);
            updateById(cfg);
        }
    }
}
