# syntax=docker/dockerfile:1

# ============================================
# STAGE 1: BUILD
# ============================================
FROM eclipse-temurin:25-jdk AS build

WORKDIR /workspace

COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/

RUN chmod +x gradlew

# Download Gradle dependencies
RUN ./gradlew dependencies \
    --configuration runtimeClasspath \
    --no-daemon

COPY src/ src/

# Build Spring Boot executable JAR
RUN ./gradlew bootJar -x test --no-daemon \
    && cp build/libs/*-SNAPSHOT.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract \
       --layers \
       --launcher \
       --destination /layers


# ============================================
# STAGE 2: JAVA RUNTIME
# ============================================
FROM eclipse-temurin:25-jre-alpine AS runtime-base

RUN addgroup -S -g 10001 ecoeats \
    && adduser -S -D -H -u 10001 -G ecoeats ecoeats \
    && rm -f \
       /opt/java/openjdk/lib/server/classes_coh.jsa \
       /opt/java/openjdk/lib/server/classes_nocoops.jsa \
       /opt/java/openjdk/lib/server/classes_nocoops_coh.jsa


# ============================================
# STAGE 3: FINAL IMAGE
# ============================================
FROM scratch AS runtime

COPY --from=runtime-base / /

ENV JAVA_HOME=/opt/java/openjdk

ENV PATH="/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

ENV LANG=en_US.UTF-8 \
    LANGUAGE=en_US:en \
    LC_ALL=en_US.UTF-8

WORKDIR /app

# Spring Boot layers
COPY --from=build --chown=10001:10001 /layers/dependencies/ ./
COPY --from=build --chown=10001:10001 /layers/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /layers/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /layers/application/ ./

# JVM configuration
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom -Djava.awt.headless=true"

ENV SPRING_PROFILES_ACTIVE=prod

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]