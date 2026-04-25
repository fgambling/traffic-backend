-- =====================================================================
-- 测试种子数据脚本
-- 执行顺序：init.sql → upgrade_v2.sql → 本脚本
-- 执行方式：mysql -u root -p traffic_db < sql/seed_test_data.sql
-- =====================================================================
USE traffic_db;

-- =====================================================================
-- 1. 更新测试商家（升级为高级版，设置测试 openid）
-- =====================================================================
UPDATE merchant
SET name         = '茉莉奶茶（测试门店）',
    package_type = 3,          -- 高级版，解锁所有功能
    status       = 1,
    openid       = 'test_openid_merchant_001'
WHERE id = 1;

-- =====================================================================
-- 2. 清理旧测试数据（可重复执行）
-- =====================================================================
DELETE FROM traffic_fact   WHERE merchant_id = 1;
DELETE FROM customer_visit WHERE merchant_id = 1;
DELETE FROM ai_advice      WHERE merchant_id = 1;

-- =====================================================================
-- 3. 生成 14 天 × 13 小时 客流数据（存储过程）
--
-- 模拟一家奶茶/轻餐饮门店的典型客流规律：
--   · 午餐高峰：11-13 点
--   · 晚餐高峰：17-20 点
--   · 周末流量约工作日 1.35 倍
--   · 每天有 ±15% 的自然波动
-- =====================================================================
DELIMITER //

DROP PROCEDURE IF EXISTS seed_traffic //

CREATE PROCEDURE seed_traffic()
BEGIN
    DECLARE d    INT     DEFAULT 0;   -- 天偏移 (0=今天, 13=13天前)
    DECLARE h    INT;                 -- 小时 9~21
    DECLARE e    INT;                 -- enter_count
    DECLARE f    INT;                 -- gender_female
    DECLARE a1   INT;                 -- age_under18
    DECLARE a2   INT;                 -- age_18_60
    DECLARE a3   INT;                 -- age_over60
    DECLARE nc   INT;                 -- new_customer_count
    DECLARE rc   INT;                 -- returning_customer_count
    DECLARE base INT;                 -- 小时基准进店人数
    DECLARE wf   DECIMAL(4,2);        -- 周末加成因子
    DECLARE dov  DECIMAL(4,2);        -- 日级波动因子
    DECLARE ts   DATETIME;

    WHILE d < 14 DO
        -- 周末 (DAYOFWEEK 1=周日, 7=周六) 客流 × 1.35
        SET wf  = IF(DAYOFWEEK(DATE_SUB(CURDATE(), INTERVAL d DAY)) IN (1, 7), 1.35, 1.00);
        -- 每天轻微波动：0.85 ~ 1.15
        SET dov = 0.85 + (d MOD 7) * 0.05;

        SET h = 9;
        WHILE h <= 21 DO
            -- 小时基准客流（奶茶店典型曲线）
            SET base = CASE h
                WHEN 9  THEN 8
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

            -- 叠加时点扰动 (±12%)
            SET e  = GREATEST(1, ROUND(base * wf * dov * (0.88 + (h + d) MOD 5 * 0.06)));
            SET f  = ROUND(e * (0.54 + (h MOD 4) * 0.015));  -- 54~60% 女性
            SET a1 = ROUND(e * 0.08);
            SET a3 = ROUND(e * (0.09 + (d MOD 4) * 0.02));
            SET a2 = e - a1 - a3;
            SET nc = ROUND(e * (0.30 + (d MOD 6) * 0.02));   -- 30~40% 新客
            SET rc = e - nc;
            SET ts = TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL d DAY)) + INTERVAL h HOUR;

            INSERT INTO traffic_fact (
                merchant_id, device_id, time_bucket,
                enter_count,  pass_count,
                gender_male,  gender_female,
                age_under18,  age_18_60, age_over60,
                accessory_glasses, accessory_hat, accessory_boots,
                bag_handbag,  bag_shoulder, bag_backpack, hold_item,
                upper_short,  upper_long,  upper_coat,
                upper_style_stripe, upper_style_logo, upper_style_plaid, upper_style_splice,
                lower_trousers, lower_shorts, lower_skirt,
                lower_style_stripe, lower_style_pattern,
                total_stay_seconds, stay_count,
                new_customer_count, returning_customer_count
            ) VALUES (
                1, 'cam-0', ts,
                e,              ROUND(e * 0.65),     -- pass_count ≈ 65% of enter
                e - f,          f,
                a1, a2, a3,
                ROUND(e * 0.28), ROUND(e * 0.18), ROUND(e * 0.11),
                ROUND(e * 0.20), ROUND(e * 0.22), ROUND(e * 0.32), ROUND(e * 0.15),
                ROUND(e * 0.25), ROUND(e * 0.60), ROUND(e * 0.15),  -- 4月份以长袖为主
                ROUND(e * 0.12), ROUND(e * 0.18), ROUND(e * 0.08), ROUND(e * 0.05),
                ROUND(e * 0.65), ROUND(e * 0.15), ROUND(e * 0.20),
                ROUND(e * 0.10), ROUND(e * 0.12),
                -- 停留时长：随时段变化（高峰短、空闲长），420~660 秒（7~11 分钟）
                ROUND(e * (420 + CASE h
                    WHEN 12 THEN  0  WHEN 13 THEN  0  WHEN 18 THEN  0  WHEN 19 THEN  0
                    WHEN 11 THEN 60  WHEN 17 THEN 60  WHEN 20 THEN 60
                    WHEN  9 THEN 180 WHEN 10 THEN 150 WHEN 14 THEN 120
                    WHEN 15 THEN 240 WHEN 16 THEN 120 WHEN 21 THEN 180
                    ELSE 90 END
                    + (d + h) MOD 5 * 24)),  e,   -- 额外 ±2 分钟日内随机波动
                nc, rc
            ) ON DUPLICATE KEY UPDATE
                enter_count              = VALUES(enter_count),
                pass_count               = VALUES(pass_count),
                gender_male              = VALUES(gender_male),
                gender_female            = VALUES(gender_female),
                age_under18              = VALUES(age_under18),
                age_18_60                = VALUES(age_18_60),
                age_over60               = VALUES(age_over60),
                accessory_glasses        = VALUES(accessory_glasses),
                accessory_hat            = VALUES(accessory_hat),
                accessory_boots          = VALUES(accessory_boots),
                bag_handbag              = VALUES(bag_handbag),
                bag_shoulder             = VALUES(bag_shoulder),
                bag_backpack             = VALUES(bag_backpack),
                hold_item                = VALUES(hold_item),
                upper_short              = VALUES(upper_short),
                upper_long               = VALUES(upper_long),
                upper_coat               = VALUES(upper_coat),
                upper_style_stripe       = VALUES(upper_style_stripe),
                upper_style_logo         = VALUES(upper_style_logo),
                upper_style_plaid        = VALUES(upper_style_plaid),
                upper_style_splice       = VALUES(upper_style_splice),
                lower_trousers           = VALUES(lower_trousers),
                lower_shorts             = VALUES(lower_shorts),
                lower_skirt              = VALUES(lower_skirt),
                lower_style_stripe       = VALUES(lower_style_stripe),
                lower_style_pattern      = VALUES(lower_style_pattern),
                total_stay_seconds       = VALUES(total_stay_seconds),
                stay_count               = VALUES(stay_count),
                new_customer_count       = VALUES(new_customer_count),
                returning_customer_count = VALUES(returning_customer_count);

            SET h = h + 1;
        END WHILE;
        SET d = d + 1;
    END WHILE;
