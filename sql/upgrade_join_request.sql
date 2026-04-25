-- =============================================
-- 联合跟进申请表
-- =============================================
USE traffic_db;

CREATE TABLE IF NOT EXISTS follow_join_request (
    id           INT      NOT NULL AUTO_INCREMENT,
    follow_id    INT      NOT NULL COMMENT '被申请联合跟进的 merchant_follow.id',
    requester_id INT      NOT NULL COMMENT '发起申请的业务员 ID',
    status       TINYINT  NOT NULL DEFAULT 0 COMMENT '0=待处理 1=已同意 2=已拒绝',
    created_at   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    INDEX idx_follow_id    (follow_id),
    INDEX idx_requester_id (requester_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='联合跟进申请';
