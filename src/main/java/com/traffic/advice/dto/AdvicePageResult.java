package com.traffic.advice.dto;

import com.traffic.advice.entity.AiAdvice;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 建议列表分页响应
 */
@Data
@AllArgsConstructor
public class AdvicePageResult {
  private List<AiAdvice> list;
  private long total;
}
