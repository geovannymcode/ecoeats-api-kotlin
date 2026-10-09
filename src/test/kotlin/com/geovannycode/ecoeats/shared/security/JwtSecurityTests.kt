package com.geovannycode.ecoeats.shared.security

import com.geovannycode.ecoeats.auth.domain.Role
import com.geovannycode.ecoeats.auth.infrastructure.UserRepository
import com.geovannycode.ecoeats.shared.config.EcoEatsProperties
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal class JwtSecurityTests {
    private val config = JwtConfiguration()
    private val now = Instant.parse("2026-10-09T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `issuer signs HS256 with correct claims and injected clock`() {
        val props = properties("a".repeat(32))
        val key = config.jwtSecretKey(props)
        val issuer = TokenIssuer(config.jwtEncoder(key), props, clock)
        val id = UUID.randomUUID()
        val jwt = config.jwtDecoder(key, clock).decode(issuer.issue(id, "test@example.com", Role.ADMIN))
        assertEquals("HS256", jwt.headers["alg"])
        assertEquals(id.toString(), jwt.subject)
        assertEquals("test@example.com", jwt.getClaimAsString("email"))
        assertEquals("ADMIN", jwt.getClaimAsString("role"))
        assertEquals(now, jwt.issuedAt)
        assertEquals(now.plus(Duration.ofHours(24)), jwt.expiresAt)
    }

    @Test
    fun `secret strength counts UTF-8 bytes`() {
        assertFailsWith<IllegalArgumentException> { config.jwtSecretKey(properties("a".repeat(31))) }
        assertEquals(32, config.jwtSecretKey(properties("é".repeat(16))).encoded.size)
        assertFailsWith<IllegalArgumentException> {
            config.jwtSecretKey(properties("a".repeat(32)).copy(jwt = EcoEatsProperties.Jwt("a".repeat(32), Duration.ZERO)))
        }
    }

    @Test
    fun `user existence cache refreshes at five seconds`() {
        val users = mockk<UserRepository>()
        val clock = mockk<Clock>()
        val id = UUID.randomUUID()
        every { clock.instant() } returnsMany listOf(now, now.plusSeconds(4), now.plusSeconds(5))
        every { users.existsById(id) } returnsMany listOf(true, false)
        val cache = UserPresenceCache(users, clock)
        assertTrue(cache.exists(id))
        assertTrue(cache.exists(id))
        assertFalse(cache.exists(id))
        verify(exactly = 2) { users.existsById(id) }
    }

    @Test
    fun `missing users are not cached`() {
        val users = mockk<UserRepository>()
        val id = UUID.randomUUID()
        every { users.existsById(id) } returns false
        val cache = UserPresenceCache(users, clock)
        repeat(2) { assertFalse(cache.exists(id)) }
        verify(exactly = 2) { users.existsById(id) }
    }

    private fun properties(secret: String) = EcoEatsProperties(
        EcoEatsProperties.Cors(listOf("http://localhost:3000")),
        EcoEatsProperties.Jwt(secret),
        EcoEatsProperties.Mail(EcoEatsProperties.Resend("test", "test@example.com", "Test")),
    )
}
