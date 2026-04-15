package com.traffic.merchant.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.traffic.merchant.entity.Merchant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 商家Mapper
 */
@Mapper
public interface MerchantMapper extends BaseMapper<Merchant> {

    /**
     * 根据设备bindId查询商家
     */
    @Select("SELECT * FROM merchant WHERE bind_id = #{bindId} AND status = 1 LIMIT 1")
    Merchant findByBindId(String bindId);

    /**
     * 根据微信openid查询商家
     */
    @Select("SELECT * FROM merchant WHERE openid = #{openid} AND status = 1 LIMIT 1")
    Merchant findByOpenid(String openid);
}
