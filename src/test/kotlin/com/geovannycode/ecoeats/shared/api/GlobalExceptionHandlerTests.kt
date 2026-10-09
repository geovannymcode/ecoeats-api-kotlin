package com.geovannycode.ecoeats.shared.api

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.resource.NoResourceFoundException
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

internal class GlobalExceptionHandlerTests {
    private lateinit var mvc: MockMvc
    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()

    @BeforeEach
    fun setUp() {
        mvc =
            MockMvcBuilders
                .standaloneSetup(ErrorTestController())
                .setControllerAdvice(GlobalExceptionHandler())
                .build()
    }

    @ParameterizedTest
    @CsvSource(
        "authentication,401,Credenciales inválidas",
        "forbidden,403,Acceso denegado",
        "missing,404,Recurso no encontrado",
        "unexpected,500,Error interno del servidor",
    )
    fun `errors return exact envelope`(
        kind: String,
        status: Int,
        message: String,
    ) {
        assertError(mvc.perform(get("/test/errors/$kind")).andReturn(), status, message)
    }

    @Test
    fun `malformed UUID returns 400`() {
        assertError(mvc.perform(get("/test/uuid/invalid")).andReturn(), 400, "Parámetro de la petición inválido")
    }

    @Test
    fun `unsupported method returns 405`() {
        assertError(mvc.perform(post("/test/uuid/${UUID.randomUUID()}")).andReturn(), 405, "Método HTTP no permitido")
    }

    @Test
    fun `malformed body returns 400`() {
        assertError(
            mvc.perform(post("/test/body").contentType(MediaType.APPLICATION_JSON).content("{")).andReturn(),
            400,
            "Cuerpo de la petición inválido",
        )
    }

    @Test
    fun `invalid body includes field errors`() {
        assertError(
            mvc
                .perform(
                    post("/test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"),
                ).andReturn(),
            400,
            "Datos de entrada inválidos",
            """[{"field":"name","message":"Nombre requerido"}]""",
        )
    }

    @Test
    fun `invalid method parameter includes field errors`() {
        assertError(
            mvc.perform(get("/test/validated").param("quantity", "0")).andReturn(),
            400,
            "Datos de entrada inválidos",
            """[{"field":"quantity","message":"Cantidad mínima 1"}]""",
        )
    }

    @Test
    fun `success with null data omits error metadata`() {
        val result = mvc.perform(get("/test/success")).andReturn()
        assertEquals(200, result.response.status)
        assertEquals(
            mapper.readTree("""{"success":true,"message":"OK","data":null}"""),
            mapper.readTree(result.response.contentAsString),
        )
    }

    private fun assertError(
        result: MvcResult,
        status: Int,
        message: String,
        errors: String? = null,
    ) {
        assertEquals(status, result.response.status)
        val actual = mapper.readTree(result.response.contentAsString)
        val timestamp = actual.get("timestamp").asString()
        assertTrue(timestamp.endsWith("Z"))
        Instant.parse(timestamp)
        val fields = errors?.let { ",\"errors\":$it" } ?: ""
        assertEquals(
            mapper.readTree(
                """{"success":false,"message":"$message","data":null,"timestamp":"$timestamp"$fields}""",
            ),
            actual,
        )
    }
}

@RestController
internal class ErrorTestController {
    @GetMapping("/test/errors/{kind}")
    fun error(
        @PathVariable kind: String,
    ): Unit =
        when (kind) {
            "authentication" -> throw BadCredentialsException("private credentials")
            "forbidden" -> throw AccessDeniedException("private authorization")
            "missing" -> throw NoResourceFoundException(HttpMethod.GET, "/missing", "/missing")
            else -> throw IllegalStateException("private internal details")
        }

    @GetMapping("/test/uuid/{id}")
    fun uuid(
        @PathVariable id: UUID,
    ) = ApiResponse.ok(id)

    @PostMapping("/test/body")
    fun body(
        @Valid @RequestBody request: ValidationRequest,
    ) = ApiResponse.ok(request)

    @GetMapping("/test/validated")
    fun validated(
        @RequestParam @Min(value = 1, message = "Cantidad mínima 1") quantity: Int,
    ) = ApiResponse.ok(quantity)

    @GetMapping("/test/success")
    fun success() = ApiResponse.ok<Nothing>(null)
}

internal data class ValidationRequest(
    @field:NotBlank(message = "Nombre requerido") val name: String,
)
