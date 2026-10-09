package com.geovannycode.ecoeats.image.infrastructure

import org.springframework.boot.context.properties.ConfigurationProperties
import java.net.URI
import java.time.Duration

@ConfigurationProperties("ecoeats.storage")
internal data class StorageProperties(
    val bucket: String,
    val endpoint: URI,
    val accessKeyId: String,
    val secretAccessKey: String,
    val region: String = "auto",
    val forcePathStyle: Boolean = false,
    val urlTtl: Duration = Duration.ofHours(24),
) {
    init {
        require(bucket.isNotBlank()) { "Storage bucket must not be blank" }
        require(endpoint.scheme in setOf("http", "https") && endpoint.host != null) { "Storage endpoint must be an absolute HTTP URL" }
        require(accessKeyId.isNotBlank() && secretAccessKey.isNotBlank()) { "Storage credentials must not be blank" }
        require(!urlTtl.isNegative && !urlTtl.isZero && urlTtl <= Duration.ofDays(7)) {
            "Image URL TTL must be greater than zero and at most seven days"
        }
    }
}
