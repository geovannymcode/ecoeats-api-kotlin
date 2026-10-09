package com.geovannycode.ecoeats

import com.github.dockerjava.api.exception.NotFoundException
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import java.net.URI
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    @Bean
    fun independentRequestAddresses(): org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer =
        org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer { builder ->
            val counter =
                java.util.concurrent.atomic
                    .AtomicInteger()
            builder.defaultRequest(
                org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                    .get("/")
                    .with { request ->
                        request.remoteAddr = "test-${counter.incrementAndGet()}"
                        request
                    },
            )
        }

    @Bean
    fun storageTestProperties(): DynamicPropertyRegistrar {
        val endpoint = TestStorage.endpoint
        return DynamicPropertyRegistrar { registry ->
            registry.add("ecoeats.storage.bucket") { "ecoeats-test" }
            registry.add("ecoeats.storage.endpoint") { endpoint }
            registry.add("ecoeats.storage.region") { "us-east-1" }
            registry.add("ecoeats.storage.access-key-id") { "minioadmin" }
            registry.add("ecoeats.storage.secret-access-key") { "minioadmin" }
            registry.add("ecoeats.storage.force-path-style") { "true" }
            registry.add("ecoeats.storage.url-ttl") { "24h" }
        }
    }

    @Bean
    @ServiceConnection
    fun postgresContainer(): PostgreSQLContainer = PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"))
}

private class TestMinio : GenericContainer<TestMinio>(minioImage())

private fun minioImage(): Future<String> {
    val name = "ecoeats-minio:RELEASE.2025-10-15T17-29-55Z"
    return try {
        DockerClientFactory
            .instance()
            .client()
            .inspectImageCmd(name)
            .exec()
        CompletableFuture.completedFuture(name)
    } catch (_: NotFoundException) {
        ImageFromDockerfile(name, false).withFileFromPath("Dockerfile", Path.of("docker/minio/Dockerfile"))
    }
}

private object TestStorage {
    private val minio =
        TestMinio()
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server", "/data")
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000).withStartupTimeout(Duration.ofMinutes(3)))
            .apply { start() }
    val endpoint: String = "http://${minio.host}:${minio.getMappedPort(9000)}"

    init {
        S3Client
            .builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("minioadmin", "minioadmin")))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .build()
            .use { client ->
                client.createBucket { it.bucket("ecoeats-test") }
            }
    }
}
