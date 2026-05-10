-- =====================================================================
-- seed_demo.sql  ·  商家端演示数据（全量重置）
-- 执行：mysql -u root -p traffic_db < sql/seed_demo.sql
-- 包含：30天历史小时数据 + 今日5分钟粒度 + AI建议 + 顾客访问记录
-- =====================================================================
USE traffic_db;

-- =====================================================================
-- 0. 确保 stay_bucket 列存在（幂等，MySQL 兼容写法）
-- =====================================================================
DROP PROCEDURE IF EXISTS add_stay_columns;
DELIMITER //
CREATE PROCEDURE add_stay_columns()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'traffic_fact' AND COLUMN_NAME = 'stay_under5min'
  ) THEN
    ALTER TABLE traffic_fact
      ADD COLUMN stay_under5min INT NOT NULL DEFAULT 0 COMMENT '<5min人数',
      ADD COLUMN stay_5to15min  INT NOT NULL DEFAULT 0 COMMENT '5-15min人数',
      ADD COLUMN stay_over15min INT NOT NULL DEFAULT 0 COMMENT '>15min人数';
  END IF;
END //
DELIMITER ;
CALL add_stay_columns();
DROP PROCEDURE IF EXISTS add_stay_columns;

-- =====================================================================
-- 1. 更新演示商家（保留 openid 不动，只修商家名和套餐）
-- =====================================================================
UPDATE merchant
SET name         = '茉莉奶茶（演示门店）',
    package_type = 3,   -- 高级版，解锁全部功能（规则建议 + AI大模型预留区）
    status       = 1
WHERE id = 1;

-- =====================================================================
-- 2. 清除旧数据
-- =====================================================================
DELETE FROM traffic_fact   WHERE merchant_id = 1;
DELETE FROM customer_visit WHERE merchant_id = 1;
DELETE FROM ai_advice      WHERE merchant_id = 1;

-- =====================================================================
-- 3. 生成 30 天历史小时数据 + 今日 5 分钟粒度
--    模型：奶茶/轻餐饮门店
--      · 午高峰 11-13 点，晚高峰 17-20 点
--      · 周末 × 1.35
--      · 近 30 天有约 +18% 的增长趋势（从 d=29 到 d=0 线性递增）
-- =====================================================================
DELIMITER //

DROP PROCEDURE IF EXISTS seed_demo_traffic //

