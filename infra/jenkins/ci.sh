#!/bin/bash
# DS 微服务 CI/CD 执行脚本（Jenkins Freestyle 任务的实际工作体）
#
# 与旧版的区别：源码不再从磁盘挂载复制，而是**从 GitHub 拉取**（默认 ZangjunS/AquariusPlus:main）。
#   1) clone/pull 到 WSL 原生盘 /home/zs/ds-ci/build/src（同时满足 docker daemon 能访问构建上下文）
#   2) Maven 依赖自动落到 /root/.m2/repository（挂宿主 .m2，缺什么自动下载）
#   3) 5 个镜像打唯一 tag → push 私有仓库 → set image → 并行 rollout → 访问验证
#
# 可用环境变量覆盖：REPO_URL / GIT_REF / TAG / SKIP_BUILD / VERIFY_HOST
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/ZangjunS/AquariusPlus.git}"
GIT_REF="${GIT_REF:-main}"
WORK=/home/zs/ds-ci/build
SRC="${CI_SRC:-$WORK/src}"          # bootstrap.sh 已同步过就复用，避免重复 clone
TOKEN_FILE=/home/zs/ds-ci/.git-token # 私有仓库时放 PAT（一行），public 仓库不需要
M2_REPO=/mnt/d/dev/envi/maven/.m2/repository
REGISTRY_PUSH=127.0.0.1:5000        # 宿主侧（docker push 由宿主 daemon 执行）
REGISTRY_PULL=172.17.1.1:5000       # kind 节点侧（kubelet 拉取）
VERIFY_HOST=${VERIFY_HOST:-host.docker.internal}
NS=ds
TAG="${TAG:-build-${BUILD_NUMBER:-manual}}"
SKIP_BUILD="${SKIP_BUILD:-false}"

# ========= 模块选择（Jenkins 勾选项 → 实际清单） =========
# 构建哪些（默认全不选；BUILD_ALL=true 等于全选）
if [ -n "${BUILD_ALL+x}${BUILD_AUTH+x}" ]; then
  if [ "${BUILD_ALL:-false}" = "true" ]; then
    BUILD_MODULES="ds-auth ds-user ds-product ds-gateway ds-web"
  else
    BUILD_MODULES=""
    [ "${BUILD_AUTH:-false}"    = "true" ] && BUILD_MODULES="$BUILD_MODULES ds-auth"
    [ "${BUILD_USER:-false}"    = "true" ] && BUILD_MODULES="$BUILD_MODULES ds-user"
    [ "${BUILD_PRODUCT:-false}" = "true" ] && BUILD_MODULES="$BUILD_MODULES ds-product"
    [ "${BUILD_GATEWAY:-false}" = "true" ] && BUILD_MODULES="$BUILD_MODULES ds-gateway"
    [ "${BUILD_WEB:-false}"     = "true" ] && BUILD_MODULES="$BUILD_MODULES ds-web"
  fi
  BUILD_MODULES=$(echo $BUILD_MODULES)
else
  # 直接 curl 触发（没传这些参数）时保持旧行为：全部构建
  BUILD_MODULES="ds-auth ds-user ds-product ds-gateway ds-web"
fi

# 重启哪些（默认全选；RS_NONE=true 等于全取消）
if [ "${RS_NONE:-false}" = "true" ]; then
  RS_MODULES=""
else
  RS_MODULES=""
  [ "${RS_AUTH:-true}"    = "true" ] && RS_MODULES="$RS_MODULES ds-auth"
  [ "${RS_USER:-true}"    = "true" ] && RS_MODULES="$RS_MODULES ds-user"
  [ "${RS_PRODUCT:-true}" = "true" ] && RS_MODULES="$RS_MODULES ds-product"
  [ "${RS_GATEWAY:-true}" = "true" ] && RS_MODULES="$RS_MODULES ds-gateway"
  [ "${RS_WEB:-true}"     = "true" ] && RS_MODULES="$RS_MODULES ds-web"
