package com.geovannycode.ecoeats.dish

import com.geovannycode.ecoeats.dish.api.toResponse
import com.geovannycode.ecoeats.dish.application.DishChanges
import com.geovannycode.ecoeats.dish.application.DishService
import com.geovannycode.ecoeats.dish.domain.DishDeleted
import com.geovannycode.ecoeats.dish.domain.DishDeletionResult
import com.geovannycode.ecoeats.dish.domain.DishEntity
import com.geovannycode.ecoeats.dish.domain.DishResult
import com.geovannycode.ecoeats.dish.infrastructure.DishRepository
import com.geovannycode.ecoeats.image.application.ImageUrlResolver
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import java.math.BigDecimal
import java.util.Optional
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal class DishServiceTests {
    private val repository = mockk<DishRepository>()
    private val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    private val service = DishService(repository, publisher)
    private val id = UUID.randomUUID()
    private val changes = DishChanges("New", "Description", BigDecimal("12.34"), 10, 20, BigDecimal("4.5"), "Rice", true)

    @Test
    fun `list uses repository ordering`() {
        val ordered = listOf(DishEntity("A"), DishEntity("Z"))
        every { repository.findAllByOrderByNameAsc() } returns ordered
        assertSame(ordered, service.list())
    }

    @Test
    fun `find returns expected result for existing and missing dishes`() {
        val entity = DishEntity("Existing")
        every { repository.findById(id) } returns Optional.of(entity)
        assertSame(entity, assertIs<DishResult.Found>(service.find(id)).dish)
        every { repository.findById(id) } returns Optional.empty()
        assertSame(DishResult.NotFound, service.find(id))
    }

    @Test
    fun `create initializes editable fields and empty image values`() {
        every { repository.save(any()) } answers { firstArg() }
        val created = service.create(changes)
        assertEquals(changes.name, created.name)
        assertEquals(changes.price, created.price)
        assertEquals(changes.description, created.description)
        assertEquals(changes.carbohydrates, created.carbohydrates)
        assertEquals(changes.proteins, created.proteins)
        assertEquals(changes.rating, created.rating)
        assertEquals(changes.ingredients, created.ingredients)
        assertTrue(created.flagHeader)
        assertEquals("", created.image)
        assertEquals("", created.thumbnail)
    }

    @Test
    fun `update preserves stored images`() {
        val existing = DishEntity("Old", image = "bucket/image", thumbnail = "https://example.com/thumb")
        every { repository.findByIdForUpdate(id) } returns existing
        every { repository.save(existing) } returns existing
        assertSame(existing, assertIs<DishResult.Found>(service.update(id, changes)).dish)
        assertEquals("New", existing.name)
        assertEquals(changes.description, existing.description)
        assertEquals(changes.price, existing.price)
        assertEquals(changes.rating, existing.rating)
        assertEquals(changes.carbohydrates, existing.carbohydrates)
        assertEquals(changes.proteins, existing.proteins)
        assertEquals(changes.ingredients, existing.ingredients)
        assertEquals(changes.flagHeader, existing.flagHeader)
        assertEquals("bucket/image", existing.image)
        assertEquals("https://example.com/thumb", existing.thumbnail)
    }

    @Test
    fun `missing update and delete have no side effects`() {
        every { repository.findByIdForUpdate(id) } returns null
        assertSame(DishResult.NotFound, service.update(id, changes))
        assertSame(DishDeletionResult.NotFound, service.delete(id))
        verify(exactly = 0) {
            repository.save(any())
            repository.delete(any())
            publisher.publishEvent(any<Any>())
        }
    }

    @Test
    fun `delete publishes image references with dish identity`() {
        val entity = mockk<DishEntity>()
        every { entity.id } returns id
        every { entity.image } returns "image-key"
        every { entity.thumbnail } returns "thumbnail-key"
        every { repository.findByIdForUpdate(id) } returns entity
        every { repository.delete(entity) } just Runs
        assertSame(DishDeletionResult.Deleted, service.delete(id))
        verifyOrder {
            repository.delete(entity)
            publisher.publishEvent(DishDeleted(id, "image-key", "thumbnail-key"))
        }
    }

    @Test
    fun `response mapper resolves both stored image values`() {
        val entity = mockk<DishEntity>(relaxed = true)
        every { entity.id } returns id
        every { entity.price } returns BigDecimal("12.34")
        every { entity.rating } returns BigDecimal("4.5")
        every { entity.image } returns "image-key"
        every { entity.thumbnail } returns "thumb-key"
        val resolver = mockk<ImageUrlResolver>()
        every { resolver.resolve("image-key") } returns "https://example.com/image"
        every { resolver.resolve("thumb-key") } returns "https://example.com/thumb"
        val response = entity.toResponse(resolver)
        assertEquals("https://example.com/image", response.image)
        assertEquals("https://example.com/thumb", response.thumbnail)
        assertEquals(BigDecimal("12.34"), response.price)
    }
}