CREATE PROCEDURE seed_demo_traffic()
BEGIN
    DECLARE d    INT     DEFAULT 0;   -- 天偏移：0=今天，29=30天前
    DECLARE h    INT;
    DECLARE m    INT;
    DECLARE e    INT;
    DECLARE f    INT;
    DECLARE a1   INT;
    DECLARE a2   INT;
    DECLARE a3   INT;
    DECLARE nc   INT;
    DECLARE rc   INT;
    DECLARE base INT;
    DECLARE wf   DECIMAL(5,3);
    DECLARE dov  DECIMAL(5,3);   -- 日级波动
    DECLARE grow DECIMAL(5,3);   -- 增长因子：越老的日子越小
    DECLARE ts   DATETIME;
    DECLARE r5   DECIMAL(5,3);
    DECLARE r15  DECIMAL(5,3);
    DECLARE u5   INT;
    DECLARE o15  INT;

    WHILE d <= 29 DO

        -- 周末加成
        SET wf = IF(DAYOFWEEK(DATE_SUB(CURDATE(), INTERVAL d DAY)) IN (1,7), 1.35, 1.00);

        -- 30天增长趋势（d=29 最小 0.82，d=0 最大 1.00）
        SET grow = 0.82 + (29 - d) * (0.18 / 29.0);

        -- 日波动 ±10%（7天周期）
        SET dov = 0.90 + (d MOD 7) * 0.033;

        SET h = 9;
        WHILE h <= 21 DO

            -- 小时基准客流
            SET base = CASE h
                WHEN  9 THEN  8  WHEN 10 THEN 15
                WHEN 11 THEN 34  WHEN 12 THEN 58
                WHEN 13 THEN 44  WHEN 14 THEN 20
                WHEN 15 THEN 14  WHEN 16 THEN 18
                WHEN 17 THEN 38  WHEN 18 THEN 65
                WHEN 19 THEN 55  WHEN 20 THEN 40
                WHEN 21 THEN 16
                ELSE 0
            END;

            -- 停留分布比例
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
                -- ── 今日：5 分钟间隔，只写到当前小时/分钟 ──────────────
                IF h <= HOUR(NOW()) THEN
                    SET m = 0;
                    WHILE m < 60 DO
                        SET e = GREATEST(0, ROUND(
                            (base * wf * grow / 12.0)
                            * (0.70 + (h + m + 3) MOD 7 * 0.09)
                        ));

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
                                ROUND(e * (420 + CASE h
                                    WHEN 12 THEN  0  WHEN 13 THEN  0  WHEN 18 THEN  0  WHEN 19 THEN  0
                                    WHEN 11 THEN 60  WHEN 17 THEN 60  WHEN 20 THEN 60
                                    WHEN  9 THEN 180 WHEN 10 THEN 150 WHEN 14 THEN 120
                                    WHEN 15 THEN 240 WHEN 16 THEN 120 WHEN 21 THEN 180
                                    ELSE 90 END + (h * 12 + m / 5) MOD 7 * 18)),
                                GREATEST(0, e),
                                u5, GREATEST(0, e - u5 - o15), o15,
                                nc, rc
                            ) ON DUPLICATE KEY UPDATE
                                enter_count        = VALUES(enter_count),
                                pass_count         = VALUES(pass_count),
                                gender_male        = VALUES(gender_male),
                                gender_female      = VALUES(gender_female),
                                age_under18        = VALUES(age_under18),
                                age_18_60          = VALUES(age_18_60),
                                age_over60         = VALUES(age_over60),
                                total_stay_seconds = VALUES(total_stay_seconds),
                                stay_count         = VALUES(stay_count),
                                stay_under5min     = VALUES(stay_under5min),
                                stay_5to15min      = VALUES(stay_5to15min),
                                stay_over15min     = VALUES(stay_over15min),
                                new_customer_count = VALUES(new_customer_count),
                                returning_customer_count = VALUES(returning_customer_count);
                        END IF;

                        SET m = m + 5;
                    END WHILE;
                END IF;

            ELSE
                -- ── 历史日（d=1~29）：小时粒度 ───────────────────────────
                SET e   = GREATEST(1, ROUND(base * wf * grow * dov
                                * (0.88 + (h + d) MOD 5 * 0.06)));
                SET f   = ROUND(e * (0.54 + (h MOD 4) * 0.015));
                SET a1  = ROUND(e * 0.08);
                SET a3  = ROUND(e * (0.09 + (d MOD 4) * 0.02));
                SET a2  = GREATEST(0, e - a1 - a3);
                SET nc  = ROUND(e * (0.30 + (d MOD 6) * 0.02));
                SET rc  = e - nc;
                SET u5  = ROUND(e * r5);
                SET o15 = ROUND(e * r15);
                SET ts  = TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL d DAY)) + INTERVAL h HOUR;

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
                    ROUND(e * (420 + CASE h
                        WHEN 12 THEN  0  WHEN 13 THEN  0  WHEN 18 THEN  0  WHEN 19 THEN  0
                        WHEN 11 THEN 60  WHEN 17 THEN 60  WHEN 20 THEN 60
                        WHEN  9 THEN 180 WHEN 10 THEN 150 WHEN 14 THEN 120
                        WHEN 15 THEN 240 WHEN 16 THEN 120 WHEN 21 THEN 180
                        ELSE 90 END + (d + h) MOD 7 * 18)),
                    GREATEST(0, e),
                    u5, GREATEST(0, e - u5 - o15), o15,
                    nc, rc
                ) ON DUPLICATE KEY UPDATE
                    enter_count        = VALUES(enter_count),
                    pass_count         = VALUES(pass_count),
                    gender_male        = VALUES(gender_male),
                    gender_female      = VALUES(gender_female),
                    age_under18        = VALUES(age_under18),
                    age_18_60          = VALUES(age_18_60),
                    age_over60         = VALUES(age_over60),
                    total_stay_seconds = VALUES(total_stay_seconds),
                    stay_count         = VALUES(stay_count),
                    stay_under5min     = VALUES(stay_under5min),
                    stay_5to15min      = VALUES(stay_5to15min),
                    stay_over15min     = VALUES(stay_over15min),
                    new_customer_count = VALUES(new_customer_count),
                    returning_customer_count = VALUES(returning_customer_count);
            END IF;

            SET h = h + 1;
        END WHILE;
        SET d = d + 1;
    END WHILE;
END //

DELIMITER ;

CALL seed_demo_traffic();
DROP PROCEDURE IF EXISTS seed_demo_traffic;

