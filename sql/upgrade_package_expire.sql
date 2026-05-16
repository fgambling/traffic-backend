-- 套餐有效期字段
-- 普通版不需要有效期，中级版/高级版由管理员设置到期日
USE traffic_db;

ALTER TABLE merchant
    ADD COLUMN package_expire_at DATE NULL COMMENT '套餐到期日（普通版为NULL表示永久）';
