package com.geovannycode.ecoeats.auth

import com.geovannycode.ecoeats.TestcontainersConfiguration
import com.geovannycode.ecoeats.auth.domain.Role
import com.geovannycode.ecoeats.auth.infrastructure.UserRepository
import com.geovannycode.ecoeats.shared.security.TokenIssuer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class, AuthIntegrationTests.SecurityTestConfiguration::class)
@AutoConfigureMockMvc
@SpringBootTest(
    properties = [
        "spring.docker.compose.enabled=false",
        "ecoeats.jwt.secret=test-only-secret-with-at-least-32-bytes",
        "ecoeats.mail.resend.api-key=test-only",
        "ecoeats.mail.resend.from-email=test@example.com",
        "ecoeats.seed.admin-password=",
        "ecoeats.cors.allowed-origins=http://localhost:3000",
    ],
)
internal class AuthIntegrationTests {
    @Autowired private lateinit var mvc: MockMvc

    @Autowired private lateinit var users: UserRepository

    @Autowired private lateinit var mapper: ObjectMapper

    @Autowired private lateinit var decoder: JwtDecoder

    @Autowired private lateinit var encoder: JwtEncoder

    @Autowired private lateinit var passwordEncoder: PasswordEncoder

    @Autowired private lateinit var issuer: TokenIssuer

    @Autowired private lateinit var clock: MutableClock

    @BeforeEach
    fun clearAccounts() {
        users.deleteAll()
        clock.reset()
    }

    @Test
    fun `register responds with exact mobile contract`() {
        val result = register()
        assertEquals(201, result.response.status)
        val actual = body(result)
        val id = actual.at("/data/user/id").asString()
        UUID.fromString(id)
        assertEquals(
            mapper.readTree(
                """{"success":true,"message":"Usuario registrado correctamente","data":{"user":{"id":"$id","numero_documento":"100","nombres":"Test","apellidos":"Account","correo":"person@example.com"}}}""",
            ),
            actual,
        )
        val saved = assertNotNull(users.findByEmailIgnoreCase("person@example.com"))
        assertTrue(passwordEncoder.matches("secret-value", saved.password))
        assertEquals(Role.USER, saved.role)
    }

    @Test
    fun `email conflict ignores letter case`() {
        register()
        assertError(register("101", "PERSON@EXAMPLE.COM"), 409, "El email ya está registrado")
    }

    @Test
    fun `document conflict returns correct message`() {
        register()
        assertError(register("100", "other@example.com"), 409, "El número de documento ya está registrado")
    }

    @Test
    fun `validation returns public field names and Spanish messages`() {
        val result =
            mvc
                .perform(
                    post("/api/securities/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"numero_documento":"","nombres":"","apellidos":"","correo":"invalid","password":"abc"}"""),
                ).andReturn()
        assertEquals(400, result.response.status)
        val actual = body(result)
        assertEquals("Datos de entrada inválidos", actual.get("message").asString())
        val errors = actual.get("errors").associate { it.get("field").asString() to it.get("message").asString() }
        assertEquals(
            mapOf(
                "numero_documento" to "El número de documento es obligatorio",
                "nombres" to "Los nombres son obligatorios",
                "apellidos" to "Los apellidos son obligatorios",
                "correo" to "El correo debe ser válido",
                "password" to "La contraseña debe tener al menos 6 caracteres",
            ),
            errors,
        )
        assertEquals(setOf("success", "message", "data", "timestamp", "errors"), actual.properties().map { it.key }.toSet())
        assertTrue(actual.get("data").isNull)
        Instant.parse(actual.get("timestamp").asString())
    }

    @Test
    fun `login returns exact contract and a usable signed token`() {
        register()
        val result = login("PERSON@example.com", "secret-value")
        assertEquals(200, result.response.status)
        val actual = body(result)
        val session = actual.get("data")
        val token = session.get("token").asString()
        val id = session.get("id").asString()
        assertEquals(
            mapper.readTree(
                """{"success":true,"message":"Login exitoso","data":{"id":"$id","email":"person@example.com","token":"$token","tokenFirebaseAuth":""}}""",
            ),
            actual,
        )
        assertEquals(id, decoder.decode(token).subject)
        val secured = mvc.perform(get("/api/dish").header("Authorization", "Bearer $token")).andReturn()
        assertEquals(200, secured.response.status)
        assertEquals(12, body(secured).get("data").size())
    }

