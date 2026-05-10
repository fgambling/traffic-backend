# traffic-backend — 后端服务

Spring Boot 3.2 · MyBatis-Plus · MySQL 8 · Redis · JWT

---

## 一、环境要求

| 工具 | 版本 |
|------|------|
| JDK | 17+ |
| Maven | 3.8+ |
| MySQL | 8.0+ |
| Redis | 6.0+ |

---

## 二、数据库初始化

### 方式一：一键脚本（推荐）

```bash
cd traffic-backend
chmod +x setup-db.sh
MYSQL_PWD=你的密码 ./setup-db.sh
```

脚本会自动依次执行所有建表脚本和测试数据导入。

### 方式二：手动执行

```bash
# 1. 基础表结构（必须按此顺序）
mysql -u root -p                                    < sql/init.sql
mysql -u root -p traffic_db                         < sql/upgrade_v2.sql
mysql -u root -p traffic_db                         < sql/upgrade_salesman.sql
mysql -u root -p traffic_db                         < sql/upgrade_v3_stay_buckets.sql
mysql -u root -p traffic_db                         < sql/upgrade_admin.sql
mysql -u root -p traffic_db                         < sql/upgrade_follow_record.sql
mysql -u root -p traffic_db                         < sql/upgrade_earned_commission.sql
mysql -u root -p traffic_db                         < sql/upgrade_join_request.sql
mysql -u root -p traffic_db                         < sql/upgrade_merchant_auth.sql

# 2. AI 模块字段（字段已存在时报错可忽略）
mysql -u root -p traffic_db                         < sql/upgrade_ai_module.sql

# 3. 演示数据（包含30天历史+今日数据+AI建议）
mysql -u root -p traffic_db                         < sql/seed_salesman.sql
mysql -u root -p traffic_db                         < sql/seed_demo.sql
```

AI 模块所需字段已包含在 `upgrade_ai_module.sql` 中，`setup-db.sh` 会自动处理（字段已存在时静默忽略）。

---

## 三、配置文件

`src/main/resources/application.yml`，需要修改的项：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/traffic_db
    username: root       # 改成你的 MySQL 用户名
    password: root       # 改成你的 MySQL 密码
  data:
    redis:
      host: localhost    # Redis 地址
      port: 6379
      password:          # Redis 密码，没有留空
```

---

## 四、启动服务

```bash
# 开发模式
mvn spring-boot:run

# 或打包后运行
mvn clean package -DskipTests
java -jar target/traffic-backend-1.0.0.jar
```

启动成功后访问：`http://localhost:8080`

---

## 五、默认账号

| 角色 | 手机号 / 账号 | 密码 | 套餐 | 状态 |
|------|-------------|------|------|------|
| 管理员 | `admin` | `admin123` | — | — |
| 商家（演示门店） | `199` | `123456` | 高级版 | 正常 |
| 商家（茉莉奶茶） | `199` | `123456` | 高级版 | 正常 |
| 商家（喜茶） | `13911110001` | `123456` | 中级版 | 正常 |
| 商家（瑞幸咖啡） | `13922220002` | `123456` | 高级版 | 正常 |
| 商家（鲜芋仙） | `13655550005` | `123456` | 普通版 | 正常 |
| 业务员 | `13800001111` | `123456` | — | — |

> 状态为"禁用"或"待审核"的商家需在管理后台审批后才能登录。

---

## 六、AI 大模型配置（可选）

AI 建议功能需要在管理后台完成配置，无需修改代码：

1. 登录管理后台（`http://localhost:5173`，账号 admin / admin123）
2. 进入 **建议管理 → 高级版·AI大模型**
3. 填写：
   - **服务商**：OpenAI / 文心一言 / 通义千问 / DeepSeek / MiniMax
   - **模型**：从下拉列表选择
   - **API Key**：对应平台申请的密钥
4. 点击 **测试连接**，成功后点击 **保存配置**

> 未配置时，高级版商家手动点击生成会提示"管理员尚未配置大模型"；凌晨定时任务也不会执行 LLM 调用。

---

## 七、接口说明

| 前缀 | 说明 | 认证 |
|------|------|------|
| `/api/admin/**` | 后台管理接口 | admin token |
| `/api/merchant/**` | 商家端接口 | merchant token |
| `/api/salesman/**` | 业务员端接口 | salesman token |
| `/api/auth/**` | 登录鉴权 | 无需 |
| `/api/device/**` | 设备数据上报 | 无需 |
| `/uploads/**` | 上传文件访问 | 无需 |

---

## 八、常见问题

**Q：启动报 `Unknown column 'confidence'` 或 `Unknown column 'call_id'`**
A：执行第二节"补丁"中的 ALTER TABLE 语句。

**Q：Redis 连接失败**
A：`redis-cli ping` 返回 `PONG` 表示 Redis 正常；否则先启动 Redis。

**Q：端口 8080 被占用**
A：修改 `application.yml` 中的 `server.port`，同时同步修改前端的 `BASE_URL`。

**Q：seed_today.sql 数据是旧的**
A：`seed_today.sql` 可以随时重新执行，会覆盖今日客流数据，适合演示前刷新。
