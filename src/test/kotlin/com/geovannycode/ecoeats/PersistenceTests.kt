package com.geovannycode.ecoeats

import com.geovannycode.ecoeats.auth.domain.PasswordResetTokenEntity
import com.geovannycode.ecoeats.auth.domain.Role
import com.geovannycode.ecoeats.auth.domain.UserEntity
import com.geovannycode.ecoeats.auth.infrastructure.AdminSeeder
import com.geovannycode.ecoeats.auth.infrastructure.PasswordResetTokenRepository
import com.geovannycode.ecoeats.auth.infrastructure.UserRepository
import com.geovannycode.ecoeats.dish.domain.DishEntity
import com.geovannycode.ecoeats.dish.infrastructure.DishRepository
import com.geovannycode.ecoeats.shared.config.EcoEatsProperties
import jakarta.persistence.EntityManager
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.DefaultApplicationArguments
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.core.io.ClassPathResource
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.init.ScriptUtils
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Import(TestcontainersConfiguration::class)
@SpringBootTest(
    properties = [
        "spring.docker.compose.enabled=false",
        "spring.jpa.hibernate.ddl-auto=validate",
        "ecoeats.jwt.secret=test-only-secret-with-at-least-32-bytes",
        "ecoeats.mail.resend.api-key=test-only",
        "ecoeats.mail.resend.from-email=test@example.com",
        "ecoeats.seed.admin-password=",
    ],
)
internal class PersistenceTests {
    @Autowired private lateinit var users: UserRepository

    @Autowired private lateinit var tokens: PasswordResetTokenRepository

    @Autowired private lateinit var dishes: DishRepository

    @Autowired private lateinit var flyway: Flyway

    @Autowired private lateinit var dataSource: DataSource

    @Autowired private lateinit var jdbc: JdbcTemplate

    @Autowired private lateinit var encoder: PasswordEncoder

    @Autowired private lateinit var properties: EcoEatsProperties

    @Autowired private lateinit var transaction: TransactionTemplate

    @Autowired private lateinit var entityManager: EntityManager

    @BeforeEach
    fun clearTestAccounts() {
        tokens.deleteAll()
        users.deleteAll()
        jdbc.update("DELETE FROM dishes WHERE name = 'Persistence test dish'")
    }

    @Test
    fun `four migrations apply and seed remains idempotent`() {
        assertEquals(4, flyway.info().applied().size)
        assertEquals(12, dishes.count())
        assertEquals(0, flyway.migrate().migrationsExecuted)
        assertEquals(0, flyway.migrate().migrationsExecuted)
        // Execute V4 again as well: exercise WHERE NOT EXISTS, not just Flyway history.
        dataSource.connection.use {
            ScriptUtils.executeSqlScript(it, ClassPathResource("db/migration/V4__seed_dishes.sql"))
        }
        val seeded = dishes.findAllByOrderByNameAsc()
        assertEquals(12, seeded.size)
        assertEquals(12, seeded.map { it.name }.distinct().size)
        assertEquals("Bandeja Paisa", seeded.single { it.name == "Bandeja Paisa" }.name)
        val bandeja = seeded.single { it.name == "Bandeja Paisa" }
        assertEquals(BigDecimal("28000.00"), bandeja.price)
        assertEquals(BigDecimal("4.9"), bandeja.rating)
        assertTrue(bandeja.thumbnail.startsWith("https://"))
        assertNotNull(bandeja.createdAt)
        assertNotNull(bandeja.updatedAt)
        val databaseOrder = jdbc.queryForList("SELECT name FROM dishes ORDER BY name", String::class.java)
        assertEquals(databaseOrder, seeded.map { it.name })
    }

    @Test
    fun `email uniqueness is enforced ignoring case`() {
        users.saveAndFlush(user("Person@Example.com", "100"))
        assertTrue(users.existsByEmailIgnoreCase("PERSON@example.com"))
        assertNotNull(users.findByEmailIgnoreCase("person@EXAMPLE.com"))
        assertNull(users.findByEmailIgnoreCase("missing@example.com"))
        assertFailsWith<DataIntegrityViolationException> {
            users.saveAndFlush(user("person@example.com", "101"))
        }
        assertEquals(1, users.count())
    }

    @Test
    fun `document uniqueness is enforced`() {
        users.saveAndFlush(user("first@example.com", "100"))
        assertTrue(users.existsByNumeroDocumento("100"))
        assertFailsWith<DataIntegrityViolationException> {
            users.saveAndFlush(user("second@example.com", "100"))
        }
        assertEquals(1, users.count())
    }

