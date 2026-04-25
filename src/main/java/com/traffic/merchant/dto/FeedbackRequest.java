package com.traffic.merchant.dto;

import lombok.Data;

/**
 * 建议反馈请求体
 * POST /api/merchant/advice/feedback
 */
@Data
public class FeedbackRequest {

    /** 建议ID */
    private Long id;

    /** 反馈值：1=有用，2=无用 */
    private Integer feedback;
}
