FROM maven:3.9.16-eclipse-temurin-25-noble AS build
WORKDIR /workspace
COPY pom.xml checkstyle.xml ./
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package

FROM eclipse-temurin:25.0.4_7-jre-noble
ARG OTEL_JAVA_AGENT_VERSION=2.31.1
ARG OTEL_JAVA_AGENT_SHA256=bbf83c151b6400709e2f225bdd07a04f839d9d13b8b93464241333fd25d3e3ba
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl \
    && mkdir -p /opt/otel \
    && curl -fsSL "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_JAVA_AGENT_VERSION}/opentelemetry-javaagent.jar" -o /opt/otel/opentelemetry-javaagent.jar \
    && echo "${OTEL_JAVA_AGENT_SHA256}  /opt/otel/opentelemetry-javaagent.jar" | sha256sum -c - \
    && groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/catalog-api-*.jar app.jar
USER 10001:10001
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseContainerSupport" \
    OTEL_SERVICE_NAME="catalog-api" \
    OTEL_EXPORTER_OTLP_ENDPOINT="http://otel-collector.observability.svc.cluster.local:4318" \
    OTEL_EXPORTER_OTLP_PROTOCOL="http/protobuf" \
    OTEL_METRICS_EXPORTER="none"
ENTRYPOINT ["java", "-javaagent:/opt/otel/opentelemetry-javaagent.jar", "-jar", "/app/app.jar"]
