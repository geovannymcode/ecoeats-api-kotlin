package com.geovannycode.ecoeats.dish

import com.geovannycode.ecoeats.TestcontainersConfiguration
import com.geovannycode.ecoeats.auth.domain.Role
import com.geovannycode.ecoeats.auth.domain.UserEntity
import com.geovannycode.ecoeats.auth.infrastructure.UserRepository
import com.geovannycode.ecoeats.dish.domain.DishDeleted
import com.geovannycode.ecoeats.dish.infrastructure.DishRepository
import com.geovannycode.ecoeats.shared.security.TokenIssuer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.test.context.event.ApplicationEvents
import org.springframework.test.context.event.RecordApplicationEvents
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "spring.docker.compose.enabled=false",
        "ecoeats.jwt.secret=test-only-secret-with-at-least-32-bytes",
        "ecoeats.mail.resend.api-key=test-only",
        "ecoeats.mail.resend.from-email=test@example.com",
        "ecoeats.seed.admin-password=",
    ],
)
@AutoConfigureMockMvc
@RecordApplicationEvents
internal class DishIntegrationTests {
    @Autowired private lateinit var mvc: MockMvc

    @Autowired private lateinit var mapper: ObjectMapper

    @Autowired private lateinit var users: UserRepository

    @Autowired private lateinit var dishes: DishRepository

    @Autowired private lateinit var issuer: TokenIssuer

    @Autowired private lateinit var events: ApplicationEvents
    private lateinit var userToken: String
    private lateinit var adminToken: String

    @BeforeEach
    fun setUp() {
        dishes.findAll().filter { it.name.startsWith("CRUD test") }.forEach { dishes.delete(it) }
        users.deleteAll()
        val user = users.saveAndFlush(UserEntity("100", "Test", "User", "user@example.com", "test-hash"))
        val admin = users.saveAndFlush(UserEntity("101", "Test", "Admin", "admin@example.com", "test-hash", Role.ADMIN))
        userToken = issuer.issue(assertNotNull(user.id), user.email, user.role)
        adminToken = issuer.issue(assertNotNull(admin.id), admin.email, admin.role)
    }

    @Test
    fun `list matches all seed fields and numeric JSON contract`() {
        val result = mvc.perform(get("/api/dish").header("Authorization", "Bearer $userToken")).andReturn()
        assertEquals(200, result.response.status)
        val actual = mapper.readTree(result.response.contentAsString)
        assertEquals(setOf("success", "message", "data"), actual.properties().map { it.key }.toSet())
        assertTrue(actual.get("success").asBoolean())
        assertEquals("OK", actual.get("message").asString())
        val rows = dishes.findAllByOrderByNameAsc()
        val json = actual.get("data")
        assertEquals(12, json.size())
        val fields =
            setOf(
                "id",
                "name",
                "description",
                "image",
                "thumbails",
                "price",
                "carbohydrates",
                "proteins",
                "rating",
                "ingredients",
                "flagHeader",
            )
        rows.forEachIndexed { index, dish ->
            val node = json.get(index)
            assertEquals(fields, node.properties().map { it.key }.toSet())
            assertEquals(dish.id.toString(), node.get("id").asString())
            assertEquals(dish.name, node.get("name").asString())
            assertEquals(dish.description, node.get("description").asString())
            assertEquals(dish.image, node.get("image").asString())
            assertEquals(dish.thumbnail, node.get("thumbails").asString())
            assertTrue(node.get("price").isNumber)
            assertTrue(node.get("rating").isNumber)
            assertEquals(0, dish.price.compareTo(node.get("price").decimalValue()))
            assertEquals(0, dish.rating.compareTo(node.get("rating").decimalValue()))
            assertTrue(node.get("carbohydrates").isIntegralNumber)
            assertTrue(node.get("proteins").isIntegralNumber)
            assertEquals(dish.carbohydrates, node.get("carbohydrates").asInt())
            assertEquals(dish.proteins, node.get("proteins").asInt())
            assertEquals(dish.ingredients, node.get("ingredients").asString())
            assertEquals(dish.flagHeader, node.get("flagHeader").asBoolean())
        }
        val single = mvc.perform(get("/api/dish/${rows.first().id}").header("Authorization", "Bearer $userToken")).andReturn()
        assertEquals(200, single.response.status)
        assertEquals(json.get(0), mapper.readTree(single.response.contentAsString).get("data"))
    }

    @Test
    fun `unknown dish returns 404 and malformed UUID returns 400`() {
        assertError(
            mvc.perform(get("/api/dish/${UUID.randomUUID()}").header("Authorization", "Bearer $userToken")).andReturn(),
            404,
            "Plato no encontrado",
        )
        assertError(
            mvc.perform(get("/api/dish/not-a-uuid").header("Authorization", "Bearer $userToken")).andReturn(),
            400,
            "Parámetro de la petición inválido",
        )
    }

    @Test
    fun `anonymous read returns 401 envelope`() {
        assertError(mvc.perform(get("/api/dish")).andReturn(), 401, "No autorizado")
    }

