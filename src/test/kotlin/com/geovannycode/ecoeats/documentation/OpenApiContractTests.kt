package com.geovannycode.ecoeats.documentation

import com.geovannycode.ecoeats.TestcontainersConfiguration
import com.geovannycode.ecoeats.shared.config.OpenApiConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.Yaml
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "spring.docker.compose.enabled=false", "ecoeats.jwt.secret=test-only-secret-with-at-least-32-bytes",
        "ecoeats.mail.resend.api-key=test-only", "ecoeats.mail.resend.from-email=test@example.com",
        "ecoeats.seed.admin-password=", "ecoeats.public-url=", "SPRINGDOC_ENABLED=true",
    ],
)
@AutoConfigureMockMvc
internal class OpenApiContractTests {
    @Autowired private lateinit var mvc: MockMvc

    @Autowired private lateinit var mapper: ObjectMapper

    @Autowired private lateinit var build: BuildProperties

    @Test
    fun `generated OpenAPI matches versioned contract`() {
        val response = mvc.perform(get("/v3/api-docs")).andReturn().response
        assertEquals(200, response.status)
        val generated = mapper.readValue(response.contentAsString, Map::class.java)
        val snapshot = Path.of("docs/openapi.yaml")
        if (java.lang.Boolean.getBoolean("ecoeats.updateOpenApi")) {
            Files.createDirectories(snapshot.parent)
            val options =
                DumperOptions().apply {
                    defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
                    width = 120
                }
            Files.writeString(snapshot, Yaml(options).dump(generated.sortedContract()))
        }
        assertTrue(Files.exists(snapshot), "Run ./gradlew generateOpenApi to create the reviewed contract")
        val versioned = Yaml().load<Any>(Files.readString(snapshot))
        assertEquals(
            mapper.valueToTree<JsonNode>(versioned),
            mapper.valueToTree<JsonNode>(generated),
            "OpenAPI changed. Review the HTTP contract, then regenerate with ./gradlew generateOpenApi",
        )
    }

    @Test
    fun `all operations expose concrete envelopes security and upload selector`() {
        val result = mvc.perform(get("/v3/api-docs")).andReturn()
        assertEquals(200, result.response.status)
        val api = mapper.readTree(result.response.contentAsString)
        assertEquals("EcoEats API", api.at("/info/title").asString())
        assertEquals(build.version, api.at("/info/version").asString())
        assertEquals("bearer", api.at("/components/securitySchemes/bearerAuth/scheme").asString())
        var count = 0
        api.get("paths").properties().forEach { (path, item) ->
            item.properties().filter { it.key in setOf("get", "post", "put", "delete") }.forEach { (_, operation) ->
                count++
                assertTrue(operation.get("summary").asString().isNotBlank())
                assertTrue(operation.get("description").asString().isNotBlank())
                if (path.startsWith("/api/dish")) {
                    assertEquals("[]", operation.at("/security/0/bearerAuth").toString())
                } else {
                    operation.get("security")?.let { assertTrue(it.isEmpty) }
                }
                operation.get("responses").properties().forEach { (_, response) ->
                    val ref = response.at("/content/application~1json/schema/\$ref").asString()
                    assertTrue(
                        ref.substringAfterLast('/') in
                            setOf(
                                "ApiResponseRegister",
                                "ApiResponseLogin",
                                "ApiResponseEmpty",
                                "ApiResponseVerifyOtp",
                                "ApiResponseDish",
                                "ApiResponseDishList",
                                "ApiErrorResponse",
                                "ApiHealthResponse",
                            ),
                    )
                    assertFalse(response.at("/content/application~1json/examples").isEmpty)
                }
            }
        }
        assertEquals(13, count)
        val dish = api.at("/components/schemas/DishResponse/properties")
        assertEquals(
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
            ),
            dish
                .properties()
                .map {
                    it.key
                }.toSet(),
        )
        assertEquals("number", dish.at("/price/type").asString())
        assertEquals("number", dish.at("/rating/type").asString())
        assertTrue(dish.at("/thumbails/description").asString().contains("a propósito"))
        val upload = api.at("/paths/~1api~1dish~1{id}~1image/post/requestBody/content/multipart~1form-data/schema/properties/file")
        assertEquals("binary", upload.get("format").asString())
        assertEquals("string", upload.get("type").asString())
        assertTrue(
            api
                .at("/components/schemas/ApiErrorResponse/required")
                .toList()
                .map { it.asString() }
                .contains("data"),
        )
    }

    @Test
    fun `Swagger UI retains authorization and production server uses configured URL`() {
        assertTrue(
            mvc
                .perform(get("/docs"))
                .andReturn()
                .response.status in 200..399,
        )
        assertEquals(
            200,
            mvc
                .perform(get("/swagger-ui/index.html"))
                .andReturn()
                .response.status,
        )
        val response = mvc.perform(get("/v3/api-docs/swagger-config")).andReturn().response
        assertEquals(200, response.status)
        val config = mapper.readTree(response.contentAsString)
        assertTrue(config.get("persistAuthorization").asBoolean())
        assertTrue(config.get("tryItOutEnabled").asBoolean())
        assertEquals("alpha", config.get("tagsSorter").asString())
        val configured =
            OpenApiConfig(
                BuildProperties(
                    Properties().apply {
                        setProperty("version", "test-version")
                    },
                ),
                "https://api.example.com/",
            ).ecoEatsOpenApi()
        assertEquals(listOf("http://localhost:8080", "https://api.example.com"), configured.servers.map { it.url })
    }
}

private fun Any?.sortedContract(): Any? =
    when (this) {
        is Map<*, *> -> entries.associate { it.key.toString() to it.value.sortedContract() }.toSortedMap()
        is List<*> -> map { it.sortedContract() }
        else -> this
    }
