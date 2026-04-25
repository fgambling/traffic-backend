package com.traffic.salesman.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.traffic.salesman.dto.FollowVO;
import com.traffic.salesman.entity.MerchantFollow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.ResultType;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface MerchantFollowMapper extends BaseMapper<MerchantFollow> {

    /**
     * 查询业务员的所有跟进记录（含商家信息）
     */
    @Select("SELECT mf.id, mf.merchant_id, mf.status, mf.follow_record, mf.voucher_url, mf.commission, mf.updated_at, " +
            "m.name AS merchant_name, m.contact_person, m.contact_phone, m.license_no, m.address, " +
            "(SELECT COUNT(*) FROM merchant_follow mf2 WHERE mf2.merchant_id = mf.merchant_id " +
            " AND mf2.salesman_id != #{salesmanId} AND mf2.status IN (1,2,4,5)) AS co_follow_count " +
            "FROM merchant_follow mf " +
            "LEFT JOIN merchant m ON mf.merchant_id = m.id " +
            "WHERE mf.salesman_id = #{salesmanId} " +
            "ORDER BY mf.updated_at DESC")
    List<FollowVO> findBySalesmanId(@Param("salesmanId") Integer salesmanId);

    /**
     * 查询跟进同一商家的其他业务员姓名列表
     */
    @Select("SELECT s.name FROM merchant_follow mf " +
            "JOIN salesman s ON mf.salesman_id = s.id " +
            "WHERE mf.merchant_id = #{merchantId} AND mf.salesman_id != #{excludeSalesmanId}")
    List<String> findCoSalesmenNames(Integer merchantId, Integer excludeSalesmanId);

    /** 本月新增跟进商家数 */
    @Select("SELECT COUNT(*) FROM merchant_follow " +
            "WHERE salesman_id = #{salesmanId} " +
            "AND YEAR(created_at) = YEAR(NOW()) AND MONTH(created_at) = MONTH(NOW())")
    int countMonthNew(Integer salesmanId);

    /** 分页查询已合作商家（status=2） */
    @ResultType(FollowVO.class)
    @Select("SELECT mf.id, mf.merchant_id, mf.status, mf.follow_record, mf.voucher_url, " +
            "mf.commission, mf.updated_at, mf.cooperation_time, " +
            "m.name AS merchant_name, m.contact_person, m.contact_phone, m.license_no, m.address " +
            "FROM merchant_follow mf " +
            "LEFT JOIN merchant m ON mf.merchant_id = m.id " +
            "WHERE mf.salesman_id = #{salesmanId} AND mf.status = 2 " +
            "ORDER BY mf.cooperation_time DESC")
    IPage<FollowVO> findSignedPage(IPage<FollowVO> page, @Param("salesmanId") Integer salesmanId);

    /** 查询所有已合作商家（用于 Excel 导出，不分页） */
    @Select("SELECT mf.id, mf.merchant_id, mf.status, mf.follow_record, mf.voucher_url, " +
            "mf.commission, mf.updated_at, mf.cooperation_time, " +
            "m.name AS merchant_name, m.contact_person, m.contact_phone, m.license_no, m.address " +
            "FROM merchant_follow mf " +
            "LEFT JOIN merchant m ON mf.merchant_id = m.id " +
            "WHERE mf.salesman_id = #{salesmanId} AND mf.status = 2 " +
            "ORDER BY mf.cooperation_time DESC")
    List<FollowVO> findAllSigned(Integer salesmanId);

    /**
     * 近6个月业绩走势（按自然月分组）
     * 返回字段：period(yyyy-MM), signCount, commission
     */
    @Select("SELECT DATE_FORMAT(cooperation_time, '%Y-%m') AS period, " +
            "COUNT(*) AS signCount, COALESCE(SUM(commission), 0) AS commission " +
            "FROM merchant_follow " +
            "WHERE salesman_id = #{salesmanId} AND status = 2 " +
            "AND cooperation_time >= DATE_SUB(NOW(), INTERVAL 6 MONTH) " +
            "GROUP BY period ORDER BY period ASC")
    List<Map<String, Object>> trendByMonth(Integer salesmanId);

    /**
     * 近4个季度业绩走势
     * 返回字段：period(yyyyQn), signCount, commission
     */
    @Select("SELECT CONCAT(YEAR(cooperation_time), 'Q', QUARTER(cooperation_time)) AS period, " +
            "COUNT(*) AS signCount, COALESCE(SUM(commission), 0) AS commission " +
            "FROM merchant_follow " +
            "WHERE salesman_id = #{salesmanId} AND status = 2 " +
            "AND cooperation_time >= DATE_SUB(NOW(), INTERVAL 12 MONTH) " +
            "GROUP BY period ORDER BY period ASC")
    List<Map<String, Object>> trendByQuarter(Integer salesmanId);

    /** 本月签约数（status=2 且 cooperation_time 在本月） */
    @Select("SELECT COUNT(*) FROM merchant_follow " +
            "WHERE salesman_id = #{salesmanId} AND status = 2 " +
            "AND YEAR(cooperation_time) = YEAR(NOW()) " +
            "AND MONTH(cooperation_time) = MONTH(NOW())")
    int countMonthSign(Integer salesmanId);

    /** 按状态统计数量 */
    @Select("SELECT COUNT(*) FROM merchant_follow WHERE salesman_id = #{salesmanId} AND status = #{status}")
    int countByStatus(Integer salesmanId, Integer status);

    /** 管理员查询待审批合作（status=4），含业务员和商家信息 */
    @ResultType(com.traffic.salesman.dto.FollowVO.class)
    @Select("SELECT mf.id, mf.merchant_id, mf.status, mf.follow_record, mf.voucher_url, " +
            "mf.commission, mf.updated_at, mf.cooperation_time, " +
            "m.name AS merchant_name, m.contact_person, m.contact_phone, m.license_no, m.address, " +
            "s.name AS salesman_name, s.phone AS salesman_phone " +
            "FROM merchant_follow mf " +
            "LEFT JOIN merchant m ON mf.merchant_id = m.id " +
            "LEFT JOIN salesman s ON mf.salesman_id = s.id " +
            "WHERE mf.status = 4 " +
            "ORDER BY mf.updated_at DESC")
    IPage<java.util.Map<String, Object>> findPendingPage(IPage<?> page);

    /**
     * 管理员查询所有跟进记录（可按 status 过滤），含业务员和商家信息
     * status=null 时返回全部
     */
    @Select("<script>" +
            "SELECT mf.id, mf.merchant_id, mf.status, mf.follow_record, mf.voucher_url, " +
            "mf.commission, mf.updated_at, mf.cooperation_time, " +
            "m.name AS merchant_name, m.contact_person, m.contact_phone, m.license_no, m.address, " +
            "s.name AS salesman_name, s.phone AS salesman_phone " +
            "FROM merchant_follow mf " +
            "LEFT JOIN merchant m ON mf.merchant_id = m.id " +
            "LEFT JOIN salesman s ON mf.salesman_id = s.id " +
            "<where>" +
            "<if test='status != null'>mf.status = #{status}</if>" +
            "<if test='salesmanName != null and salesmanName != \"\"'>" +
            " AND s.name LIKE CONCAT('%', #{salesmanName}, '%')" +
            "</if>" +
            "<if test='merchantName != null and merchantName != \"\"'>" +
            " AND m.name LIKE CONCAT('%', #{merchantName}, '%')" +
            "</if>" +
            "</where>" +
            "ORDER BY mf.updated_at DESC" +
            "</script>")
    IPage<java.util.Map<String, Object>> findAllFollowsPage(
            IPage<?> page,
            @Param("status") Integer status,
            @Param("salesmanName") String salesmanName,
            @Param("merchantName") String merchantName);
}
