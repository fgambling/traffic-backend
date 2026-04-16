package com.traffic.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.traffic.merchant.entity.MerchantConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 商家配置Mapper
 */
@Mapper
public interface MerchantConfigMapper extends BaseMapper<MerchantConfig> {

    /**
     * 根据商家ID + 配置Key 查询单条配置
     */
    @Select("SELECT * FROM merchant_config " +
            "WHERE merchant_id = #{merchantId} AND config_key = #{configKey} LIMIT 1")
    MerchantConfig findByKey(@Param("merchantId") Integer merchantId,
                             @Param("configKey") String configKey);
}
