package com.geovannycode.ecoeats.auth

import com.geovannycode.ecoeats.TestcontainersConfiguration
import com.geovannycode.ecoeats.auth.application.otpSha256
import com.geovannycode.ecoeats.auth.domain.UserEntity
import com.geovannycode.ecoeats.auth.infrastructure.PasswordResetTokenRepository
import com.geovannycode.ecoeats.auth.infrastructure.UserRepository
import com.geovannycode.ecoeats.notification.EmailDeliveryException
import com.geovannycode.ecoeats.notification.EmailSender
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import tools.jackson.databind.ObjectMapper
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

@Import(TestcontainersConfiguration::class, PasswordRecoveryIntegrationTests.RecoveryTestConfiguration::class)
@AutoConfigureMockMvc
@SpringBootTest(
    properties = [
        "spring.docker.compose.enabled=false",
        "ecoeats.jwt.secret=test-only-secret-with-at-least-32-bytes",
        "ecoeats.mail.resend.api-key=test-only",
        "ecoeats.mail.resend.from-email=test@example.com",
        "ecoeats.seed.admin-password=",
    ],
)
internal class PasswordRecoveryIntegrationTests {
    @Autowired private lateinit var mvc: MockMvc

    @Autowired private lateinit var users: UserRepository

    @Autowired private lateinit var tokens: PasswordResetTokenRepository

    @Autowired private lateinit var mapper: ObjectMapper

    @Autowired private lateinit var passwords: PasswordEncoder

    @Autowired private lateinit var sender: CapturingEmailSender

    @Autowired private lateinit var clock: RecoveryClock

    @BeforeEach
    fun setUp() {
        users.deleteAll()
        sender.reset()
        clock.reset()
        users.saveAndFlush(UserEntity("100", "Test", "Account", "person@example.com", checkNotNull(passwords.encode("old-password"))))
    }

    @Test
    fun `full recovery flow replaces password and prevents token reuse`() {
        val forgot = forgot()
        assertSuccess(forgot, "Código de recuperación enviado", "null")
        val otp = sender.code()
        val token = tokens.findAll().single()
        assertEquals(otp.otpSha256(), token.otpHash)
        assertNotEquals(otp, token.otpHash)
        assertEquals(clock.instant().plusSeconds(900), token.expiresAt)
        assertSuccess(verify(otp), "OTP válido", "{\"valid\":true}")
        assertSuccess(reset(otp), "Contraseña restablecida exitosamente", "null")
        assertTrue(tokens.findAll().single().used)
        assertSuccess(verify(otp), "OTP inválido o expirado", "{\"valid\":false}")
        assertError(reset(otp), 400, "OTP inválido o expirado")
        assertEquals(200, login("new-password").response.status)
        assertError(login("old-password"), 401, "Credenciales inválidas")
    }

    @Test
    fun `unknown account returns exact 404 and does not send email`() {
        assertError(postJson("forgot-password", mapOf("email" to "missing@example.com")), 404, "No se encontró un usuario con ese email")
        assertEquals(0, tokens.count())
        assertTrue(sender.codes.isEmpty())
        assertSuccess(
            postJson("verify-otp", mapOf("email" to "missing@example.com", "otp" to "123456")),
            "OTP inválido o expirado",
            "{\"valid\":false}",
        )
    }

    @Test
    fun `new recovery request invalidates previous OTP`() {
        forgot()
        val previous = sender.code()
        forgot()
        val latest = sender.code()
        assertNotEquals(previous, latest)
        assertEquals(2, tokens.count())
        assertEquals(1, tokens.findAll().count { !it.used })
        assertSuccess(verify(previous), "OTP inválido o expirado", "{\"valid\":false}")
        assertSuccess(verify(latest), "OTP válido", "{\"valid\":true}")
    }

    @Test
    fun `five failures lock out token including correct OTP`() {
        forgot()
        val correct = sender.code()
        val incorrect = if (correct == "999999") "111111" else "999999"
        repeat(5) { assertSuccess(verify(incorrect), "OTP inválido o expirado", "{\"valid\":false}") }
        val stored = tokens.findAll().single()
        assertTrue(stored.used)
        assertEquals(5, stored.attempts)
        assertError(reset(correct), 400, "OTP inválido o expirado")
    }

    @Test
    fun `token expires at exactly fifteen minutes`() {
        forgot()
        val otp = sender.code()
        clock.advanceSeconds(900)
        assertSuccess(verify(otp), "OTP inválido o expirado", "{\"valid\":false}")
        assertError(reset(otp), 400, "OTP inválido o expirado")
    }