fi
RS_MODULES=$(echo $RS_MODULES)

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
echo "TAG=$TAG  REPO=$REPO_URL  REF=$GIT_REF  SRC=$SRC"
echo "构建模块=[${BUILD_MODULES:-无}]  重启程序=[${RS_MODULES:-无}]"

# 私有仓库支持：有 token 文件就走带凭据的 clone，没有就匿名（public 仓库够用）
GIT_ENV=()
if [ -s "$TOKEN_FILE" ]; then
  echo "检测到 $TOKEN_FILE，使用带凭据的拉取"
  GIT_TOKEN=$(tr -d '\r\n' < "$TOKEN_FILE")
  cat > /tmp/gh-askpass.sh <<'EOS'
#!/bin/sh
case "$1" in
  *Username*) echo "x-access-token" ;;
  *Password*) echo "$GIT_TOKEN" ;;
esac
EOS
  chmod 700 /tmp/gh-askpass.sh
  export GIT_TOKEN
  GIT_ENV=(-c "credential.helper=!" -c "core.askPass=/tmp/gh-askpass.sh")
fi

git_run() { git "${GIT_ENV[@]}" "$@"; }

echo "===================== 1. 从 GitHub 拉取源码 ====================="
if [ "$SKIP_BUILD" = "true" ]; then
  echo "SKIP_BUILD=true：跳过拉取 / Maven / 构建 / 推送，直接用仓库里已有的 $TAG"
  [ -d "$SRC" ] || { echo "错误：$SRC 不存在，SKIP_BUILD 需要已有构建产物"; exit 1; }
else
  mkdir -p "$WORK"
  if [ -d "$SRC/.git" ]; then
    echo "增量更新 $SRC"
    git_run -C "$SRC" remote set-url origin "$REPO_URL"
    git_run -C "$SRC" fetch --depth 1 origin "$GIT_REF"
    git_run -C "$SRC" reset --hard "FETCH_HEAD" >/dev/null
    git_run -C "$SRC" clean -fdx >/dev/null
  else
    echo "首次克隆到 $SRC"
    rm -rf "$SRC"
    git_run clone --depth 1 --branch "$GIT_REF" "$REPO_URL" "$SRC"
  fi
  echo "commit: $(git -C "$SRC" log -1 --oneline)"
  [ -f "$SRC/springainativecode/backend/pom.xml" ] || { echo "错误：拉下来的代码结构不对，缺 backend/pom.xml"; exit 1; }
fi
BACKEND="$SRC/springainativecode"
INFRA="$SRC/infra/k8s"

