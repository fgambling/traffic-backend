-- 为 follow_record 表新增 type 和 image_url 字段
ALTER TABLE follow_record
    ADD COLUMN type      VARCHAR(20)  NOT NULL DEFAULT 'note'  COMMENT '记录类型: note=文字记录, status=状态变更',
    ADD COLUMN image_url VARCHAR(500) NULL                     COMMENT '图片附件 URL';