END //

DELIMITER ;

CALL seed_traffic();
DROP PROCEDURE IF EXISTS seed_traffic;

-- =====================================================================
-- 4. 新老客记录（50 条虚拟顾客）
-- =====================================================================
INSERT IGNORE INTO customer_visit (merchant_id, person_hash, first_visit_date, last_visit_date, visit_count)
VALUES
(1, SHA2(CONCAT('person_001',1), 256), DATE_SUB(CURDATE(),INTERVAL 30 DAY), CURDATE(),        12),
(1, SHA2(CONCAT('person_002',1), 256), DATE_SUB(CURDATE(),INTERVAL 25 DAY), DATE_SUB(CURDATE(),INTERVAL 1 DAY), 8),
(1, SHA2(CONCAT('person_003',1), 256), DATE_SUB(CURDATE(),INTERVAL 20 DAY), DATE_SUB(CURDATE(),INTERVAL 2 DAY), 6),
(1, SHA2(CONCAT('person_004',1), 256), DATE_SUB(CURDATE(),INTERVAL 15 DAY), DATE_SUB(CURDATE(),INTERVAL 3 DAY), 4),
(1, SHA2(CONCAT('person_005',1), 256), DATE_SUB(CURDATE(),INTERVAL 10 DAY), DATE_SUB(CURDATE(),INTERVAL 1 DAY), 3),
(1, SHA2(CONCAT('person_006',1), 256), DATE_SUB(CURDATE(),INTERVAL 8  DAY), DATE_SUB(CURDATE(),INTERVAL 2 DAY), 3),
(1, SHA2(CONCAT('person_007',1), 256), DATE_SUB(CURDATE(),INTERVAL 7  DAY), DATE_SUB(CURDATE(),INTERVAL 4 DAY), 2),
(1, SHA2(CONCAT('person_008',1), 256), DATE_SUB(CURDATE(),INTERVAL 6  DAY), DATE_SUB(CURDATE(),INTERVAL 1 DAY), 2),
(1, SHA2(CONCAT('person_009',1), 256), DATE_SUB(CURDATE(),INTERVAL 5  DAY), CURDATE(),        2),
(1, SHA2(CONCAT('person_010',1), 256), DATE_SUB(CURDATE(),INTERVAL 3  DAY), CURDATE(),        2),
(1, SHA2(CONCAT('person_011',1), 256), DATE_SUB(CURDATE(),INTERVAL 2  DAY), CURDATE(),        2),
(1, SHA2(CONCAT('person_012',1), 256), DATE_SUB(CURDATE(),INTERVAL 1  DAY), CURDATE(),        2),
(1, SHA2(CONCAT('person_013',1), 256), CURDATE(),                           CURDATE(),        1),
(1, SHA2(CONCAT('person_014',1), 256), CURDATE(),                           CURDATE(),        1),
(1, SHA2(CONCAT('person_015',1), 256), CURDATE(),                           CURDATE(),        1),
(1, SHA2(CONCAT('person_016',1), 256), DATE_SUB(CURDATE(),INTERVAL 1  DAY), DATE_SUB(CURDATE(),INTERVAL 1 DAY), 1),
(1, SHA2(CONCAT('person_017',1), 256), DATE_SUB(CURDATE(),INTERVAL 2  DAY), DATE_SUB(CURDATE(),INTERVAL 2 DAY), 1),
(1, SHA2(CONCAT('person_018',1), 256), DATE_SUB(CURDATE(),INTERVAL 3  DAY), DATE_SUB(CURDATE(),INTERVAL 3 DAY), 1),
(1, SHA2(CONCAT('person_019',1), 256), DATE_SUB(CURDATE(),INTERVAL 4  DAY), DATE_SUB(CURDATE(),INTERVAL 4 DAY), 1),
(1, SHA2(CONCAT('person_020',1), 256), DATE_SUB(CURDATE(),INTERVAL 5  DAY), DATE_SUB(CURDATE(),INTERVAL 5 DAY), 1),
(1, SHA2(CONCAT('person_021',1), 256), DATE_SUB(CURDATE(),INTERVAL 60 DAY), DATE_SUB(CURDATE(),INTERVAL 2 DAY), 22),
(1, SHA2(CONCAT('person_022',1), 256), DATE_SUB(CURDATE(),INTERVAL 55 DAY), DATE_SUB(CURDATE(),INTERVAL 3 DAY), 18),
(1, SHA2(CONCAT('person_023',1), 256), DATE_SUB(CURDATE(),INTERVAL 45 DAY), DATE_SUB(CURDATE(),INTERVAL 1 DAY), 15),
(1, SHA2(CONCAT('person_024',1), 256), DATE_SUB(CURDATE(),INTERVAL 40 DAY), CURDATE(),        13),
(1, SHA2(CONCAT('person_025',1), 256), DATE_SUB(CURDATE(),INTERVAL 35 DAY), DATE_SUB(CURDATE(),INTERVAL 2 DAY), 11);

