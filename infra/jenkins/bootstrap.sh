#!/bin/bash
# Jenkins 任务的入口脚本（bootstrap）：先把 GitHub 代码同步到原生盘，再执行仓库里的 ci.sh。
#
# 为什么要有它：Jenkins 官方 LTS 镜像没有 git 插件，任务用 Freestyle + shell 最省事；
# 用这个脚本代替「Git SCM」，效果等价：每次构建都从 GitHub 拉最新代码，且流水线逻辑本身也来自 GitHub。
#
# 任务配置：Execute shell = bash /src/infra/jenkins/bootstrap.sh
# 可用环境变量覆盖：REPO_URL / GIT_REF
set -euo pipefail

REPO_URL="${REPO_URL:-https://github.com/ZangjunS/AquariusPlus.git}"
GIT_REF="${GIT_REF:-main}"
WORK=/home/zs/ds-ci/build
SRC=$WORK/src
TOKEN_FILE=/home/zs/ds-ci/.git-token   # 私有仓库时放 PAT（一行），public 仓库不需要

echo "===================== 0. 同步 GitHub 源码 ====================="
echo "repo=$REPO_URL ref=$GIT_REF src=$SRC"

GIT_ENV=()
if [ -s "$TOKEN_FILE" ]; then
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

mkdir -p "$WORK"
if [ -d "$SRC/.git" ]; then
  git_run -C "$SRC" remote set-url origin "$REPO_URL"
  git_run -C "$SRC" fetch --depth 1 origin "$GIT_REF"
  git_run -C "$SRC" reset --hard FETCH_HEAD >/dev/null
  git_run -C "$SRC" clean -fdx >/dev/null
else
  rm -rf "$SRC"
  git_run clone --depth 1 --branch "$GIT_REF" "$REPO_URL" "$SRC"
fi
echo "commit: $(git -C "$SRC" log -1 --oneline)"

# 把本次构建的 commit 写进构建页描述。
# Freestyle + NullSCM 的构建没有「Changes」页，commit 只在控制台日志里；
# 这里通过 Jenkins API 回写描述，构建列表和详情页都能直接看到构建了哪个提交。
CI_COMMIT=$(git -C "$SRC" rev-parse --short HEAD)
CI_MSG=$(git -C "$SRC" log -1 --pretty=%s)
export CI_COMMIT CI_MSG
echo "[desc] BUILD_NUMBER=${BUILD_NUMBER:-<unset>} JOB_NAME=${JOB_NAME:-<unset>} JENKINS_URL=${JENKINS_URL:-<unset>}"
if [ -n "${BUILD_NUMBER:-}" ] && [ -n "${JOB_NAME:-}" ]; then
  JOB_PATH=$(printf '%s' "$JOB_NAME" | sed 's# #%20#g')
  if curl -s -m 10 -u "${JENKINS_USER:-admin}:${JENKINS_PASS:-admin123}" \
      --data-urlencode "description=commit ${CI_COMMIT} · ${CI_MSG}" \
      "${JENKINS_URL:-http://127.0.0.1:8080}/job/${JOB_PATH}/${BUILD_NUMBER}/submitDescription" >/dev/null 2>&1; then
    echo "构建 #${BUILD_NUMBER} 描述已写入：commit ${CI_COMMIT}"
  else
    echo "(写构建描述失败，不影响构建)"
  fi
fi

# 把已同步的目录告诉 ci.sh，避免重复 clone
export CI_SRC="$SRC"
exec bash "$SRC/infra/jenkins/ci.sh"
