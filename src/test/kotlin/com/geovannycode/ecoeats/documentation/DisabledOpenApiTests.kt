package com.geovannycode.ecoeats.documentation

import com.geovannycode.ecoeats.TestcontainersConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import kotlin.test.assertEquals

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "spring.docker.compose.enabled=false", "ecoeats.jwt.secret=test-only-secret-with-at-least-32-bytes",
        "ecoeats.mail.resend.api-key=test-only", "ecoeats.mail.resend.from-email=test@example.com",
        "ecoeats.seed.admin-password=", "SPRINGDOC_ENABLED=false",
    ],
)
@AutoConfigureMockMvc
internal class DisabledOpenApiTests {
    @Autowired private lateinit var mvc: MockMvc

    @Test
    fun `environment switch disables docs while health stays public`() {
        assertEquals(
            404,
            mvc
                .perform(get("/v3/api-docs"))
                .andReturn()
                .response.status,
        )
        assertEquals(
            404,
            mvc
                .perform(get("/docs"))
                .andReturn()
                .response.status,
        )
        assertEquals(
            404,
            mvc
                .perform(get("/swagger-ui/index.html"))
                .andReturn()
                .response.status,
        )
        assertEquals(
            200,
            mvc
                .perform(get("/health"))
                .andReturn()
                .response.status,
        )
    }
}
