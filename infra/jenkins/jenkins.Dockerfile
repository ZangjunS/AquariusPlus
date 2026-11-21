# Jenkins 容器（挂载宿主机 Docker socket，让 Jenkins 能 build/push）
# 端口 8090 避免与前端 80 冲突
image: jenkins/jenkins:2.440.3-lts-jdk17
