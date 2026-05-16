CREATE TABLE IF NOT EXISTS admin_user (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(64)  NOT NULL UNIQUE,
    name       VARCHAR(64)  NOT NULL DEFAULT '',
    password   VARCHAR(128) NOT NULL COMMENT 'BCrypt hash',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='管理员账号';

-- 初始账号 admin / admin123
INSERT IGNORE INTO admin_user (id, username, name, password)
VALUES (1, 'admin', '超级管理员',
  '$2a$10$7QbqLGKU3l4kqOCjXDOvQeKGmDQvPMFcFXH9rR6LJgX9WvD8yRtMW');
-- 上方 hash 对应明文 admin123，可在系统中重置
