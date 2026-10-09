package com.geovannycode.ecoeats.image

import com.geovannycode.ecoeats.TestcontainersConfiguration
import com.geovannycode.ecoeats.auth.domain.Role
import com.geovannycode.ecoeats.auth.domain.UserEntity
import com.geovannycode.ecoeats.auth.infrastructure.UserRepository
import com.geovannycode.ecoeats.dish.domain.DishEntity
import com.geovannycode.ecoeats.dish.infrastructure.DishRepository
import com.geovannycode.ecoeats.image.application.DishImageResult
import com.geovannycode.ecoeats.image.application.DishImageService
import com.geovannycode.ecoeats.image.infrastructure.StorageProperties
import com.geovannycode.ecoeats.shared.security.TokenIssuer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import software.amazon.awssdk.services.s3.S3Client
import tools.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "spring.docker.compose.enabled=false", "ecoeats.jwt.secret=test-only-secret-with-at-least-32-bytes",
        "ecoeats.mail.resend.api-key=test-only", "ecoeats.mail.resend.from-email=test@example.com", "ecoeats.seed.admin-password=",
    ],
)
@AutoConfigureMockMvc
internal class DishImageIntegrationTests {
    @Autowired private lateinit var mvc: MockMvc

    @Autowired private lateinit var mapper: ObjectMapper

    @Autowired private lateinit var users: UserRepository

    @Autowired private lateinit var dishes: DishRepository

    @Autowired private lateinit var issuer: TokenIssuer

    @Autowired private lateinit var s3: S3Client

    @Autowired private lateinit var properties: StorageProperties

    @Autowired private lateinit var imageService: DishImageService

    @Autowired private lateinit var transactions: PlatformTransactionManager
    private lateinit var admin: String
    private lateinit var user: String
    private lateinit var id: UUID

    @BeforeEach
    fun setUp() {
        users.deleteAll()
        val adminUser = users.saveAndFlush(UserEntity("image-admin", "Image", "Admin", "admin@example.com", "hash", Role.ADMIN))
        val normalUser = users.saveAndFlush(UserEntity("image-user", "Image", "User", "user@example.com", "hash"))
        admin = issuer.issue(assertNotNull(adminUser.id), adminUser.email, adminUser.role)
        user = issuer.issue(assertNotNull(normalUser.id), normalUser.email, normalUser.role)
        id = assertNotNull(dishes.saveAndFlush(DishEntity("Image integration test")).id)
    }

    @Test
    fun `PNG upload downloads signed JPEGs and replacement and dish deletion clean objects`() {
        val first = upload(png(1600, 800))
        assertEquals(200, first.response.status)
        assertEquals("Imagen actualizada correctamente", mapper.readTree(first.response.contentAsString).get("message").asString())
        val firstKeys = keys()
        assertEquals(2, firstKeys.size)
        assertTrue(firstKeys.all { it.startsWith("dishes/$id/") && it.endsWith(".jpg") })
        val read = mvc.perform(get("/api/dish/$id").header("Authorization", "Bearer $user")).andReturn()
        val body = mapper.readTree(read.response.contentAsString)
        val originalUrl = body.at("/data/image").asString()
        val thumbUrl = body.at("/data/thumbails").asString()
        assertEquals(originalUrl, mapper.readTree(first.response.contentAsString).at("/data/image").asString())
        assertImage(originalUrl, 1200, 600)
        assertImage(thumbUrl, 300, 150)
        assertEquals(200, upload(png()).response.status)
        val replaced = keys()
        assertEquals(2, replaced.size)
        assertTrue(firstKeys.intersect(replaced.toSet()).isEmpty())
        assertEquals(
            200,
            mvc
                .perform(delete("/api/dish/$id").header("Authorization", "Bearer $admin"))
                .andReturn()
                .response.status,
        )
        assertTrue(keys().isEmpty())
    }

    @Test
    fun `rollback removes new objects and preserves committed image references`() {
        assertEquals(200, upload(png()).response.status)
        val old = dishes.findById(id).orElseThrow()
        val previous = keys().toSet()
        TransactionTemplate(transactions).executeWithoutResult { transaction ->
            assertIs<DishImageResult.Uploaded>(imageService.upload(id, MockMultipartFile("file", png())))
            assertEquals(4, keys().size)
            transaction.setRollbackOnly()
        }
        val restored = dishes.findById(id).orElseThrow()
        assertEquals(old.image, restored.image)
        assertEquals(old.thumbnail, restored.thumbnail)
        assertEquals(previous, keys().toSet())
        mvc.perform(delete("/api/dish/$id").header("Authorization", "Bearer $admin")).andReturn()
    }

