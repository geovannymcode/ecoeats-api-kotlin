package com.geovannycode.ecoeats.shared.web

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.geovannycode.ecoeats.TestcontainersConfiguration
import com.geovannycode.ecoeats.image.infrastructure.BucketHealthIndicator
import com.geovannycode.ecoeats.image.infrastructure.StorageProperties
import com.geovannycode.ecoeats.shared.security.SecurityErrorWriter
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadBucketRequest
import software.amazon.awssdk.services.s3.model.HeadBucketResponse
import tools.jackson.databind.ObjectMapper
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "spring.docker.compose.enabled=false", "ecoeats.jwt.secret=test-only-secret-with-at-least-32-bytes",
        "ecoeats.mail.resend.api-key=test-only", "ecoeats.mail.resend.from-email=test@example.com",
        "ecoeats.cors.allowed-origins=https://client.example.com", "ecoeats.seed.admin-password=",
    ],
)
@AutoConfigureMockMvc
internal class HardeningTests {
    @Autowired private lateinit var mvc: MockMvc

    @Autowired private lateinit var mapper: ObjectMapper

    @Test
    fun `security chain enforces login quota before validation`() {
        repeat(10) {
            val response =
                mvc
                    .perform(
                        post("/api/securities/login")
                            .with { request ->
                                request.remoteAddr = "203.0.113.40"
                                request
                            }.contentType("application/json")
                            .content("{}"),
                    ).andReturn()
                    .response
            assertEquals(400, response.status)
        }
        val response =
            mvc
                .perform(
                    post("/api/securities/login")
                        .with { request ->
                            request.remoteAddr = "203.0.113.40"
                            request
                        }.contentType("application/json")
                        .content("{}"),
                ).andReturn()
                .response
        assertEquals(429, response.status)
        assertNotNull(response.getHeader("Retry-After"))
        assertNotNull(response.getHeader("X-Request-Id"))
    }

    @Test
    fun `Spring resolves forwarded IP before rate limiting`() {
        val rate = RateLimitFilter(SecurityErrorWriter(mapper))
        val forwarded =
            org.springframework.web.filter
                .ForwardedHeaderFilter()

        fun request(ip: String): MockHttpServletResponse {
            val input =
                MockHttpServletRequest("POST", "/api/securities/verify-otp").apply {
                    remoteAddr = "railway-proxy"
                    addHeader("X-Forwarded-For", ip)
                }
            val response = MockHttpServletResponse()
            forwarded.doFilter(input, response, FilterChain { req, res -> rate.doFilter(req, res, FilterChain { _, _ -> }) })
            return response
        }
        repeat(5) { assertEquals(200, request("203.0.113.41").status) }
        assertEquals(429, request("203.0.113.41").status)
        assertEquals(200, request("203.0.113.42").status)
    }

