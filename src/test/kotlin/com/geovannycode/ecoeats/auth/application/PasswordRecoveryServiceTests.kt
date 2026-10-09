package com.geovannycode.ecoeats.auth.application

import com.geovannycode.ecoeats.auth.domain.ForgotPasswordResult
import com.geovannycode.ecoeats.auth.domain.PasswordResetResult
import com.geovannycode.ecoeats.auth.domain.PasswordResetTokenEntity
import com.geovannycode.ecoeats.auth.domain.UserEntity
import com.geovannycode.ecoeats.auth.infrastructure.PasswordResetTokenRepository
import com.geovannycode.ecoeats.auth.infrastructure.UserRepository
import com.geovannycode.ecoeats.notification.EmailDeliveryException
import com.geovannycode.ecoeats.notification.EmailSender
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.crypto.password.PasswordEncoder
import java.security.SecureRandom
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

internal class PasswordRecoveryServiceTests {
    private val users = mockk<UserRepository>()
    private val tokens = mockk<PasswordResetTokenRepository>()
    private val encoder = mockk<PasswordEncoder>()
    private val sender = mockk<EmailSender>()
    private val random = mockk<SecureRandom>()
    private val user = mockk<UserEntity>()
    private val userId = UUID.randomUUID()
    private val now = Instant.parse("2026-10-09T12:00:00Z")
    private lateinit var service: PasswordRecoveryService

    @BeforeEach
    fun setUp() {
        every { user.id } returns userId
        every { user.email } returns "person@example.com"
        every { users.findByEmailForUpdate("person@example.com") } returns user
        every { tokens.save(any()) } answers { firstArg() }
        service = PasswordRecoveryService(users, tokens, encoder, sender, random, Clock.fixed(now, ZoneOffset.UTC))
    }

    @Test
    fun `forgot password returns not found without sending email`() {
        every { users.findByEmailForUpdate(any()) } returns null
        assertSame(ForgotPasswordResult.UserNotFound, service.forgotPassword("missing@example.com"))
        verify(exactly = 0) { sender.sendPasswordResetOtp(any(), any()) }
    }

    @Test
    fun `forgot invalidates previous token then saves hash before sending`() {
        val saved = slot<PasswordResetTokenEntity>()
        every { tokens.invalidateActiveTokens(userId) } returns 1
        every { random.nextInt(900_000) } returns 23_456
        every { tokens.saveAndFlush(capture(saved)) } answers { firstArg() }
        every { sender.sendPasswordResetOtp("person@example.com", "123456") } just Runs
        assertSame(ForgotPasswordResult.Sent, service.forgotPassword(" Person@EXAMPLE.com "))
        assertEquals("8d969eef6ecad3c29a3a629280e686cf0c3f5d5a86aff3ca12020c923adc6c92", saved.captured.otpHash)
        assertEquals(now.plusSeconds(900), saved.captured.expiresAt)
        assertEquals(userId, saved.captured.userId)
        verifyOrder {
            tokens.invalidateActiveTokens(userId)
            tokens.saveAndFlush(any())
            sender.sendPasswordResetOtp("person@example.com", "123456")
        }
    }

    @Test
    fun `delivery failure propagates to trigger rollback`() {
        every { tokens.invalidateActiveTokens(userId) } returns 1
        every { random.nextInt(900_000) } returns 23_456
        every { tokens.saveAndFlush(any()) } answers { firstArg() }
        every { sender.sendPasswordResetOtp(any(), any()) } throws EmailDeliveryException()
        assertFailsWith<EmailDeliveryException> { service.forgotPassword("person@example.com") }
    }

    @Test
    fun `expired token is invalid even at exact expiration boundary`() {
        val token = token(expiresAt = now)
        every { tokens.findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(userId) } returns token
        assertFalse(service.verifyOtp("person@example.com", "123456"))
        assertSame(PasswordResetResult.InvalidOtp, service.resetPassword("person@example.com", "123456", "new-password"))
        verify(exactly = 0) { encoder.encode(any()) }
    }

    @Test
    fun `used token cannot be verified or reset`() {
        every { tokens.findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(userId) } returns token(used = true)
        assertFalse(service.verifyOtp("person@example.com", "123456"))
        assertSame(PasswordResetResult.InvalidOtp, service.resetPassword("person@example.com", "123456", "new-password"))
    }

    @Test
    fun `five wrong attempts consume token and reject subsequent correct OTP`() {
        val token = token()
        every { tokens.findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(userId) } returns token
        repeat(5) { assertFalse(service.verifyOtp("person@example.com", "999999")) }
        assertEquals(5, token.attempts)
        assertTrue(token.used)
        assertFalse(service.verifyOtp("person@example.com", "123456"))
        verify(exactly = 5) { tokens.save(token) }
    }

    @Test
    fun `reset failures count against same attempt limit`() {
        val token = token(attempts = 4)
        every { tokens.findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(userId) } returns token
        assertSame(PasswordResetResult.InvalidOtp, service.resetPassword("person@example.com", "999999", "new-password"))
        assertEquals(5, token.attempts)
        assertTrue(token.used)
    }

    @Test
    fun `valid verify does not consume token`() {
        val token = token()
        every { tokens.findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(userId) } returns token
        assertTrue(service.verifyOtp("person@example.com", "123456"))
        assertFalse(token.used)
        assertEquals(0, token.attempts)
        verify(exactly = 0) { tokens.save(any()) }
    }

    @Test
    fun `reset encodes new password and consumes token`() {
        val token = token()
        every { tokens.findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(userId) } returns token
        every { encoder.encode("new-password") } returns "new-bcrypt-hash"
        every { user.password = any() } just Runs
        every { users.save(user) } returns user
        assertSame(PasswordResetResult.Reset, service.resetPassword("person@example.com", "123456", "new-password"))
        assertTrue(token.used)
        verify {
            user.password = "new-bcrypt-hash"
            users.save(user)
            tokens.save(token)
        }
    }

    @Test
    fun `missing account or token is invalid`() {
        every { users.findByEmailForUpdate("missing@example.com") } returns null
        every { tokens.findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(userId) } returns null
        assertFalse(service.verifyOtp("missing@example.com", "123456"))
        assertFalse(service.verifyOtp("person@example.com", "123456"))
        assertSame(PasswordResetResult.InvalidOtp, service.resetPassword("missing@example.com", "123456", "new-password"))
    }

    private fun token(
        expiresAt: Instant = now.plusSeconds(900),
        attempts: Int = 0,
        used: Boolean = false,
    ) = PasswordResetTokenEntity(userId, "123456".otpSha256(), expiresAt, attempts, used)
}
