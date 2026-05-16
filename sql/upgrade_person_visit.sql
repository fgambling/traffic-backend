-- 记录每个商家见过的 personId，用于区分新客/回头客
USE traffic_db;

CREATE TABLE IF NOT EXISTS person_visit (
    id            BIGINT   NOT NULL AUTO_INCREMENT,
    merchant_id   INT      NOT NULL COMMENT '商家ID',
    person_id     VARCHAR(64) NOT NULL COMMENT '边缘设备行人唯一ID',
    first_seen_at DATETIME NOT NULL COMMENT '首次到访时间',
    last_seen_at  DATETIME NOT NULL COMMENT '最近到访时间',
    visit_count   INT      NOT NULL DEFAULT 1 COMMENT '累计到访次数',
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_person (merchant_id, person_id),
    INDEX idx_merchant (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='行人到访记录（用于新客/回头客判断）';
