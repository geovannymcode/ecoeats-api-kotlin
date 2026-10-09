package com.geovannycode.ecoeats.image.infrastructure

import com.geovannycode.ecoeats.dish.domain.DishDeleted
import com.geovannycode.ecoeats.image.application.DishImagesReplaced
import com.geovannycode.ecoeats.image.application.ImageStorage
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
internal class DishImageCleanup(
    private val storage: ImageStorage,
    private val urls: CachedPresignedUrls,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun replaced(event: DishImagesReplaced) = deleteKeys(event.image, event.thumbnail)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun deleted(event: DishDeleted) = deleteKeys(event.image, event.thumbnail)

    private fun deleteKeys(vararg references: String) {
        references.filter { it.isBucketKey() }.distinct().forEach { key ->
            urls.evict(key)
            try {
                storage.delete(key)
            } catch (failure: RuntimeException) {
                // The DB commit has already succeeded; do not report a rolled-back operation.
                logger.error("Image cleanup after commit failed: {}", failure.javaClass.simpleName)
            }
        }
    }
}
