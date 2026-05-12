FROM node:22-alpine AS frontend-build
WORKDIR /workspace/frontend
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.14-eclipse-temurin-25 AS backend-build
WORKDIR /workspace/backend
COPY backend/.mvn .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline
COPY backend/src src
COPY --from=frontend-build /workspace/frontend/dist src/main/resources/static
RUN ./mvnw -q -DskipTests package

FROM maven:3.9.14-eclipse-temurin-25
ARG DEPENDENCY_CHECK_VERSION=12.2.1
WORKDIR /app
ENV SPRING_PROFILES_ACTIVE=docker
ENV JARSCAN_DATA_DIR=/app/data
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip \
    && curl -fsSL -o /tmp/dependency-check.zip "https://github.com/dependency-check/DependencyCheck/releases/download/v${DEPENDENCY_CHECK_VERSION}/dependency-check-${DEPENDENCY_CHECK_VERSION}-release.zip" \
    && unzip -q /tmp/dependency-check.zip -d /opt \
    && ln -sf /opt/dependency-check/bin/dependency-check.sh /usr/local/bin/dependency-check.sh \
    && rm -rf /var/lib/apt/lists/* /tmp/dependency-check.zip
COPY --from=backend-build /workspace/backend/target/backend-0.1.0-SNAPSHOT.jar /app/jarscan.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/jarscan.jar"]
