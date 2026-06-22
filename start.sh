#!/usr/bin/env bash
set -e

cd "$(dirname "$0")/gms-server"

JAR="target/BeiDou.jar"
CONFIG="src/main/resources/application.yml"

if [ ! -f "$JAR" ]; then
  echo "❌ 未找到 $JAR，请先执行: mvn clean package -pl gms-server"
  exit 1
fi

JAVA_BIN="${JAVA_HOME:-$(which java)}"
echo "☕ 使用 Java: $JAVA_BIN"
echo "📦 启动: $JAR"
echo "⚙️  配置: $CONFIG"
echo ""

exec java -Xmx4G -Dspring.config.location="$CONFIG" -jar "$JAR"
