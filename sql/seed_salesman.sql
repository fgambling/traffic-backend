-- =============================================
-- 业务员测试数据
-- 执行前请确保 traffic_db 已存在并已执行 init.sql
-- =============================================

-- 插入测试业务员（openid 用于 dev-salesman-login）
INSERT INTO salesman (name, phone, openid, total_commission, balance, status)
VALUES ('张三（测试业务员）', '13800001111', 'test_openid_salesman_001', 8350.00, 3200.00, 1)
ON DUPLICATE KEY UPDATE
    openid           = VALUES(openid),
    total_commission = VALUES(total_commission),
    balance          = VALUES(balance);

-- 获取刚插入的 salesman id（假设为1，若不是请替换）
SET @sid = (SELECT id FROM salesman WHERE openid = 'test_openid_salesman_001' LIMIT 1);

-- 插入几条跟进商家记录（关联已有的 merchant id=1）
-- 先插入几个待激活商家作为跟进目标
INSERT INTO merchant (name, license_no, contact_person, contact_phone, status)
VALUES
    ('荣创科技',   'LIC_RC_001', '赵四', '13811112222', 0),
    ('芳香咖啡',   'LIC_FX_001', '张明', '13922223333', 0),
    ('鲜花花艺坊', 'LIC_XH_001', '王娟', '13733334444', 1),
    ('轻食主义',   'LIC_QS_001', '陈勇', '13644445555', 1)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 跟进记录
INSERT INTO merchant_follow (salesman_id, merchant_id, status, follow_record, cooperation_time, commission)
SELECT
    @sid,
    m.id,
    CASE m.name
        WHEN '荣创科技'   THEN 1   -- 接洽中
        WHEN '芳香咖啡'   THEN 1   -- 接洽中
        WHEN '鲜花花艺坊' THEN 2   -- 已合作
        WHEN '轻食主义'   THEN 2   -- 已合作
    END,
    CASE m.name
        WHEN '荣创科技'   THEN '初次联系，对方有意向'
        WHEN '芳香咖啡'   THEN '已演示产品，等待决策'
        WHEN '鲜花花艺坊' THEN '签约3个月，运营稳定'
        WHEN '轻食主义'   THEN '新签约商家，设备已安装'
    END,
    CASE m.name
        WHEN '鲜花花艺坊' THEN DATE_SUB(NOW(), INTERVAL 90 DAY)
        WHEN '轻食主义'   THEN DATE_SUB(NOW(), INTERVAL 10 DAY)
        ELSE NULL
    END,
    CASE m.name
        WHEN '鲜花花艺坊' THEN 4200.00
        WHEN '轻食主义'   THEN 4150.00
        ELSE NULL
    END
FROM merchant m
WHERE m.name IN ('荣创科技', '芳香咖啡', '鲜花花艺坊', '轻食主义')
ON DUPLICATE KEY UPDATE follow_record = VALUES(follow_record);

SELECT '业务员种子数据插入完成' AS msg;
SELECT CONCAT('业务员 ID: ', @sid, '，openid: test_openid_salesman_001') AS login_info;
