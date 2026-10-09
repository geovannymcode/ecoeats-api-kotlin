plugins {
    jacoco
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.dependency.management)
}

group = "com.geovannycode"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.bucket4j:bucket4j_jdk17-core:8.20.0")
    implementation("com.github.ben-manes.caffeine:caffeine")
    implementation(platform("software.amazon.awssdk:bom:2.55.13"))
    implementation("software.amazon.awssdk:s3") {
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
        exclude(group = "software.amazon.awssdk", module = "apache-client")
    }
    implementation("software.amazon.awssdk:url-connection-client")
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.15.2")
    implementation("org.springframework.boot:spring-boot-starter-restclient")
    testImplementation("org.springframework.boot:spring-boot-starter-restclient-test")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    testImplementation(libs.mockk)
    testImplementation(libs.springmockk)
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation(libs.springdoc)
    implementation(libs.jackson.kotlin)
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    implementation("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-flyway-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

springBoot {
    buildInfo()
    mainClass.set("com.geovannycode.ecoeats.EcoEatsApplicationKt")
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.named<Test>("test") {
    inputs.file(layout.projectDirectory.file("docs/openapi.yaml"))
}

tasks.register<Test>("generateOpenApi") {
    group = "documentation"
    description = "Regenerates docs/openapi.yaml from the app running with Testcontainers."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter { includeTestsMatching("com.geovannycode.ecoeats.documentation.OpenApiContractTests") }
    systemProperty("ecoeats.updateOpenApi", "true")
    outputs.file(layout.projectDirectory.file("docs/openapi.yaml"))
    outputs.upToDateWhen { false }
}

jacoco { toolVersion = "0.8.14" }

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            element = "PACKAGE"
            includes = listOf("*.application", "*.domain")
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check { dependsOn(tasks.jacocoTestReport, tasks.jacocoTestCoverageVerification) }

// Keep ktlint's embedded parser isolated from Spring's Kotlin dependency management.
gradle.projectsEvaluated {
    configurations.named("ktlint") {
        resolutionStrategy.eachDependency {
            if (requested.group == "org.jetbrains.kotlin") useVersion("2.1.0")
        }
    }
}
