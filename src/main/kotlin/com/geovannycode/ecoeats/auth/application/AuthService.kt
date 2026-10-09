package com.geovannycode.ecoeats.auth.application

import com.geovannycode.ecoeats.auth.domain.LoginResult
import com.geovannycode.ecoeats.auth.domain.LoginSession
import com.geovannycode.ecoeats.auth.domain.RegistrationResult
import com.geovannycode.ecoeats.auth.domain.UserEntity
import com.geovannycode.ecoeats.auth.infrastructure.UserRepository
import com.geovannycode.ecoeats.shared.security.TokenIssuer
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.util.UUID

@Service
internal class AuthService(private val users: UserRepository, private val encoder: PasswordEncoder, private val tokens: TokenIssuer) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val dummyHash = checkNotNull(encoder.encode(UUID.randomUUID().toString()))

    fun register(numeroDocumento: String, nombres: String, apellidos: String, correo: String, password: String): RegistrationResult {
        val email = correo.normalizedEmail()
        logger.info("Register attempt: email={}", email.maskedEmail())
        if (users.existsByEmailIgnoreCase(email)) return RegistrationResult.EmailTaken
        if (users.existsByNumeroDocumento(numeroDocumento)) return RegistrationResult.DocumentTaken
        val user = UserEntity(numeroDocumento, nombres, apellidos, email, checkNotNull(encoder.encode(password)))
        // The repository owns the insert transaction. A failed insert rolls back before conflict queries.
        return try {
            RegistrationResult.Registered(users.saveAndFlush(user))
        } catch (exception: DataIntegrityViolationException) {
            when {
                users.existsByEmailIgnoreCase(email) -> RegistrationResult.EmailTaken
                users.existsByNumeroDocumento(numeroDocumento) -> RegistrationResult.DocumentTaken
                else -> throw exception
            }
        }
    }

    fun login(email: String, password: String): LoginResult {
        val normalized = email.normalizedEmail()
        val user = users.findByEmailIgnoreCase(normalized)
        val supportedLength = password.toByteArray(Charsets.UTF_8).size <= 72
        val matches = encoder.matches(if (supportedLength) password else "", user?.password ?: dummyHash)
        if (user == null || !matches || !supportedLength) {
            logger.info("Login failed: email={}", normalized.maskedEmail())
            return LoginResult.InvalidCredentials
        }
        val id = checkNotNull(user.id) { "Persisted user has no ID" }
        return LoginResult.Authenticated(LoginSession(id, user.email, tokens.issue(id, user.email, user.role), user.tokenFirebaseAuth))
    }
}
