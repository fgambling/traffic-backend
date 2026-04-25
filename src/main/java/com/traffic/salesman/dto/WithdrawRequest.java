package com.traffic.salesman.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class WithdrawRequest {
    private BigDecimal amount;
    /** 1微信 2银行卡 */
    private Integer way;
    private String account;
}
