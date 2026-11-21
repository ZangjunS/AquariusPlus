#!/bin/bash
# 启动 Jenkins（LTS 2.440.3 + JDK17 容器）。在 WSL2 内以普通用户执行。
# 要点（都是踩过的坑）：
#   1. 与宿主共享 docker.sock，因此 docker build 的上下文路径在容器内必须与宿主完全一致 → REPO_SRC 原样挂载
#   2. settings.xml 里的 localRepository 是 Windows 路径，容器内无效 → 挂宿主 Maven 仓库到 /root/.m2/repository
#   3. 容器里的 127.0.0.1 不是宿主 → 加 --add-host host.docker.internal:host-gateway
#   4. kind 的 API Server 只绑宿主 127.0.0.1:34875 → Jenkins 容器要接入 kind 网络，用 ds-kind-control-plane:6443
#   5. /tmp 不是宿主 /tmp → 构建工作目录用宿主共享的原生盘目录 /home/zs/ds-ci
set -e

REPO_D=/mnt/d/000/dev/code/item/zangs/springclould/ds
REPO_SRC=$REPO_D/springainativecode
M2=/mnt/c/Users/zangs/.m2
INIT_DIR=$REPO_D/infra/jenkins/init.groovy.d
mkdir -p /home/zs/ds-ci

docker rm -f ds-jenkins >/dev/null 2>&1 || true
docker run -d --name ds-jenkins \
  --restart unless-stopped \
  -u root \
  -p 8090:8080 \
  --add-host host.docker.internal:host-gateway \
  -e JAVA_OPTS="-Djenkins.install.runSetupWizard=false -Dhudson.model.DownloadService.noSignatureCheck=true" \
  -e TZ=Asia/Shanghai \
  -v ds-jenkins-home:/var/jenkins_home \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -v /usr/bin/docker:/usr/bin/docker \
  -v /usr/bin/kubectl:/usr/bin/kubectl \
  -v /home/zs/.kube:/root/.kube \
  -v /home/zs/ds-ci:/home/zs/ds-ci \
  -v "$REPO_D":/src \
  -v "$REPO_SRC":"$REPO_SRC" \
  -v "$M2":/root/.m2/repository \
  -v "$INIT_DIR":/var/jenkins_home/init.groovy.d \
  jenkins/jenkins:2.440.3-lts-jdk17

# 接入 kind 网络，才能访问 API Server
docker network connect kind ds-jenkins >/dev/null 2>&1 || true

echo "启动中，等待 http://127.0.0.1:8090 就绪 ..."
for i in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' http://127.0.0.1:8090/login || true)
  if [ "$code" = "200" ]; then echo "Jenkins 就绪（第 ${i} 次探测）"; docker logs --tail 5 ds-jenkins; exit 0; fi
  sleep 3
done
echo "超时，查看日志：docker logs ds-jenkins"
