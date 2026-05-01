# ======================================
# Build Stage
# ======================================
FROM tlifu75/secura:maven-deps-java21-1.0.0 AS build

WORKDIR /build

# JVM + Maven memory tuning (CRITICAL for Jenkins)
ENV MAVEN_OPTS="-Xms512m -Xmx1536m -XX:+ExitOnOutOfMemoryError" \
    JAVA_TOOL_OPTIONS="-Xms512m -Xmx1536m"

# Copy project definition
COPY pom.xml .

# Copy sources
COPY src ./src

# Build (no tests, no transfer spam)
RUN mvn -B clean package -DskipTests --no-transfer-progress


# ======================================
# Runtime Stage
# ======================================
FROM tlifu75/secura:wildfly-java21-1.0.0

ENV WILDFLY_HOME=/opt/wildfly \
    DEPLOYMENT_DIR=/opt/wildfly/standalone/deployments \
    WAR_NAME=taskflow-frontend.war

COPY --from=build /build/target/${WAR_NAME} ${DEPLOYMENT_DIR}/

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/ || exit 1
