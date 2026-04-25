-- =================================================================
-- v3 升级：traffic_fact 增加停留时长分桶字段
--
-- 背景：
--   商家端"停留时长分析"模块需要按 <5分钟 / 5-15分钟 / >15分钟 三档
--   做分布统计，需要在事实表中增加 3 个计数列。
--
-- 注意：
--   1. 该脚本可重复执行（使用 IF NOT EXISTS 判断），不会报错。
--   2. 升级后，历史数据这三列默认为 0；只有升级后新采集/聚合的
--      数据（经过 AttributeParser + DeviceServiceImpl.upsertTrafficFact）
--      才会写入真实分布。
--   3. 若需要回填历史数据的分布，需另外跑一次批处理（目前未提供）。
-- =================================================================

-- MySQL 5.7 不支持 ADD COLUMN IF NOT EXISTS，这里用存储过程实现兼容
DELIMITER $$

DROP PROCEDURE IF EXISTS _add_stay_bucket_columns $$
CREATE PROCEDURE _add_stay_bucket_columns()
BEGIN
    -- stay_under5min
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'traffic_fact'
          AND COLUMN_NAME  = 'stay_under5min'
    ) THEN
        ALTER TABLE traffic_fact
            ADD COLUMN stay_under5min INT NOT NULL DEFAULT 0
            COMMENT '停留 <5 分钟人数';
    END IF;

    -- stay_5to15min
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'traffic_fact'
          AND COLUMN_NAME  = 'stay_5to15min'
    ) THEN
        ALTER TABLE traffic_fact
            ADD COLUMN stay_5to15min INT NOT NULL DEFAULT 0
            COMMENT '停留 5-15 分钟人数';
    END IF;

    -- stay_over15min
    IF NOT EXISTS (
        SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = 'traffic_fact'
          AND COLUMN_NAME  = 'stay_over15min'
    ) THEN
        ALTER TABLE traffic_fact
            ADD COLUMN stay_over15min INT NOT NULL DEFAULT 0
            COMMENT '停留 >15 分钟人数';
    END IF;
END $$

DELIMITER ;

CALL _add_stay_bucket_columns();
DROP PROCEDURE _add_stay_bucket_columns;

-- 可选：给历史数据一个粗略的分桶估算（将所有 stay_count 归入中档，避免全零）
-- 如果你希望历史数据的"停留分布"有一个近似可视化，可取消下面注释：
-- UPDATE traffic_fact
-- SET stay_5to15min = stay_count
-- WHERE stay_under5min = 0
--   AND stay_5to15min  = 0
--   AND stay_over15min = 0
--   AND stay_count     > 0;