-- =====================================================================
-- 5. AI / 规则引擎建议（10 条，覆盖不同类型）
-- =====================================================================
INSERT INTO ai_advice (merchant_id, trigger_rule_id, source, advice_type, content, data_snapshot, feedback, created_at)
VALUES
-- 备货类
(1, 'R001', 1, '备货',
 '今日女性顾客占比 61%，显著高于历史均值 55%。建议增加女性偏好饮品（如草莓、芋泥系列）的原料备货量约 20%，并考虑在入口处摆放相关宣传物料。',
 '{"femaleRatio":61,"historicalAvg":55,"totalEnter":328}', 1,
 DATE_SUB(NOW(), INTERVAL 1 DAY)),

(1, 'R003', 1, '备货',
 '过去一小时内戴眼镜顾客占比 34%，高于均值 28%，推测为周边写字楼白领人群。建议备足低糖/无糖选项，并在点单区突出健康饮品标签。',
 '{"glassesRatio":34,"avgGlassesRatio":28,"window":"last_1h"}', 0,
 DATE_SUB(NOW(), INTERVAL 2 HOUR)),

-- 排班类
(1, 'R005', 1, '排班',
 '当前时段顾客平均停留时长 12 分钟，较昨日同时段（7 分钟）明显延长。排队可能增加，建议增加 1 名收银员应对晚高峰。',
 '{"avgStay":720,"yesterdayAvgStay":420,"hour":18}', 1,
 DATE_SUB(NOW(), INTERVAL 30 MINUTE)),

