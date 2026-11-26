# kanban 后端运行镜像
#
# 说明：
#   1) 目标机 192.168.2.17 无外网（registry-1.docker.io 不可达），因此运行镜像只能用本机
#      已有的 eclipse-temurin:17-jdk，不能拉 17-jre。jar 是 Java 17 字节码，JDK 也能跑，
#      代价只是镜像体积大一点；日后能拉到 jre 镜像时把 FROM 换掉即可。
#   2) jar 由本机（Windows + Maven）构建后上传到本目录，容器只负责运行，不在容器里编译，
#      避免目标机为了构建去联网下载 Maven 依赖。
FROM eclipse-temurin:17-jdk

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_OPTS="-Xms256m -Xmx768m -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai"

WORKDIR /app

# 上传目录：附件物理存储，由 docker-compose 挂载到宿主机 ./uploads
RUN mkdir -p /app/uploads

COPY kanban-server.jar /app/kanban-server.jar

EXPOSE 9085

# exec 让 java 成为 1 号进程，docker stop 的 SIGTERM 能直接送到 Spring Boot 的优雅停机
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/kanban-server.jar"]
