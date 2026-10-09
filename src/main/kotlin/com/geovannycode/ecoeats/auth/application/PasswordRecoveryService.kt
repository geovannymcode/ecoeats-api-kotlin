package com.geovannycode.ecoeats.auth.application

import com.geovannycode.ecoeats.auth.domain.ForgotPasswordResult
import com.geovannycode.ecoeats.auth.domain.PasswordResetResult
import com.geovannycode.ecoeats.auth.domain.PasswordResetTokenEntity
import com.geovannycode.ecoeats.auth.domain.UserEntity
import com.geovannycode.ecoeats.auth.infrastructure.PasswordResetTokenRepository
import com.geovannycode.ecoeats.auth.infrastructure.UserRepository
import com.geovannycode.ecoeats.notification.EmailSender
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Clock

@Service
internal class PasswordRecoveryService(
    private val users: UserRepository,
    private val tokens: PasswordResetTokenRepository,
    private val encoder: PasswordEncoder,
    private val emailSender: EmailSender,
    private val random: SecureRandom,
    private val clock: Clock,
) {
    @Transactional
    fun forgotPassword(email: String): ForgotPasswordResult {
        val user = users.findByEmailForUpdate(email.normalizedEmail()) ?: return ForgotPasswordResult.UserNotFound
        val userId = checkNotNull(user.id)
        tokens.invalidateActiveTokens(userId)
        val otp = (100_000 + random.nextInt(900_000)).toString()
        tokens.saveAndFlush(PasswordResetTokenEntity(userId, otp.otpSha256(), clock.instant().plusSeconds(15 * 60L)))
        // Delivery failures propagate so both the new token and invalidation roll back.
        emailSender.sendPasswordResetOtp(user.email, otp)
        return ForgotPasswordResult.Sent
    }

    @Transactional
    fun verifyOtp(email: String, otp: String): Boolean {
        val user = users.findByEmailForUpdate(email.normalizedEmail()) ?: return false
        return validatedToken(user, otp) != null
    }

    @Transactional
    fun resetPassword(email: String, otp: String, newPassword: String): PasswordResetResult {
        val user = users.findByEmailForUpdate(email.normalizedEmail()) ?: return PasswordResetResult.InvalidOtp
        val token = validatedToken(user, otp) ?: return PasswordResetResult.InvalidOtp
        user.password = checkNotNull(encoder.encode(newPassword)) { "Password encoder returned no hash" }
        token.used = true
        users.save(user)
        tokens.save(token)
        return PasswordResetResult.Reset
    }

    private fun validatedToken(user: UserEntity, otp: String): PasswordResetTokenEntity? {
        val token = tokens.findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(checkNotNull(user.id)) ?: return null
        if (token.used || !token.expiresAt.isAfter(clock.instant())) return null
        if (token.attempts >= 5) {
            token.used = true
            tokens.save(token)
            return null
        }
        if (!otp.matchesOtpHash(token.otpHash)) {
            token.attempts += 1
            if (token.attempts >= 5) token.used = true
            tokens.save(token)
            return null
        }
        return token
    }
}
