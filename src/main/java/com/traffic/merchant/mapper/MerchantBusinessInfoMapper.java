package com.traffic.merchant.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.traffic.merchant.entity.MerchantBusinessInfo;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MerchantBusinessInfoMapper extends BaseMapper<MerchantBusinessInfo> {

    default MerchantBusinessInfo findByMerchant(Integer merchantId) {
        return selectOne(new LambdaQueryWrapper<MerchantBusinessInfo>()
                .eq(MerchantBusinessInfo::getMerchantId, merchantId));
    }
}
