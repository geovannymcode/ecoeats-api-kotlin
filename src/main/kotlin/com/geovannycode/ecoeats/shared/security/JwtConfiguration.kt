package com.geovannycode.ecoeats.shared.security

import com.geovannycode.ecoeats.shared.config.EcoEatsProperties
import com.nimbusds.jose.jwk.source.ImmutableSecret
import com.nimbusds.jose.proc.SecurityContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtTimestampValidator
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder
import java.time.Clock
import java.time.Duration
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec

@Configuration(proxyBeanMethods = false)
internal class JwtConfiguration {
    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun jwtSecretKey(properties: EcoEatsProperties): SecretKey {
        val bytes = properties.jwt.secret.toByteArray(Charsets.UTF_8)
        require(bytes.size >= 32) { "JWT secret must contain at least 32 UTF-8 bytes" }
        require(properties.jwt.expiration > Duration.ZERO) { "JWT expiration must be positive" }
        return SecretKeySpec(bytes, "HmacSHA256")
    }

    @Bean
    fun jwtEncoder(jwtSecretKey: SecretKey): JwtEncoder = NimbusJwtEncoder(ImmutableSecret<SecurityContext>(jwtSecretKey))

    @Bean
    fun jwtDecoder(
        jwtSecretKey: SecretKey,
        clock: Clock,
    ): JwtDecoder =
        NimbusJwtDecoder.withSecretKey(jwtSecretKey).macAlgorithm(MacAlgorithm.HS256).build().apply {
            setJwtValidator(JwtTimestampValidator(Duration.ZERO).apply { setClock(clock) })
        }
}
