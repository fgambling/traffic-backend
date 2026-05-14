-- =============================================
-- 业务员端功能升级脚本
-- 执行前请确保已运行 init.sql 和 upgrade_v2.sql
-- =============================================
USE traffic_db;

-- =============================================
-- 新增：跟进历史记录表
-- =============================================
CREATE TABLE IF NOT EXISTS follow_record (
    id         INT         NOT NULL AUTO_INCREMENT,
    follow_id  INT         NOT NULL COMMENT '关联 merchant_follow.id',
    content    VARCHAR(500)         COMMENT '跟进内容',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_follow_id (follow_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='跟进历史记录';

-- =============================================
-- 新增：业务员营销素材表
-- =============================================
CREATE TABLE IF NOT EXISTS salesman_material (
    id          INT         NOT NULL AUTO_INCREMENT,
    salesman_id INT         NOT NULL COMMENT '业务员ID',
    title       VARCHAR(100)         COMMENT '素材标题',
    type        VARCHAR(20)          COMMENT 'image / video / doc',
    url         VARCHAR(500)         COMMENT '文件URL',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_salesman_id (salesman_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务员营销素材';

-- =============================================
-- 修改：merchant_follow 增加凭证图片字段
-- =============================================
ALTER TABLE merchant_follow
    ADD COLUMN voucher_url VARCHAR(500) COMMENT '合作凭证图片URL' AFTER follow_record;
