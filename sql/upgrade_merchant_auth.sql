-- 为 merchant 表补充登录所需字段（password、is_lead）
-- 如已存在则忽略（IF NOT EXISTS）
USE traffic_db;

ALTER TABLE merchant
    ADD COLUMN IF NOT EXISTS password VARCHAR(128) NULL COMMENT '登录密码（BCrypt）' AFTER openid,
    ADD COLUMN IF NOT EXISTS is_lead  TINYINT      NOT NULL DEFAULT 0 COMMENT '线索商家: 1=业务员跟进线索 0=正式商家' AFTER password;
