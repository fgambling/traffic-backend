-- 商家客流智能分析系统 - 数据库初始化脚本
-- 创建数据库（如已存在则忽略）
CREATE DATABASE IF NOT EXISTS traffic_db DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE traffic_db;

-- =============================================
-- 商家表
-- =============================================
CREATE TABLE IF NOT EXISTS merchant (
    id            INT          NOT NULL AUTO_INCREMENT COMMENT '商家ID',
    name          VARCHAR(100) NOT NULL COMMENT '商家名称',
    license_no    VARCHAR(64)  NOT NULL COMMENT '营业执照号',
    contact_person VARCHAR(50) COMMENT '联系人姓名',
    contact_phone  VARCHAR(20) COMMENT '联系电话',
    address        VARCHAR(255) COMMENT '地址',
    package_type   TINYINT     NOT NULL DEFAULT 1 COMMENT '套餐类型: 1普通版 2中级版 3高级版',
    status         TINYINT     NOT NULL DEFAULT 0 COMMENT '状态: 0待激活 1正常 2禁用',
    bind_id        VARCHAR(64) COMMENT '关联设备bindId',
    openid         VARCHAR(64) COMMENT '微信openid（商家小程序登录用）',
    created_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_license_no (license_no),
    KEY idx_bind_id (bind_id),
    KEY idx_openid (openid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家表';

-- =============================================
-- 业务员表
-- =============================================
CREATE TABLE IF NOT EXISTS salesman (
    id               INT           NOT NULL AUTO_INCREMENT COMMENT '业务员ID',
    name             VARCHAR(50)   NOT NULL COMMENT '姓名',
    phone            VARCHAR(20)   NOT NULL COMMENT '手机号',
    password         VARCHAR(128)  COMMENT '密码（加密存储）',
    openid           VARCHAR(64)   COMMENT '微信openid',
    total_commission DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '累计佣金',
    balance          DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '可提现余额',
    status           TINYINT       NOT NULL DEFAULT 1 COMMENT '状态: 0禁用 1正常',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_phone (phone),
    KEY idx_openid (openid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务员表';

-- =============================================
-- 商家跟进表
-- =============================================
CREATE TABLE IF NOT EXISTS merchant_follow (
    id               INT          NOT NULL AUTO_INCREMENT COMMENT '跟进记录ID',
    salesman_id      INT          NOT NULL COMMENT '业务员ID',
    merchant_id      INT          NOT NULL COMMENT '商家ID',
    status           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态: 1接洽中 2已合作 3已流失',
    follow_record    TEXT         COMMENT '跟进记录',
    cooperation_time DATETIME     COMMENT '合作时间',
    commission       DECIMAL(10,2) COMMENT '该商家带来的佣金',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_salesman_id (salesman_id),
    KEY idx_merchant_id (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家跟进表';

-- =============================================
-- 提现申请表
-- =============================================
CREATE TABLE IF NOT EXISTS withdraw_apply (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '申请ID',
    salesman_id INT           NOT NULL COMMENT '业务员ID',
    amount      DECIMAL(10,2) NOT NULL COMMENT '提现金额',
    way         TINYINT       NOT NULL COMMENT '提现方式: 1微信 2银行卡',
    account     VARCHAR(100)  NOT NULL COMMENT '收款账号',
    status      TINYINT       NOT NULL DEFAULT 0 COMMENT '状态: 0审核中 1已打款 2驳回',
    remark      VARCHAR(255)  COMMENT '备注',
    created_at  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '申请时间',
    PRIMARY KEY (id),
    KEY idx_salesman_id (salesman_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='提现申请表';

-- =============================================
-- 客流聚合宽表（核心表）
-- 按 merchant_id + device_id + 分钟时间戳 聚合
-- =============================================
CREATE TABLE IF NOT EXISTS traffic_fact (
    id                  BIGINT   NOT NULL AUTO_INCREMENT COMMENT '记录ID',
    merchant_id         INT      NOT NULL COMMENT '商家ID',
    device_id           VARCHAR(64) NOT NULL COMMENT '摄像头设备ID',
    time_bucket         DATETIME NOT NULL COMMENT '分钟时间戳（秒清零）',

    -- 人流量
    enter_count         INT      NOT NULL DEFAULT 0 COMMENT '进店人数',
    pass_count          INT      NOT NULL DEFAULT 0 COMMENT '穿行人数（未进店）',

    -- 性别
    gender_male         INT      NOT NULL DEFAULT 0 COMMENT '男性人数',
    gender_female       INT      NOT NULL DEFAULT 0 COMMENT '女性人数',

    -- 年龄段
    age_under18         INT      NOT NULL DEFAULT 0 COMMENT '未成年（<18岁）',
    age_18_60           INT      NOT NULL DEFAULT 0 COMMENT '中青年（18-60岁）',
    age_over60          INT      NOT NULL DEFAULT 0 COMMENT '老年（>60岁）',

    -- 配饰
    accessory_glasses   INT      NOT NULL DEFAULT 0 COMMENT '戴眼镜人数',
    accessory_hat       INT      NOT NULL DEFAULT 0 COMMENT '戴帽子人数',
    accessory_boots     INT      NOT NULL DEFAULT 0 COMMENT '穿靴子人数',

    -- 包袋
    bag_handbag         INT      NOT NULL DEFAULT 0 COMMENT '手提包人数',
    bag_shoulder        INT      NOT NULL DEFAULT 0 COMMENT '单肩包人数',
    bag_backpack        INT      NOT NULL DEFAULT 0 COMMENT '背包人数',
    hold_item           INT      NOT NULL DEFAULT 0 COMMENT '手持物品人数',

    -- 上衣类型（互斥）
    upper_short         INT      NOT NULL DEFAULT 0 COMMENT '短袖人数',
    upper_long          INT      NOT NULL DEFAULT 0 COMMENT '长袖人数',
    upper_coat          INT      NOT NULL DEFAULT 0 COMMENT '长外套人数',

    -- 上衣风格（可叠加）
    upper_style_stripe  INT      NOT NULL DEFAULT 0 COMMENT '上衣条纹',
    upper_style_logo    INT      NOT NULL DEFAULT 0 COMMENT '上衣Logo',
    upper_style_plaid   INT      NOT NULL DEFAULT 0 COMMENT '上衣格子',
    upper_style_splice  INT      NOT NULL DEFAULT 0 COMMENT '上衣拼接',

    -- 下装类型（互斥）
    lower_trousers      INT      NOT NULL DEFAULT 0 COMMENT '长裤人数',
    lower_shorts        INT      NOT NULL DEFAULT 0 COMMENT '短裤人数',
    lower_skirt         INT      NOT NULL DEFAULT 0 COMMENT '裙子人数',

    -- 下装风格
    lower_style_stripe  INT      NOT NULL DEFAULT 0 COMMENT '下装条纹',
    lower_style_pattern INT      NOT NULL DEFAULT 0 COMMENT '下装图案',

    -- 停留时长统计
    total_stay_seconds  INT      NOT NULL DEFAULT 0 COMMENT '总停留秒数（排除穿行）',
    stay_count          INT      NOT NULL DEFAULT 0 COMMENT '有效停留人次（排除穿行）',

    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_device_time (merchant_id, device_id, time_bucket),
    INDEX idx_merchant_time (merchant_id, time_bucket)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='客流聚合宽表';

-- =============================================
-- AI建议表
-- =============================================
CREATE TABLE IF NOT EXISTS ai_advice (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '建议ID',
    merchant_id INT          NOT NULL COMMENT '商家ID',
    advice_type VARCHAR(50)  NOT NULL COMMENT '建议类型（如：traffic_analysis, product_recommend）',
    content     TEXT         NOT NULL COMMENT 'AI建议正文',
    data_source JSON         COMMENT '生成建议所用的数据摘要',
    feedback    TINYINT      NOT NULL DEFAULT 0 COMMENT '用户反馈: 0未反馈 1有用 2无用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    KEY idx_merchant_id (merchant_id),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI经营建议表';

-- =============================================
-- 商家业务信息表
-- =============================================
CREATE TABLE IF NOT EXISTS merchant_business_info (
    id               INT      NOT NULL AUTO_INCREMENT COMMENT 'ID',
    merchant_id      INT      NOT NULL COMMENT '商家ID',
    menu             JSON     COMMENT '菜单/商品列表',
    promotions       JSON     COMMENT '促销活动',
    business_hours   JSON     COMMENT '营业时间',
    target_audience  VARCHAR(255) COMMENT '目标客群描述',
    updated_at       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_merchant_id (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商家业务信息表（供AI分析使用）';

-- =============================================
-- AI调用日志表
-- =============================================
CREATE TABLE IF NOT EXISTS ai_call_log (
    id               BIGINT        NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    merchant_id      INT           NOT NULL COMMENT '商家ID',
    request_prompt   TEXT          COMMENT '发送给AI的提示词',
    response_raw     TEXT          COMMENT 'AI原始响应',
    parsed_advice_ids JSON         COMMENT '解析生成的建议ID列表',
    tokens_used      INT           COMMENT '消耗Token数',
    cost_estimated   DECIMAL(8,4)  COMMENT '预估费用（美元）',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间',
    PRIMARY KEY (id),
    KEY idx_merchant_id (merchant_id),
    KEY idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI调用日志表';

-- =============================================
-- 初始化测试数据（可选）
-- =============================================
-- 插入一条测试商家（bind_id与设备匹配）
INSERT IGNORE INTO merchant (id, name, license_no, contact_person, contact_phone, package_type, status, bind_id)
VALUES (1, '测试商家', '91110000MA0000001X', '张三', '13800000001', 1, 1, 'BIND-001');