(1, 'R008', 1, '排班',
 '今日 12:00-13:00 进店量为 58 人，较过去 4 周同时段均值 42 人高出 38%。明日午市建议提前 30 分钟开始备料，确保 11:30 前完成出餐准备。',
 '{"currentCount":58,"historicalAvg":42,"hour":12}', 2,
 DATE_SUB(NOW(), INTERVAL 1 DAY)),

-- 营销类
(1, 'R002', 1, '营销',
 '未成年顾客（<18岁）今日占比 12%，高于近 7 日均值 8%。推测为放学客群。建议 16:00-18:00 推出学生专属优惠套餐（凭学生证享 9 折），抓住放学流量窗口。',
 '{"under18Ratio":12,"avgUnder18Ratio":8,"peakHour":17}', 0,
 DATE_SUB(NOW(), INTERVAL 3 HOUR)),

(1, 'R006', 1, '营销',
 '过去 3 天同时段客流连续下滑（周一 62 人→周二 55 人→周三 48 人）。建议周四在朋友圈发布限时买一送一活动，刺激到店，防止客流趋势持续走低。',
 '{"trend":["62","55","48"],"dropRate":0.23}', 1,
 DATE_SUB(NOW(), INTERVAL 2 DAY)),

(1, NULL, 2, '营销',
 '根据近 7 天客流画像分析：\n\n**核心发现**\n- 女性顾客占 59%，18-35 岁年龄段占主体\n- 背包客占 32%（推测为学生/上班族）\n- 晚 18-19 点为最高峰，日均到店 62 人\n\n**建议行动**\n1. 推出「下班解压套餐」（18:00-20:00 限定，主打浓郁系列）\n2. 设计拍照打卡区吸引 18-25 岁女性客群进行社交传播\n3. 周末增加季节限定款，配合周末 1.3 倍客流冲量',
 '{"source":"ai_llm","dataRange":"近7天","keyMetrics":{"femaleRatio":59,"peakHour":18,"avgDaily":365}}', 0,
 DATE_SUB(NOW(), INTERVAL 5 HOUR)),

-- 综合类
(1, 'R004', 1, '备货',
 '携带手提包顾客今日占比 22%，高于均值 18%，疑似逛街顺路客群。此类顾客决策较快，建议在收银台旁增设「快选套餐」展示牌，缩短点单时间提升翻台效率。',
 '{"handbagRatio":22,"avgHandbagRatio":18}', 0,
 DATE_SUB(NOW(), INTERVAL 4 HOUR)),

(1, 'R007', 1, '排班',
 '今日 9:00-10:00 进店 14 人，但周六同时段历史均值为 21 人（今日为周六），低于预期 33%。建议检查门口是否有遮挡或标识不清问题，并考虑推送开店优惠通知触达附近用户。',
 '{"currentCount":14,"weekendAvg":21,"hour":9,"dayType":"weekend"}', 1,
 DATE_SUB(NOW(), INTERVAL 6 HOUR)),

(1, NULL, 2, '排班',
 '**人员排班优化建议**\n\n基于 14 天客流数据，建议如下排班调整：\n\n| 时段 | 当前 | 建议 | 原因 |\n|------|------|------|------|\n| 11:30-13:30 | 2人 | 3人 | 午高峰平均 52 人/小时 |\n| 17:30-20:00 | 2人 | 3人 | 晚高峰平均 58 人/小时 |\n| 14:00-17:00 | 2人 | 1人 | 低谷期仅 13-16 人/小时 |\n\n预计调整后可减少顾客等待时间约 40%。',
 '{"source":"ai_llm","peakHours":[12,13,18,19],"troughHours":[14,15,16]}', 0,
 DATE_SUB(NOW(), INTERVAL 12 HOUR));

-- =====================================================================
-- 完成提示
-- =====================================================================
SELECT CONCAT(
    '✅ 种子数据写入完成：',
    (SELECT COUNT(*) FROM traffic_fact   WHERE merchant_id = 1), ' 条客流记录，',
    (SELECT COUNT(*) FROM customer_visit WHERE merchant_id = 1), ' 条访客记录，',
    (SELECT COUNT(*) FROM ai_advice      WHERE merchant_id = 1), ' 条建议记录'
) AS result;
