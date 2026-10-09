package com.geovannycode.ecoeats.notification

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.geovannycode.ecoeats.shared.config.EcoEatsProperties
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.mock.env.MockEnvironment
import org.springframework.mock.http.client.MockClientHttpRequest
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.content
import org.springframework.test.web.client.match.MockRestRequestMatchers.header
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withException
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestClient
import tools.jackson.databind.json.JsonMapper
import java.net.SocketTimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal class ResendEmailSenderTests {
    private lateinit var server: MockRestServiceServer
    private lateinit var sender: ResendEmailSender
    private val resend = EcoEatsProperties.Resend("test-only-api-key", "noreply@example.com", "EcoEats")
    private val template =
        ClassPathResource("templates/email/password-reset-otp.html")
            .inputStream
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    @BeforeEach
    fun setUp() {
        val builder = EmailConfiguration().resendClientBuilder(resend, RestClient.builder())
        server = MockRestServiceServer.bindTo(builder).build()
        sender = ResendEmailSender(builder.build(), resend.fromEmail, resend.fromName, template)
    }

    @Test
    fun `Resend request has expected headers and exact JSON body`() {
        server
            .expect(requestTo("https://api.resend.com/emails"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-only-api-key"))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect { request ->
                val body = JsonMapper.builder().build().readTree((request as MockClientHttpRequest).bodyAsString)
                assertEquals("EcoEats <noreply@example.com>", body.get("from").asString())
                assertEquals("person@example.com", body.get("to").get(0).asString())
                assertEquals("EcoEats - Código de recuperación de contraseña", body.get("subject").asString())
                assertEquals(template.replace("{{otp}}", "123456"), body.get("html").asString())
                assertEquals(setOf("from", "to", "subject", "html"), body.properties().map { it.key }.toSet())
            }.andRespond(withSuccess("""{"id":"test-email-id"}""", MediaType.APPLICATION_JSON))
        sender.sendPasswordResetOtp("person@example.com", "123456")
        server.verify()
    }

    @ParameterizedTest
    @ValueSource(ints = [401, 429, 503])
    fun `Resend errors expose status in logs without sensitive values`(status: Int) {
        server
            .expect(requestTo("https://api.resend.com/emails"))
            .andRespond(withStatus(HttpStatus.valueOf(status)).body("test-only-api-key 123456"))
        val logger = LoggerFactory.getLogger(ResendEmailSender::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        try {
            assertFailsWith<EmailDeliveryException> { sender.sendPasswordResetOtp("person@example.com", "123456") }
            val messages = appender.list.map { it.formattedMessage }
            assertTrue(messages.any { it.contains("status=$status") })
            assertFalse(messages.any { it.contains("test-only-api-key") || it.contains("123456") })
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
        server.verify()
    }

    @Test
    fun `transport failure becomes EmailDeliveryException`() {
        server
            .expect(requestTo("https://api.resend.com/emails"))
            .andRespond(withException(SocketTimeoutException("private transport details")))
        val exception = assertFailsWith<EmailDeliveryException> { sender.sendPasswordResetOtp("person@example.com", "123456") }
        assertNull(exception.cause)
        server.verify()
    }

    @Test
    fun `empty key selects logging sender only in local profile`() {
        val properties =
            EcoEatsProperties(
                EcoEatsProperties.Cors(emptyList()),
                EcoEatsProperties.Jwt("a".repeat(32)),
                EcoEatsProperties.Mail(resend.copy(apiKey = "")),
            )
        val local = MockEnvironment().apply { setActiveProfiles("local") }
        assertIs<LoggingEmailSender>(EmailConfiguration().emailSender(properties, local, RestClient.builder()))
        assertFailsWith<IllegalArgumentException> {
            EmailConfiguration().emailSender(properties, MockEnvironment(), RestClient.builder())
        }
    }
}
