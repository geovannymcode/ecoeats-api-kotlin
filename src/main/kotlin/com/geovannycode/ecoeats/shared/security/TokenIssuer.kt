package com.geovannycode.ecoeats.shared.security

import com.geovannycode.ecoeats.auth.domain.Role
import com.geovannycode.ecoeats.shared.config.EcoEatsProperties
import org.springframework.security.oauth2.jose.jws.MacAlgorithm
import org.springframework.security.oauth2.jwt.JwsHeader
import org.springframework.security.oauth2.jwt.JwtClaimsSet
import org.springframework.security.oauth2.jwt.JwtEncoder
import org.springframework.security.oauth2.jwt.JwtEncoderParameters
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.UUID

@Component
internal class TokenIssuer(
    private val encoder: JwtEncoder,
    private val properties: EcoEatsProperties,
    private val clock: Clock,
) {
    fun issue(
        userId: UUID,
        email: String,
        role: Role,
    ): String {
        val now = clock.instant()
        val claims =
            JwtClaimsSet
                .builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role.name)
                .issuedAt(now)
                .expiresAt(now.plus(properties.jwt.expiration))
                .build()
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).tokenValue
    }
}
