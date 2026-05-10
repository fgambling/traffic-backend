package com.traffic.advice.util;

/**
 * LLM 相关公共常量
 */
public final class LlmConstants {

    private LlmConstants() {}

    /** 默认 Prompt 模板，管理员未自定义时使用 */
    public static final String DEFAULT_PROMPT_TEMPLATE =
        "你是一位零售门店经营顾问，擅长根据客流数据给出简洁、可落地的经营建议。\n\n" +
        "以下是门店的客流与商家信息：\n" +
        "{{data}}\n\n" +
        "请根据以上数据，输出 2~3 条经营建议。要求：\n" +
        "- 每条建议必须直接针对数据中的具体数字，避免泛泛而谈\n" +
        "- 若数据中包含店铺业态、菜单/商品、促销活动、目标客群、营业时段等自定义信息，需结合这些内容给出更有针对性的方案\n" +
        "- 内容 50~100 字，包含具体行动（谁、做什么、何时做）\n" +
        "- confidence：数据依据充分则填\"高\"，数据有限或推断成分多则填\"低\"\n\n" +
        "严格以如下 JSON 数组返回，不要任何额外文字：\n" +
        "[{\"type\":\"备货\",\"content\":\"具体建议内容\",\"confidence\":\"高\"},...]\n" +
        "type 只能是 备货、排班、营销、服务 之一。";
}
