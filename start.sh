#!/bin/bash
# 从 .env 加载环境变量并启动应用

if [ ! -f .env ]; then
  echo "错误：找不到 .env 文件"
  exit 1
fi

# 导出 .env 中的变量（跳过注释和空行）
export $(grep -v '^#' .env | grep -v '^$' | xargs)

echo "环境变量已加载，启动应用..."
mvn spring-boot:run
