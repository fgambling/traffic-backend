package com.traffic.salesman.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.traffic.salesman.entity.FollowJoinRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface FollowJoinRequestMapper extends BaseMapper<FollowJoinRequest> {

    /**
     * 查询我（作为被跟进方）收到的待处理联合跟进申请
     * salesmanId = 拥有 follow_id 对应跟进记录的业务员
     */
    @Select("SELECT r.id, r.follow_id, r.requester_id, r.created_at, " +
            "s.name AS requester_name, m.name AS merchant_name " +
            "FROM follow_join_request r " +
            "JOIN merchant_follow mf ON r.follow_id = mf.id " +
            "JOIN salesman s ON r.requester_id = s.id " +
            "JOIN merchant m ON mf.merchant_id = m.id " +
            "WHERE mf.salesman_id = #{salesmanId} AND r.status = 0 " +
            "ORDER BY r.created_at DESC")
    List<Map<String, Object>> findIncomingPending(@Param("salesmanId") Integer salesmanId);
}
