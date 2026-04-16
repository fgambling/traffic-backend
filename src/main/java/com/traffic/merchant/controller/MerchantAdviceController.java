package com.traffic.merchant.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.traffic.advice.entity.AiAdvice;
import com.traffic.advice.mapper.AiAdviceMapper;
import com.traffic.common.BusinessException;
import com.traffic.common.ErrorCode;
import com.traffic.common.R;
import com.traffic.security.JwtPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商家建议接口
 */
@RestController
@RequestMapping("/api/merchant/advice")
@RequiredArgsConstructor
public class MerchantAdviceController {

    private final AiAdviceMapper aiAdviceMapper;

    /**
     * 分页查询建议列表
     * GET /api/merchant/advice/list?type=all&source=1&page=1&size=10
     *
     * @param type   建议类型（营销/排班/备货），不传=全部
     * @param source 来源（1=规则引擎 2=AI大模型），不传=全部
     * @param page   页码（默认1）
     * @param size   每页条数（默认10）
     */
    @GetMapping("/list")
    public R<PageResult<AiAdvice>> listAdvice(
            @AuthenticationPrincipal JwtPrincipal principal,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer source,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (!"merchant".equals(principal.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // type="all" 视同不过滤
        String adviceType = ("all".equalsIgnoreCase(type) || type == null) ? null : type;

        Page<AiAdvice> pageReq = new Page<>(page, size);
        var result = aiAdviceMapper.pageByMerchant(pageReq, principal.getMerchantId(),
                                                   adviceType, source);

        return R.ok(new PageResult<>(result.getRecords(), result.getTotal(), page, size));
    }

    /** 通用分页结果封装 */
    public record PageResult<T>(List<T> list, long total, int page, int size) {}
}
