-- 套餐申请表
CREATE TABLE IF NOT EXISTS package_application (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    merchant_id INT         NOT NULL COMMENT '申请商家',
    target_pkg  TINYINT     NOT NULL COMMENT '申请套餐：2=中级版 3=高级版',
    remark      VARCHAR(500)         COMMENT '备注（选填）',
    image_url   VARCHAR(500)         COMMENT '凭证图片（选填）',
    status      TINYINT     NOT NULL DEFAULT 0 COMMENT '0=待处理 1=已通过 2=已拒绝',
    admin_note  VARCHAR(500)         COMMENT '管理员备注',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_merchant (merchant_id),
    INDEX idx_status   (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家套餐升级申请';
