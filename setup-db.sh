#!/bin/bash
# =============================================================
# 商家客流智能分析系统 — 数据库一键初始化脚本
# 用法：./setup-db.sh
#       MYSQL_PWD=密码 ./setup-db.sh   （免交互输入密码）
# =============================================================

set -e

MYSQL_USER="${MYSQL_USER:-root}"
DB_NAME="traffic_db"

echo ""
echo "============================================="
echo "  商家客流智能分析系统 — 数据库初始化"
echo "============================================="
echo ""

# 如果环境变量没有密码，提示输入
if [ -z "$MYSQL_PWD" ]; then
  read -s -p "请输入 MySQL 密码（用户：$MYSQL_USER）: " MYSQL_PWD
  echo ""
fi

export MYSQL_PWD

run_sql() {
  local file="$1"
  local db="${2:-}"
  echo "▶ 执行: $file"
  if [ -n "$db" ]; then
    mysql -u "$MYSQL_USER" "$db" < "$file"
  else
    mysql -u "$MYSQL_USER" < "$file"
  fi
}

echo ""
echo "【第一步】创建数据库及基础表结构..."
run_sql sql/init.sql

echo ""
echo "【第二步】升级各功能模块..."
run_sql sql/upgrade_v2.sql                "$DB_NAME"
run_sql sql/upgrade_salesman.sql          "$DB_NAME"
run_sql sql/upgrade_v3_stay_buckets.sql   "$DB_NAME"
run_sql sql/upgrade_admin.sql             "$DB_NAME"
run_sql sql/upgrade_follow_record.sql     "$DB_NAME"
run_sql sql/upgrade_earned_commission.sql "$DB_NAME"
run_sql sql/upgrade_join_request.sql      "$DB_NAME"
run_sql sql/upgrade_merchant_auth.sql     "$DB_NAME"
# AI 模块补丁（字段已存在时 MySQL 会报错，用 || true 忽略）
mysql -u "$MYSQL_USER" "$DB_NAME" < sql/upgrade_ai_module.sql 2>/dev/null || true
mysql -u "$MYSQL_USER" "$DB_NAME" < sql/upgrade_package_expire.sql 2>/dev/null || true
run_sql sql/upgrade_person_visit.sql      "$DB_NAME"

echo ""
echo "【第三步】导入演示数据..."
run_sql sql/seed_salesman.sql  "$DB_NAME"
run_sql sql/seed_demo.sql      "$DB_NAME"

echo ""
echo "============================================="
echo "  ✅ 数据库初始化完成！"
echo ""
echo "  默认账号："
echo "  · 管理员  账号: admin        密码: admin123"
echo "  · 商家    手机: 199（高级版演示）         密码: 123456"
echo "  · 业务员  手机: 13800001111  密码: 123456"
echo "============================================="
echo ""
