package com.geovannycode.ecoeats.image.infrastructure

import com.geovannycode.ecoeats.image.application.ImageUrlResolver
import org.springframework.stereotype.Component

@Component
internal class BucketImageUrlResolver(
    private val urls: CachedPresignedUrls,
) : ImageUrlResolver {
    override fun resolve(stored: String): String = if (stored.isBucketKey()) urls.resolve(stored) else stored
}

internal fun String.isBucketKey(): Boolean =
    isNotEmpty() && !startsWith("http://", ignoreCase = true) && !startsWith("https://", ignoreCase = true)