    @Test
    fun `local sender never logs OTP even at DEBUG`() {
        val logger = LoggerFactory.getLogger(com.geovannycode.ecoeats.notification.LoggingEmailSender::class.java) as Logger
        val oldLevel = logger.level
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.level = ch.qos.logback.classic.Level.DEBUG
        logger.addAppender(appender)
        try {
            com.geovannycode.ecoeats.notification
                .LoggingEmailSender()
                .sendPasswordResetOtp("geovanny@example.com", "781942")
            assertFalse(
                appender.list.any { it.formattedMessage.contains("781942") || it.formattedMessage.contains("geovanny@example.com") },
            )
            assertTrue(
                appender.list
                    .single()
                    .formattedMessage
                    .contains("g***@example.com"),
            )
        } finally {
            logger.level = oldLevel
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    @Test
    fun `endpoint quota and global quota return 429 with retry envelope and health is excluded`() {
        val errors = SecurityErrorWriter(mapper)
        listOf(
            "/api/securities/login" to 10,
            "/api/securities/register" to 10,
            "/api/securities/forgot-password" to 5,
            "/api/securities/verify-otp" to 5,
            "/api/dish" to 100,
        ).forEach { (path, quota) ->
            val filter = RateLimitFilter(errors)
            repeat(quota) {
                val response = MockHttpServletResponse()
                filter.doFilter(
                    MockHttpServletRequest(if (path == "/api/dish") "GET" else "POST", path),
                    response,
                    FilterChain { _, _ -> },
                )
                assertEquals(200, response.status)
            }
            val response = MockHttpServletResponse()
            filter.doFilter(
                MockHttpServletRequest(if (path == "/api/dish") "GET" else "POST", path),
                response,
                FilterChain { _, _ -> error("Exceeded quota reached the application") },
            )
            assertEquals(429, response.status)
            assertTrue(assertNotNull(response.getHeader("Retry-After")).toLong() >= 1)
            val json = mapper.readTree(response.contentAsString)
            assertEquals("Demasiadas solicitudes, intenta más tarde", json.get("message").asString())
            assertTrue(json.get("data").isNull)
            val health = MockHttpServletResponse()
            filter.doFilter(MockHttpServletRequest("GET", "/health"), health, FilterChain { _, _ -> })
            assertEquals(200, health.status)
            val other = MockHttpServletRequest("GET", "/api/dish").apply { remoteAddr = "different-ip" }
            filter.doFilter(other, MockHttpServletResponse(), FilterChain { _, _ -> })
        }
    }

    @Test
    fun `preflight accepts configured origin and rejects foreign origin and unsupported method`() {
        val allowed =
            mvc
                .perform(
                    options("/api/dish")
                        .header("Origin", "https://client.example.com")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Authorization, Content-Type"),
                ).andReturn()
                .response
        assertEquals(200, allowed.status)
        assertEquals("https://client.example.com", allowed.getHeader("Access-Control-Allow-Origin"))
        assertNull(allowed.getHeader("Access-Control-Allow-Credentials"))
        assertEquals("3600", allowed.getHeader("Access-Control-Max-Age"))
        val forbidden =
            mvc
                .perform(
                    options("/api/dish")
                        .header("Origin", "https://foreign.example.com")
                        .header("Access-Control-Request-Method", "POST"),
                ).andReturn()
                .response
        assertEquals(403, forbidden.status)
        assertNull(forbidden.getHeader("Access-Control-Allow-Origin"))
        assertEquals(
            403,
            mvc
                .perform(
                    options("/api/dish")
                        .header("Origin", "https://client.example.com")
                        .header("Access-Control-Request-Method", "PATCH"),
                ).andReturn()
                .response.status,
        )
    }

    @Test
    fun `JSON limit rejects content length and chunked body before parsing`() {
        val body = "a".repeat(1024 * 1024 + 1)
        val response = mvc.perform(post("/api/securities/login").contentType("application/json").content(body)).andReturn().response
        assertEquals(413, response.status)
        assertEquals("El cuerpo JSON supera el tamaño máximo de 1 MB", mapper.readTree(response.contentAsString).get("message").asString())
        val chunked =
            object : MockHttpServletRequest("POST", "/api/securities/login") {
                override fun getContentLengthLong(): Long = -1
            }.apply {
                contentType = "application/json"
                setContent(body.toByteArray())
            }
        val direct = MockHttpServletResponse()
        JsonBodyLimitFilter(SecurityErrorWriter(mapper)).doFilter(chunked, direct, FilterChain { _, _ -> error("Oversized body passed") })
        assertEquals(413, direct.status)
    }

    @Test
    fun `security headers differ for API and docs and request id is echoed`() {
        val response = mvc.perform(get("/health").header("X-Request-Id", "client-request-123")).andReturn().response
        assertEquals("client-request-123", response.getHeader("X-Request-Id"))
        assertEquals("nosniff", response.getHeader("X-Content-Type-Options"))
        assertEquals("DENY", response.getHeader("X-Frame-Options"))
        assertEquals("no-referrer", response.getHeader("Referrer-Policy"))
        assertTrue(assertNotNull(response.getHeader("Content-Security-Policy")).contains("default-src 'none'"))
        assertNull(response.getHeader("Strict-Transport-Security"))
        val docs = mvc.perform(get("/swagger-ui/index.html")).andReturn().response
        assertTrue(assertNotNull(docs.getHeader("Content-Security-Policy")).contains("default-src 'self'"))
    }

    @Test
    fun `request logs omit bodies headers queries and restore MDC`() {
        val logger = LoggerFactory.getLogger(RequestLoggingFilter::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            MDC.put("requestId", "previous")
            val request =
                MockHttpServletRequest("POST", "/unknown/password-secret").apply {
                    queryString = "otp=otp-secret&token=token-secret"
                    addHeader("Authorization", "Bearer token-secret")
                    addHeader("X-Api-Key", "api-key-secret")
                    addHeader("X-Request-Id", "safe-id")
                    setContent("password-secret otp-secret token-secret api-key-secret s3-secret".toByteArray())
                }
            val response = MockHttpServletResponse()
            RequestLoggingFilter().doFilter(request, response, FilterChain { _, _ -> assertEquals("safe-id", MDC.get("requestId")) })
            assertEquals("previous", MDC.get("requestId"))
            val line = appender.list.single().formattedMessage
            listOf(
                "password-secret",
                "otp-secret",
                "token-secret",
                "api-key-secret",
                "s3-secret",
            ).forEach { assertFalse(line.contains(it)) }
            assertTrue(line.contains("status=200"))
            assertTrue(line.contains("requestId=safe-id"))
            assertTrue(line.contains("route=[unmatched]"))
        } finally {
            MDC.remove("requestId")
            logger.detachAppender(appender)
            appender.stop()
        }
    }

    @Test
    fun `bucket health reports DOWN without SDK error details`() {
        val client = mockk<S3Client>()
        val properties = StorageProperties("test", URI.create("http://localhost:9000"), "access", "secret")
        every { client.headBucket(any<HeadBucketRequest>()) } returns HeadBucketResponse.builder().build()
        val indicator = BucketHealthIndicator(client, properties)
        assertEquals("UP", indicator.health().status.code)
        every { client.headBucket(any<HeadBucketRequest>()) } throws IllegalStateException("s3-secret")
        assertEquals("DOWN", indicator.health().status.code)
        assertTrue(indicator.health().details.isEmpty())
    }
}
