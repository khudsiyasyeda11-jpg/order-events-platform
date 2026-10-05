# syntax=docker/dockerfile:1.7
# Build context: repository root (multi-module build).
#   docker build -f order-service/Dockerfile -t order-service:local .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY events-contract/pom.xml events-contract/
COPY order-service/pom.xml order-service/
COPY notification-service/pom.xml notification-service/
RUN --mount=type=cache,target=/root/.m2 mvn -B -q -pl order-service -am dependency:go-offline -DskipTests || true
COPY events-contract/src events-contract/src
COPY order-service/src order-service/src
RUN --mount=type=cache,target=/root/.m2 mvn -B -pl order-service -am package -DskipTests \
 && java -Djarmode=tools -jar order-service/target/order-service.jar extract --layers --launcher --destination /workspace/extracted

FROM eclipse-temurin:21-jre-noble AS runtime
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --no-create-home app
WORKDIR /app
COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./
USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    SPRING_PROFILES_ACTIVE=prod
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
