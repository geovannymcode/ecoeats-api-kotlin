package com.geovannycode.ecoeats.image

import com.geovannycode.ecoeats.image.application.DishImageProcessor
import com.geovannycode.ecoeats.image.application.ImageFormat
import com.geovannycode.ecoeats.image.application.ImageStorage
import com.geovannycode.ecoeats.image.infrastructure.BucketImageUrlResolver
import com.geovannycode.ecoeats.image.infrastructure.CachedPresignedUrls
import com.geovannycode.ecoeats.image.infrastructure.StorageProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

internal class ImageUnitTests {
    private val storage = mockk<ImageStorage>()
    private val clock = mockk<Clock>()
    private val start = Instant.parse("2026-10-09T00:00:00Z")
    private val properties =
        StorageProperties("test", URI.create("http://localhost:9000"), "access", "secret", urlTtl = Duration.ofHours(24))

    @Test
    fun `formats use magic bytes rather than filenames or MIME types`() {
        assertEquals(ImageFormat.JPEG, ImageFormat.detect(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())))
        assertEquals(ImageFormat.PNG, ImageFormat.detect(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)))
        assertEquals(ImageFormat.WEBP, ImageFormat.detect("RIFFxxxxWEBP".toByteArray()))
        listOf(byteArrayOf(), "text".toByteArray(), "RIFFxxxxWAV".toByteArray(), byteArrayOf(0xff.toByte(), 0xd8.toByte()))
            .forEach { assertNull(ImageFormat.detect(it)) }
    }

    @Test
    fun `URLs are stable until remaining TTL reaches twenty percent`() {
        every { clock.instant() } returns start
        every { storage.presignedGetUrl("key") } returnsMany listOf("url-1", "url-2", "url-3")
        val cache = CachedPresignedUrls(storage, properties, clock)
        assertEquals("url-1", cache.resolve("key"))
        every { clock.instant() } returns start.plus(Duration.ofHours(19))
        assertEquals("url-1", cache.resolve("key"))
        every { clock.instant() } returns start.plus(Duration.ofMinutes(1152))
        assertEquals("url-2", cache.resolve("key"))
        cache.evict("key")
        assertEquals("url-3", cache.resolve("key"))
        verify(exactly = 3) { storage.presignedGetUrl("key") }
    }

    @Test
    fun `resolver preserves empty and absolute URLs and signs keys`() {
        val cache = mockk<CachedPresignedUrls>()
        every { cache.resolve("dishes/key.jpg") } returns "signed"
        val resolver = BucketImageUrlResolver(cache)
        listOf("", "https://images.unsplash.com/photo", "http://example.com/image", "HTTPS://example.com/image")
            .forEach { assertEquals(it, resolver.resolve(it)) }
        assertEquals("signed", resolver.resolve("dishes/key.jpg"))
        verify(exactly = 1) { cache.resolve(any()) }
    }

    @Test
    fun `processor downsizes to JPEG with proportional thumbnail and no upscaling`() {
        val processor = DishImageProcessor()
        val result = assertNotNull(processor.process(png(1600, 800)))
        val original = ImageIO.read(result.original.inputStream())
        val thumbnail = ImageIO.read(result.thumbnail.inputStream())
        assertEquals(1200, original.width)
        assertEquals(600, original.height)
        assertEquals(300, thumbnail.width)
        assertEquals(150, thumbnail.height)
        assertEquals(ImageFormat.JPEG, ImageFormat.detect(result.original))
        assertEquals(ImageFormat.JPEG, ImageFormat.detect(result.thumbnail))
        val small = assertNotNull(processor.process(png(100, 50)))
        assertEquals(100, ImageIO.read(small.original.inputStream()).width)
        assertEquals(100, ImageIO.read(small.thumbnail.inputStream()).width)
        assertNull(processor.process(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)))
    }

    @Test
    fun `storage rejects invalid signing lifetimes`() {
        assertFailsWith<IllegalArgumentException> { properties.copy(urlTtl = Duration.ZERO) }
        assertFailsWith<IllegalArgumentException> { properties.copy(urlTtl = Duration.ofDays(8)) }
    }
}

internal fun png(
    width: Int = 400,
    height: Int = 200,
): ByteArray =
    ByteArrayOutputStream().use { output ->
        ImageIO.write(BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB), "png", output)
        output.toByteArray()
    }
