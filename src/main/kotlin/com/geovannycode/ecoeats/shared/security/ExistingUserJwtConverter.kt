package com.geovannycode.ecoeats.shared.security

import org.springframework.core.convert.converter.Converter
import org.springframework.security.authentication.AbstractAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter
import org.springframework.stereotype.Component
import java.util.UUID

@Component
internal class ExistingUserJwtConverter(private val presence: UserPresenceCache) : Converter<Jwt, AbstractAuthenticationToken> {
    private val delegate = JwtAuthenticationConverter().apply {
        setJwtGrantedAuthoritiesConverter { jwt ->
            val role = jwt.getClaimAsString("role")
            if (role != "USER" && role != "ADMIN") throw InvalidTokenException()
            listOf(SimpleGrantedAuthority("ROLE_$role"))
        }
    }

    override fun convert(jwt: Jwt): AbstractAuthenticationToken {
        val userId = try {
            UUID.fromString(jwt.subject ?: throw InvalidTokenException())
        } catch (_: IllegalArgumentException) {
            throw InvalidTokenException()
        }
        if (!presence.exists(userId)) throw InvalidTokenException()
        return checkNotNull(delegate.convert(jwt))
    }
}
