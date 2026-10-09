package com.geovannycode.ecoeats.shared.config

import jakarta.validation.Valid
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated
import java.time.Duration

@ConfigurationProperties("ecoeats")
@Validated
internal data class EcoEatsProperties(
    @field:Valid val cors: Cors,
    @field:Valid val jwt: Jwt,
    @field:Valid val mail: Mail,
    val seed: Seed = Seed(),
) {
    data class Cors(val allowedOrigins: List<String>)
    data class Jwt(
        @field:NotBlank val secret: String,
        val expiration: Duration = Duration.ofHours(24),
    )
    data class Mail(@field:Valid val resend: Resend)
    data class Resend(
        val apiKey: String,
        @field:NotBlank @field:Email val fromEmail: String,
        @field:NotBlank val fromName: String,
    )
    data class Seed(val adminPassword: String? = null)
}
