package com.geovannycode.ecoeats.auth.infrastructure

import com.geovannycode.ecoeats.auth.domain.Role
import com.geovannycode.ecoeats.auth.domain.UserEntity
import com.geovannycode.ecoeats.shared.config.EcoEatsProperties
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
internal class AdminSeeder(
    private val properties: EcoEatsProperties,
    private val users: UserRepository,
    private val passwordEncoder: PasswordEncoder,
) : ApplicationRunner {
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    override fun run(args: ApplicationArguments) {
        val password = properties.seed.adminPassword
        if (password.isNullOrBlank()) {
            logger.info("Admin seed skipped: no password configured")
            return
        }
        if (users.existsByEmailIgnoreCase("admin@ecoeats.com")) {
            logger.info("Admin seed skipped: account already exists")
            return
        }
        users.save(
            UserEntity(
                numeroDocumento = "00000000",
                nombres = "Admin",
                apellidos = "EcoEats",
                email = "admin@ecoeats.com",
                password = checkNotNull(passwordEncoder.encode(password)) { "Password encoder returned no hash" },
                role = Role.ADMIN,
            ),
        )
        logger.info("Admin account seeded")
    }
}
