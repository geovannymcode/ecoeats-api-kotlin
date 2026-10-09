package com.geovannycode.ecoeats.shared.web

import com.geovannycode.ecoeats.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options
import tools.jackson.databind.ObjectMapper
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "ecoeats.jwt.secret=test-only-secret-with-at-least-32-bytes",
        "ecoeats.mail.resend.api-key=test-only", "ecoeats.mail.resend.from-email=test@example.com",
        "ecoeats.cors.allowed-origins=", "ecoeats.seed.admin-password=",
    ],
)
@ActiveProfiles("prod")
@AutoConfigureMockMvc
internal class ProductionHardeningTests {
    @Autowired private lateinit var mvc: MockMvc

    @Autowired private lateinit var mapper: ObjectMapper

    @Test
    fun `production publishes aggregate health and HSTS over HTTPS`() {
        val response = mvc.perform(get("/health").secure(true)).andReturn().response
        assertEquals(200, response.status)
        assertNotNull(response.getHeader("Strict-Transport-Security"))
        assertEquals(setOf("status"), mapper.readTree(response.contentAsString).propertyNames().toSet())
    }

    @Test
    fun `empty production origins reject all web preflights`() {
        val response =
            mvc
                .perform(
                    options("/api/dish")
                        .header("Origin", "https://client.example.com")
                        .header("Access-Control-Request-Method", "GET"),
                ).andReturn()
                .response
        assertEquals(403, response.status)
        assertNull(response.getHeader("Access-Control-Allow-Origin"))
    }
}
