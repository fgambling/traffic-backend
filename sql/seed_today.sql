-- =====================================================================
-- seed_today.sql
-- 为 merchant_id=1 插入今日和昨日测试数据（可重复执行）
-- 今日：5分钟间隔数据（支持分钟级客流曲线）
-- 昨日：小时级数据（支持 deltaPercent 昨日对比）
-- 执行：mysql -u root -p traffic_db < sql/seed_today.sql
-- =====================================================================
USE traffic_db;

-- 清理今日和昨日旧数据
DELETE FROM traffic_fact
WHERE merchant_id = 1
  AND DATE(time_bucket) IN (CURDATE(), DATE_SUB(CURDATE(), INTERVAL 1 DAY));

-- =====================================================================
-- 生成存储过程
-- =====================================================================
DELIMITER //
DROP PROCEDURE IF EXISTS seed_today_data //

CREATE PROCEDURE seed_today_data()
BEGIN
    DECLARE d    INT DEFAULT 0;     -- 0=今天, 1=昨天
    DECLARE h    INT;               -- 小时 9-21
    DECLARE m    INT;               -- 分钟（今天用5分钟间隔）
    DECLARE e    INT;               -- enter_count (该时间段)
    DECLARE f    INT;               -- gender_female
    DECLARE a1   INT;               -- age_under18
    DECLARE a2   INT;               -- age_18_60
    DECLARE a3   INT;               -- age_over60
    DECLARE nc   INT;               -- new_customer
    DECLARE rc   INT;               -- returning_customer
    DECLARE base INT;               -- 小时基准进店人数
    DECLARE wf   DECIMAL(4,2);      -- 周末加成
    DECLARE ts   DATETIME;          -- time_bucket
    DECLARE r5   DECIMAL(5,3);      -- stay_under5min 比例
    DECLARE r15  DECIMAL(5,3);      -- stay_over15min 比例
    DECLARE u5   INT;               -- stay_under5min 人数
    DECLARE o15  INT;               -- stay_over15min 人数

    WHILE d <= 1 DO

        -- 周末（日=1,六=7）客流 × 1.35
        SET wf = IF(
            DAYOFWEEK(DATE_SUB(CURDATE(), INTERVAL d DAY)) IN (1, 7),
            1.35, 1.00
        );

        SET h = 9;
        WHILE h <= 21 DO

            -- 奶茶/轻餐饮小时基准
            SET base = CASE h
                WHEN  9 THEN  8
                WHEN 10 THEN 14
                WHEN 11 THEN 32
                WHEN 12 THEN 55
                WHEN 13 THEN 42
                WHEN 14 THEN 18
                WHEN 15 THEN 13
                WHEN 16 THEN 16
                WHEN 17 THEN 35
                WHEN 18 THEN 62
                WHEN 19 THEN 52
                WHEN 20 THEN 38
                WHEN 21 THEN 14
                ELSE 0
            END;

            -- 停留分布比例：高峰短停多（under5↑），清闲长停多（over15↑）
            SET r5 = CASE h
                WHEN 12 THEN 0.28 WHEN 13 THEN 0.27 WHEN 18 THEN 0.26 WHEN 19 THEN 0.25
                WHEN 11 THEN 0.20 WHEN 17 THEN 0.19 WHEN 20 THEN 0.18
                WHEN  9 THEN 0.12 WHEN 10 THEN 0.13 WHEN 14 THEN 0.14 WHEN 16 THEN 0.13
                WHEN 15 THEN 0.10 WHEN 21 THEN 0.11
                ELSE 0.17 END;
            SET r15 = CASE h
                WHEN 12 THEN 0.05 WHEN 13 THEN 0.05 WHEN 18 THEN 0.06 WHEN 19 THEN 0.06
                WHEN 11 THEN 0.09 WHEN 17 THEN 0.09 WHEN 20 THEN 0.10
                WHEN  9 THEN 0.22 WHEN 10 THEN 0.20 WHEN 14 THEN 0.18 WHEN 16 THEN 0.17
                WHEN 15 THEN 0.25 WHEN 21 THEN 0.23
                ELSE 0.12 END;

            IF d = 0 THEN
                -- ── 今日：每 5 分钟一条记录，客流随机分散在各分钟 ────────
                -- 只插入到当前小时（避免未来时段出现非零数据）
                IF h <= HOUR(NOW()) THEN
                    SET m = 0;
                    WHILE m < 60 DO
                        -- 该5分钟的进店量 ≈ 小时量/12，加±30%随机波动
                        SET e = GREATEST(0, ROUND(
                            (base * wf / 12.0)
                            * (0.70 + (h + m + 3) MOD 7 * 0.09)
                        ));

                        -- 当前整小时只写到当前分钟
                        IF h < HOUR(NOW()) OR m <= MINUTE(NOW()) THEN
                            SET f   = ROUND(e * (0.54 + (h MOD 4) * 0.015));
                            SET a1  = ROUND(e * 0.08);
                            SET a3  = ROUND(e * 0.10);
                            SET a2  = GREATEST(0, e - a1 - a3);
                            SET nc  = ROUND(e * 0.33);
                            SET rc  = e - nc;
                            SET u5  = ROUND(e * r5);
                            SET o15 = ROUND(e * r15);
                            SET ts  = TIMESTAMP(CURDATE()) + INTERVAL h HOUR + INTERVAL m MINUTE;

                            INSERT INTO traffic_fact (
                                merchant_id, device_id, time_bucket,
                                enter_count, pass_count,
                                gender_male, gender_female,
                                age_under18, age_18_60, age_over60,
                                accessory_glasses, accessory_hat, accessory_boots,
                                bag_handbag, bag_shoulder, bag_backpack, hold_item,
                                upper_short, upper_long, upper_coat,
                                upper_style_stripe, upper_style_logo,
                                upper_style_plaid, upper_style_splice,
                                lower_trousers, lower_shorts, lower_skirt,
                                lower_style_stripe, lower_style_pattern,
                                total_stay_seconds, stay_count,
                                stay_under5min, stay_5to15min, stay_over15min,
                                new_customer_count, returning_customer_count
                            ) VALUES (
                                1, 'cam-0', ts,
                                e,                   ROUND(e * 0.65),
                                GREATEST(0, e - f),  f,
                                a1, a2, a3,
                                ROUND(e * 0.28), ROUND(e * 0.18), ROUND(e * 0.11),
                                ROUND(e * 0.20), ROUND(e * 0.22), ROUND(e * 0.32), ROUND(e * 0.15),
                                ROUND(e * 0.25), ROUND(e * 0.60), ROUND(e * 0.15),
                                ROUND(e * 0.12), ROUND(e * 0.18),
                                ROUND(e * 0.08), ROUND(e * 0.05),
                                ROUND(e * 0.65), ROUND(e * 0.15), ROUND(e * 0.20),
                                ROUND(e * 0.10), ROUND(e * 0.12),
                                -- 停留时长：高峰短、空闲长，420~660 秒（7~11 分钟）
                                -- (h*12 + m/5) MOD 7 * 18 使同一小时内各5分钟槽的值不同
                                ROUND(e * (420 + CASE h
                                    WHEN 12 THEN  0  WHEN 13 THEN  0  WHEN 18 THEN  0  WHEN 19 THEN  0
                                    WHEN 11 THEN 60  WHEN 17 THEN 60  WHEN 20 THEN 60
                                    WHEN  9 THEN 180 WHEN 10 THEN 150 WHEN 14 THEN 120
                                    WHEN 15 THEN 240 WHEN 16 THEN 120 WHEN 21 THEN 180
                                    ELSE 90 END
                                    + (h * 12 + m / 5) MOD 7 * 18)),
                                GREATEST(0, e),
                                u5, GREATEST(0, e - u5 - o15), o15,
                                nc, rc
                            ) ON DUPLICATE KEY UPDATE
                                enter_count        = VALUES(enter_count),
                                pass_count         = VALUES(pass_count),
                                total_stay_seconds = VALUES(total_stay_seconds),
                                stay_count         = VALUES(stay_count),
                                stay_under5min     = VALUES(stay_under5min),
                                stay_5to15min      = VALUES(stay_5to15min),
                                stay_over15min     = VALUES(stay_over15min);
                        END IF;

                        SET m = m + 5;
                    END WHILE;
                END IF;

            ELSE
                -- ── 昨日：每小时一条记录（用于 deltaPercent 对比） ────────
                SET e   = GREATEST(1, ROUND(base * wf * (0.88 + h MOD 5 * 0.06)));
                SET f   = ROUND(e * (0.54 + (h MOD 4) * 0.015));
                SET a1  = ROUND(e * 0.08);
                SET a3  = ROUND(e * 0.10);
                SET a2  = GREATEST(0, e - a1 - a3);
                SET nc  = ROUND(e * 0.32);
                SET rc  = e - nc;
                SET u5  = ROUND(e * r5);
                SET o15 = ROUND(e * r15);
                SET ts  = TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY)) + INTERVAL h HOUR;

                INSERT INTO traffic_fact (
                    merchant_id, device_id, time_bucket,
                    enter_count, pass_count,
                    gender_male, gender_female,
                    age_under18, age_18_60, age_over60,
                    accessory_glasses, accessory_hat, accessory_boots,
                    bag_handbag, bag_shoulder, bag_backpack, hold_item,
                    upper_short, upper_long, upper_coat,
                    upper_style_stripe, upper_style_logo,
                    upper_style_plaid, upper_style_splice,
                    lower_trousers, lower_shorts, lower_skirt,
                    lower_style_stripe, lower_style_pattern,
                    total_stay_seconds, stay_count,
                    stay_under5min, stay_5to15min, stay_over15min,
                    new_customer_count, returning_customer_count
                ) VALUES (
                    1, 'cam-0', ts,
                    e,                   ROUND(e * 0.65),
                    GREATEST(0, e - f),  f,
                    a1, a2, a3,
                    ROUND(e * 0.28), ROUND(e * 0.18), ROUND(e * 0.11),
                    ROUND(e * 0.20), ROUND(e * 0.22), ROUND(e * 0.32), ROUND(e * 0.15),
                    ROUND(e * 0.25), ROUND(e * 0.60), ROUND(e * 0.15),
                    ROUND(e * 0.12), ROUND(e * 0.18),
                    ROUND(e * 0.08), ROUND(e * 0.05),
                    ROUND(e * 0.65), ROUND(e * 0.15), ROUND(e * 0.20),
                    ROUND(e * 0.10), ROUND(e * 0.12),
                    -- 停留时长：昨日同一规律，高峰短、空闲长
                    ROUND(e * (420 + CASE h
                        WHEN 12 THEN  0  WHEN 13 THEN  0  WHEN 18 THEN  0  WHEN 19 THEN  0
                        WHEN 11 THEN 60  WHEN 17 THEN 60  WHEN 20 THEN 60
                        WHEN  9 THEN 180 WHEN 10 THEN 150 WHEN 14 THEN 120
                        WHEN 15 THEN 240 WHEN 16 THEN 120 WHEN 21 THEN 180
                        ELSE 90 END
                        + h MOD 5 * 24)),
                    GREATEST(0, e),
                    u5, GREATEST(0, e - u5 - o15), o15,
                    nc, rc
                ) ON DUPLICATE KEY UPDATE
                    enter_count        = VALUES(enter_count),
                    pass_count         = VALUES(pass_count),
                    total_stay_seconds = VALUES(total_stay_seconds),
                    stay_count         = VALUES(stay_count),
                    stay_under5min     = VALUES(stay_under5min),
                    stay_5to15min      = VALUES(stay_5to15min),
                    stay_over15min     = VALUES(stay_over15min);
            END IF;

            SET h = h + 1;
        END WHILE;

        SET d = d + 1;
    END WHILE;
END //

DELIMITER ;

CALL seed_today_data();
DROP PROCEDURE IF EXISTS seed_today_data;

-- 结果汇总
SELECT
    DATE(time_bucket)          AS `日期`,
    COUNT(*)                   AS `记录数`,
    SUM(enter_count)           AS `今日进店总人数`,
    MIN(time_bucket)           AS `最早时间`,
    MAX(time_bucket)           AS `最晚时间`
FROM traffic_fact
WHERE merchant_id = 1
  AND DATE(time_bucket) IN (CURDATE(), DATE_SUB(CURDATE(), INTERVAL 1 DAY))
GROUP BY DATE(time_bucket)
ORDER BY 1 DESC;
