package com.geovannycode.ecoeats.image.infrastructure

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.S3Configuration
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.time.Duration

@Configuration(proxyBeanMethods = false)
internal class StorageConfiguration {
    @Bean
    fun s3Client(properties: StorageProperties): S3Client =
        S3Client
            .builder()
            .endpointOverride(properties.endpoint)
            .region(Region.of(properties.region))
            .credentialsProvider(properties.credentials())
            .serviceConfiguration(properties.serviceConfiguration())
            .httpClientBuilder(
                UrlConnectionHttpClient
                    .builder()
                    .connectionTimeout(Duration.ofSeconds(5))
                    .socketTimeout(Duration.ofSeconds(30)),
            ).build()

    @Bean
    fun s3Presigner(properties: StorageProperties): S3Presigner =
        S3Presigner
            .builder()
            .endpointOverride(properties.endpoint)
            .region(Region.of(properties.region))
            .credentialsProvider(properties.credentials())
            .serviceConfiguration(properties.serviceConfiguration())
            .build()
}

private fun StorageProperties.credentials(): StaticCredentialsProvider =
    StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKeyId, secretAccessKey))

private fun StorageProperties.serviceConfiguration(): S3Configuration =
    S3Configuration.builder().pathStyleAccessEnabled(forcePathStyle).build()
