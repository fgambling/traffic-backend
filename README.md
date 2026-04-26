# traffic-backend — 后端服务

Spring Boot 3.2 + MyBatis-Plus + MySQL + Redis + JWT

---

## 一、环境要求

| 工具 | 版本要求 |
|------|---------|
| JDK | 17+ |
| Maven | 3.8+ |
| MySQL | 8.0+（推荐 8.0） |
| Redis | 6.0+ |

---

## 二、数据库初始化

> 推荐使用下方的**一键脚本**，也可以手动按顺序执行。

### 方式一：一键导入（推荐）

项目根目录下已提供 `setup-db.sh` 脚本，执行一次即可完成所有表结构和测试数据的导入：

```bash
# 给脚本加执行权限
chmod +x setup-db.sh

# 执行（会依次提示输入 MySQL 密码）
./setup-db.sh
```

或者一条命令传入密码：

```bash
MYSQL_PWD=你的密码 ./setup-db.sh
```

### 方式二：手动按顺序执行

以下 SQL 文件必须**严格按此顺序**执行：

```bash
mysql -u root -p < sql/init.sql
mysql -u root -p traffic_db < sql/upgrade_v2.sql
mysql -u root -p traffic_db < sql/upgrade_salesman.sql
mysql -u root -p traffic_db < sql/upgrade_v3_stay_buckets.sql
mysql -u root -p traffic_db < sql/upgrade_admin.sql
mysql -u root -p traffic_db < sql/upgrade_follow_record.sql
mysql -u root -p traffic_db < sql/upgrade_earned_commission.sql
mysql -u root -p traffic_db < sql/upgrade_join_request.sql
mysql -u root -p traffic_db < sql/upgrade_merchant_auth.sql
```

### 导入测试数据（可选，建议导入）

```bash
# 测试业务员 + 跟进商家数据
mysql -u root -p traffic_db < sql/seed_salesman.sql

# 通用测试商家数据
mysql -u root -p traffic_db < sql/seed_test_data.sql

# 今日 + 昨日客流数据（merchant_id=1，用于看板展示）
mysql -u root -p traffic_db < sql/seed_today.sql
```

> `seed_today.sql` 可以**随时重复执行**，会自动覆盖今日数据，适合每次演示前刷新数据。

---

## 三、配置文件

配置文件路径：`src/main/resources/application.yml`

需要关注的配置项：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/traffic_db   # 如果 MySQL 不在本机，修改 localhost
    username: root        # 改成你的 MySQL 用户名
    password: root        # 改成你的 MySQL 密码

  data:
    redis:
      host: localhost     # Redis 地址
      port: 6379
      password:           # Redis 密码（没有就留空）
```

> 也可以通过环境变量覆盖（推荐生产环境使用）：
> `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` / `REDIS_HOST` 等

---

## 四、启动服务

```bash
# 方式一：Maven 直接运行
mvn spring-boot:run

# 方式二：先打包再运行
mvn clean package -DskipTests
java -jar target/traffic-backend-1.0.0.jar
```

启动成功后访问：`http://localhost:8080`

---

## 五、默认账号

| 角色 | 账号 | 密码 | 说明 |
|------|------|------|------|
| 管理员 | admin | admin123 | 硬编码，不在数据库中 |
| 商家 | 导入测试数据后自动创建 | 123456 | 手机号登录，默认密码 123456 |
| 业务员 | 导入 seed_salesman.sql 后 | 手机号 13800001111 | 小程序扫码或密码登录 |

---

## 六、接口说明

| 前缀 | 说明 |
|------|------|
| `/api/admin/**` | 后台管理接口（需要 admin token） |
| `/api/merchant/**` | 商家端接口（需要 merchant token） |
| `/api/salesman/**` | 业务员端接口（需要 salesman token） |
| `/api/auth/**` | 登录鉴权接口（无需 token） |
| `/api/device/**` | 设备上报接口（无需 token） |
| `/uploads/**` | 上传图片静态访问（无需 token） |

---

## 七、常见问题

**Q：启动报错 `Unknown column 'password' in merchant`**
A：需要执行 `upgrade_merchant_auth.sql`，见第二节。

**Q：Redis 连接失败**
A：检查 Redis 是否已启动：`redis-cli ping`，返回 `PONG` 表示正常。

**Q：端口被占用**
A：修改 `application.yml` 中的 `server.port`，同时同步修改前端的 `BASE_URL`。
