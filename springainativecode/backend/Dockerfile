# 运行阶段直接用已经 mvn package 出来的可执行 jar，镜像里不需要 maven
# 构建方（Jenkins / 本地）先产出 target/{MODULE}.jar，再用 --build-arg MODULE 选择模块
# 说明：项目字节码是 Java 14（class 版本 58），用 JRE 17 运行完全兼容；
#       Docker Hub 上 eclipse-temurin:14-jre 在本机网络拉不到，故用 17-jre。
FROM eclipse-temurin:17-jre

ARG MODULE=ds-gateway
WORKDIR /app

COPY ${MODULE}/target/${MODULE}.jar /app/app.jar

ENV TZ=Asia/Shanghai \
    JAVA_OPTS="-Xms256m -Xmx512m"

EXPOSE 8080 8081 8082 8083

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