-- =====================================================================
-- 4. 顾客访问记录（新老客画像）
-- =====================================================================
INSERT IGNORE INTO customer_visit
    (merchant_id, person_hash, first_visit_date, last_visit_date, visit_count)
VALUES
-- 高频老客
(1,SHA2('demo_p01',256),DATE_SUB(CURDATE(),INTERVAL 28 DAY),CURDATE(),24),
(1,SHA2('demo_p02',256),DATE_SUB(CURDATE(),INTERVAL 25 DAY),DATE_SUB(CURDATE(),INTERVAL 1 DAY),18),
(1,SHA2('demo_p03',256),DATE_SUB(CURDATE(),INTERVAL 22 DAY),DATE_SUB(CURDATE(),INTERVAL 2 DAY),15),
(1,SHA2('demo_p04',256),DATE_SUB(CURDATE(),INTERVAL 20 DAY),CURDATE(),13),
(1,SHA2('demo_p05',256),DATE_SUB(CURDATE(),INTERVAL 18 DAY),DATE_SUB(CURDATE(),INTERVAL 1 DAY),11),
(1,SHA2('demo_p06',256),DATE_SUB(CURDATE(),INTERVAL 15 DAY),DATE_SUB(CURDATE(),INTERVAL 2 DAY),9),
(1,SHA2('demo_p07',256),DATE_SUB(CURDATE(),INTERVAL 14 DAY),CURDATE(),8),
(1,SHA2('demo_p08',256),DATE_SUB(CURDATE(),INTERVAL 12 DAY),DATE_SUB(CURDATE(),INTERVAL 1 DAY),7),
-- 中频老客
(1,SHA2('demo_p09',256),DATE_SUB(CURDATE(),INTERVAL 10 DAY),CURDATE(),5),
(1,SHA2('demo_p10',256),DATE_SUB(CURDATE(),INTERVAL 9 DAY),DATE_SUB(CURDATE(),INTERVAL 2 DAY),4),
(1,SHA2('demo_p11',256),DATE_SUB(CURDATE(),INTERVAL 8 DAY),DATE_SUB(CURDATE(),INTERVAL 3 DAY),4),
(1,SHA2('demo_p12',256),DATE_SUB(CURDATE(),INTERVAL 7 DAY),DATE_SUB(CURDATE(),INTERVAL 1 DAY),3),
(1,SHA2('demo_p13',256),DATE_SUB(CURDATE(),INTERVAL 6 DAY),CURDATE(),3),
(1,SHA2('demo_p14',256),DATE_SUB(CURDATE(),INTERVAL 5 DAY),DATE_SUB(CURDATE(),INTERVAL 2 DAY),3),
(1,SHA2('demo_p15',256),DATE_SUB(CURDATE(),INTERVAL 4 DAY),DATE_SUB(CURDATE(),INTERVAL 1 DAY),2),
(1,SHA2('demo_p16',256),DATE_SUB(CURDATE(),INTERVAL 4 DAY),CURDATE(),2),
(1,SHA2('demo_p17',256),DATE_SUB(CURDATE(),INTERVAL 3 DAY),DATE_SUB(CURDATE(),INTERVAL 2 DAY),2),
(1,SHA2('demo_p18',256),DATE_SUB(CURDATE(),INTERVAL 3 DAY),CURDATE(),2),
-- 近期新客
(1,SHA2('demo_p19',256),DATE_SUB(CURDATE(),INTERVAL 2 DAY),DATE_SUB(CURDATE(),INTERVAL 2 DAY),1),
(1,SHA2('demo_p20',256),DATE_SUB(CURDATE(),INTERVAL 2 DAY),DATE_SUB(CURDATE(),INTERVAL 1 DAY),1),
(1,SHA2('demo_p21',256),DATE_SUB(CURDATE(),INTERVAL 1 DAY),DATE_SUB(CURDATE(),INTERVAL 1 DAY),1),
(1,SHA2('demo_p22',256),DATE_SUB(CURDATE(),INTERVAL 1 DAY),CURDATE(),1),
(1,SHA2('demo_p23',256),CURDATE(),CURDATE(),1),
(1,SHA2('demo_p24',256),CURDATE(),CURDATE(),1),
(1,SHA2('demo_p25',256),CURDATE(),CURDATE(),1);

-- =====================================================================
-- 5. AI / 规则引擎建议（15 条，覆盖备货/排班/营销三类）
-- =====================================================================
INSERT INTO ai_advice
    (merchant_id, trigger_rule_id, source, advice_type, content, data_snapshot, feedback, created_at)