    @ParameterizedTest
    @ValueSource(strings = ["POST", "PUT", "DELETE"])
    fun `USER cannot mutate dishes`(method: String) {
        val path = if (method == "POST") "/api/dish" else "/api/dish/${UUID.randomUUID()}"
        assertError(
            mvc
                .perform(
                    request(HttpMethod.valueOf(method), path)
                        .header("Authorization", "Bearer $userToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()),
                ).andReturn(),
            403,
            "Acceso denegado",
        )
        assertEquals(12, dishes.count())
    }

    @Test
    fun `ADMIN completes CRUD and deletion publishes stored image references`() {
        val created =
            mvc
                .perform(
                    post("/api/dish")
                        .header("Authorization", "Bearer $adminToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()),
                ).andReturn()
        assertEquals(201, created.response.status)
        val response = mapper.readTree(created.response.contentAsString)
        assertEquals("Plato creado correctamente", response.get("message").asString())
        val id = UUID.fromString(response.at("/data/id").asString())
        assertEquals("", response.at("/data/image").asString())
        assertEquals("", response.at("/data/thumbails").asString())
        assertFalse(response.at("/data/flagHeader").asBoolean())
        assertEquals(13, dishes.count())
        val stored = dishes.findById(id).orElseThrow()
        stored.image = "bucket/image"
        stored.thumbnail = "https://example.com/thumb"
        dishes.saveAndFlush(stored)
        val updated =
            mvc
                .perform(
                    put("/api/dish/$id")
                        .header("Authorization", "Bearer $adminToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("CRUD test updated", true)),
                ).andReturn()
        assertEquals(200, updated.response.status)
        val updatedJson = mapper.readTree(updated.response.contentAsString)
        assertEquals("Plato actualizado correctamente", updatedJson.get("message").asString())
        assertEquals("CRUD test updated", updatedJson.at("/data/name").asString())
        assertTrue(updatedJson.at("/data/flagHeader").asBoolean())
        assertTrue(updatedJson.at("/data/image").asString().contains("/ecoeats-test/bucket/image?"))
        assertEquals("https://example.com/thumb", updatedJson.at("/data/thumbails").asString())
        val deleted = mvc.perform(delete("/api/dish/$id").header("Authorization", "Bearer $adminToken")).andReturn()
        assertEquals(200, deleted.response.status)
        assertEquals(
            mapper.readTree("""{"success":true,"message":"Plato eliminado correctamente","data":null}"""),
            mapper.readTree(deleted.response.contentAsString),
        )
        assertFalse(dishes.existsById(id))
        val event = events.stream(DishDeleted::class.java).toList().single()
        assertEquals(DishDeleted(id, "bucket/image", "https://example.com/thumb"), event)
        assertEquals(12, dishes.count())
        assertError(
            mvc.perform(delete("/api/dish/$id").header("Authorization", "Bearer $adminToken")).andReturn(),
            404,
            "Plato no encontrado",
        )
        assertError(
            mvc
                .perform(
                    put("/api/dish/$id")
                        .header("Authorization", "Bearer $adminToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload()),
                ).andReturn(),
            404,
            "Plato no encontrado",
        )
    }

    @Test
    fun `invalid dish values return Spanish errors per field`() {
        val invalid =
            mapOf(
                "name" to " ",
                "description" to "",
                "price" to -1,
                "carbohydrates" to -1,
                "proteins" to -1,
                "rating" to 6,
                "ingredients" to "",
            )
        val result =
            mvc
                .perform(
                    post("/api/dish")
                        .header("Authorization", "Bearer $adminToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(invalid)),
                ).andReturn()
        assertEquals(400, result.response.status)
        val actual = mapper.readTree(result.response.contentAsString)
        assertEquals("Datos de entrada inválidos", actual.get("message").asString())
        assertEquals(
            setOf("name", "price", "carbohydrates", "proteins", "rating"),
            actual
                .get("errors")
                .toList()
                .map { it.get("field").asString() }
                .toSet(),
        )
        assertEquals(12, dishes.count())
    }

    private fun payload(
        name: String = "CRUD test dish",
        header: Boolean? = null,
    ): String {
        val body =
            mutableMapOf<String, Any>(
                "name" to name,
                "description" to "Description",
                "price" to BigDecimal("12.34"),
                "carbohydrates" to 10,
                "proteins" to 20,
                "rating" to BigDecimal("4.5"),
                "ingredients" to "Rice",
            )
        header?.let { body["flagHeader"] = it }
        return mapper.writeValueAsString(body)
    }

    private fun assertError(
        result: MvcResult,
        status: Int,
        message: String,
    ) {
        assertEquals(status, result.response.status)
        val actual = mapper.readTree(result.response.contentAsString)
        val timestamp = actual.get("timestamp").asString()
        Instant.parse(timestamp)
        assertEquals(mapper.readTree("""{"success":false,"message":"$message","data":null,"timestamp":"$timestamp"}"""), actual)
    }
}
