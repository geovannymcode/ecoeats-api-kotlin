# syntax=docker/dockerfile:1
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle/ gradle/
RUN chmod +x gradlew
RUN --mount=type=cache,target=/root/.gradle ./gradlew dependencies --configuration runtimeClasspath --no-daemon
COPY src/ src/
RUN --mount=type=cache,target=/root/.gradle ./gradlew bootJar -x test --no-daemon \
    && cp build/libs/*-SNAPSHOT.jar app.jar \
    && java -Djarmode=tools -jar app.jar extract --layers --launcher --destination /layers

FROM eclipse-temurin:25-jre-alpine AS runtime-base
# Keep the standard CDS archive; omit optional heap/header variants to meet the size budget.
RUN addgroup -S -g 10001 ecoeats && adduser -S -D -H -u 10001 -G ecoeats ecoeats \
    && rm -f /opt/java/openjdk/lib/server/classes_coh.jsa \
       /opt/java/openjdk/lib/server/classes_nocoops.jsa \
       /opt/java/openjdk/lib/server/classes_nocoops_coh.jsa

# Repack the Temurin filesystem so removed archives do not remain in inherited image layers.
FROM scratch AS runtime
COPY --from=runtime-base / /
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH="/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
ENV LANG=en_US.UTF-8 LANGUAGE=en_US:en LC_ALL=en_US.UTF-8
WORKDIR /app
COPY --from=build --chown=10001:10001 /layers/dependencies/ ./
COPY --from=build --chown=10001:10001 /layers/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /layers/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /layers/application/ ./
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom -Djava.awt.headless=true"
ENV SPRING_PROFILES_ACTIVE=prod
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
