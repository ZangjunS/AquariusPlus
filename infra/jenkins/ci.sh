#!/bin/bash
# DS 微服务 CI/CD 实际执行脚本（Jenkins Freestyle 任务的 "Execute shell" 调用它）
# 依赖：Jenkins 容器挂载 /var/run/docker.sock、/usr/bin/docker、/usr/bin/kubectl、/root/.kube/config，
#       且 --add-host host.docker.internal:host-gateway（用于容器内访问宿主暴露的 30080/30081/5000）
# 关键点：
#   1) 源码先同步到 WSL 原生盘（/tmp/ds-ci/src）再构建 —— /mnt/d 是 9p 挂载，Maven 在上面慢 5~10 倍
#   2) settings.xml 的 localRepository 是 Windows 路径，容器里必须显式 -Dmaven.repo.local
#   3) 镜像 tag 必须每次唯一，否则 kubelet 命中本地旧镜像，改了代码跑的还是旧 jar
set -euo pipefail

REPO=/mnt/d/000/dev/code/item/zangs/springclould/ds/springainativecode
M2_REPO=/mnt/d/dev/envi/maven/.m2/repository
WORK=/home/zs/ds-ci/build
REGISTRY_PUSH=127.0.0.1:5000       # 宿主侧（docker push 由宿主 daemon 执行）
REGISTRY_PULL=172.17.1.1:5000      # kind 节点侧（kubelet 拉取）
VERIFY_HOST=${VERIFY_HOST:-host.docker.internal}
NS=ds
TAG="${TAG:-build-${BUILD_NUMBER:-manual}}"
SKIP_BUILD="${SKIP_BUILD:-false}"

# 容器内的 127.0.0.1 是容器自己，kubeconfig 要改成走 kind 网络里的 API Server
KC=/tmp/kubeconfig
if [ -f /root/.kube/config ]; then
  cp /root/.kube/config "$KC"
  sed -i '/certificate-authority-data:/d' "$KC"
  sed -i 's|server: https://127.0.0.1:[0-9]*|server: https://ds-kind-control-plane:6443\n    insecure-skip-tls-verify: true|' "$KC"
  export KUBECONFIG="$KC"
fi

echo "===================== 0. 环境 ====================="
docker version --format 'docker server {{.Server.Version}}'
kubectl version --client=true | head -2
echo "TAG=$TAG  WORK=$WORK"

echo "===================== 1. 同步源码到原生盘 ====================="
if [ "$SKIP_BUILD" = "true" ]; then
  echo "SKIP_BUILD=true：跳过源码同步 / Maven / 镜像构建 / 推送，直接用仓库里已有的 $TAG"
else
rm -rf "$WORK"; mkdir -p "$WORK/src"
tar -C "$REPO" -cf - --exclude=target --exclude=node_modules . | tar -C "$WORK/src" -xf -
echo "同步完成：$(du -sh "$WORK/src" | cut -f1)"

echo "===================== 2. Maven 打包 ====================="
docker run --rm \
  -v "$M2_REPO:/root/.m2/repository" \
  -v "$WORK/src:/src" \
  -w /src/backend \
  maven:3.9-eclipse-temurin-17 \
  mvn -B -s .mvn/settings.xml -Dmaven.repo.local=/root/.m2/repository -DskipTests clean package
ls -lh "$WORK/src"/backend/*/target/*.jar

echo "===================== 3. 构建镜像 ====================="
cd "$WORK/src"
for m in ds-auth ds-user ds-product ds-gateway; do
  echo "--- $m"
  docker build -f backend/Dockerfile --build-arg MODULE=$m \
    -t ${REGISTRY_PUSH}/ds/$m:${TAG} \
    -t ${REGISTRY_PUSH}/ds/$m:latest backend >/dev/null
done
echo "--- ds-web"
docker build -f web/Dockerfile \
  -t ${REGISTRY_PUSH}/ds/ds-web:${TAG} \
  -t ${REGISTRY_PUSH}/ds/ds-web:latest web >/dev/null

echo "===================== 4. 推送到私有仓库 ====================="
for m in ds-auth ds-user ds-product ds-gateway ds-web; do
  docker push ${REGISTRY_PUSH}/ds/$m:${TAG} >/dev/null
  docker push ${REGISTRY_PUSH}/ds/$m:latest >/dev/null
  echo "pushed ${REGISTRY_PUSH}/ds/$m:${TAG}"
done
curl -s --max-time 5 http://${VERIFY_HOST}:5000/v2/_catalog || echo "(catalog 查询超时，不影响发布)"
echo
fi

echo "===================== 5. 发布到 K8s ====================="
# 顺序很重要：先 apply 清单（清单里写 :latest，保证 apply 后仍是可用镜像），
# 再 set image 到本次唯一 tag —— 反过来会被清单覆盖回去，kubelet 还会命中本地旧镜像。
INFRA=/src/infra/k8s
[ -d "$INFRA" ] || INFRA=/mnt/d/000/dev/code/item/zangs/springclould/ds/infra/k8s
kubectl apply -f "$INFRA/namespace.yaml" >/dev/null
kubectl apply -f "$INFRA/services.yaml" >/dev/null
kubectl apply -f "$INFRA/web.yaml" >/dev/null
echo "清单已 apply（image 基线 :latest）"

for m in ds-auth ds-user ds-product ds-gateway ds-web; do
  kubectl -n ${NS} set image deploy/$m $m=${REGISTRY_PULL}/ds/$m:${TAG}
done
for m in ds-auth ds-user ds-product ds-gateway ds-web; do
  kubectl -n ${NS} rollout status deploy/$m --timeout=300s
done
kubectl -n ${NS} get pods

echo "===================== 6. 访问验证 ====================="
echo "网关健康:        $(curl -s -o /dev/null -w '%{http_code}' http://${VERIFY_HOST}:30080/actuator/health)"
echo "前端页面:        $(curl -s -o /dev/null -w '%{http_code}' http://${VERIFY_HOST}:30081/)"
echo "无 token/products: $(curl -s -o /dev/null -w '%{http_code}' http://${VERIFY_HOST}:30080/products)  (期望 401)"
TOKEN=$(curl -s -X POST -H 'Content-Type: application/json' \
  -d '{"username":"demo","password":"123456"}' \
  http://${VERIFY_HOST}:30080/auth/login | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')
if [ -n "${TOKEN:-}" ]; then
  echo "带 token/products: $(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer $TOKEN" http://${VERIFY_HOST}:30080/products)"
fi
echo "DONE tag=$TAG"
# 说明：端口开放（expose.yaml / port-bridge.sh）属于「环境部署」步骤，不在本流水线内执行，
# 详见 infra/k8s/deploy-all.sh 与部署规范第 9 章。流水线只负责构建、推送、发布、验证。
