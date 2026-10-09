package com.geovannycode.ecoeats.image.infrastructure

import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.HealthIndicator
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import java.time.Duration

@Component("bucketHealthIndicator")
internal class BucketHealthIndicator(
    private val client: S3Client,
    private val properties: StorageProperties,
) : HealthIndicator {
    override fun health(): Health =
        try {
            client.headBucket(
                HeadBucketRequest
                    .builder()
                    .bucket(properties.bucket)
                    .overrideConfiguration { config ->
                        config.apiCallTimeout(Duration.ofSeconds(2)).apiCallAttemptTimeout(Duration.ofSeconds(1))
                    }.build(),
            )
            Health.up().build()
        } catch (_: RuntimeException) {
            // No SDK exception details: they can contain request signatures or endpoints.
            Health.down().build()
        }
}
