package com.geovannycode.ecoeats.shared.config

import org.springframework.context.ApplicationContextInitializer
import org.springframework.context.ConfigurableApplicationContext

internal class ProductionEnvironmentValidator : ApplicationContextInitializer<ConfigurableApplicationContext> {
    override fun initialize(applicationContext: ConfigurableApplicationContext) {
        val environment = applicationContext.environment
        if (!environment.matchesProfiles("prod")) return
        REQUIRED_VARIABLES.forEach { variable ->
            require(!environment.getProperty(variable).isNullOrBlank()) { "Missing required environment variable: $variable" }
        }
        require(
            environment
                .getProperty("JWT_SECRET")
                .orEmpty()
                .toByteArray(Charsets.UTF_8)
                .size >= 32,
        ) {
            "JWT_SECRET must contain at least 32 bytes"
        }
        require(environment.getProperty("PGSSLMODE", "disable") in setOf("disable", "require", "verify-ca", "verify-full")) {
            "PGSSLMODE must be disable, require, verify-ca or verify-full"
        }
    }

    private companion object {
        val REQUIRED_VARIABLES =
            listOf(
                "JWT_SECRET",
                "RESEND_API_KEY",
                "RESEND_FROM_EMAIL",
                "BUCKET",
                "ENDPOINT",
                "ACCESS_KEY_ID",
                "SECRET_ACCESS_KEY",
                "PGHOST",
                "PGPORT",
                "PGDATABASE",
                "PGUSER",
                "PGPASSWORD",
            )
    }
}
