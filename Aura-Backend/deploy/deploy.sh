#!/usr/bin/env bash
# Aura Backend 一键部署脚本（在开发机 Aura-Backend 目录下运行）
# 用法: ./deploy/deploy.sh <user@server-ip>   例: ./deploy/deploy.sh <user@your-server>
set -euo pipefail

TARGET="${1:?用法: ./deploy/deploy.sh <user@server-ip>}"
REMOTE_DIR="/opt/aura-backend"

echo "==> 1/4 构建 installDist"
./gradlew installDist --quiet

echo "==> 2/4 上传程序与配置到 ${TARGET}:${REMOTE_DIR}"
ssh "$TARGET" "sudo mkdir -p ${REMOTE_DIR} && sudo chown -R \$(whoami):\$(id -gn) ${REMOTE_DIR} && mkdir -p ${REMOTE_DIR}/secrets"
rsync -a --delete --exclude secrets build/install/AuraBackend/ "$TARGET:${REMOTE_DIR}/"
rsync -a config.properties "$TARGET:${REMOTE_DIR}/config.properties"
rsync -a secrets/qw_private.pem "$TARGET:${REMOTE_DIR}/secrets/qw_private.pem"

echo "==> 3/4 安装 systemd 服务"
ssh "$TARGET" "sudo tee /etc/systemd/system/aura-backend.service > /dev/null" < deploy/aura-backend.service
ssh "$TARGET" "sudo systemctl daemon-reload && sudo systemctl enable aura-backend"

echo "==> 4/4 (重)启动服务"
ssh "$TARGET" "sudo systemctl restart aura-backend && sleep 2 && systemctl is-active aura-backend"

echo ""
echo "部署完成。验证: ssh ${TARGET} 'curl -s http://127.0.0.1:8080/healthz'"
echo "记得在腾讯云控制台防火墙放行 TCP 8080（如尚未放行）。"
