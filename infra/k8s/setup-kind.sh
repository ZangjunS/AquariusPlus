#!/usr/bin/env bash
# DS 环境「一键从零搭建」脚本（WSL2 内执行，普通用户 zs 即可，不要 sudo）
#
# 做什么：
#   1. 按 kind-ds.yaml 建 kind 集群（含 10 条 extraPortMappings，宿主机可直接访问 NodePort）
#   2. 起私有镜像仓库 registry:2，并让集群节点信任这个 http 仓库
#   3. 部署 Postgres / Redis / RabbitMQ / Nacos（含初始化 SQL 与 Nacos 配置）
#   4. 部署 4 个后端服务 + 前端，并开放基础设施端口（expose.yaml）
#   5. 起端口桥接容器（kind 集群已存在、端口没写进 extraPortMappings 时的兜底）
#
# 前置：docker、kind、kubectl 已装；Windows 侧端口 30080/30081/30082/30432/30672/30679/30848/30849/30972/30990 未被占用。
#
# 用法：
#   bash setup-kind.sh              # 全套
#   bash setup-kind.sh --no-cluster # 跳过建集群（集群已存在时）
#   bash setup-kind.sh --infra-only # 只部署基础设施
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
CLUSTER=${CLUSTER:-ds-kind}
NS=ds
REG_NAME=ds-registry
REG_PORT=5000
REG_VOL=ds-registry-data

NO_CLUSTER=false
INFRA_ONLY=false
for a in "$@"; do
  case "$a" in
    --no-cluster) NO_CLUSTER=true ;;
    --infra-only) INFRA_ONLY=true ;;
  esac
done

echo "==================== 0. 前置检查 ===================="
for c in docker kind kubectl; do
  command -v "$c" >/dev/null || { echo "缺少 $c，请先安装"; exit 1; }
done
docker info >/dev/null || { echo "docker 没起来"; exit 1; }

echo "==================== 1. 建 kind 集群 ===================="
if [ "$NO_CLUSTER" = false ]; then
  if kind get clusters 2>/dev/null | grep -qx "$CLUSTER"; then
    echo "集群 $CLUSTER 已存在，跳过创建（如需重建：kind delete cluster --name $CLUSTER）"
  else
    kind create cluster --config "$HERE/kind-ds.yaml"
  fi
fi
kubectl config use-context "kind-$CLUSTER" >/dev/null
kubectl get nodes

echo "==================== 2. 私有镜像仓库 ===================="
if ! docker ps -a --format '{{.Names}}' | grep -qx "$REG_NAME"; then
  docker run -d --name "$REG_NAME" --restart unless-stopped \
    -p ${REG_PORT}:5000 -v ${REG_VOL}:/var/lib/registry registry:2 >/dev/null
  echo "已创建 $REG_NAME"
else
  docker start "$REG_NAME" >/dev/null 2>&1 || true
fi

# 节点侧地址：kind 网桥宿主侧 IP（节点容器里访问宿主）
NODE_GW=$(docker network inspect kind -f '{{range .IPAM.Config}}{{.Gateway}}{{end}}')
echo "节点侧仓库地址 = ${NODE_GW}:${REG_PORT}"

# 让节点 containerd 允许 http 拉取（registry:2 是明文 http）
NODE_CONT=ds-kind-control-plane
docker exec "$NODE_CONT" mkdir -p "/etc/containerd/certs.d/${NODE_GW}:${REG_PORT}"
docker exec "$NODE_CONT" sh -c "cat > /etc/containerd/certs.d/${NODE_GW}:${REG_PORT}/hosts.toml <<EOF
server = \"http://${NODE_GW}:${REG_PORT}\"
[host.\"http://${NODE_GW}:${REG_PORT}\"]
  capabilities = [\"pull\", \"resolve\"]
EOF"
echo "节点 containerd 已信任 http 仓库"

echo "==================== 3. 基础设施 ===================="
kubectl create namespace "$NS" --dry-run=client -o yaml | kubectl apply -f -
for f in postgres.yaml redis.yaml rabbitmq.yaml nacos.yaml; do
  kubectl apply -f "$HERE/$f"
done
for d in postgres redis rabbitmq nacos; do
  kubectl -n "$NS" rollout status deploy/$d --timeout=300s
done

echo "==================== 4. 业务服务 + 端口开放 ===================="
kubectl apply -f "$HERE/services.yaml"
kubectl apply -f "$HERE/web.yaml"
if [ "$INFRA_ONLY" = false ]; then
  # 端口开放是必需步骤：Jenkins/Nacos/RabbitMQ/Redis/Postgres 都要能从宿主机连
  kubectl apply -f "$HERE/expose.yaml"
  kubectl -n "$NS" get svc | grep -- '-ext' || true
  bash "$HERE/../scripts/port-bridge.sh" || echo "端口桥接失败（集群内访问不受影响）"
fi

# Nacos 命名空间 dev：服务注册时带 namespaceId=dev，但命名空间记录必须显式创建，
# 否则控制台命名空间下拉框里没有 dev（服务其实是正常注册的）。创建后由 PVC 持久化。
if [ "$INFRA_ONLY" = false ]; then
  for i in $(seq 1 12); do
    code=$(curl -s -o /dev/null -w '%{http_code}' "http://127.0.0.1:30848/nacos/v1/ns/service/list?namespaceId=dev" 2>/dev/null || true)
    [ "$code" = "200" ] && break
    sleep 5
  done
  curl -s -X POST "http://127.0.0.1:30848/nacos/v1/console/namespaces" \
    -d "customNamespaceId=dev&namespaceName=dev&namespaceDesc=dev%20env&namespaceType=0" >/dev/null 2>&1 || true
  echo "Nacos 命名空间 dev 已就绪（已存在则为幂等）"
fi

echo ""
echo "==================== 完成 ===================="
echo "  前端页面  http://127.0.0.1:30081  (demo / 123456)"
echo "  网关 API  http://127.0.0.1:30080"
echo "  仓库      http://127.0.0.1:${REG_PORT}  / 节点侧 ${NODE_GW}:${REG_PORT}"
echo "  Nacos     http://127.0.0.1:30848/nacos"
echo "  RabbitMQ  http://127.0.0.1:30972  (ds / ds123456)"
echo "  Postgres  127.0.0.1:30432  (ds / ds123456, 库 ds)"
echo "  Redis     127.0.0.1:30679"
echo "  下一步：起 Jenkins 容器并跑 ds-cicd 任务（见 DEPLOY-SPEC.md 第 6 章）"
