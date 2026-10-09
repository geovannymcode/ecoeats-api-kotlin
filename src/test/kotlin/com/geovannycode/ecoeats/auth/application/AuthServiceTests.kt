package com.geovannycode.ecoeats.auth.application

import com.geovannycode.ecoeats.auth.domain.LoginResult
import com.geovannycode.ecoeats.auth.domain.RegistrationResult
import com.geovannycode.ecoeats.auth.domain.Role
import com.geovannycode.ecoeats.auth.domain.UserEntity
import com.geovannycode.ecoeats.auth.infrastructure.UserRepository
import com.geovannycode.ecoeats.shared.security.TokenIssuer
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

internal class AuthServiceTests {
    private val users = mockk<UserRepository>()
    private val encoder = mockk<PasswordEncoder>()
    private val tokens = mockk<TokenIssuer>()
    private lateinit var service: AuthService
    private val userId = UUID.randomUUID()
    private val persisted = mockk<UserEntity>()

    @BeforeEach
    fun setUp() {
        every { encoder.encode(any()) } returns "dummy-or-encoded-hash"
        every { users.existsByEmailIgnoreCase(any()) } returns false
        every { users.existsByNumeroDocumento(any()) } returns false
        every { persisted.id } returns userId
        every { persisted.email } returns "person@example.com"
        every { persisted.password } returns "stored-hash"
        every { persisted.role } returns Role.USER
        every { persisted.tokenFirebaseAuth } returns "firebase-value"
        service = AuthService(users, encoder, tokens)
    }

    @Test
    fun `registration normalizes email and encodes password`() {
        val inserted = slot<UserEntity>()
        every { users.saveAndFlush(capture(inserted)) } returns persisted
        val result = assertIs<RegistrationResult.Registered>(register())
        assertSame(persisted, result.user)
        assertEquals("person@example.com", inserted.captured.email)
        assertEquals("100", inserted.captured.numeroDocumento)
        assertEquals(Role.USER, inserted.captured.role)
        assertEquals("dummy-or-encoded-hash", inserted.captured.password)
        verify { encoder.encode("secret-value") }
    }

    @Test
    fun `registration reports duplicate email`() {
        every { users.existsByEmailIgnoreCase("person@example.com") } returns true
        assertSame(RegistrationResult.EmailTaken, register())
        verify(exactly = 0) { users.saveAndFlush(any()) }
    }

    @Test
    fun `registration reports duplicate document`() {
        every { users.existsByNumeroDocumento("100") } returns true
        assertSame(RegistrationResult.DocumentTaken, register())
        verify(exactly = 0) { users.saveAndFlush(any()) }
    }

    @Test
    fun `concurrent email conflict translates after failed insert`() {
        every { users.existsByEmailIgnoreCase(any()) } returnsMany listOf(false, true)
        every { users.saveAndFlush(any()) } throws DataIntegrityViolationException("unique constraint")
        assertSame(RegistrationResult.EmailTaken, register())
    }

    @Test
    fun `concurrent document conflict translates after failed insert`() {
        every { users.existsByNumeroDocumento(any()) } returnsMany listOf(false, true)
        every { users.saveAndFlush(any()) } throws DataIntegrityViolationException("unique constraint")
        assertSame(RegistrationResult.DocumentTaken, register())
    }

    @Test
    fun `unexpected persistence failure is not mislabeled as a duplicate`() {
        every { users.saveAndFlush(any()) } throws DataIntegrityViolationException("other constraint")
        assertFailsWith<DataIntegrityViolationException> { register() }
    }

    @Test
    fun `valid login returns complete session`() {
        every { users.findByEmailIgnoreCase("person@example.com") } returns persisted
        every { encoder.matches("secret-value", "stored-hash") } returns true
        every { tokens.issue(userId, "person@example.com", Role.USER) } returns "issued-token"
        val session = assertIs<LoginResult.Authenticated>(service.login(" Person@EXAMPLE.com ", "secret-value")).response
        assertEquals(userId, session.id)
        assertEquals("person@example.com", session.email)
        assertEquals("issued-token", session.token)
        assertEquals("firebase-value", session.tokenFirebaseAuth)
    }

    @Test
    fun `wrong password returns invalid credentials`() {
        every { users.findByEmailIgnoreCase(any()) } returns persisted
        every { encoder.matches("wrong", "stored-hash") } returns false
        assertSame(LoginResult.InvalidCredentials, service.login("person@example.com", "wrong"))
        verify(exactly = 0) { tokens.issue(any(), any(), any()) }
    }

    @Test
    fun `missing user still checks a dummy hash`() {
        every { users.findByEmailIgnoreCase(any()) } returns null
        every { encoder.matches("wrong", "dummy-or-encoded-hash") } returns false
        assertSame(LoginResult.InvalidCredentials, service.login("missing@example.com", "wrong"))
        verify(exactly = 1) { encoder.matches("wrong", "dummy-or-encoded-hash") }
        verify(exactly = 0) { tokens.issue(any(), any(), any()) }
    }

    @Test
    fun `oversized password performs comparison and returns invalid credentials`() {
        every { users.findByEmailIgnoreCase(any()) } returns persisted
        every { encoder.matches("", "stored-hash") } returns false
        assertSame(LoginResult.InvalidCredentials, service.login("person@example.com", "é".repeat(37)))
        verify(exactly = 1) { encoder.matches("", "stored-hash") }
    }

    @Test
    fun `email logging masks local part and strips line breaks`() {
        assertEquals("g***@dominio.com", "geovanny@dominio.com".maskedEmail())
        assertEquals("g***@dominio.com", "geovanny@dominio.com\r\n".maskedEmail())
        assertEquals("***", "invalid".maskedEmail())
    }

    private fun register() = service.register("100", "Test", "Account", " Person@EXAMPLE.com ", "secret-value")
}
