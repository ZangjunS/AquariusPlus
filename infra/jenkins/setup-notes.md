# Jenkins 初始配置（首次启动后通过 UI 安装插件，这里只放最小配置）
# 实际 Jenkins 插件安装走 UI：
#   Manage Jenkins → Plugins → 安装:
#     - Pipeline
#     - Docker Pipeline
#     - Git
#     - Kubernetes
#     - Timestamper
#     - Blue Ocean (可选，可视化流水线)
# 配置 Global Tool Locations:
#   - JDK 14 → /usr/lib/jvm/java-14-openjdk (容器内)
#   - Maven  → 容器自带
# 配置 Credentials:
#   - Harbor 用户名密码 (admin / Harbor12345)
#   - Git 仓库地址 + 凭证
# 配置 System:
#   - 把宿主机 kubectl 二进制拷进容器，或装 kubectl
# 配置 Pipeline Job:
#   - 新建 Pipeline 任务，Definition 选 "Pipeline script"
#   - 粘贴 jenkinsfile 内容
