# 本地开发：
#   1) 设置 JAVA_HOME 指向 JDK 14（本机 D:\dev\envi\java\j14）
#      $env:JAVA_HOME = "D:\dev\envi\java\j14"   # PowerShell
#      export JAVA_HOME=/d/dev/envi/java/j14        # Git Bash
#   2) 起基础设施:  docker compose up -d nacos postgres redis rabbitmq
#   3) 在 Nacos 控制台 namespace=dev 建 ds-auth / ds-user / ds-product / ds-gateway 4 个 yml 配置
#      （内容参考 sql/nacos-config-example.yml）
#   4) 编译打包:
#      mvn -s .mvn/settings.xml -DskipTests clean package
#   5) 启动（任选）:
#      方式 A（IDE）: 各服务 main 方法直接 run，JDK 14
#      方式 B（命令行）:
#        mvn -s .mvn/settings.xml -pl ds-auth     spring-boot:run
#        mvn -s .mvn/settings.xml -pl ds-user     spring-boot:run
#        mvn -s .mvn/settings.xml -pl ds-product  spring-boot:run
#        mvn -s .mvn/settings.xml -pl ds-gateway  spring-boot:run
#      方式 C（已打包 jar）:
#        java -jar ds-auth/target/ds-auth.jar
#        java -jar ds-user/target/ds-user.jar
#        java -jar ds-product/target/ds-product.jar
#        java -jar ds-gateway/target/ds-gateway.jar
