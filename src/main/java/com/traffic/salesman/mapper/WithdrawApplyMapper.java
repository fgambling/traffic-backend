package com.traffic.salesman.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.salesman.entity.WithdrawApply;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface WithdrawApplyMapper extends BaseMapper<WithdrawApply> {

    @Select("SELECT * FROM withdraw_apply WHERE salesman_id = #{salesmanId} ORDER BY created_at DESC")
    List<WithdrawApply> findBySalesmanId(Integer salesmanId);

    /** 分页查询提现记录 */
    @Select("SELECT * FROM withdraw_apply WHERE salesman_id = #{salesmanId} ORDER BY created_at DESC")
    IPage<WithdrawApply> findPageBySalesmanId(Page<WithdrawApply> page, @Param("salesmanId") Integer salesmanId);

    /** 本月提现总额（仅计算未驳回的申请：status 0审核中 或 1已打款） */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM withdraw_apply " +
            "WHERE salesman_id = #{salesmanId} AND status != 2 " +
            "AND YEAR(created_at) = YEAR(NOW()) AND MONTH(created_at) = MONTH(NOW())")
    BigDecimal sumMonthWithdraw(Integer salesmanId);

    /** 待审核金额（冻结余额） */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM withdraw_apply " +
            "WHERE salesman_id = #{salesmanId} AND status = 0")
    BigDecimal sumPendingWithdraw(Integer salesmanId);

    /** 累计已打款金额 */
    @Select("SELECT COALESCE(SUM(amount), 0) FROM withdraw_apply " +
            "WHERE salesman_id = #{salesmanId} AND status = 1")
    BigDecimal sumApprovedWithdraw(Integer salesmanId);
}