    @Test
    fun `password beyond BCrypt byte limit returns validation error`() {
        val request =
            mapOf(
                "numero_documento" to "100",
                "nombres" to "Test",
                "apellidos" to "Account",
                "correo" to "test@example.com",
                "password" to "é".repeat(37),
            )
        val result =
            mvc
                .perform(
                    post("/api/securities/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)),
                ).andReturn()
        assertEquals(400, result.response.status)
        assertEquals(
            mapper.readTree("""[{"field":"password","message":"La contraseña no puede superar 72 bytes UTF-8"}]"""),
            body(result).get("errors"),
        )
    }

    @Test
    fun `wrong password and missing account return identical credential envelopes`() {
        register()
        assertError(login("person@example.com", "wrong-value"), 401, "Credenciales inválidas")
        assertError(login("missing@example.com", "wrong-value"), 401, "Credenciales inválidas")
    }

    @Test
    fun `anonymous dish request returns envelope`() {
        assertError(mvc.perform(get("/api/dish")).andReturn(), 401, "No autorizado")
    }

    @Test
    fun `expired JWT returns unauthorized`() {
        register()
        val user = assertNotNull(users.findByEmailIgnoreCase("person@example.com"))
        val claims =
            JwtClaimsSet
                .builder()
                .subject(assertNotNull(user.id).toString())
                .claim("email", user.email)
                .claim("role", "USER")
                .issuedAt(clock.instant().minusSeconds(3600))
                .expiresAt(clock.instant().minusSeconds(1))
                .build()
        val token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
        assertError(mvc.perform(get("/api/dish").header("Authorization", "Bearer $token")).andReturn(), 401, "No autorizado")
    }

    @Test
    fun `invalid signature returns unauthorized`() {
        register()
        val token = body(login("person@example.com", "secret-value")).at("/data/token").asString()
        val parts = token.split('.')
        val signature = (if (parts[2].first() == 'A') "B" else "A") + parts[2].drop(1)
        val altered = "${parts[0]}.${parts[1]}.$signature"
        assertError(mvc.perform(get("/api/dish").header("Authorization", "Bearer $altered")).andReturn(), 401, "No autorizado")
    }

    @ParameterizedTest
    @ValueSource(strings = ["POST", "PUT", "DELETE"])
    fun `user cannot write dishes but admin can`(method: String) {
        register()
        val user = assertNotNull(users.findByEmailIgnoreCase("person@example.com"))
        val userToken = issuer.issue(assertNotNull(user.id), user.email, Role.USER)
        val adminToken = issuer.issue(assertNotNull(user.id), user.email, Role.ADMIN)
        val path = if (method == "POST") "/api/dish" else "/api/dish/${UUID.randomUUID()}"
        assertError(
            mvc
                .perform(
                    request(HttpMethod.valueOf(method), path)
                        .header("Authorization", "Bearer $userToken"),
                ).andReturn(),
            403,
            "Acceso denegado",
        )
        val permitted =
            mvc
                .perform(
                    request(HttpMethod.valueOf(method), path)
                        .header("Authorization", "Bearer $adminToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"),
                ).andReturn()
                .response.status
        assertEquals(if (method == "DELETE") 404 else 400, permitted)
    }

    @Test
    fun `deleted user returns invalid token after cached presence expires`() {
        register()
        val token = body(login("person@example.com", "secret-value")).at("/data/token").asString()
        assertEquals(
            200,
            mvc
                .perform(get("/api/dish").header("Authorization", "Bearer $token"))
                .andReturn()
                .response.status,
        )
        users.deleteAll()
        clock.advanceSeconds(5)
        assertError(mvc.perform(get("/api/dish").header("Authorization", "Bearer $token")).andReturn(), 401, "Token inválido")
    }

    @Test
    fun `public health and Swagger work without token`() {
        listOf("/health", "/docs", "/v3/api-docs", "/swagger-ui/index.html").forEach {
            val status =
                mvc
                    .perform(get(it))
                    .andReturn()
                    .response.status
            assertTrue(status == 200 || status == 302, "$it status $status")
        }
    }

    @Test
    fun `CORS preflight uses configured origins`() {
        val result =
            mvc
                .perform(
                    options("/api/dish")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"),
                ).andReturn()
        assertEquals(200, result.response.status)
        assertEquals("http://localhost:3000", result.response.getHeader("Access-Control-Allow-Origin"))
    }

    @Test
    fun `concurrent registrations produce one account and one conflict`() {
        Executors.newFixedThreadPool(2).use { pool ->
            val start = CountDownLatch(1)
            val requests =
                (1..2).map { index ->
                    pool.submit<Int> {
                        check(start.await(10, TimeUnit.SECONDS))
                        register("$index", "race@example.com").response.status
                    }
                }
            start.countDown()
            assertEquals(listOf(201, 409), requests.map { it.get(20, TimeUnit.SECONDS) }.sorted())
            assertEquals(1, users.count())
        }
    }

    private fun register(
        document: String = "100",
        email: String = "Person@Example.com",
    ): MvcResult =
        mvc
            .perform(
                post("/api/securities/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """{"numero_documento":"$document","nombres":"Test","apellidos":"Account","correo":"$email","password":"secret-value"}""",
                    ),
            ).andReturn()

    private fun login(
        email: String,
        password: String,
    ): MvcResult =
        mvc
            .perform(
                post("/api/securities/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"email":"$email","password":"$password"}"""),
            ).andReturn()

    private fun body(result: MvcResult): JsonNode = mapper.readTree(result.response.contentAsString)

    private fun assertError(
        result: MvcResult,
        status: Int,
        message: String,
    ) {
        assertEquals(status, result.response.status)
        val actual = body(result)
        val timestamp = actual.get("timestamp").asString()
        Instant.parse(timestamp)
        assertTrue(timestamp.endsWith("Z"))
        assertEquals(mapper.readTree("""{"success":false,"message":"$message","data":null,"timestamp":"$timestamp"}"""), actual)
    }

    @TestConfiguration(proxyBeanMethods = false)
    class SecurityTestConfiguration {
        @Bean @Primary
        fun testClock(): MutableClock = MutableClock()
    }

    class MutableClock : Clock() {
        private val initial = Instant.parse("2026-10-09T12:00:00Z")
        private val current = AtomicReference(initial)

        override fun instant(): Instant = current.get()

        override fun getZone(): ZoneId = ZoneOffset.UTC

        override fun withZone(zone: ZoneId): Clock = Clock.fixed(instant(), zone)

        fun advanceSeconds(seconds: Long) {
            current.updateAndGet { it.plusSeconds(seconds) }
        }

        fun reset() {
            current.set(initial)
        }
    }
}
