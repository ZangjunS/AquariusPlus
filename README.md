# DS Spring Cloud 微服务脚手架（JDK 14 / Boot 2.7 / Cloud 2021.0 / SCA 2021.1）

## 目录结构
```
ds/
  springainativecode/           # 业务代码（后端 + 前端）
    backend/                    # Spring Cloud 多模块
      pom.xml                   # 父 POM
      ds-common/                # 通用（常量/异常/统一返回/MyBatisPlus/Redis/Resilience4j/JWT/Knife4j）
      ds-api/                   # 实体 + DTO + Feign 客户端（跨服务契约）
      ds-gateway/               # 网关（路由 + JWT 鉴权 + Resilience4j 限流）
      ds-auth/                  # 认证（JWT 签发/校验/注册/登录，BCrypt）
      ds-user/                  # 用户服务（Feign 调 auth 示例）
      ds-product/               # 商品服务（CRUD + Redis 缓存 + RabbitMQ 发布 + 熔断重试）
      sql/                      # PostgreSQL 初始化
      docker-compose.yml        # 后端专用 compose（基础设施 + 4 服务）
      Dockerfile                # 后端 Dockerfile（多阶段）
      .mvn/settings.xml         # Maven 镜像 + 本地仓库
    web/                        # 前端（Vue3 + Vite + Element Plus + Pinia）
      src/views/Login.vue       # 登录页
      src/views/Home.vue        # 主页（用户/商品 + 新建）
      nginx.conf                # Nginx 反代到网关
      Dockerfile                # 前端 Nginx 镜像
  infra/                        # 外部环境与 CI/CD
    k8s/                        # K8s 清单（Kind 验证）
      namespace.yaml / postgres / redis / rabbitmq / nacos / pgadmin / harbor
      services.yaml            # 4 个业务服务（NodePort 30080 网关）
      web.yaml                 # 前端 Nginx（NodePort 30081）
      setup-kind.sh            # 一键建 kind + 起 Harbor + 部署
      deploy-all.sh            # apply 全部 yaml
    jenkins/
      jenkinsfile              # 多阶段流水线（构建 + 推 Harbor + kubectl apply）
      setup-notes.md           # Jenkins 插件安装清单
  docker-compose.yml            # 一键起全部（基础设施 + 4 服务 + web + Harbor + Jenkins）
  .gitignore
  README.md
```

## 版本矩阵
| 项 | 版本 |
|---|---|
| JDK | 14（`D:\dev\envi\java\j14`） |
| Spring Boot | 2.7.18 |
| Spring Cloud | 2021.0.8 |
| Spring Cloud Alibaba | 2021.1（Nacos） |
| MyBatis-Plus | 3.5.5 |
| Resilience4j | 1.7.0（`spring-boot2`） |
| JJWT | 0.11.5（javax） |
| Knife4j | 4.5.0（`openapi3-spring-boot-starter`） |
| Vue | 3.4 + Vite 5 + Element Plus 2.7 + Pinia 2 |

## 本地开发（不装 K8s）
```powershell
$env:JAVA_HOME = "D:\dev\envi\java\j14"

# 1) 起基础设施 + 4 服务 + 前端
docker compose up -d nacos postgres redis rabbitmq ds-gateway ds-auth ds-user ds-product ds-web

# 2) Nacos 控制台 http://127.0.0.1:8848/nacos (nacos/nacos)
#    命名空间 dev，建 4 个 yml 配置（内容参考 springainativecode/backend/sql/nacos-config-example.yml）

# 3) 浏览器访问
#    http://127.0.0.1            # 前端（Nginx 容器）
#    http://127.0.0.1:8080/doc.html  # 接口文档（网关聚合）
```

## 本地 Kind 验证（K8s 全流程）
```powershell
cd infra\k8s
# 安装 kind + kubectl + docker
.\setup-kind.sh   # 建集群 + 起 Harbor + 部署基础组件 + 4 服务 + 前端
# 或
.\deploy-all.sh   # 只 apply yaml（集群已存在）

# 访问
#   前端  http://127.0.0.1:30081
#   网关  http://127.0.0.1:30080
#   pgAdmin http://127.0.0.1:30083  (admin@ds.com / admin123)
#   Harbor http://127.0.0.1:30082  (admin / Harbor12345)
```

## Jenkins 流水线
```powershell
# 1) 起 Jenkins
docker compose up -d jenkins
# 浏览器 http://127.0.0.1:8090/jenkins
# 首次进：docker compose logs jenkins | Select-String "Jenkins is fully ready" 之前
#          在 jenkins 容器里取初始密码:
#          docker compose exec jenkins cat /var/jenkins_home/secrets/initialAdminPassword

# 2) 装插件（见 infra/jenkins/setup-notes.md）

# 3) 新建 Pipeline 任务，Definition = "Pipeline script"，粘贴 infra/jenkins/jenkinsfile

# 4) 触发构建：构建 → 4 个 jar + 1 个 nginx → 推 Harbor → kubectl apply → 滚动更新
```

## 端口速查
| 组件 | 本地 compose | K8s NodePort |
|---|---|---|
| 前端 Nginx | 80 | 30081 |
| ds-gateway | 8080 | 30080 |
| ds-auth | 8081 | — |
| ds-user | 8082 | — |
| ds-product | 8083 | — |
| Nacos | 8848 / 9848 | — |
| Postgres | 5432 | — |
| Redis | 6379 | — |
| RabbitMQ | 5672 / 15672 | — |
| pgAdmin | — | 30083 |
| Harbor | 30082 | 30082 |
| Jenkins | 8090 | — |

## 说明
- **JDK 14**：本机 `D:\dev\envi\java\j14`，设 `JAVA_HOME` 指向它即可。
- **Maven 镜像**：`springainativecode/backend/.mvn/settings.xml` 配了 aliyun 公共仓库代理 central；`.mvn/maven.config` 把本地仓库写到 `D:\dev\envi\maven\.m2\repository`（不污染 `%USERPROFILE%`，与 `AENVI_info/README.txt` 记录一致）。
- **JWT 密钥**：`ds-common/security/JwtUtil.java` 里硬编码占位密钥，生产务必改成 Nacos 下发的随机 32+ 字节。
- **RabbitMQ 事件**：`ds-product` 里 `ProductEventConsumer` 是本地回环，生产可拆独立消费服务。
- **Resilience4j 1.7.0**：`ds-product` 挂 `@CircuitBreaker` + `@Retry`；网关侧 `CircuitBreaker` + `Retry` + `RateLimiter`（100 req/s）。
- **Redis 缓存**：`ds-product.getById` 走 `RedisTemplate`，写操作先清缓存。
- **Feign**：`AuthFeignClient` / `ProductFeignClient` 在 `ds-api`，业务服务 `@EnableFeignClients("com.ds.api")` 引入即可。
