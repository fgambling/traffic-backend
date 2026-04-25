package com.traffic.advice.dto;

import lombok.Data;

/**
 * 用户反馈请求
 */
@Data
public class FeedbackRequest {
    private Long id;
    /** 1=有用 2=无用 */
    private Integer feedback;
}