echo "===================== 2. Maven 打包（依赖自动下载到 /root/.m2） ====================="
# 只有勾了后端模块才需要 Maven；只勾 ds-web（纯静态页）可以整段跳过
BE_SELECTED=$(echo "$BUILD_MODULES" | tr ' ' '\n' | grep -E '^ds-(auth|user|product|gateway)$' || true)
if [ "$SKIP_BUILD" != "true" ] && [ -n "$BE_SELECTED" ]; then
  echo "本地 m2 缓存：$(du -sh /root/.m2/repository 2>/dev/null | cut -f1 || echo 0)"
  docker run --rm \
    -v "$M2_REPO:/root/.m2/repository" \
    -v "$BACKEND:/src" \
    -w /src/backend \
    maven:3.9-eclipse-temurin-17 \
    mvn -B -s .mvn/settings.xml -Dmaven.repo.local=/root/.m2/repository -DskipTests clean package
  ls -lh "$BACKEND"/backend/*/target/*.jar 2>/dev/null || echo "(jar 列表读取失败，看上面 Maven 输出)"

  echo "===================== 3. 构建镜像（只构建勾选的模块） ====================="
  cd "$BACKEND"
  for m in $BUILD_MODULES; do
    if [ "$m" = "ds-web" ]; then
      echo "--- ds-web"
      docker build -f web/Dockerfile \
        -t ${REGISTRY_PUSH}/ds/$m:${TAG} \
        -t ${REGISTRY_PUSH}/ds/$m:latest web >/dev/null
    else
      echo "--- $m"
      docker build -f backend/Dockerfile --build-arg MODULE=$m \
        -t ${REGISTRY_PUSH}/ds/$m:${TAG} \
        -t ${REGISTRY_PUSH}/ds/$m:latest backend >/dev/null
    fi
  done

  echo "===================== 4. 推送到私有仓库（只推送勾选的模块） ====================="
  for m in $BUILD_MODULES; do
    docker push ${REGISTRY_PUSH}/ds/$m:${TAG} >/dev/null
    docker push ${REGISTRY_PUSH}/ds/$m:latest >/dev/null
    echo "pushed ${REGISTRY_PUSH}/ds/$m:${TAG}  (latest 同步)"
  done
  curl -s --max-time 5 http://${VERIFY_HOST}:5000/v2/_catalog || echo "(catalog 查询超时，不影响发布)"
  echo
elif [ "$SKIP_BUILD" = "true" ]; then
  echo "========== 构建阶段：SKIP_BUILD=true，跳过 Maven / 构建 / 推送 =========="
elif [ -n "$BUILD_MODULES" ]; then
  echo "========== 构建阶段：勾选的模块里没有后端模块（只勾了 ds-web）；只构建前端镜像 =========="
  cd "$BACKEND"
  docker build -f web/Dockerfile \
    -t ${REGISTRY_PUSH}/ds/ds-web:${TAG} -t ${REGISTRY_PUSH}/ds/ds-web:latest web >/dev/null
  docker push ${REGISTRY_PUSH}/ds/ds-web:${TAG} >/dev/null
  docker push ${REGISTRY_PUSH}/ds/ds-web:latest >/dev/null
  echo "pushed ${REGISTRY_PUSH}/ds/ds-web:${TAG}"
else
  echo "========== 构建阶段：没有勾选任何模块，跳过 Maven / 构建 / 推送 =========="
fi

echo "===================== 5. 发布到 K8s（只重启勾选的程序） ====================="
# 顺序很重要：先 apply 清单（清单里写 :latest），再 set image 到本次唯一 tag。
# 反过来会被清单覆盖回去，kubelet 还可能命中本地旧镜像。
kubectl apply -f "$INFRA/namespace.yaml" >/dev/null
kubectl apply -f "$INFRA/services.yaml" >/dev/null
kubectl apply -f "$INFRA/web.yaml" >/dev/null
echo "清单已 apply（image 基线 :latest）"

if [ -z "$RS_MODULES" ]; then
  echo "本次未勾选任何重启程序：只保留清单，不 set image、不 rollout"
  kubectl -n ${NS} get deploy
  echo "DONE tag=$TAG commit=$(git -C "$SRC" rev-parse --short HEAD 2>/dev/null || echo unknown)（无重启）"
  exit 0
fi

for m in $RS_MODULES; do
  kubectl -n ${NS} set image deploy/$m $m=${REGISTRY_PULL}/ds/$m:${TAG}
done
echo "本次重启：$RS_MODULES"
# 不要用 `kubectl wait --for=condition=Available`：旧 Pod 仍 Available，会立刻返回成功，
# 新 Pod 可能还在启动 → 验证阶段打出假 502。rollout status 跟踪的才是本次更新。
pids=""
for m in $RS_MODULES; do
  kubectl -n ${NS} rollout status deploy/$m --timeout=900s &
  pids="$pids $!"
done
rc=0
for p in $pids; do
  wait $p || rc=1
done
kubectl -n ${NS} get pods
if [ $rc -ne 0 ]; then
  echo "滚动更新未在 900s 内全部完成，查看上面 rollout status 输出定位卡住的 Deployment"
  exit 1
fi

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
echo "DONE tag=$TAG commit=$(git -C "$SRC" rev-parse --short HEAD 2>/dev/null || echo unknown)"
# 说明：端口开放（expose.yaml / port-bridge.sh）属于「环境部署」步骤，不在本流水线内执行。
