FROM eclipse-temurin:8-jre

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    JAVA_TOOL_OPTIONS=-Dfile.encoding=UTF-8

WORKDIR /app

COPY deploy/backend/jshERP.jar /app/jshERP.jar
COPY deploy/backend/application-docker.properties /app/config/application.properties
COPY deploy/backend/plugins/ /app/plugins/

RUN mkdir -p /app/logs /app/upload /app/tmp/tomcat /app/pluginConfig

EXPOSE 9999

ENTRYPOINT ["java", "-Xms256m", "-Xmx1024m", "-jar", "/app/jshERP.jar", "--spring.config.location=file:/app/config/application.properties"]
