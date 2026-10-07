# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src/main ./src/main
# Database/browser tests are run separately, never against cloud during a build.
RUN --mount=type=cache,target=/root/.m2 mvn -B -ntp -Dmaven.test.skip=true package \
    && mkdir /workspace/migration \
    && cd /workspace/migration \
    && jar xf /workspace/target/edulab-khtn8.war \
       WEB-INF/lib WEB-INF/classes/db/migration \
       WEB-INF/classes/vn/edu/khtn8/deployment/DatabaseMigration.class

FROM eclipse-temurin:21-jre-jammy AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 edulab \
    && useradd --uid 10001 --gid edulab --home-dir /opt/edulab --no-create-home edulab \
    && mkdir -p /opt/edulab /var/lib/edulab/storage \
    && chown edulab:edulab /var/lib/edulab/storage
WORKDIR /opt/edulab
COPY --from=build /workspace/target/edulab-khtn8.war ./app.war
COPY --from=build /workspace/migration ./migration
COPY --chmod=755 docker/entrypoint.sh /usr/local/bin/edulab-entrypoint
ENV SPRING_PROFILES_ACTIVE=remote \
    EDULAB_SERVER_PORT=8080 \
    EDULAB_UPLOAD_ROOT=/var/lib/edulab/storage \
    EDULAB_COOKIE_SECURE=true \
    EDULAB_DEV_SCAN_BYPASS=false \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70.0 -Dfile.encoding=UTF-8" \
    TZ=Asia/Ho_Chi_Minh
USER 10001:10001
EXPOSE 8080
STOPSIGNAL SIGTERM
HEALTHCHECK --interval=30s --timeout=10s --start-period=90s --retries=3 \
    CMD curl --fail --silent --show-error --max-time 8 "http://127.0.0.1:${EDULAB_SERVER_PORT}/edulab/actuator/health" || exit 1
ENTRYPOINT ["/usr/local/bin/edulab-entrypoint"]
CMD ["app"]