    @Test
    fun `remove endpoint clears fields and deletes objects without deleting dish`() {
        assertEquals(200, upload(png()).response.status)
        val result = mvc.perform(delete("/api/dish/$id/image").header("Authorization", "Bearer $admin")).andReturn()
        assertEquals(200, result.response.status)
        assertEquals("", mapper.readTree(result.response.contentAsString).at("/data/image").asString())
        assertEquals("", mapper.readTree(result.response.contentAsString).at("/data/thumbails").asString())
        assertTrue(dishes.existsById(id))
        assertTrue(keys().isEmpty())
        assertEquals(
            200,
            mvc
                .perform(delete("/api/dish/$id/image").header("Authorization", "Bearer $admin"))
                .andReturn()
                .response.status,
        )
    }

    @Test
    fun `renamed text is rejected and corrupted PNG returns 422`() {
        error(upload("text renamed to PNG".toByteArray()), 415, "Formato no soportado. Usa JPEG, PNG o WebP")
        error(upload(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)), 422, "La imagen está dañada")
        assertTrue(keys().isEmpty())
    }

    @Test
    fun `six MB returns 413 even through MockMvc without servlet size checks`() {
        error(upload(ByteArray(6 * 1024 * 1024)), 413, "La imagen supera el tamaño máximo de 5 MB")
        assertTrue(keys().isEmpty())
    }

    @Test
    fun `USER and anonymous cannot manage images`() {
        error(upload(png(), user), 403, "Acceso denegado")
        error(mvc.perform(delete("/api/dish/$id/image").header("Authorization", "Bearer $user")).andReturn(), 403, "Acceso denegado")
        val anonymous = mvc.perform(multipart("/api/dish/$id/image").file(MockMultipartFile("file", png()))).andReturn()
        error(anonymous, 401, "No autorizado")
        assertTrue(keys().isEmpty())
    }

    @Test
    fun `unknown dish and missing file return expected errors`() {
        val missing = UUID.randomUUID()
        error(
            mvc
                .perform(
                    multipart("/api/dish/$missing/image")
                        .file(MockMultipartFile("file", png()))
                        .header("Authorization", "Bearer $admin"),
                ).andReturn(),
            404,
            "Plato no encontrado",
        )
        error(
            mvc.perform(delete("/api/dish/$missing/image").header("Authorization", "Bearer $admin")).andReturn(),
            404,
            "Plato no encontrado",
        )
        error(
            mvc.perform(multipart("/api/dish/$id/image").header("Authorization", "Bearer $admin")).andReturn(),
            400,
            "Falta el archivo de imagen",
        )
    }

    private fun upload(
        bytes: ByteArray,
        token: String = admin,
    ): MvcResult =
        mvc
            .perform(
                multipart("/api/dish/$id/image")
                    .file(MockMultipartFile("file", "renamed.png", "image/png", bytes))
                    .header("Authorization", "Bearer $token"),
            ).andReturn()

    private fun keys(): List<String> = s3.listObjectsV2 { it.bucket(properties.bucket).prefix("dishes/$id/") }.contents().map { it.key() }

    private fun error(
        result: MvcResult,
        status: Int,
        message: String,
    ) {
        assertEquals(status, result.response.status)
        val node = mapper.readTree(result.response.contentAsString)
        assertFalse(node.get("success").asBoolean())
        assertEquals(message, node.get("message").asString())
        assertTrue(node.get("data").isNull)
        assertNotNull(node.get("timestamp"))
    }

    private fun assertImage(
        url: String,
        width: Int,
        height: Int,
    ) {
        HttpClient.newHttpClient().use { client ->
            val response = client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(), HttpResponse.BodyHandlers.ofByteArray())
            assertEquals(200, response.statusCode())
            assertEquals("image/jpeg", response.headers().firstValue("content-type").orElseThrow())
            val decoded = response.body().inputStream().use { ImageIO.read(it) }
            assertEquals(width, decoded.width)
            assertEquals(height, decoded.height)
        }
    }
}
