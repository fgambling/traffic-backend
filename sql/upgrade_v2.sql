-- =============================================
-- V2 升级脚本：规则引擎功能
-- 执行前请确保已运行 init.sql
-- =============================================
USE traffic_db;

-- =============================================
-- 新增：新老客识别表
-- =============================================
CREATE TABLE IF NOT EXISTS customer_visit (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    merchant_id    INT         NOT NULL COMMENT '商家ID',
    person_hash    VARCHAR(64) NOT NULL COMMENT 'SHA256(personId + merchantId)，防碰撞',
    first_visit_date DATE      NOT NULL COMMENT '首次到访日期',
    last_visit_date  DATE      NOT NULL COMMENT '最近到访日期',
    visit_count    INT         NOT NULL DEFAULT 1 COMMENT '累计到访天数',
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_person (merchant_id, person_hash),
    INDEX idx_merchant_date (merchant_id, last_visit_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='新老客识别表';

-- =============================================
-- 新增：商家自定义配置表
-- =============================================
CREATE TABLE IF NOT EXISTS merchant_config (
    id           INT         NOT NULL AUTO_INCREMENT,
    merchant_id  INT         NOT NULL COMMENT '商家ID',
    config_key   VARCHAR(64) NOT NULL COMMENT '配置键，如 builtin_rule_config / custom_rules / menu_categories',
    config_value JSON        NOT NULL COMMENT '配置值（JSON）',
    updated_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_config (merchant_id, config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家配置表（规则、菜单等）';

-- =============================================
-- 修改：ai_advice 表增加规则引擎字段
-- =============================================
ALTER TABLE ai_advice
    ADD COLUMN trigger_rule_id VARCHAR(32)  COMMENT '触发规则ID（如 R001/custom_001）' AFTER merchant_id,
    ADD COLUMN source          TINYINT      NOT NULL DEFAULT 1 COMMENT '来源: 1规则引擎 2AI大模型' AFTER trigger_rule_id,
    ADD COLUMN data_snapshot   JSON         COMMENT '触发时的关键数据快照' AFTER content;

-- =============================================
-- 修改：traffic_fact 表增加新老客字段
-- =============================================
ALTER TABLE traffic_fact
    ADD COLUMN new_customer_count       INT NOT NULL DEFAULT 0 COMMENT '新客人数',
    ADD COLUMN returning_customer_count INT NOT NULL DEFAULT 0 COMMENT '老客人数';
