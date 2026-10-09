package com.geovannycode.ecoeats

import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@Import(TestcontainersConfiguration::class)
@SpringBootTest(properties = [
    "spring.docker.compose.enabled=false",
    "ecoeats.jwt.secret=test-only-secret-with-at-least-32-bytes",
    "ecoeats.mail.resend.api-key=test-only",
    "ecoeats.mail.resend.from-email=test@example.com",
])
class EcoEatsApplicationTests {

	@Test
	fun contextLoads() {
	}

}