    @Test
    fun `delivery failure returns 503 and restores previous active token`() {
        forgot()
        val previous = sender.code()
        val previousId = tokens.findAll().single().id
        sender.fail = true
        assertError(forgot(), 503, "No fue posible enviar el correo, intenta de nuevo")
        val token = tokens.findAll().single()
        assertEquals(previousId, token.id)
        assertFalse(token.used)
        assertSuccess(verify(previous), "OTP válido", "{\"valid\":true}")
    }

    @Test
    fun `OTP and new password validation errors use public fields`() {
        listOf("12345", "abcdef", "1234567").forEach { invalid ->
            val result = verify(invalid)
            assertEquals(400, result.response.status)
            val actual = mapper.readTree(result.response.contentAsString)
            assertEquals(
                "otp",
                actual
                    .get("errors")
                    .get(0)
                    .get("field")
                    .asString(),
            )
            assertEquals(
                "El OTP debe tener exactamente 6 dígitos",
                actual
                    .get("errors")
                    .get(0)
                    .get("message")
                    .asString(),
            )
        }
        val result = postJson("reset-password", mapOf("email" to "person@example.com", "otp" to "123456", "newPassword" to "abc"))
        assertEquals(400, result.response.status)
        assertEquals(
            "newPassword",
            mapper
                .readTree(result.response.contentAsString)
                .get("errors")
                .get(0)
                .get("field")
                .asString(),
        )
    }

    @Test
    fun `concurrent resets can use an OTP only once`() {
        forgot()
        val otp = sender.code()
        Executors.newFixedThreadPool(2).use { pool ->
            val start = CountDownLatch(1)
            val requests =
                (1..2).map {
                    pool.submit<Int> {
                        check(start.await(10, TimeUnit.SECONDS))
                        reset(otp).response.status
                    }
                }
            start.countDown()
            assertEquals(listOf(200, 400), requests.map { it.get(20, TimeUnit.SECONDS) }.sorted())
        }
        assertTrue(tokens.findAll().single().used)
    }

    private fun forgot() = postJson("forgot-password", mapOf("email" to "PERSON@example.com"))

    private fun verify(otp: String) = postJson("verify-otp", mapOf("email" to "person@example.com", "otp" to otp))

    private fun reset(otp: String) =
        postJson(
            "reset-password",
            mapOf(
                "email" to "person@example.com",
                "otp" to otp,
                "newPassword" to "new-password",
            ),
        )

    private fun login(password: String) = postJson("login", mapOf("email" to "person@example.com", "password" to password))

    private fun postJson(
        endpoint: String,
        payload: Map<String, String>,
    ): MvcResult =
        mvc
            .perform(
                post("/api/securities/$endpoint")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(mapper.writeValueAsString(payload)),
            ).andReturn()

    private fun assertSuccess(
        result: MvcResult,
        message: String,
        data: String,
    ) {
        assertEquals(200, result.response.status)
        assertEquals(
            mapper.readTree("""{"success":true,"message":"$message","data":$data}"""),
            mapper.readTree(result.response.contentAsString),
        )
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
        assertTrue(timestamp.endsWith("Z"))
        assertEquals(mapper.readTree("""{"success":false,"message":"$message","data":null,"timestamp":"$timestamp"}"""), actual)
    }

    @TestConfiguration(proxyBeanMethods = false)
    class RecoveryTestConfiguration {
        @Bean @Primary
        fun fakeEmailSender(): CapturingEmailSender = CapturingEmailSender()

        @Bean @Primary
        fun recoveryClock(): RecoveryClock = RecoveryClock()

        @Bean @Primary
        fun deterministicOtpRandom(): SecureRandom =
            object : SecureRandom() {
                private val sequence = AtomicInteger()

                override fun nextInt(bound: Int): Int = sequence.getAndIncrement().mod(bound)
            }
    }

    class CapturingEmailSender : EmailSender {
        val codes = ConcurrentHashMap<String, String>()

        @Volatile var fail: Boolean = false

        override fun sendPasswordResetOtp(
            to: String,
            otp: String,
        ) {
            if (fail) throw EmailDeliveryException()
            codes[to] = otp
        }

        fun code(): String = checkNotNull(codes["person@example.com"])

        fun reset() {
            codes.clear()
            fail = false
        }
    }

    class RecoveryClock : Clock() {
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
