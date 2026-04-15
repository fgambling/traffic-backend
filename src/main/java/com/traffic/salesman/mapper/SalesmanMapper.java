package com.traffic.salesman.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.traffic.salesman.entity.Salesman;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 业务员Mapper
 */
@Mapper
public interface SalesmanMapper extends BaseMapper<Salesman> {

    /**
     * 根据微信openid查询业务员
     */
    @Select("SELECT * FROM salesman WHERE openid = #{openid} AND status = 1 LIMIT 1")
    Salesman findByOpenid(String openid);
}
