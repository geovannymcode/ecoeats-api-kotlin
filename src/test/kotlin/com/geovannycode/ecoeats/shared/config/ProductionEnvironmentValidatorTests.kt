package com.geovannycode.ecoeats.shared.config

import org.junit.jupiter.api.Test
import org.springframework.context.support.GenericApplicationContext
import org.springframework.mock.env.MockEnvironment
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

internal class ProductionEnvironmentValidatorTests {
    @Test
    fun `production fails before beans initialize and identifies each missing variable`() {
        val variables =
            mapOf(
                "JWT_SECRET" to "test-secret-with-at-least-32-bytes",
                "RESEND_API_KEY" to "test-only",
                "RESEND_FROM_EMAIL" to "test@example.com",
                "BUCKET" to "test",
                "ENDPOINT" to "http://localhost:9000",
                "ACCESS_KEY_ID" to "test",
                "SECRET_ACCESS_KEY" to "test",
                "PGHOST" to "localhost",
                "PGPORT" to "5432",
                "PGDATABASE" to "test",
                "PGUSER" to "test",
                "PGPASSWORD" to "test",
            )
        variables.keys.forEach { missing ->
            val env = MockEnvironment().apply { setActiveProfiles("prod") }
            variables.forEach { (name, value) -> env.setProperty(name, if (name == missing) " " else value) }
            GenericApplicationContext().use { context ->
                context.environment = env
                val failure = assertFailsWith<IllegalArgumentException> { ProductionEnvironmentValidator().initialize(context) }
                assertEquals("Missing required environment variable: $missing", failure.message)
            }
        }
        GenericApplicationContext().use { context ->
            context.environment =
                MockEnvironment().apply {
                    setActiveProfiles("prod")
                    variables.forEach { (name, value) -> setProperty(name, value) }
                }
            ProductionEnvironmentValidator().initialize(context)
        }
    }

    @Test
    fun `local development does not require production credentials`() {
        GenericApplicationContext().use { context ->
            context.environment = MockEnvironment().apply { setActiveProfiles("local") }
            ProductionEnvironmentValidator().initialize(context)
        }
    }
}
