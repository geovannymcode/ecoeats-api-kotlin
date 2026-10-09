package com.geovannycode.ecoeats.image

import com.geovannycode.ecoeats.dish.domain.DishEntity
import com.geovannycode.ecoeats.dish.domain.DishResult
import com.geovannycode.ecoeats.dish.infrastructure.DishRepository
import com.geovannycode.ecoeats.image.application.DishImageProcessor
import com.geovannycode.ecoeats.image.application.DishImageResult
import com.geovannycode.ecoeats.image.application.DishImageService
import com.geovannycode.ecoeats.image.application.DishImagesReplaced
import com.geovannycode.ecoeats.image.application.ImageStorage
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import org.springframework.mock.web.MockMultipartFile
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.multipart.MaxUploadSizeExceededException
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

internal class DishImageServiceTests {
    private val dishes = mockk<DishRepository>()
    private val storage = mockk<ImageStorage>(relaxed = true)
    private val events = mockk<ApplicationEventPublisher>(relaxed = true)
    private val service = DishImageService(dishes, storage, DishImageProcessor(), events)
    private val id = UUID.randomUUID()
    private val dish = DishEntity("Test", image = "old.jpg", thumbnail = "old-thumb.jpg")

    @Test
    fun `missing dish does not upload or remove anything`() {
        every { dishes.findByIdForUpdate(id) } returns null
        assertSame(DishImageResult.DishNotFound, service.upload(id, file(png())))
        assertSame(DishResult.NotFound, service.remove(id))
        verify { storage wasNot Called }
    }

    @Test
    fun `unsupported corrupt and oversized uploads do not change dish`() {
        every { dishes.findByIdForUpdate(id) } returns dish
        assertSame(DishImageResult.UnsupportedFormat, service.upload(id, file("text".toByteArray())))
        assertSame(DishImageResult.CorruptImage, service.upload(id, file(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47))))
        assertFailsWith<MaxUploadSizeExceededException> { service.upload(id, file(ByteArray(6 * 1024 * 1024))) }
        assertEquals("old.jpg", dish.image)
        verify { storage wasNot Called }
    }

    @Test
    fun `failed thumbnail upload compensates new objects and preserves old references`() {
        every { dishes.findByIdForUpdate(id) } returns dish
        every { storage.put(match { it.endsWith("-thumb.jpg") }, any(), "image/jpeg") } throws IllegalStateException("Storage unavailable")
        assertFailsWith<IllegalStateException> { service.upload(id, file(png())) }
        verify(exactly = 1) { storage.delete(match { it.endsWith(".jpg") && !it.endsWith("-thumb.jpg") }) }
        verify(exactly = 1) { storage.delete(match { it.endsWith("-thumb.jpg") }) }
        assertEquals("old.jpg", dish.image)
        verify(exactly = 0) {
            dishes.save(any())
            events.publishEvent(any<Any>())
        }
    }

    @Test
    fun `rollback cleans new objects while commit preserves them`() {
        every { dishes.findByIdForUpdate(id) } returns dish
        every { dishes.save(dish) } returns dish
        TransactionSynchronizationManager.initSynchronization()
        try {
            assertIs<DishImageResult.Uploaded>(service.upload(id, file(png())))
            val callback = TransactionSynchronizationManager.getSynchronizations().single()
            callback.afterCompletion(TransactionSynchronization.STATUS_COMMITTED)
            verify(exactly = 0) { storage.delete(any()) }
            callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK)
            verify {
                storage.delete(dish.image)
                storage.delete(dish.thumbnail)
            }
            verify { events.publishEvent(DishImagesReplaced("old.jpg", "old-thumb.jpg")) }
        } finally {
            TransactionSynchronizationManager.clearSynchronization()
        }
    }

    @Test
    fun `remove clears image fields and publishes old references`() {
        every { dishes.findByIdForUpdate(id) } returns dish
        every { dishes.save(dish) } returns dish
        assertSame(dish, assertIs<DishResult.Found>(service.remove(id)).dish)
        assertEquals("", dish.image)
        assertEquals("", dish.thumbnail)
        verify { events.publishEvent(DishImagesReplaced("old.jpg", "old-thumb.jpg")) }
        verify { storage wasNot Called }
    }

    private fun file(bytes: ByteArray) = MockMultipartFile("file", "anything.txt", "text/plain", bytes)
}
