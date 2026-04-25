-- =============================================
-- 后台管理功能升级脚本
-- 执行前请确保已运行 init.sql、upgrade_v2.sql、upgrade_salesman.sql
-- =============================================
USE traffic_db;

-- =============================================
-- 佣金规则表
-- =============================================
CREATE TABLE IF NOT EXISTS commission_rule (
    id          INT         NOT NULL AUTO_INCREMENT,
    package_type INT        NOT NULL COMMENT '套餐类型 1基础版 2高级版',
    name        VARCHAR(100)         COMMENT '规则名称',
    rate        DECIMAL(5,4)         COMMENT '佣金比例（与 fixed_amount 二选一）',
    fixed_amount DECIMAL(10,2)       COMMENT '固定佣金金额',
    description VARCHAR(200)         COMMENT '备注说明',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_pkg (package_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='佣金规则';

-- =============================================
-- BUG / 异常日志表
-- =============================================
CREATE TABLE IF NOT EXISTS bug_log (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    level       VARCHAR(10)          COMMENT 'error / warn / info',
    module      VARCHAR(50)          COMMENT '模块',
    message     VARCHAR(500)         COMMENT '错误摘要',
    stack_trace TEXT                 COMMENT '堆栈信息',
    user_id     INT                  COMMENT '触发用户ID',
    user_role   VARCHAR(20)          COMMENT '用户角色',
    resolved    TINYINT NOT NULL DEFAULT 0 COMMENT '0未处理 1已解决',
    created_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_level (level),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='BUG/异常日志';

-- =============================================
-- 系统配置表（Key-Value）
-- =============================================
CREATE TABLE IF NOT EXISTS system_config (
    id          INT         NOT NULL AUTO_INCREMENT,
    config_key  VARCHAR(64) NOT NULL UNIQUE COMMENT '配置键',
    config_value TEXT                COMMENT '配置值',
    description VARCHAR(200)         COMMENT '说明',
    updated_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置（KV存储）';

-- 初始化 AI 配置默认值
INSERT IGNORE INTO system_config (config_key, config_value, description) VALUES
  ('llm.model',          'gpt-4o-mini',  'LLM 模型名称'),
  ('llm.api_key',        '',             'LLM API 密钥'),
  ('llm.prompt_template','你是一位专业的商业顾问...', '系统 Prompt 模板'),
  ('llm.daily_limit',    '200',          '每日 LLM 调用次数上限');

-- =============================================
-- ai_advice 增加管理员审核字段
-- =============================================
ALTER TABLE ai_advice
    ADD COLUMN IF NOT EXISTS review_status TINYINT NOT NULL DEFAULT 0 COMMENT '0未审核 1已采纳 2标记待优化' AFTER feedback,
    ADD COLUMN IF NOT EXISTS admin_note VARCHAR(200) COMMENT '管理员备注' AFTER review_status;