    @Test
    fun `admin seeding is idempotent and preserves existing password`() {
        seeder("local-test-password").run(DefaultApplicationArguments())
        seeder("different-test-password").run(DefaultApplicationArguments())
        val admin = assertNotNull(users.findByEmailIgnoreCase("ADMIN@ECOEATS.COM"))
        assertEquals(1, users.count())
        assertEquals(Role.ADMIN, admin.role)
        assertEquals("00000000", admin.numeroDocumento)
        assertEquals("Admin", admin.nombres)
        assertEquals("EcoEats", admin.apellidos)
        assertTrue(encoder.matches("local-test-password", admin.password))
        assertFalse(encoder.matches("different-test-password", admin.password))
        assertTrue(admin.password.startsWith("\$2a\$10\$"))
        assertFalse(admin.toString().contains(admin.password))
    }

    @Test
    fun `no password means no admin`() {
        listOf(null, "", "   ").forEach { seeder(it).run(DefaultApplicationArguments()) }
        assertEquals(0, users.count())
    }

    @Test
    fun `UUID v7 and timestamps are generated and survive loading`() {
        val user = users.saveAndFlush(user())
        val userId = assertNotNull(user.id)
        val created = assertNotNull(user.createdAt)
        assertNotNull(user.updatedAt)
        val dish = dishes.saveAndFlush(DishEntity("Persistence test dish", price = BigDecimal("12.34")))
        val token = tokens.saveAndFlush(PasswordResetTokenEntity(userId, "a".repeat(64), Instant.now().plusSeconds(600)))
        listOf(user.id, dish.id, token.id).forEach { assertEquals(7, assertNotNull(it).version()) }
        assertNotNull(dish.createdAt)
        assertNotNull(token.createdAt)
        transaction.executeWithoutResult {
            val managed = entityManager.find(UserEntity::class.java, userId)
            managed.password = "changed-test-hash"
            entityManager.flush()
        }
        val reloaded = assertNotNull(users.findByEmailIgnoreCase(user.email))
        // PostgreSQL rounds timestamps to microseconds.
        assertEquals(created.epochSecond, assertNotNull(reloaded.createdAt).epochSecond)
        assertTrue(assertNotNull(reloaded.updatedAt).isAfter(assertNotNull(reloaded.createdAt)))
        assertEquals("changed-test-hash", reloaded.password)
    }

    @Test
    fun `token lookup and invalidation are scoped to user and delete cascades`() {
        val owner = users.saveAndFlush(user("owner@example.com", "100"))
        val other = users.saveAndFlush(user("other@example.com", "101"))
        val ownerId = assertNotNull(owner.id)
        val otherId = assertNotNull(other.id)
        val first = tokens.saveAndFlush(PasswordResetTokenEntity(ownerId, "a".repeat(64), Instant.now().plusSeconds(600)))
        jdbc.update("UPDATE password_reset_tokens SET created_at = now() - interval '1 hour' WHERE id = ?", first.id)
        val latest = tokens.saveAndFlush(PasswordResetTokenEntity(ownerId, "b".repeat(64), Instant.now().plusSeconds(600)))
        tokens.saveAndFlush(PasswordResetTokenEntity(otherId, "c".repeat(64), Instant.now().plusSeconds(600)))
        assertEquals(latest.id, assertNotNull(tokens.findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(ownerId)).id)
        assertEquals(2, tokens.invalidateActiveTokens(ownerId))
        assertEquals(0, tokens.invalidateActiveTokens(ownerId))
        assertNull(tokens.findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(ownerId))
        assertNotNull(tokens.findFirstByUserIdAndUsedFalseOrderByCreatedAtDesc(otherId))
        assertFalse(latest.toString().contains(latest.otpHash))
        users.deleteById(ownerId)
        assertEquals(1, tokens.count())
    }

    @Test
    fun `entity identity is consistent with Hibernate proxies`() {
        val user = users.saveAndFlush(user())
        transaction.executeWithoutResult {
            val proxy = users.getReferenceById(assertNotNull(user.id))
            assertEquals(user, proxy)
            assertEquals(proxy, user)
            assertEquals(user.hashCode(), proxy.hashCode())
        }
        assertNotEquals(user(), user())
    }

    @Test
    fun `dish numeric checks reject invalid persisted values`() {
        assertFailsWith<DataIntegrityViolationException> {
            dishes.saveAndFlush(DishEntity("Persistence test dish", price = BigDecimal("-1")))
        }
        assertFailsWith<DataIntegrityViolationException> {
            dishes.saveAndFlush(DishEntity("Persistence test dish", rating = BigDecimal("5.1")))
        }
    }

    private fun user(
        email: String = "person@example.com",
        document: String = "100",
    ) = UserEntity(document, "Test", "Account", email, "test-only-password-hash")

    private fun seeder(password: String?) =
        AdminSeeder(
            properties.copy(seed = EcoEatsProperties.Seed(password)),
            users,
            encoder,
        )
}
