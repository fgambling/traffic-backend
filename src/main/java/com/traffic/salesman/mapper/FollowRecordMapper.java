package com.traffic.salesman.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.traffic.salesman.entity.FollowRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface FollowRecordMapper extends BaseMapper<FollowRecord> {

    /** 查询某条跟进的历史记录，最新在前，最多返回 30 条 */
    @Select("SELECT * FROM follow_record WHERE follow_id = #{followId} ORDER BY created_at DESC LIMIT 30")
    List<FollowRecord> findByFollowId(Integer followId);

    /** 查询同一商家所有跟进的历史记录（联合跟进共享），最新在前，最多 60 条 */
    @Select("SELECT fr.* FROM follow_record fr " +
            "JOIN merchant_follow mf ON fr.follow_id = mf.id " +
            "WHERE mf.merchant_id = #{merchantId} " +
            "ORDER BY fr.created_at DESC LIMIT 60")
    List<FollowRecord> findByMerchantId(Integer merchantId);
}
