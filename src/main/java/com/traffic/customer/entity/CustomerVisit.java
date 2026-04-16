package com.traffic.customer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDate;

/**
 * 新老客识别实体
 * 对应 customer_visit 表，以 SHA256(personId+merchantId) 去重
 */
@Data
@Accessors(chain = true)
@TableName("customer_visit")
public class CustomerVisit {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer merchantId;

    /** SHA256(personId + merchantId)，用于跨设备去重 */
    private String personHash;

    /** 首次到访日期 */
    private LocalDate firstVisitDate;

    /** 最近到访日期 */
    private LocalDate lastVisitDate;

    /** 累计到访天数 */
    private Integer visitCount;
}
