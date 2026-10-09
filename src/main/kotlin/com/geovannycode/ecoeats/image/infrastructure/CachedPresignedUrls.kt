package com.geovannycode.ecoeats.image.infrastructure

import com.geovannycode.ecoeats.image.application.ImageStorage
import org.springframework.stereotype.Component
import java.time.Clock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

@Component
internal class CachedPresignedUrls(
    private val storage: ImageStorage,
    private val properties: StorageProperties,
    private val clock: Clock,
) {
    private data class Entry(
        val url: String,
        val expiresAt: Instant,
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    fun resolve(key: String): String {
        val now = clock.instant()
        // Remove unused expired entries as well as refreshing frequently requested URLs.
        entries.entries.removeIf { !it.value.expiresAt.isAfter(now) }
        return checkNotNull(
            entries.compute(key) { _, existing ->
                if (existing != null && existing.expiresAt.isAfter(now.plus(properties.urlTtl.dividedBy(5)))) {
                    existing
                } else {
                    Entry(storage.presignedGetUrl(key), now.plus(properties.urlTtl))
                }
            },
        ).url
    }

    fun evict(key: String) {
        entries.remove(key)
    }
}
