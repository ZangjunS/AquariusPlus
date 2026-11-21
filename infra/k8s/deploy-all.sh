# 一键部署所有 yaml（setup-kind.sh 已跑过后用）
# 注意：清单顺序 = 命名空间 → 基础设施 → 业务服务 → 端口开放（expose.yaml 是必需步骤）
#!/usr/bin/env bash
set -e
NS="ds"
cd "$(dirname "$0")"

echo "==> 命名空间 + 基础设施"
kubectl apply -f namespace.yaml
kubectl apply -f postgres.yaml
kubectl apply -f redis.yaml
kubectl apply -f rabbitmq.yaml
kubectl apply -f nacos.yaml

echo "==> 业务服务 + 前端"
kubectl apply -f services.yaml
kubectl apply -f web.yaml

echo "==> 端口开放（Jenkins/Nacos/RabbitMQ/Redis/Postgres → NodePort）"
kubectl apply -f expose.yaml
kubectl -n "$NS" get svc | grep -- '-ext' || true
bash ../scripts/port-bridge.sh || echo "端口桥接失败（集群内访问不受影响）"

echo "==> 等待就绪"
for svc in postgres redis rabbitmq nacos ds-auth ds-user ds-product ds-gateway ds-web; do
  kubectl -n "$NS" rollout status deploy/$svc --timeout=300s
done

echo ""
kubectl -n "$NS" get svc
kubectl -n "$NS" get pods
