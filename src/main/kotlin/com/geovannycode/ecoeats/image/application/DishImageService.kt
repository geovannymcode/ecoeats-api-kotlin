package com.geovannycode.ecoeats.image.application

import com.geovannycode.ecoeats.dish.domain.DishResult
import com.geovannycode.ecoeats.dish.infrastructure.DishRepository
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.MultipartFile
import java.util.UUID

@Service
internal class DishImageService(
    private val dishes: DishRepository,
    private val storage: ImageStorage,
    private val processor: DishImageProcessor,
    private val events: ApplicationEventPublisher,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun upload(
        dishId: UUID,
        file: MultipartFile,
    ): DishImageResult {
        val dish = dishes.findByIdForUpdate(dishId) ?: return DishImageResult.DishNotFound
        if (file.size > MAX_UPLOAD_BYTES) throw MaxUploadSizeExceededException(MAX_UPLOAD_BYTES)
        val bytes = file.inputStream.use { it.readNBytes(MAX_UPLOAD_BYTES.toInt() + 1) }
        if (bytes.size > MAX_UPLOAD_BYTES) throw MaxUploadSizeExceededException(MAX_UPLOAD_BYTES)
        if (ImageFormat.detect(bytes) == null) return DishImageResult.UnsupportedFormat
        val image = processor.process(bytes) ?: return DishImageResult.CorruptImage
        val prefix = "dishes/$dishId/${UUID.randomUUID()}"
        val imageKey = "$prefix.jpg"
        val thumbnailKey = "$prefix-thumb.jpg"
        val newKeys = listOf(imageKey, thumbnailKey)
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                object : TransactionSynchronization {
                    override fun afterCompletion(status: Int) {
                        if (status != TransactionSynchronization.STATUS_COMMITTED) newKeys.forEach(::deleteSafely)
                    }
                },
            )
        }
        try {
            storage.put(imageKey, image.original, "image/jpeg")
            storage.put(thumbnailKey, image.thumbnail, "image/jpeg")
        } catch (failure: RuntimeException) {
            // Also compensates a failed put that may already have reached the bucket.
            newKeys.forEach(::deleteSafely)
            throw failure
        }
        events.publishEvent(DishImagesReplaced(dish.image, dish.thumbnail))
        dish.image = imageKey
        dish.thumbnail = thumbnailKey
        return DishImageResult.Uploaded(dishes.save(dish))
    }

    @Transactional
    fun remove(dishId: UUID): DishResult {
        val dish = dishes.findByIdForUpdate(dishId) ?: return DishResult.NotFound
        events.publishEvent(DishImagesReplaced(dish.image, dish.thumbnail))
        dish.image = ""
        dish.thumbnail = ""
        return DishResult.Found(dishes.save(dish))
    }

    private fun deleteSafely(key: String) {
        try {
            storage.delete(key)
        } catch (failure: RuntimeException) {
            logger.error("Image rollback cleanup failed: {}", failure.javaClass.simpleName)
        }
    }

    companion object {
        private const val MAX_UPLOAD_BYTES = 5L * 1024 * 1024
    }
}