VALUES
-- ── 备货类 ────────────────────────────────────────────────────────────
(1,'R001',1,'备货',
 '今日女性顾客占比 61%，高于历史均值 55%。建议增加草莓、芋泥系列原料备货约 20%，并在入口处摆放相关宣传物料。',
 '{"femaleRatio":61,"historicalAvg":55,"totalEnter":328}',1,
 DATE_SUB(NOW(),INTERVAL 1 DAY)),

(1,'R002',1,'备货',
 '午高峰（12-13 点）进店量 58 人，较过去四周均值 42 人高出 38%。建议明日 11:30 前提前完成备料，并增加当日热销 SKU 库存。',
 '{"currentCount":58,"historicalAvg":42,"hour":12}',1,
 DATE_SUB(NOW(),INTERVAL 6 HOUR)),

(1,'R003',1,'备货',
 '过去一小时戴眼镜顾客占比 34%，高于均值 28%，推测为周边写字楼白领。建议备足低糖/无糖选项，并在点单区突出健康饮品标签。',
 '{"glassesRatio":34,"avgGlassesRatio":28}',0,
 DATE_SUB(NOW(),INTERVAL 2 HOUR)),

(1,'R004',1,'备货',
 '手提包顾客今日占比 22%，高于均值 18%，疑似逛街顺路客群。建议在收银台旁增设"快选套餐"展示牌，缩短点单时间提升翻台效率。',
 '{"handbagRatio":22,"avgHandbagRatio":18}',0,
 DATE_SUB(NOW(),INTERVAL 4 HOUR)),

(1,'R005',1,'备货',
 '近 7 天背包客占比持续在 30-34% 之间，推测学生/通勤族是核心客群。建议上线"通勤特惠"组合套餐（饮品+轻食），定价 25-35 元区间。',
 '{"backpackRatio":32,"days":7}',2,
 DATE_SUB(NOW(),INTERVAL 2 DAY)),

-- ── 排班类 ────────────────────────────────────────────────────────────
(1,'R006',1,'排班',
 '当前时段顾客平均停留 12 分钟，较昨日同时段（7 分钟）延长明显。晚高峰排队风险上升，建议增加 1 名收银员。',
 '{"avgStay":720,"yesterdayAvgStay":420,"hour":18}',1,
 DATE_SUB(NOW(),INTERVAL 30 MINUTE)),

(1,'R007',1,'排班',
 '过去 3 天 11:00-13:00 进店量持续在 90-105 人/小时，建议午市班从 10:30 开始备岗，确保高峰前完成出餐准备。',
 '{"avgLunchCount":97,"hour":11}',1,
 DATE_SUB(NOW(),INTERVAL 1 DAY)),

(1,'R008',1,'排班',
 '今日 14:00-16:00 进店量仅 15-18 人/小时，为全天低谷。建议安排员工轮流休息，同时利用空档整理物料、补充冰块。',
 '{"troughHours":[14,15,16],"avgCount":16}',0,
 DATE_SUB(NOW(),INTERVAL 5 HOUR)),

(1,'R009',1,'排班',
 '周六客流较平日高 35%，过去四周周六晚高峰（18-20 点）平均进店 82 人/小时。建议下周六增加 1 名兼职员工，保障高峰服务质量。',
 '{"weekendMultiplier":1.35,"satEveAvg":82}',1,
 DATE_SUB(NOW(),INTERVAL 3 DAY)),

-- ── 营销类 ────────────────────────────────────────────────────────────
(1,'R010',1,'营销',
 '未成年顾客（<18 岁）今日占比 12%，高于近 7 日均值 8%。推测为放学客群。建议 16:00-18:00 推出学生专属 9 折优惠（凭学生证），抓住放学流量窗口。',
 '{"under18Ratio":12,"avgRatio":8,"peakHour":17}',0,
 DATE_SUB(NOW(),INTERVAL 3 HOUR)),

(1,'R011',1,'营销',
 '过去 3 天同时段客流连续下滑（周一 62 人→周二 55 人→周三 48 人）。建议周四在朋友圈发布限时买一送一活动，防止趋势持续走低。',
 '{"trend":[62,55,48],"dropRate":0.23}',1,
 DATE_SUB(NOW(),INTERVAL 2 DAY)),

