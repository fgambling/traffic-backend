-- =====================================================================
-- upgrade_ai_module.sql  ·  AI 建议模块补丁
-- 新增字段：ai_advice.confidence / call_id，merchant_business_info.business_type
-- 执行：mysql -u root -p traffic_db < sql/upgrade_ai_module.sql
-- 可重复执行（字段已存在时忽略错误）
-- =====================================================================
USE traffic_db;

-- ai_advice 表：置信度字段（高/中/低）
ALTER TABLE ai_advice
    ADD COLUMN confidence VARCHAR(10) NULL COMMENT '置信度：高/中/低（AI大模型生成时填写）';

-- ai_advice 表：LLM 调用批次 ID（同一次调用的多条建议共享同一 UUID）
ALTER TABLE ai_advice
    ADD COLUMN call_id VARCHAR(36) NULL COMMENT 'LLM调用批次ID';

-- merchant_business_info 表：店铺业态
ALTER TABLE merchant_business_info
    ADD COLUMN business_type VARCHAR(30) NULL COMMENT '店铺业态（餐饮/零售/服务等）';

-- ai_advice 表：记录生成建议时实际使用的 LLM 模型（用于精确历史成本核算）
ALTER TABLE ai_advice
    ADD COLUMN model_used VARCHAR(50) NULL COMMENT '实际使用的LLM模型（source=2时记录）';