(1,'R012',1,'营销',
 '老客回访率本周为 38%，较上周下降 5 个百分点。建议通过微信服务号向 30 天内到访的顾客推送"回头客专属券"，面值 5 元，拉动复购。',
 '{"returnRate":0.38,"lastWeek":0.43}',0,
 DATE_SUB(NOW(),INTERVAL 4 DAY)),

(1,'R013',1,'营销',
 '近 7 天晚 19 点客流占全日 17%，为单小时最高占比。建议推出"19点打卡特饮"限定款，配合拍照道具提升社交传播，扩大晚高峰影响力。',
 '{"hour19Share":0.17,"dailyAvgEnter":365}',1,
 DATE_SUB(NOW(),INTERVAL 6 DAY)),

-- ── 综合 AI 建议（source=2 大模型，用于演示高级版预留区） ──────────────
(1,NULL,2,'营销',
 '**近 7 天客流深度分析**\n\n**核心洞察**\n- 女性顾客占 59%，18-35 岁为主体，与潮流饮品消费主力吻合\n- 背包客占 32%，结合地理位置推测为学生+通勤族叠加客群\n- 晚 18-19 点为最高峰（日均 65 人），占全日 18%\n\n**建议行动**\n1. 推出「下班解压套餐」（18:00-20:00 限定，主打浓郁系列，单杯 +3 元可升大杯）\n2. 在门口设置拍照打卡区，贴合 18-25 岁女性客群的社交分享习惯\n3. 周末增加季节限定款（2 款/周），配合周末 1.35× 客流冲销量',
 '{"source":"ai_llm","dataRange":"近7天","femaleRatio":59,"peakHour":18}',0,
 DATE_SUB(NOW(),INTERVAL 12 HOUR)),

(1,NULL,2,'排班',
 '**人员排班优化建议**\n\n基于 30 天客流数据：\n\n| 时段 | 建议人数 | 依据 |\n|------|---------|------|\n| 11:30-13:30 | 3人 | 午高峰均值 52 人/小时 |\n| 17:30-20:00 | 3人 | 晚高峰均值 60 人/小时 |\n| 14:00-17:00 | 1人 | 低谷期仅 14-18 人/小时 |\n| 其余时段 | 2人 | 常规服务 |\n\n预计调整后可减少顾客等待时间约 35%，同时降低低谷期人力成本约 20%。',
 '{"source":"ai_llm","peakHours":[12,13,18,19],"troughHours":[14,15,16]}',0,
 DATE_SUB(NOW(),INTERVAL 1 DAY));

-- =====================================================================
-- 6. 后台演示商家（不同状态/套餐，供管理员页面展示用）
-- =====================================================================
INSERT INTO merchant (name, license_no, contact_person, contact_phone, address, package_type, status, password, is_lead)
VALUES
  ('喜茶（朝阳大悦城店）', '91110105MA0001AA1A', '李晓梅', '13911110001', '北京市朝阳区朝阳北路101号大悦城B1', 2, 1,
   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lmuS', 0),
  ('瑞幸咖啡（中关村店）',  '91110108MA0002BB2B', '王建国', '13922220002', '北京市海淀区中关村大街1号', 3, 1,
   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lmuS', 0),
  ('熊猫不走蛋糕',          '91440106MA0003CC3C', '张小红', '13833330003', '广州市天河区珠江新城华夏路8号', 1, 0,
   NULL, 0),
  ('好利来（解放碑店）',    '91500103MA0004DD4D', '陈大勇', '13744440004', '重庆市渝中区解放碑步行街民权路88号', 2, 2,
   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lmuS', 0),
  ('鲜芋仙（西湖银泰店）',  '91330102MA0005EE5E', '刘小燕', '13655550005', '杭州市上城区延安路98号银泰百货B1', 1, 1,
   '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lmuS', 0)
ON DUPLICATE KEY UPDATE name = VALUES(name), status = VALUES(status);

-- =====================================================================
-- 完成统计
-- =====================================================================
SELECT CONCAT(
    '✅ 演示数据写入完成：',
    (SELECT COUNT(*)    FROM traffic_fact   WHERE merchant_id = 1), ' 条客流记录，',
    (SELECT SUM(enter_count) FROM traffic_fact WHERE merchant_id = 1), ' 总进店人次，',
    (SELECT COUNT(*)    FROM customer_visit WHERE merchant_id = 1), ' 条访客记录，',
    (SELECT COUNT(*)    FROM ai_advice      WHERE merchant_id = 1), ' 条建议记录'
) AS result;
